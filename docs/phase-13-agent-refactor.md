# Phase 13：AI 面试官 — 从应用到 Agent 的重构方案

## 一、思维转变

### 旧模型：简历管理系统（AI Application）

```
用户操作按钮 → Controller → Service → LLM（当成函数）→ 返回数据
```

核心隐喻：**一个业务系统**。用户在不同页面点不同按钮，每条 API 调用孤立，流程由 Java 代码硬编码。

### 新模型：AI 面试官（Agent）

```
用户交代任务 → AiInterviewer Agent → 自主决策 → 调用工具 → 观察结果 → 下一步
```

核心隐喻：**一个真实的人**。Agent 拥有面试官的身份、知识和工具，自主决定面试节奏和深度。

---

## 二、新架构总览

```
┌─────────────────────────────────────────────────────────┐
│                     AiInterviewer Agent                  │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐ │
│  │ Identity  │  │ Memory   │  │ Tools    │  │        │ │
│  │（角色设定）│  │（记忆）   │  │（工具集） │  │ LLM   │ │
│  └──────────┘  └──────────┘  └──────────┘  │（大脑） │ │
│                                            └────────┘ │
└─────────────────────────────────────────────────────────┘
         │                        │
         │ 工具调用                │ 自主决策
         ▼                        ▼
  ┌──────────────┐        ┌──────────────┐
  │ 现有 Service  │        │  用户的输入   │
  │（降级为 Tool）│        │ （任务而非操作）│
  └──────────────┘        └──────────────┘
```

### 核心变化

| 组件 | 当前角色 | Agent 角色 |
|------|---------|-----------|
| `InterviewService` | 编排面试流程 | **拆分**为多个 Tool 方法 |
| `ResumeService` | 简历 CRUD | 保留 CRUD + 暴露 `readResume` Tool |
| `ResumeKnowledgeService` | 知识库检索 | 暴露 `searchKnowledge` Tool |
| Controller | 接收操作请求 | 接收**任务请求**（意图），转发给 Agent |
| Chat | 独立对话模块 | 成为 Agent 与用户的**交互通道** |
| 前端 | 功能页面 + 按钮 | **对话界面 + 状态可视化** |

---

## 三、Agent 主体设计

### 3.1 角色设定（System Prompt）

AiInterviewer 的身份指令，加载到 ChatClient 的 system text：

```
你是一位资深技术面试官，名叫 AI 面试官。
你正直、专业、有洞察力。

你的工作方式：
1. 当用户给你一份简历时，你先仔细阅读，了解候选人的技术栈和项目经历
2. 你自主决定面试的方向、节奏和深度
3. 你出题后会自我检查：这道题是否和简历相关内容？是否足够有区分度？
4. 你根据候选人的回答动态调整：答得好就深入追问，答得不好就换基础题
5. 你觉得收集到足够信息后，主动生成面试报告

你拥有的能力（工具）：
- readResume：查看候选人简历全文
- searchKnowledge：查阅面经/技术资料
- generateQuestions：出题
- evaluateAnswer：评估回答
- probeDeeper：针对某个知识点追问
- generateReport：生成面试报告
```

### 3.2 记忆系统

Agent 需要短期记忆（当前面试上下文）和长期记忆（候选人档案）：

```
AiInterviewer Memory:
├── 当前面试会话
│   ├── 候选人简历摘要（已读）
│   ├── 已出的题目列表
│   ├── 已回答的记录（题目+答案+评分）
│   └── 当前进度（第几题、面了哪些方向）
│
└── 面试官知识
    ├── 技术面经（从知识库检索）
    └── 面试方法论（system prompt 固化）
```

### 3.3 决策循环

每次用户输入，Agent 执行 Observe → Think → Act → Observe 循环：

```
用户输入
   │
   ▼
┌─────────────────────┐
│  Observe            │
│  理解用户意图        │
└─────────┬───────────┘
          ▼
┌─────────────────────┐
│  Think              │
│  判断当前状态        │
│  决定下一步动作      │
└─────────┬───────────┘
          ▼
┌─────────────────────┐
│  Act (Tool Call)    │
│  调用工具或直接回复  │
└─────────┬───────────┘
          ▼
┌─────────────────────┐
│  Observe Result     │
│  看工具返回了什么    │
└─────────┬───────────┘
          ▼
      继续或结束
```

---

## 四、工具集设计

每个工具对应一个 Spring Bean 或 @Tool 注解方法，LLM 自主决定调用时机和参数。

### 4.1 readResume

```java
@Tool(description = "查看候选人简历全文，了解其技术栈、项目经历和教育背景")
String readResume(Long resumeId)
```

- 输入：简历 ID
- 输出：简历全文文本（不含敏感元数据）
- 调用时机：Agent 接手面试时的第一步

### 4.2 searchKnowledge

```java
@Tool(description = "查阅面试知识库，搜索面经、技术文章或特定知识点")
String searchKnowledge(String query, int limit)
```

- 复用现有 `ResumeKnowledgeService.searchRelevantContext`（但目标改为知识库而非简历）
- 调用时机：Agent 对某个技术点拿不准，或想参考面经出题

### 4.3 generateQuestions

```java
@Tool(description = "基于对候选人简历的了解，生成一组面试题")
List<QuestionDTO> generateQuestions(String skillId, String difficulty, int count)
```

- 与现有出题逻辑类似，但调用时机由 Agent 决定
- Agent 可以先 readResume，再决定从哪个方向出题

### 4.4 evaluateAnswer

```java
@Tool(description = "评估候选人对某道题的回答，返回评分和反馈")
AnswerEvaluationDTO evaluateAnswer(String question, String userAnswer)
```

### 4.5 probeDeeper

```java
@Tool(description = "针对某个知识点或技术点深入追问")
String probeDeeper(String topic, String context)
```

- 新工具！现有系统没有
- Agent 觉得候选人答得浅，或者发现某个简历提到的技术很有意思，主动追问

### 4.6 generateReport

```java
@Tool(description = "面试结束，生成综合评估报告。调用此工具意味着面试终结")
InterviewReportDTO generateReport()
```

---

## 五、交互流程对比

### 当前（操作式）

```
用户: POST /api/interview/start  { skillId, difficulty, resumeId }
  → 出题 → 返回 5 道题

用户: POST /api/interview/answer { answer }
  → 评估 → 返回评分

用户: POST /api/interview/next
  → 返回下一题

用户: POST /api/interview/report
  → 生成报告
```

用户负责**编排**每一步，系统只是执行单元。

### Agent 模式（任务式）

```
用户: "帮我看一下这份简历，然后面试这个候选人"
  → Agent.readResume()
  → Agent: "这位候选人擅长 Java Spring，有 3 年微服务经验，我先从项目经验开始问"
  → Agent.generateQuestions()
  → Agent: "第一题：你在 xx 项目中提到用了 CQRS，能讲讲为什么选这个模式吗？"

用户: "他答得一般"
  → Agent.evaluateAnswer()  // 评估已有回答
  → Agent: "确实，这部分他理解不够深，我换个角度再问问"
  → Agent.generateQuestions() 或 probeDeeper()

用户: "差不多了"
  → Agent.generateReport()
  → Agent: "这是面试报告..."
```

Agent 自主推进流程，用户只需要表达意图。

---

## 六、前端变化

### 当前

多页面、多表单、多按钮的 CRUD 风格界面。

### Agent 模式

```
┌─────────────────────────────────────────┐
│  AI 面试官                                 │
│  ┌─────────────────────────────────────┐ │
│  │   Agent 消息流（类似 Chat 界面）      │ │
│  │   - 面试官说：我先看看简历            │ │
│  │   - 面试官说：好的，开始面试          │ │
│  │   - 面试官问：第一题...               │ │
│  │   - 你回答：...                       │ │
│  │   - 面试官评估：答得不错，深入问一下  │ │
│  │   - 报告生成                          │ │
│  └─────────────────────────────────────┘ │
│  ┌─────────────────────────────────────┐ │
│  │   输入框（支持自由对话 + 快捷操作）   │ │
│  │   [提交简历] [查看进度] [生成报告]   │ │
│  └─────────────────────────────────────┘ │
│  ┌─────────────────────────────────────┐ │
│  │   侧边面板：面试状态可视化            │ │
│  │   - 当前进度 (3/8 题)               │ │
│  │   - 已覆盖技能标签                    │ │
│  │   - 实时评分趋势                      │ │
│  └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

核心变化：从"页面驱动的操作界面"变为"对话驱动的协作界面"。

---

## 七、重构路线（建议分步）

### Step 1：Agent 基础设施（不改现有功能）

- 引入 Spring AI 的 Tool Calling 支持
- 创建 `AiInterviewerAgent` 类，封装 Agent 循环
- 将 `InterviewService` 的方法暴露为 `@Tool`
- 新增一个 `/api/interview/agent/chat` 端点，接收自然语言输入

> 此时现有 API 仍可用，Agent 端点是平行新增

### Step 2：Agent 接管出题流程

- 前端增加 Agent 对话入口
- Agent 自主 readResume → generateQuestions 代替现有 `startInterview`
- `probeDeeper` 工具上线

### Step 3：Agent 接管全流程

- 面试、评估、报告全由 Agent 编排
- 旧 API 降级为可选兼容层
- 前端改造为对话 + 状态可视化

### Step 4：知识库整合

- `searchKnowledge` 工具接入面经/技术文档
- Agent 出题时能从知识库获取参考，出更有针对性的题

---

## 八、需要评估的风险

| 风险 | 说明 | 缓解措施 |
|------|------|---------|
| 成本增加 | Agent 多轮思考消耗大量 token | 设定 Agent 最大步数限制；使用快速模型做决策，慢速模型做评估 |
| 不确定性 | 同样的输入可能走不同路径 | 关键的评估/报告路径做结构化输出兜底 |
| 调试困难 | Agent 的决策链难以复现 | 记录完整的 Agent 日志（thought + action + observation） |
| 用户体验 | 对话式交互可能让用户困惑 | 保留快捷操作按钮；Agent 透明展示自己的思考过程 |

---

## 九、与现有架构的关系

```
                          ┌──────────────────┐
                          │  AiInterviewer    │
                          │   Agent           │
                          └────────┬─────────┘
                                   │ 工具调用
          ┌────────────────────────┼────────────────────┐
          │                        │                     │
          ▼                        ▼                     ▼
  ┌──────────────┐      ┌──────────────────┐   ┌─────────────┐
  │ Interview     │      │ Resume           │   │ Knowledge   │
  │ Service/Tools │      │ Service/Tools    │   │ Service     │
  └──────────────┘      └──────────────────┘   └─────────────┘
          │                        │                     │
          ▼                        ▼                     ▼
  ┌─────────────────────────────────────────────────────────┐
  │               Data Layer (Entity / Repository)          │
  └─────────────────────────────────────────────────────────┘
```

现有 Service 层**不需要重写**，只需要包装为 `@Tool` 方法供 Agent 调用。Data Layer 完全不变。
