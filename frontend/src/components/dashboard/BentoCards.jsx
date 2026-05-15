import { motion } from 'framer-motion';
import { Zap, Target, BarChart2, TrendingUp } from 'lucide-react';
import { cn, fmt } from '../../utils/helpers';

const containerVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.08 } },
};

const cardVariants = {
  hidden:  { opacity: 0, y: 24, scale: 0.96 },
  visible: {
    opacity: 1, y: 0, scale: 1,
    transition: { duration: 0.5, ease: [0.23, 1, 0.32, 1] },
  },
};

const ACCENTS = {
  green:  {
    text:   'text-emerald-400',
    dim:    'text-emerald-500/70',
    dot:    'bg-emerald-400',
    ring:   'shadow-[0_0_28px_rgba(16,185,129,0.12)]',
    hover:  'hover:shadow-[0_0_40px_rgba(16,185,129,0.2)]',
    glow:   'from-emerald-500/[0.07] via-transparent',
    border: 'border-emerald-500/[0.12]',
    badge:  'bg-emerald-500/10 text-emerald-400',
  },
  amber:  {
    text:   'text-amber-400',
    dim:    'text-amber-500/70',
    dot:    'bg-amber-400',
    ring:   'shadow-[0_0_28px_rgba(245,158,11,0.12)]',
    hover:  'hover:shadow-[0_0_40px_rgba(245,158,11,0.2)]',
    glow:   'from-amber-500/[0.07] via-transparent',
    border: 'border-amber-500/[0.12]',
    badge:  'bg-amber-500/10 text-amber-400',
  },
  rose:   {
    text:   'text-rose-400',
    dim:    'text-rose-500/70',
    dot:    'bg-rose-400',
    ring:   'shadow-[0_0_28px_rgba(244,63,94,0.12)]',
    hover:  'hover:shadow-[0_0_40px_rgba(244,63,94,0.2)]',
    glow:   'from-rose-500/[0.07] via-transparent',
    border: 'border-rose-500/[0.12]',
    badge:  'bg-rose-500/10 text-rose-400',
  },
  indigo: {
    text:   'text-indigo-400',
    dim:    'text-indigo-500/70',
    dot:    'bg-indigo-400',
    ring:   'shadow-[0_0_28px_rgba(99,102,241,0.12)]',
    hover:  'hover:shadow-[0_0_40px_rgba(99,102,241,0.2)]',
    glow:   'from-indigo-500/[0.07] via-transparent',
    border: 'border-indigo-500/[0.12]',
    badge:  'bg-indigo-500/10 text-indigo-400',
  },
};

function StatCard({ icon: Icon, label, value, sub, accent = 'green', size = 'sm', extra }) {
  const a = ACCENTS[accent];

  return (
    <motion.div
      variants={cardVariants}
      whileHover={{ scale: 1.02, y: -3, transition: { duration: 0.2, ease: 'easeOut' } }}
      whileTap={{ scale: 0.98 }}
      className={cn(
        'relative overflow-hidden rounded-2xl p-5 cursor-default',
        'bg-white/[0.025] backdrop-blur-md',
        'border border-white/[0.07]',
        a.ring,
        a.hover,
        'transition-shadow duration-300',
      )}
    >
      {/* Corner glow */}
      <div className={cn(
        'absolute -top-10 -left-10 w-32 h-32 rounded-full blur-2xl pointer-events-none',
        `bg-gradient-radial ${a.glow} to-transparent opacity-60`,
      )} />

      <div className="relative">
        <div className="flex items-start justify-between mb-4">
          <div className={cn('p-2 rounded-xl bg-white/[0.06]', a.border, 'border')}>
            <Icon size={15} strokeWidth={1.8} className={a.text} />
          </div>
          <span className={cn('inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold', a.badge)}>
            <span className={cn('w-1.5 h-1.5 rounded-full dot-pulse', a.dot)} />
            live
          </span>
        </div>

        <p className="text-[10px] font-semibold text-zinc-500 uppercase tracking-widest mb-1.5">
          {label}
        </p>
        <p className={cn(
          'font-bold tracking-tight leading-none',
          size === 'lg' ? 'text-[2rem]' : 'text-xl',
          a.text,
        )}>
          {value}
        </p>

        {sub && (
          <p className="text-[11px] text-zinc-600 mt-2 leading-snug">{sub}</p>
        )}

        {extra && <div className="mt-3">{extra}</div>}
      </div>
    </motion.div>
  );
}

function BudgetBar({ current, budget }) {
  const budgetNum  = Number(budget)  || 0;
  const currentNum = Number(current) || 0;
  if (!budgetNum) return null;

  const max      = budgetNum * 1.4;
  const curPct   = Math.min((currentNum / max) * 100, 100);
  const limitPct = Math.min((budgetNum  / max) * 100, 100);
  const over     = currentNum > budgetNum;

  return (
    <div>
      <div className="relative h-1.5 rounded-full bg-white/[0.06] overflow-hidden">
        <motion.div
          className="absolute left-0 top-0 h-full rounded-full"
          initial={{ width: 0 }}
          animate={{ width: `${curPct}%` }}
          transition={{ duration: 1, delay: 0.4, ease: [0.23, 1, 0.32, 1] }}
          style={{ background: over ? '#f43f5e' : 'linear-gradient(90deg, #10b981, #34d399)' }}
        />
        <div
          className="absolute top-0 h-full w-px bg-white/20"
          style={{ left: `${limitPct}%` }}
        />
      </div>
      <div className="flex justify-between mt-1.5 text-[10px] text-zinc-600">
        <span>Şu an {fmt(currentNum)}</span>
        <span>Limit {fmt(budgetNum)}</span>
      </div>
    </div>
  );
}

export default function BentoCards({ summary, transactions }) {
  if (!summary) return null;

  const { totalSpending, categoryBreakdown, monthlyBudget, dailyRate } = summary;

  const catCount  = Object.keys(categoryBreakdown || {}).length;
  const txCount   = transactions?.length ?? 0;
  const hasBudget = monthlyBudget != null;
  const over      = hasBudget && Number(totalSpending) > Number(monthlyBudget);

  const topCat = Object.entries(categoryBreakdown || {})
    .sort(([, a], [, b]) => Number(b) - Number(a))[0];

  return (
    <motion.div
      variants={containerVariants}
      initial="hidden"
      animate="visible"
      className="grid grid-cols-2 md:grid-cols-4 gap-3 md:gap-4"
    >
      <div className="col-span-2">
        <StatCard
          icon={BarChart2}
          label="Toplam Harcama"
          value={fmt(totalSpending)}
          sub={`${txCount} işlem · ${catCount} kategori`}
          accent="green"
          size="lg"
        />
      </div>

      <StatCard
        icon={Zap}
        label="Günlük Hız"
        value={fmt(Number(dailyRate) || 0)}
        sub="Ortalama günlük"
        accent="amber"
      />

      {topCat && (
        <StatCard
          icon={Target}
          label="En Büyük Kategori"
          value={topCat[0]}
          sub={fmt(topCat[1])}
          accent="indigo"
        />
      )}

      {hasBudget && (
        <div className={topCat ? 'col-span-2 md:col-span-3' : 'col-span-2 md:col-span-4'}>
          <StatCard
            icon={TrendingUp}
            label="Bütçe Kullanımı"
            value={`%${Number(monthlyBudget) ? Math.round((Number(totalSpending) / Number(monthlyBudget)) * 100) : 0}`}
            sub={`${fmt(totalSpending)} / ${fmt(monthlyBudget)}`}
            accent={over ? 'rose' : 'green'}
            extra={<BudgetBar current={totalSpending} budget={monthlyBudget} />}
          />
        </div>
      )}
    </motion.div>
  );
}
