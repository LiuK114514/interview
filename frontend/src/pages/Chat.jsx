import { useState, useRef, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { chat as chatApi } from '../api';

const WELCOME_MESSAGE = {
  role: 'ai',
  text: '你好！我是 AI 面试助手。你可以问我任何面试相关的问题，或者直接开始模拟对话。\n\n例如：\n- "请给我出一道 Java 多线程面试题"\n- "解释一下 Spring AOP 的原理"\n- "我该如何回答系统设计题？"',
};

export default function Chat() {
  const [messages, setMessages] = useState([WELCOME_MESSAGE]);
  const [input, setInput] = useState('');
  const [sessionId] = useState(() => crypto.randomUUID());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [streamingText, setStreamingText] = useState('');
  const bottomRef = useRef(null);
  const inputRef = useRef(null);
  const streamingRef = useRef('');

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streamingText]);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const handleSend = useCallback(function (e) {
    e?.preventDefault();
    if (!input.trim() || loading) return;
    const userMsg = input.trim();
    setInput('');
    setMessages(prev => [...prev, { role: 'user', text: userMsg }]);
    setLoading(true);
    setError(null);
    streamingRef.current = '';
    setStreamingText('');

    chatApi.sendStream(
      { message: userMsg, sessionId },
      {
        onMessage(chunk) {
          streamingRef.current += chunk;
          setStreamingText(streamingRef.current);
        },
        onDone() {
          const finalText = streamingRef.current;
          streamingRef.current = '';
          setStreamingText('');
          setMessages(prev => [...prev, { role: 'ai', text: finalText }]);
          setLoading(false);
          inputRef.current?.focus();
        },
        onError(errMsg) {
          setError(errMsg);
          setLoading(false);
          inputRef.current?.focus();
        },
      }
    );
  }, [input, loading, sessionId]);

  function handleClear() {
    setMessages([WELCOME_MESSAGE]);
    setStreamingText('');
    streamingRef.current = '';
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 8rem)' }}>
      <div className="page-header" style={{ marginBottom: '1rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <h1>AI 聊天</h1>
            <p>与 AI 自由对话，练习面试技能</p>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={handleClear}>清除对话</button>
        </div>
      </div>

      {/* Messages */}
      <div style={{ flex: 1, overflowY: 'auto', paddingRight: '0.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        <AnimatePresence initial={false}>
          {messages.map((msg, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3 }}
              style={{
                display: 'flex',
                gap: '0.75rem',
                flexDirection: msg.role === 'user' ? 'row-reverse' : 'row',
                alignItems: 'flex-start',
              }}
            >
              {/* Avatar */}
              <div style={{
                width: 32, height: 32, borderRadius: '50%', flexShrink: 0,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: '0.85rem', fontWeight: 600,
                background: msg.role === 'user' ? 'var(--color-accent-bg)' : 'var(--color-bg-card-hover)',
                color: msg.role === 'user' ? 'var(--color-accent)' : 'var(--color-text-secondary)',
                border: '1px solid var(--color-border)',
              }}>
                {msg.role === 'user' ? '你' : 'AI'}
              </div>

              {/* Bubble */}
              <div style={{
                maxWidth: '70%',
                padding: '0.85rem 1.1rem',
                borderRadius: msg.role === 'user' ? 'var(--radius-lg) var(--radius-lg) var(--radius-sm) var(--radius-lg)' : 'var(--radius-lg) var(--radius-lg) var(--radius-lg) var(--radius-sm)',
                background: msg.role === 'user' ? 'var(--color-accent-bg)' : 'var(--color-bg-card)',
                border: `1px solid ${msg.role === 'user' ? 'rgba(212,168,83,0.15)' : 'var(--color-border)'}`,
                lineHeight: 1.7,
                fontSize: '0.92rem',
                color: msg.role === 'error' ? 'var(--color-danger)' : 'var(--color-text)',
                overflow: 'hidden',
              }}>
                {msg.role === 'user' ? (
                  msg.text
                ) : (
                  <MarkdownContent text={msg.text} />
                )}
              </div>
            </motion.div>
          ))}
        </AnimatePresence>

        {loading && (
          <motion.div
            key="streaming"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            style={{ display: 'flex', gap: '0.75rem', alignItems: 'flex-start' }}
          >
            <div style={{
              width: 32, height: 32, borderRadius: '50%', flexShrink: 0,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: '0.85rem', fontWeight: 600,
              background: 'var(--color-bg-card-hover)',
              color: 'var(--color-text-secondary)',
              border: '1px solid var(--color-border)',
            }}>AI</div>
            <div style={{
              maxWidth: '70%',
              padding: '0.85rem 1.1rem',
              borderRadius: 'var(--radius-lg) var(--radius-lg) var(--radius-lg) var(--radius-sm)',
              background: 'var(--color-bg-card)',
              border: '1px solid var(--color-border)',
              lineHeight: 1.7,
              fontSize: '0.92rem',
              color: 'var(--color-text)',
              overflow: 'hidden',
            }}>
              {streamingText ? (
                <>
                  <MarkdownContent text={streamingText} />
                  <span className="cursor-blink">|</span>
                </>
              ) : (
                <div style={{ display: 'flex', gap: '0.3rem' }}>
                  <span className="skeleton" style={{ width: 4, height: 14, display: 'inline-block' }} />
                  <span className="skeleton" style={{ width: 8, height: 14, display: 'inline-block' }} />
                  <span className="skeleton" style={{ width: 4, height: 14, display: 'inline-block' }} />
                </div>
              )}
            </div>
          </motion.div>
        )}

        {error && !loading && (
          <div style={{ fontSize: '0.8rem', color: 'var(--color-danger)', textAlign: 'center', padding: '0.5rem' }}>
            {error} — <button className="btn btn-ghost btn-sm" onClick={() => setError(null)}>关闭</button>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <form onSubmit={handleSend} style={{ display: 'flex', gap: '0.75rem', marginTop: '1rem', paddingTop: '1rem', borderTop: '1px solid var(--color-border)' }}>
        <input
          ref={inputRef}
          className="input"
          value={input}
          onChange={e => setInput(e.target.value)}
          placeholder="输入消息... (Enter 发送)"
          disabled={loading}
        />
        <button type="submit" className="btn btn-primary" disabled={!input.trim() || loading}>
          {loading ? '发送中' : '发送'}
        </button>
      </form>
    </div>
  );
}

function MarkdownContent({ text }) {
  return (
    <div className="markdown-body">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={{
        code({ className, children, ...props }) {
          const match = /language-(\w+)/.exec(className || '');
          const isInline = !match && !className;
          if (isInline) {
            return <code style={{
              background: 'rgba(0,0,0,0.3)', padding: '0.15em 0.4em',
              borderRadius: '4px', fontSize: '0.85em',
              fontFamily: "'JetBrains Mono', monospace",
            }} {...props}>{children}</code>;
          }
          return (
            <div style={{ position: 'relative', margin: '0.75em 0' }}>
              {match && <div style={{
                position: 'absolute', top: 0, right: 0, padding: '0.2em 0.6em',
                fontSize: '0.7em', color: 'var(--color-text-muted)',
                background: 'rgba(255,255,255,0.04)',
                borderBottomLeftRadius: '6px', fontFamily: "'JetBrains Mono', monospace",
              }}>{match[1]}</div>}
              <pre style={{
                background: 'rgba(0,0,0,0.35)', padding: '0.85em 1em',
                borderRadius: 'var(--radius-md)', overflowX: 'auto',
                fontSize: '0.82em', lineHeight: 1.5, margin: 0,
              }}><code className={className} style={{ fontFamily: "'JetBrains Mono', monospace" }} {...props}>{children}</code></pre>
            </div>
          );
        },
        table({ children }) {
          return (
            <div style={{ overflowX: 'auto', margin: '0.75em 0' }}>
              <table style={{ borderCollapse: 'collapse', fontSize: '0.88em', width: '100%' }}>
                {children}
              </table>
            </div>
          );
        },
        th({ children }) {
          return <th style={{ border: '1px solid var(--color-border)', padding: '0.4em 0.6em', textAlign: 'left', background: 'rgba(255,255,255,0.04)' }}>{children}</th>;
        },
        td({ children }) {
          return <td style={{ border: '1px solid var(--color-border)', padding: '0.4em 0.6em' }}>{children}</td>;
        },
        a({ href, children }) {
          return <a href={href} target="_blank" rel="noreferrer" style={{ color: 'var(--color-accent)', textDecoration: 'underline' }}>{children}</a>;
        },
        blockquote({ children }) {
          return <blockquote style={{
            borderLeft: '3px solid var(--color-accent)', paddingLeft: '0.75em',
            margin: '0.5em 0', color: 'var(--color-text-secondary)',
          }}>{children}</blockquote>;
        },
        hr() {
          return <div style={{ height: 1, background: 'var(--color-border)', margin: '0.75em 0' }} />;
        },
      }}>
        {text}
      </ReactMarkdown>
    </div>
  );
}
