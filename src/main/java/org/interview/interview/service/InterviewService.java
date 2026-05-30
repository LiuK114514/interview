package org.interview.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interview.common.ai.StructuredOutputService;
import org.interview.common.exception.BusinessException;
import org.interview.common.exception.ErrorCode;
import org.interview.interview.dto.*;
import org.interview.interview.entity.InterviewAnswerEntity;
import org.interview.interview.entity.InterviewSessionEntity;
import org.interview.interview.entity.SessionStatus;
import org.interview.interview.repository.InterviewAnswerRepository;
import org.interview.interview.repository.InterviewSessionRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InterviewService {

    private final ChatClient chatClient;
    private final StructuredOutputService structuredOutputService;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final ObjectMapper objectMapper;

    public InterviewService(OpenAiChatModel chatModel,
                            StructuredOutputService structuredOutputService,
                            InterviewSessionRepository sessionRepository,
                            InterviewAnswerRepository answerRepository,
                            ObjectMapper objectMapper) {
        this.structuredOutputService = structuredOutputService;
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.objectMapper = objectMapper;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Transactional
    public StartInterviewResponse startInterview(CreateInterviewRequest request) {
        BeanOutputConverter<InterviewQuestions> converter =
                new BeanOutputConverter<>(InterviewQuestions.class);

        String systemPrompt = """
                你是一个资深技术面试官，擅长出技术面试题。
                请根据以下要求生成面试题目：
                - 技能方向：%s
                - 难度级别：%s

                要求：
                - 生成 5 道高质量面试题
                - 覆盖基础知识、实际应用和原理理解
                - 题目要有区分度

                %s
                """.formatted(request.skillId(), request.difficulty(), converter.getFormat());

        InterviewQuestions interviewQuestions = structuredOutputService.invoke(
                chatClient, systemPrompt, "请开始出题", converter, "面试出题");

        List<QuestionDTO> questions = interviewQuestions.questions();

        InterviewSessionEntity session = new InterviewSessionEntity();
        session.setSessionId(UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        session.setSkillId(request.skillId());
        session.setDifficulty(request.difficulty());
        session.setStatus(SessionStatus.CREATED);
        session.setCurrentQuestionIndex(0);
        session.setQuestionsJson(toJson(questions));
        sessionRepository.save(session);

        return new StartInterviewResponse(
                session.getSessionId(), questions.size(), questions);
    }

    /**
     * 提交答案并调用 AI 评估（不含事务，AI 调用不能放在事务内）
     */
    public SubmitAnswerResponse submitAnswer(SubmitAnswerRequest request) {
         // Step 1 — 校验会话存在且未结束
        InterviewSessionEntity session = findSession(request.sessionId());
        validateSessionActive(session);
        //Step 2 — 反序列化题目列表，校验题目索引
        List<QuestionDTO> questions = deserializeQuestions(session.getQuestionsJson());
        QuestionDTO question = questions.stream()
                .filter(q -> q.index() == request.questionIndex())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "题目索引无效: " + request.questionIndex()));

        // 调用 AI 评估答案
        //Step 3 — 调用 AI 评估（不在事务内！）
        AnswerEvaluationDTO evaluation = evaluateAnswer(question, request.answer());

        // 保存答案 + 评估结果
        InterviewAnswerEntity answer = new InterviewAnswerEntity();
        answer.setSessionId(request.sessionId());
        answer.setQuestionIndex(request.questionIndex());
        answer.setQuestion(question.question());
        answer.setQuestionCategory(question.category());
        answer.setUserAnswer(request.answer());
        answer.setScore(evaluation.score());
        answer.setFeedback(evaluation.feedback());
        answer.setCorrectAnswer(evaluation.correctAnswer());
        answerRepository.save(answer);

        // 更新会话状态，清缓存
        if (session.getStatus() == SessionStatus.CREATED) {
            session.setStatus(SessionStatus.IN_PROGRESS);
            sessionRepository.save(session);
        }

        return new SubmitAnswerResponse(
                evaluation.score(), evaluation.feedback(), evaluation.correctAnswer());
    }

    /**
     * 获取下一题，全部答完则标记 COMPLETED
     */
    @Transactional
    public NextQuestionResponse nextQuestion(String sessionId) {
        InterviewSessionEntity session = findSession(sessionId);
        List<QuestionDTO> questions = deserializeQuestions(session.getQuestionsJson());

        Set<Integer> answeredIndexes = answerRepository.findBySessionIdOrderByQuestionIndex(sessionId)
                .stream()
                .map(InterviewAnswerEntity::getQuestionIndex)
                .collect(Collectors.toSet());

        for (QuestionDTO q : questions) {
            if (!answeredIndexes.contains(q.index())) {
                session.setCurrentQuestionIndex(q.index());
                sessionRepository.save(session);
                return new NextQuestionResponse(false, q);
            }
        }

        session.setStatus(SessionStatus.COMPLETED);
        sessionRepository.save(session);
        return new NextQuestionResponse(true, null);
    }

    /**
     * 生成面试报告（AI 综合分析所有问答）
     */
    public GenerateReportResponse generateReport(String sessionId) {
        InterviewSessionEntity session = findSession(sessionId);

        // 已生成过报告，直接返回缓存
        if (session.getStatus() == SessionStatus.EVALUATED) {
            return buildCachedReport(session);
        }

        // 必须全部答完才能出报告
        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "面试尚未完成，无法生成报告");
        }

        List<InterviewAnswerEntity> answers = answerRepository
                .findBySessionIdOrderByQuestionIndex(sessionId);

        InterviewReportDTO report = generateReportFromAI(session, answers);

        // 保存报告结果
        session.setOverallScore(report.totalScore());
        session.setOverallFeedback(report.overallFeedback());
        session.setReportJson(toJson(report));
        session.setStatus(SessionStatus.EVALUATED);
        sessionRepository.save(session);

        return new GenerateReportResponse(
                session.getSessionId(),
                report.totalScore(),
                report.overallFeedback(),
                report.strengths(),
                report.improvementSuggestions(),
                report.categoryBreakdown()
        );
    }

    private InterviewReportDTO generateReportFromAI(InterviewSessionEntity session,
                                                     List<InterviewAnswerEntity> answers) {
        BeanOutputConverter<InterviewReportDTO> converter =
                new BeanOutputConverter<>(InterviewReportDTO.class);

        StringBuilder qaSection = new StringBuilder();
        for (InterviewAnswerEntity a : answers) {
            qaSection.append("题目 %d [%s]: %s\n".formatted(
                    a.getQuestionIndex(), a.getQuestionCategory(), a.getQuestion()));
            qaSection.append("回答: %s\n".formatted(a.getUserAnswer()));
            qaSection.append("得分: %d/10\n".formatted(a.getScore()));
            qaSection.append("反馈: %s\n\n".formatted(a.getFeedback()));
        }

        String systemPrompt = """
                你是一个资深技术面试官。请根据以下面试记录生成综合评估报告。

                技能方向：%s
                难度级别：%s

                以下是面试者的问答记录：

                %s

                请给出：
                - totalScore：总分（满分 100）
                - overallFeedback：综合评价（200 字左右）
                - strengths：列出 2-3 个优势
                - improvementSuggestions：列出 2-3 个改进建议
                - categoryBreakdown：按分类列出平均分和改进建议

                %s
                """.formatted(session.getSkillId(), session.getDifficulty(),
                qaSection.toString(), converter.getFormat());

        return structuredOutputService.invoke(
                chatClient, systemPrompt, "请生成面试报告", converter, "面试报告");
    }

    private GenerateReportResponse buildCachedReport(InterviewSessionEntity session) {
        InterviewReportDTO report = deserializeReport(session.getReportJson());
        return new GenerateReportResponse(
                session.getSessionId(),
                report.totalScore(),
                report.overallFeedback(),
                report.strengths(),
                report.improvementSuggestions(),
                report.categoryBreakdown()
        );
    }

    private InterviewReportDTO deserializeReport(String json) {
        try {
            return objectMapper.readValue(json, InterviewReportDTO.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "报告解析失败");
        }
    }

    private AnswerEvaluationDTO evaluateAnswer(QuestionDTO question, String userAnswer) {
        BeanOutputConverter<AnswerEvaluationDTO> converter =
                new BeanOutputConverter<>(AnswerEvaluationDTO.class);

        String systemPrompt = """
                你是一个资深技术面试官。请严格评估面试者的回答。

                面试题目：%s
                面试者回答：%s

                请给出：
                - score：1-10 的整数分数
                - feedback：详细的评语（指出优点和不足）
                - correctAnswer：高质量的参考答案

                %s
                """.formatted(question.question(), userAnswer, converter.getFormat());

        return structuredOutputService.invoke(
                chatClient, systemPrompt, "请评估面试者的回答", converter, "答案评估");
    }

    private InterviewSessionEntity findSession(String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "面试会话不存在: " + sessionId));
    }

    private void validateSessionActive(InterviewSessionEntity session) {
        if (session.getStatus() == SessionStatus.COMPLETED
                || session.getStatus() == SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "面试已结束");
        }
    }

    private List<QuestionDTO> deserializeQuestions(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<QuestionDTO>>() {});
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "题目解析失败");
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "JSON 序列化失败: " + e.getMessage());
        }
    }
}
