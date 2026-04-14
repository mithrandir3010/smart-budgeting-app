import { useState } from 'react';
import { Link } from 'react-router-dom';
import { uploadStatement } from '../api/client';
import { useTheme } from '../context/ThemeContext';
import { ArrowLeft, Sun, Moon, FileText, Upload } from 'lucide-react';

function extractMessage(data) {
  if (!data) return 'Yükleme sırasında bir hata oluştu.';
  if (typeof data === 'string') return data;
  if (typeof data === 'object' && data.message) return data.message;
  return JSON.stringify(data);
}

export default function UploadPage() {
  const { theme, toggleTheme } = useTheme();
  const [file, setFile] = useState(null);
  const [status, setStatus] = useState(null); // { type: 'success'|'error'|'duplicate', message }
  const [loading, setLoading] = useState(false);

  const handleFileChange = (e) => {
    const selected = e.target.files[0];
    if (selected && selected.type !== 'application/pdf') {
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
      setStatus({ type: 'error', message: 'Lütfen bir PDF dosyası seçin.' });
      return;
    }
    setLoading(true);
    setStatus(null);
    try {
      const res = await uploadStatement(file);
      setStatus({ type: 'success', message: res.data });
      setFile(null);
      e.target.reset();
    } catch (err) {
      const isDuplicate = err.response?.status === 409;
      const msg = extractMessage(err.response?.data);
      setStatus({ type: isDuplicate ? 'duplicate' : 'error', message: msg });
    } finally {
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
                <p className="text-xs text-zinc-400 dark:text-zinc-500">
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
                <span className="font-bold mr-1">{icon}</span>
                {status.message}
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
