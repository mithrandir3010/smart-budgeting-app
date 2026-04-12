/**
 * SerenaInsightCard
 *
 * Serena'nın koçluk mesajını görüntüler. Metin kaynağı olarak doğrudan
 * backend'deki AnalyticsService.buildCoachAdvice() çıktısını (summary.coachAdvice)
 * kullanır. Bu, MCP tool'u "serena_get_budget_summary" ile birebir aynı veridir —
 * ek bir hesaplama ya da harici API çağrısı yapılmaz.
 *
 * Görsel tasarım:
 *  - Uyarı modu  → kırmızı arka plan, sol kenar çizgisi, kırmızı metin
 *  - Normal mod  → yeşil arka plan, sol kenar çizgisi, yeşil metin
 *  - En yüksek kategori ikonu (categoryBreakdown'dan türetilir)
 */

import {
  Home,
  ShoppingCart,
  Coffee,
  Zap,
  Car,
  ShoppingBag,
  Utensils,
  Wifi,
  Smartphone,
} from 'lucide-react';

const CATEGORY_ICONS = {
  'Kira':      { Icon: Home,         color: '#6366f1' },
  'Market':    { Icon: ShoppingCart, color: '#f59e0b' },
  'Kafe':      { Icon: Coffee,       color: '#92400e' },
  'Fatura':    { Icon: Zap,          color: '#0ea5e9' },
  'Ulaşım':    { Icon: Car,          color: '#10b981' },
  'Yemek':     { Icon: Utensils,     color: '#f97316' },
  'İnternet':  { Icon: Wifi,         color: '#6366f1' },
  'Telefon':   { Icon: Smartphone,   color: '#8b5cf6' },
  'Diğer':     { Icon: ShoppingBag,  color: '#8b5cf6' },
};

const DEFAULT_ICON = { Icon: ShoppingBag, color: '#8b5cf6' };

function topCategory(categoryBreakdown) {
  const entries = Object.entries(categoryBreakdown || {});
  if (!entries.length) return null;
  return entries.sort(([, a], [, b]) => Number(b) - Number(a))[0];
}

export default function SerenaInsightCard({ summary }) {
  if (!summary) return null;

  // ── Kaynak: backend'den gelen hesaplanmış metin ──────────────────────────
  // coachAdvice = AnalyticsService.buildCoachAdvice() = MCP serena_get_budget_summary
  const text        = summary.coachAdvice ?? 'Ekstre yükledikçe sana özel öneriler sunacağım.';
  const isWarning   = !!summary.warning;
  const accentColor = isWarning ? '#ef4444' : '#10b981';

  // En yüksek harcama kategorisinin ikonu
  const top             = topCategory(summary.categoryBreakdown);
  const topName         = top?.[0] ?? null;
  const { Icon, color } = CATEGORY_ICONS[topName] ?? DEFAULT_ICON;

  return (
    <div style={{
      ...styles.card,
      borderLeft: `4px solid ${accentColor}`,
      background: isWarning ? '#fff5f5' : '#f0fdf4',
    }}>

      {/* Kart başlığı: kategori ikonu + Serena kimliği */}
      <div style={styles.header}>
        {topName && (
          <div style={{
            ...styles.categoryIconWrap,
            background: `${color}18`,
          }}>
            <Icon size={36} color={color} strokeWidth={1.8} />
          </div>
        )}

        <div style={styles.identity}>
          <div style={styles.nameRow}>
            <span style={{ ...styles.statusDot, background: accentColor }} />
            <p style={styles.name}>Serena</p>
          </div>
          <p style={styles.role}>AI Finansal Koçun</p>
        </div>

        {/* MCP kaynak rozeti */}
        <div style={{ ...styles.sourceBadge, color: accentColor, borderColor: `${accentColor}40` }}>
          MCP analizi
        </div>
      </div>

      {/* Koçluk metni — tek kaynak: summary.coachAdvice */}
      <p style={{
        ...styles.text,
        color: isWarning ? '#7f1d1d' : '#14532d',
      }}>
        {text}
      </p>
    </div>
  );
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
    gap: '14px',
    marginBottom: '14px',
  },
  categoryIconWrap: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: '60px',
    height: '60px',
    borderRadius: '14px',
    flexShrink: 0,
  },
  identity: {
    display: 'flex',
    flexDirection: 'column',
    gap: '2px',
    flex: 1,
  },
  nameRow: {
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
  },
  statusDot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    flexShrink: 0,
  },
  name: {
    margin: 0,
    fontWeight: '700',
    fontSize: '15px',
    color: '#111827',
  },
  role: {
    margin: 0,
    fontSize: '12px',
    color: '#6b7280',
    paddingLeft: '14px',
  },
  sourceBadge: {
    fontSize: '10px',
    fontWeight: '600',
    border: '1px solid',
    borderRadius: '999px',
    padding: '2px 8px',
    letterSpacing: '0.04em',
    whiteSpace: 'nowrap',
    flexShrink: 0,
  },
  text: {
    margin: 0,
    fontSize: '14px',
    lineHeight: '1.65',
    fontStyle: 'italic',
  },
};
