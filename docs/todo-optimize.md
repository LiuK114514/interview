# 待优化点

> 记录未来需要优化和改进的功能点，暂不实现。

---

## 1. ~~AI 问答流式输出（SSE）~~ ✅ 已完成

**完成内容（2026-06-04）：**
- 后端：`ChatController` 新增 `POST /api/chat/stream` 端点，基于 `SseEmitter` + `ChatClient.stream().content()` 实现
- 事件协议：三个命名事件 — `message`（逐字内容）、`done`（携带 sessionId）、`error`（携带错误信息）
- 前端：`api/index.js` 新增 `sendStream()`，基于 `fetch` + `ReadableStream` 逐行解析 SSE
- 前端：`Chat.jsx` 流式渲染打字机效果，使用 `ref` 累积流式文本避免因频繁 setState 丢失片段
- Markdown 实时渲染：流式文本动态解析，代码块/表格等跟随逐段显示

**涉及文件：**
- `ChatController.java` — SseEmitter 端点（+15 行）
- `api/index.js` — sendStream SSE 客户端
- `Chat.jsx` — 流式打字机 UI（streamingText + ref 防丢）

---

## 2. ~~AI 面试增加"根据简历面试"功能~~ ✅ 已完成（但方案已变更）

**现状：** 最初通过 RAG 检索简历片段注入 prompt，但简历文本短（500-2000 字），向量检索反而
引入 query 太短、竞态条件、信息丢失等问题。

**变更后的方案：** 出题时直接注入简历全文（`ResumeEntity.resumeText`），
RAG 能力保留给后续知识库场景。

**变更原因：**
- 简历全文直接喂 LLM，信息零损失
- 避免异步索引未完成时的空结果
- 代码更简单，效果更好

---

## 3. ~~优化简历-面试关联度（Phase 13，已完成）~~

~~**现状：** 已注入简历全文，但 system prompt 仍是"生成 5 道高质量面试题"的通用指令优先，
简历内容只是末尾附加，AI 出题时仍偏向出通用题。~~

**已完成的优化（2026-06-02）：**
- **数据源**：从向量检索 3 个片段改为直接注入简历全文（`ResumeEntity.resumeText`），信息零损失
- **有简历 prompt 重构**：system prompt 改为以简历内容为主导，LLM 看到的第一件事就是简历全文
  - 明确指令：题目必须围绕简历中提到的技术栈、项目经历和技能来出
  - 禁止出简历未涉及领域的题目
  - 如果包含项目经验，优先从项目经验中挖掘深度技术问题
- **无简历路径**：完全保持原有通用出题逻辑，两套 prompt 互不污染
- **报告评估**：同样注入简历全文，评分和反馈结合简历背景做个性化分析
- **清理**：移除了 `InterviewService` 中对 `ResumeKnowledgeService` 的依赖（不再需要向量检索简历）

---

## 4. 知识库 RAG 实战（Phase 14，计划）

**目标：** 将 RAG 应用到知识库文档（面经、技术文章、JD），而非简历自身。

**场景：**
- 面试出题时：从知识库检索相关面经/技术点，辅助 AI 出更有针对性的题目
- 答案评估时：从知识库检索参考答案，让评分更准确
- 知识库聊天：复用 `ResumeKnowledgeService` 的能力，但目标改为知识库文档

**涉及范围：**
- 知识库文档上传与向量化（复用现有的分块/向量化/检索能力）
- 新建 KnowledgeBase 模块（Controller / Service / Entity）
- 面试时从知识库 + 简历双来源检索注入 prompt
