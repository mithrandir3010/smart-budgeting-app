import { motion } from 'framer-motion';
import { Wallet, Sun, Moon } from 'lucide-react';
import Sidebar from './Sidebar';
import BottomNav from './BottomNav';
import { useTheme } from '../../context/ThemeContext';

export default function AppLayout({ children, sidebarProps = {} }) {
  const { theme, toggleTheme } = useTheme();

  return (
    <div className="flex min-h-screen bg-zinc-50 dark:bg-[#050507]">
      {/* Mesh gradient overlay — dark only */}
      <div
        className="pointer-events-none fixed inset-0 dark:block hidden"
        style={{
          background:
            'radial-gradient(ellipse 60% 40% at 20% 10%, rgba(16,185,129,0.055) 0%, transparent 60%),' +
            'radial-gradient(ellipse 50% 40% at 80% 80%, rgba(99,102,241,0.055) 0%, transparent 60%)',
        }}
      />

      {/* ── Desktop Sidebar (lg+) ─────────────────────────────────────────── */}
      <div className="hidden lg:block w-[240px] flex-shrink-0" />
      <Sidebar {...sidebarProps} />

      {/* ── Mobile Top Header (< lg) ──────────────────────────────────────── */}
      <header
        className="lg:hidden fixed top-0 left-0 right-0 z-40
          bg-white/75 dark:bg-zinc-950/80 backdrop-blur-md
          border-b border-zinc-200/60 dark:border-white/[0.06]
          pt-[env(safe-area-inset-top,0px)]"
      >
        <div className="h-14 flex items-center px-4 gap-2">
          <div className="w-6 h-6 rounded-lg bg-emerald-500/15 flex items-center justify-center">
            <Wallet size={13} className="text-emerald-500 dark:text-emerald-400" strokeWidth={2} />
          </div>
          <span className="text-sm font-bold text-zinc-900 dark:text-zinc-100 tracking-tight">
            SmartBudget
          </span>
          <button
            onClick={toggleTheme}
            className="ml-auto w-8 h-8 flex items-center justify-center rounded-lg
              text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100
              hover:bg-zinc-100 dark:hover:bg-white/[0.07] transition-colors"
            aria-label="Tema değiştir"
          >
            {theme === 'dark' ? <Sun size={16} strokeWidth={1.8} /> : <Moon size={16} strokeWidth={1.8} />}
          </button>
        </div>
      </header>

      {/* ── Mobile Bottom Navigation (< lg) ───────────────────────────────── */}
      <BottomNav />

      {/* ── Main content ──────────────────────────────────────────────────── */}
      <motion.main
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: 'easeOut', delay: 0.1 }}
        className="relative flex-1 min-w-0 px-4 sm:px-6 xl:px-8 py-6 pt-header-safe lg:pt-6 pb-nav-safe lg:pb-6"
      >
        {children}
      </motion.main>
    </div>
  );
}
