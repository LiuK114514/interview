# AI Interview Guide

基于 Spring AI 构建的 AI 智能面试平台，支持多轮对话、AI 出题、智能评估和面试报告生成。

## 功能

- **AI 聊天** — 多轮对话，支持会话记忆持久化
- **结构化输出** — AI 返回 JSON 并通过重试机制保证格式正确
- **智能出题** — 根据技能和难度动态生成面试题目
- **答案评估** — AI 实时评分并给出反馈和参考答案
- **面试报告** — 汇总所有答题情况生成综合评估报告

## 架构

```
interview/
├── config/           # Spring AI 手动配置
├── common/           # 通用能力（统一响应、异常处理、结构化输出）
├── chat/             # 聊天模块（控制器、实体、仓储、记忆）
├── interview/        # 面试业务（出题→答题→报告完整链路）
└── resources/        # 配置（PostgreSQL / H2）
```

## 快速开始

### 使用 H2 内存数据库（无需外部依赖）

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

### 使用 PostgreSQL

```bash
docker-compose up -d
./mvnw spring-boot:run
```

### 配置 AI

在 `src/main/resources/.env` 或环境变量中设置：

```env
AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
AI_API_KEY=your-api-key
AI_MODEL=qwen3.5-flash
```

兼容任何 OpenAI API 格式的服务（阿里云百炼、DeepSeek、OpenAI 等）。

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 发送消息，支持多轮对话 |
| POST | `/api/chat/analyze` | 发送消息，返回结构化分析 |
| POST | `/api/interview/start` | 创建面试会话，AI 出题 |
| POST | `/api/interview/answer` | 提交答案，AI 实时评估 |
| POST | `/api/interview/next` | 获取下一题或标记完成 |
| POST | `/api/interview/report` | 生成面试报告 |

## 技术栈

- **Spring Boot 4.0** + **Java 21**
- **Spring AI 2.0** — AI 模型调用
- **Spring Data JPA** — 持久化
- **PostgreSQL / H2** — 数据库
- **HikariCP** — 连接池

## 学习路线

本项目按照 Phase 组织，从基础到完整业务逐步构建：

1. **P1-P2**: 基础 REST API + 统一异常处理
2. **P3-P4**: 多轮对话 + 结构化输出
3. **P5-P6**: 手动配置 + 结构化输出重试
4. **P7**: JPA 持久化
5. **P8**: 完整面试流程（出题→答题→报告）
6. **P9**: PostgreSQL + 基础设施升级
7. **P10+**: 简历管理、RAG、语音面试（规划中）

详细进度参见 [docs/current-progress.md](docs/current-progress.md)。
