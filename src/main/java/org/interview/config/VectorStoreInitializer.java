package org.interview.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 启动时初始化 pgvector 扩展和 resume_chunks 的向量列。
 * Hibernate 的 ddl-auto: update 不感知 pgvector 类型，需手动迁移。
 * H2 环境自动跳过。
 */
@Component
public class VectorStoreInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final boolean pgvectorAvailable;

    public VectorStoreInitializer(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.pgvectorAvailable = !Arrays.asList(environment.getActiveProfiles()).contains("h2");
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!pgvectorAvailable) {
            log.info("H2 环境，跳过 pgvector 初始化");
            return;
        }
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS resume_chunks ADD COLUMN IF NOT EXISTS embedding vector(1024)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chunks_embedding ON resume_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10)");
            log.info("pgvector 扩展和 resume_chunks.embedding 列已就绪");
        } catch (Exception e) {
            log.warn("pgvector 初始化失败（可忽略，后续写入时会重试）: {}", e.getMessage());
        }
    }
}
