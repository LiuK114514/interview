# 已完成模块一览

截止 P12 共计 **59 个 Java + 9 个前端源文件**，对比 interview-guide 的 173 个，精简 61%。

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
| **P10** | **简历管理（Redis Stream 异步改造）** | **4 新建 + 3 改** | 上传→Redis Stream→消费者异步 AI 评分 + RAG 索引；去重 |
| **P11** | **前端应用（Vite + React）** | **9 源文件** | 暗色编辑风格 UI，覆盖全部 API |
| **P12** | **知识库 RAG（pgvector + Embedding）** | **5 新建 + 9 改** | 简历文本分块→向量化→余弦相似检索→面试/报告注入语境 |

## API 端点一览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 发送消息，支持多轮对话（sessionId） |
| POST | `/api/chat/analyze` | 发送消息，返回结构化分析结果 |
| POST | `/api/interview/start` | 创建面试会话，AI 出题 |
| POST | `/api/interview/answer` | 提交答案，AI 实时评估 |
| POST | `/api/interview/next` | 获取下一题或标记完成 |
| POST | `/api/interview/report` | 生成面试报告 |
| POST | `/api/resumes/upload` | 上传简历（异步：Redis Stream → AI 评分 + RAG 索引） |
| GET | `/api/resumes` | 简历列表（含最新评分） |
| GET | `/api/resumes/{id}/detail` | 简历详情 + 历次分析历史 |
| DELETE | `/api/resumes/{id}` | 删除简历 |
| POST | `/api/resumes/{id}/reanalyze` | 重新 AI 分析 |
