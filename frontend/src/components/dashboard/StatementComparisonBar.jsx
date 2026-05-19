import { motion } from 'framer-motion';
import { fmt } from '../../utils/helpers';

const MONTH_LABELS = ['Oca', 'Şub', 'Mar', 'Nis', 'May', 'Haz', 'Tem', 'Ağu', 'Eyl', 'Eki', 'Kas', 'Ara'];

function tabLabel(s) {
  const raw = s.statementCutDate || s.periodEnd;
  if (raw) {
    const d = new Date(raw);
    return `${d.getDate()} ${MONTH_LABELS[d.getMonth()]} ${d.getFullYear()}`;
  }
  return s.fileName.replace(/\.pdf$/i, '');
}

export default function StatementComparisonBar({ statements }) {
  if (!statements || statements.length < 2) return null;

  const amounts = statements.map((s) => Number(s.totalAmount) || 0);
  const maxAmt  = Math.max(...amounts, 1);
  const maxIdx  = amounts.indexOf(maxAmt);

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: 0.15, ease: [0.23, 1, 0.32, 1] }}
      className="glass-card p-5"
    >
      <p className="text-[10px] font-semibold text-zinc-500 uppercase tracking-widest mb-4">
        Ekstre Karşılaştırması
      </p>

      <div className="flex flex-col gap-3">
        {statements.map((s, i) => {
          const amt    = amounts[i];
          const pct    = Math.round((amt / maxAmt) * 100);
          const isMax  = i === maxIdx;
          const prev   = i > 0 ? amounts[i - 1] : null;
          const diff   = prev !== null && prev > 0 ? Math.round(((amt - prev) / prev) * 100) : null;

          return (
            <div key={s.id} className="flex items-center gap-3">
              <div className="w-20 flex-shrink-0 text-right">
                <p className="text-xs font-semibold text-zinc-700 dark:text-zinc-300">{tabLabel(s)}</p>
                {s.bankName && (
                  <p className="text-[10px] text-zinc-400 uppercase tracking-wide">{s.bankName}</p>
                )}
              </div>

              <div className="flex-1 relative h-7 flex items-center">
                <div className="absolute inset-y-0 left-0 right-0 bg-zinc-100 dark:bg-white/[0.04] rounded-full" />
                <motion.div
                  initial={{ width: 0 }}
                  animate={{ width: `${pct}%` }}
                  transition={{ duration: 0.8, delay: i * 0.08, ease: [0.23, 1, 0.32, 1] }}
                  className="relative h-full rounded-full"
                  style={{
                    background: isMax
                      ? 'linear-gradient(90deg, #f43f5e, #fb7185)'
                      : 'linear-gradient(90deg, #10b981, #34d399)',
                    minWidth: '2px',
                  }}
                />
              </div>

              <div className="w-28 flex-shrink-0 flex items-center gap-1.5">
                <p className="text-xs font-bold tabular-nums text-zinc-700 dark:text-zinc-200">
                  {fmt(amt)}
                </p>
                {diff !== null && (
                  <span className={`text-[10px] font-semibold ${diff >= 0 ? 'text-rose-400' : 'text-emerald-400'}`}>
                    {diff >= 0 ? '↑' : '↓'}{Math.abs(diff)}%
                  </span>
                )}
                {isMax && (
                  <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-rose-500/10 text-rose-400 font-semibold">
                    en yüksek
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </motion.div>
  );
}
