import { useState } from 'react';
import { Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { User, AtSign, Mail, Lock, Eye, EyeOff, AlertCircle } from 'lucide-react';
import { register } from '../api/client';
import VisionPanel, { BrandMark } from '../components/auth/VisionPanel';
import { inputCls, labelCls } from '../components/shared';

function IconInput({ icon: Icon, name, type = 'text', value, onChange, placeholder, required, right }) {
  return (
    <div className="relative">
      <Icon size={14} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-zinc-600 pointer-events-none" />
      <input
        name={name}
        type={type}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        required={required}
        className={inputCls + ' pl-10' + (right ? ' pr-11' : '')}
      />
      {right && <div className="absolute right-3.5 top-1/2 -translate-y-1/2">{right}</div>}
    </div>
  );
}

function extractErrorMessage(err) {
  const d = err?.response?.data;
  if (typeof d?.message === 'string' && d.message.trim()) return d.message;
  if (typeof d === 'string' && d.trim()) return d;
  if (!err?.response) return 'Sunucuya bağlanılamadı.';
  return 'Kayıt oluşturulamadı.';
}

export default function RegisterPage() {
  const [form,     setForm]     = useState({ username: '', email: '', password: '', fullName: '' });
  const [error,    setError]    = useState(null);
  const [loading,  setLoading]  = useState(false);
  const [sentTo,   setSentTo]   = useState(null);
  const [showPass, setShowPass] = useState(false);

  const handleChange = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await register(form);
      setSentTo(form.email);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex">

      {/* ── Left: Vision Panel ── */}
      <VisionPanel variant="register" />

      {/* ── Right: Form Panel ── */}
      <div
        className="flex-1 flex flex-col items-center justify-center px-8 py-16 min-h-screen bg-zinc-50 dark:bg-[#050507]"
      >
        {/* Mobile brand */}
        <div className="flex lg:hidden items-center gap-2 mb-10">
          <BrandMark size={30} />
          <span className="font-bold text-zinc-900 dark:text-zinc-100 text-lg tracking-tight">Smart Budget</span>
        </div>

        <AnimatePresence mode="wait">
          {sentTo ? (
            /* ── Success state ── */
            <motion.div
              key="success"
              initial={{ opacity: 0, scale: 0.92 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.4, ease: [0.23, 1, 0.32, 1] }}
              className="w-full max-w-[360px] text-center"
            >
              <div className="glass-card px-8 py-10">
                {/* Icon */}
                <motion.div
                  initial={{ scale: 0, rotate: -15 }}
                  animate={{ scale: 1, rotate: 0 }}
                  transition={{ delay: 0.15, duration: 0.5, ease: [0.23, 1, 0.32, 1] }}
                  className="w-16 h-16 rounded-2xl mx-auto mb-5 flex items-center justify-center"
                  style={{ background: 'rgba(16,185,129,0.12)', border: '1px solid rgba(16,185,129,0.2)' }}
                >
                  <Mail size={30} className="text-emerald-400" strokeWidth={1.5} />
                </motion.div>

                <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.25 }}>
                  <h2 className="text-xl font-bold text-zinc-900 dark:text-zinc-100 mb-2">E-postanızı doğrulayın</h2>
                  <p className="text-sm text-zinc-500 mb-1.5">Doğrulama linki şu adrese gönderildi:</p>
                  <p className="text-sm font-semibold text-indigo-400 mb-5">{sentTo}</p>
                  <p className="text-xs text-zinc-700 mb-6">Gelmezse spam klasörünü kontrol edin. Link 24 saat geçerlidir.</p>
                  <Link
                    to="/login"
                    className="text-sm text-indigo-400 font-semibold hover:text-indigo-300 transition-colors"
                  >
                    Giriş sayfasına dön →
                  </Link>
                </motion.div>
              </div>
            </motion.div>
          ) : (
            /* ── Form ── */
            <motion.div
              key="form"
              className="w-full max-w-[360px]"
              initial={{ opacity: 0, x: -24 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -24 }}
              transition={{ duration: 0.45, ease: [0.23, 1, 0.32, 1] }}
            >
              <div className="mb-8">
                <h1 className="text-[28px] font-bold text-zinc-900 dark:text-zinc-100 tracking-tight leading-tight mb-2">
                  Hesap oluşturun
                </h1>
                <p className="text-[13px] text-zinc-500">
                  Smart Budget'a katılın, finanslarınızı kontrol edin.
                </p>
              </div>

              <form onSubmit={handleSubmit} className="space-y-4">

                <div>
                  <label className={labelCls}>Ad Soyad</label>
                  <IconInput icon={User} name="fullName" value={form.fullName}
                    onChange={handleChange} placeholder="Adınız ve soyadınız" required />
                </div>

                <div>
                  <label className={labelCls}>Kullanıcı Adı</label>
                  <IconInput icon={AtSign} name="username" value={form.username}
                    onChange={handleChange} placeholder="kullaniciadi" required />
                </div>

                <div>
                  <label className={labelCls}>E-posta</label>
                  <IconInput icon={Mail} name="email" type="email" value={form.email}
                    onChange={handleChange} placeholder="ad@ornek.com" required />
                </div>

                <div>
                  <label className={labelCls}>Şifre</label>
                  <IconInput
                    icon={Lock}
                    name="password"
                    type={showPass ? 'text' : 'password'}
                    value={form.password}
                    onChange={handleChange}
                    placeholder="En az 6 karakter"
                    required
                    right={
                      <button type="button" onClick={() => setShowPass((v) => !v)} tabIndex={-1}
                        className="text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-300 transition-colors">
                        {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
                      </button>
                    }
                  />
                </div>

                {/* Error */}
                <AnimatePresence>
                  {error && (
                    <motion.div
                      key="err"
                      initial={{ opacity: 0, y: -6, height: 0 }}
                      animate={{ opacity: 1, y: 0, height: 'auto' }}
                      exit={{ opacity: 0, height: 0 }}
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
                  className="w-full py-3 rounded-xl text-white font-semibold text-sm mt-1 disabled:opacity-60 disabled:cursor-not-allowed"
                  style={{
                    background: 'linear-gradient(135deg, #6366F1 0%, #4F46E5 100%)',
                    boxShadow:  loading ? 'none' : '0 6px 24px rgba(99,102,241,0.35)',
                    transition: 'box-shadow 0.2s',
                  }}
                >
                  <AnimatePresence mode="wait">
                    {loading ? (
                      <motion.span key="loading" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
                        className="flex items-center justify-center gap-2">
                        <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                        Kayıt oluşturuluyor…
                      </motion.span>
                    ) : (
                      <motion.span key="idle" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                        Kayıt Ol
                      </motion.span>
                    )}
                  </AnimatePresence>
                </motion.button>
              </form>

              <p className="text-center text-[13px] text-zinc-600 mt-8">
                Zaten hesabınız var mı?{' '}
                <Link to="/login" className="text-indigo-400 font-semibold hover:text-indigo-300 transition-colors">
                  Giriş Yap
                </Link>
              </p>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
