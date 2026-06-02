package org.interview.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;
import java.util.Map;

@Configuration
public class RedisStreamConfig {

    /** 简历任务 Stream（分析 + 索引共享） */
    public static final String STREAM_KEY = "resume:tasks";
    /** 分析消费组 */
    public static final String ANALYSIS_GROUP_NAME = "resume-analysis-group";
    /** 索引消费组 */
    public static final String INDEX_GROUP_NAME = "resume-index-group";
    public static final String DLQ_KEY    = "resume:dlq";

    public static final String GROUP_NAME = ANALYSIS_GROUP_NAME; // 兼容旧引用

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void initStream() {
        initGroup(STREAM_KEY, ANALYSIS_GROUP_NAME);
        initGroup(STREAM_KEY, INDEX_GROUP_NAME);
    }

    private void initGroup(String stream, String group) {
        try {
            stringRedisTemplate.opsForStream()
                    .createGroup(stream, ReadOffset.latest(), group);
        } catch (Exception e) {
            String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            if (cause == null || !cause.contains("BUSYGROUP")) {
                throw new RuntimeException("Redis Stream Group 初始化失败: " + stream + "/" + group, e);
            }
        }
    }
}