import { useState, useEffect } from 'react';
import { motion } from 'motion/react';
import { dlq as dlqApi } from '../api';

export default function Dlq() {
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionId, setActionId] = useState(null);

  useEffect(() => { loadMessages(); }, []);

  async function loadMessages() {
    setLoading(true);
    setError(null);
    try {
      const data = await dlqApi.list();
      setMessages(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleRetry(recordId) {
    setActionId(recordId);
    try {
      await dlqApi.retry(recordId);
      await loadMessages();
    } catch (err) {
      alert(err.message);
    } finally {
      setActionId(null);
    }
  }

  async function handleDelete(recordId) {
    setActionId(recordId);
    try {
      await dlqApi.delete(recordId);
      await loadMessages();
    } catch (err) {
      alert(err.message);
    } finally {
      setActionId(null);
    }
  }

  async function handleRetryAll() {
    if (messages.length === 0) return;
    if (!confirm(`确定重试全部 ${messages.length} 条消息？`)) return;
    try {
      await dlqApi.retryAll();
      await loadMessages();
    } catch (err) {
      alert(err.message);
    }
  }

  async function handleClearAll() {
    if (messages.length === 0) return;
    if (!confirm(`确定清空全部 ${messages.length} 条死信消息？不可撤销。`)) return;
    try {
      await dlqApi.clearAll();
      await loadMessages();
    } catch (err) {
      alert(err.message);
    }
  }

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
      <style>{`
        .dlq-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 1.5rem; }
        .dlq-title { font-size: 1.5rem; font-weight: 700; display: flex; align-items: center; gap: 0.75rem; }
        .dlq-actions { display: flex; gap: 0.75rem; }
        .dlq-actions button { display: flex; align-items: center; gap: 0.5rem; padding: 0.5rem 1rem; border: none; border-radius: var(--radius-md); font-size: 0.875rem; cursor: pointer; transition: opacity .2s; }
        .dlq-actions button:disabled { opacity: 0.5; cursor: not-allowed; }
        .dlq-empty { text-align: center; padding: 4rem 0; color: var(--color-text-secondary); }
        .dlq-empty svg { width: 4rem; height: 4rem; margin: 0 auto 1rem; opacity: 0.3; }
        .dlq-table { width: 100%; border-collapse: collapse; }
        .dlq-table th { text-align: left; padding: 0.75rem 1rem; font-size: 0.8125rem; font-weight: 600; color: var(--color-text-secondary); border-bottom: 1px solid var(--color-border); background: var(--color-bg-subtle); }
        .dlq-table td { padding: 0.75rem 1rem; font-size: 0.875rem; border-bottom: 1px solid var(--color-border); }
        .dlq-table tr:hover td { background: var(--color-bg-subtle); }
        .dlq-code { font-family: 'SF Mono', 'Cascadia Code', monospace; font-size: 0.8125rem; color: var(--color-text-secondary); }
      `}</style>

      <div className="dlq-header">
        <div className="dlq-title">
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#e53e3e" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <path d="M12 8v4" /><path d="M12 16h.01" />
          </svg>
          死信队列
        </div>
        <div className="dlq-actions">
          <button onClick={handleRetryAll} disabled={messages.length === 0}
            style={{ background: 'var(--color-warning)', color: '#fff' }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M1 4v6h6"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>
            全部重试
          </button>
          <button onClick={handleClearAll} disabled={messages.length === 0}
            style={{ background: 'var(--color-danger)', color: '#fff' }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
            清空全部
          </button>
        </div>
      </div>

      {error && (
        <div style={{ padding: '0.75rem 1rem', marginBottom: '1rem', borderRadius: 'var(--radius-md)', background: 'var(--color-danger-bg)', color: 'var(--color-danger)', fontSize: '0.875rem' }}>
          加载失败：{error}
        </div>
      )}

      <div className="card" style={{ overflow: 'hidden' }}>
        {loading ? (
          <div style={{ textAlign: 'center', padding: '3rem 0', color: 'var(--color-text-secondary)' }}>加载中...</div>
        ) : messages.length === 0 ? (
          <div className="dlq-empty">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
            <p>死信队列为空</p>
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="dlq-table">
              <thead>
                <tr>
                  <th>记录 ID</th>
                  <th>简历 ID</th>
                  <th>失败时间</th>
                  <th style={{ textAlign: 'right' }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {messages.map(msg => (
                  <tr key={msg.recordId}>
                    <td><code className="dlq-code">{msg.recordId}</code></td>
                    <td>{msg.resumeId ?? '-'}</td>
                    <td style={{ color: 'var(--color-text-secondary)' }}>{msg.errorTime ?? '-'}</td>
                    <td style={{ textAlign: 'right' }}>
                      <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                        <button onClick={() => handleRetry(msg.recordId)} disabled={actionId === msg.recordId}
                          style={{ padding: '0.25rem 0.75rem', border: 'none', borderRadius: 'var(--radius-sm)', cursor: 'pointer', fontSize: '0.8125rem', background: 'var(--color-warning-bg)', color: 'var(--color-warning)' }}>
                          {actionId === msg.recordId ? '...' : '重试'}
                        </button>
                        <button onClick={() => handleDelete(msg.recordId)} disabled={actionId === msg.recordId}
                          style={{ padding: '0.25rem 0.75rem', border: 'none', borderRadius: 'var(--radius-sm)', cursor: 'pointer', fontSize: '0.8125rem', background: 'var(--color-danger-bg)', color: 'var(--color-danger)' }}>
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </motion.div>
  );
}
