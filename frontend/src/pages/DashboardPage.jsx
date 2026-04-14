import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import { getAnalyticsSummary, getTransactions, clearAuth, getStoredUser } from '../api/client';
import TransactionsTable from '../components/TransactionsTable';
import SerenaInsightCard from '../components/SerenaInsightCard';
import CoachCard from '../components/CoachCard';
import SubscriptionCard from '../components/SubscriptionCard';
import { useTheme } from '../context/ThemeContext';
import { Sun, Moon, Upload, LogOut } from 'lucide-react';

const COLORS = [
  '#6366f1', '#f43f5e', '#10b981', '#f59e0b',
  '#3b82f6', '#8b5cf6', '#14b8a6', '#84cc16',
];

function CustomTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-xl px-3.5 py-2.5 shadow-lg text-sm">
      <p className="text-xs font-semibold text-zinc-400 dark:text-zinc-500 mb-0.5">
        {payload[0].name}
      </p>
      <p className="font-bold text-zinc-900 dark:text-zinc-100">
        {Number(payload[0].value).toLocaleString('tr-TR', {
          style: 'currency', currency: 'TRY',
        })}
      </p>
    </div>
  );
}

export default function DashboardPage() {
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  const currentUser = getStoredUser();
  const [summary, setSummary] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const handleLogout = () => {
    clearAuth();
    navigate('/login');
  };

  useEffect(() => {
    Promise.all([getAnalyticsSummary(), getTransactions()])
      .then(([summaryRes, txRes]) => {
        setSummary(summaryRes.data);
        setTransactions(txRes.data);
      })
      .catch(() => setError('Veriler yüklenirken bir hata oluştu.'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-zinc-50 dark:bg-zinc-950 gap-3">
        <div className="w-8 h-8 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
        <p className="text-sm text-zinc-400 dark:text-zinc-500">Yükleniyor...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-zinc-50 dark:bg-zinc-950">
        <p className="text-rose-500">{error}</p>
      </div>
    );
  }

  const pieData = Object.entries(summary.categoryBreakdown || {}).map(
    ([name, value]) => ({ name, value: parseFloat(value) })
  );

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 pb-16 transition-colors duration-200">

      {/* Warning banner */}
      {summary.warning && (
        <div className="bg-rose-500 text-white px-6 py-3 text-sm font-medium text-center">
          ⚠ {summary.warning}
        </div>
      )}

      {/* Top nav */}
      <header className="flex justify-between items-center px-5 md:px-8 py-4 border-b border-zinc-200 dark:border-zinc-800 bg-white/70 dark:bg-zinc-900/70 backdrop-blur-sm sticky top-0 z-10">
        <div className="flex items-center gap-2">
          <span className="text-xl">💰</span>
          <span className="font-bold text-zinc-900 dark:text-zinc-100 tracking-tight text-lg">
            Smart Budget
          </span>
        </div>

        {currentUser && (
          <p className="hidden md:block text-sm text-zinc-500 dark:text-zinc-400">
            Hoş geldin,{' '}
            <span className="font-semibold text-zinc-700 dark:text-zinc-300">
              {currentUser.fullName}
            </span>
          </p>
        )}

        <div className="flex items-center gap-2">
          <button
            onClick={toggleTheme}
            className="p-2 rounded-lg text-zinc-400 dark:text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
            title={theme === 'dark' ? 'Açık tema' : 'Koyu tema'}
          >
            {theme === 'dark'
              ? <Sun size={17} strokeWidth={2} />
              : <Moon size={17} strokeWidth={2} />
            }
          </button>

          <Link
            to="/upload"
            className="flex items-center gap-1.5 bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 text-white px-3.5 py-2 rounded-lg text-sm font-semibold transition-colors"
          >
            <Upload size={13} strokeWidth={2.5} />
            <span className="hidden sm:inline">Ekstre Yükle</span>
            <span className="sm:hidden">Yükle</span>
          </Link>

          <button
            onClick={handleLogout}
            className="flex items-center gap-1.5 text-zinc-500 dark:text-zinc-400 hover:text-zinc-800 dark:hover:text-zinc-100 border border-zinc-200 dark:border-zinc-700 hover:border-zinc-300 dark:hover:border-zinc-600 px-3 py-2 rounded-lg text-sm font-medium transition-colors"
          >
            <LogOut size={14} strokeWidth={2} />
            <span className="hidden sm:inline">Çıkış</span>
          </button>
        </div>
      </header>

      {/* Page title */}
      <div className="px-5 md:px-8 pt-7 pb-1">
        <h1 className="text-2xl font-bold text-zinc-900 dark:text-zinc-100 tracking-tight">
          Harcama Özeti
        </h1>
        {currentUser && (
          <p className="text-sm text-zinc-500 dark:text-zinc-400 mt-0.5 md:hidden">
            Hoş geldin, {currentUser.fullName}
          </p>
        )}
      </div>

      <main className="px-5 md:px-8 space-y-5 mt-5">

        {/* Top grid: Total spending + Serena */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          <div className="bg-white dark:bg-zinc-900 rounded-xl shadow-sm border border-zinc-100 dark:border-zinc-800 p-6">
            <p className="text-xs font-semibold text-zinc-400 dark:text-zinc-500 uppercase tracking-widest mb-2">
              Toplam Harcama
            </p>
            <p className="text-4xl font-bold text-zinc-900 dark:text-zinc-100 tracking-tight leading-none mb-3">
              {Number(summary.totalSpending).toLocaleString('tr-TR', {
                style: 'currency', currency: 'TRY',
              })}
            </p>
            <div className="flex items-center gap-2 text-xs text-zinc-400 dark:text-zinc-500">
              <span className="inline-flex items-center gap-1 bg-zinc-100 dark:bg-zinc-800 px-2 py-0.5 rounded-full">
                {transactions.length} işlem
              </span>
              <span className="inline-flex items-center gap-1 bg-zinc-100 dark:bg-zinc-800 px-2 py-0.5 rounded-full">
                {Object.keys(summary.categoryBreakdown || {}).length} kategori
              </span>
            </div>
          </div>

          <SerenaInsightCard summary={summary} />
        </div>

        {/* Coach card */}
        <CoachCard summary={summary} />

        {/* Subscription card */}
        <SubscriptionCard />

        {/* Pie chart */}
        {pieData.length > 0 && (
          <div className="bg-white dark:bg-zinc-900 rounded-xl shadow-sm border border-zinc-100 dark:border-zinc-800 p-6">
            <h2 className="text-sm font-semibold text-zinc-600 dark:text-zinc-400 uppercase tracking-widest mb-5">
              Kategori Dağılımı
            </h2>
            <ResponsiveContainer width="100%" height={320}>
              <PieChart>
                <Pie
                  data={pieData}
                  cx="50%"
                  cy="50%"
                  outerRadius={110}
                  dataKey="value"
                  strokeWidth={2}
                  stroke={theme === 'dark' ? '#18181b' : '#f9fafb'}
                  label={({ name, percent }) =>
                    `${name} %${(percent * 100).toFixed(0)}`
                  }
                  labelLine={{
                    stroke: theme === 'dark' ? '#52525b' : '#a1a1aa',
                    strokeWidth: 1,
                  }}
                >
                  {pieData.map((_, i) => (
                    <Cell
                      key={i}
                      fill={COLORS[i % COLORS.length]}
                      opacity={0.9}
                    />
                  ))}
                </Pie>
                <Tooltip content={<CustomTooltip />} />
                <Legend
                  iconType="circle"
                  iconSize={8}
                  formatter={(value) => (
                    <span style={{
                      color: theme === 'dark' ? '#a1a1aa' : '#71717a',
                      fontSize: '12px',
                    }}>
                      {value}
                    </span>
                  )}
                />
              </PieChart>
            </ResponsiveContainer>
          </div>
        )}

        {/* Transactions */}
        <div className="bg-white dark:bg-zinc-900 rounded-xl shadow-sm border border-zinc-100 dark:border-zinc-800 p-6">
          <h2 className="text-sm font-semibold text-zinc-600 dark:text-zinc-400 uppercase tracking-widest mb-4">
            İşlemler
          </h2>
          <TransactionsTable transactions={transactions} />
        </div>

      </main>
    </div>
  );
}
