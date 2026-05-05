import { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { verifyEmail } from '../api/client';
import { useTheme } from '../context/ThemeContext';
import { Sun, Moon, CheckCircle, XCircle, Loader } from 'lucide-react';

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const { theme, toggleTheme } = useTheme();
  const [status, setStatus] = useState('loading'); // loading | success | error
  const [message, setMessage] = useState('');

  useEffect(() => {
    const token = searchParams.get('token');
    if (!token) {
      setStatus('error');
      setMessage('Doğrulama linki geçersiz.');
      return;
    }

    verifyEmail(token)
      .then(() => setStatus('success'))
      .catch((err) => {
        setStatus('error');
        setMessage(
          err?.response?.data?.message || 'Doğrulama başarısız. Link geçersiz veya süresi dolmuş.'
        );
      });
  }, [searchParams]);

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 flex items-center justify-center p-4 transition-colors">

      <button
        onClick={toggleTheme}
        className="fixed top-4 right-4 p-2 rounded-lg text-zinc-400 dark:text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
        title={theme === 'dark' ? 'Açık tema' : 'Koyu tema'}
      >
        {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
      </button>

      <div className="w-full max-w-sm">
        <div className="bg-white dark:bg-zinc-900 rounded-2xl shadow-sm border border-zinc-100 dark:border-zinc-800 px-8 py-10 text-center">

          {status === 'loading' && (
            <>
              <Loader size={40} className="mx-auto text-indigo-500 animate-spin mb-4" />
              <p className="text-sm text-zinc-500 dark:text-zinc-400">Doğrulanıyor...</p>
            </>
          )}

          {status === 'success' && (
            <>
              <div className="flex justify-center mb-4">
                <div className="p-4 bg-emerald-50 dark:bg-emerald-950/40 rounded-full">
                  <CheckCircle size={32} className="text-emerald-500" />
                </div>
              </div>
              <h2 className="text-xl font-bold text-zinc-900 dark:text-zinc-100 mb-2">
                E-posta doğrulandı!
              </h2>
              <p className="text-sm text-zinc-500 dark:text-zinc-400 mb-6">
                Hesabınız aktif. Artık giriş yapabilirsiniz.
              </p>
              <Link
                to="/login"
                className="inline-block bg-indigo-600 hover:bg-indigo-700 text-white font-semibold py-2.5 px-6 rounded-lg text-sm transition-colors"
              >
                Giriş Yap
              </Link>
            </>
          )}

          {status === 'error' && (
            <>
              <div className="flex justify-center mb-4">
                <div className="p-4 bg-rose-50 dark:bg-rose-950/40 rounded-full">
                  <XCircle size={32} className="text-rose-500" />
                </div>
              </div>
              <h2 className="text-xl font-bold text-zinc-900 dark:text-zinc-100 mb-2">
                Doğrulama başarısız
              </h2>
              <p className="text-sm text-zinc-500 dark:text-zinc-400 mb-6">{message}</p>
              <Link
                to="/register"
                className="text-sm text-indigo-600 dark:text-indigo-400 font-semibold hover:underline"
              >
                Yeniden kayıt ol
              </Link>
            </>
          )}

        </div>
      </div>
    </div>
  );
}
