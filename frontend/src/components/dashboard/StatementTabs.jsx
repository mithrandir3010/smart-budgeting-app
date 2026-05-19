import { motion } from 'framer-motion';
import { cn, fmt } from '../../utils/helpers';

const MONTH_LABELS = ['Oca', 'Şub', 'Mar', 'Nis', 'May', 'Haz', 'Tem', 'Ağu', 'Eyl', 'Eki', 'Kas', 'Ara'];

function tabLabel(s) {
  if (s.statementCutDate) {
    const d = new Date(s.statementCutDate);
    return `${d.getDate()} ${MONTH_LABELS[d.getMonth()]} ${d.getFullYear()}`;
  }
  return s.fileName.replace(/\.pdf$/i, '');
}

function trendArrow(statements, index) {
  if (index === 0) return null;
  const cur  = Number(statements[index].totalAmount)     || 0;
  const prev = Number(statements[index - 1].totalAmount) || 0;
  if (prev === 0) return null;
  const pct = Math.round(((cur - prev) / prev) * 100);
  const up  = pct >= 0;
  return (
    <span className={cn(
      'text-[10px] font-semibold ml-1',
      up ? 'text-rose-400' : 'text-emerald-400',
    )}>
      {up ? '↑' : '↓'}{Math.abs(pct)}%
    </span>
  );
}

export default function StatementTabs({ statements, selectedId, onSelect }) {
  if (!statements || statements.length < 2) return null;

  return (
    <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-hide">
      {/* Genel Analiz tab */}
      <button
        onClick={() => onSelect(null)}
        className={cn(
          'flex-shrink-0 flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-semibold transition-all',
          selectedId === null
            ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/25'
            : 'text-zinc-500 dark:text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-white/[0.05] border border-transparent',
        )}
      >
        <span className="text-sm">📊</span>
        Genel Analiz
      </button>

      {/* Ekstre tab'ları */}
      {statements.map((s, i) => {
        const active = selectedId === s.id;
        return (
          <motion.button
            key={s.id}
            onClick={() => onSelect(s.id)}
            whileTap={{ scale: 0.97 }}
            className={cn(
              'flex-shrink-0 flex flex-col items-start px-3.5 py-2 rounded-xl text-xs transition-all border',
              active
                ? 'bg-indigo-500/15 border-indigo-500/25 text-indigo-400'
                : 'text-zinc-500 dark:text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-white/[0.05] border-transparent',
            )}
          >
            <div className="flex items-center gap-1">
              <span className="font-bold">{tabLabel(s)}</span>
              {trendArrow(statements, i)}
            </div>
            <div className="flex items-center gap-1.5 mt-0.5">
              {s.bankName && (
                <span className="text-[10px] opacity-60 uppercase tracking-wide">{s.bankName}</span>
              )}
              <span className={cn(
                'text-[10px] font-semibold tabular-nums',
                active ? 'text-indigo-400/80' : 'text-zinc-400',
              )}>
                {fmt(s.totalAmount || 0)}
              </span>
            </div>
          </motion.button>
        );
      })}
    </div>
  );
}
