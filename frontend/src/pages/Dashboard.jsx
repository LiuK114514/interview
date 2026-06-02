import { useState, useEffect } from 'react';
import { motion } from 'motion/react';
import { interview, resumes as resumesApi } from '../api';

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.08 } } };
const itemAnim = { hidden: { opacity: 0, y: 20 }, show: { opacity: 1, y: 0 } };

export default function Dashboard({ onNavigate }) {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadStats();
  }, []);

  async function loadStats() {
    setLoading(true);
    setError(null);
    try {
      const [resumeList] = await Promise.all([
        resumesApi.list().catch(() => []),
      ]);
      setStats({
        resumes: Array.isArray(resumeList) ? resumeList.length : 0,
      });
    } catch {
      setError('加载数据失败');
    } finally {
      setLoading(false);
    }
  }

  if (error) {
    return (
      <div className="page-header">
        <div className="empty-state">
          <div className="empty-state-icon">⚠</div>
          <h3>加载失败</h3>
          <p>{error}</p>
          <button className="btn btn-primary" onClick={loadStats}>重新加载</button>
        </div>
      </div>
    );
  }

  return (
    <motion.div variants={container} initial="hidden" animate="show">
      <motion.div variants={itemAnim} className="page-header">
        <h1>仪表盘</h1>
        <p>AI 智能面试平台 — 管理面试、简历和对话</p>
      </motion.div>

      {/* Stats */}
      <motion.div variants={itemAnim} className="stats-grid">
        <div className="stat-card">
          <div className="stat-card-label">◆ 面试会话</div>
          <div className="stat-card-value">{loading ? '—' : (stats?.interviews ?? 0)}</div>
          <div className="stat-card-sub">已完成的面试</div>
        </div>
        <div className="stat-card">
          <div className="stat-card-label">📄 简历</div>
          <div className="stat-card-value">{loading ? '—' : stats?.resumes ?? 0}</div>
          <div className="stat-card-sub">已上传的简历</div>
        </div>
        <div className="stat-card">
          <div className="stat-card-label">💬 AI 对话</div>
          <div className="stat-card-value">—</div>
          <div className="stat-card-sub">多轮对话次数</div>
        </div>
        <div className="stat-card">
          <div className="stat-card-label">📊 报告</div>
          <div className="stat-card-value">—</div>
          <div className="stat-card-sub">已生成的面试报告</div>
        </div>
      </motion.div>

      {/* Quick Actions */}
      <motion.div variants={itemAnim} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1rem' }}>
        <QuickActionCard
          icon="🎯"
          title="开始面试"
          desc="启动一场新的 AI 模拟面试，实时出题与评估"
          onClick={() => onNavigate('/interview')}
        />
        <QuickActionCard
          icon="📄"
          title="上传简历"
          desc="上传简历进行 AI 智能评分与分析"
          onClick={() => onNavigate('/resumes')}
        />
        <QuickActionCard
          icon="💬"
          title="AI 对话"
          desc="与 AI 进行自由对话，练习面试问题"
          onClick={() => onNavigate('/chat')}
        />
      </motion.div>
    </motion.div>
  );
}

function QuickActionCard({ icon, title, desc, onClick }) {
  return (
    <motion.div
      className="card"
      style={{ cursor: 'pointer', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}
      whileHover={{ y: -3, borderColor: 'var(--color-border-hover)' }}
      whileTap={{ scale: 0.98 }}
      onClick={onClick}
    >
      <div style={{ fontSize: '1.8rem', lineHeight: 1 }}>{icon}</div>
      <h3 style={{ fontFamily: 'var(--font-display)', fontSize: '1.05rem', margin: 0 }}>{title}</h3>
      <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.85rem', margin: 0 }}>{desc}</p>
    </motion.div>
  );
}
