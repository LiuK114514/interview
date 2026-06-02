package org.interview.resume.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.interview.common.ai.StructuredOutputService;
import org.interview.common.exception.BusinessException;
import org.interview.common.exception.ErrorCode;
import org.interview.config.DocumentParserService;
import org.interview.config.MinioConfig;
import org.interview.config.RedisStreamConfig;
import org.interview.resume.dto.ResumeAnalysisDTO;
import org.interview.resume.dto.ResumeDetailDTO;
import org.interview.resume.dto.ResumeListItemDTO;
import org.interview.resume.entity.ResumeAnalysisEntity;
import org.interview.resume.entity.ResumeEntity;
import org.interview.resume.repository.ResumeAnalysisRepository;
import org.interview.resume.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/markdown"
    );

    private static final String SYSTEM_PROMPT = """
            你是一位专业的简历评估专家。请分析以下简历内容，从五个维度评分并提供改进建议。

            评分维度（满分 100）：
            - contentScore (0-25)：内容完整性——教育背景、工作经历、技能列表是否完整
            - structureScore (0-20)：结构清晰度——排版、层次、可读性
            - skillMatchScore (0-25)：技能匹配度——技能描述的具体程度与相关性
            - expressionScore (0-15)：表达专业性——语言是否专业、量化成果
            - projectScore (0-15)：项目经验——项目描述是否清晰、有深度

            输出 JSON 字段：
            - overallScore (int, 0-100)
            - scoreDetail: { contentScore, structureScore, skillMatchScore, expressionScore, projectScore }
            - summary (string)：简历总评
            - strengths (string[])：优点列表
            - suggestions (Suggestion[])：改进建议，每项包含 category, priority(高/中/低), issue, recommendation
            """;

    private final ResumeRepository resumeRepository;
    private final ResumeAnalysisRepository analysisRepository;
    private final DocumentParserService documentParserService;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final StructuredOutputService structuredOutputService;
    private final ResumeKnowledgeService resumeKnowledgeService;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    public ResumeService(ResumeRepository resumeRepository,
                         ResumeAnalysisRepository analysisRepository,
                         DocumentParserService documentParserService,
                         MinioClient minioClient,
                         MinioConfig minioConfig,
                         StructuredOutputService structuredOutputService,
                         ResumeKnowledgeService resumeKnowledgeService,
                         ChatClient.Builder chatClientBuilder,
                         ObjectMapper objectMapper,
                         StringRedisTemplate stringRedisTemplate
    ) {
        this.resumeRepository = resumeRepository;
        this.analysisRepository = analysisRepository;
        this.documentParserService = documentParserService;
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
        this.structuredOutputService = structuredOutputService;
        this.resumeKnowledgeService = resumeKnowledgeService;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> uploadAndAnalyze(MultipartFile file) {
        long start = System.currentTimeMillis();
        String filename = file.getOriginalFilename();

        //文件非空 + ≤10MB + MIME 白名单
        validateFile(file);

        // SHA-256 去重（若命中 → 直接返回历史结果 + accessCount++）
        String fileHash = documentParserService.calculateHash(file);
        Optional<ResumeEntity> existing = resumeRepository.findByFileHash(fileHash);
        if (existing.isPresent()) {
            log.info("检测到重复简历: hash={}, id={}", fileHash, existing.get().getId());
            return handleDuplicate(existing.get());
        }

        // Tika MIME 检测 + 文本提取
        String contentType = documentParserService.detectContentType(file);
        String resumeText = documentParserService.parseContent(file);

        //MinIO 存储（存储URL保存到数据库）
        String storageKey = uploadToMinio(file);

        //  JPA 持久化 
        ResumeEntity entity = new ResumeEntity();
        entity.setFileHash(fileHash);
        entity.setOriginalFilename(filename);
        entity.setFileSize(file.getSize());
        entity.setContentType(contentType);
        entity.setStorageKey(storageKey);
        entity.setStorageUrl(storageKey);
        entity.setResumeText(resumeText);
        ResumeEntity saved = resumeRepository.save(entity);

        log.info("简历已保存: id={}, filename={}, 耗时={}ms",
                saved.getId(), filename, System.currentTimeMillis() - start);

//        // 6. AI analysis (synchronous)
//        ResumeAnalysisDTO analysis = analyzeResume(resumeText);
//
//        // 7. 保存评分结果
//        saveAnalysis(saved, analysis);
//        saved.setAnalyzeStatus("COMPLETED");
//        resumeRepository.save(saved);
//
//        log.info("简历上传 + 分析完成: id={}, score={}, 总耗时={}ms",
//                saved.getId(), analysis.overallScore(), System.currentTimeMillis() - start);
//
//        // RAG 知识库索引（异步友好，但本项目保持同步简化）
//        resumeKnowledgeService.indexResume(saved.getId(), resumeText);

        //--------------------------------
        //异步改造：事务提交后再发 Redis 消息，避免消费端读到未提交的数据
        Long resumeId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // 发一条消息到 resume:tasks，两个消费组各自独立消费
                        stringRedisTemplate.opsForStream()
                                .add(StreamRecords.newRecord()
                                        .in(RedisStreamConfig.STREAM_KEY)
                                        .ofMap(Map.of("resumeId", resumeId.toString())));
                        log.info("简历消息已发送: resumeId={}", resumeId);
                    }
                });

        return Map.of(
                "resumeId", saved.getId(),
                "filename", filename,
                "analyzeStatus", "PENDING",
                "indexStatus", "PENDING"
        );
    }

    public List<ResumeListItemDTO> getAllResumes() {
        return resumeRepository.findAll().stream()
                .map(resume -> {
                    Integer latestScore = null;
                    LocalDateTime lastAnalyzedAt = null;
                    Optional<ResumeAnalysisEntity> analysisOpt =
                           analysisRepository.findFirstByResumeIdOrderByAnalyzedAtDesc(resume.getId());
                    if (analysisOpt.isPresent()) {
                        ResumeAnalysisEntity analysis = analysisOpt.get();
                        latestScore = analysis.getOverallScore();
                        lastAnalyzedAt = analysis.getAnalyzedAt();
                    }
                    return new ResumeListItemDTO(
                            resume.getId(),
                            resume.getOriginalFilename(),
                            resume.getFileSize(),
                            resume.getUploadedAt(),
                            resume.getAccessCount(),
                            latestScore,
                            lastAnalyzedAt,
                            resume.getAnalyzeStatus(),
                            resume.getAnalyzeError()
                    );
                })
                .toList();
    }

    public ResumeDetailDTO getResumeDetail(Long id) {
        ResumeEntity entity = resumeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND, "简历不存在: " + id));

        List<ResumeAnalysisEntity> analyses = analysisRepository.findByResumeIdOrderByAnalyzedAtDesc(id);
        List<ResumeDetailDTO.AnalysisHistoryDTO> history = analyses.stream()
                .map(a -> {
                    List<String> strengths = parseJsonList(a.getStrengthsJson(), new TypeReference<>() {});
                    List<Object> suggestions = parseJsonList(a.getSuggestionsJson(), new TypeReference<>() {});
                    return new ResumeDetailDTO.AnalysisHistoryDTO(
                            a.getId(), a.getOverallScore(),
                            a.getContentScore(), a.getStructureScore(),
                            a.getSkillMatchScore(), a.getExpressionScore(), a.getProjectScore(),
                            a.getSummary(), a.getAnalyzedAt(),
                            strengths, suggestions
                    );
                })
                .toList();

        return new ResumeDetailDTO(
                entity.getId(), entity.getOriginalFilename(),
                entity.getFileSize(), entity.getContentType(),
                entity.getStorageUrl(), entity.getUploadedAt(),
                entity.getAccessCount(), entity.getResumeText(),
                entity.getAnalyzeStatus(), entity.getAnalyzeError(),
                history
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteResume(Long id) {
        ResumeEntity entity = resumeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND, "简历不存在: " + id));
        analysisRepository.deleteByResumeId(id);
        resumeRepository.delete(entity);
        resumeKnowledgeService.deleteResumeIndex(id);
        log.info("简历已删除: id={}, filename={}", id, entity.getOriginalFilename());
    }

    @Transactional(rollbackFor = Exception.class)
    public void reanalyze(Long id) {
        ResumeEntity entity = resumeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND, "简历不存在: " + id));

        String text = entity.getResumeText();
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "简历文本内容为空，无法重新分析");
        }

        entity.setAnalyzeStatus("PENDING");
        entity.setAnalyzeError(null);
        resumeRepository.save(entity);

        ResumeAnalysisDTO analysis = analyzeResume(text);
        saveAnalysis(entity, analysis);

        entity.setAnalyzeStatus("COMPLETED");
        resumeRepository.save(entity);

        log.info("简历已重新分析: id={}, score={}", id, analysis.overallScore());
    }

    public ResumeAnalysisDTO analyzeResume(String resumeText) {
        BeanOutputConverter<ResumeAnalysisDTO> converter = new BeanOutputConverter<>(ResumeAnalysisDTO.class);
        String format = converter.getFormat();
        String systemWithFormat = SYSTEM_PROMPT + "\n\n" + format;

        return structuredOutputService.invoke(
                chatClient,
                systemWithFormat,
                "以下是需要评估的简历内容：\n\n" + resumeText,
                converter,
                "简历分析"
        );
    }

    private void saveAnalysis(ResumeEntity entity, ResumeAnalysisDTO dto) {
        ResumeAnalysisEntity analysis = new ResumeAnalysisEntity();
        analysis.setResume(entity);
        analysis.setOverallScore(dto.overallScore());
        analysis.setContentScore(dto.scoreDetail().contentScore());
        analysis.setStructureScore(dto.scoreDetail().structureScore());
        analysis.setSkillMatchScore(dto.scoreDetail().skillMatchScore());
        analysis.setExpressionScore(dto.scoreDetail().expressionScore());
        analysis.setProjectScore(dto.scoreDetail().projectScore());
        analysis.setSummary(dto.summary());
        try {
            analysis.setStrengthsJson(objectMapper.writeValueAsString(dto.strengths()));
            analysis.setSuggestionsJson(objectMapper.writeValueAsString(dto.suggestions()));
        } catch (Exception e) {
            log.error("持久化分析结果失败", e);
            analysis.setStrengthsJson("[]");
            analysis.setSuggestionsJson("[]");
        }
        analysisRepository.save(analysis);
    }

    private String uploadToMinio(MultipartFile file) {
        String key = "resumes/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(key)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.info("文件已上传到 MinIO: bucket={}, key={}", minioConfig.getBucket(), key);
            return key;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.RESUME_UPLOAD_FAILED, "MinIO 上传失败: " + e.getMessage());
        }
    }

    private Map<String, Object> handleDuplicate(ResumeEntity existing) {
        existing.incrementAccessCount();
        resumeRepository.save(existing);

        Optional<ResumeAnalysisEntity> latest = analysisRepository.findFirstByResumeIdOrderByAnalyzedAtDesc(existing.getId());
        boolean hasAnalysis =
                latest.isPresent() &&
                        "COMPLETED".equals(existing.getAnalyzeStatus());;

        Map<String, Object> result = new HashMap<>();

        result.put("resumeId", existing.getId());
        result.put("filename", existing.getOriginalFilename());
        result.put("analysis",
                hasAnalysis ? buildAnalysisFromEntity(latest.get()) : null);
        result.put("duplicate", true);

        return result;
    }

    private ResumeAnalysisDTO buildAnalysisFromEntity(ResumeAnalysisEntity entity) {
        List<String> strengths = parseJsonList(entity.getStrengthsJson(), new TypeReference<>() {});
        List<ResumeAnalysisDTO.SuggestionDTO> suggestions = parseJsonList(entity.getSuggestionsJson(), new TypeReference<>() {});
        return new ResumeAnalysisDTO(
                entity.getOverallScore(),
                new ResumeAnalysisDTO.ScoreDetailDTO(
                        entity.getContentScore(), entity.getStructureScore(),
                        entity.getSkillMatchScore(), entity.getExpressionScore(), entity.getProjectScore()),
                entity.getSummary(), strengths, suggestions
        );
    }

    private <T> T parseJsonList(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            json = "[]";
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("转换JSON字段失败，返回空列表: {}", e.getMessage());
            try {
                return objectMapper.readValue("[]", typeRef);
            } catch (Exception ex) {
                throw new RuntimeException("转换默认JSON字段失败", ex);
            }
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.RESUME_UPLOAD_FAILED, "文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.RESUME_FILE_TOO_LARGE,
                    "文件大小超过限制: " + file.getSize() + " > " + MAX_FILE_SIZE);
        }
        String detectedType = documentParserService.detectContentType(file);
        if (!ALLOWED_TYPES.contains(detectedType)) {
            throw new BusinessException(ErrorCode.RESUME_FILE_TYPE_INVALID,
                    "不支持的文件类型: " + detectedType);
        }
    }
}
