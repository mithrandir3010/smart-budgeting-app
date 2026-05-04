import { motion } from 'framer-motion';
import { TrendingUp, TrendingDown, Zap, Target, BarChart2 } from 'lucide-react';
import { cn, fmt } from '../../utils/helpers';

const cardVariants = {
  hidden: { opacity: 0, y: 20, scale: 0.97 },
  visible: (i) => ({
    opacity: 1, y: 0, scale: 1,
    transition: { delay: i * 0.07, duration: 0.45, ease: [0.23, 1, 0.32, 1] },
  }),
};

function StatCard({ index, icon: Icon, label, value, sub, accent = 'green', size = 'sm', extra }) {
  const accents = {
    green:  { text: 'text-emerald-400', glow: 'shadow-card-glow-green', dot: 'bg-emerald-400', border: 'border-emerald-500/20' },
    amber:  { text: 'text-amber-400',   glow: 'shadow-card-glow-amber', dot: 'bg-amber-400',   border: 'border-amber-500/20' },
    rose:   { text: 'text-rose-400',    glow: 'shadow-card-glass',      dot: 'bg-rose-400',    border: 'border-rose-500/20' },
    indigo: { text: 'text-indigo-400',  glow: 'shadow-card-glass',      dot: 'bg-indigo-400',  border: 'border-indigo-500/20' },
  };
  const a = accents[accent];

  return (
    <motion.div
      custom={index}
      variants={cardVariants}
      initial="hidden"
      animate="visible"
      className={cn(
        'relative overflow-hidden',
        'glass-card p-5',
        a.glow,
      )}
    >
      {/* Subtle background glow */}
      <div className={cn(
        'absolute inset-0 opacity-0 dark:opacity-100 pointer-events-none',
        accent === 'green' && 'bg-gradient-radial from-emerald-500/5 via-transparent to-transparent',
        accent === 'amber' && 'bg-gradient-radial from-amber-500/5 via-transparent to-transparent',
      )} />

      <div className="relative">
        <div className="flex items-start justify-between mb-3">
          <div className={cn('p-2 rounded-xl', `dark:bg-white/[0.06] bg-zinc-100`)}>
            <Icon size={16} strokeWidth={1.8} className={cn(a.text, 'opacity-90')} />
          </div>
          <span className={cn('w-2 h-2 rounded-full dot-pulse', a.dot)} />
        </div>

        <p className="text-xs font-semibold text-zinc-400 dark:text-zinc-500 uppercase tracking-widest mb-1">
          {label}
        </p>
        <p className={cn(
          'font-bold tracking-tight leading-none',
          size === 'lg' ? 'text-3xl' : 'text-xl',
          a.text,
        )}>
          {value}
        </p>

        {sub && (
          <p className="text-xs text-zinc-500 dark:text-zinc-500 mt-2 leading-snug">{sub}</p>
        )}

        {extra && <div className="mt-3">{extra}</div>}
      </div>
    </motion.div>
  );
}

function BudgetBar({ current, projected, budget }) {
  const budgetNum    = Number(budget)    || 0;
  const currentNum   = Number(current)   || 0;
  const projectedNum = Number(projected) || 0;
  if (!budgetNum) return null;

  const max      = budgetNum * 1.4;
  const curPct   = Math.min((currentNum   / max) * 100, 100);
  const projPct  = Math.min((projectedNum / max) * 100, 100);
  const limitPct = Math.min((budgetNum    / max) * 100, 100);
  const over     = projectedNum > budgetNum;

  return (
    <div>
      <div className="relative h-1.5 rounded-full bg-zinc-200 dark:bg-white/[0.08] overflow-hidden">
        <div
          className="absolute left-0 top-0 h-full rounded-full transition-all duration-700"
          style={{ width: `${curPct}%`, background: over ? '#f43f5e' : '#10b981' }}
        />
        {projPct > curPct && (
          <div
            className="absolute top-0 h-full rounded-r-full opacity-50"
            style={{
              left: `${curPct}%`,
              width: `${Math.min(projPct - curPct, 100 - curPct)}%`,
              background: over ? '#f43f5e' : '#10b981',
            }}
          />
        )}
        <div
          className="absolute top-0 h-full w-px bg-white/30"
          style={{ left: `${limitPct}%` }}
        />
      </div>
      <div className="flex justify-between mt-1.5 text-[10px] text-zinc-500">
        <span>Şu an {fmt(currentNum)}</span>
        <span>Limit {fmt(budgetNum)}</span>
      </div>
    </div>
  );
}

export default function BentoCards({ summary, transactions }) {
  if (!summary) return null;

  const {
    totalSpending, categoryBreakdown, monthlyBudget,
    projectedSpending, dailyRate,
  } = summary;

  const catCount  = Object.keys(categoryBreakdown || {}).length;
  const txCount   = transactions?.length ?? 0;
  const hasBudget = monthlyBudget != null;
  const over      = hasBudget && Number(projectedSpending) > Number(monthlyBudget);

  const topCat = Object.entries(categoryBreakdown || {})
    .sort(([, a], [, b]) => Number(b) - Number(a))[0];

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-3 md:gap-4">

      {/* Card 1 — Total (large) */}
      <div className="col-span-2">
        <StatCard
          index={0}
          icon={BarChart2}
          label="Toplam Harcama"
          value={fmt(totalSpending)}
          sub={`${txCount} işlem · ${catCount} kategori`}
          accent="green"
          size="lg"
        />
      </div>

      {/* Card 2 — Daily rate */}
      <StatCard
        index={1}
        icon={Zap}
        label="Günlük Hız"
        value={fmt(Number(dailyRate) || 0)}
        sub="Ortalama günlük harcama"
        accent="amber"
      />

      {/* Card 3 — Projection */}
      <StatCard
        index={2}
        icon={over ? TrendingUp : TrendingDown}
        label="Ay Sonu Tahmini"
        value={fmt(Number(projectedSpending) || 0)}
        sub={over ? 'Bütçe aşımı riski' : 'Hedef dahilinde'}
        accent={over ? 'rose' : 'green'}
      />

      {/* Card 4 — Top category */}
      {topCat && (
        <StatCard
          index={3}
          icon={Target}
          label="En Büyük Kategori"
          value={topCat[0]}
          sub={fmt(topCat[1])}
          accent="indigo"
        />
      )}

      {/* Card 5 — Budget bar */}
      {hasBudget && (
        <div className={topCat ? 'col-span-2 md:col-span-3' : 'col-span-2 md:col-span-4'}>
          <StatCard
            index={4}
            icon={Target}
            label="Bütçe Kullanımı"
            value={`%${Number(monthlyBudget) ? Math.round((Number(totalSpending) / Number(monthlyBudget)) * 100) : 0}`}
            sub={`${fmt(totalSpending)} / ${fmt(monthlyBudget)}`}
            accent={over ? 'rose' : 'green'}
            extra={
              <BudgetBar
                current={totalSpending}
                projected={projectedSpending}
                budget={monthlyBudget}
              />
            }
          />
        </div>
      )}

    </div>
  );
}
