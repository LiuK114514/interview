import { useState, useCallback } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import Sidebar from './components/Sidebar';
import Dashboard from './pages/Dashboard';
import Interview from './pages/Interview';
import Chat from './pages/Chat';
import Resumes from './pages/Resumes';
import Dlq from './pages/Dlq';

const PAGES = {
  dashboard: { component: Dashboard, path: '/' },
  interview: { component: Interview, path: '/interview' },
  chat: { component: Chat, path: '/chat' },
  resumes: { component: Resumes, path: '/resumes' },
  dlq: { component: Dlq, path: '/dlq' },
};

function getPageKey(path) {
  const clean = path.split('?')[0];
  // Exact match for root, startsWith for sub-pages
  if (clean === '/') return 'dashboard';
  const entry = Object.entries(PAGES).find(([, v]) => v.path !== '/' && clean.startsWith(v.path));
  return entry ? entry[0] : 'dashboard';
}

export default function App() {
  const [currentPath, setCurrentPath] = useState(() => {
    const p = window.location.pathname;
    const sp = window.location.search;
    return p + sp;
  });
  const pageKey = getPageKey(currentPath);
  const PageComponent = PAGES[pageKey].component;

  const handleNavigate = useCallback((path) => {
    // Strip search params on sidebar navigation (clean page switch)
    const clean = path.split('?')[0];
    setCurrentPath(clean);
    window.history.pushState(null, '', clean);
  }, []);

  // Handle browser back/forward
  useState(() => {
    const onPop = () => {
      const full = window.location.pathname + window.location.search;
      setCurrentPath(full);
    };
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  });

  return (
    <div className="app-layout">
      <Sidebar currentPath={currentPath} onNavigate={handleNavigate} />
      <main className="main-content">
        <AnimatePresence mode="wait">
          <motion.div
            key={pageKey + currentPath}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -12 }}
            transition={{ duration: 0.25, ease: [0.4, 0, 0.2, 1] }}
          >
            <PageComponent onNavigate={handleNavigate} />
          </motion.div>
        </AnimatePresence>
      </main>
    </div>
  );
}
