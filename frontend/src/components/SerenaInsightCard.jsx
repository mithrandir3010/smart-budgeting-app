import {
  Home,
  ShoppingCart,
  Coffee,
  Zap,
  Car,
  ShoppingBag,
} from 'lucide-react';

const CATEGORY_ICONS = {
  'Kira':    { Icon: Home,         color: '#6366f1' },
  'Market':  { Icon: ShoppingCart, color: '#f59e0b' },
  'Kafe':    { Icon: Coffee,       color: '#92400e' },
  'Fatura':  { Icon: Zap,          color: '#0ea5e9' },
  'Ulaşım':  { Icon: Car,          color: '#10b981' },
  'Diğer':   { Icon: ShoppingBag,  color: '#8b5cf6' },
};

const DEFAULT_ICON = { Icon: ShoppingBag, color: '#8b5cf6' };

function getTopCategory(categoryBreakdown) {
  const entries = Object.entries(categoryBreakdown || {});
  if (!entries.length) return null;
  return entries.sort(([, a], [, b]) => b - a)[0];
}

function buildInsight(summary) {
  if (!summary) return null;

  const { totalSpending, categoryBreakdown, warning } = summary;
  const topCategory = getTopCategory(categoryBreakdown);

  if (warning) {
    const overshoot = (Number(totalSpending) - 10000).toLocaleString('tr-TR', {
      style: 'currency',
      currency: 'TRY',
    });
    const topName = topCategory?.[0] || 'bilinmeyen kategori';
    const topAmount = Number(topCategory?.[1] || 0).toLocaleString('tr-TR', {
      style: 'currency',
      currency: 'TRY',
    });
    return {
      mood: 'warning',
      topCategoryName: topName,
      text: `Bu ay limitini ${overshoot} aştın. En büyük kalem "${topName}" (${topAmount}) — bunu biraz kısmak büyük fark yaratabilir. Ama panikleme, farkında olmak zaten çözümün yarısı.`,
    };
  }

  if (topCategory) {
    const topName = topCategory[0];
    const topAmount = Number(topCategory[1]).toLocaleString('tr-TR', {
      style: 'currency',
      currency: 'TRY',
    });
    return {
      mood: 'ok',
      topCategoryName: topName,
      text: `Limitin içindesin, aferin! En yüksek harcaman "${topName}" kategorisinde: ${topAmount}. Bu ay dengeli görünüyor, böyle devam et.`,
    };
  }

  return {
    mood: 'ok',
    topCategoryName: null,
    text: 'Henüz yeterli veri yok. Ekstre yükledikçe sana daha akıllıca öneriler sunabilirim.',
  };
}

export default function SerenaInsightCard({ summary }) {
  const insight = buildInsight(summary);
  if (!insight) return null;

  const isWarning = insight.mood === 'warning';
  const accentColor = isWarning ? '#ef4444' : '#10b981';

  const { Icon: TopIcon, color: iconColor } =
    CATEGORY_ICONS[insight.topCategoryName] ?? DEFAULT_ICON;
  const showCategoryIcon = !!insight.topCategoryName;

  return (
    <div style={{
      ...styles.card,
      borderLeft: `4px solid ${accentColor}`,
      background: isWarning ? '#fff5f5' : '#f0fdf4',
    }}>
      <div style={styles.header}>

        {/* Top-category icon — büyük ve belirgin */}
        {showCategoryIcon && (
          <div style={{
            ...styles.categoryIconWrap,
            background: `${iconColor}18`,
          }}>
            <TopIcon
              size={36}
              color={iconColor}
              strokeWidth={1.8}
            />
          </div>
        )}

        {/* Serena kimlik bloğu */}
        <div style={styles.identity}>
          <div style={styles.nameRow}>
            <span style={{
              ...styles.statusDot,
              background: accentColor,
            }} />
            <p style={styles.name}>Serena</p>
          </div>
          <p style={styles.role}>AI Finansal Asistanın</p>
        </div>
      </div>

      <p style={{
        ...styles.text,
        color: isWarning ? '#7f1d1d' : '#14532d',
      }}>
        {insight.text}
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
  text: {
    margin: 0,
    fontSize: '14px',
    lineHeight: '1.65',
    fontStyle: 'italic',
  },
};
