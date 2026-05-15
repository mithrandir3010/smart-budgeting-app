import { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { CheckCircle, XCircle } from 'lucide-react';
import { verifyEmail } from '../api/client';
import { BrandMark } from '../components/auth/VisionPanel';

function StatusIcon({ type }) {
  const isSuccess = type === 'success';
  const color     = isSuccess ? { bg: 'rgba(16,185,129,0.12)', border: 'rgba(16,185,129,0.25)', icon: 'text-emerald-400' }
                               : { bg: 'rgba(244,63,94,0.12)',  border: 'rgba(244,63,94,0.25)',  icon: 'text-rose-400' };
  const Icon = isSuccess ? CheckCircle : XCircle;

  return (
    <motion.div
      initial={{ scale: 0, rotate: -20 }}
      animate={{ scale: 1, rotate: 0 }}
      transition={{ duration: 0.5, ease: [0.23, 1, 0.32, 1] }}
      className="relative w-20 h-20 mx-auto mb-6 flex items-center justify-center rounded-2xl"
      style={{ background: color.bg, border: `1px solid ${color.border}` }}
    >
      <Icon size={36} className={color.icon} strokeWidth={1.5} />

      {/* Pulse ring */}
      <motion.div
        className="absolute inset-0 rounded-2xl"
        style={{ border: `1px solid ${color.border}` }}
        animate={{ scale: [1, 1.3, 1], opacity: [0.6, 0, 0.6] }}
        transition={{ duration: 2.5, repeat: Infinity, ease: 'easeOut', delay: 0.3 }}
      />
    </motion.div>
  );
}

function Spinner() {
  return (
    <div className="relative w-20 h-20 mx-auto mb-6">
      <div className="absolute inset-0 rounded-full border-2 border-white/[0.06]" />
      <div className="absolute inset-0 rounded-full border-2 border-transparent border-t-indigo-500 animate-spin" style={{ animationDuration: '1s' }} />
      <div className="absolute inset-[7px] rounded-full border-2 border-transparent border-t-indigo-400/50 animate-spin" style={{ animationDuration: '1.6s', animationDirection: 'reverse' }} />
      <div className="absolute inset-[18px] rounded-full flex items-center justify-center"
        style={{ background: 'rgba(99,102,241,0.1)' }}>
        <motion.div
          animate={{ opacity: [0.4, 1, 0.4] }}
          transition={{ duration: 1.5, repeat: Infinity }}
          className="w-2 h-2 rounded-full bg-indigo-400"
        />
      </div>
    </div>
  );
}

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const [status,  setStatus]  = useState('loading');
  const [message, setMessage] = useState('');

  useEffect(() => {
    const token = searchParams.get('token');
    if (!token) {
      setStatus('error');
      setMessage('Doğrulama linki geçersiz veya eksik.');
      return;
    }
    verifyEmail(token)
      .then(() => setStatus('success'))
      .catch((err) => {
        setStatus('error');
        setMessage(err?.response?.data?.message || 'Doğrulama başarısız. Link geçersiz veya süresi dolmuş.');
      });
  }, [searchParams]);

  return (
    <div
      className="min-h-screen flex flex-col items-center justify-center p-4 bg-zinc-50 dark:bg-[#050507]"
    >
      {/* Brand */}
      <motion.div
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="flex items-center gap-2 mb-10"
      >
        <BrandMark size={28} />
        <span className="font-bold text-zinc-900 dark:text-zinc-100 text-base tracking-tight">Smart Budget</span>
      </motion.div>

      {/* Card */}
      <motion.div
        initial={{ opacity: 0, y: 20, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.5, delay: 0.05, ease: [0.23, 1, 0.32, 1] }}
        className="w-full max-w-sm glass-card px-8 py-10 text-center"
      >
        <AnimatePresence mode="wait">

          {/* Loading */}
          {status === 'loading' && (
            <motion.div
              key="loading"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0, scale: 0.9 }}
              transition={{ duration: 0.3 }}
            >
              <Spinner />
              <p className="text-sm font-semibold text-zinc-700 dark:text-zinc-300 mb-1">Doğrulanıyor</p>
              <p className="text-xs text-zinc-600">Lütfen bekleyin…</p>
            </motion.div>
          )}

          {/* Success */}
          {status === 'success' && (
            <motion.div
              key="success"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.4 }}
            >
              <StatusIcon type="success" />

              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.2, duration: 0.4 }}
              >
                <h2 className="text-xl font-bold text-zinc-900 dark:text-zinc-100 mb-2">E-posta doğrulandı!</h2>
                <p className="text-sm text-zinc-500 mb-7">
                  Hesabınız aktif. Artık giriş yapabilirsiniz.
                </p>
                <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.97 }}>
                  <Link
                    to="/login"
                    className="inline-block text-white font-semibold py-2.5 px-7 rounded-xl text-sm transition-all"
                    style={{
                      background: 'linear-gradient(135deg, #6366F1, #4F46E5)',
                      boxShadow:  '0 6px 24px rgba(99,102,241,0.35)',
                    }}
                  >
                    Giriş Yap
                  </Link>
                </motion.div>
              </motion.div>
            </motion.div>
          )}

          {/* Error */}
          {status === 'error' && (
            <motion.div
              key="error"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.4 }}
            >
              <StatusIcon type="error" />

              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.2, duration: 0.4 }}
              >
                <h2 className="text-xl font-bold text-zinc-900 dark:text-zinc-100 mb-2">Doğrulama başarısız</h2>
                <p className="text-sm text-zinc-500 mb-7 leading-relaxed">{message}</p>
                <Link
                  to="/register"
                  className="text-sm text-indigo-400 font-semibold hover:text-indigo-300 transition-colors"
                >
                  Yeniden kayıt ol →
                </Link>
              </motion.div>
            </motion.div>
          )}

        </AnimatePresence>
      </motion.div>
    </div>
  );
}
