# 已掌握的 Spring AI 知识

## ChatClient 调用链

```
chatClient.prompt()
    .system("system prompt")     // 系统角色设定
    .user("用户消息")             // 用户输入
    .advisors(a -> ...)          // 注入横切能力（记忆、安全、日志等）
    .call()                      // 同步调用
    .content()                   // 获取文本回复
```

## 多轮对话机制

- `MessageWindowChatMemory`：保留最近 N 条消息的窗口式记忆
- `MessageChatMemoryAdvisor`：在每次调用时自动注入历史消息
- `chat_memory_conversation_id`：按会话 ID 隔离不同对话的上下文
- 持久化方案：`JpaChatMemoryRepository` 实现 `ChatMemoryRepository` 接口，替代内存窗口

## 结构化输出

```java
BeanOutputConverter<MessageAnalysis> converter =
    new BeanOutputConverter<>(MessageAnalysis.class);
// converter.getFormat() 自动生成 JSON Schema 说明，注入 system prompt
// converter.convert(reply) 自动将 AI 回复的 JSON 解析为 Java 对象
```

## 手动配置 vs 自动配置

| 方式 | 原理 | 适用场景 |
|------|------|---------|
| 自动配置（P1-P4） | spring.ai.openai.* → 自动创建 Bean | 快速原型，单一 Provider |
| 手动配置（P5） | AiConfig → OpenAiChatModel → ChatClient | 多 Provider、自定义超时/Header、生产环境 |

## 结构化输出重试模式（Phase 6）

```
StructuredOutputService.invoke()
    │
    ├─ 第 1 次调用 AI → BeanOutputConverter.convert()
    │     ├─ 成功 → 返回结果
    │     └─ 失败 → repairUnescapedQuotes() 本地修复
    │           ├─ 修复后成功 → 返回结果（日志警告）
    │           └─ 修复后失败 → 进入重试
    │
    └─ 第 2 次调用 AI（带错误反馈的 system prompt）
          └─ BeanOutputConverter.convert()
                ├─ 成功 → 返回结果
                └─ 失败 → BusinessException(AI_SERVICE_TIMEOUT)

重试时注入的额外 prompt：
- STRICT_JSON_INSTRUCTION：严格 JSON 约束（无 ```json、无注释）
- 上次解析失败原因
```
