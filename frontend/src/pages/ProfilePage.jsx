import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft, User, Lock, Save, Eye, EyeOff, FileDown, Trash2 } from 'lucide-react';
import { getUserProfile, updateProfile, changePassword, deleteAllStatements, getAnalyticsSummary, getTransactions, getStoredUser } from '../api/client';
import { generateReport } from '../utils/pdfReport';
import { useTheme } from '../context/ThemeContext';
import { Sun, Moon } from 'lucide-react';

const inputClass =
  'w-full rounded-lg border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 ' +
  'text-zinc-900 dark:text-zinc-100 placeholder-zinc-400 dark:placeholder-zinc-500 ' +
  'px-3.5 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 ' +
  'focus:border-transparent transition disabled:opacity-50 disabled:cursor-not-allowed';

const labelClass = 'block text-xs font-semibold text-zinc-500 dark:text-zinc-400 uppercase tracking-wide mb-1.5';

function PasswordInput({ id, value, onChange, placeholder, disabled }) {
  const [show, setShow] = useState(false);
  return (
    <div className="relative">
      <input
        id={id}
        type={show ? 'text' : 'password'}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        disabled={disabled}
        className={inputClass + ' pr-10'}
      />
      <button
        type="button"
        onClick={() => setShow((s) => !s)}
        className="absolute right-3 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300 transition-colors"
        tabIndex={-1}
      >
        {show ? <EyeOff size={15} /> : <Eye size={15} />}
      </button>
    </div>
  );
}

export default function ProfilePage() {
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();

  const [pageLoading, setPageLoading] = useState(true);

  // Profil formu
  const [profile, setProfile] = useState({ fullName: '', email: '' });
  const [profileSaving, setProfileSaving] = useState(false);

  // Şifre formu
  const [pwForm, setPwForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [pwSaving, setPwSaving] = useState(false);

  // Veri yönetimi
  const [pdfLoading, setPdfLoading] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const handleDownloadPdf = async () => {
    setPdfLoading(true);
    try {
      const [summaryRes, txRes] = await Promise.all([getAnalyticsSummary(), getTransactions()]);
      await generateReport({ summary: summaryRes.data, transactions: txRes.data, user: getStoredUser() });
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

  // Sayfa yüklenince profil çek
  useEffect(() => {
    getUserProfile()
      .then((res) => {
        setProfile({ fullName: res.data.fullName, email: res.data.email });
      })
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
      const msg = err.response?.data?.message;
      if (msg) toast.error(msg);
    } finally {
      setProfileSaving(false);
    }
  };

  const handlePasswordSubmit = async (e) => {
    e.preventDefault();
    if (pwForm.newPassword !== pwForm.confirmPassword) {
      toast.error('Yeni şifre ve şifre tekrarı eşleşmiyor.');
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
      const msg = err.response?.data?.message;
      toast.error(msg || 'Şifre değiştirilemedi.');
    } finally {
      setPwSaving(false);
    }
  };

  if (pageLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-zinc-50 dark:bg-zinc-950 gap-3">
        <div className="w-8 h-8 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
        <p className="text-sm text-zinc-400 dark:text-zinc-500">Yükleniyor...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 pb-16 transition-colors duration-200">

      {/* Header */}
      <header className="flex justify-between items-center px-5 md:px-8 py-4 border-b border-zinc-200 dark:border-zinc-800 bg-white/70 dark:bg-zinc-900/70 backdrop-blur-sm sticky top-0 z-10">
        <div className="flex items-center gap-3">
          <Link
            to="/"
            className="flex items-center gap-1.5 text-zinc-500 dark:text-zinc-400 hover:text-zinc-800 dark:hover:text-zinc-100 text-sm font-medium transition-colors"
          >
            <ArrowLeft size={15} strokeWidth={2} />
            <span className="hidden sm:inline">Dashboard</span>
          </Link>
          <span className="text-zinc-300 dark:text-zinc-700">|</span>
          <div className="flex items-center gap-2">
            <span className="text-xl">💰</span>
            <span className="font-bold text-zinc-900 dark:text-zinc-100 tracking-tight text-lg">
              Smart Budget
            </span>
          </div>
        </div>

        <button
          onClick={toggleTheme}
          className="p-2 rounded-lg text-zinc-400 dark:text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
          title={theme === 'dark' ? 'Açık tema' : 'Koyu tema'}
        >
          {theme === 'dark' ? <Sun size={17} strokeWidth={2} /> : <Moon size={17} strokeWidth={2} />}
        </button>
      </header>

      {/* Content */}
      <div className="max-w-2xl mx-auto px-5 md:px-8 pt-8 space-y-6">

        {/* Sayfa başlığı */}
        <div>
          <h1 className="text-2xl font-bold text-zinc-900 dark:text-zinc-100 tracking-tight">
            Profil Ayarları
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400 mt-1">
            Kişisel bilgilerinizi ve güvenlik ayarlarınızı buradan yönetebilirsiniz.
          </p>
        </div>

        {/* ── Kişisel Bilgiler ── */}
        <div className="bg-white dark:bg-zinc-900 rounded-xl shadow-sm border border-zinc-100 dark:border-zinc-800 p-6">
          <div className="flex items-center gap-2.5 mb-5">
            <div className="w-8 h-8 rounded-lg bg-indigo-50 dark:bg-indigo-950 flex items-center justify-center">
              <User size={15} className="text-indigo-600 dark:text-indigo-400" strokeWidth={2} />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                Kişisel Bilgiler
              </h2>
              <p className="text-xs text-zinc-400 dark:text-zinc-500">Ad, soyad ve e-posta adresinizi güncelleyin</p>
            </div>
          </div>

          <form onSubmit={handleProfileSubmit} className="space-y-4">
            <div>
              <label htmlFor="fullName" className={labelClass}>Ad Soyad</label>
              <input
                id="fullName"
                type="text"
                value={profile.fullName}
                onChange={(e) => setProfile((p) => ({ ...p, fullName: e.target.value }))}
                placeholder="Adınız ve soyadınız"
                disabled={profileSaving}
                className={inputClass}
              />
            </div>

            <div>
              <label htmlFor="email" className={labelClass}>E-posta Adresi</label>
              <input
                id="email"
                type="email"
                value={profile.email}
                onChange={(e) => setProfile((p) => ({ ...p, email: e.target.value }))}
                placeholder="ornek@eposta.com"
                disabled={profileSaving}
                className={inputClass}
              />
            </div>

            <div className="pt-1">
              <button
                type="submit"
                disabled={profileSaving}
                className="flex items-center gap-2 bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 disabled:opacity-50 disabled:cursor-not-allowed text-white px-4 py-2.5 rounded-lg text-sm font-semibold transition-colors"
              >
                {profileSaving
                  ? <div className="w-3.5 h-3.5 border-2 border-white/40 border-t-white rounded-full animate-spin" />
                  : <Save size={14} strokeWidth={2.5} />
                }
                {profileSaving ? 'Kaydediliyor...' : 'Değişiklikleri Kaydet'}
              </button>
            </div>
          </form>
        </div>

        {/* ── Güvenlik Ayarları ── */}
        <div className="bg-white dark:bg-zinc-900 rounded-xl shadow-sm border border-zinc-100 dark:border-zinc-800 p-6">
          <div className="flex items-center gap-2.5 mb-5">
            <div className="w-8 h-8 rounded-lg bg-violet-50 dark:bg-violet-950 flex items-center justify-center">
              <Lock size={15} className="text-violet-600 dark:text-violet-400" strokeWidth={2} />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                Güvenlik Ayarları
              </h2>
              <p className="text-xs text-zinc-400 dark:text-zinc-500">Hesap şifrenizi değiştirin</p>
            </div>
          </div>

          <form onSubmit={handlePasswordSubmit} className="space-y-4">
            <div>
              <label htmlFor="currentPassword" className={labelClass}>Mevcut Şifre</label>
              <PasswordInput
                id="currentPassword"
                value={pwForm.currentPassword}
                onChange={(e) => setPwForm((p) => ({ ...p, currentPassword: e.target.value }))}
                placeholder="Mevcut şifrenizi girin"
                disabled={pwSaving}
              />
            </div>

            <div>
              <label htmlFor="newPassword" className={labelClass}>Yeni Şifre</label>
              <PasswordInput
                id="newPassword"
                value={pwForm.newPassword}
                onChange={(e) => setPwForm((p) => ({ ...p, newPassword: e.target.value }))}
                placeholder="En az 6 karakter"
                disabled={pwSaving}
              />
            </div>

            <div>
              <label htmlFor="confirmPassword" className={labelClass}>Yeni Şifre Tekrar</label>
              <PasswordInput
                id="confirmPassword"
                value={pwForm.confirmPassword}
                onChange={(e) => setPwForm((p) => ({ ...p, confirmPassword: e.target.value }))}
                placeholder="Yeni şifrenizi tekrar girin"
                disabled={pwSaving}
              />
              {pwForm.confirmPassword && pwForm.newPassword !== pwForm.confirmPassword && (
                <p className="mt-1.5 text-xs text-rose-500">Şifreler eşleşmiyor.</p>
              )}
            </div>

            <div className="pt-1">
              <button
                type="submit"
                disabled={pwSaving}
                className="flex items-center gap-2 bg-violet-600 hover:bg-violet-700 active:bg-violet-800 disabled:opacity-50 disabled:cursor-not-allowed text-white px-4 py-2.5 rounded-lg text-sm font-semibold transition-colors"
              >
                {pwSaving
                  ? <div className="w-3.5 h-3.5 border-2 border-white/40 border-t-white rounded-full animate-spin" />
                  : <Lock size={14} strokeWidth={2.5} />
                }
                {pwSaving ? 'Değiştiriliyor...' : 'Şifreyi Değiştir'}
              </button>
            </div>
          </form>
        </div>

        {/* ── Veri Yönetimi ── */}
        <div className="bg-white dark:bg-zinc-900 rounded-xl shadow-sm border border-zinc-100 dark:border-zinc-800 p-6">
          <div className="flex items-center gap-2.5 mb-5">
            <div className="w-8 h-8 rounded-lg bg-emerald-50 dark:bg-emerald-950 flex items-center justify-center">
              <FileDown size={15} className="text-emerald-600 dark:text-emerald-400" strokeWidth={2} />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">Veri Yönetimi</h2>
              <p className="text-xs text-zinc-400 dark:text-zinc-500">Raporlama ve veri işlemleri</p>
            </div>
          </div>

          <div className="space-y-3">
            <button
              onClick={handleDownloadPdf}
              disabled={pdfLoading}
              className="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium
                bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300
                hover:bg-emerald-100 dark:hover:bg-emerald-950/70
                active:scale-[0.97] transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {pdfLoading
                ? <div className="w-4 h-4 border-2 border-emerald-500/40 border-t-emerald-500 rounded-full animate-spin" />
                : <FileDown size={16} strokeWidth={2} />
              }
              {pdfLoading ? 'Rapor hazırlanıyor...' : 'PDF Rapor İndir'}
            </button>

            <button
              onClick={handleDeleteAll}
              disabled={deleteLoading}
              className="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium
                bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300
                hover:bg-rose-100 dark:hover:bg-rose-950/70
                active:scale-[0.97] transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {deleteLoading
                ? <div className="w-4 h-4 border-2 border-rose-500/40 border-t-rose-500 rounded-full animate-spin" />
                : <Trash2 size={16} strokeWidth={2} />
              }
              {deleteLoading ? 'Siliniyor...' : 'Tüm Veriyi Sil'}
            </button>
          </div>
        </div>

      </div>
    </div>
  );
}
