import { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Menu, Wallet } from 'lucide-react';
import Sidebar, { SidebarInner } from './Sidebar';

export default function AppLayout({ children, sidebarProps = {} }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const close = () => setMobileOpen(false);

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

      {/* ── Mobile Header (< lg) ──────────────────────────────────────────── */}
      <header className="lg:hidden fixed top-0 left-0 right-0 z-40 h-14 flex items-center px-4 gap-3
        bg-white/75 dark:bg-zinc-950/80 backdrop-blur-md
        border-b border-zinc-200/60 dark:border-white/[0.06]">
        <button
          onClick={() => setMobileOpen(true)}
          aria-label="Menüyü aç"
          className="p-2 -ml-1 rounded-xl text-zinc-500 dark:text-zinc-400
            hover:bg-zinc-100 dark:hover:bg-white/[0.07] transition-colors"
        >
          <Menu size={20} strokeWidth={2} />
        </button>
        <div className="flex items-center gap-2">
          <div className="w-6 h-6 rounded-lg bg-emerald-500/15 flex items-center justify-center">
            <Wallet size={13} className="text-emerald-500 dark:text-emerald-400" strokeWidth={2} />
          </div>
          <span className="text-sm font-bold text-zinc-900 dark:text-zinc-100 tracking-tight">
            SmartBudget
          </span>
        </div>
      </header>

      {/* ── Mobile Drawer ─────────────────────────────────────────────────── */}
      <AnimatePresence>
        {mobileOpen && (
          <>
            {/* Backdrop */}
            <motion.div
              key="backdrop"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.22 }}
              className="lg:hidden fixed inset-0 z-40 bg-black/55 backdrop-blur-sm"
              onClick={close}
            />

            {/* Drawer panel */}
            <motion.div
              key="drawer"
              initial={{ x: '-100%' }}
              animate={{ x: 0 }}
              exit={{ x: '-100%' }}
              transition={{ type: 'spring', damping: 28, stiffness: 280 }}
              className="lg:hidden fixed left-0 top-0 h-full z-50 w-72 flex flex-col select-none glass-sidebar"
            >
              <SidebarInner {...sidebarProps} onClose={close} />
            </motion.div>
          </>
        )}
      </AnimatePresence>

      {/* ── Main content ──────────────────────────────────────────────────── */}
      {/* pt-20: mobile header (56px) + normal top padding (24px) */}
      <motion.main
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: 'easeOut', delay: 0.1 }}
        className="relative flex-1 min-w-0 px-4 sm:px-6 xl:px-8 py-6 pt-20 lg:pt-6"
      >
        {children}
      </motion.main>
    </div>
  );
}
