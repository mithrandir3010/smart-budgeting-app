import { motion } from 'framer-motion';
import Sidebar from './Sidebar';

export default function AppLayout({ children, sidebarProps = {} }) {
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

      {/* Sidebar — hidden on small screens */}
      <div className="hidden lg:block w-[240px] flex-shrink-0" />
      <Sidebar {...sidebarProps} />

      {/* Main content */}
      <motion.main
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: 'easeOut', delay: 0.1 }}
        className="relative flex-1 min-w-0 px-4 sm:px-6 xl:px-8 py-6"
      >
        {children}
      </motion.main>
    </div>
  );
}
