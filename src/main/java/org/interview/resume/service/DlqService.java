package org.interview.resume.service;

import org.interview.config.RedisStreamConfig;
import org.interview.resume.dto.DlqMessageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DlqService {

    private static final Logger log = LoggerFactory.getLogger(DlqService.class);

    private final StringRedisTemplate redisTemplate;

    public DlqService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<DlqMessageDTO> listMessages() {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(RedisStreamConfig.DLQ_KEY, Range.unbounded());

        List<DlqMessageDTO> messages = new ArrayList<>();
        if (records == null) return messages;

        for (MapRecord<String, Object, Object> record : records) {
            Map<Object, Object> value = record.getValue();
            Object idVal = value.get("resumeId");
            Long resumeId = idVal != null ? Long.valueOf(idVal.toString()) : null;
            messages.add(new DlqMessageDTO(
                    record.getId().toString(),
                    resumeId,
                    record.getId().getTimestamp() > 0
                            ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .format(new java.util.Date(record.getId().getTimestamp()))
                            : null
            ));
        }
        return messages;
    }

    public boolean retryMessage(String recordId) {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .range(RedisStreamConfig.DLQ_KEY, Range.closed(recordId, recordId));
            if (records == null || records.isEmpty()) {
                log.warn("DLQ 消息不存在: recordId={}", recordId);
                return false;
            }

            MapRecord<String, Object, Object> record = records.get(0);
            redisTemplate.opsForStream().add(
                    StreamRecords.newRecord()
                            .in(RedisStreamConfig.STREAM_KEY)
                            .ofMap(record.getValue()));
            redisTemplate.opsForStream().delete(
                    RedisStreamConfig.DLQ_KEY, record.getId());
            log.info("DLQ 消息重试成功: recordId={}", recordId);
            return true;
        } catch (Exception e) {
            log.error("DLQ 消息重试失败: recordId={}", recordId, e);
            return false;
        }
    }

    public int retryAll() {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(RedisStreamConfig.DLQ_KEY, Range.unbounded());
        if (records == null) return 0;

        int count = 0;
        for (MapRecord<String, Object, Object> record : records) {
            try {
                redisTemplate.opsForStream().add(
                        StreamRecords.newRecord()
                                .in(RedisStreamConfig.STREAM_KEY)
                                .ofMap(record.getValue()));
                redisTemplate.opsForStream().delete(
                        RedisStreamConfig.DLQ_KEY, record.getId());
                count++;
            } catch (Exception e) {
                log.error("DLQ 全部重试时失败: recordId={}", record.getId(), e);
            }
        }
        log.info("DLQ 全部重试完成: count={}", count);
        return count;
    }

    public boolean deleteMessage(String recordId) {
        try {
            Long result = redisTemplate.opsForStream().delete(
                    RedisStreamConfig.DLQ_KEY, recordId);
            boolean deleted = result != null && result > 0;
            if (deleted) {
                log.info("DLQ 消息已删除: recordId={}", recordId);
            }
            return deleted;
        } catch (Exception e) {
            log.error("DLQ 消息删除失败: recordId={}", recordId, e);
            return false;
        }
    }

    public int clearAll() {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(RedisStreamConfig.DLQ_KEY, Range.unbounded());
        if (records == null) return 0;

        int count = 0;
        for (MapRecord<String, Object, Object> record : records) {
            try {
                redisTemplate.opsForStream().delete(
                        RedisStreamConfig.DLQ_KEY, record.getId());
                count++;
            } catch (Exception e) {
                log.error("DLQ 清理时失败: recordId={}", record.getId(), e);
            }
        }
        log.info("DLQ 已清空: count={}", count);
        return count;
    }
}
