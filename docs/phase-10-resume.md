# Phase 10：简历管理设计

## 文件处理流程

```
MultipartFile 上传
  │
  ├─ 1. validateFile()           → 非空 + ≤10MB + MIME 白名单校验
  ├─ 2. calculateHash()          → SHA-256 去重
  ├─ 3. detectContentType()      → Tika MIME 检测
  ├─ 4. parseContent()           → Tika 解析为纯文本
  ├─ 5. uploadToMinio()          → MinIO 存储（key: resumes/{uuid}_{filename}）
  ├─ 6. ResumeRepository.save()  → JPA 持久化（状态 PENDING）
  ├─ 7. 事务提交后 → Redis Stream 发布消息（resumeId）
  ├─ 8. ResumeAnalysisConsumer 消费 → AI 评分 → 状态 COMPLETED
  └─ 9. ResumeIndexConsumer 消费   → 分块→向量化→存储（RAG 索引）
```

## 去重策略：文件内容哈希

```java
String fileHash = documentParserService.calculateHash(file);
Optional<ResumeEntity> existing = resumeRepository.findByFileHash(fileHash);
```
命中时直接返回历史分析结果 + `accessCount++`，不重复上传和 AI 调用。

## AI 评分维度（满分 100）

| 维度 | 满分 | 评估内容 |
|------|------|---------|
| contentScore | 25 | 内容完整性——教育背景、工作经历、技能列表 |
| structureScore | 20 | 结构清晰度——排版、层次、可读性 |
| skillMatchScore | 25 | 技能匹配度——技能描述的具体程度与相关性 |
| expressionScore | 15 | 表达专业性——语言是否专业、量化成果 |
| projectScore | 15 | 项目经验——项目描述是否清晰、有深度 |

## 同步 → 异步 AI 分析（Redis Stream）

| | 之前（同步） | 现在（异步） |
|---|---|---|
| 响应速度 | 慢（等 AI 分析完才返回） | 快（立即返回 PENDING） |
| 实现复杂度 | 低（一个 Service） | 中（Redis Stream + 双消费者） |
| 事务一致性 | ✅ 天然 | ✅ `TransactionSynchronizationManager.afterCommit()` 保证 |
| 失败重试 | 无 | ✅ Redis Pending List 自动重试 |
| 扩展性 | 单线程 | ✅ 可水平扩展消费者 |

## Redis Stream 架构

```
uploadAndAnalyze()  [@Transactional]
  │
  ├─ 保存简历到 DB
  ├─ 注册 TransactionSynchronization
  └─ afterCommit() → Redis Stream publish { resumeId }
                        │
                        ├── Consumer Group: resume-group
                        │     └── ResumeAnalysisConsumer
                        │           ├─ processAnalysis() → AI 评分
                        │           └─ acknowledge()
                        │
                        └── Consumer Group: resume-index-group
                              └── ResumeIndexConsumer
                                    ├─ processIndex() → 分块→向量化
                                    └─ acknowledge()
```

关键设计点：两个消费者在不同 Consumer Group，同一消息会**广播**给两个 Group，实现 AI 评分和 RAG 索引的并行处理。

## 错误码分层（2xxx 简历域）

| 错误码 | 含义 | 触发场景 |
|--------|------|---------|
| 2001 | 简历不存在 | GET/DELETE 不存在的简历 |
| 2002 | 上传失败 | MinIO 写失败 |
| 2003 | 解析失败 | Tika 无法提取文本（如扫描版 PDF） |
| 2004 | AI 评分失败 | AI 调用异常 |
| 2005 | 文件过大 | 超过 10MB |
| 2006 | 类型不支持 | 非 PDF/DOCX/TXT/MD |

## 配置项

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
