# 项目状态总结

> 基于 interview-guide（AI 智能面试平台）的学习性重写项目
> 目标：从零构建最小可运行的 Spring AI 应用，理解 interview-guide 的核心设计模式

---

## 索引

| 文档 | 说明 |
|------|------|
| [概览](overview.md) | 项目目标、技术栈、基础设施、项目架构 |
| [已完成模块](phases-summary.md) | P1-P12 模块一览、API 端点 |
| [Spring AI 知识点](spring-ai-knowledge.md) | ChatClient、多轮对话、结构化输出、重试模式 |
| [Phase 8：面试业务](phase-08-interview.md) | Interview 完整链路设计理解 |
| [Phase 9：基础设施](phase-09-infrastructure.md) | PostgreSQL、Schema、MinIO |
| [Phase 10：简历管理](phase-10-resume.md) | 文件处理、去重、AI 评分、Redis Stream |
| [Phase 12：知识库 RAG](phase-12-rag.md) | pgvector、分块策略、配置、Pivot 说明 |
| [路线图](roadmap.md) | Phase 13-15 计划 |
| [待优化点](todo-optimize.md) | 已知优化项与计划 |

---

## 最新进度（2026-06-04）

### Phase 13：面试-简历关联度优化 ✅ 已完成

**问题：** 选了关联简历后出题仍然偏向通用题，简历内容只作为弱上下文附加在 prompt 末尾，LLM 优先响应通用指令。

**改动文件：** `interview/service/InterviewService.java`

**优化内容（两处）：**

| 维度 | 优化前 | 优化后 |
|------|--------|--------|
| 数据源 | 向量检索 3 个片段（信息丢失） | 直接注入 `ResumeEntity.resumeText` 全文 |
| prompt 结构 | 通用指令"生成 5 道高质量面试题"主导，简历附末尾 | 简历全文放最前面，LLM 第一眼看到 |
| 约束强度 | "请结合简历内容出题" — 可选语气 | "题目必须围绕简历中提到的技术栈、项目经历和技能" — 强约束 |
| 范围控制 | 无约束 | **明确禁止出简历未涉及领域的题目** |
| 报告评估 | 同向量检索片段 | 简历全文注入，分析结合简历背景 |

**清理：** 移除了 `InterviewService` 中对 `ResumeKnowledgeService` 的依赖（不再需要向量检索简历片段）

---

### SSE 流式输出 ✅ 已完成

**改动文件：** `ChatController.java` / `api/index.js` / `Chat.jsx`

**实现方案：**

| 层 | 技术选型 | 要点 |
|---|---|---|
| 后端 | `SseEmitter` + `ChatClient.stream().content()` | 5min 超时，三个命名事件：`message` / `done` / `error` |
| 前端 API | `fetch` + `ReadableStream` + 行解析 | 按 `event:` / `data:` 行协议解析，支持断行缓冲 |
| 前端 UI | `useRef` 累积流式文本 + `streamingText` render | 避免因 React 批量更新丢失片段，Markdown 实时渲染 |

**核心流程：**

```
ChatController.chatStream()
    │
    ├─ 创建 SseEmitter (300s 超时)
    ├─ 调用 ChatClient.prompt().stream().content()
    │     └─ subscribe(onNext → emitter.send(event("message")),
    │                 onError → emitter.send(event("error")),
    │                 onComplete → emitter.send(event("done")))
    └─ 返回 SseEmitter
```

---

## 已完成模块一览（P1-P12）

| 阶段 | 状态 | 核心内容 |
|------|------|---------|
| P1-P7 | 已完成 | Spring AI 基础 + 项目骨架 + 结构化输出 + 持久化 |
| P8 | 已完成 | Interview 完整业务链路（出题→答题→评估→报告） |
| P9 | 已完成 | 基础设施（PostgreSQL + MinIO + Docker Compose） |
| P10 | 已完成 | 简历管理（上传 / Tika 解析 / MinIO 存储 / AI 评分 / SHA-256 去重） |
| P11 | 已完成 | 前端 Vite + React 暗色编辑风格 UI |
| P12 | 已完成 | 知识库 RAG（pgvector / Embedding / 分块检索） |

## 待推进

| 阶段 | 状态 | 内容 |
|------|------|------|
| P13 | **已完成** | 面试-简历关联度优化（prompt 重构 + 全文注入） |
| P14 | 计划 | 知识库 RAG 实战（面经 / 技术文档检索辅助出题） |
| P15 | 计划 | 语音面试（WebSocket + ASR/TTS） |

## API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/chat/stream` | POST | SSE 流式对话（打字机效果） |
| `/api/chat` | POST | 发送消息，支持多轮对话（sessionId） |
| `/api/chat/analyze` | POST | 发送消息，返回结构化分析结果 |
| `/api/interview/start` | POST | 开始面试（可选 resumeId 关联简历） |
| `/api/interview/answer` | POST | 提交答案 + AI 评估 |
| `/api/interview/next` | POST | 获取下一题 |
| `/api/interview/report` | POST | 生成面试报告 |
| `/api/chat/session` | POST/GET | 创建/获取聊天会话 |
| `/api/chat/message` | POST/GET | 发送/获取消息 |
| `/api/resumes/upload` | POST | 上传简历 |
| `/api/resumes` | GET | 简历列表 |
| `/api/resumes/{id}` | GET/DELETE | 简历详情/删除 |

---

## 技术栈

| 组件 | 选型 |
|------|------|
| 语言 | Java 21 |
| 框架 | Spring Boot 4.0.6 + Spring AI 2.0.0-M8 |
| 前端 | Vite 5 + React 18 + Motion |
| 数据库 | pgvector/pgvector:pg16 |
| ORM | Spring Data JPA |
| AI | 通义千问 DashScope（兼容 OpenAI 格式） |
| 文档解析 | Apache Tika 2.9.2 |
| 存储 | MinIO (S3 兼容) |
| 队列 | Redis Stream |
