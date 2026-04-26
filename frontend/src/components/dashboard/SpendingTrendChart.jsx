import { useMemo } from 'react';
import { motion } from 'framer-motion';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer,
} from 'recharts';
import { fmt } from '../../lib/utils';

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
    .map((d) => ({
      name:  `${MONTH_LABELS[d.month]} ${d.year}`,
      total: Math.round(d.total),
    }));
}

function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-[#111118] border border-white/[0.08] rounded-xl px-4 py-3 shadow-2xl text-sm">
      <p className="text-[11px] font-semibold text-zinc-400 uppercase tracking-widest mb-1">{label}</p>
      <p className="font-bold text-emerald-400 text-base">{fmt(payload[0].value)}</p>
    </div>
  );
}

export default function SpendingTrendChart({ transactions }) {
  const data = useMemo(() => buildMonthlyData(transactions || []), [transactions]);

  if (data.length < 2) return null;

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, delay: 0.45, ease: [0.23, 1, 0.32, 1] }}
      className="glass-card p-5"
    >
      <div className="mb-5">
        <p className="text-[11px] font-semibold text-zinc-400 dark:text-zinc-500 uppercase tracking-widest">
          Aylık Harcama Trendi
        </p>
        <p className="text-xs text-zinc-500 dark:text-zinc-600 mt-0.5">
          Son {data.length} ay
        </p>
      </div>

      <ResponsiveContainer width="100%" height={220}>
        <AreaChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: 0 }}>
          <defs>
            <linearGradient id="areaGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%"   stopColor="#10b981" stopOpacity={0.35} />
              <stop offset="100%" stopColor="#10b981" stopOpacity={0}    />
            </linearGradient>
          </defs>

          <CartesianGrid
            strokeDasharray="3 3"
            stroke="rgba(255,255,255,0.04)"
            vertical={false}
          />
          <XAxis
            dataKey="name"
            tick={{ fontSize: 11, fill: '#71717a', fontWeight: 500 }}
            axisLine={false}
            tickLine={false}
            interval="preserveStartEnd"
          />
          <YAxis
            tick={{ fontSize: 11, fill: '#71717a', fontWeight: 500 }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v) =>
              v >= 1000 ? `${(v / 1000).toFixed(0)}k` : String(v)
            }
            width={48}
          />
          <Tooltip content={<CustomTooltip />} cursor={{ stroke: 'rgba(16,185,129,0.3)', strokeWidth: 1 }} />

          <Area
            type="monotone"
            dataKey="total"
            stroke="#10b981"
            strokeWidth={2}
            fill="url(#areaGradient)"
            dot={false}
            activeDot={{ r: 5, fill: '#10b981', stroke: '#050507', strokeWidth: 2 }}
            isAnimationActive
            animationDuration={1200}
            animationEasing="ease-out"
          />
        </AreaChart>
      </ResponsiveContainer>
    </motion.div>
  );
}
