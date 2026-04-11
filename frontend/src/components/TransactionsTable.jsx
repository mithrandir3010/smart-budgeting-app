const CATEGORY_COLORS = {
  'Kira':    { bg: '#ede9fe', text: '#6d28d9' },
  'Market':  { bg: '#d1fae5', text: '#065f46' },
  'Kafe':    { bg: '#fef3c7', text: '#92400e' },
  'Fatura':  { bg: '#dbeafe', text: '#1e40af' },
  'Ulaşım':  { bg: '#fce7f3', text: '#9d174d' },
  'Diğer':   { bg: '#f3f4f6', text: '#374151' },
};

function categoryBadge(category) {
  const c = CATEGORY_COLORS[category] || CATEGORY_COLORS['Diğer'];
  return (
    <span style={{
      background: c.bg,
      color: c.text,
      padding: '2px 10px',
      borderRadius: '9999px',
      fontSize: '12px',
      fontWeight: '600',
    }}>
      {category || 'Diğer'}
    </span>
  );
}

export default function TransactionsTable({ transactions }) {
  if (!transactions || transactions.length === 0) {
    return (
      <div style={styles.empty}>Gösterilecek işlem bulunamadı.</div>
    );
  }

  const sorted = [...transactions].sort(
    (a, b) => new Date(b.date) - new Date(a.date)
  );

  return (
    <div style={styles.wrapper}>
      <table style={styles.table}>
        <thead>
          <tr>
            {['Tarih', 'Açıklama', 'Kategori', 'Tutar'].map((h) => (
              <th key={h} style={styles.th}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sorted.map((tx, i) => (
            <tr
              key={i}
              style={{
                ...styles.tr,
                background: i % 2 === 0 ? '#fff' : '#f9fafb',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = '#eff6ff';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background =
                  i % 2 === 0 ? '#fff' : '#f9fafb';
              }}
            >
              <td style={styles.td}>
                {new Date(tx.date).toLocaleDateString('tr-TR')}
              </td>
              <td style={{ ...styles.td, color: '#111827', fontWeight: '500' }}>
                {tx.description || '—'}
              </td>
              <td style={styles.td}>{categoryBadge(tx.category)}</td>
              <td style={{ ...styles.td, textAlign: 'right', fontWeight: '600', color: '#111827' }}>
                {Number(tx.amount).toLocaleString('tr-TR', {
                  style: 'currency',
                  currency: tx.currency || 'TRY',
                })}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const styles = {
  wrapper: {
    overflowX: 'auto',
    borderRadius: '8px',
    border: '1px solid #e5e7eb',
  },
  table: {
    width: '100%',
    borderCollapse: 'collapse',
    fontSize: '14px',
  },
  th: {
    padding: '12px 16px',
    textAlign: 'left',
    fontSize: '11px',
    fontWeight: '700',
    color: '#6b7280',
    textTransform: 'uppercase',
    letterSpacing: '0.06em',
    background: '#f3f4f6',
    borderBottom: '1px solid #e5e7eb',
  },
  tr: {
    transition: 'background 0.15s',
  },
  td: {
    padding: '12px 16px',
    borderBottom: '1px solid #f3f4f6',
    color: '#6b7280',
    verticalAlign: 'middle',
  },
  empty: {
    padding: '32px',
    textAlign: 'center',
    color: '#9ca3af',
    fontSize: '14px',
  },
};
