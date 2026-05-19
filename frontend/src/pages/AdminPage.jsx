import { useState, useEffect, useCallback, useRef } from 'react';
import { Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  LayoutDashboard, Users, Activity, ArrowLeft, Shield,
  Search, X, ChevronLeft, ChevronRight, LogIn, LogOut,
  AlertTriangle, Lock, KeyRound, User, FileUp, Trash2,
  Ban, TrendingUp, TrendingDown, Minus, Eye, CheckCircle,
  FileWarning, Settings, BarChart3, GitCommitHorizontal,
  Save, ToggleLeft, ToggleRight, Megaphone, Wrench, AlertCircle,
} from 'lucide-react';
import {
  getAdminStats, getAdminUsers, getAdminUserStatements,
  toggleAdminUserStatus, getAdminAudit, getAdminGrowth,
  getAdminBankStats, getAdminFunnel, getAdminSilentFailures,
  getAdminFailedStatements, getAdminSettings, updateAdminSettings,
  bulkToggleUserStatus,
} from '../api/client';
import { toast } from 'sonner';

// ── Utilities ──────────────────────────────────────────────────────────────────

function formatDate(iso) {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('tr-TR', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  }).format(new Date(iso));
}

function relativeTime(iso) {
  if (!iso) return '—';
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1)  return 'az önce';
  if (m < 60) return `${m}dk önce`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}s önce`;
  return `${Math.floor(h / 24)}g önce`;
}

function initials(name) {
  if (!name) return '?';
  return name.split(' ').filter(Boolean).map((n) => n[0]).join('').toUpperCase().slice(0, 2);
}

function fillGrowthData(data) {
  const map = new Map((data || []).map((d) => [d.date, d.count]));
  return Array.from({ length: 30 }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - (29 - i));
    const key = d.toISOString().slice(0, 10);
    return { date: key, count: map.get(key) || 0 };
  });
}

const BANK_LABELS = {
  HALKBANK:   { label: 'Halkbank',       color: '#10b981' },
  ISBANK:     { label: 'İş Bankası',     color: '#6366f1' },
  YAPIKREDI:  { label: 'Yapı Kredi',     color: '#f59e0b' },
  GARANTI:    { label: 'Garanti BBVA',   color: '#3b82f6' },
  AKBANK:     { label: 'Akbank',         color: '#ef4444' },
  ZIRAAT:     { label: 'Ziraat Bankası', color: '#22c55e' },
  DENIZBANK:  { label: 'Denizbank',      color: '#06b6d4' },
  VAKIFBANK:  { label: 'Vakıfbank',      color: '#a855f7' },
  FINANSBANK: { label: 'QNB Finansbank', color: '#f97316' },
  TEB:        { label: 'TEB',            color: '#14b8a6' },
  UNKNOWN:    { label: 'Bilinmiyor',     color: '#71717a' },
};

// ── Audit config ───────────────────────────────────────────────────────────────

const AUDIT_CFG = {
  LOGIN_SUCCESS:            { label: 'Giriş Başarılı',       icon: LogIn,         bg: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400' },
  LOGIN_FAILURE:            { label: 'Giriş Başarısız',      icon: AlertTriangle,  bg: 'bg-rose-500/10 text-rose-500 dark:text-rose-400' },
  ACCOUNT_LOCKED:           { label: 'Hesap Kilitlendi',     icon: Lock,           bg: 'bg-red-500/10 text-red-600 dark:text-red-400' },
  PASSWORD_CHANGED:         { label: 'Şifre Değiştirildi',   icon: KeyRound,       bg: 'bg-amber-500/10 text-amber-600 dark:text-amber-400' },
  PROFILE_UPDATED:          { label: 'Profil Güncellendi',   icon: User,           bg: 'bg-blue-500/10 text-blue-600 dark:text-blue-400' },
  STATEMENT_UPLOADED:       { label: 'Ekstre Yüklendi',      icon: FileUp,         bg: 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400' },
  STATEMENT_DELETED:        { label: 'Ekstre Silindi',        icon: Trash2,         bg: 'bg-rose-500/10 text-rose-500 dark:text-rose-400' },
  DISPOSABLE_EMAIL_BLOCKED: { label: 'Geçici E-posta Engel', icon: Ban,            bg: 'bg-orange-500/10 text-orange-600 dark:text-orange-400' },
};

const STATUS_CFG = {
  PROCESSED: { label: 'Başarılı',   cls: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400' },
  PENDING:   { label: 'Bekliyor',   cls: 'bg-amber-500/10 text-amber-600 dark:text-amber-400' },
  FAILED:    { label: 'Başarısız',  cls: 'bg-rose-500/10 text-rose-500 dark:text-rose-400' },
};

const SUPPORTED_BANKS = ['HALKBANK', 'ISBANK', 'YAPIKREDI'];

const panelAnim = {
  initial: { opacity: 0, y: 10 },
  animate: { opacity: 1, y: 0 },
  exit:    { opacity: 0, y: -8 },
  transition: { duration: 0.25, ease: [0.23, 1, 0.32, 1] },
};

// ── Sidebar ────────────────────────────────────────────────────────────────────

const VIEWS = [
  { id: 'overview', label: 'Genel Bakış',     icon: LayoutDashboard },
  { id: 'users',    label: 'Kullanıcılar',     icon: Users           },
  { id: 'parser',   label: 'Parser Logları',   icon: FileWarning     },
  { id: 'audit',    label: 'Denetim Günlüğü', icon: Activity        },
  { id: 'settings', label: 'Ayarlar',          icon: Settings        },
];

function AdminSidebar({ view, setView }) {
  return (
    <aside className="hidden lg:flex flex-col fixed left-0 top-0 h-full w-[220px] z-30
      bg-white/90 dark:bg-zinc-950/90 backdrop-blur-xl
      border-r border-zinc-200/60 dark:border-white/[0.06] select-none">

      <div className="px-4 pt-6 pb-5">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-xl bg-indigo-500/15 flex items-center justify-center">
            <Shield size={15} className="text-indigo-500 dark:text-indigo-400" strokeWidth={2} />
          </div>
          <div>
            <span className="text-sm font-bold text-zinc-900 dark:text-zinc-100 tracking-tight">Admin</span>
            <span className="block text-[10px] text-zinc-500 font-medium tracking-widest uppercase mt-px">Kontrol Paneli</span>
          </div>
        </div>
      </div>

      <div className="mx-4 mb-4 h-px bg-zinc-200 dark:bg-white/[0.06]" />

      <nav className="flex-1 px-2 space-y-1">
        {VIEWS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => setView(id)}
            className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all ${
              view === id
                ? 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 shadow-[inset_0_0_0_1px_rgba(99,102,241,0.2)]'
                : 'text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100 hover:bg-zinc-100 dark:hover:bg-white/[0.05]'
            }`}
          >
            <Icon size={16} strokeWidth={1.8} className="flex-shrink-0" />
            {label}
          </button>
        ))}
      </nav>

      <div className="px-4 pb-6 pt-4 border-t border-zinc-200 dark:border-white/[0.06]">
        <Link
          to="/dashboard"
          className="flex items-center gap-2 text-sm text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-200 transition-colors"
        >
          <ArrowLeft size={14} strokeWidth={2} />
          Dashboard'a Dön
        </Link>
      </div>
    </aside>
  );
}

// ── KPI Card ───────────────────────────────────────────────────────────────────

function KpiCard({ label, value, sub, icon: Icon, trend }) {
  const trendIcon = trend > 0
    ? <TrendingUp size={12} className="text-emerald-500" />
    : trend < 0
    ? <TrendingDown size={12} className="text-rose-500" />
    : <Minus size={12} className="text-zinc-400" />;

  return (
    <div className="glass-card p-5">
      <div className="flex items-start justify-between mb-3">
        <div className="w-9 h-9 rounded-xl bg-indigo-500/10 dark:bg-indigo-500/15 flex items-center justify-center">
          <Icon size={16} strokeWidth={1.8} className="text-indigo-500 dark:text-indigo-400" />
        </div>
        {trend !== undefined && (
          <span className="flex items-center gap-1 text-[11px] text-zinc-500">
            {trendIcon}
            {Math.abs(trend)} bu hafta
          </span>
        )}
      </div>
      <p className="text-2xl font-bold text-zinc-900 dark:text-zinc-100 tracking-tight">{value}</p>
      <p className="text-xs text-zinc-500 mt-1">{label}</p>
      {sub && <p className="text-[11px] text-zinc-400 dark:text-zinc-600 mt-0.5">{sub}</p>}
    </div>
  );
}

// ── Growth Chart ───────────────────────────────────────────────────────────────

function GrowthChart({ data }) {
  const filled = fillGrowthData(data);
  const max    = Math.max(...filled.map((d) => d.count), 1);
  return (
    <div className="glass-card p-5">
      <p className="text-xs font-semibold text-zinc-500 uppercase tracking-widest mb-4">
        Kullanıcı Kazanımı — Son 30 Gün
      </p>
      <div className="flex items-end gap-[3px] h-28">
        {filled.map(({ date, count }) => (
          <div key={date} className="flex-1 relative group" style={{ height: '100%', display: 'flex', alignItems: 'flex-end' }}>
            <div
              className="w-full rounded-t-sm transition-all bg-indigo-500/25 dark:bg-indigo-400/30 group-hover:bg-indigo-500/50 dark:group-hover:bg-indigo-400/55"
              style={{ height: `${Math.max((count / max) * 100, count > 0 ? 4 : 1)}%` }}
              title={`${date}: ${count} yeni kullanıcı`}
            />
          </div>
        ))}
      </div>
      <div className="flex justify-between mt-1.5 text-[10px] text-zinc-400 dark:text-zinc-600">
        <span>{filled[0]?.date?.slice(5)}</span>
        <span>{filled[14]?.date?.slice(5)}</span>
        <span>{filled[29]?.date?.slice(5)}</span>
      </div>
    </div>
  );
}

// ── Bank Distribution Chart ────────────────────────────────────────────────────

function BankChart({ data }) {
  if (!data || data.length === 0) return null;
  return (
    <div className="glass-card p-5">
      <div className="flex items-center gap-2 mb-4">
        <BarChart3 size={14} className="text-indigo-400" />
        <p className="text-xs font-semibold text-zinc-500 uppercase tracking-widest">Banka Bazlı Dağılım</p>
      </div>
      <div className="space-y-3">
        {data.map((item) => {
          const cfg = BANK_LABELS[item.bankName] || { label: item.bankName, color: '#71717a' };
          const supported = SUPPORTED_BANKS.includes(item.bankName);
          return (
            <div key={item.bankName}>
              <div className="flex items-center justify-between mb-1">
                <div className="flex items-center gap-1.5">
                  <span className="text-xs font-medium text-zinc-700 dark:text-zinc-300">{cfg.label}</span>
                  {!supported && item.bankName !== 'UNKNOWN' && (
                    <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-amber-500/10 text-amber-600 dark:text-amber-400 font-medium">parser yok</span>
                  )}
                  {item.bankName === 'UNKNOWN' && (
                    <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-zinc-500/10 text-zinc-500 font-medium">tanımlanamadı</span>
                  )}
                </div>
                <span className="text-xs text-zinc-500">{item.count} ekstre · %{item.percentage}</span>
              </div>
              <div className="h-2 w-full rounded-full bg-zinc-100 dark:bg-white/[0.06]">
                <div
                  className="h-2 rounded-full transition-all"
                  style={{ width: `${item.percentage}%`, background: cfg.color }}
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ── Conversion Funnel ──────────────────────────────────────────────────────────

function FunnelCard({ data }) {
  if (!data) return null;
  const steps = [
    { label: 'Kayıt Oldu',           value: data.registered,  color: 'bg-indigo-500' },
    { label: 'E-posta Doğruladı',    value: data.emailVerified, color: 'bg-violet-500' },
    { label: 'İlk Ekstreyi Yükledi', value: data.firstUpload,  color: 'bg-emerald-500' },
  ];
  const max = data.registered || 1;

  return (
    <div className="glass-card p-5">
      <div className="flex items-center gap-2 mb-4">
        <GitCommitHorizontal size={14} className="text-indigo-400" />
        <p className="text-xs font-semibold text-zinc-500 uppercase tracking-widest">Dönüşüm Hunisi</p>
      </div>
      <div className="space-y-2.5">
        {steps.map((step, i) => {
          const pct = Math.round((step.value / max) * 100);
          return (
            <div key={step.label}>
              <div className="flex items-center justify-between mb-1">
                <span className="text-xs text-zinc-600 dark:text-zinc-400">{step.label}</span>
                <span className="text-xs font-semibold text-zinc-800 dark:text-zinc-200">{step.value} <span className="text-zinc-400 font-normal">(%{pct})</span></span>
              </div>
              <div className="h-2.5 w-full rounded-full bg-zinc-100 dark:bg-white/[0.06]">
                <div
                  className={`h-2.5 rounded-full transition-all ${step.color}`}
                  style={{ width: `${pct}%`, opacity: 1 - i * 0.1 }}
                />
              </div>
            </div>
          );
        })}
      </div>
      {data.registered > 0 && (
        <p className="text-[11px] text-zinc-400 mt-3">
          Kayıttan ekstreye dönüşüm: <span className="font-semibold text-zinc-600 dark:text-zinc-300">%{Math.round((data.firstUpload / data.registered) * 100)}</span>
        </p>
      )}
    </div>
  );
}

// ── User Detail Modal ──────────────────────────────────────────────────────────

function UserDetailModal({ user, statements, stmtLoading, onClose, onToggleStatus, toggling }) {
  if (!user) return null;
  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
        className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        onClick={onClose}
      >
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 12 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95 }}
          transition={{ duration: 0.25, ease: [0.23, 1, 0.32, 1] }}
          className="glass-card w-full max-w-2xl max-h-[85vh] overflow-y-auto"
          onClick={(e) => e.stopPropagation()}
        >
          <div className="flex items-center justify-between p-5 border-b border-zinc-200 dark:border-white/[0.06]">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl flex items-center justify-center text-sm font-bold text-white"
                style={{ background: 'linear-gradient(135deg, #6366f1, #8b5cf6)' }}>
                {initials(user.fullName)}
              </div>
              <div>
                <p className="font-semibold text-zinc-900 dark:text-zinc-100">{user.fullName}</p>
                <p className="text-xs text-zinc-500">@{user.username}</p>
              </div>
            </div>
            <button onClick={onClose}
              className="w-8 h-8 flex items-center justify-center rounded-lg text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100 hover:bg-zinc-100 dark:hover:bg-white/[0.07] transition-colors">
              <X size={15} />
            </button>
          </div>

          <div className="p-5 space-y-5">
            <div className="grid grid-cols-2 gap-3 text-sm">
              {[
                ['ID',            `#${user.id}`],
                ['E-posta',       user.email],
                ['Rol',           user.role],
                ['Durum',         user.active ? 'Aktif' : 'Pasif'],
                ['Giriş Sayısı',  user.loginCount],
                ['Son Giriş',     formatDate(user.lastLoginAt)],
                ['Kayıt Tarihi',  formatDate(user.createdAt)],
                ['E-posta Doğr.', user.emailVerified ? 'Evet' : 'Hayır'],
              ].map(([label, val]) => (
                <div key={label} className="bg-zinc-50 dark:bg-white/[0.03] rounded-xl px-3 py-2.5">
                  <p className="text-[10px] font-semibold text-zinc-500 uppercase tracking-wider">{label}</p>
                  <p className="text-zinc-800 dark:text-zinc-200 font-medium mt-0.5 text-sm break-all">{val ?? '—'}</p>
                </div>
              ))}
            </div>

            <div>
              <p className="text-xs font-semibold text-zinc-500 uppercase tracking-widest mb-3">
                Ekstre Geçmişi ({statements.length})
              </p>
              {stmtLoading ? (
                <div className="flex justify-center py-6">
                  <div className="w-5 h-5 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
                </div>
              ) : statements.length === 0 ? (
                <p className="text-sm text-zinc-400 text-center py-4">Henüz ekstre yok.</p>
              ) : (
                <div className="space-y-2">
                  {statements.map((s) => {
                    const st = STATUS_CFG[s.status] || { label: s.status, cls: 'bg-zinc-500/10 text-zinc-500' };
                    return (
                      <div key={s.id} className="flex items-center justify-between px-3 py-2.5 rounded-xl bg-zinc-50 dark:bg-white/[0.03] text-sm">
                        <div className="min-w-0">
                          <p className="font-medium text-zinc-800 dark:text-zinc-200 truncate max-w-[280px]">{s.fileName}</p>
                          <p className="text-[11px] text-zinc-400 mt-0.5">{s.bankName || '—'} · {s.uploadDate}</p>
                        </div>
                        <span className={`shrink-0 text-[10px] font-semibold px-2 py-1 rounded-lg ${st.cls}`}>{st.label}</span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {user.role !== 'ROLE_ADMIN' && (
              <motion.button
                whileHover={{ scale: 1.01 }} whileTap={{ scale: 0.98 }}
                onClick={() => onToggleStatus(user.id, !user.active)}
                disabled={toggling}
                className={`w-full flex items-center justify-center gap-2 py-2.5 rounded-xl text-sm font-semibold transition-all disabled:opacity-50
                  ${user.active
                    ? 'bg-rose-50 dark:bg-rose-500/10 border border-rose-200 dark:border-rose-500/20 text-rose-600 dark:text-rose-400'
                    : 'bg-emerald-50 dark:bg-emerald-500/10 border border-emerald-200 dark:border-emerald-500/20 text-emerald-700 dark:text-emerald-400'
                  }`}
              >
                {toggling
                  ? <div className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />
                  : user.active ? <Ban size={14} /> : <CheckCircle size={14} />
                }
                {user.active ? 'Kullanıcıyı Pasife Al' : 'Kullanıcıyı Aktifleştir'}
              </motion.button>
            )}
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
}

// ── Overview View ──────────────────────────────────────────────────────────────

function OverviewView() {
  const [stats,     setStats]     = useState(null);
  const [growth,    setGrowth]    = useState([]);
  const [bankStats, setBankStats] = useState([]);
  const [funnel,    setFunnel]    = useState(null);
  const [loading,   setLoading]   = useState(true);

  useEffect(() => {
    Promise.all([
      getAdminStats(),
      getAdminGrowth(),
      getAdminBankStats(),
      getAdminFunnel(),
    ])
      .then(([s, g, b, f]) => {
        setStats(s.data);
        setGrowth(g.data);
        setBankStats(b.data);
        setFunnel(f.data);
      })
      .catch(() => toast.error('İstatistikler yüklenemedi.'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return (
    <div className="flex justify-center items-center h-64">
      <div className="w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
    </div>
  );

  return (
    <motion.div {...panelAnim}>
      <div className="mb-6">
        <h1 className="text-xl font-bold text-zinc-900 dark:text-zinc-100">Genel Bakış</h1>
        <p className="text-sm text-zinc-500 mt-0.5">Platform metrikleri ve büyüme özeti</p>
      </div>

      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4 mb-6">
        <KpiCard label="Toplam Kullanıcı"   value={stats?.totalUsers ?? 0}          icon={Users}        trend={stats?.newUsersThisWeek} sub={`Geçen hafta: ${stats?.newUsersLastWeek ?? 0}`} />
        <KpiCard label="Aktif Kullanıcı (30g)" value={stats?.activeUsersLast30Days ?? 0} icon={Activity} />
        <KpiCard label="Toplam Ekstre"      value={stats?.totalStatements ?? 0}     icon={FileUp}       />
        <KpiCard label="Başarı Oranı"       value={`%${stats?.successRate ?? 100}`} icon={CheckCircle}  sub="Başarıyla işlenen ekstre" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
        <GrowthChart data={growth} />
        <FunnelCard  data={funnel} />
      </div>

      {bankStats.length > 0 && (
        <BankChart data={bankStats} />
      )}
    </motion.div>
  );
}

// ── Users View ─────────────────────────────────────────────────────────────────

function UsersView() {
  const [users,       setUsers]       = useState([]);
  const [page,        setPage]        = useState(0);
  const [totalPages,  setTotalPages]  = useState(0);
  const [search,      setSearch]      = useState('');
  const [query,       setQuery]       = useState('');
  const [loading,     setLoading]     = useState(true);
  const [selected,    setSelected]    = useState(null);
  const [statements,  setStatements]  = useState([]);
  const [stmtLoading, setStmtLoading] = useState(false);
  const [toggling,    setToggling]    = useState(false);
  const [checked,     setChecked]     = useState(new Set());
  const [bulkLoading, setBulkLoading] = useState(false);

  const fetchUsers = useCallback((p, q) => {
    setLoading(true);
    setChecked(new Set());
    getAdminUsers(p, 20, q)
      .then((r) => { setUsers(r.data.content); setTotalPages(r.data.totalPages); })
      .catch(() => toast.error('Kullanıcılar yüklenemedi.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { fetchUsers(page, query); }, [page, query, fetchUsers]);

  useEffect(() => {
    const t = setTimeout(() => { setPage(0); setQuery(search); }, 400);
    return () => clearTimeout(t);
  }, [search]);

  const openModal = async (user) => {
    setSelected(user);
    setStatements([]);
    setStmtLoading(true);
    try {
      const r = await getAdminUserStatements(user.id);
      setStatements(r.data);
    } catch { toast.error('Ekstreler yüklenemedi.'); }
    finally  { setStmtLoading(false); }
  };

  const handleToggle = async (id, active) => {
    setToggling(true);
    try {
      await toggleAdminUserStatus(id, active);
      setUsers((prev) => prev.map((u) => u.id === id ? { ...u, active } : u));
      if (selected?.id === id) setSelected((s) => ({ ...s, active }));
      toast.success(active ? 'Kullanıcı aktifleştirildi.' : 'Kullanıcı pasife alındı.');
    } catch { toast.error('Durum güncellenemedi.'); }
    finally  { setToggling(false); }
  };

  const toggleCheck = (id) => setChecked((prev) => {
    const next = new Set(prev);
    next.has(id) ? next.delete(id) : next.add(id);
    return next;
  });

  const toggleAll = () => {
    const eligible = users.filter((u) => u.role !== 'ROLE_ADMIN').map((u) => u.id);
    setChecked((prev) => prev.size === eligible.length ? new Set() : new Set(eligible));
  };

  const handleBulk = async (active) => {
    setBulkLoading(true);
    try {
      await bulkToggleUserStatus([...checked], active);
      setUsers((prev) => prev.map((u) => checked.has(u.id) ? { ...u, active } : u));
      setChecked(new Set());
      toast.success(`${checked.size} kullanıcı ${active ? 'aktifleştirildi' : 'pasife alındı'}.`);
    } catch { toast.error('Toplu işlem başarısız.'); }
    finally  { setBulkLoading(false); }
  };

  const eligible = users.filter((u) => u.role !== 'ROLE_ADMIN');

  return (
    <motion.div {...panelAnim}>
      <div className="flex items-center justify-between mb-6 flex-wrap gap-3">
        <div>
          <h1 className="text-xl font-bold text-zinc-900 dark:text-zinc-100">Kullanıcılar</h1>
          <p className="text-sm text-zinc-500 mt-0.5">Kayıtlı hesapları yönet</p>
        </div>
        <div className="relative w-64">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="İsim, e-posta, kullanıcı adı..."
            className="w-full pl-9 pr-8 py-2 rounded-xl text-sm
              bg-white dark:bg-white/[0.04] border border-zinc-200 dark:border-white/[0.08]
              text-zinc-800 dark:text-zinc-200 placeholder:text-zinc-400
              focus:outline-none focus:ring-2 focus:ring-indigo-500/30"
          />
          {search && (
            <button onClick={() => setSearch('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-600">
              <X size={12} />
            </button>
          )}
        </div>
      </div>

      {/* Bulk action bar */}
      <AnimatePresence>
        {checked.size > 0 && (
          <motion.div
            initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }}
            className="mb-4 flex items-center gap-3 px-4 py-3 rounded-xl
              bg-indigo-50 dark:bg-indigo-500/10 border border-indigo-200 dark:border-indigo-500/20"
          >
            <span className="text-sm font-medium text-indigo-700 dark:text-indigo-300">
              {checked.size} kullanıcı seçildi
            </span>
            <div className="flex items-center gap-2 ml-auto">
              <button
                onClick={() => handleBulk(true)}
                disabled={bulkLoading}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold
                  bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20
                  hover:bg-emerald-500/20 disabled:opacity-50 transition-colors"
              >
                <CheckCircle size={12} /> Aktifleştir
              </button>
              <button
                onClick={() => handleBulk(false)}
                disabled={bulkLoading}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold
                  bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-200 dark:border-rose-500/20
                  hover:bg-rose-500/20 disabled:opacity-50 transition-colors"
              >
                <Ban size={12} /> Pasife Al
              </button>
              <button onClick={() => setChecked(new Set())} className="text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200">
                <X size={14} />
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <div className="glass-card overflow-hidden">
        {loading ? (
          <div className="flex justify-center py-16">
            <div className="w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : users.length === 0 ? (
          <p className="text-center text-zinc-400 py-16 text-sm">Kullanıcı bulunamadı.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-zinc-200 dark:border-white/[0.06]">
                  <th className="px-4 py-3 w-10">
                    <input
                      type="checkbox"
                      checked={checked.size === eligible.length && eligible.length > 0}
                      onChange={toggleAll}
                      className="rounded border-zinc-300 dark:border-zinc-600 text-indigo-500 focus:ring-indigo-500/30"
                    />
                  </th>
                  {['Kullanıcı', 'Durum', 'Giriş', 'Son Görülme', 'Ekstre', 'İşlemler'].map((h) => (
                    <th key={h} className="text-left px-4 py-3 text-[10px] font-semibold text-zinc-500 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-white/[0.04]">
                {users.map((u) => (
                  <tr key={u.id} className={`hover:bg-zinc-50 dark:hover:bg-white/[0.02] transition-colors ${checked.has(u.id) ? 'bg-indigo-50/50 dark:bg-indigo-500/5' : ''}`}>
                    <td className="px-4 py-3">
                      {u.role !== 'ROLE_ADMIN' && (
                        <input
                          type="checkbox"
                          checked={checked.has(u.id)}
                          onChange={() => toggleCheck(u.id)}
                          className="rounded border-zinc-300 dark:border-zinc-600 text-indigo-500 focus:ring-indigo-500/30"
                        />
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2.5">
                        <div className="w-8 h-8 rounded-lg flex items-center justify-center text-xs font-bold text-white shrink-0"
                          style={{ background: 'linear-gradient(135deg, #6366f1, #8b5cf6)' }}>
                          {initials(u.fullName)}
                        </div>
                        <div className="min-w-0">
                          <p className="font-medium text-zinc-800 dark:text-zinc-200 truncate max-w-[140px]">{u.fullName}</p>
                          <p className="text-[11px] text-zinc-400 truncate max-w-[140px]">{u.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`text-[10px] font-semibold px-2 py-1 rounded-lg ${
                        u.active ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400' : 'bg-rose-500/10 text-rose-500 dark:text-rose-400'
                      }`}>
                        {u.active ? 'Aktif' : 'Pasif'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-zinc-600 dark:text-zinc-400">{u.loginCount}</td>
                    <td className="px-4 py-3 text-zinc-500 text-xs whitespace-nowrap">{relativeTime(u.lastLoginAt)}</td>
                    <td className="px-4 py-3 text-zinc-600 dark:text-zinc-400">{u.statementCount}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <button onClick={() => openModal(u)}
                          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium
                            bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400
                            hover:bg-indigo-100 dark:hover:bg-indigo-500/20 transition-colors">
                          <Eye size={11} /> Detay
                        </button>
                        {u.role !== 'ROLE_ADMIN' && (
                          <button
                            onClick={() => handleToggle(u.id, !u.active)}
                            disabled={toggling}
                            className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium transition-colors disabled:opacity-50 ${
                              u.active
                                ? 'bg-rose-50 dark:bg-rose-500/10 text-rose-500 dark:text-rose-400 hover:bg-rose-100 dark:hover:bg-rose-500/20'
                                : 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 hover:bg-emerald-100 dark:hover:bg-emerald-500/20'
                            }`}
                          >
                            {u.active ? <><Ban size={11} /> Engelle</> : <><CheckCircle size={11} /> Aktif</>}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-3 px-4 py-3 border-t border-zinc-100 dark:border-white/[0.05]">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}
              className="p-1.5 rounded-lg text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100 hover:bg-zinc-100 dark:hover:bg-white/[0.07] disabled:opacity-30 transition-colors">
              <ChevronLeft size={15} />
            </button>
            <span className="text-xs text-zinc-500">{page + 1} / {totalPages}</span>
            <button onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
              className="p-1.5 rounded-lg text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100 hover:bg-zinc-100 dark:hover:bg-white/[0.07] disabled:opacity-30 transition-colors">
              <ChevronRight size={15} />
            </button>
          </div>
        )}
      </div>

      {selected && (
        <UserDetailModal
          user={selected} statements={statements} stmtLoading={stmtLoading}
          toggling={toggling} onClose={() => setSelected(null)} onToggleStatus={handleToggle}
        />
      )}
    </motion.div>
  );
}

// ── Parser Logs View ───────────────────────────────────────────────────────────

function ParserView() {
  const [tab,      setTab]      = useState('silent');
  const [silent,   setSilent]   = useState([]);
  const [failed,   setFailed]   = useState([]);
  const [loading,  setLoading]  = useState(true);

  useEffect(() => {
    setLoading(true);
    Promise.all([getAdminSilentFailures(), getAdminFailedStatements()])
      .then(([s, f]) => { setSilent(s.data); setFailed(f.data); })
      .catch(() => toast.error('Parser logları yüklenemedi.'))
      .finally(() => setLoading(false));
  }, []);

  const rows = tab === 'silent' ? silent : failed;

  return (
    <motion.div {...panelAnim}>
      <div className="mb-6">
        <h1 className="text-xl font-bold text-zinc-900 dark:text-zinc-100">Parser Logları</h1>
        <p className="text-sm text-zinc-500 mt-0.5">Sessiz hatalar ve başarısız işlemler</p>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-2 gap-4 mb-6">
        <div
          onClick={() => setTab('silent')}
          className={`glass-card p-4 cursor-pointer transition-all border-2 ${
            tab === 'silent' ? 'border-amber-500/40 bg-amber-50/50 dark:bg-amber-500/5' : 'border-transparent'
          }`}
        >
          <div className="flex items-center gap-2 mb-2">
            <div className="w-8 h-8 rounded-lg bg-amber-500/10 flex items-center justify-center">
              <AlertCircle size={14} className="text-amber-500" />
            </div>
            <span className="text-xs font-semibold text-zinc-500 uppercase tracking-wider">Sessiz Hata</span>
          </div>
          <p className="text-2xl font-bold text-zinc-900 dark:text-zinc-100">{silent.length}</p>
          <p className="text-xs text-zinc-400 mt-1">İşlendi ama 0 işlem çıktı</p>
        </div>

        <div
          onClick={() => setTab('failed')}
          className={`glass-card p-4 cursor-pointer transition-all border-2 ${
            tab === 'failed' ? 'border-rose-500/40 bg-rose-50/50 dark:bg-rose-500/5' : 'border-transparent'
          }`}
        >
          <div className="flex items-center gap-2 mb-2">
            <div className="w-8 h-8 rounded-lg bg-rose-500/10 flex items-center justify-center">
              <FileWarning size={14} className="text-rose-500" />
            </div>
            <span className="text-xs font-semibold text-zinc-500 uppercase tracking-wider">Hatalı Parse</span>
          </div>
          <p className="text-2xl font-bold text-zinc-900 dark:text-zinc-100">{failed.length}</p>
          <p className="text-xs text-zinc-400 mt-1">FAILED durumuyla tamamlandı</p>
        </div>
      </div>

      <div className="glass-card overflow-hidden">
        {loading ? (
          <div className="flex justify-center py-16">
            <div className="w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : rows.length === 0 ? (
          <div className="flex flex-col items-center py-16 gap-2">
            <CheckCircle size={28} className="text-emerald-400" />
            <p className="text-sm text-zinc-400">
              {tab === 'silent' ? 'Tüm ekstrelerden işlem çıkarıldı.' : 'Hiç başarısız ekstre yok.'}
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-zinc-200 dark:border-white/[0.06]">
                  {['Dosya', 'Kullanıcı', 'Banka', 'Tarih'].map((h) => (
                    <th key={h} className="text-left px-4 py-3 text-[10px] font-semibold text-zinc-500 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-white/[0.04]">
                {rows.map((r) => {
                  const bankCfg = BANK_LABELS[r.bankName] || { label: r.bankName || '—', color: '#71717a' };
                  return (
                    <tr key={r.statementId} className="hover:bg-zinc-50 dark:hover:bg-white/[0.02] transition-colors">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <div className={`w-1.5 h-1.5 rounded-full shrink-0 ${tab === 'silent' ? 'bg-amber-400' : 'bg-rose-400'}`} />
                          <p className="text-zinc-800 dark:text-zinc-200 truncate max-w-[220px] font-medium">{r.fileName}</p>
                        </div>
                        <p className="text-[11px] text-zinc-400 ml-3.5">#{r.statementId}</p>
                      </td>
                      <td className="px-4 py-3">
                        <p className="text-zinc-700 dark:text-zinc-300">@{r.username}</p>
                        <p className="text-[11px] text-zinc-400">#{r.userId}</p>
                      </td>
                      <td className="px-4 py-3">
                        {r.bankName ? (
                          <span className="text-xs font-medium px-2 py-1 rounded-lg"
                            style={{ background: bankCfg.color + '20', color: bankCfg.color }}>
                            {bankCfg.label}
                          </span>
                        ) : <span className="text-zinc-400">—</span>}
                      </td>
                      <td className="px-4 py-3 text-xs text-zinc-500 whitespace-nowrap">{r.uploadDate}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </motion.div>
  );
}

// ── Audit View ─────────────────────────────────────────────────────────────────

function AuditView() {
  const [logs,       setLogs]       = useState([]);
  const [page,       setPage]       = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading,    setLoading]    = useState(true);

  useEffect(() => {
    setLoading(true);
    getAdminAudit(page, 50)
      .then((r) => { setLogs(r.data.content); setTotalPages(r.data.totalPages); })
      .catch(() => toast.error('Audit logları yüklenemedi.'))
      .finally(() => setLoading(false));
  }, [page]);

  return (
    <motion.div {...panelAnim}>
      <div className="mb-6">
        <h1 className="text-xl font-bold text-zinc-900 dark:text-zinc-100">Denetim Günlüğü</h1>
        <p className="text-sm text-zinc-500 mt-0.5">Sistem olaylarının kronolojik kaydı</p>
      </div>

      <div className="glass-card overflow-hidden">
        {loading ? (
          <div className="flex justify-center py-16">
            <div className="w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : logs.length === 0 ? (
          <p className="text-center text-zinc-400 py-16 text-sm">Henüz kayıt yok.</p>
        ) : (
          <div className="divide-y divide-zinc-100 dark:divide-white/[0.04]">
            {logs.map((log) => {
              const cfg  = AUDIT_CFG[log.eventType] || { label: log.eventType, icon: Activity, bg: 'bg-zinc-500/10 text-zinc-500' };
              const Icon = cfg.icon;
              return (
                <div key={log.id} className="flex items-start gap-3 px-4 py-3 hover:bg-zinc-50 dark:hover:bg-white/[0.02] transition-colors">
                  <div className={`shrink-0 w-7 h-7 rounded-lg flex items-center justify-center mt-0.5 ${cfg.bg}`}>
                    <Icon size={13} strokeWidth={2} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-md ${cfg.bg}`}>{cfg.label}</span>
                      {log.username && <span className="text-xs font-medium text-zinc-700 dark:text-zinc-300">@{log.username}</span>}
                      {log.ipAddress && <span className="text-[11px] text-zinc-400 font-mono">{log.ipAddress}</span>}
                    </div>
                    {log.details && <p className="text-[11px] text-zinc-400 mt-0.5 truncate">{log.details}</p>}
                  </div>
                  <span className="shrink-0 text-[11px] text-zinc-400 whitespace-nowrap">{relativeTime(log.createdAt)}</span>
                </div>
              );
            })}
          </div>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-3 px-4 py-3 border-t border-zinc-100 dark:border-white/[0.05]">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}
              className="p-1.5 rounded-lg text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100 hover:bg-zinc-100 dark:hover:bg-white/[0.07] disabled:opacity-30 transition-colors">
              <ChevronLeft size={15} />
            </button>
            <span className="text-xs text-zinc-500">{page + 1} / {totalPages}</span>
            <button onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
              className="p-1.5 rounded-lg text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100 hover:bg-zinc-100 dark:hover:bg-white/[0.07] disabled:opacity-30 transition-colors">
              <ChevronRight size={15} />
            </button>
          </div>
        )}
      </div>
    </motion.div>
  );
}

// ── Settings View ──────────────────────────────────────────────────────────────

function Toggle({ enabled, onChange, label, description }) {
  return (
    <div className="flex items-center justify-between py-4 border-b border-zinc-100 dark:border-white/[0.06] last:border-0">
      <div>
        <p className="text-sm font-medium text-zinc-800 dark:text-zinc-200">{label}</p>
        {description && <p className="text-xs text-zinc-400 mt-0.5">{description}</p>}
      </div>
      <button
        onClick={() => onChange(!enabled)}
        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold transition-all ${
          enabled
            ? 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/20'
            : 'bg-zinc-100 dark:bg-white/[0.05] text-zinc-500 border border-zinc-200 dark:border-white/[0.08]'
        }`}
      >
        {enabled ? <ToggleRight size={14} /> : <ToggleLeft size={14} />}
        {enabled ? 'Açık' : 'Kapalı'}
      </button>
    </div>
  );
}

function SettingsView() {
  const [settings,     setSettings]     = useState(null);
  const [announcement, setAnnouncement] = useState('');
  const [disabledBanks, setDisabledBanks] = useState(new Set());
  const [maintenance,  setMaintenance]  = useState(false);
  const [loading,      setLoading]      = useState(true);
  const [saving,       setSaving]       = useState(false);

  useEffect(() => {
    getAdminSettings()
      .then((r) => {
        const d = r.data;
        setSettings(d);
        setMaintenance(d.maintenanceMode);
        setAnnouncement(d.announcement || '');
        const banks = d.disabledBanks ? d.disabledBanks.split(',').map((s) => s.trim()).filter(Boolean) : [];
        setDisabledBanks(new Set(banks));
      })
      .catch(() => toast.error('Ayarlar yüklenemedi.'))
      .finally(() => setLoading(false));
  }, []);

  const toggleBank = (bank) => {
    setDisabledBanks((prev) => {
      const next = new Set(prev);
      next.has(bank) ? next.delete(bank) : next.add(bank);
      return next;
    });
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await updateAdminSettings({
        maintenanceMode: maintenance,
        announcement,
        disabledBanks: [...disabledBanks].join(','),
      });
      toast.success('Ayarlar kaydedildi.');
    } catch { toast.error('Ayarlar kaydedilemedi.'); }
    finally  { setSaving(false); }
  };

  if (loading) return (
    <div className="flex justify-center items-center h-64">
      <div className="w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
    </div>
  );

  return (
    <motion.div {...panelAnim}>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-zinc-900 dark:text-zinc-100">Ayarlar</h1>
          <p className="text-sm text-zinc-500 mt-0.5">Sistem geneli operasyonel kontroller</p>
        </div>
        <motion.button
          whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}
          onClick={handleSave}
          disabled={saving}
          className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold text-white disabled:opacity-60"
          style={{ background: 'linear-gradient(135deg, #6366F1, #4F46E5)', boxShadow: '0 4px 14px rgba(99,102,241,0.35)' }}
        >
          {saving
            ? <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            : <Save size={14} />
          }
          Kaydet
        </motion.button>
      </div>

      {/* Maintenance Mode */}
      <div className="glass-card p-5 mb-4">
        <div className="flex items-center gap-2 mb-4">
          <Wrench size={14} className="text-indigo-400" />
          <p className="text-xs font-semibold text-zinc-500 uppercase tracking-widest">Sistem Kontrolleri</p>
        </div>

        <Toggle
          enabled={maintenance}
          onChange={setMaintenance}
          label="Bakım Modu"
          description="Açıldığında admin ve auth dışındaki tüm endpoint'ler 503 döner. Kullanıcılar API erişimine 'Sistem bakımda' mesajıyla karşılaşır."
        />

        {maintenance && (
          <motion.div
            initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }}
            className="mt-3 flex items-start gap-2.5 bg-amber-500/10 border border-amber-500/20 rounded-xl px-3 py-2.5 text-xs text-amber-600 dark:text-amber-400"
          >
            <AlertTriangle size={13} className="shrink-0 mt-0.5" />
            Bakım modu aktif olduğunda sisteme giriş yapan kullanıcılar dashboard'ı göremez.
          </motion.div>
        )}
      </div>

      {/* Announcement */}
      <div className="glass-card p-5 mb-4">
        <div className="flex items-center gap-2 mb-4">
          <Megaphone size={14} className="text-indigo-400" />
          <p className="text-xs font-semibold text-zinc-500 uppercase tracking-widest">Uygulama İçi Duyuru</p>
        </div>

        <p className="text-xs text-zinc-500 mb-3">
          Boş bırakırsan banner gösterilmez. Dolu olduğunda tüm kullanıcıların dashboard'ında banner olarak görünür.
        </p>

        <textarea
          value={announcement}
          onChange={(e) => setAnnouncement(e.target.value)}
          placeholder="Örn: Yarın 02:00–03:00 arası sistem bakımı yapılacaktır."
          rows={3}
          className="w-full px-3 py-2.5 rounded-xl text-sm
            bg-white dark:bg-white/[0.04] border border-zinc-200 dark:border-white/[0.08]
            text-zinc-800 dark:text-zinc-200 placeholder:text-zinc-400
            focus:outline-none focus:ring-2 focus:ring-indigo-500/30 resize-none"
        />

        {announcement && (
          <div className="mt-3 flex items-start gap-2 bg-indigo-50 dark:bg-indigo-500/10 border border-indigo-200 dark:border-indigo-500/20 rounded-xl px-3 py-2.5">
            <Megaphone size={13} className="text-indigo-500 shrink-0 mt-0.5" />
            <p className="text-xs text-indigo-700 dark:text-indigo-300">{announcement}</p>
          </div>
        )}
      </div>

      {/* Bank Parser Switches */}
      <div className="glass-card p-5">
        <div className="flex items-center gap-2 mb-1">
          <BarChart3 size={14} className="text-indigo-400" />
          <p className="text-xs font-semibold text-zinc-500 uppercase tracking-widest">Banka Parser Switcher</p>
        </div>
        <p className="text-xs text-zinc-400 mb-4">
          Deaktif edilen banka parseri, o bankaya ait ekstre yüklenmeye çalışıldığında "Bakımda" hatası verir.
        </p>

        {SUPPORTED_BANKS.map((bank) => {
          const cfg = BANK_LABELS[bank];
          const disabled = disabledBanks.has(bank);
          return (
            <Toggle
              key={bank}
              enabled={!disabled}
              onChange={() => toggleBank(bank)}
              label={cfg.label}
              description={disabled ? 'Parser deaktif — yükleme engelleniyor' : 'Parser aktif'}
            />
          );
        })}
      </div>
    </motion.div>
  );
}

// ── Main ───────────────────────────────────────────────────────────────────────

export default function AdminPage() {
  const [view, setView] = useState('overview');

  return (
    <div className="min-h-screen flex bg-zinc-50 dark:bg-[#050507]">
      <div
        className="pointer-events-none fixed inset-0 dark:block hidden"
        style={{
          background:
            'radial-gradient(ellipse 60% 40% at 20% 10%, rgba(99,102,241,0.055) 0%, transparent 60%),' +
            'radial-gradient(ellipse 50% 40% at 80% 80%, rgba(139,92,246,0.055) 0%, transparent 60%)',
        }}
      />

      <AdminSidebar view={view} setView={setView} />

      {/* Mobile tab bar */}
      <div className="lg:hidden fixed top-0 left-0 right-0 z-40 flex items-center gap-1 px-3 pt-2 pb-2
        bg-white/90 dark:bg-zinc-950/90 backdrop-blur-xl border-b border-zinc-200/60 dark:border-white/[0.06] overflow-x-auto">
        {VIEWS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => setView(id)}
            className={`shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium transition-all ${
              view === id ? 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400' : 'text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200'
            }`}
          >
            <Icon size={13} strokeWidth={1.8} />
            {label}
          </button>
        ))}
        <Link to="/dashboard"
          className="ml-auto shrink-0 flex items-center gap-1 text-xs text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200">
          <ArrowLeft size={12} /> Geri
        </Link>
      </div>

      <main className="relative flex-1 lg:ml-[220px] px-4 sm:px-6 py-6 pt-[56px] lg:pt-8 pb-8">
        <AnimatePresence mode="wait">
          {view === 'overview' && <OverviewView key="overview" />}
          {view === 'users'    && <UsersView    key="users"    />}
          {view === 'parser'   && <ParserView   key="parser"   />}
          {view === 'audit'    && <AuditView    key="audit"    />}
          {view === 'settings' && <SettingsView key="settings" />}
        </AnimatePresence>
      </main>
    </div>
  );
}
