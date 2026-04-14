/**
 * CoachCard
 * Harcama verilerini görselleştirir — progress bar, tahmin, günlük hız.
 * Veri: /api/v1/analytics/summary (AnalyticsService)
 */

import { TrendingUp, Zap, BarChart2 } from 'lucide-react';

function fmt(n) {
  return Number(n).toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' });
}

function ProgressBar({ current, projected, budget }) {
  const budgetNum    = Number(budget)    || 10000;
  const currentNum   = Number(current)   || 0;
  const projectedNum = Number(projected) || 0;

  const max      = budgetNum * 1.5;
  const curPct   = Math.min((currentNum   / max) * 100, 100);
  const projPct  = Math.min((projectedNum / max) * 100, 100);
  const limitPct = Math.min((budgetNum    / max) * 100, 100);
  const isOverBudget = projectedNum > budgetNum;

  return (
    <div className="mb-4">
      <div className="relative h-2.5 bg-zinc-200 dark:bg-zinc-700 rounded-full mb-2.5">
        {/* Gerçekleşen */}
        <div
          className="absolute left-0 top-0 h-full rounded-full transition-all duration-500"
          style={{
            width: `${curPct}%`,
            background: isOverBudget ? '#f43f5e' : '#6366f1',
          }}
        />
        {/* Tahmini ek — çizgili desen */}
        {projPct > curPct && (
          <div
            className="absolute top-0 h-full rounded-r-full"
            style={{
              left: `${curPct}%`,
              width: `${projPct - curPct}%`,
              background: isOverBudget
                ? 'repeating-linear-gradient(45deg,#fda4af,#fda4af 4px,#ffe4e6 4px,#ffe4e6 8px)'
                : 'repeating-linear-gradient(45deg,#a5b4fc,#a5b4fc 4px,#e0e7ff 4px,#e0e7ff 8px)',
            }}
          />
        )}
        {/* Limit çizgisi */}
        <div
          className="absolute top-[-5px] w-0.5 h-5 bg-zinc-500 dark:bg-zinc-400 rounded-sm"
          style={{ left: `${limitPct}%` }}
        />
      </div>
      <div className="flex justify-between text-xs">
        <span className={`font-semibold ${isOverBudget ? 'text-rose-500' : 'text-indigo-500'}`}>
          Şu an: {fmt(currentNum)}
        </span>
        <span className="text-zinc-400 dark:text-zinc-500">
          Limit: {fmt(budgetNum)}
        </span>
        <span className={`font-semibold ${isOverBudget ? 'text-rose-500' : 'text-emerald-500'}`}>
          Tahmin: {fmt(projectedNum)}
        </span>
      </div>
    </div>
  );
}

export default function CoachCard({ summary }) {
  if (!summary || Number(summary.totalSpending) === 0) return null;

  const { projectedSpending, dailyRate, monthlyBudget, totalSpending } = summary;
  const projected    = Number(projectedSpending) || 0;
  const budget       = Number(monthlyBudget)      || 10000;
  const isOverBudget = projected > budget;

  const diff    = Math.abs(projected - budget);
  const diffPct = budget ? ((diff / budget) * 100).toFixed(0) : 0;

  return (
    <div className={`rounded-xl border-l-4 p-5 shadow-sm transition-colors ${
      isOverBudget
        ? 'bg-rose-50 dark:bg-rose-950/20 border-rose-500'
        : 'bg-emerald-50 dark:bg-emerald-950/20 border-emerald-500'
    }`}>

      {/* Başlık */}
      <div className="flex items-center gap-3 mb-4">
        <div className={`flex items-center justify-center w-10 h-10 rounded-xl flex-shrink-0 ${
          isOverBudget
            ? 'bg-rose-100 dark:bg-rose-900/40'
            : 'bg-emerald-100 dark:bg-emerald-900/40'
        }`}>
          <BarChart2
            size={20}
            strokeWidth={2}
            className={isOverBudget ? 'text-rose-500' : 'text-emerald-600 dark:text-emerald-400'}
          />
        </div>

        <div className="flex-1 min-w-0">
          <p className="font-bold text-sm text-zinc-900 dark:text-zinc-100">
            Harcama Hızı & Projeksiyon
          </p>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">
            Ay sonu tahmini — gerçek zamanlı
          </p>
        </div>

        {Number(dailyRate) > 0 && (
          <div className={`flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold flex-shrink-0 ${
            isOverBudget
              ? 'bg-rose-100 dark:bg-rose-900/50 text-rose-600 dark:text-rose-400'
              : 'bg-emerald-100 dark:bg-emerald-900/50 text-emerald-700 dark:text-emerald-400'
          }`}>
            <Zap size={11} strokeWidth={2.5} />
            <span>{fmt(Number(dailyRate))} / gün</span>
          </div>
        )}
      </div>

      <ProgressBar
        current={totalSpending}
        projected={projectedSpending}
        budget={monthlyBudget}
      />

      {/* Ay sonu tahmini */}
      <div className="flex items-center gap-2 mb-3 flex-wrap">
        <TrendingUp
          size={16}
          strokeWidth={2}
          className={isOverBudget ? 'text-rose-500' : 'text-emerald-600 dark:text-emerald-400'}
        />
        <span className="text-sm text-zinc-500 dark:text-zinc-400">Ay sonu tahmini:</span>
        <span className={`text-lg font-bold tracking-tight ${
          isOverBudget
            ? 'text-rose-500'
            : 'text-emerald-700 dark:text-emerald-400'
        }`}>
          {fmt(projected)}
        </span>

        {isOverBudget ? (
          <span className="bg-rose-100 dark:bg-rose-900/60 text-rose-700 dark:text-rose-400 text-xs font-semibold px-2 py-0.5 rounded-full">
            +%{diffPct} limit üstü
          </span>
        ) : (
          <span className="bg-emerald-100 dark:bg-emerald-900/60 text-emerald-700 dark:text-emerald-400 text-xs font-semibold px-2 py-0.5 rounded-full">
            -%{diffPct} altında ✓
          </span>
        )}
      </div>

      <p className={`text-xs font-medium leading-relaxed ${
        isOverBudget
          ? 'text-rose-700 dark:text-rose-400'
          : 'text-emerald-700 dark:text-emerald-400'
      }`}>
        {isOverBudget
          ? `Tahmini aşım: ${fmt(diff)} — günlük hızı düşürmek durumu değiştirir.`
          : `Tahmini tasarruf: ${fmt(diff)} — limitin altında kalıyorsun.`
        }
      </p>
    </div>
  );
}
