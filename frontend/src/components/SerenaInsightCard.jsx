/**
 * SerenaInsightCard
 * Serena'nın koçluk mesajını görüntüler.
 * Kaynak: summary.coachAdvice = AnalyticsService.buildCoachAdvice() = MCP serena_get_budget_summary
 */

import {
  Home, ShoppingCart, Coffee, Zap, Car,
  ShoppingBag, Utensils, Wifi, Smartphone,
} from 'lucide-react';

const CATEGORY_MAP = {
  'Kira':      { Icon: Home,         color: 'text-indigo-500', bg: 'bg-indigo-100 dark:bg-indigo-900/40' },
  'Market':    { Icon: ShoppingCart, color: 'text-amber-500',  bg: 'bg-amber-100 dark:bg-amber-900/40' },
  'Kafe':      { Icon: Coffee,       color: 'text-yellow-600', bg: 'bg-yellow-100 dark:bg-yellow-900/40' },
  'Fatura':    { Icon: Zap,          color: 'text-sky-500',    bg: 'bg-sky-100 dark:bg-sky-900/40' },
  'Ulaşım':    { Icon: Car,          color: 'text-emerald-500',bg: 'bg-emerald-100 dark:bg-emerald-900/40' },
  'Yemek':     { Icon: Utensils,     color: 'text-orange-500', bg: 'bg-orange-100 dark:bg-orange-900/40' },
  'İnternet':  { Icon: Wifi,         color: 'text-indigo-500', bg: 'bg-indigo-100 dark:bg-indigo-900/40' },
  'Telefon':   { Icon: Smartphone,   color: 'text-violet-500', bg: 'bg-violet-100 dark:bg-violet-900/40' },
  'Diğer':     { Icon: ShoppingBag,  color: 'text-violet-500', bg: 'bg-violet-100 dark:bg-violet-900/40' },
};

const DEFAULT_ICON = { Icon: ShoppingBag, color: 'text-violet-500', bg: 'bg-violet-100 dark:bg-violet-900/40' };

function topCategory(categoryBreakdown) {
  const entries = Object.entries(categoryBreakdown || {});
  if (!entries.length) return null;
  return entries.sort(([, a], [, b]) => Number(b) - Number(a))[0];
}

export default function SerenaInsightCard({ summary }) {
  if (!summary) return null;

  const text      = summary.coachAdvice ?? 'Ekstre yükledikçe sana özel öneriler sunacağım.';
  const isWarning = !!summary.warning;

  const top               = topCategory(summary.categoryBreakdown);
  const topName           = top?.[0] ?? null;
  const { Icon, color, bg } = CATEGORY_MAP[topName] ?? DEFAULT_ICON;

  return (
    <div className={`rounded-xl border-l-4 p-5 shadow-sm transition-colors ${
      isWarning
        ? 'bg-rose-50 dark:bg-rose-950/20 border-rose-500'
        : 'bg-emerald-50 dark:bg-emerald-950/20 border-emerald-500'
    }`}>

      {/* Başlık */}
      <div className="flex items-center gap-3 mb-3">
        {topName && (
          <div className={`flex items-center justify-center w-14 h-14 rounded-2xl flex-shrink-0 ${bg}`}>
            <Icon size={30} className={color} strokeWidth={1.8} />
          </div>
        )}

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 mb-0.5">
            <span className={`w-2 h-2 rounded-full flex-shrink-0 ${
              isWarning ? 'bg-rose-500' : 'bg-emerald-500'
            }`} />
            <p className="font-bold text-sm text-zinc-900 dark:text-zinc-100">Serena</p>
          </div>
          <p className="text-xs text-zinc-500 dark:text-zinc-400 pl-3.5">AI Finansal Koçun</p>
        </div>

        <span className={`text-xs font-semibold border rounded-full px-2.5 py-0.5 flex-shrink-0 ${
          isWarning
            ? 'text-rose-600 dark:text-rose-400 border-rose-300 dark:border-rose-800'
            : 'text-emerald-600 dark:text-emerald-400 border-emerald-300 dark:border-emerald-800'
        }`}>
          MCP analizi
        </span>
      </div>

      {/* Koçluk metni */}
      <p className={`text-sm leading-relaxed italic ${
        isWarning
          ? 'text-rose-800 dark:text-rose-300'
          : 'text-emerald-800 dark:text-emerald-300'
      }`}>
        {text}
      </p>
    </div>
  );
}
