import { useMemo } from 'react';
import { motion } from 'framer-motion';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, ReferenceLine,
} from 'recharts';
import { fmt } from '../../utils/helpers';
import { useTheme } from '../../context/ThemeContext';

const MONTH_LABELS = ['Oca', 'Şub', 'Mar', 'Nis', 'May', 'Haz', 'Tem', 'Ağu', 'Eyl', 'Eki', 'Kas', 'Ara'];

function buildMonthlyData(transactions) {
  const map = {};
  for (const tx of transactions) {
    const d = new Date(tx.date);
    if (isNaN(d)) continue;
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    if (!map[key]) map[key] = { key, year: d.getFullYear(), month: d.getMonth(), total: 0 };
    map[key].total += Math.abs(Number(tx.amount) || 0);
  }
  return Object.values(map)
    .sort((a, b) => a.key.localeCompare(b.key))
    .slice(-12)
    .map((d) => ({ name: `${MONTH_LABELS[d.month]} ${d.year}`, total: Math.round(d.total) }));
}

function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-xl px-4 py-3 shadow-2xl text-sm bg-white dark:bg-[rgba(10,10,18,0.92)] border border-zinc-200 dark:border-white/[0.08] backdrop-blur-sm">
      <p className="text-[10px] font-semibold text-zinc-500 uppercase tracking-widest mb-1">{label}</p>
      <p className="font-bold text-emerald-600 dark:text-emerald-400 text-base tabular-nums">{fmt(payload[0].value)}</p>
    </div>
  );
}

function CustomDot({ cx, cy, value, bgColor }) {
  if (!value) return null;
  return (
    <g>
      <circle cx={cx} cy={cy} r={4} fill="#10b981" stroke={bgColor} strokeWidth={2} />
      <circle cx={cx} cy={cy} r={8} fill="#10b981" fillOpacity={0.15} />
    </g>
  );
}

export default function SpendingTrendChart({ transactions }) {
  const { theme } = useTheme();
  const isDark = theme === 'dark';

  const data = useMemo(() => buildMonthlyData(transactions || []), [transactions]);
  if (data.length < 2) return null;

  const avgVal  = Math.round(data.reduce((s, d) => s + d.total, 0) / data.length);

  const gridStroke   = isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.06)';
  const axisTickFill = isDark ? '#52525b' : '#71717a';
  const dotBgColor   = isDark ? '#050507' : '#f9fafb';

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, delay: 0.45, ease: [0.23, 1, 0.32, 1] }}
      className="relative overflow-hidden glass-card p-5"
    >
      {/* Ambient glow top-right — dark only */}
      <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full blur-3xl pointer-events-none bg-indigo-500/10 dark:bg-indigo-500/10 hidden dark:block" />

      <div className="relative mb-5 flex items-end justify-between">
        <div>
          <p className="text-[10px] font-semibold text-zinc-500 uppercase tracking-widest">
            Aylık Harcama Trendi
          </p>
          <p className="text-xs text-zinc-500 dark:text-zinc-600 mt-0.5">Son {data.length} ay</p>
        </div>
        <div className="text-right">
          <p className="text-[10px] text-zinc-500 dark:text-zinc-600 uppercase tracking-wider">Ortalama</p>
          <p className="text-sm font-bold text-indigo-500 dark:text-indigo-400 tabular-nums">{fmt(avgVal)}</p>
        </div>
      </div>

      <ResponsiveContainer width="100%" height={220}>
        <AreaChart data={data} margin={{ top: 8, right: 4, bottom: 0, left: 0 }}>
          <defs>
            <linearGradient id="trendGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%"   stopColor="#10b981" stopOpacity={isDark ? 0.4 : 0.25} />
              <stop offset="50%"  stopColor="#6366f1" stopOpacity={isDark ? 0.15 : 0.08} />
              <stop offset="100%" stopColor="#6366f1" stopOpacity={0}   />
            </linearGradient>
            <linearGradient id="strokeGradient" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%"   stopColor="#10b981" />
              <stop offset="100%" stopColor="#6366f1" />
            </linearGradient>
          </defs>

          <CartesianGrid strokeDasharray="3 3" stroke={gridStroke} vertical={false} />
          <XAxis
            dataKey="name"
            tick={{ fontSize: 10, fill: axisTickFill, fontWeight: 500 }}
            axisLine={false}
            tickLine={false}
            interval="preserveStartEnd"
          />
          <YAxis
            tick={{ fontSize: 10, fill: axisTickFill, fontWeight: 500 }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v) => v >= 1000 ? `${(v / 1000).toFixed(0)}k` : String(v)}
            width={44}
          />
          <Tooltip
            content={<CustomTooltip />}
            cursor={{ stroke: 'rgba(16,185,129,0.2)', strokeWidth: 1, strokeDasharray: '4 4' }}
          />
          <ReferenceLine
            y={avgVal}
            stroke={isDark ? 'rgba(99,102,241,0.3)' : 'rgba(99,102,241,0.4)'}
            strokeDasharray="4 4"
            strokeWidth={1}
          />
          <Area
            type="monotone"
            dataKey="total"
            stroke="url(#strokeGradient)"
            strokeWidth={2.5}
            fill="url(#trendGradient)"
            dot={false}
            activeDot={<CustomDot bgColor={dotBgColor} />}
            isAnimationActive
            animationDuration={1400}
            animationEasing="ease-out"
          />
        </AreaChart>
      </ResponsiveContainer>
    </motion.div>
  );
}
