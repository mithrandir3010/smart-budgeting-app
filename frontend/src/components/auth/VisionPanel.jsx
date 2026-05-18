import { TrendingDown, Zap, CreditCard, ShieldCheck, Target, Sparkles } from 'lucide-react';

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

export function BrandMark({ size = 36 }) {
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
  background:          'rgba(255,255,255,0.055)',
  backdropFilter:      'blur(20px)',
  WebkitBackdropFilter:'blur(20px)',
  borderColor:         'rgba(255,255,255,0.09)',
};

function InsightCard({ style, children }) {
  return (
    <div className="absolute rounded-2xl p-4 border" style={{ ...CARD_BASE, ...style }}>
      {children}
    </div>
  );
}

function IconBadge({ color, children }) {
  return (
    <div className="w-7 h-7 rounded-lg flex items-center justify-center shrink-0" style={{ background: color }}>
      {children}
    </div>
  );
}

// ── Login variant — shows financial snapshot cards ────────────────────────────
function LoginCards() {
  return (
    <div className="relative w-full" style={{ height: 340 }}>

      <InsightCard style={{ left: 0, top: 0, width: 224, animation: 'floatA 5.5s ease-in-out infinite' }}>
        <div className="flex items-center gap-2 mb-3">
          <IconBadge color="rgba(99,102,241,0.3)">
            <TrendingDown size={13} className="text-indigo-400" />
          </IconBadge>
          <span className="text-xs font-medium" style={{ color: 'rgba(255,255,255,0.45)' }}>Bu Ay Harcama</span>
        </div>
        <div className="text-[26px] font-bold text-white leading-none mb-0.5">₺8.240</div>
        <div className="text-xs mb-3" style={{ color: 'rgba(255,255,255,0.28)' }}>Mayıs 2026</div>
        <div className="flex items-end gap-1 h-8">
          {[35, 60, 42, 78, 50, 90, 55].map((h, i) => (
            <div key={i} className="flex-1 rounded-sm" style={{
              height: `${h * 0.36}px`,
              background: i === 5 ? 'linear-gradient(180deg, #818CF8, #6366F1)' : 'rgba(99,102,241,0.22)',
            }} />
          ))}
        </div>
      </InsightCard>

      <InsightCard style={{ right: 0, top: 20, width: 210, animation: 'floatB 6.5s ease-in-out infinite', animationDelay: '1s' }}>
        <div className="flex items-center gap-2 mb-3">
          <IconBadge color="rgba(16,185,129,0.2)">
            <Zap size={13} className="text-emerald-400" />
          </IconBadge>
          <span className="text-xs font-medium" style={{ color: 'rgba(255,255,255,0.45)' }}>Abonelikler</span>
        </div>
        <div className="flex items-end gap-2 mb-3">
          <span className="text-[26px] font-bold text-white leading-none">3</span>
          <span className="text-xs mb-0.5" style={{ color: 'rgba(255,255,255,0.35)' }}>aktif</span>
        </div>
        <div className="flex flex-wrap gap-1.5">
          {['Netflix', 'Spotify', 'iCloud'].map((s) => (
            <span key={s} className="text-[10px] px-2 py-0.5 rounded-full"
              style={{ background: 'rgba(255,255,255,0.08)', color: 'rgba(255,255,255,0.45)' }}>
              {s}
            </span>
          ))}
        </div>
      </InsightCard>

      <InsightCard style={{ left: 24, bottom: 0, width: 240, animation: 'floatC 7s ease-in-out infinite', animationDelay: '2s' }}>
        <div className="flex items-center gap-2 mb-3">
          <IconBadge color="rgba(245,158,11,0.2)">
            <CreditCard size={13} className="text-amber-400" />
          </IconBadge>
          <span className="text-xs font-medium" style={{ color: 'rgba(255,255,255,0.45)' }}>En Yüksek Kategori</span>
        </div>
        <div className="flex items-center justify-between mb-2.5">
          <span className="text-sm font-semibold text-white">Alışveriş</span>
          <span className="text-xs font-bold" style={{ color: '#FBBF24' }}>%34</span>
        </div>
        <div className="w-full h-1.5 rounded-full" style={{ background: 'rgba(255,255,255,0.08)' }}>
          <div className="h-1.5 rounded-full" style={{ width: '34%', background: 'linear-gradient(90deg, #F59E0B, #FCD34D)' }} />
        </div>
      </InsightCard>
    </div>
  );
}

// ── Register variant — shows feature promise cards ────────────────────────────
function RegisterCards() {
  return (
    <div className="relative w-full" style={{ height: 340 }}>

      <InsightCard style={{ left: 0, top: 0, width: 230, animation: 'floatA 5.5s ease-in-out infinite' }}>
        <div className="flex items-center gap-2 mb-3">
          <IconBadge color="rgba(16,185,129,0.2)">
            <Sparkles size={13} className="text-emerald-400" />
          </IconBadge>
          <span className="text-xs font-medium" style={{ color: 'rgba(255,255,255,0.45)' }}>AI Analizi</span>
        </div>
        <p className="text-sm font-semibold text-white mb-1.5">Otomatik Kategorizasyon</p>
        <p className="text-xs leading-relaxed" style={{ color: 'rgba(255,255,255,0.3)' }}>
          Harcamalarınız yapay zeka tarafından saniyeler içinde analiz edilir.
        </p>
        <div className="flex gap-1.5 mt-3 flex-wrap">
          {['Yemek', 'Ulaşım', 'Market', 'Eğlence'].map((cat) => (
            <span key={cat} className="text-[10px] px-2 py-0.5 rounded-full"
              style={{ background: 'rgba(16,185,129,0.15)', color: '#34d399' }}>
              {cat}
            </span>
          ))}
        </div>
      </InsightCard>

      <InsightCard style={{ right: 0, top: 24, width: 210, animation: 'floatB 6.5s ease-in-out infinite', animationDelay: '1s' }}>
        <div className="flex items-center gap-2 mb-3">
          <IconBadge color="rgba(99,102,241,0.25)">
            <Target size={13} className="text-indigo-400" />
          </IconBadge>
          <span className="text-xs font-medium" style={{ color: 'rgba(255,255,255,0.45)' }}>Bütçe Hedefi</span>
        </div>
        <div className="text-[24px] font-bold text-white leading-none mb-1">%78</div>
        <p className="text-xs mb-3" style={{ color: 'rgba(255,255,255,0.28)' }}>Hedef kullanımı</p>
        <div className="w-full h-1.5 rounded-full" style={{ background: 'rgba(255,255,255,0.08)' }}>
          <div className="h-1.5 rounded-full" style={{ width: '78%', background: 'linear-gradient(90deg, #6366f1, #a78bfa)' }} />
        </div>
      </InsightCard>

      <InsightCard style={{ left: 16, bottom: 0, width: 238, animation: 'floatC 7s ease-in-out infinite', animationDelay: '2s' }}>
        <div className="flex items-center gap-2 mb-3">
          <IconBadge color="rgba(59,130,246,0.2)">
            <ShieldCheck size={13} className="text-blue-400" />
          </IconBadge>
          <span className="text-xs font-medium" style={{ color: 'rgba(255,255,255,0.45)' }}>Güvenlik</span>
        </div>
        <p className="text-sm font-semibold text-white mb-1.5">Verileriniz şifreli</p>
        <p className="text-xs leading-relaxed" style={{ color: 'rgba(255,255,255,0.3)' }}>
          Tüm finansal verileriniz JWT ile korunur. Asla üçüncü taraflarla paylaşılmaz.
        </p>
      </InsightCard>
    </div>
  );
}

// ── Exported VisionPanel ──────────────────────────────────────────────────────
export default function VisionPanel({ variant = 'login' }) {
  return (
    <>
      <style>{KEYFRAMES}</style>
      <div
        className="hidden lg:flex lg:w-[52%] relative overflow-hidden flex-col justify-between p-14"
        style={{
          background: `
            radial-gradient(rgba(255,255,255,0.028) 1px, transparent 1px),
            linear-gradient(160deg, #06091A 0%, #0E1530 60%, #0A1020 100%)
          `,
          backgroundSize: '28px 28px, cover',
        }}
      >
        {/* Blobs */}
        <div className="absolute top-[-15%] right-[-8%] w-[480px] h-[480px] rounded-full blur-3xl pointer-events-none"
          style={{ background: 'radial-gradient(circle, rgba(99,102,241,0.28), transparent 70%)' }} />
        <div className="absolute bottom-[-10%] left-[-12%] w-[400px] h-[400px] rounded-full blur-3xl pointer-events-none"
          style={{ background: 'radial-gradient(circle, rgba(124,58,237,0.22), transparent 70%)' }} />
        <div className="absolute top-[45%] left-[15%] w-[300px] h-[300px] rounded-full blur-3xl pointer-events-none"
          style={{ background: 'radial-gradient(circle, rgba(14,165,233,0.1), transparent 70%)' }} />

        {/* Brand */}
        <a
          href="https://smartbudgetr.com/"
          className="relative z-10 flex items-center gap-3 hover:opacity-80 transition-opacity"
          target="_blank"
          rel="noopener noreferrer"
        >
          <BrandMark />
          <span className="text-white font-bold text-xl tracking-tight">Smart Budget</span>
        </a>

        {/* Cards */}
        <div className="relative z-10 flex-1 flex items-center py-8">
          {variant === 'login' ? <LoginCards /> : <RegisterCards />}
        </div>

        {/* Tagline */}
        <div className="relative z-10">
          <p className="text-sm leading-relaxed" style={{ color: 'rgba(255,255,255,0.35)' }}>
            {variant === 'login'
              ? <>Harcamalarınızı anlayın,<br />geleceğinizi planlayın.</>
              : <>Finansal özgürlüğe<br />ilk adımı bugün atın.</>
            }
          </p>
        </div>
      </div>
    </>
  );
}
