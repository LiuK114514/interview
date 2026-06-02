# 项目概览

> 基于 interview-guide（AI 智能面试平台）的学习性重写项目
> 目标：从零构建最小可运行的 Spring AI 应用，理解 interview-guide 的核心设计模式

## 项目目标

从 `interview-guide`（173 个 Java 文件）中提取最核心的设计模式，在 `interview` 项目中用最小代码量复现。不是为了重复功能，而是为了理解：

- Spring AI 的基本用法和底层原理
- 一个好的 AI 应用项目应该怎么组织代码
- interview-guide 为什么那样设计

## 技术栈

| 组件 | 技术选型 |
|---|---|
| 语言 | Java 21 |
| 框架 | Spring Boot 4.0.6 + Spring AI 2.0.0-M8 |
| 前端 | Vite 5 + React 18 + Motion |
| 数据库 | pgvector/pgvector:pg16（PostgreSQL 16 + pgvector）/ H2 |
| ORM | Spring Data JPA (Hibernate) |
| AI | 兼容 OpenAI 格式的 API（通义千问 DashScope） |
| 文档解析 | Apache Tika 2.9.2（PDF/DOCX/TXT/MD） |
| 对象存储 | MinIO（S3 兼容） |
| 消息队列 | Redis Stream（异步简历分析 + 向量化） |
| 构建工具 | Maven |

## 基础设施（docker-compose）

```yaml
services:
  postgres:17     # 5432 → interview / interview123
  redis:7-alpine  # 6379 → Redis Stream 异步任务队列
  minio:latest    # 9000(API) / 9001(Console) → minioadmin/minioadmin123
```

## 项目架构

```
interview/
├── pom.xml                                          ← PostgreSQL + Tika + MinIO 依赖
├── docker-compose.yml                               ← PostgreSQL + Redis + MinIO 三服务编排
├── sql/schema.sql                                   ← PostgreSQL DDL 参考 + H2/PG 类型对比
│
├── src/main/java/org/interview/
│   ├── InterviewApplication.java                    ← 启动类，排除 6 个 OpenAI 自动配置
│   │
│   ├── config/
│   │   ├── AiConfig.java                            ← 手动创建 OpenAiChatModel + ChatClient.Builder
│   │   ├── DocumentParserService.java               ← Tika：解析/MIME检测/SHA-256哈希
│   │   ├── MinioConfig.java                         ← MinIO 客户端 + 自动建桶
│   │   ├── RedisConfig.java                         ← Redis 客户端配置
│   │   ├── RedisStreamConfig.java                   ← Redis Stream + Consumer Group 配置
│   │   └── VectorStoreInitializer.java              ← pgvector 迁移（启动建索引）
│   │
│   ├── common/
│   │   ├── ai/
│   │   │   └── StructuredOutputService.java         ← 结构化输出重试（解析失败自动重试 + 本地修复）
│   │   ├── result/
│   │   │   └── Result<T>                            ← 统一响应：{ code, message, data }
│   │   └── exception/
│   │       ├── ErrorCode.java                       ← 错误码枚举（含 2xxx 简历域 + 7xxx AI域）
│   │       ├── BusinessException.java               ← 业务异常
│   │       └── GlobalExceptionHandler.java          ← 全局兜底
│   │
│   ├── chat/
│   │   ├── ChatController.java                     ← REST 控制器（按需使用 advisor）
│   │   ├── dto/ (ChatRequest, ChatResponse, MessageAnalysis)
│   │   ├── entity/ (ChatSessionEntity, ChatMessageEntity)
│   │   ├── repository/ (ChatSessionRepository, ChatMessageRepository)
│   │   └── memory/
│   │       └── JpaChatMemoryRepository.java        ← JPA 持久化 ChatMemory（替代内存窗口）
│   │
│   ├── interview/                                   ← Phase 8：面试业务
│   │   ├── controller/InterviewController.java     ← POST /api/interview/{start,answer,next,report}
│   │   ├── dto/ (10 个 record)
│   │   ├── entity/ (SessionStatus enum, InterviewSessionEntity, InterviewAnswerEntity)
│   │   ├── repository/ (InterviewSessionRepository, InterviewAnswerRepository)
│   │   └── service/
│   │       └── InterviewService.java               ← 出题/答题/评估/报告全流程
│   │
│   └── resume/                                      ← Phase 10：简历管理
│       ├── controller/ResumeController.java        ← POST/GET/DELETE /api/resumes/**
│       ├── dto/ (ResumeAnalysisDTO, ResumeListItemDTO, ResumeDetailDTO)
│       ├── entity/ (ResumeEntity, ResumeAnalysisEntity, ResumeChunkEntity)
│       ├── repository/ (ResumeRepository, ResumeAnalysisRepository, ResumeChunkRepository)
│       ├── consumer/
│       │   ├── ResumeAnalysisConsumer.java         ← Redis Stream 消费：AI 评分
│       │   └── ResumeIndexConsumer.java            ← Redis Stream 消费：RAG 索引
│       └── service/
│           ├── ResumeService.java                  ← 上传/解析/MinIO/AI评分/CRUD
│           ├── ResumeAnalysisService.java          ← 异步 AI 评分
│           └── ResumeKnowledgeService.java         ← RAG：分块/向量化/检索
│
└── frontend/                                        ← Phase 11：Vite + React 前端
    ├── index.html                                   ← 入口（Syne + DM Sans 字体）
    ├── package.json / vite.config.js                ← 构建配置，proxy → :8080
    └── src/
        ├── App.jsx / main.jsx                       ← 路由 + 页面切换动画
        ├── index.css                                ← 设计系统（暗色·暖金·编辑风）
        ├── api/index.js                             ← 后端 API 封装层
        ├── hooks/useApi.js                          ← useApi 通用 hook
        ├── components/Sidebar.jsx                   ← 固定侧边导航
        └── pages/
            ├── Dashboard.jsx                        ← 仪表盘：统计 + 快捷入口
            ├── Interview.jsx                        ← 面试全流程（选题→答题→评估→报告）
            ├── Chat.jsx                             ← 多轮 AI 对话
            └── Resumes.jsx                          ← 简历 CRUD + AI 分析展示
```
