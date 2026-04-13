import { useEffect, useState } from 'react';
import { getSubscriptions } from '../api/client';

const SERVICE_ICONS = {
  netflix:  '🎬',
  spotify:  '🎵',
  icloud:   '☁️',
  'apple tv': '📺',
  youtube:  '▶️',
  disney:   '🏰',
  amazon:   '📦',
  prime:    '📦',
  dropbox:  '📂',
  google:   '🔵',
  onedrive: '☁️',
  adobe:    '🎨',
  microsoft:'💻',
  gym:      '💪',
  dergi:    '📰',
  default:  '🔄',
};

function getIcon(description) {
  if (!description) return SERVICE_ICONS.default;
  const lower = description.toLowerCase();
  for (const [key, icon] of Object.entries(SERVICE_ICONS)) {
    if (key !== 'default' && lower.includes(key)) return icon;
  }
  return SERVICE_ICONS.default;
}

const USER_ID = 1;

export default function SubscriptionCard() {
  const [subscriptions, setSubscriptions] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getSubscriptions(USER_ID)
      .then((res) => setSubscriptions(res.data))
      .catch(() => setSubscriptions([]))
      .finally(() => setLoading(false));
  }, []);

  const monthlyTotal = subscriptions.reduce(
    (sum, s) => sum + parseFloat(s.amount || 0),
    0
  );

  const formatTRY = (amount) =>
    Number(amount).toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' });

  if (loading) {
    return (
      <div style={styles.card}>
        <p style={styles.loadingText}>Abonelikler yükleniyor...</p>
      </div>
    );
  }

  return (
    <div style={styles.card}>
      {/* Başlık */}
      <div style={styles.header}>
        <div style={styles.titleGroup}>
          <span style={styles.titleIcon}>🔄</span>
          <div>
            <p style={styles.label}>Aylık Abonelikler</p>
            <p style={styles.subLabel}>Otomatik yenilenen ödemeler</p>
          </div>
        </div>
        <div style={styles.totalBox}>
          <p style={styles.totalLabel}>Aylık Toplam</p>
          <p style={styles.totalAmount}>{formatTRY(monthlyTotal)}</p>
        </div>
      </div>

      {/* Rozet */}
      <div style={styles.badgeRow}>
        <span style={styles.badge}>{subscriptions.length} abonelik tespit edildi</span>
      </div>

      {/* Liste */}
      {subscriptions.length === 0 ? (
        <p style={styles.emptyText}>Abonelik bulunamadı.</p>
      ) : (
        <div style={styles.list}>
          {subscriptions.map((sub, i) => (
            <div key={i} style={styles.item}>
              <div style={styles.itemLeft}>
                <span style={styles.itemIcon}>{getIcon(sub.description)}</span>
                <div>
                  <p style={styles.itemName}>{sub.description}</p>
                  {sub.category && (
                    <p style={styles.itemCategory}>{sub.category}</p>
                  )}
                </div>
              </div>
              <p style={styles.itemAmount}>{formatTRY(sub.amount)}</p>
            </div>
          ))}
        </div>
      )}

      {/* Alt not */}
      {subscriptions.length > 0 && (
        <p style={styles.footerNote}>
          💡 Bu harcamalar ekstre yüklendiğinde AI tarafından otomatik tespit edildi.
        </p>
      )}
    </div>
  );
}

const styles = {
  card: {
    background: '#fff',
    borderRadius: '12px',
    padding: '24px 28px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
    borderLeft: '4px solid #8b5cf6',
  },
  loadingText: {
    margin: 0,
    color: '#9ca3af',
    fontSize: '14px',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: '12px',
  },
  titleGroup: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
  },
  titleIcon: {
    fontSize: '28px',
    lineHeight: 1,
  },
  label: {
    margin: '0 0 2px',
    fontSize: '15px',
    fontWeight: '700',
    color: '#111827',
  },
  subLabel: {
    margin: 0,
    fontSize: '12px',
    color: '#9ca3af',
  },
  totalBox: {
    textAlign: 'right',
  },
  totalLabel: {
    margin: '0 0 2px',
    fontSize: '11px',
    color: '#6b7280',
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
  },
  totalAmount: {
    margin: 0,
    fontSize: '22px',
    fontWeight: '700',
    color: '#8b5cf6',
    letterSpacing: '-0.5px',
  },
  badgeRow: {
    marginBottom: '16px',
  },
  badge: {
    display: 'inline-block',
    background: '#f3f0ff',
    color: '#7c3aed',
    fontSize: '11px',
    fontWeight: '600',
    padding: '3px 10px',
    borderRadius: '999px',
    letterSpacing: '0.03em',
  },
  emptyText: {
    margin: 0,
    color: '#9ca3af',
    fontSize: '14px',
    fontStyle: 'italic',
  },
  list: {
    display: 'flex',
    flexDirection: 'column',
    gap: '2px',
  },
  item: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '10px 14px',
    borderRadius: '8px',
    background: '#fafafa',
    transition: 'background 0.15s',
  },
  itemLeft: {
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
  },
  itemIcon: {
    fontSize: '20px',
    lineHeight: 1,
    minWidth: '24px',
    textAlign: 'center',
  },
  itemName: {
    margin: '0 0 2px',
    fontSize: '14px',
    fontWeight: '600',
    color: '#1f2937',
  },
  itemCategory: {
    margin: 0,
    fontSize: '11px',
    color: '#9ca3af',
  },
  itemAmount: {
    margin: 0,
    fontSize: '14px',
    fontWeight: '700',
    color: '#374151',
    whiteSpace: 'nowrap',
  },
  footerNote: {
    margin: '16px 0 0',
    fontSize: '12px',
    color: '#9ca3af',
    fontStyle: 'italic',
    borderTop: '1px solid #f3f4f6',
    paddingTop: '12px',
  },
};
