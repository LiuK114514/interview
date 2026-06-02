package org.interview.resume.service;

import org.interview.resume.entity.ResumeChunkEntity;
import org.interview.resume.entity.ResumeEntity;
import org.interview.resume.repository.ResumeChunkRepository;
import org.interview.resume.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ResumeKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeKnowledgeService.class);

    private static final int CHUNK_SIZE = 300;
    private static final int CHUNK_OVERLAP = 50;
    private static final Duration EMBEDDING_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_TEXT_LENGTH = 100_000;
    private static final ExecutorService embedExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "embed-call");
        t.setDaemon(true);
        return t;
    });

    private final EmbeddingModel embeddingModel;
    private final ResumeChunkRepository chunkRepository;
    private final JdbcTemplate jdbcTemplate;
    private final boolean pgvectorAvailable;
    private final ResumeRepository resumeRepository;
    private final TransactionTemplate transactionTemplate;

    public ResumeKnowledgeService(EmbeddingModel embeddingModel,
                                  ResumeChunkRepository chunkRepository,
                                  JdbcTemplate jdbcTemplate,
                                  Environment environment,
                                  ResumeRepository resumeRepository,
                                  PlatformTransactionManager transactionManager) {
        this.embeddingModel = embeddingModel;
        this.chunkRepository = chunkRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.pgvectorAvailable = !Arrays.asList(environment.getActiveProfiles()).contains("h2");
        this.resumeRepository = resumeRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 对简历进行分块 → 向量化 → 存储。
     * 幂等操作：先删除该简历的旧分块再重建。
     */
    public void indexResume(Long resumeId, String resumeText) {

        if (resumeText == null || resumeText.isBlank()) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            chunkRepository.deleteByResumeId(resumeId);
            chunkRepository.flush();
        });

        if (resumeText.length() > MAX_TEXT_LENGTH) {
            resumeText = resumeText.substring(0, MAX_TEXT_LENGTH);
        }

        int chunkIndex = 0;

        for (String chunk : streamChunks(resumeText, CHUNK_SIZE, CHUNK_OVERLAP)) {

            processChunk(resumeId, chunkIndex++, chunk);
        }

        log.info("简历知识库索引完成: resumeId={}, chunks={}",
                resumeId,
                chunkIndex);
    }

    /**
     * 删除简历的知识库索引
     */
    @Transactional
    public void deleteResumeIndex(Long resumeId) {
        chunkRepository.deleteByResumeId(resumeId);
        log.info("简历知识库索引已删除: resumeId={}", resumeId);
    }

    /**
     * 检索与查询最相关的简历上下文片段
     *
     * @param resumeId 简历 ID
     * @param query    搜索查询（如面试方向、技术栈）
     * @param limit    返回结果数
     * @return 最相关的文本片段列表
     */
    public List<String> searchRelevantContext(Long resumeId, String query, int limit) {
        if (!pgvectorAvailable) {
            // H2 环境下退化为全文检索
            return chunkRepository.findByResumeIdOrderByChunkIndex(resumeId)
                    .stream()
                    .map(ResumeChunkEntity::getChunkText)
                    .collect(Collectors.toList())
                    .stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        float[] queryVec = embed(query);
        String vecStr = vectorToString(queryVec);

        String sql = """
                SELECT chunk_text
                FROM resume_chunks
                WHERE resume_id = ? AND embedding IS NOT NULL
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> rs.getString("chunk_text"),
                resumeId, vecStr, limit);
    }

    // ========== 私有方法 ==========

   private Iterable<String> streamChunks(CharSequence text, int chunkSize, int chunkOverlap) {
    return () -> new Iterator<>() {
        int start = 0;
        int len = text.length();
        int nextChar = 0;

        @Override
        public boolean hasNext() {
            return start < len;
        }

        @Override
        public String next() {
            if (!hasNext()) throw new NoSuchElementException();
            int end = Math.min(start + chunkSize, len);
            int lastSpace = -1;
            int lastNewline = -1;
            // 找自然断点
            for (int i = start; i < end; i++) {
                char c = text.charAt(i);
                if (c == ' ' || c == '\n') {
                    lastSpace = i;
                    if (c == '\n') lastNewline = i;
                }
            }
            int lastBreak = Math.max(lastSpace, lastNewline);
            if (lastBreak >= start + chunkSize / 2) {
                end = lastBreak;
            }
            // trim 左右空格
            int chunkStart = start;
            int chunkEnd = end;
            while (chunkStart < chunkEnd && Character.isWhitespace(text.charAt(chunkStart))) chunkStart++;
            while (chunkEnd > chunkStart && Character.isWhitespace(text.charAt(chunkEnd - 1))) chunkEnd--;

            String chunk = text.subSequence(chunkStart, chunkEnd).toString();

            // 步进量
            int next = end - chunkOverlap;
            if (next <= start) next = start + 1;
            start = next;

            return chunk;
            }
        };
    }
   
    private float[] embed(String text) {
        try {
            return CompletableFuture.supplyAsync(() ->
                            embeddingModel.call(new EmbeddingRequest(List.of(text), null)), embedExecutor)
                    .orTimeout(EMBEDDING_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .join()
                    .getResult().getOutput();
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                throw new RuntimeException("Embedding API 超时 (" + EMBEDDING_TIMEOUT.getSeconds() + "s)", e.getCause());
            }
            throw new RuntimeException("Embedding API 调用失败", e.getCause());
        }
    }

    private void processChunk(
            Long resumeId,
            int chunkIndex,
            String chunk) {

        float[] vec = null;

        try {
            vec = embed(chunk);
        } catch (Exception e) {
            log.warn("第{}个切片向量化失败: {}",
                    chunkIndex,
                    e.getMessage());
        }
        final float[] finalVec = vec;
        transactionTemplate.executeWithoutResult(status -> {
            ResumeChunkEntity entity =
                    new ResumeChunkEntity();
            entity.setResumeId(resumeId);
            entity.setChunkIndex(chunkIndex);
            entity.setChunkText(chunk);
            chunkRepository.save(entity);
            chunkRepository.flush();
            if (finalVec != null) {
                jdbcTemplate.update(
                        "UPDATE resume_chunks " +
                                "SET embedding=?::vector " +
                                "WHERE id=?",
                        vectorToString(finalVec),
                        entity.getId());
            }
        });
    }

    private String vectorToString(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    @Transactional(rollbackFor = Exception.class)
    public void processIndex(Long resumeId) {
        System.err.println(">>> [processIndex] resumeId=" + resumeId);
        log.info("处理索引");
        ResumeEntity resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new RuntimeException("简历不存在"));
        resume.setIndexStatus("PROCESSING");
        resumeRepository.save(resume);
        indexResume(resumeId, resume.getResumeText());
        resume.setIndexStatus("COMPLETED");
        resumeRepository.save(resume);
        log.info("简历向量化完成: {}", resumeId);
    }
}
