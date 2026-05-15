import { motion } from 'framer-motion';
import {
  PieChart, Pie, Cell, Tooltip, ResponsiveContainer,
} from 'recharts';
import { fmt } from '../../utils/helpers';

const PALETTE = [
  '#10b981', // emerald
  '#f59e0b', // amber
  '#6366f1', // indigo
  '#f43f5e', // rose
  '#3b82f6', // blue
  '#8b5cf6', // violet
  '#14b8a6', // teal
  '#ec4899', // pink
];

function CustomTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-white dark:bg-[#111118] border border-zinc-200 dark:border-white/[0.08] rounded-xl px-4 py-3 shadow-2xl text-sm">
      <p className="text-[11px] font-semibold text-zinc-500 dark:text-zinc-400 uppercase tracking-widest mb-1">
        {payload[0].name}
      </p>
      <p className="font-bold text-zinc-900 dark:text-zinc-100 text-base">
        {fmt(payload[0].value)}
      </p>
      <p className="text-[11px] text-zinc-500 mt-0.5">
        %{(payload[0].payload.percent * 100).toFixed(1)}
      </p>
    </div>
  );
}

function CustomLegend({ data, total }) {
  return (
    <div className="space-y-2 w-full">
      {data.map((item, i) => {
        const pct = total > 0 ? (item.value / total) * 100 : 0;
        return (
          <div key={item.name} className="flex items-center gap-2.5">
            <span
              className="w-2.5 h-2.5 rounded-full flex-shrink-0"
              style={{ background: PALETTE[i % PALETTE.length] }}
            />
            <span className="text-xs text-zinc-500 dark:text-zinc-400 flex-1 truncate">{item.name}</span>
            <span className="text-xs font-semibold text-zinc-700 dark:text-zinc-300 tabular-nums">
              %{pct.toFixed(0)}
            </span>
          </div>
        );
      })}
    </div>
  );
}

export default function DonutChart({ categoryBreakdown }) {
  const raw = Object.entries(categoryBreakdown || {});
  if (!raw.length) return null;

  const total = raw.reduce((s, [, v]) => s + Number(v), 0);
  const data  = raw
    .map(([name, value]) => ({ name, value: parseFloat(value), percent: parseFloat(value) / total }))
    .sort((a, b) => b.value - a.value);

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: 0.3, ease: [0.23, 1, 0.32, 1] }}
      className="glass-card p-5"
    >
      <div className="flex items-center justify-between mb-5">
        <div>
          <p className="text-[11px] font-semibold text-zinc-500 uppercase tracking-widest">
            Kategori Dağılımı
          </p>
          <p className="text-xs text-zinc-500 dark:text-zinc-600 mt-0.5">
            {data.length} kategori · {fmt(total)}
          </p>
        </div>
      </div>

      <div className="flex flex-col sm:flex-row gap-6 items-center">
        {/* Donut */}
        <div className="relative flex-shrink-0" style={{ width: 200, height: 200 }}>
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={data}
                cx="50%"
                cy="50%"
                innerRadius={58}
                outerRadius={88}
                dataKey="value"
                strokeWidth={2}
                stroke="transparent"
                paddingAngle={3}
                isAnimationActive
                animationBegin={0}
                animationDuration={900}
              >
                {data.map((_, i) => (
                  <Cell
                    key={i}
                    fill={PALETTE[i % PALETTE.length]}
                    opacity={0.9}
                  />
                ))}
              </Pie>
              <Tooltip content={<CustomTooltip />} />
            </PieChart>
          </ResponsiveContainer>

          {/* Center label */}
          <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
            <p className="text-[10px] text-zinc-500 font-semibold uppercase tracking-widest">Toplam</p>
            <p className="text-base font-bold text-zinc-900 dark:text-zinc-100 leading-tight mt-0.5">
              {fmt(total)}
            </p>
          </div>
        </div>

        {/* Legend */}
        <div className="flex-1 min-w-0">
          <CustomLegend data={data} total={total} />
        </div>
      </div>
    </motion.div>
  );
}
