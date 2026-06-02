# Phase 12：知识库 RAG

## 整体架构（回顾）

最初的设计是让 RAG 从简历自身检索片段注入面试 prompt。实践后发现简历文本短（500-2000 字），
绕一圈向量检索不如直接注入完整文本，且向量化异步存在竞态条件。

**Pivot 结论**：面试出题直接注入完整简历文本（更简单、更可靠）；
RAG 能力保留，但目标转向**知识库文档检索**（面经、技术文档、JD），而非简历自身。

```
用户上传简历
    │
    ├─ ResumeService (已有)   → 解析/存储/评分
    │
    └─ ResumeKnowledgeService (保留)
          ├─ chunkText()         → 分块
          ├─ embed()             → 向量化
          └─ 语义检索             → 供后续知识库场景使用

面试开始时 (InterviewService)
    │
    ├─ resumeId 不为 null？
    │     ├─ 是 → 直接注入 ResumeEntity.resumeText 全文到 system prompt
    │     └─ 否 → 原有逻辑不变
    │
    └─ 生成报告时同理，注入完整简历文本让评估更个性化
```

## 为什么出题不用 RAG 检索简历？

| 方式 | 优点 | 缺点 |
|------|------|------|
| PgVectorStore (Spring AI) | 开箱即用，CRUD 封装好 | 黑盒，不利于理解原理；Hibernate 类型映射麻烦 |
| JPA + JdbcTemplate (本实现) | 分块/检索逻辑透明；SQL 可控；学习价值高 | 代码量略多 |
| **直接注入简历全文**（当前推荐） | 信息零损失；无竞态；代码简单 | 简历 > 5000 字时需截断 |

本项目简历通常 500-2000 字，**直接注入全文比 RAG 更简单可靠**。
RAG 能力保留，后续用于**知识库多文档检索**（面经、技术文档等），才是它的典型场景。

## pgvector 基础设施

| 组件 | 说明 |
|------|------|
| Docker 镜像 | `pgvector/pgvector:pg16`（PostgreSQL 16 + pgvector 预装） |
| 向量列 | `vector(1024)` — 匹配 text-embedding-v3 输出维度 |
| 索引 | `IVFFlat (vector_cosine_ops, lists=10)` — 余弦相似度近似检索 |
| 初始化 | `VectorStoreInitializer` — 启动时 `CREATE EXTENSION IF NOT EXISTS vector` + `ADD COLUMN` |
| H2 兼容 | 检测 `h2` profile 时跳过向量操作，退化为按顺序返回文本块 |

## Chunking 策略

```
输入: "教育背景：北京大学 计算机科学... 工作经历：... 技能：Java Spring..."
      │
      ├─ Chunk 0: "教育背景：北京大学 计算机科学..."
      ├─ Chunk 1: "...工作经历：某公司 后端开发..."
      └─ Chunk 2: "...技能：Java Spring..."
              ↑ 50 字符重叠确保上下文不割裂
```

简历文本通常较短（500-2000 字），因此分块数少（2-8 块），检索效率高。

## 数据流：上传 → 索引 → 检索

```
上传 (ResumeService)
  → 事务提交后 Redis Stream publish { resumeId }
  ├─ ResumeAnalysisConsumer → processAnalysis() → AI 评分 → COMPLETED
  └─ ResumeIndexConsumer → processIndex() → indexResume() → chunkText() → batchEmbed() → UPDATE embedding

开始面试 (InterviewService.startInterview)
  → resumeId 不为 null？
      ├─ 是 → resumeRepository.findById() → 取 resumeText 全文直接注入 system prompt
      └─ 否 → 原有通用逻辑

生成报告 (InterviewService.generateReportFromAI)
  → 同样注入简历全文到评估 prompt

📌 RAG（searchRelevantContext）当前未用于出题，保留给后续知识库场景
```

## 新增文件（P10 + P12）

| 文件 | 说明 |
|------|------|
| `config/RedisStreamConfig.java` | Redis Stream 配置（Stream + 2 个 Consumer Group + DLQ） |
| `resume/consumer/ResumeAnalysisConsumer.java` | 异步 AI 评分消费者 |
| `resume/consumer/ResumeIndexConsumer.java` | 异步 RAG 索引消费者 |
| `resume/service/ResumeAnalysisService.java` | 异步 AI 评分服务 |
| `resume/service/ResumeKnowledgeService.java` | RAG 核心服务（分块/向量化/检索） |
| `resume/entity/ResumeChunkEntity.java` | 分块 JPA 实体 |
| `resume/repository/ResumeChunkRepository.java` | 分块 Repository |
| `config/VectorStoreInitializer.java` | pgvector 迁移（启动建索引） |

## 修改文件

| 文件 | 改动 |
|------|------|
| `docker-compose.yml` | postgres:17 → pgvector/pgvector:pg16；+Redis 7-alpine 服务 |
| `pom.xml` | +pgvector JDBC、+spring-boot-starter-data-redis |
| `config/AiConfig.java` | +EmbeddingModel bean、+embeddingModel 配置 |
| `resume/service/ResumeService.java` | Redis Stream 异步改造：afterCommit() 发布消息，注入 ResumeKnowledgeService |
| `resume/service/ResumeKnowledgeService.java` | +processIndex() 异步索引、+ResumeRepository 依赖 |
| `resume/entity/ResumeEntity.java` | +indexStatus 字段（PENDING→PROCESSING→COMPLETED） |
| `interview/dto/CreateInterviewRequest.java` | +resumeId 可选字段 |
| `interview/entity/InterviewSessionEntity.java` | +resumeId 列 |
| `interview/service/InterviewService.java` | 注入简历语境到出题和报告 prompt |
| `frontend/src/pages/Interview.jsx` | +简历选择器 UI |

## 配置（application.yml）

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
  data:
    redis:
      host: localhost
      port: 6379
```

### Redis Stream 配置项

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
