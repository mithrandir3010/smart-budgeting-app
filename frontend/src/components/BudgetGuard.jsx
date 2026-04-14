import { useState } from 'react';
import { AlertTriangle, XCircle, CheckCircle, X, ChevronDown, ChevronUp } from 'lucide-react';

/**
 * BudgetGuard
 *
 * AnalyticsSummaryDto.alerts listesini okur ve kullanıcıya bütçe uyarılarını gösterir.
 * OK statüsündeki kategoriler varsayılan olarak gizlenir.
 *
 * Statüs renkleri:
 *   CRITICAL → Kırmızı (≥%90)
 *   WARNING  → Amber   (%70–89)
 *   OK       → Yeşil   (<%70)
 */

const STATUS_CONFIG = {
  CRITICAL: {
    Icon: XCircle,
    bar:    'bg-rose-500',
    badge:  'bg-rose-100 dark:bg-rose-900/40 text-rose-700 dark:text-rose-400 border-rose-200 dark:border-rose-800',
    card:   'border-rose-200 dark:border-rose-900/60 bg-rose-50 dark:bg-rose-950/20',
    label:  'KRİTİK',
    icon:   'text-rose-500',
  },
  WARNING: {
    Icon: AlertTriangle,
    bar:    'bg-amber-400',
    badge:  'bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-400 border-amber-200 dark:border-amber-800',
    card:   'border-amber-200 dark:border-amber-900/60 bg-amber-50 dark:bg-amber-950/20',
    label:  'UYARI',
    icon:   'text-amber-500',
  },
  OK: {
    Icon: CheckCircle,
    bar:    'bg-emerald-500',
    badge:  'bg-emerald-100 dark:bg-emerald-900/40 text-emerald-700 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800',
    card:   'border-zinc-100 dark:border-zinc-800 bg-white dark:bg-zinc-900',
    label:  'İYİ',
    icon:   'text-emerald-500',
  },
};

function AlertCard({ alert }) {
  const cfg = STATUS_CONFIG[alert.status] || STATUS_CONFIG.OK;
  const { Icon } = cfg;
  const pct = Math.min(alert.percentageUsed, 100);

  const formatTRY = (v) =>
    Number(v).toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' });

  return (
    <div className={`rounded-xl border p-4 transition-colors ${cfg.card}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2.5 min-w-0">
          <Icon size={18} className={`flex-shrink-0 ${cfg.icon}`} strokeWidth={2} />
          <div className="min-w-0">
            <p className="text-sm font-semibold text-zinc-900 dark:text-zinc-100 truncate">
              {alert.category}
            </p>
            <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
              {formatTRY(alert.spent)} / {formatTRY(alert.limitAmount)}
            </p>
          </div>
        </div>

        <div className="flex-shrink-0 text-right">
          <span className={`inline-block text-xs font-bold border rounded-full px-2 py-0.5 ${cfg.badge}`}>
            {cfg.label}
          </span>
          <p className="text-xs font-semibold text-zinc-600 dark:text-zinc-400 mt-0.5 tabular-nums">
            %{alert.percentageUsed.toFixed(1)}
          </p>
        </div>
      </div>

      {/* Progress bar */}
      <div className="mt-3 h-1.5 bg-zinc-200 dark:bg-zinc-700 rounded-full overflow-hidden">
        <div
          className={`h-full rounded-full transition-all duration-500 ${cfg.bar}`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}

export default function BudgetGuard({ alerts }) {
  const [showOk, setShowOk] = useState(false);

  if (!alerts || alerts.length === 0) return null;

  const critical = alerts.filter((a) => a.status === 'CRITICAL');
  const warning  = alerts.filter((a) => a.status === 'WARNING');
  const ok       = alerts.filter((a) => a.status === 'OK');

  const activeAlerts = [...critical, ...warning];
  const hasActive    = activeAlerts.length > 0;

  if (!hasActive && ok.length === 0) return null;

  return (
    <div className="bg-white dark:bg-zinc-900 rounded-xl shadow-sm border border-zinc-100 dark:border-zinc-800 p-5">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <h2 className="text-sm font-semibold text-zinc-600 dark:text-zinc-400 uppercase tracking-widest">
            Bütçe Uyarıları
          </h2>
          {critical.length > 0 && (
            <span className="inline-block bg-rose-100 dark:bg-rose-900/40 text-rose-700 dark:text-rose-400 text-xs font-bold px-2 py-0.5 rounded-full">
              {critical.length} kritik
            </span>
          )}
          {warning.length > 0 && (
            <span className="inline-block bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-400 text-xs font-bold px-2 py-0.5 rounded-full">
              {warning.length} uyarı
            </span>
          )}
        </div>
        {ok.length > 0 && (
          <button
            onClick={() => setShowOk((v) => !v)}
            className="flex items-center gap-1 text-xs text-zinc-400 dark:text-zinc-500 hover:text-zinc-600 dark:hover:text-zinc-300 transition-colors"
          >
            {showOk ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
            {ok.length} iyi
          </button>
        )}
      </div>

      {/* Active alerts */}
      {hasActive ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {activeAlerts.map((a) => (
            <AlertCard key={a.limitId} alert={a} />
          ))}
        </div>
      ) : (
        <div className="flex items-center gap-2 text-sm text-emerald-600 dark:text-emerald-400">
          <CheckCircle size={16} strokeWidth={2} />
          <span>Tüm kategoriler limit içinde — harika gidiyorsun!</span>
        </div>
      )}

      {/* OK alerts (collapsible) */}
      {showOk && ok.length > 0 && (
        <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-3">
          {ok.map((a) => (
            <AlertCard key={a.limitId} alert={a} />
          ))}
        </div>
      )}

      {/* Hint: limit tanımlı değilse */}
      {alerts.length === 0 && (
        <p className="text-xs text-zinc-400 dark:text-zinc-500 italic">
          Kategori limitlerini tanımlamak için API'yi kullanabilirsin.
        </p>
      )}
    </div>
  );
}
