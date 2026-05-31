package org.interview.common.exception;

public enum ErrorCode {

    BAD_REQUEST(400, "请求参数错误"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 简历域 2xxx
    RESUME_NOT_FOUND(2001, "简历不存在"),
    RESUME_UPLOAD_FAILED(2002, "简历上传失败"),
    RESUME_PARSE_FAILED(2003, "简历解析失败"),
    RESUME_ANALYSIS_FAILED(2004, "简历AI评分失败"),
    RESUME_FILE_TOO_LARGE(2005, "简历文件过大"),
    RESUME_FILE_TYPE_INVALID(2006, "不支持的文件类型"),

    AI_SERVICE_TIMEOUT(7002, "AI服务响应超时或解析失败"),
    AI_SERVICE_UNAVAILABLE(7001, "AI服务暂时不可用"),
    AI_API_KEY_INVALID(7004, "AI服务密钥无效"),
    AI_RATE_LIMIT_EXCEEDED(7005, "AI服务调用频率超限"),
    AI_SERVICE_ERROR(7003, "AI服务调用失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
