/**
 * SerenaInsightCard
 * Serena'nın koçluk mesajını görüntüler.
 * Kaynak: summary.coachAdvice = AnalyticsService.buildCoachAdvice() = MCP serena_get_budget_summary
 */

import {
  Home, ShoppingCart, Coffee, Zap, Car,
  ShoppingBag, Utensils, Wifi, Smartphone, Wallet,
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
        <div className={`flex items-center justify-center w-14 h-14 rounded-2xl flex-shrink-0 ${
          topName ? bg : 'bg-indigo-100 dark:bg-indigo-900/40'
        }`}>
          {topName
            ? <Icon size={30} className={color} strokeWidth={1.8} />
            : <Wallet size={28} className="text-indigo-500" strokeWidth={1.8} />
          }
        </div>

        <div className="flex-1 min-w-0">
          <p className="font-bold text-sm text-zinc-900 dark:text-zinc-100">
            Finansal Asistan
          </p>
          <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
            Aylık bütçe hedefi belirleyerek harcamalarını daha iyi kontrol altına alabilirsin.
          </p>
        </div>
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
