package org.interview.resume.controller;

import org.interview.common.result.Result;
import org.interview.resume.dto.DlqMessageDTO;
import org.interview.resume.service.DlqService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dlq")
public class DlqController {

    private final DlqService dlqService;

    public DlqController(DlqService dlqService) {
        this.dlqService = dlqService;
    }

    @GetMapping
    public Result<List<DlqMessageDTO>> listMessages() {
        return Result.success(dlqService.listMessages());
    }

    @PostMapping("/{recordId}/retry")
    public Result<Map<String, Object>> retryMessage(@PathVariable String recordId) {
        boolean ok = dlqService.retryMessage(recordId);
        return ok
                ? Result.success(Map.of("message", "重试成功"))
                : Result.error(500, "重试失败，消息不存在或已被处理");
    }

    @PostMapping("/retry-all")
    public Result<Map<String, Object>> retryAll() {
        int count = dlqService.retryAll();
        return Result.success(Map.of("count", count, "message", "已重试 " + count + " 条消息"));
    }

    @DeleteMapping("/{recordId}")
    public Result<Map<String, Object>> deleteMessage(@PathVariable String recordId) {
        boolean ok = dlqService.deleteMessage(recordId);
        return ok
                ? Result.success(Map.of("message", "已删除"))
                : Result.error(500, "删除失败，消息不存在");
    }

    @DeleteMapping
    public Result<Map<String, Object>> clearAll() {
        int count = dlqService.clearAll();
        return Result.success(Map.of("count", count, "message", "已清空 " + count + " 条消息"));
    }
}
