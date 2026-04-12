/**
 * CoachCard
 *
 * Harcama verilerini görselleştirir — sayılar, progress bar, tahmin.
 * Koçluk metnini göstermez; o sorumluluk SerenaInsightCard'a aittir.
 *
 * Veri kaynağı: /api/v1/analytics/summary (AnalyticsService)
 * Aynı veri MCP tool serena_get_budget_summary üzerinden de erişilebilirdir.
 */

import { TrendingUp, Zap, BarChart2 } from 'lucide-react';

function ProgressBar({ current, projected, budget }) {
  const budgetNum    = Number(budget)    || 10000;
  const currentNum   = Number(current)   || 0;
  const projectedNum = Number(projected) || 0;

  // Bar maksimumu: bütçenin 1.5 katı — aşım görünür kalsın
  const max      = budgetNum * 1.5;
  const curPct   = Math.min((currentNum   / max) * 100, 100);
  const projPct  = Math.min((projectedNum / max) * 100, 100);
  const limitPct = Math.min((budgetNum    / max) * 100, 100);

  const isOverBudget = projectedNum > budgetNum;

  return (
    <div style={styles.barWrap}>
      <div style={styles.barTrack}>
        {/* Gerçekleşen harcama */}
        <div style={{
          ...styles.barFill,
          width: `${curPct}%`,
          background: isOverBudget ? '#ef4444' : '#6366f1',
        }} />

        {/* Tahmini ek harcama — çizgili */}
        {projPct > curPct && (
          <div style={{
            ...styles.barProjected,
            left: `${curPct}%`,
            width: `${projPct - curPct}%`,
            background: isOverBudget
              ? 'repeating-linear-gradient(45deg,#fca5a5,#fca5a5 4px,#fee2e2 4px,#fee2e2 8px)'
              : 'repeating-linear-gradient(45deg,#a5b4fc,#a5b4fc 4px,#e0e7ff 4px,#e0e7ff 8px)',
          }} />
        )}

        {/* Limit işareti */}
        <div style={{ ...styles.limitLine, left: `${limitPct}%` }} />
      </div>

      <div style={styles.barLabels}>
        <span style={{ color: isOverBudget ? '#ef4444' : '#6366f1', fontWeight: 600 }}>
          Şu an: {fmt(currentNum)}
        </span>
        <span style={{ color: '#9ca3af' }}>
          Limit: {fmt(budgetNum)}
        </span>
        <span style={{ color: isOverBudget ? '#ef4444' : '#10b981', fontWeight: 600 }}>
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
  const accentColor  = isOverBudget ? '#ef4444' : '#10b981';
  const bgColor      = isOverBudget ? '#fff5f5' : '#f0fdf4';

  // Kalan limit ya da aşım miktarı
  const diff    = Math.abs(projected - budget);
  const diffPct = budget ? ((diff / budget) * 100).toFixed(0) : 0;

  return (
    <div style={{
      ...styles.card,
      background: bgColor,
      borderLeft: `4px solid ${accentColor}`,
    }}>

      {/* Başlık satırı */}
      <div style={styles.header}>
        <div style={{ ...styles.iconWrap, background: `${accentColor}18` }}>
          <BarChart2 size={22} color={accentColor} strokeWidth={2} />
        </div>

        <div style={{ flex: 1 }}>
          <p style={styles.title}>Harcama Hızı & Projeksiyon</p>
          <p style={styles.subtitle}>Ay sonu tahmini — gerçek zamanlı</p>
        </div>

        {/* Günlük harcama hızı rozeti */}
        {Number(dailyRate) > 0 && (
          <div style={{ ...styles.badge, background: `${accentColor}15`, color: accentColor }}>
            <Zap size={12} strokeWidth={2.5} />
            <span>{fmt(Number(dailyRate))} / gün</span>
          </div>
        )}
      </div>

      {/* Progress bar */}
      <ProgressBar
        current={totalSpending}
        projected={projectedSpending}
        budget={monthlyBudget}
      />

      {/* Ay sonu tahmini — büyük değer */}
      <div style={styles.projectedWrap}>
        <TrendingUp size={18} color={accentColor} strokeWidth={2} />
        <span style={{ ...styles.projectedLabel, color: '#6b7280' }}>Ay sonu tahmini:</span>
        <span style={{ ...styles.projectedValue, color: accentColor }}>
          {fmt(projected)}
        </span>

        {isOverBudget ? (
          <span style={styles.overagePill}>
            +%{diffPct} limit üstü
          </span>
        ) : (
          <span style={styles.surplusPill}>
            -%{diffPct} altında ✓
          </span>
        )}
      </div>

      {/* Kalan / aşım özeti */}
      <p style={{ ...styles.summaryLine, color: isOverBudget ? '#b91c1c' : '#15803d' }}>
        {isOverBudget
          ? `Tahmini aşım: ${fmt(diff)} — günlük hızı düşürmek durumu değiştirir.`
          : `Tahmini tasarruf: ${fmt(diff)} — limitin altında kalıyorsun.`
        }
      </p>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Yardımcılar
// ─────────────────────────────────────────────────────────────────────────────

function fmt(n) {
  return Number(n).toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' });
}

const styles = {
  card: {
    borderRadius: '12px',
    padding: '20px 24px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.06)',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    marginBottom: '16px',
  },
  iconWrap: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: '42px',
    height: '42px',
    borderRadius: '10px',
    flexShrink: 0,
  },
  title: {
    margin: 0,
    fontWeight: '700',
    fontSize: '15px',
    color: '#111827',
  },
  subtitle: {
    margin: 0,
    fontSize: '12px',
    color: '#6b7280',
  },
  badge: {
    display: 'flex',
    alignItems: 'center',
    gap: '4px',
    padding: '4px 10px',
    borderRadius: '999px',
    fontSize: '12px',
    fontWeight: '600',
    flexShrink: 0,
  },
  barWrap: {
    marginBottom: '14px',
  },
  barTrack: {
    position: 'relative',
    height: '10px',
    background: '#e5e7eb',
    borderRadius: '999px',
    overflow: 'visible',
    marginBottom: '6px',
  },
  barFill: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: '100%',
    borderRadius: '999px',
    transition: 'width 0.4s ease',
  },
  barProjected: {
    position: 'absolute',
    top: 0,
    height: '100%',
    borderRadius: '0 999px 999px 0',
  },
  limitLine: {
    position: 'absolute',
    top: '-4px',
    width: '2px',
    height: '18px',
    background: '#374151',
    borderRadius: '1px',
  },
  barLabels: {
    display: 'flex',
    justifyContent: 'space-between',
    fontSize: '11px',
    color: '#6b7280',
  },
  projectedWrap: {
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    marginBottom: '10px',
    flexWrap: 'wrap',
  },
  projectedLabel: {
    fontSize: '13px',
  },
  projectedValue: {
    fontSize: '18px',
    fontWeight: '700',
    letterSpacing: '-0.5px',
  },
  overagePill: {
    background: '#fee2e2',
    color: '#b91c1c',
    padding: '2px 8px',
    borderRadius: '999px',
    fontSize: '11px',
    fontWeight: '600',
  },
  surplusPill: {
    background: '#dcfce7',
    color: '#15803d',
    padding: '2px 8px',
    borderRadius: '999px',
    fontSize: '11px',
    fontWeight: '600',
  },
  summaryLine: {
    margin: 0,
    fontSize: '12.5px',
    lineHeight: '1.5',
    fontWeight: '500',
  },
};
