# 项目状态总结

> 基于 interview-guide（AI 智能面试平台）的学习性重写项目
> 目标：从零构建最小可运行的 Spring AI 应用，理解 interview-guide 的核心设计模式

---

## 1. 项目目标

从 `interview-guide`（173 个 Java 文件）中提取最核心的设计模式，在 `interview` 项目中用最小代码量复现。不是为了重复功能，而是为了理解：

- Spring AI 的基本用法和底层原理
- 一个好的 AI 应用项目应该怎么组织代码
- interview-guide 为什么那样设计

## 2. 技术栈

| 组件 | 技术选型 |
|---|---|
| 语言 | Java 21 |
| 框架 | Spring Boot 4.0.6 + Spring AI 2.0.0-M8 |
| 数据库 | PostgreSQL 17（生产）/ H2（本地快速开发） |
| ORM | Spring Data JPA (Hibernate) |
| AI | 兼容 OpenAI 格式的 API（通义千问 DashScope） |
| 文档解析 | Apache Tika 2.9.2（PDF/DOCX/TXT/MD） |
| 对象存储 | MinIO（S3 兼容） |
| 构建工具 | Maven |

## 3. 基础设施（docker-compose）

```yaml
services:
  postgres:17     # 5432 → interview / interview123
  redis:7-alpine  # 6379 → 缓存（预留）
  minio:latest    # 9000(API) / 9001(Console) → minioadmin/minioadmin123
```

## 4. 当前已完成模块（10 个 Phase，46 个源文件）

| Phase | 内容 | 文件数 | interview-guide 对应 |
|-------|------|--------|---------------------|
| **P1** | 最简 Chat API：一个端点 + 一个依赖 | 4 | LlmProviderRegistry 最简版 |
| **P2** | 统一响应 + 异常处理规范 | 4 | Result + ErrorCode + BusinessException + GlobalExceptionHandler |
| **P3** | 多轮对话：会话记忆 | 3 个改 | MessageChatMemoryAdvisor + MessageWindowChatMemory |
| **P4** | 结构化输出：让 AI 返回 JSON | 1 | BeanOutputConverter（StructuredOutputInvoker 核心里面那层） |
| **P5** | 手动配置 ChatClient，去掉自动配置 | 2 个改 | AiConfig → OpenAIClient → OpenAiChatModel → ChatClient |
| **P6** | 结构化输出重试机制 | 1 新建 + 2 改 | StructuredOutputInvoker |
| **P7** | **JPA + Repository：对话持久化** | **5 新建 + 3 改** | InterviewSessionEntity + InterviewSessionRepository |
| **P8** | **Interview 完整流程（出题→答题→报告）** | **17 新建 + 7 改** | InterviewQuestionService + 状态机 + 上下文聚合 |
| **P9** | **PostgreSQL 接入 + Schema 脚本** | **1 改 + 1 新建** | 生产级数据库 + 连接池配置 |
| **P10** | **简历管理（上传→Tika解析→MinIO→AI评分）** | **10 新建 + 3 改** | 上传/Tika解析/MinIO存储/AI评分/去重 |

**截止 P10 共计 46 个 Java 源文件**，对比 interview-guide 的 173 个，精简 73%。

### API 端点一览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 发送消息，支持多轮对话（sessionId） |
| POST | `/api/chat/analyze` | 发送消息，返回结构化分析结果 |
| POST | `/api/interview/start` | 创建面试会话，AI 出题 |
| POST | `/api/interview/answer` | 提交答案，AI 实时评估 |
| POST | `/api/interview/next` | 获取下一题或标记完成 |
| POST | `/api/interview/report` | 生成面试报告 |
| POST | `/api/resumes/upload` | **上传简历，Tika 解析 + MinIO 存储 + AI 评分** |
| GET | `/api/resumes` | **简历列表（含最新评分）** |
| GET | `/api/resumes/{id}/detail` | **简历详情 + 历次分析历史** |
| DELETE | `/api/resumes/{id}` | **删除简历** |
| POST | `/api/resumes/{id}/reanalyze` | **重新 AI 分析** |

## 5. 项目架构

```
interview/
├── pom.xml                                          ← PostgreSQL + Tika + MinIO 依赖
├── docker-compose.yml                               ← PostgreSQL + Redis + MinIO 三服务编排
├── sql/schema.sql                                   ← PostgreSQL DDL 参考 + H2/PG 类型对比
│
└── src/main/java/org/interview/
    ├── InterviewApplication.java                    ← 启动类，排除 6 个 OpenAI 自动配置
    │
    ├── config/
    │   ├── AiConfig.java                            ← 手动创建 OpenAiChatModel + ChatClient.Builder
    │   ├── DocumentParserService.java               ← Tika：解析/MIME检测/SHA-256哈希
    │   └── MinioConfig.java                         ← MinIO 客户端 + 自动建桶
    │
    ├── common/
    │   ├── ai/
    │   │   └── StructuredOutputService.java         ← 结构化输出重试（解析失败自动重试 + 本地修复）
    │   ├── result/
    │   │   └── Result<T>                            ← 统一响应：{ code, message, data }
    │   └── exception/
    │       ├── ErrorCode.java                       ← 错误码枚举（含 2xxx 简历域 + 7xxx AI域）
    │       ├── BusinessException.java               ← 业务异常
    │       └── GlobalExceptionHandler.java          ← 全局兜底
    │
    ├── chat/
    │   ├── ChatController.java                     ← REST 控制器（按需使用 advisor）
    │   ├── dto/ (ChatRequest, ChatResponse, MessageAnalysis)
    │   ├── entity/ (ChatSessionEntity, ChatMessageEntity)
    │   ├── repository/ (ChatSessionRepository, ChatMessageRepository)
    │   └── memory/
    │       └── JpaChatMemoryRepository.java        ← JPA 持久化 ChatMemory（替代内存窗口）
    │
    ├── interview/                                   ← Phase 8：面试业务
    │   ├── controller/InterviewController.java     ← POST /api/interview/{start,answer,next,report}
    │   ├── dto/ (10 个 record)
    │   ├── entity/ (SessionStatus enum, InterviewSessionEntity, InterviewAnswerEntity)
    │   ├── repository/ (InterviewSessionRepository, InterviewAnswerRepository)
    │   └── service/
    │       └── InterviewService.java               ← 出题/答题/评估/报告全流程
    │
    └── resume/                                      ← Phase 10：简历管理
        ├── controller/ResumeController.java        ← POST/GET/DELETE /api/resumes/**
        ├── dto/ (ResumeAnalysisDTO, ResumeListItemDTO, ResumeDetailDTO)
        ├── entity/ (ResumeEntity, ResumeAnalysisEntity)
        ├── repository/ (ResumeRepository, ResumeAnalysisRepository)
        └── service/
            └── ResumeService.java                  ← 上传/解析/MinIO/AI评分/CRUD
```

## 6. 已掌握的 Spring AI 知识

### 6.1 ChatClient 调用链

```
chatClient.prompt()
    .system("system prompt")     // 系统角色设定
    .user("用户消息")             // 用户输入
    .advisors(a -> ...)          // 注入横切能力（记忆、安全、日志等）
    .call()                      // 同步调用
    .content()                   // 获取文本回复
```

### 6.2 多轮对话机制

- `MessageWindowChatMemory`：保留最近 N 条消息的窗口式记忆
- `MessageChatMemoryAdvisor`：在每次调用时自动注入历史消息
- `chat_memory_conversation_id`：按会话 ID 隔离不同对话的上下文
- 持久化方案：`JpaChatMemoryRepository` 实现 `ChatMemoryRepository` 接口，替代内存窗口

### 6.3 结构化输出

```java
BeanOutputConverter<MessageAnalysis> converter =
    new BeanOutputConverter<>(MessageAnalysis.class);
// converter.getFormat() 自动生成 JSON Schema 说明，注入 system prompt
// converter.convert(reply) 自动将 AI 回复的 JSON 解析为 Java 对象
```

### 6.4 手动配置 vs 自动配置

| 方式 | 原理 | 适用场景 |
|------|------|---------|
| 自动配置（P1-P4） | spring.ai.openai.* → 自动创建 Bean | 快速原型，单一 Provider |
| 手动配置（P5） | AiConfig → OpenAiChatModel → ChatClient | 多 Provider、自定义超时/Header、生产环境 |

### 6.5 结构化输出重试模式（Phase 6）

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

## 7. Phase 8 设计理解（Interview 面试业务）

### 7.1 通用能力 → 业务封装模式

```
StructuredOutputService (Phase 6, 通用)
  └── InterviewService (Phase 8, 业务)
        ├── 定义 DTO + prompt
        ├── 调用 invoke()，不关心重试/修复
        └── 保存业务数据到 DB
```

### 7.2 Entity 与 DTO 的分层原则

| 层 | 面向谁 | 核心原则 |
|----|--------|---------|
| **Entity** | 数据库表 | 考虑查询效率、数据完整性、JPA 映射 |
| **DTO** | API 调用方（前端） | 只包含调用方需要的字段，可独立演进 |

### 7.3 `sessionId` 为什么需要独立于 `id`？

| | `id` (Long) | `sessionId` (String) |
|---|---|---|
| 什么时候知道 | INSERT 之后（自增） | INSERT 之前（UUID） |
| 能否在 API 请求中传递 | ❌ | ✅ |
| 是否泄露数据量 | ✅ 暴露 id=100 知道有100场面试 | ✅ 无规律 |

### 7.4 不要滥用 `defaultAdvisors`

`MessageChatMemoryAdvisor` 对多轮聊天有必要，但对一次性 AI 分析调用是负担。Builder 保持干净，各方法按需加 advisor。

### 7.5 `ChatClient.Builder` 是单例，不能共享改动

面试服务直接从 `OpenAiChatModel` 创建自己的 `ChatClient`，不经过共享 Builder。

## 8. Phase 9：基础设施升级

### P9.1 PostgreSQL 接入

| 文件 | 改动 |
|------|------|
| `pom.xml` | H2 → `test` scope，新增 `postgresql` 运行时依赖 |
| `application.yml` | 数据源切换为 PostgreSQL + HikariCP 连接池 |
| `application-h2.yml` | **新建** — 保留 H2 作为快速开发 profile |

### P9.2 SQL Schema 脚本

`sql/schema.sql` — 将现有 Entity 映射为 PostgreSQL DDL，附 H2 vs PostgreSQL 数据类型对比：

| 差异点 | H2 | PostgreSQL |
|--------|----|-----------|
| 自增主键 | `BIGINT AUTO_INCREMENT` | `BIGINT GENERATED BY DEFAULT AS IDENTITY` |
| TEXT | 最大 ~256MB | 最大 ~1GB |
| BOOLEAN | 映射为 TINYINT(1) | 原生 true/false |
| 序列 | 表级自增，无独立序列 | 独立 SEQUENCE 对象 |

### P9.3 Redis 缓存（尝试后还原）

曾尝试引入 Spring Cache + Redis 优化，分析后发现 `findSession()` 已经是唯一索引查询（~1ms），状态频繁变更导致缓存几乎不命中，最终还原。

### P9.4 MinIO 对象存储

docker-compose 集成 MinIO 服务，`MinioConfig` 自动建桶，`ResumeService.uploadToMinio()` 上传文件。

## 9. Phase 10 设计理解（简历管理）

### 9.1 文件处理流程

```
MultipartFile 上传
  │
  ├─ 1. validateFile()           → 非空 + ≤10MB + MIME 白名单校验
  ├─ 2. calculateHash()          → SHA-256 去重
  ├─ 3. detectContentType()      → Tika MIME 检测
  ├─ 4. parseContent()           → Tika 解析为纯文本
  ├─ 5. uploadToMinio()          → MinIO 存储（key: resumes/{uuid}_{filename}）
  ├─ 6. ResumeRepository.save()  → JPA 持久化（状态 PENDING）
  ├─ 7. analyzeResume()          → AI 结构化评分（5 维度）
  └─ 8. saveAnalysis()           → 保存分析结果（状态 COMPLETED）
```

### 9.2 去重策略：文件内容哈希

```java
String fileHash = documentParserService.calculateHash(file);
Optional<ResumeEntity> existing = resumeRepository.findByFileHash(fileHash);
```
命中时直接返回历史分析结果 + `accessCount++`，不重复上传和 AI 调用。

### 9.3 AI 评分维度（满分 100）

| 维度 | 满分 | 评估内容 |
|------|------|---------|
| contentScore | 25 | 内容完整性——教育背景、工作经历、技能列表 |
| structureScore | 20 | 结构清晰度——排版、层次、可读性 |
| skillMatchScore | 25 | 技能匹配度——技能描述的具体程度与相关性 |
| expressionScore | 15 | 表达专业性——语言是否专业、量化成果 |
| projectScore | 15 | 项目经验——项目描述是否清晰、有深度 |

### 9.4 同步 vs 异步 AI 分析

| | 同步（本实现） | 异步（参考项目） |
|---|---|---|
| 响应速度 | 慢（等 AI 分析完才返回） | 快（立即返回 PENDING） |
| 复杂度 | 低（一个 Service 搞定） | 高（Redis Stream + 轮询） |
| 适用场景 | 学习/原型 | 生产环境 |

### 9.5 错误码分层（2xxx 简历域）

| 错误码 | 含义 | 触发场景 |
|--------|------|---------|
| 2001 | 简历不存在 | GET/DELETE 不存在的简历 |
| 2002 | 上传失败 | MinIO 写失败 |
| 2003 | 解析失败 | Tika 无法提取文本（如扫描版 PDF） |
| 2004 | AI 评分失败 | AI 调用异常 |
| 2005 | 文件过大 | 超过 10MB |
| 2006 | 类型不支持 | 非 PDF/DOCX/TXT/MD |

### 9.6 配置项

```yaml
app:
  ai:
    base-url: ${AI_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
    api-key: ${AI_API_KEY:sk-...}
    model: ${AI_MODEL:qwen3.5-flash}
  minio:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    secret-key: ${MINIO_SECRET_KEY:minioadmin123}
    bucket: ${MINIO_BUCKET:resumes}
  resume:
    max-file-size: 10485760  # 10MB
```

快速开发（不启动 PostgreSQL）时用 H2 profile：
```bash
--spring.profiles.active=h2
```

## 10. 配置（application.yml）

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
```

## 11. 学习路线图

```
Phase 1-7 (已完成) ─── Spring AI 基础 + 项目骨架 + 健壮性设计 + 持久化
        │
        ▼
Phase 8 (已完成) ─── Interview 完整业务链路（出题→答题→报告）
        │
        ▼
Phase 9 (已完成) ─── 基础设施升级（PostgreSQL + Schema + MinIO）
        │
        ▼
Phase 10 (已完成) ─── 简历管理（上传/Tika解析/MinIO存储/AI评分/去重）
        │
        ▼
Phase 11 (计划) ─── 知识库 RAG（向量化 + 检索增强生成）
        │
        ▼
Phase 12 (计划) ─── 语音面试（WebSocket + ASR/TTS 实时通话）
```

推荐顺序：**Phase 10 → Phase 11 → Phase 12**。
