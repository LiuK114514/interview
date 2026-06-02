package org.interview.resume.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.interview.resume.dto.ResumeAnalysisDTO;
import org.interview.resume.entity.ResumeAnalysisEntity;
import org.interview.resume.entity.ResumeEntity;
import org.interview.resume.repository.ResumeAnalysisRepository;
import org.interview.resume.repository.ResumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeAnalysisService {

    private final ResumeRepository resumeRepository;
    private final ResumeAnalysisRepository analysisRepository;
    private final ResumeService resumeService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void processAnalysis(Long resumeId) {
        log.info("处理分析");
        ResumeEntity resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new RuntimeException("简历不存在: " + resumeId));

        resume.setAnalyzeStatus("PROCESSING");
        resumeRepository.save(resume);

        ResumeAnalysisDTO dto = resumeService.analyzeResume(resume.getResumeText());

        ResumeAnalysisEntity entity = new ResumeAnalysisEntity();
        entity.setResume(resume);
        entity.setOverallScore(dto.overallScore());
        entity.setContentScore(dto.scoreDetail().contentScore());
        entity.setStructureScore(dto.scoreDetail().structureScore());
        entity.setSkillMatchScore(dto.scoreDetail().skillMatchScore());
        entity.setExpressionScore(dto.scoreDetail().expressionScore());
        entity.setProjectScore(dto.scoreDetail().projectScore());
        entity.setSummary(dto.summary());
        try {
            entity.setStrengthsJson(objectMapper.writeValueAsString(dto.strengths()));
            entity.setSuggestionsJson(objectMapper.writeValueAsString(dto.suggestions()));
        } catch (Exception e) {
            log.warn("序列化分析详情失败", e);
            entity.setStrengthsJson("[]");
            entity.setSuggestionsJson("[]");
        }
        analysisRepository.save(entity);

        resume.setAnalyzeStatus("COMPLETED");
        resumeRepository.save(resume);

        log.info("简历分析完成: resumeId={}, score={}", resumeId, dto.overallScore());
    }
}
