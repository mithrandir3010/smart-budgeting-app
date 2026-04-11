import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  PieChart,
  Pie,
  Cell,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { getAnalyticsSummary } from '../api/client';

const COLORS = [
  '#6366f1', '#ec4899', '#f59e0b', '#10b981',
  '#3b82f6', '#ef4444', '#8b5cf6', '#14b8a6',
];

const USER_ID = 1;

export default function DashboardPage() {
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    getAnalyticsSummary(USER_ID)
      .then((res) => setSummary(res.data))
      .catch(() => setError('Veriler yüklenirken bir hata oluştu.'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div style={styles.centered}>
        <p>Yükleniyor...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div style={styles.centered}>
        <p style={{ color: '#ef4444' }}>{error}</p>
      </div>
    );
  }

  const pieData = Object.entries(summary.categoryBreakdown || {}).map(
    ([name, value]) => ({ name, value: parseFloat(value) })
  );

  return (
    <div style={styles.page}>
      {/* Kırmızı uyarı barı */}
      {summary.warning && (
        <div style={styles.warningBar}>
          <span>⚠ {summary.warning}</span>
        </div>
      )}

      <div style={styles.header}>
        <h1 style={styles.title}>Harcama Özeti</h1>
        <Link to="/upload" style={styles.uploadBtn}>
          + Ekstre Yükle
        </Link>
      </div>

      {/* Toplam harcama kartı */}
      <div style={styles.card}>
        <p style={styles.cardLabel}>Toplam Harcama</p>
        <p style={styles.cardAmount}>
          {Number(summary.totalSpending).toLocaleString('tr-TR', {
            style: 'currency',
            currency: 'TRY',
          })}
        </p>
      </div>

      {/* Pasta grafik */}
      {pieData.length > 0 ? (
        <div style={styles.chartCard}>
          <h2 style={styles.chartTitle}>Kategori Dağılımı</h2>
          <ResponsiveContainer width="100%" height={340}>
            <PieChart>
              <Pie
                data={pieData}
                cx="50%"
                cy="50%"
                outerRadius={120}
                dataKey="value"
                label={({ name, percent }) =>
                  `${name} %${(percent * 100).toFixed(0)}`
                }
              >
                {pieData.map((_, index) => (
                  <Cell
                    key={index}
                    fill={COLORS[index % COLORS.length]}
                  />
                ))}
              </Pie>
              <Tooltip
                formatter={(value) =>
                  value.toLocaleString('tr-TR', {
                    style: 'currency',
                    currency: 'TRY',
                  })
                }
              />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <div style={styles.chartCard}>
          <p style={{ color: '#9ca3af' }}>Henüz kategori verisi yok.</p>
        </div>
      )}
    </div>
  );
}

const styles = {
  page: {
    minHeight: '100vh',
    background: '#f9fafb',
    fontFamily: 'system-ui, sans-serif',
  },
  centered: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    minHeight: '100vh',
  },
  warningBar: {
    background: '#ef4444',
    color: '#fff',
    padding: '12px 24px',
    fontSize: '15px',
    fontWeight: '500',
    textAlign: 'center',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '24px 32px 0',
  },
  title: {
    margin: 0,
    fontSize: '28px',
    fontWeight: '700',
    color: '#111827',
  },
  uploadBtn: {
    background: '#6366f1',
    color: '#fff',
    padding: '10px 20px',
    borderRadius: '8px',
    textDecoration: 'none',
    fontSize: '14px',
    fontWeight: '600',
  },
  card: {
    margin: '24px 32px 0',
    background: '#fff',
    borderRadius: '12px',
    padding: '28px 32px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
  },
  cardLabel: {
    margin: '0 0 8px',
    fontSize: '14px',
    color: '#6b7280',
    fontWeight: '500',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
  },
  cardAmount: {
    margin: 0,
    fontSize: '40px',
    fontWeight: '700',
    color: '#111827',
  },
  chartCard: {
    margin: '24px 32px',
    background: '#fff',
    borderRadius: '12px',
    padding: '28px 32px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
  },
  chartTitle: {
    margin: '0 0 16px',
    fontSize: '18px',
    fontWeight: '600',
    color: '#374151',
  },
};
