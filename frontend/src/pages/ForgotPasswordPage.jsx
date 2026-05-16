import { useState } from 'react';
import { Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { AlertCircle, CheckCircle2, ArrowLeft } from 'lucide-react';
import { forgotPassword } from '../api/client';
import VisionPanel, { BrandMark } from '../components/auth/VisionPanel';
import { inputCls, labelCls } from '../components/shared';

export default function ForgotPasswordPage() {
  const [email,   setEmail]   = useState('');
  const [error,   setError]   = useState(null);
  const [sent,    setSent]    = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await forgotPassword(email);
      setSent(true);
    } catch {
      setError('Bir hata oluştu. Lütfen daha sonra tekrar deneyin.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex">
      <VisionPanel variant="login" />

      <div className="flex-1 flex flex-col items-center justify-center px-8 py-16 min-h-screen bg-zinc-50 dark:bg-[#050507]">
        <div className="flex lg:hidden items-center gap-2 mb-10">
          <BrandMark size={30} />
          <span className="font-bold text-zinc-900 dark:text-zinc-100 text-lg tracking-tight">Smart Budget</span>
        </div>

        <motion.div
          className="w-full max-w-[360px]"
          initial={{ opacity: 0, x: 24 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.45, ease: [0.23, 1, 0.32, 1] }}
        >
          <div className="mb-9">
            <h1 className="text-[28px] font-bold text-zinc-900 dark:text-zinc-100 tracking-tight leading-tight mb-2">
              Şifremi Unuttum
            </h1>
            <p className="text-[13px] text-zinc-500">
              E-posta adresinizi girin, size sıfırlama linki gönderelim.
            </p>
          </div>

          <AnimatePresence mode="wait">
            {sent ? (
              <motion.div
                key="success"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex flex-col items-center gap-4 text-center py-4"
              >
                <div className="w-14 h-14 rounded-full bg-emerald-500/10 flex items-center justify-center">
                  <CheckCircle2 size={28} className="text-emerald-400" />
                </div>
                <p className="text-[14px] text-zinc-600 dark:text-zinc-400">
                  Eğer bu e-posta kayıtlıysa, şifre sıfırlama linki gönderildi.
                  <br /><br />
                  Gelen kutunuzu kontrol edin (spam klasörünü de kontrol edin).
                </p>
              </motion.div>
            ) : (
              <motion.form
                key="form"
                onSubmit={handleSubmit}
                className="space-y-5"
                initial={{ opacity: 1 }}
                exit={{ opacity: 0 }}
              >
                <div>
                  <label className={labelCls}>E-posta Adresi</label>
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="ornek@email.com"
                    required
                    autoComplete="email"
                    className={inputCls}
                  />
                </div>

                <AnimatePresence>
                  {error && (
                    <motion.div
                      key="error"
                      initial={{ opacity: 0, y: -6, height: 0 }}
                      animate={{ opacity: 1, y: 0, height: 'auto' }}
                      exit={{ opacity: 0, y: -6, height: 0 }}
                      transition={{ duration: 0.25 }}
                      className="flex items-start gap-2.5 bg-rose-500/10 border border-rose-500/20 rounded-xl px-4 py-3 text-[13px] text-rose-400"
                    >
                      <AlertCircle size={15} className="shrink-0 mt-0.5" />
                      {error}
                    </motion.div>
                  )}
                </AnimatePresence>

                <motion.button
                  type="submit"
                  disabled={loading}
                  whileHover={!loading ? { scale: 1.01, boxShadow: '0 0 32px rgba(99,102,241,0.45)' } : {}}
                  whileTap={!loading ? { scale: 0.98 } : {}}
                  className="w-full py-3 rounded-xl text-white font-semibold text-sm mt-1 disabled:opacity-60 disabled:cursor-not-allowed"
                  style={{
                    background: 'linear-gradient(135deg, #6366F1 0%, #4F46E5 100%)',
                    boxShadow: loading ? 'none' : '0 6px 24px rgba(99,102,241,0.35)',
                  }}
                >
                  <AnimatePresence mode="wait">
                    {loading ? (
                      <motion.span key="loading" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
                        className="flex items-center justify-center gap-2">
                        <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                        Gönderiliyor…
                      </motion.span>
                    ) : (
                      <motion.span key="idle" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                        Sıfırlama Linki Gönder
                      </motion.span>
                    )}
                  </AnimatePresence>
                </motion.button>
              </motion.form>
            )}
          </AnimatePresence>

          <Link
            to="/login"
            className="flex items-center justify-center gap-1.5 text-[13px] text-zinc-500 hover:text-indigo-400 transition-colors mt-8"
          >
            <ArrowLeft size={13} />
            Giriş sayfasına dön
          </Link>
        </motion.div>
      </div>
    </div>
  );
}
