// Shared UI primitives — single source of truth for common patterns

export const inputCls =
  'w-full rounded-xl bg-zinc-50 dark:bg-white/[0.04] border border-zinc-200 dark:border-white/[0.08] ' +
  'text-zinc-900 dark:text-zinc-100 ' +
  'placeholder-zinc-400 dark:placeholder-zinc-600 px-4 py-2.5 text-sm ' +
  'focus:outline-none focus:border-indigo-500/50 ' +
  'focus:ring-2 focus:ring-indigo-500/30 focus:shadow-[0_0_15px_rgba(99,102,241,0.18)] ' +
  'transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed';

export const labelCls =
  'block text-[11px] font-semibold text-zinc-500 uppercase tracking-widest mb-1.5';

export const fadeUp = (delay = 0) => ({
  initial:    { opacity: 0, y: 18 },
  animate:    { opacity: 1, y: 0 },
  transition: { duration: 0.5, delay, ease: [0.23, 1, 0.32, 1] },
});

// Uses the glass-card CSS class which has both light and dark definitions in index.css
export function GlassCard({ children, className = '' }) {
  return (
    <div className={`glass-card p-5 ${className}`}>
      {children}
    </div>
  );
}
