import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import { getAnalyticsSummary, getTransactions } from '../api/client';
import TransactionsTable from '../components/TransactionsTable';
import SerenaInsightCard from '../components/SerenaInsightCard';

const COLORS = [
  '#6366f1', '#ec4899', '#f59e0b', '#10b981',
  '#3b82f6', '#ef4444', '#8b5cf6', '#14b8a6',
];

const USER_ID = 1;

export default function DashboardPage() {
  const [summary, setSummary] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    Promise.all([
      getAnalyticsSummary(USER_ID),
      getTransactions(USER_ID),
    ])
      .then(([summaryRes, txRes]) => {
        setSummary(summaryRes.data);
        setTransactions(txRes.data);
      })
      .catch(() => setError('Veriler yüklenirken bir hata oluştu.'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return <div style={styles.centered}><p>Yükleniyor...</p></div>;
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
          ⚠ {summary.warning}
        </div>
      )}

      {/* Üst başlık */}
      <div style={styles.header}>
        <h1 style={styles.title}>Harcama Özeti</h1>
        <Link to="/upload" style={styles.uploadBtn}>+ Ekstre Yükle</Link>
      </div>

      {/* İki kolonlu üst alan: Toplam kart + Serena */}
      <div style={styles.topGrid}>
        <div style={styles.card}>
          <p style={styles.cardLabel}>Toplam Harcama</p>
          <p style={styles.cardAmount}>
            {Number(summary.totalSpending).toLocaleString('tr-TR', {
              style: 'currency',
              currency: 'TRY',
            })}
          </p>
          <p style={styles.cardSub}>
            {transactions.length} işlem &bull;{' '}
            {Object.keys(summary.categoryBreakdown || {}).length} kategori
          </p>
        </div>

        <SerenaInsightCard summary={summary} />
      </div>

      {/* Pasta grafik */}
      {pieData.length > 0 && (
        <div style={styles.section}>
          <h2 style={styles.sectionTitle}>Kategori Dağılımı</h2>
          <ResponsiveContainer width="100%" height={320}>
            <PieChart>
              <Pie
                data={pieData}
                cx="50%"
                cy="50%"
                outerRadius={110}
                dataKey="value"
                label={({ name, percent }) =>
                  `${name} %${(percent * 100).toFixed(0)}`
                }
              >
                {pieData.map((_, i) => (
                  <Cell key={i} fill={COLORS[i % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip
                formatter={(v) =>
                  Number(v).toLocaleString('tr-TR', {
                    style: 'currency',
                    currency: 'TRY',
                  })
                }
              />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* İşlemler tablosu */}
      <div style={styles.section}>
        <h2 style={styles.sectionTitle}>İşlemler</h2>
        <TransactionsTable transactions={transactions} />
      </div>
    </div>
  );
}

const styles = {
  page: {
    minHeight: '100vh',
    background: '#f9fafb',
    fontFamily: 'system-ui, sans-serif',
    paddingBottom: '48px',
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
    fontSize: '26px',
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
  topGrid: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: '20px',
    margin: '20px 32px 0',
  },
  card: {
    background: '#fff',
    borderRadius: '12px',
    padding: '28px 32px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
  },
  cardLabel: {
    margin: '0 0 6px',
    fontSize: '12px',
    color: '#6b7280',
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: '0.06em',
  },
  cardAmount: {
    margin: '0 0 8px',
    fontSize: '38px',
    fontWeight: '700',
    color: '#111827',
    letterSpacing: '-1px',
  },
  cardSub: {
    margin: 0,
    fontSize: '13px',
    color: '#9ca3af',
  },
  section: {
    margin: '24px 32px 0',
    background: '#fff',
    borderRadius: '12px',
    padding: '24px 28px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
  },
  sectionTitle: {
    margin: '0 0 16px',
    fontSize: '16px',
    fontWeight: '600',
    color: '#374151',
  },
};
