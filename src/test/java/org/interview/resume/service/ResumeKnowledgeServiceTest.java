package org.interview.resume.service;

import org.interview.resume.entity.ResumeChunkEntity;
import org.interview.resume.repository.ResumeChunkRepository;
import org.interview.resume.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ResumeKnowledgeService.indexResume 单元测试。
 *
 * <p>模拟 H2 profile（environment.getActiveProfiles() → ["h2"]），
 * 使 pgvectorAvailable=false，走纯文本分块→存储路径。
 * 注意：测试文本长度需 > CHUNK_OVERLAP(50) 避免 chunkText 边界 bug。
 */
@DisplayName("ResumeKnowledgeService.indexResume")
@ExtendWith(MockitoExtension.class)
class ResumeKnowledgeServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private ResumeChunkRepository chunkRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Environment environment;

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private PlatformTransactionManager ptm;

    private ResumeKnowledgeService service;

    @Captor
    private ArgumentCaptor<ResumeChunkEntity> chunkCaptor;

    @BeforeEach
    void setUp() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"h2"});
        when(ptm.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        service = new ResumeKnowledgeService(embeddingModel, chunkRepository,
                jdbcTemplate, environment, resumeRepository, ptm);
    }

    @Test
    @DisplayName("正常分块：文本被拆分并保存，旧分块被删除")
    void indexResume_shouldCreateChunks() {
        String text = "张三，Java开发工程师，8年经验。" +
                "精通Spring Boot、Spring Cloud、MyBatis等框架。" +
                "熟悉MySQL、Redis、Elasticsearch。" +
                "有微服务架构设计和分布式系统开发经验。";

        service.indexResume(5L, text);

        verify(chunkRepository).deleteByResumeId(5L);
        verify(chunkRepository, atLeastOnce()).save(any(ResumeChunkEntity.class));
    }

    @Test
    @DisplayName("null 文本：跳过，不操作数据库")
    void indexResume_shouldSkipNullText() {
        service.indexResume(5L, null);

        verify(chunkRepository, never()).deleteByResumeId(anyLong());
        verify(chunkRepository, never()).save(any());
    }

    @Test
    @DisplayName("空白文本：跳过，不操作数据库")
    void indexResume_shouldSkipBlankText() {
        service.indexResume(5L, "   ");

        verify(chunkRepository, never()).deleteByResumeId(anyLong());
        verify(chunkRepository, never()).save(any());
    }

    @Test
    @DisplayName("幂等性：再次索引先删旧分块再存新分块")
    void indexResume_shouldDeleteThenInsert() {
        service.indexResume(5L, "Java开发工程师。" +
                "熟练掌握Spring框架。");
        service.indexResume(5L, "Python开发工程师。" +
                "熟练掌握Django框架。");

        verify(chunkRepository, times(2)).deleteByResumeId(5L);
    }

    @Test
    @DisplayName("长文本：分块索引连续，每块内容完整")
    void indexResume_shouldProduceOrderedChunks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("这是第").append(i).append("段。Spring Boot是一个优秀的Java框架。");
        }
        String longText = sb.toString();

        service.indexResume(5L, longText);

        verify(chunkRepository, atLeast(2)).save(chunkCaptor.capture());
        List<ResumeChunkEntity> chunks = chunkCaptor.getAllValues();

        assertTrue(chunks.size() >= 2, "长文本应产生至少2个分块");

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getChunkIndex(), "分块索引应连续");
            assertEquals(5L, chunks.get(i).getResumeId());
            assertNotNull(chunks.get(i).getChunkText());
            assertFalse(chunks.get(i).getChunkText().isBlank());
            assertTrue(chunks.get(i).getChunkText().length() < 600,
                    "每个分块应小于600字符");
        }
    }

    @Test
    @DisplayName("短文本（>50字符）：应只产生1个分块")
    void indexResume_shouldProduceSingleChunkForShortText() {
        service.indexResume(5L, "简短但超过50字符的简历文本。".repeat(3));

        verify(chunkRepository).save(chunkCaptor.capture());
        assertEquals(0, chunkCaptor.getValue().getChunkIndex());
        assertEquals(5L, chunkCaptor.getValue().getResumeId());
    }

    @Test
    @DisplayName("文本恰好在边界附近（略大于 CHUNK_SIZE）：应正确处理")
    void indexResume_shouldHandleTextJustAboveChunkSize() {
        // 生成一个略大于 CHUNK_SIZE(300) 字符的文本
        String text = "A".repeat(310);

        service.indexResume(5L, text);

        verify(chunkRepository, atLeastOnce()).save(any(ResumeChunkEntity.class));
    }
}
