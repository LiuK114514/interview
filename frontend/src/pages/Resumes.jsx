import { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { resumes as resumesApi } from '../api';

function fmtSize(bytes) {
  if (!bytes) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function fmtTime(t) {
  if (!t) return '—';
  return new Date(t).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  });
}

function scoreColor(s) {
  if (s >= 80) return 'var(--color-success)';
  if (s >= 60) return 'var(--color-warning)';
  return 'var(--color-danger)';
}

function statusBadge(status) {
  switch (status) {
    case 'COMPLETED': return <span className="badge badge-success">已完成</span>;
    case 'PENDING':   return <span className="badge badge-warning">分析中</span>;
    case 'FAILED':    return <span className="badge badge-danger">失败</span>;
    default:          return <span className="badge badge-info">{status || '—'}</span>;
  }
}

export default function Resumes() {
  const [resumeList, setResumeList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const fileRef = useRef(null);

  useEffect(() => { loadList(); }, []);

  async function loadList() {
    setLoading(true);
    setError(null);
    try {
      const data = await resumesApi.list();
      setResumeList(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleUpload(files) {
    const file = files?.[0];
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      await resumesApi.upload(file);
      await loadList();
    } catch (err) {
      setError(err.message);
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  }

  async function handleDetail(id) {
    setDetailLoading(true);
    setError(null);
    try {
      const data = await resumesApi.detail(id);
      setDetail(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setDetailLoading(false);
    }
  }

  async function handleDelete(id) {
    if (!confirm('确定删除这份简历？')) return;
    try {
      await resumesApi.delete(id);
      setDetail(null);
      await loadList();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleReanalyze(id) {
    try {
      await resumesApi.reanalyze(id);
      await handleDetail(id);
      await loadList();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div>
      <div className="page-header">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <h1>简历管理</h1>
            <p>上传简历进行 AI 智能分析，5 维度评分 + 改进建议</p>
          </div>
          <button className="btn btn-primary" onClick={() => fileRef.current?.click()} disabled={uploading}>
            {uploading ? '上传中...' : '+ 上传简历'}
          </button>
          <input ref={fileRef} type="file" accept=".pdf,.docx,.doc,.txt,.md" onChange={e => handleUpload(e.target.files)} style={{ display: 'none' }} />
        </div>
      </div>

      {error && (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}
          style={{ background: 'var(--color-danger-bg)', border: '1px solid rgba(231,76,60,0.2)', borderRadius: 'var(--radius-md)', padding: '0.75rem 1rem', marginBottom: '1rem', color: 'var(--color-danger)', fontSize: '0.9rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>{error}</span>
          <button className="btn btn-ghost btn-sm" onClick={() => setError(null)}>关闭</button>
        </motion.div>
      )}

      {/* Upload drop zone */}
      <motion.div className="card" style={{ marginBottom: '2rem', textAlign: 'center', padding: '2.5rem 1.5rem', borderStyle: 'dashed', cursor: 'pointer' }}
        whileHover={{ borderColor: 'var(--color-accent)', background: 'var(--color-accent-bg)' }}
        onClick={() => fileRef.current?.click()}>
        <div style={{ fontSize: '2.5rem', marginBottom: '0.75rem', opacity: 0.5 }}>📄</div>
        <h3 style={{ color: 'var(--color-text-secondary)', marginBottom: '0.5rem' }}>点击上传简历</h3>
        <p style={{ fontSize: '0.85rem', color: 'var(--color-text-muted)' }}>支持 PDF、DOCX、TXT、Markdown，最大 10MB</p>
      </motion.div>

      {/* Main content */}
      <div style={{ display: 'grid', gridTemplateColumns: detail ? '380px 1fr' : '1fr', gap: '1.5rem', alignItems: 'start' }}>
        {/* ========== LIST ========== */}
        <div>
          <h3 style={{ marginBottom: '0.75rem', fontFamily: 'var(--font-display)', fontSize: '1rem' }}>
            已上传简历 <span style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem', fontWeight: 400 }}>({resumeList.length})</span>
          </h3>

          {loading ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
              {[1, 2, 3].map(i => <div key={i} className="skeleton" style={{ height: 72, borderRadius: 'var(--radius-md)' }} />)}
            </div>
          ) : resumeList.length === 0 ? (
            <div className="empty-state" style={{ padding: '2rem 1rem' }}>
              <div className="empty-state-icon" style={{ fontSize: '2rem' }}>📋</div>
              <h3>暂无简历</h3>
              <p>上传简历开始 AI 分析</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              <AnimatePresence>
                {resumeList.map(r => (
                  <motion.div key={r.id} layout initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, scale: 0.95 }}
                    className="card" style={{ cursor: 'pointer', padding: '0.85rem 1rem', borderColor: detail?.id === r.id ? 'var(--color-accent)' : '' }}
                    whileHover={{ y: -1 }} onClick={() => handleDetail(r.id)}>
                    <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
                      {/* File icon */}
                      <div style={{
                        width: 36, height: 36, borderRadius: 'var(--radius-sm)', flexShrink: 0,
                        background: 'var(--color-accent-bg)', display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontSize: '1.1rem', color: 'var(--color-accent)',
                      }}>
                        {r.filename?.endsWith('.pdf') ? '📕' : r.filename?.endsWith('.docx') ? '📘' : '📄'}
                      </div>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div className="truncate" style={{ fontWeight: 500, fontSize: '0.9rem' }}>{r.filename || `简历 #${r.id}`}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', marginTop: '0.15rem' }}>
                          {fmtSize(r.fileSize)} · {fmtTime(r.uploadedAt)} · {statusBadge(r.analyzeStatus)}
                        </div>
                      </div>
                      {r.latestScore != null && (
                        <div style={{ textAlign: 'right', flexShrink: 0 }}>
                          <div style={{ fontFamily: 'var(--font-display)', fontSize: '1.25rem', fontWeight: 700, color: scoreColor(r.latestScore) }}>{r.latestScore}</div>
                          <div style={{ fontSize: '0.65rem', color: 'var(--color-text-muted)' }}>/100</div>
                        </div>
                      )}
                    </div>
                  </motion.div>
                ))}
              </AnimatePresence>
            </div>
          )}
        </div>

        {/* ========== DETAIL ========== */}
        <AnimatePresence mode="wait">
          {detail ? (
            <motion.div key={detail.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
              {detailLoading ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                  <div className="skeleton" style={{ height: 160, borderRadius: 'var(--radius-md)' }} />
                  <div className="skeleton" style={{ height: 300, borderRadius: 'var(--radius-md)' }} />
                </div>
              ) : (
                <>
                  {/* ===== Header Card ===== */}
                  <div className="card" style={{ marginBottom: '1rem', padding: '1.25rem 1.5rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1rem' }}>
                      <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
                        <div style={{ fontSize: '1.8rem' }}>
                          {detail.filename?.endsWith('.pdf') ? '📕' : detail.filename?.endsWith('.docx') ? '📘' : '📄'}
                        </div>
                        <div>
                          <h2 style={{ fontSize: '1.15rem', margin: 0, wordBreak: 'break-all' }}>{detail.filename}</h2>
                          <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginTop: '0.15rem' }}>
                            {detail.contentType} · {fmtSize(detail.fileSize)}
                          </div>
                        </div>
                      </div>
                      <div style={{ display: 'flex', gap: '0.4rem', flexShrink: 0 }}>
                        <button className="btn btn-ghost btn-sm" onClick={() => handleReanalyze(detail.id)} disabled={detail.analyzeStatus === 'PENDING'}>重新分析</button>
                        <button className="btn btn-danger btn-sm" onClick={() => handleDelete(detail.id)}>删除</button>
                      </div>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '0.5rem', fontSize: '0.82rem', color: 'var(--color-text-secondary)' }}>
                      <div><span style={{ color: 'var(--color-text-muted)' }}>上传时间</span><br />{fmtTime(detail.uploadedAt)}</div>
                      <div><span style={{ color: 'var(--color-text-muted)' }}>状态</span><br />{statusBadge(detail.analyzeStatus)}</div>
                      {detail.analyzeError && <div style={{ gridColumn: '1 / -1', color: 'var(--color-danger)' }}>错误：{detail.analyzeError}</div>}
                    </div>
                  </div>

                  {/* ===== Analysis Records ===== */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                    {detail.analyses?.length > 0 ? (
                      detail.analyses.map((a, i) => (
                        <div className="card" key={a.id || i} style={{ padding: '1.25rem 1.5rem' }}>
                          {/* Header */}
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                              <span className="badge badge-accent">第 {detail.analyses.length - i} 次分析</span>
                              <span style={{ fontSize: '0.78rem', color: 'var(--color-text-muted)' }}>{fmtTime(a.analyzedAt)}</span>
                            </div>
                            <div style={{ fontFamily: 'var(--font-display)', fontSize: '1.6rem', fontWeight: 700, color: scoreColor(a.overallScore) }}>
                              {a.overallScore}<span style={{ fontSize: '0.85rem', color: 'var(--color-text-muted)', fontWeight: 400 }}>/100</span>
                            </div>
                          </div>

                          {/* Score bars */}
                          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: '0.75rem', marginBottom: '1rem' }}>
                            {[
                              { label: '内容完整性', key: 'contentScore', max: 25 },
                              { label: '结构清晰度', key: 'structureScore', max: 20 },
                              { label: '技能匹配度', key: 'skillMatchScore', max: 25 },
                              { label: '表达专业性', key: 'expressionScore', max: 15 },
                              { label: '项目经验', key: 'projectScore', max: 15 },
                            ].map(dim => {
                              const val = a[dim.key] ?? 0;
                              const pct = (val / dim.max) * 100;
                              return (
                                <div key={dim.key}>
                                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', marginBottom: '0.2rem' }}>
                                    <span style={{ color: 'var(--color-text-secondary)' }}>{dim.label}</span>
                                    <span style={{ color: 'var(--color-text-muted)' }}>{val}/{dim.max}</span>
                                  </div>
                                  <div style={{ height: 4, background: 'var(--color-border)', borderRadius: 2, overflow: 'hidden' }}>
                                    <motion.div initial={{ width: 0 }} animate={{ width: `${pct}%` }} transition={{ duration: 0.8, ease: 'easeOut' }}
                                      style={{ height: '100%', borderRadius: 2, background: `linear-gradient(90deg, ${scoreColor(val * (100 / dim.max))}, var(--color-accent))` }} />
                                  </div>
                                </div>
                              );
                            })}
                          </div>

                          {/* Summary */}
                          {a.summary && (
                            <div style={{ fontSize: '0.88rem', lineHeight: 1.7, color: 'var(--color-text-secondary)', marginBottom: '1rem', padding: '0.75rem 1rem', background: 'var(--color-bg-inset)', borderRadius: 'var(--radius-md)' }}>
                              {a.summary}
                            </div>
                          )}

                          {/* Strengths & Suggestions */}
                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
                            {a.strengths?.length > 0 && (
                              <div>
                                <div style={{ fontSize: '0.78rem', color: 'var(--color-success)', fontWeight: 600, marginBottom: '0.4rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>优势</div>
                                <ul style={{ margin: 0, paddingLeft: '1.1rem', fontSize: '0.82rem', color: 'var(--color-text-secondary)', lineHeight: 1.8 }}>
                                  {a.strengths.map((s, j) => <li key={j}>{s}</li>)}
                                </ul>
                              </div>
                            )}
                            {a.suggestions?.length > 0 && (
                              <div>
                                <div style={{ fontSize: '0.78rem', color: 'var(--color-warning)', fontWeight: 600, marginBottom: '0.4rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>改进建议</div>
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                                  {a.suggestions.map((s, j) => (
                                    <div key={j} style={{ fontSize: '0.82rem', lineHeight: 1.5, padding: '0.5rem 0.65rem', background: 'var(--color-bg-inset)', borderRadius: 'var(--radius-sm)' }}>
                                      <div style={{ display: 'flex', gap: '0.4rem', alignItems: 'center', marginBottom: '0.2rem' }}>
                                        <span className={`badge ${s.priority === 'high' ? 'badge-danger' : s.priority === 'medium' ? 'badge-warning' : 'badge-info'}`}>
                                          {s.priority === 'high' ? '高优先级' : s.priority === 'medium' ? '中优先级' : '建议'}
                                        </span>
                                        {s.category && <span style={{ fontSize: '0.7rem', color: 'var(--color-text-muted)' }}>{s.category}</span>}
                                      </div>
                                      <div style={{ color: 'var(--color-text-secondary)' }}>{s.issue || s.recommendation || s}</div>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            )}
                          </div>
                        </div>
                      ))
                    ) : (
                      <div className="card" style={{ textAlign: 'center', padding: '2.5rem 1.5rem' }}>
                        <div style={{ fontSize: '2.5rem', marginBottom: '0.75rem', opacity: 0.3 }}>🔍</div>
                        <h3 style={{ color: 'var(--color-text-secondary)', marginBottom: '0.5rem', fontSize: '1rem' }}>暂无分析记录</h3>
                        <p style={{ fontSize: '0.85rem', color: 'var(--color-text-muted)' }}>
                          {detail.analyzeStatus === 'PENDING' ? 'AI 正在分析中，请稍候...' : '点击"重新分析"按钮开始 AI 评分'}
                        </p>
                      </div>
                    )}
                  </div>
                </>
              )}
            </motion.div>
          ) : (
            <motion.div key="empty-detail" initial={{ opacity: 0 }} animate={{ opacity: 1 }}
              className="card" style={{ textAlign: 'center', padding: '3rem 1.5rem', minHeight: 300, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
              <div style={{ fontSize: '3rem', marginBottom: '1rem', opacity: 0.2 }}>📋</div>
              <h3 style={{ color: 'var(--color-text-secondary)', marginBottom: '0.5rem' }}>选择一份简历</h3>
              <p style={{ fontSize: '0.85rem', color: 'var(--color-text-muted)' }}>点击左侧简历查看详细分析和评分</p>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
