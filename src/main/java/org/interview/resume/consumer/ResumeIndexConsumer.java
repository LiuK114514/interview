package org.interview.resume.consumer;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.interview.config.RedisStreamConfig;
import org.interview.resume.entity.ResumeEntity;
import org.interview.resume.repository.ResumeRepository;
import org.interview.resume.service.ResumeKnowledgeService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResumeIndexConsumer implements InitializingBean {

    private final StringRedisTemplate redisTemplate;
    private final ResumeKnowledgeService resumeKnowledgeService;
    private final ResumeRepository resumeRepository;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "resume-index-consumer");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void afterPropertiesSet() {
        executor.submit(() -> {
            try {
                new IndexTask().run();
            } catch (Throwable t) {
                log.error("IndexTask 线程因未捕获异常退出，将不再处理新消息", t);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    private class IndexTask implements Runnable {
        @Override
        public void run() {
            log.info("index-consumer 启动");
            while (!Thread.currentThread().isInterrupted()) {
                try {

                    List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(
                            Consumer.from(RedisStreamConfig.INDEX_GROUP_NAME, "index-consumer"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(5)),
                            StreamOffset.create(RedisStreamConfig.STREAM_KEY, ReadOffset.lastConsumed()));
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Object idVal = record.getValue().get("resumeId");
                    log.info("index-consumer 收到消息: {}", record.getValue());
                    if (idVal == null) {
                        log.warn("消息缺少 resumeId，跳过并确认: recordId={}", record.getId());
                        redisTemplate.opsForStream().acknowledge(
                                RedisStreamConfig.STREAM_KEY, RedisStreamConfig.INDEX_GROUP_NAME, record.getId());
                        continue;
                    }
                    Long resumeId = Long.valueOf(idVal.toString());
                    resumeKnowledgeService.processIndex(resumeId);
                    redisTemplate.opsForStream().acknowledge(
                            RedisStreamConfig.STREAM_KEY, RedisStreamConfig.INDEX_GROUP_NAME, record.getId());
                } catch (Exception e) {
                    log.error("向量化消费异常", e);
                    handlePendingList();
                }
            }
        }
    }

    private void handlePendingList() {
        int retries = 0;
        int maxRetries = 3;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(
                        Consumer.from(RedisStreamConfig.INDEX_GROUP_NAME, "index-consumer"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(RedisStreamConfig.STREAM_KEY, ReadOffset.from("0")));
                if (list == null || list.isEmpty()) {
                    break;
                }
                MapRecord<String, Object, Object> record = list.get(0);
                Object idVal = record.getValue().get("resumeId");
                if (idVal == null) {
                    log.warn("pending 消息缺少 resumeId，跳过并确认: recordId={}", record.getId());
                    redisTemplate.opsForStream().acknowledge(
                            RedisStreamConfig.STREAM_KEY, RedisStreamConfig.INDEX_GROUP_NAME, record.getId());
                    continue;
                }
                Long resumeId = Long.valueOf(idVal.toString());
                resumeKnowledgeService.processIndex(resumeId);
                redisTemplate.opsForStream().acknowledge(
                        RedisStreamConfig.STREAM_KEY, RedisStreamConfig.INDEX_GROUP_NAME, record.getId());
                retries = 0;
            } catch (Exception e) {
                retries++;
                if (retries > maxRetries) {
                    log.error("重试{}次仍失败，移入死信队列: {}", maxRetries, e.getMessage());
                    try {
                        List<MapRecord<String, Object, Object>> pending = redisTemplate.opsForStream().read(
                                Consumer.from(RedisStreamConfig.INDEX_GROUP_NAME, "index-consumer"),
                                StreamReadOptions.empty().count(1),
                                StreamOffset.create(RedisStreamConfig.STREAM_KEY, ReadOffset.from("0")));
                        if (pending != null && !pending.isEmpty()) {
                            MapRecord<String, Object, Object> failed = pending.get(0);
                            redisTemplate.opsForStream().add(
                                    StreamRecords.newRecord()
                                            .in(RedisStreamConfig.DLQ_KEY)
                                            .ofMap(failed.getValue()));
                            redisTemplate.opsForStream().acknowledge(
                                    RedisStreamConfig.STREAM_KEY, RedisStreamConfig.INDEX_GROUP_NAME, failed.getId());
                            log.info("已确认原消息并移入 DLQ: recordId={}", failed.getId());
                        }
                    } catch (Exception dlqEx) {
                        log.error("移入死信队列失败", dlqEx);
                    }
                    break;
                }
                log.warn("第{}次重试失败，即将重试: {}", retries, e.getMessage());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
    }
}
