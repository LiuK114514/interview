package org.interview.resume.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.interview.resume.repository.ResumeChunkRepository;
import org.interview.resume.repository.ResumeRepository;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class BeforeEachTest {
    @Mock EmbeddingModel embeddingModel;
    @Mock ResumeChunkRepository chunkRepository;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock Environment environment;
    @Mock ResumeRepository resumeRepository;
    @Mock PlatformTransactionManager ptm;
    private ResumeKnowledgeService service;
    @BeforeEach
    void setUp() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"h2"});
        when(ptm.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        service = new ResumeKnowledgeService(embeddingModel, chunkRepository,
                jdbcTemplate, environment, resumeRepository, ptm);
    }
    @Test void t1() { service.indexResume(5L, "123456789012345678901234567890123456789012345678901234567890"); }  // > 50 chars
    @Test void t2() { service.indexResume(5L, "text that is long enough to avoid string index issues in the system"); }
}
