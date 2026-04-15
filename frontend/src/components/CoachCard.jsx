/**
 * CoachCard
 *
 * Kullanıcının monthlyBudget değerine göre iki mod:
 *  1. Bütçe yok  → "Bütçe Hedefi Belirle" prompt kartı
 *  2. Bütçe var  → Harcama hızı + projeksiyon + progress bar
 */

import { useState } from 'react';
import { TrendingUp, Zap, BarChart2, Target, Loader2 } from 'lucide-react';

function fmt(n) {
  return Number(n).toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' });
}

// ── Progress Bar ──────────────────────────────────────────────────────────────

function ProgressBar({ current, projected, budget }) {
  const budgetNum    = Number(budget)    || 0;
  const currentNum   = Number(current)   || 0;
  const projectedNum = Number(projected) || 0;
  if (budgetNum === 0) return null;

  const max      = budgetNum * 1.5;
  const curPct   = Math.min((currentNum   / max) * 100, 100);
  const projPct  = Math.min((projectedNum / max) * 100, 100);
  const limitPct = Math.min((budgetNum    / max) * 100, 100);
  const isOverBudget = projectedNum > budgetNum;

  return (
    <div className="mb-4">
      <div className="relative h-2.5 bg-zinc-200 dark:bg-zinc-700 rounded-full mb-2.5">
        <div
          className="absolute left-0 top-0 h-full rounded-full transition-all duration-500"
          style={{ width: `${curPct}%`, background: isOverBudget ? '#f43f5e' : '#6366f1' }}
        />
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

// ── Bütçe Belirleme Prompt Kartı ──────────────────────────────────────────────

function BudgetPromptCard({ hasData, onBudgetSet }) {
  const [input, setInput]   = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError]   = useState('');

  const handleSave = async () => {
    const amount = parseFloat(input);
    if (!input || isNaN(amount) || amount <= 0) {
      setError('Geçerli bir tutar girin (örn: 10000).');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await onBudgetSet(amount);
    } catch {
      setError('Kaydedilemedi, lütfen tekrar deneyin.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="rounded-xl border border-dashed border-indigo-300 dark:border-indigo-700 bg-indigo-50 dark:bg-indigo-950/20 p-5">
      <div className="flex items-start gap-3 mb-4">
        <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-indigo-100 dark:bg-indigo-900/40 flex-shrink-0">
          <Target size={20} className="text-indigo-500" strokeWidth={2} />
        </div>
        <div>
          <p className="font-bold text-sm text-zinc-900 dark:text-zinc-100">
            Bütçe Hedefi Belirle
          </p>
          <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5 leading-relaxed">
            {hasData
              ? 'Aylık harcama limitini belirleyerek Serena\'nın seni korumasını sağlayabilirsin.'
              : 'Henüz ekstre yüklemeden de limitini belirleyebilirsin — Serena veriler gelince seni izlemeye başlar.'}
          </p>
        </div>
      </div>

      <div className="flex gap-2">
        <div className="relative flex-1">
          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-zinc-400 font-medium">₺</span>
          <input
            type="number"
            min="1"
            step="100"
            placeholder="Örn: 10000"
            value={input}
            onChange={(e) => { setError(''); setInput(e.target.value); }}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSave(); }}
            className="w-full pl-7 pr-3 py-2.5 bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-xl text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition placeholder:text-zinc-400"
          />
        </div>
        <button
          onClick={handleSave}
          disabled={saving || !input}
          className="flex items-center gap-1.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold px-4 py-2.5 rounded-xl text-sm transition-colors flex-shrink-0"
        >
          {saving
            ? <Loader2 size={14} className="animate-spin" />
            : 'Kaydet'
          }
        </button>
      </div>

      {error && (
        <p className="text-xs text-rose-500 font-medium mt-2">{error}</p>
      )}

      <p className="text-xs text-zinc-400 dark:text-zinc-500 mt-3 italic">
        İstediğin zaman "Limitler" menüsünden değiştirebilirsin.
      </p>
    </div>
  );
}

// ── Ana Bileşen ───────────────────────────────────────────────────────────────

export default function CoachCard({ summary, onBudgetSet }) {
  if (!summary) return null;

  const hasData   = Number(summary.totalSpending) > 0;
  const hasBudget = summary.monthlyBudget != null;

  // Bütçe hedefi belirlenmemiş → prompt göster
  if (!hasBudget) {
    return <BudgetPromptCard hasData={hasData} onBudgetSet={onBudgetSet} />;
  }

  // Bütçe var ama henüz veri yok → render etme
  if (!hasData) return null;

  const { projectedSpending, dailyRate, monthlyBudget, totalSpending } = summary;
  const projected    = Number(projectedSpending) || 0;
  const budget       = Number(monthlyBudget)     || 0;
  const isOverBudget = projected > budget;
  const diff         = Math.abs(projected - budget);
  const diffPct      = budget ? ((diff / budget) * 100).toFixed(0) : 0;

  return (
    <div className={`rounded-xl border-l-4 p-5 shadow-sm transition-colors ${
      isOverBudget
        ? 'bg-rose-50 dark:bg-rose-950/20 border-rose-500'
        : 'bg-emerald-50 dark:bg-emerald-950/20 border-emerald-500'
    }`}>

      {/* Başlık */}
      <div className="flex items-center gap-3 mb-4">
        <div className={`flex items-center justify-center w-10 h-10 rounded-xl flex-shrink-0 ${
          isOverBudget ? 'bg-rose-100 dark:bg-rose-900/40' : 'bg-emerald-100 dark:bg-emerald-900/40'
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
          isOverBudget ? 'text-rose-500' : 'text-emerald-700 dark:text-emerald-400'
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
