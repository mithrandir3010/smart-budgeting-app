function buildInsight(summary) {
  if (!summary) return null;

  const { totalSpending, categoryBreakdown, warning } = summary;

  const topCategory = Object.entries(categoryBreakdown || {}).sort(
    ([, a], [, b]) => b - a
  )[0];

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
      text: `Limitin içindesin, aferin! En yüksek harcaman "${topName}" kategorisinde: ${topAmount}. Bu ay dengeli görünüyor, böyle devam et.`,
    };
  }

  return {
    mood: 'ok',
    text: 'Henüz yeterli veri yok. Ekstre yükledikçe sana daha akıllıca öneriler sunabilirim.',
  };
}

export default function SerenaInsightCard({ summary }) {
  const insight = buildInsight(summary);
  if (!insight) return null;

  const isWarning = insight.mood === 'warning';

  return (
    <div style={{
      ...styles.card,
      borderLeft: `4px solid ${isWarning ? '#ef4444' : '#10b981'}`,
      background: isWarning ? '#fff5f5' : '#f0fdf4',
    }}>
      <div style={styles.header}>
        <div style={styles.avatar}>
          {isWarning ? '🔴' : '🟢'}
        </div>
        <div>
          <p style={styles.name}>Serena</p>
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
    gap: '12px',
    marginBottom: '12px',
  },
  avatar: {
    fontSize: '28px',
    lineHeight: 1,
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
  },
  text: {
    margin: 0,
    fontSize: '14px',
    lineHeight: '1.65',
    fontStyle: 'italic',
  },
};
