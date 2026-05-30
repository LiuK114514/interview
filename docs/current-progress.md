# 项目状态总结

> 基于 interview-guide（AI 智能面试平台）的学习性重写项目
> 目标：从零构建最小可运行的 Spring AI 应用，理解 interview-guide 的核心设计模式

---

## 1. 项目目标

从 `interview-guide`（173 个 Java 文件）中提取最核心的设计模式，在 `interview` 项目中用最小代码量复现。不是为了重复功能，而是为了理解：

- Spring AI 的基本用法和底层原理
- 一个好的 AI 应用项目应该怎么组织代码
- interview-guide 为什么那样设计

## 2. 当前已完成模块（8 个 Phase）

| Phase | 内容 | 文件数 | interview-guide 对应 |
|-------|------|--------|---------------------|
| **P1** | 最简 Chat API：一个端点 + 一个依赖 | 4 | LlmProviderRegistry 最简版 |
| **P2** | 统一响应 + 异常处理规范 | 4 | Result + ErrorCode + BusinessException + GlobalExceptionHandler |
| **P3** | 多轮对话：会话记忆 | 3 个改 | MessageChatMemoryAdvisor + MessageWindowChatMemory |
| **P4** | 结构化输出：让 AI 返回 JSON | 1 | BeanOutputConverter（StructuredOutputInvoker 的核心里面那层） |
| **P5** | 手动配置 ChatClient，去掉自动配置 | 2 个改 | AiConfig → OpenAIClient → OpenAiChatModel → ChatClient |
| **P6** | 结构化输出重试机制 | 1 新建 + 2 改 | StructuredOutputInvoker |
| **P7** | **JPA + Repository：对话持久化** | **5 新建 + 3 改** | **InterviewSessionEntity + InterviewSessionRepository** |
| **P8** | **Interview 完整流程（出题→答题→报告）** | **17 新建 + 7 改** | **InterviewQuestionService + 状态机 + 上下文聚合** |
| **P9.1** | **PostgreSQL 接入** | **1 改 + 1 新建** | **生产级数据库 + 连接池配置** |
| **P9.2** | **SQL Schema 脚本** | **1 新建** | **H2 vs PostgreSQL 类型差异对比** |

**截止 P9.2 共计约 39 个源文件**（含 2 个配置/启动文件 + 1 个 SQL 脚本），对比 interview-guide 的 173 个，精简 77%。

### API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 发送消息，支持多轮对话（sessionId） |
| POST | `/api/chat/analyze` | 发送消息，返回结构化分析结果 |
| POST | `/api/interview/start` | 创建面试会话，AI 出题（Phase 8.1） |
| POST | `/api/interview/answer` | 提交答案，AI 实时评估（Phase 8.2） |
| POST | `/api/interview/next` | 获取下一题或标记完成（Phase 8.2） |
| POST | `/api/interview/report` | 生成面试报告（Phase 8.3） |

## 3. 已掌握的 Spring AI 知识

### 3.1 ChatClient 调用链

```
chatClient.prompt()
    .system("system prompt")     // 系统角色设定
    .user("用户消息")             // 用户输入
    .advisors(a -> ...)          // 注入横切能力（记忆、安全、日志等）
    .call()                      // 同步调用
    .content()                   // 获取文本回复
```

### 3.2 多轮对话机制

- `MessageWindowChatMemory`：保留最近 N 条消息的窗口式记忆
- `MessageChatMemoryAdvisor`：在每次调用时自动注入历史消息
- `chat_memory_conversation_id`：按会话 ID 隔离不同对话的上下文

对应 interview-guide 中的 `LlmProviderRegistry.buildDefaultAdvisors()`。

### 3.3 结构化输出

```java
BeanOutputConverter<MessageAnalysis> converter =
    new BeanOutputConverter<>(MessageAnalysis.class);

// converter.getFormat() 自动生成 JSON Schema 说明，注入 system prompt
// converter.convert(reply) 自动将 AI 回复的 JSON 解析为 Java 对象
```

对应 interview-guide 中的 `InterviewQuestionService` 出题逻辑 + `StructuredOutputInvoker` 核心。

### 3.4 手动配置 vs 自动配置

| 方式 | 原理 | 适用场景 |
|------|------|---------|
| 自动配置（P1-P4） | spring.ai.openai.* → 自动创建 Bean | 快速原型，单一 Provider |
| 手动配置（P5） | AiConfig → OpenAIClient → OpenAiChatModel → ChatClient | 多 Provider、自定义超时/Header、生产环境 |

interview-guide 选择手动配置的原因：
- 支持运行时动态切换 Provider
- 需要为不同场景创建不同 ChatClient（普通、纯文本、语音）
- 排除自动配置的 6 个 AutoConfiguration 类

### 3.5 结构化输出重试模式（Phase 6）

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

对应 interview-guide 中的 `StructuredOutputInvoker`：核心是 "解析失败 → 让 AI 知道自己错了 → 重试" 这个反馈循环。

## 4. 当前项目架构

```
interview/
├── pom.xml                                          ← PostgreSQL 依赖，H2 改为 test scope
│
├── sql/
│   └── schema.sql                                   ← PostgreSQL DDL 参考脚本（含类型对比）
│
├── docker-compose.yml                               ← PostgreSQL + MinIO（三服务编排）
│
└── src/main/java/org/interview/
    ├── InterviewApplication.java                    ← 启动类，排除自动配置
    │
    ├── config/
    │   └── AiConfig.java                            ← 手动创建 OpenAiChatModel + ChatClient.Builder
    │
    ├── common/
    │   ├── ai/
    │   │   └── StructuredOutputService.java         ← 结构化输出重试（解析失败自动重试 + 本地修复）
    │   ├── result/
    │   │   └── Result<T>                            ← 统一响应：{ code, message, data }
    │   └── exception/
    │       ├── ErrorCode.java                       ← 错误码枚举（含 AI_SERVICE_TIMEOUT 7002）
    │       ├── BusinessException.java               ← 业务异常
    │       └── GlobalExceptionHandler.java          ← 全局兜底
    │
    └── chat/
        ├── ChatController.java                     ← REST 控制器（按需使用 advisor）
        ├── dto/
        │   ├── ChatRequest.java                    ← { message, sessionId? }
        │   ├── ChatResponse.java                   ← { sessionId, reply }
        │   └── MessageAnalysis.java                ← { sentiment, score, keywords, suggestion }
        ├── entity/
        │   ├── ChatSessionEntity.java              ← 会话实体（JPA）
        │   └── ChatMessageEntity.java              ← 消息实体（JPA）
        ├── repository/
        │   ├── ChatSessionRepository.java          ← 会话仓库
        │   └── ChatMessageRepository.java          ← 消息仓库
        └── memory/
            └── JpaChatMemoryRepository.java        ← 实现 ChatMemoryRepository 接口，JPA 持久化存储

    └── interview/                                    ← Phase 8：面试业务
        ├── controller/
        │   └── InterviewController.java             ← POST /api/interview/{start,answer,next,report}
        ├── dto/
        │   ├── CreateInterviewRequest.java          ← { skillId, difficulty }
        │   ├── QuestionDTO.java                     ← { index, question, category }
        │   ├── InterviewQuestions.java              ← 包装 List 给 BeanOutputConverter
        │   ├── StartInterviewResponse.java          ← { sessionId, totalQuestions, questions }
        │   ├── SubmitAnswerRequest.java             ← { sessionId, questionIndex, answer }
        │   ├── SubmitAnswerResponse.java            ← { score, feedback, correctAnswer }
        │   ├── AnswerEvaluationDTO.java             ← AI 结构化输出格式
        │   ├── NextQuestionRequest.java             ← { sessionId }
        │   ├── NextQuestionResponse.java            ← { completed, question }
        │   ├── GenerateReportRequest.java           ← { sessionId }
        │   ├── GenerateReportResponse.java          ← { totalScore, strengths, ... }
        │   └── InterviewReportDTO.java              ← 报告结构化输出格式
        ├── entity/
        │   ├── SessionStatus.java                   ← CREATED → IN_PROGRESS → COMPLETED → EVALUATED
        │   ├── InterviewSessionEntity.java          ← 面试会话（JPA），含报告缓存字段
        │   └── InterviewAnswerEntity.java           ← 答案 + 评估结果（JPA）
        ├── repository/
        │   ├── InterviewSessionRepository.java      ← 会话仓库
        │   └── InterviewAnswerRepository.java       ← 答案仓库
        └── service/
            └── InterviewService.java                ← 业务：出题/答题/评估/报告
```

### 配置

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/interview
    username: interview
    password: interview123
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

app:
  ai:
    base-url: ${AI_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
    api-key: ${AI_API_KEY:sk-...}
    model: ${AI_MODEL:qwen3.5-flash}
```

快速开发（不启动 PostgreSQL）时用 H2 profile：
```bash
--spring.profiles.active=h2
```

## 5. Phase 8 设计理解

### 5.1 通用能力 → 业务封装模式

```
StructuredOutputService (Phase 6, 通用)
  └── InterviewService (Phase 8, 业务)
        ├── 定义 DTO + prompt
        ├── 调用 invoke()，不关心重试/修复
        └── 保存业务数据到 DB
```

**原则**：通用能力放在 `common/` 层，业务层只管组装和编排。职责清晰，可复用。

### 5.2 Entity 与 DTO 的分层原则

| 层 | 面向谁 | 核心原则 |
|----|--------|---------|
| **Entity** | 数据库表 | 考虑查询效率、数据完整性、JPA 映射 |
| **DTO** | API 调用方（前端） | 只包含调用方需要的字段，可独立演进 |

### 5.3 `sessionId` 为什么需要独立于 `id`？

| | `id` (Long) | `sessionId` (String) |
|---|---|---|
| 什么时候知道 | INSERT 之后（自增） | INSERT 之前（UUID） |
| 能否在 API 请求中传递 | ❌ | ✅ |
| 是否泄露数据量 | ✅ 暴露 id=100 知道有100场面试 | ✅ 无规律 |

### 5.4 不要滥用 `defaultAdvisors`

`MessageChatMemoryAdvisor` 对多轮聊天有必要，但对一次性 AI 分析调用是负担。

```java
// 之前：设了 defaultAdvisor → 所有端点强制加记忆，analyze() 没传 conversationId 报错
this.chatClient = builder.defaultAdvisors(chatMemoryAdvisor).build();

// 之后：Builder 保持干净，各方法按需加 advisor
this.builder = builder;
chat()：     .advisors(chatMemoryAdvisor).advisors(a -> ...)
analyze()：  不加 advisor → 干净的一次性调用
```

### 5.5 `ChatClient.Builder` 是单例，不能共享改动

面试服务直接从 `OpenAiChatModel` 创建自己的 `ChatClient`，不经过共享 Builder。

## 6. Phase 9：基础设施升级

### P9.1 PostgreSQL 接入 ✅

将 H2 内存数据库换为 PostgreSQL，改动点：

| 文件 | 改动 |
|------|------|
| `pom.xml` | H2 → `test` scope，新增 `postgresql` 运行时依赖 |
| `application.yml` | 数据源切换为 PostgreSQL + HikariCP 连接池 |
| `application-h2.yml` | **新建** — 保留 H2 作为快速开发 profile |

### P9.2 SQL Schema 脚本 ✅

`sql/schema.sql` — 将现有 Entity 映射为 PostgreSQL DDL，附 H2 vs PostgreSQL 数据类型对比：

| 差异点 | H2 | PostgreSQL |
|--------|----|-----------|
| 自增主键 | `BIGINT AUTO_INCREMENT` | `BIGINT GENERATED BY DEFAULT AS IDENTITY` |
| TEXT | 最大 ~256MB | 最大 ~1GB |
| BOOLEAN | 映射为 TINYINT(1) | 原生 true/false |
| 序列 | 表级自增，无独立序列 | 独立 SEQUENCE 对象 |

### P9.3 Redis 缓存（尝试后还原）

曾尝试引入 Spring Cache + Redis 优化 `findSession()` 查询，分析后还原：

- **问题**：`findSession()` 是 `WHERE session_id = ?` 的唯一索引查询（~1ms），缓存收益极低
- **问题**：Session 状态频繁变更（每答一题 status 可能变），`@CacheEvict` 导致缓存几乎不命中
- **教训**：不是所有 DB 查询都需要缓存。简单 PK/UK 查询 + 频繁修改的场景，缓存弊大于利

### P9.4 MinIO 对象存储（计划）

跳过，等 Phase 10 简历管理用到时再做。

## 7. 学习路线图

```
Phase 1-7 (已完成) ─── Spring AI 基础 + 项目骨架 + 健壮性设计 + 持久化
        │
        ▼
Phase 8 (已完成) ─── Interview 业务链路 ─── 完整面试功能（出题→答题→报告）
        │
        ▼
Phase 9 (已完成) ─── 基础设施升级 ─── PostgreSQL 接入 + Schema 脚本
        │
        ▼
Phase 10 (计划) ─── 简历管理 ─── 文件处理（MinIO + Tika）+ AI 结构化提取 + 评分
        │
        ▼
Phase 11 (计划) ─── 知识库 RAG ─── 向量化 + 检索增强生成
        │
        ▼
Phase 12 (计划) ─── 语音面试 ─── WebSocket + ASR/TTS 实时通话
```

推荐顺序：**Phase 10 → Phase 11 → Phase 12**。
