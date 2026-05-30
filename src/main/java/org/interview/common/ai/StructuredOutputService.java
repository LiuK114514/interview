package org.interview.common.ai;

import org.interview.common.exception.BusinessException;
import org.interview.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

@Service
public class StructuredOutputService {

    private static final int MAX_ATTEMPTS = 2;
    private static final String STRICT_JSON_INSTRUCTION = """
            请仅返回可被 JSON 解析器直接解析的 JSON 对象，并严格满足字段结构要求：
            1) 不要输出 Markdown 代码块（如 ```json）。
            2) 不要输出任何解释文字、前后缀、注释。
            3) 所有字符串内引号必须正确转义。
            """;
    private String buildInitialPrompt(String systemPrompt) {
        return systemPrompt + "\n\n" + STRICT_JSON_INSTRUCTION;
    }

    public <T> T invoke(
            ChatClient chatClient,
            String systemPrompt,
            String userPrompt,
            BeanOutputConverter<T> converter,
            String taskName) {

        Logger log = LoggerFactory.getLogger(getClass());
        Exception lastError = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
           String attemptPrompt = attempt == 1
                ? buildInitialPrompt(systemPrompt)   // 首次也带上严格 JSON 指令
                : buildRetryPrompt(systemPrompt, lastError);
            try {
                String reply = chatClient.prompt()
                        .system(attemptPrompt)
                        .user(userPrompt)
                        .call()
                        .content();

                return convertWithRepair(reply, converter, taskName, log);
            } catch (Exception e) {
                lastError = e;
                if (attempt < MAX_ATTEMPTS) {
                    log.warn("{} 解析失败，准备重试: attempt={}/{} error={}",
                            taskName, attempt, MAX_ATTEMPTS, e.getMessage());
                } else {
                    log.error("{} 解析失败，已达最大重试次数", taskName);
                }
            }
        }

        throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT,
                taskName + " 结构化输出解析失败: " + (lastError != null ? lastError.getMessage() : "未知错误"));
    }

    private <T> T convertWithRepair(
            String content,
            BeanOutputConverter<T> converter,
            String taskName,
            Logger log) {
        try {
            return converter.convert(content);
        } catch (Exception firstError) {
            String repaired = repairUnescapedQuotes(content);
            if (!repaired.equals(content)) {
                try {
                    T result = converter.convert(repaired);
                    log.warn("{} JSON 存在未转义引号，已在本地修复后解析成功", taskName);
                    return result;
                } catch (Exception repairError) {
                    firstError.addSuppressed(repairError);
                }
            }
            throw firstError;
        }
    }

    private String repairUnescapedQuotes(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        StringBuilder sb = new StringBuilder(content.length() + 16);
        boolean inString = false;
        boolean escaping = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);

            if (!inString) {
                if (ch == '"') {
                    inString = true;
                }
                sb.append(ch);
                continue;
            }

            if (escaping) {
                sb.append(ch);
                escaping = false;
                continue;
            }

            if (ch == '\\') {
                sb.append(ch);
                escaping = true;
                continue;
            }

            if (ch == '"') {
                if (isJsonTerminator(content, i + 1)) {
                    inString = false;
                    sb.append(ch);
                } else {
                    sb.append("\\\"");
                }
                continue;
            }

            sb.append(ch);
        }
        return sb.toString();
    }

    private boolean isJsonTerminator(String content, int start) {
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == ',' || c == '}' || c == ']' || c == ':';
        }
        return true;
    }

    private String buildRetryPrompt(String originalPrompt, Exception lastError) {
        StringBuilder prompt = new StringBuilder(originalPrompt)
                .append("\n\n")
                .append(STRICT_JSON_INSTRUCTION)
                .append("\n上次输出解析失败，请仅返回合法 JSON。");

        if (lastError != null && lastError.getMessage() != null) {
            prompt.append("\n上次失败原因：").append(lastError.getMessage());
        }
        return prompt.toString();
    }
}
