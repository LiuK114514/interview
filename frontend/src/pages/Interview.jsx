import { useState, useRef, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { interview as interviewApi, resumes as resumesApi } from '../api';

const SKILLS = [
  { id: 'java', label: 'Java 基础', icon: '☕' },
  { id: 'spring', label: 'Spring 框架', icon: '🌱' },
  { id: 'system-design', label: '系统设计', icon: '🏗️' },
  { id: 'algorithm', label: '算法', icon: '🔢' },
  { id: 'database', label: '数据库', icon: '🗄️' },
  { id: 'general', label: '综合', icon: '🎯' },
];

const DIFFICULTIES = [
  { id: '初级', label: '初级', desc: '基础知识考查' },
  { id: '中级', label: '中级', desc: '原理理解 + 实践' },
  { id: '高级', label: '高级', desc: '深入原理 + 架构' },
];

const STORAGE_KEY = 'interview_state';

function scoreColor(s) {
  if (s >= 8) return 'var(--color-success)';
  if (s >= 5) return 'var(--color-warning)';
  return 'var(--color-danger)';
}

function saveState(state) {
  try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state)); } catch {}
}

function loadState() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch { return null; }
}

function clearState() {
  try { sessionStorage.removeItem(STORAGE_KEY); } catch {}
}

function updateUrl(sid) {
  const base = '/interview';
  const full = sid ? `${base}?sid=${sid}` : base;
  window.history.replaceState(null, '', full);
}

export default function Interview() {
  const [phase, setPhase] = useState('start');
  const [sessionId, setSessionId] = useState(null);
  const [skillId, setSkillId] = useState('');
  const [difficulty, setDifficulty] = useState('');
  const [resumeList, setResumeList] = useState([]);
  const [resumeId, setResumeId] = useState(null);
  const [questions, setQuestions] = useState([]);
  const [currentIdx, setCurrentIdx] = useState(0);
  const [answer, setAnswer] = useState('');
  const [feedback, setFeedback] = useState(null);
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const answerRef = useRef(null);
  const restored = useRef(false);

  // ----- Load resumes on start -----
  useEffect(() => {
    if (phase === 'start') {
      resumesApi.list().then(list => {
        setResumeList(list || []);
      }).catch(() => {});
    }
  }, [phase]);

  // ----- Restore session on mount -----
  useEffect(() => {
    if (restored.current) return;
    restored.current = true;

    const params = new URLSearchParams(window.location.search);
    const sid = params.get('sid');

    // Try restore from sessionStorage (preferred — has full state)
    const saved = loadState();
    if (saved && saved.sessionId) {
      setSessionId(saved.sessionId);
      setSkillId(saved.skillId || '');
      setDifficulty(saved.difficulty || '');
      setQuestions(saved.questions || []);
      setCurrentIdx(saved.currentIdx ?? 0);
      setPhase(saved.phase || 'start');
      if (saved.feedback) setFeedback(saved.feedback);
      if (saved.report) setReport(saved.report);
      if (saved.answer) setAnswer(saved.answer);
      return;
    }

    // Fallback: URL has sid but no sessionStorage — minimal restore
    if (sid) {
      setSessionId(sid);
    }
  }, []);

  // ----- Persist state on changes -----
  const persist = useCallback(() => {
    saveState({
      sessionId, skillId, difficulty, resumeId,
      questions, currentIdx, phase,
      feedback, report, answer,
    });
  }, [sessionId, skillId, difficulty, questions, currentIdx, phase, feedback, report, answer]);

  useEffect(() => { persist(); }, [persist]);

  useEffect(() => {
    if ((phase === 'questioning' || phase === 'feedback') && answerRef.current) {
      answerRef.current.focus();
    }
  }, [phase, currentIdx]);

  // ----- Start interview -----
  async function handleStart() {
    if (!skillId || !difficulty) return;
    setLoading(true);
    setError(null);
    try {
      const res = await interviewApi.start({ skillId, difficulty, resumeId });
      setSessionId(res.sessionId);
      setQuestions(res.questions || []);
      setCurrentIdx(0);
      updateUrl(res.sessionId);
      setPhase('questioning');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  // ----- Submit answer -----
  async function handleSubmitAnswer() {
    if (!answer.trim()) return;
    const qi = questions[currentIdx]?.index ?? currentIdx;
    setLoading(true);
    setError(null);
    try {
      const res = await interviewApi.answer({ sessionId, questionIndex: qi, answer });
      setFeedback(res);
      setPhase('feedback');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  // ----- Next / Finish -----
  async function handleNext() {
    setLoading(true);
    setError(null);
    setFeedback(null);
    setAnswer('');
    try {
      const res = await interviewApi.next({ sessionId });
      if (res.completed) {
        // Set phase to report FIRST so the user sees loading state
        setPhase('report');
        try {
          const r = await interviewApi.report({ sessionId });
          setReport(r);
        } catch (err) {
          setError(err.message || '报告生成失败，请稍后重试');
        }
      } else if (res.question) {
        setQuestions(prev => [...prev, res.question]);
        setCurrentIdx(prev => prev + 1);
        setPhase('questioning');
      } else {
        setError('获取下一题失败：返回数据异常');
      }
    } catch (err) {
      setError(err.message);
      // Re-show feedback on failure
      setPhase('feedback');
    } finally {
      setLoading(false);
    }
  }

  async function handleGenerateReport() {
    setLoading(true);
    setError(null);
    try {
      const r = await interviewApi.report({ sessionId });
      setReport(r);
      setPhase('report');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  function handleReset() {
    clearState();
    updateUrl(null);
    setPhase('start');
    setSessionId(null);
    setSkillId('');
    setDifficulty('');
    setResumeId(null);
    setQuestions([]);
    setCurrentIdx(0);
    setAnswer('');
    setFeedback(null);
    setReport(null);
    setError(null);
  }

  const currentQuestion = questions[currentIdx];
  const totalQ = questions.length;

  return (
    <div>
      <div className="page-header">
        <h1>AI 模拟面试</h1>
        <p>真实面试模拟，AI 出题、实时评估、生成报告</p>
      </div>

      {error && (
        <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}
          style={{ background: 'var(--color-danger-bg)', border: '1px solid rgba(231,76,60,0.2)', borderRadius: 'var(--radius-md)', padding: '0.75rem 1rem', marginBottom: '1rem', color: 'var(--color-danger)', fontSize: '0.9rem' }}>
          {error}
        </motion.div>
      )}

      {/* Restore hint */}
      {phase !== 'start' && sessionId && (
        <div style={{ fontSize: '0.78rem', color: 'var(--color-text-muted)', marginBottom: '0.75rem', textAlign: 'right' }}>
          会话 ID: {sessionId} · 切回其他页面后回来自动恢复
        </div>
      )}

      <AnimatePresence mode="wait">
        {phase === 'start' && (
          <motion.div key="start" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -20 }}>
            <div className="card" style={{ maxWidth: 640, margin: '0 auto' }}>
              <h2 style={{ marginBottom: '0.5rem' }}>选择面试方向</h2>
              <p style={{ color: 'var(--color-text-secondary)', marginBottom: '1.5rem', fontSize: '0.9rem' }}>选择一个技术方向和难度级别，AI 将生成针对性面试题。</p>

              <div style={{ fontSize: '0.85rem', color: 'var(--color-text-secondary)', marginBottom: '0.6rem', fontFamily: 'var(--font-display)', letterSpacing: '0.03em', textTransform: 'uppercase' }}>技能方向</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: '0.6rem', marginBottom: '1.5rem' }}>
                {SKILLS.map(s => (
                  <motion.button key={s.id} whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}
                    className={`btn ${skillId === s.id ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => setSkillId(s.id)}
                    style={{ justifyContent: 'flex-start', fontSize: '0.85rem', padding: '0.7rem 0.85rem' }}>
                    <span>{s.icon}</span> {s.label}
                  </motion.button>
                ))}
              </div>

              {resumeList.length > 0 && (
                <>
                  <div style={{ fontSize: '0.85rem', color: 'var(--color-text-secondary)', marginBottom: '0.6rem', marginTop: '0.5rem', fontFamily: 'var(--font-display)', letterSpacing: '0.03em', textTransform: 'uppercase' }}>
                    关联简历 <span style={{ fontSize: '0.72rem', opacity: 0.6, fontWeight: 400, textTransform: 'none' }}>（选填 — AI 将根据简历内容出题）</span>
                  </div>
                  <div style={{ marginBottom: '1.5rem' }}>
                    <button
                      className={`btn ${resumeId === null ? 'btn-primary' : 'btn-secondary'}`}
                      onClick={() => setResumeId(null)}
                      style={{ marginRight: '0.5rem', marginBottom: '0.4rem', fontSize: '0.82rem' }}>
                      不关联
                    </button>
                    {resumeList.map(r => (
                      <button key={r.id}
                        className={`btn ${resumeId === r.id ? 'btn-primary' : 'btn-secondary'}`}
                        onClick={() => setResumeId(r.id)}
                        style={{ marginRight: '0.5rem', marginBottom: '0.4rem', fontSize: '0.82rem' }}>
                        {r.filename} {r.latestScore != null ? `(${r.latestScore}分)` : ''}
                      </button>
                    ))}
                  </div>
                </>
              )}

              <div style={{ fontSize: '0.85rem', color: 'var(--color-text-secondary)', marginBottom: '0.6rem', fontFamily: 'var(--font-display)', letterSpacing: '0.03em', textTransform: 'uppercase' }}>难度级别</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '0.6rem', marginBottom: '2rem' }}>
                {DIFFICULTIES.map(d => (
                  <motion.button key={d.id} whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}
                    className={`btn ${difficulty === d.id ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => setDifficulty(d.id)}
                    style={{ flexDirection: 'column', gap: '0.2rem', padding: '0.85rem 0.5rem', lineHeight: 1.3 }}>
                    <span style={{ fontWeight: 600 }}>{d.label}</span>
                    <span style={{ fontSize: '0.72rem', opacity: 0.7, fontWeight: 400 }}>{d.desc}</span>
                  </motion.button>
                ))}
              </div>

              <button className="btn btn-primary btn-lg" style={{ width: '100%' }}
                disabled={!skillId || !difficulty || loading} onClick={handleStart}>
                {loading ? <><span className="spinner" style={{ width: 18, height: 18, borderWidth: 2 }} /> 出题中...</> : '开始面试'}
              </button>
            </div>
          </motion.div>
        )}

        {phase === 'questioning' && currentQuestion && (
          <motion.div key={`q-${currentIdx}`} initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -20 }} style={{ maxWidth: 700, margin: '0 auto' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.5rem' }}>
              <div className="badge badge-accent">第 {currentIdx + 1} 题</div>
              <div style={{ flex: 1, height: 3, background: 'var(--color-border)', borderRadius: 2, overflow: 'hidden' }}>
                <div style={{ width: `${(currentIdx / 5) * 100}%`, height: '100%', background: 'var(--color-accent)', borderRadius: 2, transition: 'width 0.5s ease' }} />
              </div>
              <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>共 5 题</span>
            </div>

            {currentQuestion.category && (
              <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', marginBottom: '0.75rem' }}>
                分类：<span className="badge badge-info">{currentQuestion.category}</span>
              </div>
            )}

            <div className="card" style={{ marginBottom: '1.5rem', position: 'relative', overflow: 'hidden' }}>
              <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 2, background: 'linear-gradient(90deg, var(--color-accent), var(--color-accent-dim))' }} />
              <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginBottom: '0.75rem', fontFamily: 'var(--font-display)', letterSpacing: '0.05em', textTransform: 'uppercase' }}>面试题目</div>
              <p style={{ fontSize: '1.15rem', lineHeight: 1.7 }}>{currentQuestion.question}</p>
            </div>

            <div className="card" style={{ marginBottom: '1rem' }}>
              <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginBottom: '0.75rem', fontFamily: 'var(--font-display)', letterSpacing: '0.05em', textTransform: 'uppercase' }}>你的回答</div>
              <textarea ref={answerRef} className="input" value={answer} onChange={e => setAnswer(e.target.value)}
                placeholder="在此输入你的回答..." rows={6}
                onKeyDown={e => { if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') handleSubmitAnswer(); }} />
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '0.75rem' }}>
                <span style={{ fontSize: '0.78rem', color: 'var(--color-text-muted)' }}>⌘+Enter 快捷提交</span>
                <button className="btn btn-primary" disabled={!answer.trim() || loading} onClick={handleSubmitAnswer}>
                  {loading ? '评估中...' : '提交回答'}
                </button>
              </div>
            </div>
          </motion.div>
        )}

        {phase === 'feedback' && feedback && (
          <motion.div key={`fb-${currentIdx}`} initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -20 }} style={{ maxWidth: 700, margin: '0 auto' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem', marginBottom: '1.5rem' }}>
              <div className="card" style={{ textAlign: 'center', padding: '1rem 1.5rem' }}>
                <div style={{ fontSize: '0.72rem', color: 'var(--color-text-muted)', marginBottom: '0.15rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>本题得分</div>
                <div style={{ fontFamily: 'var(--font-display)', fontSize: '2.5rem', fontWeight: 700, color: scoreColor(feedback.score) }}>
                  {feedback.score}<span style={{ fontSize: '1rem', color: 'var(--color-text-muted)', fontWeight: 400 }}>/10</span>
                </div>
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: '0.78rem', color: 'var(--color-text-muted)', marginBottom: '0.5rem', fontFamily: 'var(--font-display)', letterSpacing: '0.05em', textTransform: 'uppercase' }}>进度</div>
                <div style={{ display: 'flex', gap: '0.35rem' }}>
                  {Array.from({ length: 5 }).map((_, i) => (
                    <div key={i} style={{
                      flex: 1, height: 4, borderRadius: 2,
                      background: i <= currentIdx ? (i < currentIdx ? 'var(--color-success)' : 'var(--color-accent)') : 'var(--color-border)',
                    }} />
                  ))}
                </div>
                <div style={{ fontSize: '0.78rem', color: 'var(--color-text-muted)', marginTop: '0.3rem' }}>
                  已完成 {currentIdx}/5 题
                </div>
              </div>
            </div>

            <div className="card" style={{ marginBottom: '1rem', position: 'relative', overflow: 'hidden' }}>
              <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 2, background: 'linear-gradient(90deg, var(--color-accent), var(--color-accent-dim))' }} />
              <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginBottom: '0.75rem', fontFamily: 'var(--font-display)', letterSpacing: '0.05em', textTransform: 'uppercase' }}>AI 评估</div>
              <p style={{ lineHeight: 1.7, whiteSpace: 'pre-wrap' }}>{feedback.feedback}</p>
            </div>

            {feedback.correctAnswer && (
              <div className="card" style={{ marginBottom: '1.5rem', background: 'var(--color-info-bg)', border: '1px solid rgba(108,140,255,0.15)' }}>
                <div style={{ fontSize: '0.8rem', color: 'var(--color-info)', marginBottom: '0.5rem', fontFamily: 'var(--font-display)', letterSpacing: '0.05em', textTransform: 'uppercase' }}>参考答案要点</div>
                <p style={{ fontSize: '0.9rem', lineHeight: 1.7, whiteSpace: 'pre-wrap', color: 'var(--color-text-secondary)' }}>{feedback.correctAnswer}</p>
              </div>
            )}

            <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'space-between' }}>
              <button className="btn btn-secondary" onClick={handleReset}>结束面试</button>
              <div style={{ display: 'flex', gap: '0.75rem' }}>
                <button className="btn btn-ghost" onClick={handleGenerateReport} disabled={loading}>生成报告</button>
                <button className="btn btn-primary" onClick={handleNext} disabled={loading}>
                  {loading ? '加载中...' : currentIdx >= 4 ? '完成面试 →' : '下一题 →'}
                </button>
              </div>
            </div>
          </motion.div>
        )}

        {phase === 'report' && (
          <motion.div key="report" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -20 }} style={{ maxWidth: 700, margin: '0 auto' }}>
            <div className="card" style={{ marginBottom: '1.5rem', position: 'relative', overflow: 'hidden', textAlign: 'center', padding: '2rem 1.5rem' }}>
              <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 2, background: 'linear-gradient(90deg, var(--color-accent), var(--color-accent-dim))' }} />
              {!report ? (
                <>
                  <div style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>⏳</div>
                  <h2 style={{ marginBottom: '0.5rem' }}>正在生成报告</h2>
                  <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.9rem' }}>AI 正在综合分析你的回答，请稍候...</p>
                  {loading && <div style={{ display: 'flex', justifyContent: 'center', marginTop: '1rem' }}><span className="spinner" /></div>}
                  {error && (
                    <div style={{ marginTop: '1rem', color: 'var(--color-danger)', fontSize: '0.85rem' }}>
                      {error}
                      <button className="btn btn-primary btn-sm" style={{ marginTop: '0.5rem' }} onClick={handleGenerateReport} disabled={loading}>
                        重试
                      </button>
                    </div>
                  )}
                </>
              ) : (
                <>
                  <div style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>📊</div>
                  <h2 style={{ marginBottom: '0.25rem' }}>面试报告</h2>
                  <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.9rem' }}>整体表现评估与改进建议</p>
                  <div style={{ marginTop: '1rem' }}>
                    <div style={{ fontFamily: 'var(--font-display)', fontSize: '3rem', fontWeight: 700, color: scoreColor(report.totalScore) }}>
                      {report.totalScore}<span style={{ fontSize: '1.2rem', color: 'var(--color-text-muted)', fontWeight: 400 }}>/10</span>
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>综合评分</div>
                  </div>
                </>
              )}
            </div>

            {report && (
              <>
                <div className="card" style={{ marginBottom: '1rem' }}>
                  <div className="card-title" style={{ marginBottom: '0.6rem' }}>总评</div>
                  <p style={{ lineHeight: 1.7, whiteSpace: 'pre-wrap', color: 'var(--color-text-secondary)' }}>{report.overallFeedback}</p>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1.5rem' }}>
                  {report.strengths?.length > 0 && (
                    <div className="card">
                      <div style={{ fontSize: '0.78rem', color: 'var(--color-success)', fontWeight: 600, marginBottom: '0.5rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>优势</div>
                      <ul style={{ margin: 0, paddingLeft: '1.1rem', fontSize: '0.85rem', color: 'var(--color-text-secondary)', lineHeight: 2 }}>
                        {report.strengths.map((s, i) => <li key={i}>{s}</li>)}
                      </ul>
                    </div>
                  )}
                  {report.improvementSuggestions?.length > 0 && (
                    <div className="card">
                      <div style={{ fontSize: '0.78rem', color: 'var(--color-warning)', fontWeight: 600, marginBottom: '0.5rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>改进建议</div>
                      <ul style={{ margin: 0, paddingLeft: '1.1rem', fontSize: '0.85rem', color: 'var(--color-text-secondary)', lineHeight: 2 }}>
                        {report.improvementSuggestions.map((s, i) => <li key={i}>{s}</li>)}
                      </ul>
                    </div>
                  )}
                </div>

                {report.categoryBreakdown?.length > 0 && (
                  <div className="card" style={{ marginBottom: '1.5rem' }}>
                    <div className="card-title" style={{ marginBottom: '0.75rem' }}>分类评分</div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                      {report.categoryBreakdown.map((cat, i) => (
                        <div key={i}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.2rem' }}>
                            <span style={{ fontSize: '0.85rem', fontWeight: 500 }}>{cat.category}</span>
                            <span style={{ fontFamily: 'var(--font-display)', fontSize: '1.1rem', fontWeight: 600, color: scoreColor(cat.averageScore) }}>{cat.averageScore}/10</span>
                          </div>
                          <div style={{ height: 4, background: 'var(--color-border)', borderRadius: 2, overflow: 'hidden' }}>
                            <motion.div initial={{ width: 0 }} animate={{ width: `${(cat.averageScore / 10) * 100}%` }} transition={{ duration: 0.8, ease: 'easeOut' }}
                              style={{ height: '100%', borderRadius: 2, background: 'linear-gradient(90deg, var(--color-accent-dim), var(--color-accent))' }} />
                          </div>
                          {cat.suggestion && <div style={{ fontSize: '0.78rem', color: 'var(--color-text-muted)', marginTop: '0.2rem' }}>{cat.suggestion}</div>}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}

            <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'center' }}>
              <button className="btn btn-primary btn-lg" onClick={handleReset}>开始新的面试</button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
