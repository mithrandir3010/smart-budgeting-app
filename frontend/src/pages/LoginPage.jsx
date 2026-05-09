import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { login, saveAuth } from '../api/client';
import { TrendingDown, Zap, CreditCard, Eye, EyeOff } from 'lucide-react';

const KEYFRAMES = `
  @keyframes floatA {
    0%, 100% { transform: translateY(0px) rotate(-1.5deg); }
    50%       { transform: translateY(-12px) rotate(-1.5deg); }
  }
  @keyframes floatB {
    0%, 100% { transform: translateY(0px) rotate(2deg); }
    50%       { transform: translateY(-16px) rotate(2deg); }
  }
  @keyframes floatC {
    0%, 100% { transform: translateY(0px) rotate(-0.5deg); }
    50%       { transform: translateY(-10px) rotate(-0.5deg); }
  }
`;

function BrandMark({ size = 36 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 36 36" fill="none">
      <rect width="36" height="36" rx="10" fill="#6366F1" />
      <rect x="7"  y="22" width="5" height="8"  rx="1.5" fill="white" fillOpacity="0.55" />
      <rect x="14" y="15" width="5" height="15" rx="1.5" fill="white" fillOpacity="0.8"  />
      <rect x="21" y="8"  width="5" height="22" rx="1.5" fill="white" />
      <circle cx="29" cy="7" r="3" fill="#A5F3FC" />
    </svg>
  );
}

const CARD_BASE = {
  background:    'rgba(255,255,255,0.055)',
  backdropFilter:'blur(20px)',
  WebkitBackdropFilter: 'blur(20px)',
  borderColor:   'rgba(255,255,255,0.09)',
};

function InsightCard({ style, children, className = '' }) {
  return (
    <div
      className={`absolute rounded-2xl p-4 border ${className}`}
      style={{ ...CARD_BASE, ...style }}
    >
      {children}
    </div>
  );
}

function IconBadge({ color, children }) {
  return (
    <div
      className="w-7 h-7 rounded-lg flex items-center justify-center shrink-0"
      style={{ background: color }}
    >
      {children}
    </div>
  );
}

export default function LoginPage() {
  const navigate = useNavigate();
  const [form, setForm]       = useState({ username: '', password: '' });
  const [error, setError]     = useState(null);
  const [loading, setLoading] = useState(false);
  const [showPass, setShowPass] = useState(false);

  const handleChange = (e) =>
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await login(form);
      const { username, email, fullName } = res.data;
      saveAuth({ username, email, fullName });
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.message || 'Giriş yapılamadı.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <style>{KEYFRAMES}</style>

      <div className="min-h-screen flex">

        {/* ── Left Panel ──────────────────────────────────────────────────── */}
        <div
          className="hidden lg:flex lg:w-[52%] relative overflow-hidden flex-col justify-between p-14"
          style={{
            background: 'linear-gradient(160deg, #06091A 0%, #0E1530 60%, #0A1020 100%)',
            backgroundImage: `
              radial-gradient(rgba(255,255,255,0.028) 1px, transparent 1px)
            `,
            backgroundSize: '28px 28px',
          }}
        >
          {/* Atmospheric gradient blobs */}
          <div
            className="absolute top-[-15%] right-[-8%] w-[480px] h-[480px] rounded-full blur-3xl pointer-events-none"
            style={{ background: 'radial-gradient(circle, rgba(99,102,241,0.28), transparent 70%)' }}
          />
          <div
            className="absolute bottom-[-10%] left-[-12%] w-[400px] h-[400px] rounded-full blur-3xl pointer-events-none"
            style={{ background: 'radial-gradient(circle, rgba(124,58,237,0.22), transparent 70%)' }}
          />
          <div
            className="absolute top-[45%] left-[15%] w-[300px] h-[300px] rounded-full blur-3xl pointer-events-none"
            style={{ background: 'radial-gradient(circle, rgba(14,165,233,0.1), transparent 70%)' }}
          />

          {/* Brand */}
          <div className="relative z-10 flex items-center gap-3">
            <BrandMark />
            <span className="text-white font-bold text-xl tracking-tight">Smart Budget</span>
          </div>

          {/* Floating cards */}
          <div className="relative z-10 flex-1 flex items-center py-8">
            <div className="relative w-full" style={{ height: '340px' }}>

              {/* Card 1 — Monthly spending */}
              <InsightCard
                style={{ left: 0, top: 0, width: '224px', animation: 'floatA 5.5s ease-in-out infinite' }}
              >
                <div className="flex items-center gap-2 mb-3">
                  <IconBadge color="rgba(99,102,241,0.3)">
                    <TrendingDown size={13} className="text-indigo-400" />
                  </IconBadge>
                  <span className="text-xs font-medium" style={{ color: 'rgba(255,255,255,0.45)' }}>
                    Bu Ay Harcama
                  </span>
                </div>
                <div className="text-[26px] font-bold text-white leading-none mb-0.5">₺8.240</div>
                <div className="text-xs mb-3" style={{ color: 'rgba(255,255,255,0.28)' }}>Mayıs 2026</div>
                <div className="flex items-end gap-1 h-8">
                  {[35, 60, 42, 78, 50, 90, 55].map((h, i) => (
                    <div
                      key={i}
                      className="flex-1 rounded-sm"
                      style={{
                        height: `${h * 0.36}px`,
                        background: i === 5
                          ? 'linear-gradient(180deg, #818CF8, #6366F1)'
                          : 'rgba(99,102,241,0.22)',
                      }}
                    />
                  ))}
                </div>
              </InsightCard>

              {/* Card 2 — Subscriptions */}
              <InsightCard
                style={{ right: 0, top: 20, width: '210px', animation: 'floatB 6.5s ease-in-out infinite', animationDelay: '1s' }}
              >
                <div className="flex items-center gap-2 mb-3">
                  <IconBadge color="rgba(16,185,129,0.2)">
                    <Zap size={13} className="text-emerald-400" />
                  </IconBadge>
                  <span className="text-xs font-medium" style={{ color: 'rgba(255,255,255,0.45)' }}>
                    Abonelikler
                  </span>
                </div>
                <div className="flex items-end gap-2 mb-3">
                  <span className="text-[26px] font-bold text-white leading-none">3</span>
                  <span className="text-xs mb-0.5" style={{ color: 'rgba(255,255,255,0.35)' }}>aktif</span>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {['Netflix', 'Spotify', 'iCloud'].map((s) => (
                    <span
                      key={s}
                      className="text-[10px] px-2 py-0.5 rounded-full"
                      style={{ background: 'rgba(255,255,255,0.08)', color: 'rgba(255,255,255,0.45)' }}
                    >
                      {s}
                    </span>
                  ))}
                </div>
              </InsightCard>

              {/* Card 3 — Top category */}
              <InsightCard
                style={{ left: 24, bottom: 0, width: '240px', animation: 'floatC 7s ease-in-out infinite', animationDelay: '2s' }}
              >
                <div className="flex items-center gap-2 mb-3">
                  <IconBadge color="rgba(245,158,11,0.2)">
                    <CreditCard size={13} className="text-amber-400" />
                  </IconBadge>
                  <span className="text-xs font-medium" style={{ color: 'rgba(255,255,255,0.45)' }}>
                    En Yüksek Kategori
                  </span>
                </div>
                <div className="flex items-center justify-between mb-2.5">
                  <span className="text-sm font-semibold text-white">Alışveriş</span>
                  <span className="text-xs font-bold" style={{ color: '#FBBF24' }}>%34</span>
                </div>
                <div className="w-full h-1.5 rounded-full" style={{ background: 'rgba(255,255,255,0.08)' }}>
                  <div
                    className="h-1.5 rounded-full"
                    style={{ width: '34%', background: 'linear-gradient(90deg, #F59E0B, #FCD34D)' }}
                  />
                </div>
                <div className="flex justify-between mt-2">
                  <span className="text-[10px]" style={{ color: 'rgba(255,255,255,0.25)' }}>₺0</span>
                  <span className="text-[10px]" style={{ color: 'rgba(255,255,255,0.25)' }}>₺8.240</span>
                </div>
              </InsightCard>
            </div>
          </div>

          {/* Tagline */}
          <div className="relative z-10">
            <p className="text-sm leading-relaxed" style={{ color: 'rgba(255,255,255,0.35)' }}>
              Harcamalarınızı anlayın,<br />geleceğinizi planlayın.
            </p>
          </div>
        </div>

        {/* ── Right Panel — Form ───────────────────────────────────────────── */}
        <div className="flex-1 flex flex-col items-center justify-center bg-white px-8 py-16 min-h-screen">

          {/* Mobile-only brand */}
          <div className="flex lg:hidden items-center gap-2 mb-10">
            <BrandMark size={30} />
            <span className="font-bold text-zinc-800 text-lg tracking-tight">Smart Budget</span>
          </div>

          <div className="w-full max-w-[360px]">

            {/* Heading */}
            <div className="mb-10">
              <h1 className="text-[28px] font-bold text-zinc-900 tracking-tight leading-tight mb-2">
                Tekrar hoş geldiniz
              </h1>
              <p className="text-[13px] text-zinc-400">
                Hesabınıza giriş yapın ve finanslarınızı yönetin.
              </p>
            </div>

            <form onSubmit={handleSubmit} className="space-y-5">

              {/* Username */}
              <div>
                <label className="block text-[11px] font-semibold text-zinc-400 uppercase tracking-widest mb-1.5">
                  Kullanıcı Adı
                </label>
                <input
                  name="username"
                  type="text"
                  value={form.username}
                  onChange={handleChange}
                  placeholder="kullanici_adi"
                  required
                  autoComplete="username"
                  className="w-full px-4 py-3 rounded-xl border border-zinc-200 bg-zinc-50 text-zinc-900 placeholder-zinc-300 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent focus:bg-white transition-all"
                />
              </div>

              {/* Password */}
              <div>
                <label className="block text-[11px] font-semibold text-zinc-400 uppercase tracking-widest mb-1.5">
                  Şifre
                </label>
                <div className="relative">
                  <input
                    name="password"
                    type={showPass ? 'text' : 'password'}
                    value={form.password}
                    onChange={handleChange}
                    placeholder="••••••••"
                    required
                    autoComplete="current-password"
                    className="w-full px-4 py-3 pr-11 rounded-xl border border-zinc-200 bg-zinc-50 text-zinc-900 placeholder-zinc-300 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent focus:bg-white transition-all"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPass((v) => !v)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-600 transition-colors"
                    tabIndex={-1}
                  >
                    {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              {/* Error */}
              {error && (
                <div className="bg-rose-50 text-rose-600 border border-rose-100 rounded-xl px-4 py-3 text-[13px]">
                  {error}
                </div>
              )}

              {/* Submit */}
              <button
                type="submit"
                disabled={loading}
                className="w-full py-3 rounded-xl text-white font-semibold text-sm transition-all active:scale-[0.98] disabled:opacity-60 mt-1"
                style={{
                  background: 'linear-gradient(135deg, #6366F1 0%, #4F46E5 100%)',
                  boxShadow: loading ? 'none' : '0 6px 24px rgba(99,102,241,0.38)',
                }}
              >
                {loading ? 'Giriş yapılıyor…' : 'Giriş Yap'}
              </button>
            </form>

            {/* Register link */}
            <p className="text-center text-[13px] text-zinc-400 mt-8">
              Hesabınız yok mu?{' '}
              <Link
                to="/register"
                className="text-indigo-600 font-semibold hover:text-indigo-700 transition-colors"
              >
                Ücretsiz Kayıt Ol
              </Link>
            </p>
          </div>
        </div>
      </div>
    </>
  );
}
