import { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { motion, AnimatePresence } from 'framer-motion';
import { uploadStatement } from '../api/client';
import {
  ArrowLeft, FileText, Upload, BarChart2,
  CheckCircle, AlertTriangle, AlertCircle, Cpu,
} from 'lucide-react';
import { BrandMark } from '../components/auth/VisionPanel';
import { useTheme } from '../context/ThemeContext';

// ── Constants ─────────────────────────────────────────────────────────────────
const LOADING_STEPS = [
  'PDF okunuyor...',
  'İşlemler analiz ediliyor...',
  'Kategoriler belirleniyor...',
  'AI önerileri hazırlanıyor...',
  'Sonuçlar kayıt ediliyor...',
];

const HOW_IT_WORKS = [
  { icon: '🔍', title: 'Otomatik Tespit',    desc: 'Aynı dosyayı tekrar yüklerseniz sistem bunu anında tespit eder.' },
  { icon: '📅', title: 'Dönem Kontrolü',     desc: 'Aynı dönemi kapsayan başka bir dosya yüklemek isterseniz uyarı alırsınız.' },
  { icon: '🤖', title: 'Akıllı Etiketleme', desc: 'Netflix, Spotify gibi abonelikler otomatik olarak işaretlenir.' },
];

function extractMessage(data) {
  if (!data) return 'Yükleme sırasında bir hata oluştu.';
  if (typeof data === 'string') return data;
  if (typeof data === 'object' && data.message) return data.message;
  return JSON.stringify(data);
}

// ── AI Loading Overlay ────────────────────────────────────────────────────────
function AIOverlay({ stepIndex, isDark }) {
  const progress = Math.round(((stepIndex + 1) / LOADING_STEPS.length) * 100);

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex flex-col items-center justify-center"
      style={{
        background: isDark ? 'rgba(5,5,7,0.88)' : 'rgba(250,250,252,0.92)',
        backdropFilter: 'blur(20px)',
      }}
    >
      {/* Scanner card */}
      <div className="glass-card relative w-64 p-6 overflow-hidden mb-8">
        {/* Scanner sweep line */}
        <motion.div
          className="absolute inset-x-0 h-px pointer-events-none"
          style={{ background: 'linear-gradient(90deg, transparent, rgba(16,185,129,0.9), transparent)' }}
          animate={{ y: [0, 140, 0] }}
          transition={{ duration: 2.8, repeat: Infinity, ease: 'linear' }}
        />
        {/* Horizontal scan glow */}
        <motion.div
          className="absolute inset-x-0 h-8 pointer-events-none"
          style={{ background: 'linear-gradient(180deg, transparent, rgba(16,185,129,0.06), transparent)' }}
          animate={{ y: [-32, 140, -32] }}
          transition={{ duration: 2.8, repeat: Infinity, ease: 'linear' }}
        />

        {/* Icon */}
        <div className="flex justify-center mb-5">
          <div className="relative">
            <div className="w-16 h-16 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center">
              <Cpu size={28} className="text-indigo-500 dark:text-indigo-400" strokeWidth={1.5} />
            </div>
            {/* Pulse ring */}
            <motion.div
              className="absolute inset-0 rounded-2xl border border-indigo-400/40"
              animate={{ scale: [1, 1.25, 1], opacity: [0.6, 0, 0.6] }}
              transition={{ duration: 2, repeat: Infinity, ease: 'easeOut' }}
            />
          </div>
        </div>

        {/* Step text */}
        <AnimatePresence mode="wait">
          <motion.p
            key={stepIndex}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.3 }}
            className="text-sm font-semibold text-zinc-800 dark:text-zinc-100 text-center mb-1"
          >
            {LOADING_STEPS[stepIndex]}
          </motion.p>
        </AnimatePresence>
        <p className="text-[11px] text-zinc-500 dark:text-zinc-600 text-center">Bu işlem birkaç saniye sürebilir</p>
      </div>

      {/* Progress bar */}
      <div className="w-64 space-y-2">
        <div className="flex justify-between text-[10px] text-zinc-500">
          <span>İşleniyor</span>
          <span className="tabular-nums text-emerald-600 dark:text-emerald-500">{progress}%</span>
        </div>
        <div className="h-1 rounded-full bg-zinc-200 dark:bg-white/[0.06] overflow-hidden">
          <motion.div
            className="h-full rounded-full"
            style={{ background: 'linear-gradient(90deg, #10b981, #6366f1)' }}
            animate={{ width: `${progress}%` }}
            transition={{ duration: 0.6, ease: [0.23, 1, 0.32, 1] }}
          />
        </div>

        {/* Step dots */}
        <div className="flex justify-center gap-1.5 pt-1">
          {LOADING_STEPS.map((_, i) => (
            <motion.div
              key={i}
              animate={{
                width:      i === stepIndex ? 20 : 6,
                background: i <= stepIndex ? '#10b981' : isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)',
              }}
              transition={{ duration: 0.3 }}
              className="h-1.5 rounded-full"
            />
          ))}
        </div>
      </div>
    </motion.div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────
export default function UploadPage() {
  const navigate = useNavigate();
  const { theme } = useTheme();
  const isDark = theme === 'dark';

  const [file,         setFile]         = useState(null);
  const [status,       setStatus]       = useState(null);
  const [loading,      setLoading]      = useState(false);
  const [countdown,    setCountdown]    = useState(null);
  const [stepIndex,    setStepIndex]    = useState(0);
  const [isDragging,   setIsDragging]   = useState(false);

  const timerRef    = useRef(null);
  const stepTimerRef = useRef(null);
  const inputRef    = useRef(null);

  // Auto-redirect after success
  useEffect(() => {
    if (status?.type === 'success') {
      setCountdown(3);
      timerRef.current = setInterval(() => {
        setCountdown((prev) => {
          if (prev <= 1) { clearInterval(timerRef.current); navigate('/'); return 0; }
          return prev - 1;
        });
      }, 1000);
    }
    return () => clearInterval(timerRef.current);
  }, [status?.type, navigate]);

  // ── File handlers ────────────────────────────────────────────────────────────
  const acceptFile = (selected) => {
    if (!selected) return;
    if (selected.type !== 'application/pdf') {
      toast.error('Lütfen yalnızca PDF dosyası seçin.');
      return;
    }
    setFile(selected);
    setStatus(null);
  };

  const handleFileChange = (e) => acceptFile(e.target.files[0]);

  const handleDragEnter = (e) => { e.preventDefault(); e.stopPropagation(); setIsDragging(true); };
  const handleDragOver  = (e) => { e.preventDefault(); e.stopPropagation(); setIsDragging(true); };
  const handleDragLeave = (e) => { e.preventDefault(); e.stopPropagation(); setIsDragging(false); };
  const handleDrop      = (e) => {
    e.preventDefault(); e.stopPropagation();
    setIsDragging(false);
    acceptFile(e.dataTransfer.files[0]);
  };

  // ── Submit ───────────────────────────────────────────────────────────────────
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!file) { toast.warning('Lütfen bir PDF dosyası seçin.'); return; }

    setLoading(true);
    setStepIndex(0);
    setStatus(null);

    stepTimerRef.current = setInterval(() => {
      setStepIndex((prev) => (prev + 1) % LOADING_STEPS.length);
    }, 1800);

    try {
      const res = await uploadStatement(file);
      const msg = typeof res.data === 'string' ? res.data : 'Ekstre başarıyla yüklendi.';
      toast.success(msg);
      setStatus({ type: 'success', message: msg });
      setFile(null);
      if (inputRef.current) inputRef.current.value = '';
    } catch (err) {
      const s   = err.response?.status;
      const msg = extractMessage(err.response?.data);
      if (s === 409) {
        setStatus({ type: 'duplicate', message: msg });
      } else if (s !== 401 && s !== 413 && !(s >= 500)) {
        toast.error(msg);
        setStatus({ type: 'error', message: msg });
      } else {
        setStatus({ type: 'error', message: msg });
      }
    } finally {
      clearInterval(stepTimerRef.current);
      setLoading(false);
    }
  };

  // ── Status config ────────────────────────────────────────────────────────────
  const STATUS_CFG = {
    success:   { icon: CheckCircle,   border: 'border-emerald-300 dark:border-emerald-500/25', bg: 'bg-emerald-50 dark:bg-emerald-500/10', text: 'text-emerald-700 dark:text-emerald-400' },
    error:     { icon: AlertCircle,   border: 'border-rose-300 dark:border-rose-500/25',       bg: 'bg-rose-50 dark:bg-rose-500/10',       text: 'text-rose-600 dark:text-rose-400' },
    duplicate: { icon: AlertTriangle, border: 'border-amber-300 dark:border-amber-500/25',     bg: 'bg-amber-50 dark:bg-amber-500/10',     text: 'text-amber-700 dark:text-amber-400' },
  };

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-[#050507] mesh-bg flex justify-center px-4 py-10">

      {/* AI Loading overlay */}
      <AnimatePresence>
        {loading && <AIOverlay stepIndex={stepIndex} isDark={isDark} />}
      </AnimatePresence>

      <div className="w-full max-w-lg">

        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
          className="flex items-center justify-between mb-8"
        >
          <Link
            to="/"
            className="flex items-center gap-1.5 text-sm font-medium text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-200 transition-colors"
          >
            <ArrowLeft size={15} strokeWidth={2} />
            Dashboard
          </Link>
          <div className="flex items-center gap-2">
            <BrandMark size={24} />
            <span className="font-bold text-zinc-900 dark:text-zinc-100 text-base tracking-tight">Smart Budget</span>
          </div>
        </motion.div>

        {/* Title */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45, delay: 0.05 }}
          className="mb-7"
        >
          <h1 className="text-2xl font-bold text-zinc-900 dark:text-zinc-100 tracking-tight mb-1.5">Ekstre Yükle</h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-600 leading-relaxed">
            Banka ekstrenizi PDF formatında yükleyin — harcamalar yapay zeka ile otomatik analiz edilir.
          </p>
        </motion.div>

        <form onSubmit={handleSubmit} className="space-y-4">

          {/* ── Drop zone ── */}
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.1 }}
          >
            <motion.label
              htmlFor="pdf-input"
              onDragEnter={handleDragEnter}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              animate={{
                scale:           isDragging ? 1.015 : 1,
                backgroundColor: isDragging
                  ? 'rgba(16,185,129,0.06)'
                  : isDark
                  ? (file ? 'rgba(99,102,241,0.04)' : 'rgba(255,255,255,0.025)')
                  : (file ? 'rgba(99,102,241,0.03)' : 'rgba(255,255,255,0.9)'),
                borderColor: isDragging
                  ? 'rgba(16,185,129,0.7)'
                  : file
                  ? 'rgba(99,102,241,0.4)'
                  : isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.15)',
                boxShadow: isDragging
                  ? '0 0 40px rgba(16,185,129,0.18), inset 0 0 40px rgba(16,185,129,0.04)'
                  : file
                  ? '0 0 20px rgba(99,102,241,0.12)'
                  : '0 0 0px transparent',
              }}
              transition={{ duration: 0.2 }}
              className="flex flex-col items-center justify-center border-2 border-dashed rounded-2xl p-12 cursor-pointer text-center backdrop-blur-md"
              style={{ minHeight: 220 }}
            >
              <input
                ref={inputRef}
                id="pdf-input"
                type="file"
                accept="application/pdf"
                onChange={handleFileChange}
                className="hidden"
              />

              <AnimatePresence mode="wait">
                {file ? (
                  <motion.div
                    key="file"
                    initial={{ opacity: 0, scale: 0.85 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.9 }}
                    transition={{ duration: 0.3, ease: [0.23, 1, 0.32, 1] }}
                    className="flex flex-col items-center"
                  >
                    <div className="w-14 h-14 rounded-2xl bg-indigo-500/15 border border-indigo-500/25 flex items-center justify-center mb-4">
                      <FileText size={26} className="text-indigo-500 dark:text-indigo-400" strokeWidth={1.6} />
                    </div>
                    <p className="font-semibold text-sm text-zinc-800 dark:text-zinc-200 break-all max-w-xs mb-1.5">
                      {file.name}
                    </p>
                    <p className="text-xs text-zinc-500 dark:text-zinc-600">
                      {(file.size / 1024).toFixed(1)} KB · PDF
                    </p>
                    <p className="text-xs text-indigo-500 dark:text-indigo-400 mt-3 font-medium">
                      Değiştirmek için tıklayın
                    </p>
                  </motion.div>
                ) : (
                  <motion.div
                    key="empty"
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.9 }}
                    transition={{ duration: 0.25 }}
                    className="flex flex-col items-center"
                  >
                    <motion.div
                      animate={isDragging
                        ? { scale: 1.15, rotate: -5 }
                        : { scale: 1, rotate: 0 }
                      }
                      transition={{ duration: 0.2 }}
                      className="w-14 h-14 rounded-2xl bg-zinc-100 dark:bg-white/[0.05] border border-zinc-200 dark:border-white/[0.08] flex items-center justify-center mb-4"
                    >
                      <Upload
                        size={24}
                        strokeWidth={1.6}
                        className={isDragging ? 'text-emerald-500' : 'text-zinc-400 dark:text-zinc-500'}
                      />
                    </motion.div>
                    <p className="font-semibold text-sm text-zinc-700 dark:text-zinc-300 mb-1.5">
                      {isDragging ? 'Bırakın!' : 'PDF dosyasını sürükleyin'}
                    </p>
                    <p className="text-xs text-zinc-500 dark:text-zinc-600">
                      veya <span className="text-indigo-500 dark:text-indigo-400 font-medium">dosya seçmek için tıklayın</span>
                    </p>
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.label>
          </motion.div>

          {/* ── Status banner ── */}
          <AnimatePresence>
            {status && (() => {
              const cfg = STATUS_CFG[status.type] || STATUS_CFG.error;
              const Icon = cfg.icon;
              return (
                <motion.div
                  key="status"
                  initial={{ opacity: 0, y: -8, height: 0 }}
                  animate={{ opacity: 1, y: 0, height: 'auto' }}
                  exit={{ opacity: 0, y: -8, height: 0 }}
                  transition={{ duration: 0.3 }}
                  className={`rounded-xl border px-4 py-3.5 text-sm ${cfg.border} ${cfg.bg}`}
                >
                  <div className="flex items-start gap-3">
                    <Icon size={16} className={`${cfg.text} flex-shrink-0 mt-0.5`} />
                    <div className="flex-1">
                      <p className={`${cfg.text} font-medium leading-snug`}>{status.message}</p>
                      {status.type === 'success' && countdown !== null && (
                        <div className="flex items-center justify-between gap-3 mt-2.5">
                          <span className="text-xs text-zinc-500">
                            {countdown}s içinde Dashboard'a yönlendiriliyorsun...
                          </span>
                          <button
                            type="button"
                            onClick={() => { clearInterval(timerRef.current); navigate('/'); }}
                            className="flex items-center gap-1.5 text-xs font-semibold text-emerald-600 dark:text-emerald-400 hover:text-emerald-700 dark:hover:text-emerald-300 flex-shrink-0 transition-colors"
                          >
                            <BarChart2 size={12} />
                            Hemen Git
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                </motion.div>
              );
            })()}
          </AnimatePresence>

          {/* ── Submit button ── */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: 0.18 }}
          >
            <motion.button
              type="submit"
              disabled={loading || !file}
              whileHover={!loading && file ? { scale: 1.01, boxShadow: '0 0 28px rgba(99,102,241,0.4)' } : {}}
              whileTap={!loading && file ? { scale: 0.98 } : {}}
              className="w-full text-white font-semibold py-3 rounded-xl text-sm transition-all disabled:opacity-40 disabled:cursor-not-allowed"
              style={{ background: 'linear-gradient(135deg, #6366f1, #8b5cf6)', boxShadow: '0 0 20px rgba(99,102,241,0.25)' }}
            >
              {loading ? 'İşleniyor...' : 'Ekstreyi Yükle ve Analiz Et'}
            </motion.button>
          </motion.div>
        </form>

        {/* ── How it works ── */}
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.28 }}
          className="mt-8"
        >
          <p className="text-[10px] font-semibold text-zinc-500 uppercase tracking-widest mb-3 px-0.5">
            Nasıl çalışır?
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {HOW_IT_WORKS.map(({ icon, title, desc }, i) => (
              <motion.div
                key={title}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.4, delay: 0.32 + i * 0.07 }}
                whileHover={{ y: -2, transition: { duration: 0.2 } }}
                className="glass-card p-4"
              >
                <span className="text-xl mb-2.5 block">{icon}</span>
                <p className="text-xs font-semibold text-zinc-700 dark:text-zinc-300 mb-1">{title}</p>
                <p className="text-[11px] text-zinc-500 dark:text-zinc-600 leading-relaxed">{desc}</p>
              </motion.div>
            ))}
          </div>
        </motion.div>

      </div>
    </div>
  );
}
