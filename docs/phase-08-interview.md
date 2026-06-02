# Phase 8：面试业务设计

## 通用能力 → 业务封装模式

```
StructuredOutputService (Phase 6, 通用)
  └── InterviewService (Phase 8, 业务)
        ├── 定义 DTO + prompt
        ├── 调用 invoke()，不关心重试/修复
        └── 保存业务数据到 DB
```

## Entity 与 DTO 的分层原则

| 层 | 面向谁 | 核心原则 |
|----|--------|---------|
| **Entity** | 数据库表 | 考虑查询效率、数据完整性、JPA 映射 |
| **DTO** | API 调用方（前端） | 只包含调用方需要的字段，可独立演进 |

## `sessionId` 为什么需要独立于 `id`？

| | `id` (Long) | `sessionId` (String) |
|---|---|---|
| 什么时候知道 | INSERT 之后（自增） | INSERT 之前（UUID） |
| 能否在 API 请求中传递 | ❌ | ✅ |
| 是否泄露数据量 | ✅ 暴露 id=100 知道有100场面试 | ✅ 无规律 |

## 不要滥用 `defaultAdvisors`

`MessageChatMemoryAdvisor` 对多轮聊天有必要，但对一次性 AI 分析调用是负担。Builder 保持干净，各方法按需加 advisor。

## `ChatClient.Builder` 是单例，不能共享改动

面试服务直接从 `OpenAiChatModel` 创建自己的 `ChatClient`，不经过共享 Builder。
