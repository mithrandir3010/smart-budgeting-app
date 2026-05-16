import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Wallet, FileText, Zap, BarChart2, ShieldCheck,
  CreditCard, Bell, Menu, X, ArrowRight, ChevronRight,
} from 'lucide-react';

// ── Dashboard Mockup ──────────────────────────────────────────────────────────
function DashboardMockup() {
  const bars = [40, 65, 50, 80, 60, 90, 45];
  const transactions = [
    { name: 'Netflix', cat: 'Abonelik', amount: '-₺189', color: 'text-rose-400' },
    { name: 'Migros', cat: 'Market',    amount: '-₺634', color: 'text-amber-400' },
    { name: 'Spotify', cat: 'Abonelik', amount: '-₺59',  color: 'text-rose-400' },
    { name: 'BP Akaryakıt', cat: 'Ulaşım', amount: '-₺420', color: 'text-blue-400' },
  ];

  return (
    <div className="relative w-full max-w-[480px] mx-auto select-none">
      {/* Glow arkası */}
      <div className="absolute inset-0 -z-10 blur-3xl opacity-30"
        style={{ background: 'radial-gradient(ellipse at 60% 40%, #6366f1 0%, #10b981 60%, transparent 80%)' }} />

      {/* Pencere çerçevesi */}
      <div className="rounded-2xl border border-white/10 bg-zinc-900/80 backdrop-blur-xl overflow-hidden shadow-2xl">
        {/* Tarayıcı bar */}
        <div className="flex items-center gap-1.5 px-4 py-3 border-b border-white/[0.06] bg-zinc-950/50">
          <span className="w-3 h-3 rounded-full bg-rose-500/70" />
          <span className="w-3 h-3 rounded-full bg-amber-500/70" />
          <span className="w-3 h-3 rounded-full bg-emerald-500/70" />
          <div className="flex-1 mx-4 h-5 rounded-md bg-white/[0.04] flex items-center justify-center">
            <span className="text-[10px] text-zinc-600">smartbudgetr.com/dashboard</span>
          </div>
        </div>

        {/* Dashboard içeriği */}
        <div className="p-4 space-y-3">
          {/* Üst stat kartları */}
          <div className="grid grid-cols-3 gap-2">
            {[
              { label: 'Toplam Harcama', value: '₺8.240', delta: '+12%', up: true },
              { label: 'Abonelikler',    value: '₺892',   delta: '6 adet', up: false },
              { label: 'Bütçe Kalan',   value: '₺1.760', delta: '%17',    up: true },
            ].map((s) => (
              <div key={s.label} className="rounded-xl bg-white/[0.04] border border-white/[0.06] p-2.5">
                <p className="text-[9px] text-zinc-500 mb-1 truncate">{s.label}</p>
                <p className="text-sm font-bold text-zinc-100">{s.value}</p>
                <p className={`text-[9px] mt-0.5 ${s.up ? 'text-emerald-400' : 'text-amber-400'}`}>{s.delta}</p>
              </div>
            ))}
          </div>

          {/* Bar chart */}
          <div className="rounded-xl bg-white/[0.03] border border-white/[0.06] p-3">
            <p className="text-[10px] text-zinc-500 mb-3">Aylık Harcama Trendi</p>
            <div className="flex items-end gap-1.5 h-16">
              {bars.map((h, i) => (
                <div key={i} className="flex-1 flex flex-col items-center gap-1">
                  <div
                    className="w-full rounded-sm"
                    style={{
                      height: `${h}%`,
                      background: i === 5
                        ? 'linear-gradient(to top, #6366f1, #818cf8)'
                        : 'rgba(99,102,241,0.25)',
                    }}
                  />
                </div>
              ))}
            </div>
            <div className="flex justify-between mt-1.5">
              {['Kas', 'Ara', 'Oca', 'Şub', 'Mar', 'Nis', 'May'].map((m) => (
                <span key={m} className="text-[8px] text-zinc-600 flex-1 text-center">{m}</span>
              ))}
            </div>
          </div>

          {/* Son işlemler */}
          <div className="rounded-xl bg-white/[0.03] border border-white/[0.06] p-3">
            <p className="text-[10px] text-zinc-500 mb-2">Son İşlemler</p>
            <div className="space-y-2">
              {transactions.map((t) => (
                <div key={t.name} className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="w-6 h-6 rounded-lg bg-white/[0.06] flex items-center justify-center">
                      <CreditCard size={10} className="text-zinc-400" />
                    </div>
                    <div>
                      <p className="text-[10px] font-medium text-zinc-300">{t.name}</p>
                      <p className="text-[8px] text-zinc-600">{t.cat}</p>
                    </div>
                  </div>
                  <span className={`text-[10px] font-semibold ${t.color}`}>{t.amount}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Alt blur gradient — merak uyandırıcı */}
        <div className="absolute bottom-0 left-0 right-0 h-24 pointer-events-none"
          style={{ background: 'linear-gradient(to top, rgba(9,9,11,0.95) 0%, transparent 100%)' }} />
      </div>

      {/* Floating badge */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.8, duration: 0.5 }}
        className="absolute -bottom-4 -left-4 flex items-center gap-2 bg-emerald-500/10 border border-emerald-500/25 rounded-full px-3 py-1.5 backdrop-blur-md"
      >
        <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
        <span className="text-xs text-emerald-300 font-medium">6 abonelik tespit edildi</span>
      </motion.div>
    </div>
  );
}

// ── Landing Page ──────────────────────────────────────────────────────────────
const fadeUp = (delay = 0) => ({
  initial:    { opacity: 0, y: 24 },
  whileInView:{ opacity: 1, y: 0 },
  viewport:   { once: true },
  transition: { duration: 0.55, delay, ease: [0.23, 1, 0.32, 1] },
});

export default function LandingPage() {
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 overflow-x-hidden">

      {/* ── HEADER ─────────────────────────────────────────────────────────── */}
      <header className="fixed top-0 left-0 right-0 z-50 border-b border-white/[0.06] bg-zinc-950/80 backdrop-blur-xl">
        <div className="max-w-6xl mx-auto px-5 h-16 flex items-center justify-between">

          {/* Logo */}
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-emerald-500/15 flex items-center justify-center">
              <Wallet size={16} className="text-emerald-400" strokeWidth={2} />
            </div>
            <span className="font-bold text-zinc-100 tracking-tight">Smart Budget</span>
          </div>

          {/* Desktop nav */}
          <div className="hidden sm:flex items-center gap-3">
            <Link
              to="/login"
              className="px-4 py-2 text-sm font-medium text-zinc-400 hover:text-zinc-100 transition-colors"
            >
              Giriş Yap
            </Link>
            <Link
              to="/register"
              className="px-4 py-2 rounded-xl text-sm font-semibold text-white transition-all hover:opacity-90"
              style={{ background: 'linear-gradient(135deg, #6366F1 0%, #4F46E5 100%)' }}
            >
              Kayıt Ol
            </Link>
          </div>

          {/* Mobile hamburger */}
          <button
            onClick={() => setMenuOpen((v) => !v)}
            className="sm:hidden p-2 rounded-lg text-zinc-400 hover:text-zinc-100 hover:bg-white/[0.06] transition-all"
          >
            {menuOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>

        {/* Mobile menu */}
        <AnimatePresence>
          {menuOpen && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              className="sm:hidden border-t border-white/[0.06] bg-zinc-950/95 px-5 py-4 flex flex-col gap-3"
            >
              <Link
                to="/login"
                onClick={() => setMenuOpen(false)}
                className="py-2.5 text-sm font-medium text-zinc-300 hover:text-white transition-colors"
              >
                Giriş Yap
              </Link>
              <Link
                to="/register"
                onClick={() => setMenuOpen(false)}
                className="py-3 rounded-xl text-sm font-semibold text-white text-center transition-all"
                style={{ background: 'linear-gradient(135deg, #6366F1 0%, #4F46E5 100%)' }}
              >
                Kayıt Ol — Ücretsiz
              </Link>
            </motion.div>
          )}
        </AnimatePresence>
      </header>

      {/* ── HERO ───────────────────────────────────────────────────────────── */}
      <section className="pt-32 pb-20 px-5 max-w-6xl mx-auto">
        <div className="flex flex-col lg:flex-row items-center gap-16">

          {/* Sol — metin */}
          <div className="flex-1 text-center lg:text-left">
            <motion.div {...fadeUp(0)}>
              <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-indigo-500/10 border border-indigo-500/20 text-indigo-300 mb-6">
                <Zap size={11} className="text-indigo-400" />
                Yapay Zeka Destekli Bütçe Analizi
              </span>
            </motion.div>

            <motion.h1 {...fadeUp(0.08)}
              className="text-4xl sm:text-5xl lg:text-[52px] font-extrabold leading-[1.1] tracking-tight text-zinc-50 mb-5"
            >
              Banka Şifreni{' '}
              <span className="bg-gradient-to-r from-indigo-400 to-emerald-400 bg-clip-text text-transparent">
                Paylaşmadan
              </span>
              , Harcamalarını Saniyeler İçinde Analiz Et.
            </motion.h1>

            <motion.p {...fadeUp(0.15)}
              className="text-base sm:text-lg text-zinc-400 leading-relaxed mb-9 max-w-xl mx-auto lg:mx-0"
            >
              Kredi kartı ekstreni yükle; yapay zeka ve akıllı algoritmalarla bu ay en çok nereye harcadığını, gözden kaçan aboneliklerini ve taksitlerini anında gör.
            </motion.p>

            <motion.div {...fadeUp(0.22)} className="flex flex-col sm:flex-row gap-3 justify-center lg:justify-start">
              <motion.button
                whileHover={{ scale: 1.02, boxShadow: '0 0 40px rgba(99,102,241,0.5)' }}
                whileTap={{ scale: 0.98 }}
                onClick={() => navigate('/register')}
                className="flex items-center justify-center gap-2 px-8 py-4 rounded-2xl text-white font-bold text-base transition-all"
                style={{ background: 'linear-gradient(135deg, #6366F1 0%, #4F46E5 100%)', boxShadow: '0 8px 32px rgba(99,102,241,0.35)' }}
              >
                Hemen Ücretsiz Başla
                <ArrowRight size={17} />
              </motion.button>
              <button
                onClick={() => navigate('/login')}
                className="px-6 py-4 rounded-2xl text-sm font-semibold text-zinc-300 border border-white/10 hover:border-white/20 hover:text-white transition-all"
              >
                Giriş Yap
              </button>
            </motion.div>

            <motion.p {...fadeUp(0.28)} className="mt-5 text-xs text-zinc-600">
              Kredi kartı gerekmez · Banka bağlantısı yok · Veriler şifreli
            </motion.p>
          </div>

          {/* Sağ — mockup */}
          <motion.div
            className="flex-1 w-full"
            initial={{ opacity: 0, x: 40 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.7, delay: 0.2, ease: [0.23, 1, 0.32, 1] }}
          >
            <DashboardMockup />
          </motion.div>
        </div>
      </section>

      {/* ── HOW IT WORKS ───────────────────────────────────────────────────── */}
      <section className="py-20 px-5 border-t border-white/[0.05]">
        <div className="max-w-5xl mx-auto">
          <motion.div {...fadeUp(0)} className="text-center mb-14">
            <h2 className="text-3xl sm:text-4xl font-bold text-zinc-50 mb-3">3 Adımda Nasıl Çalışır?</h2>
            <p className="text-zinc-500 text-base">Ekstrenden içgörüye — 60 saniyeden az sürer.</p>
          </motion.div>

          {/* Web: yatay — Mobil: dikey timeline */}
          <div className="relative">
            {/* Bağlantı çizgisi (sadece desktop) */}
            <div className="hidden lg:block absolute top-10 left-[17%] right-[17%] h-px bg-gradient-to-r from-transparent via-indigo-500/30 to-transparent" />

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-0 lg:gap-8">
              {[
                {
                  step: '01',
                  icon: FileText,
                  title: 'Ekstreni Yükle',
                  desc: 'Bankandan indirdiğin PDF ekstreni sürükle-bırak ile yükle. Hiçbir banka bilgisi gerekmez.',
                  color: 'text-indigo-400',
                  bg: 'bg-indigo-500/10 border-indigo-500/20',
                },
                {
                  step: '02',
                  icon: Zap,
                  title: 'Yapay Zeka Analiz Etsin',
                  desc: 'Gelişmiş algoritmalar harcamalarını saniyeler içinde kategorize eder, abonelik ve taksitleri tespit eder.',
                  color: 'text-emerald-400',
                  bg: 'bg-emerald-500/10 border-emerald-500/20',
                },
                {
                  step: '03',
                  icon: BarChart2,
                  title: 'Bütçeni Yönet',
                  desc: 'Dinamik grafikler ve akıllı önerilerle harcamalarını takip et, tasarruf hedefleri belirle.',
                  color: 'text-violet-400',
                  bg: 'bg-violet-500/10 border-violet-500/20',
                },
              ].map(({ step, icon: Icon, title, desc, color, bg }, i) => (
                <motion.div key={step} {...fadeUp(i * 0.1)}
                  className="relative flex lg:flex-col items-start lg:items-center gap-5 lg:gap-4 lg:text-center p-6"
                >
                  {/* Mobil timeline çizgisi */}
                  {i < 2 && (
                    <div className="lg:hidden absolute left-10 top-[72px] bottom-0 w-px bg-gradient-to-b from-white/10 to-transparent" />
                  )}

                  <div className={`relative flex-shrink-0 w-14 h-14 rounded-2xl border flex items-center justify-center ${bg}`}>
                    <Icon size={22} className={color} strokeWidth={1.8} />
                    <span className="absolute -top-2 -right-2 w-5 h-5 rounded-full bg-zinc-900 border border-white/10 text-[10px] font-bold text-zinc-400 flex items-center justify-center">
                      {i + 1}
                    </span>
                  </div>

                  <div>
                    <h3 className="text-base font-bold text-zinc-100 mb-1.5">{title}</h3>
                    <p className="text-sm text-zinc-500 leading-relaxed">{desc}</p>
                  </div>
                </motion.div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* ── VALUE PROPOSITION ──────────────────────────────────────────────── */}
      <section className="py-20 px-5 border-t border-white/[0.05]">
        <div className="max-w-5xl mx-auto">
          <motion.div {...fadeUp(0)} className="text-center mb-14">
            <h2 className="text-3xl sm:text-4xl font-bold text-zinc-50 mb-3">Ne Kazanacaksın?</h2>
            <p className="text-zinc-500 text-base">Tek bir ekstre yüklemesiyle finansal farkındalığını artır.</p>
          </motion.div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
            {[
              {
                icon: BarChart2,
                iconColor: 'text-indigo-400',
                iconBg: 'bg-indigo-500/10 border-indigo-500/20',
                title: 'Harcama Analizi',
                desc: 'Bu ay en çok markete mi, eğlenceye mi harcadın? Kategorilere göre pasta grafiğiyle anlık görünürlük.',
                badge: 'Grafikler',
                badgeColor: 'bg-indigo-500/10 text-indigo-300 border-indigo-500/20',
              },
              {
                icon: Bell,
                iconColor: 'text-rose-400',
                iconBg: 'bg-rose-500/10 border-rose-500/20',
                title: 'Abonelik Tespiti',
                desc: 'Farkında olmadan her ay kartından çekilen Netflix, Spotify veya unutulan bulut servisleri otomatik yakalanır.',
                badge: 'Otomatik',
                badgeColor: 'bg-rose-500/10 text-rose-300 border-rose-500/20',
              },
              {
                icon: ShieldCheck,
                iconColor: 'text-emerald-400',
                iconBg: 'bg-emerald-500/10 border-emerald-500/20',
                title: 'Gizlilik & Güvenlik',
                desc: 'Banka şifren yok, API entegrasyonu yok. Verilerin tamamen izole ve şifreli olarak senin kontrolünde.',
                badge: 'Güvenli',
                badgeColor: 'bg-emerald-500/10 text-emerald-300 border-emerald-500/20',
              },
            ].map(({ icon: Icon, iconColor, iconBg, title, desc, badge, badgeColor }, i) => (
              <motion.div key={title} {...fadeUp(i * 0.1)}
                className="group relative rounded-2xl border border-white/[0.08] bg-white/[0.03] p-6 hover:border-white/[0.14] hover:bg-white/[0.05] transition-all duration-300"
              >
                <div className={`w-12 h-12 rounded-xl border flex items-center justify-center mb-4 ${iconBg}`}>
                  <Icon size={20} className={iconColor} strokeWidth={1.8} />
                </div>
                <div className="flex items-center gap-2 mb-2">
                  <h3 className="text-base font-bold text-zinc-100">{title}</h3>
                  <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full border ${badgeColor}`}>
                    {badge}
                  </span>
                </div>
                <p className="text-sm text-zinc-500 leading-relaxed">{desc}</p>
              </motion.div>
            ))}
          </div>

          {/* CTA alt */}
          <motion.div {...fadeUp(0.3)} className="text-center mt-14">
            <motion.button
              whileHover={{ scale: 1.02, boxShadow: '0 0 40px rgba(99,102,241,0.45)' }}
              whileTap={{ scale: 0.98 }}
              onClick={() => navigate('/register')}
              className="inline-flex items-center gap-2 px-8 py-4 rounded-2xl text-white font-bold text-base transition-all"
              style={{ background: 'linear-gradient(135deg, #6366F1 0%, #4F46E5 100%)', boxShadow: '0 8px 32px rgba(99,102,241,0.3)' }}
            >
              Ücretsiz Hesap Oluştur
              <ChevronRight size={17} />
            </motion.button>
            <p className="mt-3 text-xs text-zinc-600">Kayıt olman 30 saniye sürer.</p>
          </motion.div>
        </div>
      </section>

      {/* ── FOOTER ─────────────────────────────────────────────────────────── */}
      <footer className="border-t border-white/[0.05] py-8 px-5">
        <div className="max-w-5xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4 text-sm text-zinc-600">
          <div className="flex items-center gap-2">
            <div className="w-6 h-6 rounded-lg bg-emerald-500/15 flex items-center justify-center">
              <Wallet size={12} className="text-emerald-400" strokeWidth={2} />
            </div>
            <span>© 2025 Smart Budget. Tüm hakları saklıdır.</span>
          </div>
          <div className="flex items-center gap-5">
            <Link to="/register" className="hover:text-zinc-300 transition-colors">Gizlilik Politikası</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
