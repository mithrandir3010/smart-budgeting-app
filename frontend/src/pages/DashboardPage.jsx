import { useEffect, useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Upload, Sun, Moon } from 'lucide-react';
import {
  getAnalyticsSummary, getTransactions, getBudgetAlerts,
  updateMonthlyBudget, deleteAllStatements, getStoredUser,
} from '../api/client';
import { generateReport } from '../utils/pdfReport';
import { useTheme } from '../context/ThemeContext';

import AppLayout        from '../components/layout/AppLayout';
import BentoCards       from '../components/dashboard/BentoCards';
import DonutChart       from '../components/dashboard/DonutChart';
import SpendingTrendChart from '../components/dashboard/SpendingTrendChart';
import TransactionsTable  from '../components/TransactionsTable';
import SerenaInsightCard  from '../components/SerenaInsightCard';
import CoachCard          from '../components/CoachCard';
import SubscriptionCard   from '../components/SubscriptionCard';
import BudgetGuard        from '../components/BudgetGuard';
import BudgetLimitModal   from '../components/BudgetLimitModal';
import InstallmentCard    from '../components/InstallmentCard';

// ── Loading skeleton ──────────────────────────────────────────────────────────
function SkeletonPulse({ className }) {
  return (
    <div className={`rounded-2xl dark:bg-white/[0.05] bg-zinc-200 animate-pulse ${className}`} />
  );
}

function LoadingView() {
  return (
    <AppLayout>
      <div className="space-y-4">
        {/* Stat cards — 2 col on mobile, 4 on desktop */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <SkeletonPulse className="col-span-2 h-24 md:h-28" />
          <SkeletonPulse className="h-24 md:h-28" />
          <SkeletonPulse className="h-24 md:h-28" />
        </div>
        {/* Charts — stacked on mobile, side by side on desktop */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <SkeletonPulse className="h-52 md:h-64" />
          <SkeletonPulse className="h-52 md:h-64" />
        </div>
        {/* AI insight + coach cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <SkeletonPulse className="h-24" />
          <SkeletonPulse className="h-24" />
        </div>
        {/* Transactions list */}
        <SkeletonPulse className="h-48 md:h-64" />
        {/* Subscription + installment — collapsed on mobile so shorter */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <SkeletonPulse className="h-16 md:h-48" />
          <SkeletonPulse className="h-16 md:h-48" />
        </div>
      </div>
    </AppLayout>
  );
}

// ── Empty state ───────────────────────────────────────────────────────────────
function EmptyView() {
  return (
    <AppLayout>
      <div className="flex flex-col items-center justify-center min-h-[70vh] gap-6">
        <motion.div
          initial={{ scale: 0.8, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ duration: 0.5, ease: [0.23, 1, 0.32, 1] }}
          className="w-24 h-24 rounded-3xl dark:bg-white/[0.04] bg-zinc-100 flex items-center justify-center"
        >
          <Upload size={40} className="text-zinc-400" strokeWidth={1.5} />
        </motion.div>
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.15, duration: 0.4 }}
          className="text-center"
        >
          <p className="text-lg font-semibold text-zinc-300 dark:text-zinc-300 mb-1">
            Henüz ekstre yüklenmedi
          </p>
          <p className="text-sm text-zinc-500 mb-6">
            İlk ekstrenizi yükleyerek analizlere başlayın.
          </p>
          <Link
            to="/upload"
            className="inline-flex items-center gap-2 bg-emerald-500 hover:bg-emerald-400 active:scale-95 text-white font-semibold px-5 py-2.5 rounded-xl text-sm transition-all shadow-neon-green"
          >
            <Upload size={15} strokeWidth={2.5} />
            Ekstre Yükle
          </Link>
        </motion.div>
      </div>
    </AppLayout>
  );
}

// ── Main Dashboard ────────────────────────────────────────────────────────────
export default function DashboardPage() {
  const navigate  = useNavigate();
  const location  = useLocation();
  const { theme, toggleTheme } = useTheme();
  const currentUser = getStoredUser();

  const [summary,      setSummary]      = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [alerts,       setAlerts]       = useState([]);
  const [loading,      setLoading]      = useState(true);
  const [error,        setError]        = useState(null);
  const [showLimitModal, setShowLimitModal] = useState(false);
  const [pdfLoading,   setPdfLoading]   = useState(false);

  const handleDownloadPdf = async () => {
    if (!summary) return;
    setPdfLoading(true);
    try {
      await generateReport({ summary, transactions, user: currentUser });
    } finally {
      setPdfLoading(false);
    }
  };

  const fetchAlerts = async () => {
    try {
      const res = await getBudgetAlerts();
      setAlerts(res.data);
    } catch { /* non-critical */ }
  };

  const fetchSummary = async () => {
    try {
      const res = await getAnalyticsSummary();
      setSummary(res.data);
    } catch { /* non-critical */ }
  };

  const handleBudgetSet = async (amount) => {
    await updateMonthlyBudget(amount);
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
    } catch { /* toast handled by client */ }
    finally { setLoading(false); }
  };

  useEffect(() => {
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

  if (loading) return <LoadingView />;

  if (error) {
    return (
      <AppLayout>
        <div className="flex items-center justify-center min-h-[60vh]">
          <p className="text-rose-400">{error}</p>
        </div>
      </AppLayout>
    );
  }

  if (!summary) return <EmptyView />;

  const sidebarProps = {
    onLimitClick: () => setShowLimitModal(true),
    onDeleteAll:  handleDeleteAll,
    onDownloadPdf: handleDownloadPdf,
    pdfLoading,
  };

  return (
    <AppLayout sidebarProps={sidebarProps}>

      {/* Budget limit modal */}
      <AnimatePresence>
        {showLimitModal && (
          <BudgetLimitModal
            onClose={() => setShowLimitModal(false)}
            onSaved={fetchAlerts}
          />
        )}
      </AnimatePresence>

      {/* Top bar: greeting + theme toggle */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <motion.h1
            initial={{ opacity: 0, x: -10 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.35 }}
            className="text-xl font-bold text-zinc-900 dark:text-zinc-100 tracking-tight"
          >
            {currentUser
              ? <span>Merhaba, <span className="text-neon-green">{currentUser.fullName.split(' ')[0]}</span></span>
              : 'Dashboard'
            }
          </motion.h1>
          <p className="text-xs text-zinc-500 dark:text-zinc-500 mt-0.5">
            Harcama özetiniz hazır
          </p>
        </div>

        <button
          onClick={toggleTheme}
          className="p-2 rounded-xl dark:bg-white/[0.05] bg-zinc-100 hover:bg-zinc-200 dark:hover:bg-white/[0.09] text-zinc-400 transition-colors"
          title={theme === 'dark' ? 'Açık tema' : 'Koyu tema'}
        >
          {theme === 'dark' ? <Sun size={16} strokeWidth={2} /> : <Moon size={16} strokeWidth={2} />}
        </button>
      </div>

      {/* Warning banner */}
      {summary.warning && (
        <motion.div
          initial={{ opacity: 0, height: 0 }}
          animate={{ opacity: 1, height: 'auto' }}
          className="mb-4 rounded-xl bg-rose-500/10 border border-rose-500/20 px-4 py-3 text-sm text-rose-400 font-medium"
        >
          ⚠ {summary.warning}
        </motion.div>
      )}

      {/* Budget alerts */}
      {alerts.length > 0 && (
        <div className="mb-4">
          <BudgetGuard alerts={alerts} />
        </div>
      )}

      {/* ── Bento Grid stat cards ── */}
      <BentoCards summary={summary} transactions={transactions} />

      {/* ── Charts row ── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mt-4">
        <DonutChart categoryBreakdown={summary.categoryBreakdown} />
        <SpendingTrendChart transactions={transactions} />
      </div>

      {/* ── AI Insight + Coach ── */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.55, duration: 0.4 }}
        >
          <SerenaInsightCard summary={summary} />
        </motion.div>
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.62, duration: 0.4 }}
        >
          <CoachCard summary={summary} onBudgetSet={handleBudgetSet} />
        </motion.div>
      </div>

      {/* ── Subscription ── */}
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.7, duration: 0.4 }}
        className="mt-4"
      >
        <SubscriptionCard />
      </motion.div>

      {/* ── Transactions table ── */}
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.75, duration: 0.4 }}
        className="mt-4 glass-card p-5"
      >
        <p className="text-[11px] font-semibold text-zinc-400 dark:text-zinc-500 uppercase tracking-widest mb-4">
          Tüm İşlemler
        </p>
        <TransactionsTable transactions={transactions} />
      </motion.div>

      {/* ── Installments ── */}
      <div className="mt-4">
        <InstallmentCard transactions={transactions} />
      </div>

    </AppLayout>
  );
}
