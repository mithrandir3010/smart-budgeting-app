import { useState } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Eye, EyeOff, AlertCircle, CheckCircle2 } from 'lucide-react';
import { toast } from 'sonner';
import { resetPassword } from '../api/client';
import VisionPanel, { BrandMark } from '../components/auth/VisionPanel';
import { inputCls, labelCls } from '../components/shared';

export default function ResetPasswordPage() {
  const [searchParams]  = useSearchParams();
  const navigate        = useNavigate();
  const token           = searchParams.get('token') || '';

  const [form,     setForm]     = useState({ newPassword: '', confirmPassword: '' });
  const [error,    setError]    = useState(null);
  const [done,     setDone]     = useState(false);
  const [loading,  setLoading]  = useState(false);
  const [showPass, setShowPass] = useState(false);

  const handleChange = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (form.newPassword !== form.confirmPassword) {
      setError('Şifreler eşleşmiyor.');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await resetPassword(token, form.newPassword, form.confirmPassword);
      setDone(true);
      toast.success('Şifreniz başarıyla sıfırlandı.');
      setTimeout(() => navigate('/login'), 2500);
    } catch (err) {
      setError(err.response?.data?.message || 'Geçersiz veya süresi dolmuş link.');
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-zinc-50 dark:bg-[#050507]">
        <div className="text-center space-y-4">
          <p className="text-zinc-500 text-sm">Geçersiz şifre sıfırlama linki.</p>
          <Link to="/forgot-password" className="text-indigo-400 text-sm hover:underline">
            Yeni link talep et
          </Link>
        </div>
      </div>
    );
  }

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
              Yeni Şifre Belirle
            </h1>
            <p className="text-[13px] text-zinc-500">
              En az 8 karakter, bir büyük harf ve bir rakam içermelidir.
            </p>
          </div>

          <AnimatePresence mode="wait">
            {done ? (
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
                  Şifreniz başarıyla sıfırlandı.
                  <br />Giriş sayfasına yönlendiriliyorsunuz…
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
                  <label className={labelCls}>Yeni Şifre</label>
                  <div className="relative">
                    <input
                      name="newPassword"
                      type={showPass ? 'text' : 'password'}
                      value={form.newPassword}
                      onChange={handleChange}
                      placeholder="••••••••"
                      required
                      minLength={8}
                      autoComplete="new-password"
                      className={inputCls + ' pr-11'}
                    />
                    <button
                      type="button"
                      onClick={() => setShowPass((v) => !v)}
                      tabIndex={-1}
                      className="absolute right-3.5 top-1/2 -translate-y-1/2 text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-300 transition-colors"
                    >
                      {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
                    </button>
                  </div>
                </div>

                <div>
                  <label className={labelCls}>Şifre Tekrar</label>
                  <input
                    name="confirmPassword"
                    type={showPass ? 'text' : 'password'}
                    value={form.confirmPassword}
                    onChange={handleChange}
                    placeholder="••••••••"
                    required
                    autoComplete="new-password"
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
                        Sıfırlanıyor…
                      </motion.span>
                    ) : (
                      <motion.span key="idle" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                        Şifremi Sıfırla
                      </motion.span>
                    )}
                  </AnimatePresence>
                </motion.button>
              </motion.form>
            )}
          </AnimatePresence>
        </motion.div>
      </div>
    </div>
  );
}
