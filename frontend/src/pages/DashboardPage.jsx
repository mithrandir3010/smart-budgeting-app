import { useEffect, useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import {
  PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import { getAnalyticsSummary, getTransactions, getBudgetAlerts, updateMonthlyBudget, deleteAllStatements, clearAuth, getStoredUser } from '../api/client';
import TransactionsTable from '../components/TransactionsTable';
import SerenaInsightCard from '../components/SerenaInsightCard';
import CoachCard from '../components/CoachCard';
import SubscriptionCard from '../components/SubscriptionCard';
import BudgetGuard from '../components/BudgetGuard';
import BudgetLimitModal from '../components/BudgetLimitModal';
import InstallmentCard from '../components/InstallmentCard';
import { useTheme } from '../context/ThemeContext';
import { Sun, Moon, Upload, LogOut, ShieldAlert, Trash2, FileDown } from 'lucide-react';
import { generateReport } from '../utils/pdfReport';

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
  const navigate  = useNavigate();
  const location  = useLocation();
  const { theme, toggleTheme } = useTheme();
  const currentUser = getStoredUser();
  const [summary, setSummary] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showLimitModal, setShowLimitModal] = useState(false);
  const [pdfLoading, setPdfLoading] = useState(false);

  const handleDownloadPdf = async () => {
    if (!summary) return;
    setPdfLoading(true);
    try {
      await generateReport({ summary, transactions, user: currentUser });
    } finally {
      setPdfLoading(false);
    }
  };

  const handleLogout = () => {
    clearAuth();
    navigate('/login');
  };

  const fetchAlerts = async () => {
    try {
      const res = await getBudgetAlerts();
      setAlerts(res.data);
    } catch {
      // Alerts are non-critical; silently ignore failures
    }
  };

  const fetchSummary = async () => {
    try {
      const res = await getAnalyticsSummary();
      setSummary(res.data);
    } catch {
      // Summary reload errors are non-critical (data still visible)
    }
  };

  const handleBudgetSet = async (amount) => {
    await updateMonthlyBudget(amount);
    // Refresh both summary (monthlyBudget field) and alerts (threshold recalculation)
    await Promise.all([fetchSummary(), fetchAlerts()]);
  };

  const handleDeleteAll = async () => {
    if (!window.confirm('Tüm işlemler ve ekstre kayıtları kalıcı olarak silinecek. Emin misiniz?')) return;
    try {
      await deleteAllStatements();
      setSummary(null);
      setTransactions([]);
      setAlerts([]);
      setLoading(true);
      const [summaryRes, txRes] = await Promise.all([getAnalyticsSummary(), getTransactions()]);
      setSummary(summaryRes.data);
      setTransactions(txRes.data);
    } catch {
      // error toast handled by client interceptor
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // location.key her navigasyonda değişir (React Router v6).
    // Bu sayede yeni ekstre yüklenip Dashboard'a dönüldüğünde
    // eski state temizlenir ve taze veri çekilir.
    setSummary(null);
    setTransactions([]);
    setAlerts([]);
    setError(null);
    setLoading(true);

    Promise.all([getAnalyticsSummary(), getTransactions()])
      .then(([summaryRes, txRes]) => {
        setSummary(summaryRes.data);
        setTransactions(txRes.data);
      })
      .catch(() => setError('Veriler yüklenirken bir hata oluştu.'))
      .finally(() => setLoading(false));

    fetchAlerts();
  }, [location.key]); // eslint-disable-line react-hooks/exhaustive-deps

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

  if (!summary) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-zinc-50 dark:bg-zinc-950 gap-4">
        <p className="text-zinc-500 dark:text-zinc-400 text-sm">Henüz ekstre yüklenmedi.</p>
        <Link
          to="/upload"
          className="flex items-center gap-1.5 bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 text-white px-4 py-2 rounded-lg text-sm font-semibold transition-colors"
        >
          <Upload size={13} strokeWidth={2.5} />
          Ekstre Yükle
        </Link>
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

          <button
            onClick={() => setShowLimitModal(true)}
            className="flex items-center gap-1.5 text-zinc-500 dark:text-zinc-400 hover:text-zinc-800 dark:hover:text-zinc-100 border border-zinc-200 dark:border-zinc-700 hover:border-zinc-300 dark:hover:border-zinc-600 px-3 py-2 rounded-lg text-sm font-medium transition-colors"
            title="Bütçe limitleri"
          >
            <ShieldAlert size={14} strokeWidth={2} />
            <span className="hidden sm:inline">Limitler</span>
          </button>

          <button
            onClick={handleDownloadPdf}
            disabled={pdfLoading || !summary}
            className="flex items-center gap-1.5 text-indigo-600 dark:text-indigo-400 hover:text-indigo-800 dark:hover:text-indigo-200 border border-indigo-200 dark:border-indigo-800 hover:border-indigo-400 dark:hover:border-indigo-600 px-3 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            title="PDF Rapor İndir"
          >
            {pdfLoading
              ? <div className="w-3.5 h-3.5 border-2 border-indigo-400 border-t-transparent rounded-full animate-spin" />
              : <FileDown size={14} strokeWidth={2} />
            }
            <span className="hidden sm:inline">{pdfLoading ? 'Hazırlanıyor...' : 'PDF İndir'}</span>
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
            onClick={handleDeleteAll}
            className="flex items-center gap-1.5 text-rose-500 dark:text-rose-400 hover:text-rose-700 dark:hover:text-rose-300 border border-rose-200 dark:border-rose-800 hover:border-rose-300 dark:hover:border-rose-700 px-3 py-2 rounded-lg text-sm font-medium transition-colors"
            title="Tüm verileri sil"
          >
            <Trash2 size={14} strokeWidth={2} />
            <span className="hidden sm:inline">Sıfırla</span>
          </button>

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

      {/* Budget limit modal */}
      {showLimitModal && (
        <BudgetLimitModal
          onClose={() => setShowLimitModal(false)}
          onSaved={fetchAlerts}
        />
      )}

      <main className="px-5 md:px-8 space-y-5 mt-5">

        {/* Budget Guard — alerts */}
        {alerts.length > 0 && <BudgetGuard alerts={alerts} />}

        {/* Top grid: Total spending + Insight */}
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
        <CoachCard summary={summary} onBudgetSet={handleBudgetSet} />

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

        {/* Installment card — TransactionsTable'ın hemen altında */}
        <InstallmentCard transactions={transactions} />

      </main>
    </div>
  );
}
