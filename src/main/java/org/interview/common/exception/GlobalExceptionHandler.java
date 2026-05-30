package org.interview.common.exception;

import org.interview.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    @ExceptionHandler(RestClientException.class)
    public Result<Void> handleAiException(RestClientException e) {
        log.error("AI服务调用失败: {}", e.getMessage());

        String msg = e.getMessage();
        if (msg != null) {
            if (msg.contains("401") || msg.contains("Unauthorized")) {
                return Result.error(ErrorCode.AI_API_KEY_INVALID.getCode(), "API Key 无效，请检查配置");
            }
            if (msg.contains("429") || msg.contains("Too Many Requests")) {
                return Result.error(ErrorCode.AI_RATE_LIMIT_EXCEEDED.getCode(), "请求过于频繁，请稍后重试");
            }
        }
        return Result.error(ErrorCode.AI_SERVICE_ERROR.getCode(), "AI服务暂时不可用");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ErrorCode.INTERNAL_ERROR.getCode(), "系统繁忙，请稍后重试");
    }
}
