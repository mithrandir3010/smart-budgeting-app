import { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { uploadStatement } from '../api/client';
import { useTheme } from '../context/ThemeContext';
import { ArrowLeft, Sun, Moon, FileText, Upload, BarChart2 } from 'lucide-react';

const LOADING_STEPS = [
  'PDF okunuyor...',
  'İşlemler analiz ediliyor...',
  'Kategoriler belirleniyor...',
  'AI önerileri hazırlanıyor...',
  'Sonuçlar kayıt ediliyor...',
];

function extractMessage(data) {
  if (!data) return 'Yükleme sırasında bir hata oluştu.';
  if (typeof data === 'string') return data;
  if (typeof data === 'object' && data.message) return data.message;
  return JSON.stringify(data);
}

export default function UploadPage() {
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  const [file, setFile] = useState(null);
  const [status, setStatus] = useState(null); // { type: 'success'|'error'|'duplicate', message }
  const [loading, setLoading] = useState(false);
  const [countdown, setCountdown] = useState(null); // saniye sayacı
  const [stepIndex, setStepIndex] = useState(0);
  const timerRef = useRef(null);
  const stepTimerRef = useRef(null);

  // Başarılı yüklemeden 3 saniye sonra Dashboard'a yönlendir
  useEffect(() => {
    if (status?.type === 'success') {
      setCountdown(3);
      timerRef.current = setInterval(() => {
        setCountdown((prev) => {
          if (prev <= 1) {
            clearInterval(timerRef.current);
            navigate('/');
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    }
    return () => clearInterval(timerRef.current);
  }, [status?.type, navigate]);

  const handleFileChange = (e) => {
    const selected = e.target.files[0];
    if (selected && selected.type !== 'application/pdf') {
      toast.error('Lütfen yalnızca PDF dosyası seçin.');
      setStatus({ type: 'error', message: 'Lütfen yalnızca PDF dosyası seçin.' });
      setFile(null);
      return;
    }
    setFile(selected || null);
    setStatus(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!file) {
      toast.warning('Lütfen bir PDF dosyası seçin.');
      setStatus({ type: 'error', message: 'Lütfen bir PDF dosyası seçin.' });
      return;
    }
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
      e.target.reset();
    } catch (err) {
      const status = err.response?.status;
      const msg = extractMessage(err.response?.data);
      if (status === 409) {
        // toast handled by axios interceptor; just update inline status
        setStatus({ type: 'duplicate', message: msg });
      } else if (status !== 401 && status !== 413 && !(status >= 500)) {
        // 401/413/5xx already handled by interceptor; show inline for others (400/422/404)
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

  const alertConfig = {
    success:   { cls: 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border-emerald-200 dark:border-emerald-900', icon: '✓' },
    error:     { cls: 'bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-400 border-rose-200 dark:border-rose-900',                   icon: '✕' },
    duplicate: { cls: 'bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border-amber-200 dark:border-amber-900',             icon: '⚠' },
  };

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 flex justify-center px-4 py-10 transition-colors">

      {/* Loading overlay */}
      {loading && (
        <div className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-zinc-950/75 backdrop-blur-sm">
          {/* Spinning rings */}
          <div className="relative w-24 h-24 mb-8">
            <div className="absolute inset-0 rounded-full border-4 border-indigo-500/15" />
            <div className="absolute inset-0 rounded-full border-4 border-transparent border-t-indigo-500 animate-spin" style={{ animationDuration: '1s' }} />
            <div className="absolute inset-[7px] rounded-full border-4 border-transparent border-t-indigo-400/50 animate-spin" style={{ animationDuration: '1.6s', animationDirection: 'reverse' }} />
            <div className="absolute inset-[18px] rounded-full bg-indigo-500/10 flex items-center justify-center">
              <BarChart2 size={18} className="text-indigo-400" strokeWidth={1.8} />
            </div>
          </div>

          {/* Step text with fade key */}
          <div className="text-center space-y-2 px-6">
            <p
              key={stepIndex}
              className="text-white font-semibold text-sm animate-pulse"
            >
              {LOADING_STEPS[stepIndex]}
            </p>
            <p className="text-zinc-500 text-xs">Bu işlem birkaç saniye sürebilir</p>
          </div>

          {/* Progress dots */}
          <div className="flex gap-1.5 mt-6">
            {LOADING_STEPS.map((_, i) => (
              <div
                key={i}
                className={`h-1 rounded-full transition-all duration-500 ${
                  i === stepIndex
                    ? 'w-5 bg-indigo-500'
                    : i < stepIndex
                    ? 'w-1.5 bg-indigo-500/40'
                    : 'w-1.5 bg-zinc-700'
                }`}
              />
            ))}
          </div>
        </div>
      )}

      <button
        onClick={toggleTheme}
        className="fixed top-4 right-4 p-2 rounded-lg text-zinc-400 dark:text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
        title={theme === 'dark' ? 'Açık tema' : 'Koyu tema'}
      >
        {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
      </button>

      <div className="w-full max-w-lg">

        <Link
          to="/"
          className="inline-flex items-center gap-1.5 text-sm font-medium text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 mb-7 transition-colors"
        >
          <ArrowLeft size={15} strokeWidth={2.5} />
          Dashboard'a Dön
        </Link>

        <h1 className="text-2xl font-bold text-zinc-900 dark:text-zinc-100 tracking-tight mb-2">
          Ekstre Yükle
        </h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400 leading-relaxed mb-7">
          Banka ekstrenizi PDF formatında yükleyin. Harcamalar otomatik olarak analiz edilecektir.
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">

          {/* Drop zone */}
          <label className="flex flex-col items-center justify-center border-2 border-dashed border-zinc-300 dark:border-zinc-700 hover:border-indigo-400 dark:hover:border-indigo-500 rounded-xl p-12 bg-white dark:bg-zinc-900 cursor-pointer transition-colors text-center">
            <input
              type="file"
              accept="application/pdf"
              onChange={handleFileChange}
              className="hidden"
            />
            {file ? (
              <>
                <div className="w-12 h-12 bg-indigo-100 dark:bg-indigo-900/40 rounded-xl flex items-center justify-center mb-3">
                  <FileText size={24} className="text-indigo-500" strokeWidth={1.8} />
                </div>
                <p className="font-semibold text-sm text-zinc-800 dark:text-zinc-200 break-all mb-1">
                  {file.name}
                </p>
                <p className="text-xs text-zinc-400 dark:text-zinc-500">
                  {(file.size / 1024).toFixed(1)} KB
                </p>
              </>
            ) : (
              <>
                <div className="w-12 h-12 bg-zinc-100 dark:bg-zinc-800 rounded-xl flex items-center justify-center mb-3">
                  <Upload size={22} className="text-zinc-400" strokeWidth={1.8} />
                </div>
                <p className="font-semibold text-sm text-zinc-700 dark:text-zinc-300 mb-1">
                  PDF dosyasını seçin
                </p>
                <p className="hidden sm:block text-xs text-zinc-400 dark:text-zinc-500">
                  veya buraya sürükleyip bırakın
                </p>
              </>
            )}
          </label>

          {/* Status alert */}
          {status && (() => {
            const { cls, icon } = alertConfig[status.type] || alertConfig.error;
            return (
              <div className={`border rounded-xl px-4 py-3 text-sm leading-relaxed ${cls}`}>
                <div className="flex items-start gap-2">
                  <span className="font-bold flex-shrink-0">{icon}</span>
                  <span className="flex-1">{status.message}</span>
                </div>
                {status.type === 'success' && countdown !== null && (
                  <div className="mt-3 flex items-center justify-between gap-3">
                    <span className="text-xs opacity-75">
                      {countdown}s içinde analiz sayfasına yönlendiriliyorsun...
                    </span>
                    <button
                      onClick={() => { clearInterval(timerRef.current); navigate('/'); }}
                      className="flex items-center gap-1.5 text-xs font-semibold underline underline-offset-2 flex-shrink-0"
                    >
                      <BarChart2 size={13} />
                      Hemen Git
                    </button>
                  </div>
                )}
              </div>
            );
          })()}

          <button
            type="submit"
            disabled={loading || !file}
            className="w-full bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold py-3 rounded-xl text-sm transition-colors"
          >
            {loading ? 'İşleniyor...' : 'Ekstreyi Yükle'}
          </button>
        </form>

        {/* Info box */}
        <div className="mt-8 bg-white dark:bg-zinc-900 border border-zinc-100 dark:border-zinc-800 rounded-xl p-5">
          <p className="text-xs font-bold text-zinc-500 dark:text-zinc-400 uppercase tracking-widest mb-3">
            Nasıl çalışır?
          </p>
          <ul className="space-y-2">
            {[
              'Aynı dosyayı tekrar yüklerseniz sistem bunu otomatik tespit eder.',
              'Aynı dönemi kapsayan farklı bir dosya yüklemeye çalışırsanız uyarı alırsınız.',
              'Abonelikler (Netflix, Spotify vb.) otomatik olarak işaretlenir.',
            ].map((item, i) => (
              <li key={i} className="flex items-start gap-2 text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed">
                <span className="w-4 h-4 bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400 rounded-full flex items-center justify-center font-bold flex-shrink-0 mt-0.5 text-[10px]">
                  {i + 1}
                </span>
                {item}
              </li>
            ))}
          </ul>
        </div>

      </div>
    </div>
  );
}
