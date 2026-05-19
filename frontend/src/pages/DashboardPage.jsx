import { useEffect, useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Upload, AlertTriangle } from 'lucide-react';
import {
  getAnalyticsSummary, getTransactions, getBudgetAlerts,
  updateMonthlyBudget, deleteAllStatements, getStoredUser, getPublicSettings,
  getStatements,
} from '../api/client';
import { Megaphone, X as XIcon } from 'lucide-react';
import { generateReport } from '../utils/pdfReport';

import AppLayout               from '../components/layout/AppLayout';
import BentoCards              from '../components/dashboard/BentoCards';
import DonutChart              from '../components/dashboard/DonutChart';
import SpendingTrendChart      from '../components/dashboard/SpendingTrendChart';
import StatementTabs           from '../components/dashboard/StatementTabs';
import StatementComparisonBar  from '../components/dashboard/StatementComparisonBar';
import TransactionsTable       from '../components/TransactionsTable';
import SerenaInsightCard       from '../components/SerenaInsightCard';
import CoachCard               from '../components/CoachCard';
import SubscriptionCard        from '../components/SubscriptionCard';
import BudgetGuard             from '../components/BudgetGuard';
import BudgetLimitModal        from '../components/BudgetLimitModal';
import InstallmentCard         from '../components/InstallmentCard';
import { fadeUp, GlassCard }   from '../components/shared';

// ── Helpers ───────────────────────────────────────────────────────────────────
const TODAY = new Date().toLocaleDateString('tr-TR', {
  weekday: 'long', day: 'numeric', month: 'long',
});

function SectionLabel({ children }) {
  return (
    <p className="text-[10px] font-semibold text-zinc-500 dark:text-zinc-600 uppercase tracking-widest mb-3 px-0.5">
      {children}
    </p>
  );
}

// ── Loading skeleton ──────────────────────────────────────────────────────────
function Skeleton({ className }) {
  return <div className={`rounded-2xl bg-white/[0.04] animate-pulse ${className}`} />;
}

function LoadingView() {
  return (
    <AppLayout>
      <div className="space-y-4">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <Skeleton className="col-span-2 h-28" />
          <Skeleton className="h-28" />
          <Skeleton className="h-28" />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Skeleton className="h-64" />
          <Skeleton className="h-64" />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
        <Skeleton className="h-64" />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Skeleton className="h-16 md:h-48" />
          <Skeleton className="h-16 md:h-48" />
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
          className="w-24 h-24 rounded-3xl bg-white/[0.04] border border-white/[0.07] flex items-center justify-center"
        >
          <Upload size={40} className="text-zinc-500" strokeWidth={1.5} />
        </motion.div>
        <motion.div {...fadeUp(0.15)} className="text-center">
          <p className="text-lg font-semibold text-zinc-700 dark:text-zinc-300 mb-1">Henüz ekstre yüklenmedi</p>
          <p className="text-sm text-zinc-600 mb-6">İlk ekstrenizi yükleyerek analizlere başlayın.</p>
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
  const location    = useLocation();
  const currentUser = getStoredUser();

  const [summary,             setSummary]             = useState(null);
  const [transactions,        setTransactions]        = useState([]);
  const [alerts,              setAlerts]              = useState([]);
  const [loading,             setLoading]             = useState(true);
  const [error,               setError]               = useState(null);
  const [showLimitModal,      setShowLimitModal]      = useState(false);
  const [pdfLoading,          setPdfLoading]          = useState(false);
  const [announcement,        setAnnouncement]        = useState('');
  const [annDismissed,        setAnnDismissed]        = useState(false);
  const [statements,          setStatements]          = useState([]);
  const [selectedStatementId, setSelectedStatementId] = useState(null);
  const [tabSummary,          setTabSummary]          = useState(null);
  const [tabLoading,          setTabLoading]          = useState(false);

  const handleDownloadPdf = async () => {
    if (!summary) return;
    setPdfLoading(true);
    try { await generateReport({ summary, transactions, user: currentUser }); }
    finally { setPdfLoading(false); }
  };

  const fetchAlerts = async () => {
    try { const res = await getBudgetAlerts(); setAlerts(res.data); }
    catch { /* non-critical */ }
  };

  const fetchSummary = async () => {
    try { const res = await getAnalyticsSummary(); setSummary(res.data); }
    catch { /* non-critical */ }
  };

  const handleSelectStatement = async (statementId) => {
    setSelectedStatementId(statementId);
    if (statementId === null) {
      setTabSummary(null);
      return;
    }
    setTabLoading(true);
    try {
      const res = await getAnalyticsSummary(statementId);
      setTabSummary(res.data);
    } catch { setTabSummary(null); }
    finally { setTabLoading(false); }
  };

  const handleBudgetSet = async (amount) => {
    await updateMonthlyBudget(amount);
    await Promise.all([fetchSummary(), fetchAlerts()]);
  };

  const handleDeleteAll = async () => {
    if (!window.confirm('Tüm işlemler ve ekstre kayıtları kalıcı olarak silinecek. Emin misiniz?')) return;
    try {
      await deleteAllStatements();
      setSummary(null); setTransactions([]); setAlerts([]);
      setStatements([]); setSelectedStatementId(null); setTabSummary(null); setLoading(true);
      const [summaryRes, txRes] = await Promise.all([getAnalyticsSummary(), getTransactions()]);
      setSummary(summaryRes.data); setTransactions(txRes.data);
    } catch { /* toast handled by client */ }
    finally { setLoading(false); }
  };

  useEffect(() => {
    setSummary(null); setTransactions([]); setAlerts([]); setError(null);
    setSelectedStatementId(null); setTabSummary(null); setLoading(true);
    Promise.all([getAnalyticsSummary(), getTransactions(), getStatements()])
      .then(([s, t, st]) => {
        setSummary(s.data);
        setTransactions(t.data);
        setStatements(st.data || []);
      })
      .catch(() => setError('Veriler yüklenirken bir hata oluştu.'))
      .finally(() => setLoading(false));
    fetchAlerts();
    getPublicSettings().then((r) => setAnnouncement(r.data.announcement || '')).catch(() => {});
  }, [location.key]); // eslint-disable-line react-hooks/exhaustive-deps

  if (loading) return <LoadingView />;

  if (error) {
    return (
      <AppLayout>
        <div className="flex items-center justify-center min-h-[60vh]">
          <div className="flex items-center gap-3 text-rose-400">
            <AlertTriangle size={18} />
            <p className="text-sm">{error}</p>
          </div>
        </div>
      </AppLayout>
    );
  }

  if (!summary) return <EmptyView />;

  return (
    <AppLayout sidebarProps={{
      onLimitClick:  () => setShowLimitModal(true),
      onDeleteAll:   handleDeleteAll,
      onDownloadPdf: handleDownloadPdf,
      pdfLoading,
    }}>

      {/* Announcement banner */}
      <AnimatePresence>
        {announcement && !annDismissed && (
          <motion.div
            initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }}
            className="mb-4 flex items-start gap-3 px-4 py-3 rounded-xl
              bg-indigo-50 dark:bg-indigo-500/10 border border-indigo-200 dark:border-indigo-500/20"
          >
            <Megaphone size={15} className="text-indigo-500 shrink-0 mt-0.5" />
            <p className="flex-1 text-sm text-indigo-700 dark:text-indigo-300">{announcement}</p>
            <button onClick={() => setAnnDismissed(true)} className="text-indigo-400 hover:text-indigo-600 dark:hover:text-indigo-200 transition-colors">
              <XIcon size={14} />
            </button>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {showLimitModal && (
          <BudgetLimitModal
            onClose={() => setShowLimitModal(false)}
            onSaved={fetchAlerts}
          />
        )}
      </AnimatePresence>

      {/* ── Greeting ── */}
      <motion.div {...fadeUp(0)} className="mb-7">
        <p className="text-[11px] text-zinc-600 font-medium mb-1 capitalize">{TODAY}</p>
        <h1 className="text-2xl font-bold text-zinc-900 dark:text-zinc-100 tracking-tight leading-tight">
          {currentUser
            ? <>Merhaba, <span className="text-neon-green">{currentUser.fullName.split(' ')[0]}</span> 👋</>
            : 'Dashboard'
          }
        </h1>
        <p className="text-sm text-zinc-600 mt-1">Harcama özetiniz hazır</p>
      </motion.div>

      {/* ── Alerts ── */}
      <AnimatePresence>
        {summary.warning && (
          <motion.div
            key="warning"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="mb-4 rounded-xl bg-rose-500/10 border border-rose-500/20 px-4 py-3 text-sm text-rose-400 font-medium"
          >
            ⚠ {summary.warning}
          </motion.div>
        )}
      </AnimatePresence>

      {alerts.length > 0 && (
        <motion.div {...fadeUp(0.05)} className="mb-4">
          <BudgetGuard alerts={alerts} />
        </motion.div>
      )}

      {/* ── Stat cards — her zaman genel toplam ── */}
      <motion.div {...fadeUp(0.1)}>
        <SectionLabel>Genel Bakış</SectionLabel>
        <BentoCards summary={summary} transactions={transactions} />
      </motion.div>

      {/* ── Ekstre tab seçici ── */}
      {statements.length >= 2 && (
        <motion.div {...fadeUp(0.18)} className="mt-5">
          <StatementTabs
            statements={statements}
            selectedId={selectedStatementId}
            onSelect={handleSelectStatement}
          />
        </motion.div>
      )}

      {/* ── Analitik — tab seçimine göre değişir ── */}
      <motion.div {...fadeUp(0.25)} className="mt-6">
        <SectionLabel>Analitik</SectionLabel>
        {tabLoading ? (
          <div className="h-64 rounded-2xl bg-white/[0.04] animate-pulse" />
        ) : selectedStatementId !== null ? (
          /* Tek ekstre modu: sadece donut, trend chart tek ay için anlamsız */
          <div className="grid grid-cols-1 gap-4">
            <DonutChart categoryBreakdown={tabSummary?.categoryBreakdown ?? {}} />
          </div>
        ) : (
          /* Genel Analiz modu */
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <DonutChart categoryBreakdown={summary.categoryBreakdown} />
              <SpendingTrendChart transactions={transactions} />
            </div>
            <StatementComparisonBar statements={statements} />
          </div>
        )}
      </motion.div>

      {/* ── AI — tab seçimine göre değişir ── */}
      <motion.div {...fadeUp(0.38)} className="mt-6">
        <SectionLabel>Yapay Zeka</SectionLabel>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <SerenaInsightCard summary={selectedStatementId !== null ? (tabSummary ?? summary) : summary} />
          <CoachCard summary={selectedStatementId !== null ? (tabSummary ?? summary) : summary} onBudgetSet={handleBudgetSet} />
        </div>
      </motion.div>

      {/* ── Subscriptions ── */}
      <motion.div {...fadeUp(0.48)} className="mt-6">
        <SectionLabel>Abonelikler</SectionLabel>
        <SubscriptionCard />
      </motion.div>

      {/* ── Transactions — ekstre seçiliyse filtrele ── */}
      <motion.div {...fadeUp(0.55)} className="mt-6">
        <SectionLabel>
          {selectedStatementId !== null ? 'Bu Ekstreye Ait İşlemler' : 'Tüm İşlemler'}
        </SectionLabel>
        <GlassCard>
          <TransactionsTable
            transactions={
              selectedStatementId !== null
                ? transactions.filter((t) => t.statementId === selectedStatementId)
                : transactions
            }
          />
        </GlassCard>
      </motion.div>

      {/* ── Installments ── */}
      <motion.div {...fadeUp(0.62)} className="mt-6 mb-4">
        <SectionLabel>Taksitler</SectionLabel>
        <InstallmentCard
          transactions={
            selectedStatementId !== null
              ? transactions.filter((t) => t.statementId === selectedStatementId)
              : transactions
          }
        />
      </motion.div>

    </AppLayout>
  );
}
