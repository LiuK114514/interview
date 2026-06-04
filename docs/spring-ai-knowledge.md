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

### 流式调用（SSE）

```
chatClient.prompt()
    .user("用户消息")
    .advisors(a -> ...)
    .stream()                    // 流式调用 ← 区别在这里
    .content()                   // 返回 Flux<String>
    .subscribe(
        chunk -> { /* 逐块处理 */ },
        error -> { /* 异常处理 */ },
        () -> { /* 完成回调 */ }
    );
```

**与同步调用的区别：**

| 维度 | `.call().content()` | `.stream().content()` |
|------|-------------------|---------------------|
| 返回类型 | `String`（完整文本） | `Flux<String>`（反应式流） |
| 响应时间 | 等待 LLM 完整生成后才返回 | 边生成边推送，首字延迟低 |
| 适用场景 | 非实时（分析、评估） | 实时对话、聊天 |

## SSE 服务端推送模式

在 `ChatController` 中使用的 `SseEmitter` 模式：

```
@PostMapping("/api/chat/stream")
public SseEmitter chatStream(@RequestBody ChatRequest request) {
    SseEmitter emitter = new SseEmitter(300_000L);  // 5min 超时

    chatClient.prompt()
            .user(request.message())
            .stream().content()
            .subscribe(
                chunk -> emitter.send(SseEmitter.event()
                    .name("message").data(chunk)),   // 逐字推送
                error -> emitter.send(SseEmitter.event()
                    .name("error").data(...)),         // 异常通知
                () -> emitter.send(SseEmitter.event()
                    .name("done").data(sessionId))     // 完成信号
            );

    return emitter;  // 立即返回，异步推送
}
```

**三个命名事件协议：**
- `event: message` / `data: <逐字内容>` — AI 回复的实时片段
- `event: done` / `data: <sessionId>` — 流结束信号
- `event: error` / `data: <错误消息>` — 异常通知

**前端消费模式（fetch + ReadableStream）：**

```
const res = await fetch('/api/chat/stream', { method: 'POST', body: ... });
const reader = res.body.getReader();
const decoder = new TextDecoder();
let buffer = '', currentEvent = '';

while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    // 按行解析 event:/data: 协议
    for (const line of buffer.split('\n')) { ... }
}
```

**关键注意点：**
- `SseEmitter` 适合单机/低并发场景；高并发推荐 `Flux<ServerSentEvent>`（Spring WebFlux）
- 流式文本用 `useRef` 累积，避免 React 批量更新丢失片段
- 5min 超时需根据 LLM 响应时长调整

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
