import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { motion, AnimatePresence } from 'framer-motion';
import {
  ArrowLeft, User, Lock, Save, Eye, EyeOff,
  FileDown, Trash2, ShieldCheck, Database,
} from 'lucide-react';
import {
  getUserProfile, updateProfile, changePassword,
  deleteAllStatements, getAnalyticsSummary, getTransactions, getStoredUser,
} from '../api/client';
import { generateReport } from '../utils/pdfReport';
import { BrandMark } from '../components/auth/VisionPanel';
import { fadeUp, inputCls } from '../components/shared';

// ── Input components ──────────────────────────────────────────────────────────
const labelCls = 'block text-[10px] font-semibold text-zinc-500 uppercase tracking-widest mb-1.5';

function Field({ label, children }) {
  return (
    <div>
      <label className={labelCls}>{label}</label>
      {children}
    </div>
  );
}

function PasswordInput({ value, onChange, placeholder, disabled }) {
  const [show, setShow] = useState(false);
  return (
    <div className="relative">
      <input
        type={show ? 'text' : 'password'}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        disabled={disabled}
        className={inputCls + ' pr-11'}
      />
      <button
        type="button"
        onClick={() => setShow((s) => !s)}
        tabIndex={-1}
        className="absolute right-3.5 top-1/2 -translate-y-1/2 text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-300 transition-colors"
      >
        {show ? <EyeOff size={15} /> : <Eye size={15} />}
      </button>
    </div>
  );
}

// ── Tab definitions ───────────────────────────────────────────────────────────
const TABS = [
  { id: 'personal', label: 'Kişisel Bilgiler', icon: User },
  { id: 'security', label: 'Güvenlik',          icon: ShieldCheck },
  { id: 'data',     label: 'Veri Yönetimi',     icon: Database },
];

// ── Primary button ────────────────────────────────────────────────────────────
function PrimaryBtn({ loading, loadingText, children, accent = 'indigo', ...props }) {
  const bg = {
    indigo: 'bg-indigo-600 hover:bg-indigo-500 shadow-[0_0_20px_rgba(99,102,241,0.25)] hover:shadow-[0_0_28px_rgba(99,102,241,0.4)]',
    violet: 'bg-violet-600 hover:bg-violet-500 shadow-[0_0_20px_rgba(139,92,246,0.25)] hover:shadow-[0_0_28px_rgba(139,92,246,0.4)]',
  }[accent];

  return (
    <motion.button
      type="submit"
      whileHover={{ scale: 1.02 }}
      whileTap={{ scale: 0.97 }}
      disabled={loading}
      className={`flex items-center gap-2 ${bg} text-white px-5 py-2.5 rounded-xl text-sm font-semibold transition-all disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none`}
      {...props}
    >
      {loading
        ? <div className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
        : null}
      {loading ? loadingText : children}
    </motion.button>
  );
}

// ── Tab content panels ────────────────────────────────────────────────────────
const panelAnim = {
  initial:  { opacity: 0, y: 10 },
  animate:  { opacity: 1, y: 0 },
  exit:     { opacity: 0, y: -8 },
  transition: { duration: 0.28, ease: [0.23, 1, 0.32, 1] },
};

function PersonalPanel({ profile, setProfile, onSubmit, saving }) {
  return (
    <motion.form key="personal" {...panelAnim} onSubmit={onSubmit} className="space-y-5">
      <Field label="Ad Soyad">
        <input
          type="text"
          value={profile.fullName}
          onChange={(e) => setProfile((p) => ({ ...p, fullName: e.target.value }))}
          placeholder="Adınız ve soyadınız"
          disabled={saving}
          className={inputCls}
        />
      </Field>
      <Field label="E-posta Adresi">
        <input
          type="email"
          value={profile.email}
          onChange={(e) => setProfile((p) => ({ ...p, email: e.target.value }))}
          placeholder="ad@ornek.com"
          disabled={saving}
          className={inputCls}
        />
      </Field>
      <div className="pt-1">
        <PrimaryBtn loading={saving} loadingText="Kaydediliyor..." accent="indigo">
          <Save size={14} strokeWidth={2.5} />
          Değişiklikleri Kaydet
        </PrimaryBtn>
      </div>
    </motion.form>
  );
}

function SecurityPanel({ pwForm, setPwForm, onSubmit, saving }) {
  return (
    <motion.form key="security" {...panelAnim} onSubmit={onSubmit} className="space-y-5">
      <Field label="Mevcut Şifre">
        <PasswordInput
          value={pwForm.currentPassword}
          onChange={(e) => setPwForm((p) => ({ ...p, currentPassword: e.target.value }))}
          placeholder="Mevcut şifreniz"
          disabled={saving}
        />
      </Field>
      <Field label="Yeni Şifre">
        <PasswordInput
          value={pwForm.newPassword}
          onChange={(e) => setPwForm((p) => ({ ...p, newPassword: e.target.value }))}
          placeholder="En az 6 karakter"
          disabled={saving}
        />
      </Field>
      <Field label="Yeni Şifre Tekrar">
        <PasswordInput
          value={pwForm.confirmPassword}
          onChange={(e) => setPwForm((p) => ({ ...p, confirmPassword: e.target.value }))}
          placeholder="Yeni şifreyi tekrarlayın"
          disabled={saving}
        />
        <AnimatePresence>
          {pwForm.confirmPassword && pwForm.newPassword !== pwForm.confirmPassword && (
            <motion.p
              initial={{ opacity: 0, y: -4 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              className="mt-1.5 text-[11px] text-rose-500"
            >
              Şifreler eşleşmiyor
            </motion.p>
          )}
        </AnimatePresence>
      </Field>
      <div className="pt-1">
        <PrimaryBtn loading={saving} loadingText="Değiştiriliyor..." accent="violet">
          <Lock size={14} strokeWidth={2.5} />
          Şifreyi Değiştir
        </PrimaryBtn>
      </div>
    </motion.form>
  );
}

function DataPanel({ onDownload, onDelete, pdfLoading, deleteLoading }) {
  return (
    <motion.div key="data" {...panelAnim} className="space-y-3">
      <p className="text-xs text-zinc-500 dark:text-zinc-600 mb-5">
        Finansal verilerinizi dışa aktarın veya kalıcı olarak silin.
      </p>

      {/* PDF rapor */}
      <motion.button
        whileHover={{ scale: 1.01, y: -1 }}
        whileTap={{ scale: 0.98 }}
        onClick={onDownload}
        disabled={pdfLoading}
        className="w-full flex items-center gap-4 px-5 py-4 rounded-xl text-sm font-medium
          bg-emerald-50 dark:bg-emerald-500/10 border border-emerald-200 dark:border-emerald-500/20 text-emerald-700 dark:text-emerald-400
          hover:bg-emerald-100 dark:hover:bg-emerald-500/15 hover:border-emerald-300 dark:hover:border-emerald-500/30
          transition-all disabled:opacity-40 disabled:cursor-not-allowed"
      >
        {pdfLoading
          ? <div className="w-4 h-4 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin" />
          : <FileDown size={16} strokeWidth={2} />
        }
        <div className="text-left">
          <p className="font-semibold">{pdfLoading ? 'Rapor hazırlanıyor...' : 'PDF Rapor İndir'}</p>
          <p className="text-[11px] text-emerald-600/80 dark:text-emerald-600 mt-0.5">Tüm işlemleri dışa aktar</p>
        </div>
      </motion.button>

      {/* Veri sil */}
      <motion.button
        whileHover={{ scale: 1.01, y: -1 }}
        whileTap={{ scale: 0.98 }}
        onClick={onDelete}
        disabled={deleteLoading}
        className="w-full flex items-center gap-4 px-5 py-4 rounded-xl text-sm font-medium
          bg-rose-50 dark:bg-rose-500/10 border border-rose-200 dark:border-rose-500/20 text-rose-600 dark:text-rose-400
          hover:bg-rose-100 dark:hover:bg-rose-500/15 hover:border-rose-300 dark:hover:border-rose-500/30
          transition-all disabled:opacity-40 disabled:cursor-not-allowed"
      >
        {deleteLoading
          ? <div className="w-4 h-4 border-2 border-rose-500/30 border-t-rose-500 rounded-full animate-spin" />
          : <Trash2 size={16} strokeWidth={2} />
        }
        <div className="text-left">
          <p className="font-semibold">{deleteLoading ? 'Siliniyor...' : 'Tüm Veriyi Sil'}</p>
          <p className="text-[11px] text-rose-500/80 dark:text-rose-600 mt-0.5">Bu işlem geri alınamaz</p>
        </div>
      </motion.button>
    </motion.div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────
export default function ProfilePage() {
  const [pageLoading,  setPageLoading]  = useState(true);
  const [activeTab,    setActiveTab]    = useState('personal');

  const [profile,      setProfile]      = useState({ fullName: '', email: '' });
  const [profileSaving, setProfileSaving] = useState(false);

  const [pwForm,       setPwForm]       = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [pwSaving,     setPwSaving]     = useState(false);

  const [pdfLoading,   setPdfLoading]   = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);

  useEffect(() => {
    getUserProfile()
      .then((res) => setProfile({ fullName: res.data.fullName, email: res.data.email }))
      .catch(() => toast.error('Profil bilgileri yüklenemedi.'))
      .finally(() => setPageLoading(false));
  }, []);

  const handleProfileSubmit = async (e) => {
    e.preventDefault();
    if (!profile.fullName.trim() || !profile.email.trim()) {
      toast.error('Ad Soyad ve e-posta boş bırakılamaz.');
      return;
    }
    setProfileSaving(true);
    try {
      await updateProfile({ fullName: profile.fullName.trim(), email: profile.email.trim() });
      toast.success('Profil bilgileri güncellendi.');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Güncelleme başarısız.');
    } finally {
      setProfileSaving(false);
    }
  };

  const handlePasswordSubmit = async (e) => {
    e.preventDefault();
    if (pwForm.newPassword !== pwForm.confirmPassword) {
      toast.error('Yeni şifre ve tekrarı eşleşmiyor.');
      return;
    }
    if (pwForm.newPassword.length < 6) {
      toast.error('Yeni şifre en az 6 karakter olmalı.');
      return;
    }
    setPwSaving(true);
    try {
      await changePassword(pwForm);
      toast.success('Şifreniz başarıyla değiştirildi.');
      setPwForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
    } catch (err) {
      toast.error(err.response?.data?.message || 'Şifre değiştirilemedi.');
    } finally {
      setPwSaving(false);
    }
  };

  const handleDownloadPdf = async () => {
    setPdfLoading(true);
    try {
      const [s, t] = await Promise.all([getAnalyticsSummary(), getTransactions()]);
      await generateReport({ summary: s.data, transactions: t.data, user: getStoredUser() });
    } catch {
      toast.error('Rapor oluşturulamadı.');
    } finally {
      setPdfLoading(false);
    }
  };

  const handleDeleteAll = async () => {
    if (!window.confirm('Tüm işlemler ve ekstre kayıtları kalıcı olarak silinecek. Emin misiniz?')) return;
    setDeleteLoading(true);
    try {
      await deleteAllStatements();
      toast.success('Tüm veriler silindi.');
    } catch {
      toast.error('Veriler silinirken hata oluştu.');
    } finally {
      setDeleteLoading(false);
    }
  };

  const initials = profile.fullName
    ? profile.fullName.split(' ').filter(Boolean).map((n) => n[0]).join('').toUpperCase().slice(0, 2)
    : '?';

  if (pageLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-zinc-50 dark:bg-[#050507] gap-3">
        <div className="w-8 h-8 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
        <p className="text-sm text-zinc-500 dark:text-zinc-600">Yükleniyor...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-[#050507] mesh-bg pb-16">

      {/* ── Header ── */}
      <motion.header
        {...fadeUp(0)}
        className="sticky top-0 z-20 flex items-center justify-between px-5 md:px-8 py-3.5
          bg-white/80 dark:bg-[rgba(5,5,7,0.8)] backdrop-blur-xl
          border-b border-zinc-200/60 dark:border-white/[0.05]"
      >
        <div className="flex items-center gap-3">
          <Link
            to="/"
            className="flex items-center gap-1.5 text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-200 text-sm font-medium transition-colors"
          >
            <ArrowLeft size={15} strokeWidth={2} />
            <span className="hidden sm:inline">Dashboard</span>
          </Link>
          <span className="text-zinc-300 dark:text-zinc-800">|</span>
          <div className="flex items-center gap-2">
            <BrandMark size={24} />
            <span className="font-bold text-zinc-900 dark:text-zinc-100 tracking-tight text-base">Smart Budget</span>
          </div>
        </div>
      </motion.header>

      {/* ── Content ── */}
      <div className="max-w-5xl mx-auto px-5 md:px-8 pt-8">

        {/* Page title */}
        <motion.div {...fadeUp(0.05)} className="mb-8">
          <h1 className="text-2xl font-bold text-zinc-900 dark:text-zinc-100 tracking-tight">Profil Ayarları</h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-600 mt-1">Hesap bilgilerinizi ve güvenlik ayarlarınızı yönetin</p>
        </motion.div>

        <div className="grid grid-cols-1 lg:grid-cols-[260px_1fr] gap-6 items-start">

          {/* ── Left: Profile summary card ── */}
          <motion.div {...fadeUp(0.1)} className="lg:sticky lg:top-24">
            <div className="glass-card p-6 text-center">
              {/* Avatar */}
              <div className="relative inline-flex mb-4">
                <div
                  className="w-20 h-20 rounded-2xl flex items-center justify-center text-2xl font-bold text-white select-none"
                  style={{ background: 'linear-gradient(135deg, #6366f1, #8b5cf6)' }}
                >
                  {initials}
                </div>
                <span className="absolute -bottom-1.5 -right-1.5 w-5 h-5 rounded-full bg-emerald-400 border-2 border-zinc-50 dark:border-[#050507]" />
              </div>

              <h2 className="text-base font-bold text-zinc-900 dark:text-zinc-100 truncate">{profile.fullName || '—'}</h2>
              <p className="text-xs text-zinc-500 mt-0.5 truncate">{profile.email || '—'}</p>

              <div className="mt-5 pt-5 border-t border-zinc-200 dark:border-white/[0.06] space-y-2.5">
                {[
                  { label: 'Plan',  value: 'Ücretsiz' },
                  { label: 'Durum', value: 'Aktif' },
                ].map(({ label, value }) => (
                  <div key={label} className="flex justify-between text-xs">
                    <span className="text-zinc-500">{label}</span>
                    <span className="text-zinc-700 dark:text-zinc-300 font-medium">{value}</span>
                  </div>
                ))}
              </div>

              {/* Quick-nav to tabs on mobile */}
              <div className="mt-5 grid grid-cols-3 gap-1.5 lg:hidden">
                {TABS.map(({ id, icon: Icon, label }) => (
                  <button
                    key={id}
                    onClick={() => setActiveTab(id)}
                    className={`flex flex-col items-center gap-1 py-2 rounded-xl text-[10px] font-semibold transition-all ${
                      activeTab === id
                        ? 'bg-indigo-500/10 dark:bg-indigo-500/20 text-indigo-600 dark:text-indigo-400 border border-indigo-300 dark:border-indigo-500/30'
                        : 'bg-zinc-100 dark:bg-white/[0.03] text-zinc-500 dark:text-zinc-600 border border-zinc-200 dark:border-white/[0.05] hover:text-zinc-700 dark:hover:text-zinc-400'
                    }`}
                  >
                    <Icon size={14} strokeWidth={1.8} />
                    {label.split(' ')[0]}
                  </button>
                ))}
              </div>
            </div>
          </motion.div>

          {/* ── Right: Tabs + Content ── */}
          <motion.div {...fadeUp(0.18)}>
            <div className="glass-card p-6">
              {/* ── Tab bar (Apple layoutId style) ── */}
              <div className="relative flex gap-1 p-1 rounded-xl mb-7 bg-zinc-100 dark:bg-white/[0.04] hidden lg:flex">
                {TABS.map(({ id, label, icon: Icon }) => (
                  <button
                    key={id}
                    onClick={() => setActiveTab(id)}
                    className="relative flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium z-10 transition-colors"
                    style={{ color: activeTab === id ? (document.documentElement.classList.contains('dark') ? '#e4e4e7' : '#18181b') : '#71717a' }}
                  >
                    {activeTab === id && (
                      <motion.div
                        layoutId="activeTab"
                        className="absolute inset-0 rounded-lg bg-white dark:bg-white/[0.08] shadow-sm"
                        transition={{ type: 'spring', stiffness: 400, damping: 35 }}
                      />
                    )}
                    <Icon size={14} strokeWidth={1.8} />
                    <span className="relative">{label}</span>
                  </button>
                ))}
              </div>

              {/* ── Tab content ── */}
              <AnimatePresence mode="wait">
                {activeTab === 'personal' && (
                  <PersonalPanel
                    key="personal"
                    profile={profile}
                    setProfile={setProfile}
                    onSubmit={handleProfileSubmit}
                    saving={profileSaving}
                  />
                )}
                {activeTab === 'security' && (
                  <SecurityPanel
                    key="security"
                    pwForm={pwForm}
                    setPwForm={setPwForm}
                    onSubmit={handlePasswordSubmit}
                    saving={pwSaving}
                  />
                )}
                {activeTab === 'data' && (
                  <DataPanel
                    key="data"
                    onDownload={handleDownloadPdf}
                    onDelete={handleDeleteAll}
                    pdfLoading={pdfLoading}
                    deleteLoading={deleteLoading}
                  />
                )}
              </AnimatePresence>
            </div>
          </motion.div>

        </div>
      </div>
    </div>
  );
}
