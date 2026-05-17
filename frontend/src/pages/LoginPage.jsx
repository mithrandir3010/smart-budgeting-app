import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Eye, EyeOff, AlertCircle } from 'lucide-react';
import { login, saveAuth } from '../api/client';
import VisionPanel, { BrandMark } from '../components/auth/VisionPanel';
import { inputCls, labelCls } from '../components/shared';

export default function LoginPage() {
  const navigate = useNavigate();
  const [form,     setForm]     = useState({ username: '', password: '' });
  const [error,    setError]    = useState(null);
  const [loading,  setLoading]  = useState(false);
  const [showPass, setShowPass] = useState(false);

  const handleChange = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await login(form);
      const { username, email, fullName, role } = res.data;
      saveAuth({ username, email, fullName, role });
      navigate('/dashboard');
    } catch (err) {
      setError(err.response?.data?.message || 'Giriş yapılamadı.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex">

      {/* ── Left: Vision Panel ── */}
      <VisionPanel variant="login" />

      {/* ── Right: Form Panel ── */}
      <div
        className="flex-1 flex flex-col items-center justify-center px-8 py-16 min-h-screen bg-zinc-50 dark:bg-[#050507]"
      >
        {/* Mobile brand */}
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
          {/* Heading */}
          <div className="mb-9">
            <h1 className="text-[28px] font-bold text-zinc-900 dark:text-zinc-100 tracking-tight leading-tight mb-2">
              Tekrar hoş geldiniz
            </h1>
            <p className="text-[13px] text-zinc-500">
              Hesabınıza giriş yapın ve finanslarınızı yönetin.
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">

            <div>
              <label className={labelCls}>Kullanıcı Adı</label>
              <input
                name="username"
                type="text"
                value={form.username}
                onChange={handleChange}
                placeholder="kullaniciadi"
                required
                autoComplete="username"
                className={inputCls}
              />
            </div>

            <div>
              <label className={labelCls}>Şifre</label>
              <div className="relative">
                <input
                  name="password"
                  type={showPass ? 'text' : 'password'}
                  value={form.password}
                  onChange={handleChange}
                  placeholder="••••••••"
                  required
                  autoComplete="current-password"
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

            {/* Error */}
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

            {/* Submit */}
            <motion.button
              type="submit"
              disabled={loading}
              whileHover={!loading ? { scale: 1.01, boxShadow: '0 0 32px rgba(99,102,241,0.45)' } : {}}
              whileTap={!loading ? { scale: 0.98 } : {}}
              className="w-full py-3 rounded-xl text-white font-semibold text-sm mt-1 disabled:opacity-60 disabled:cursor-not-allowed overflow-hidden"
              style={{
                background:  'linear-gradient(135deg, #6366F1 0%, #4F46E5 100%)',
                boxShadow:   loading ? 'none' : '0 6px 24px rgba(99,102,241,0.35)',
                transition:  'box-shadow 0.2s',
              }}
            >
              <AnimatePresence mode="wait">
                {loading ? (
                  <motion.span
                    key="loading"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    className="flex items-center justify-center gap-2"
                  >
                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    Giriş yapılıyor…
                  </motion.span>
                ) : (
                  <motion.span
                    key="idle"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                  >
                    Giriş Yap
                  </motion.span>
                )}
              </AnimatePresence>
            </motion.button>
          </form>

          <div className="flex flex-col items-center gap-3 mt-8">
            <p className="text-center text-[13px] text-zinc-600">
              Hesabınız yok mu?{' '}
              <Link
                to="/register"
                className="text-indigo-400 font-semibold hover:text-indigo-300 transition-colors"
              >
                Ücretsiz Kayıt Ol
              </Link>
            </p>
            <Link
              to="/forgot-password"
              className="text-[13px] text-zinc-500 hover:text-indigo-400 transition-colors"
            >
              Şifremi unuttum
            </Link>
          </div>
        </motion.div>
      </div>
    </div>
  );
}
