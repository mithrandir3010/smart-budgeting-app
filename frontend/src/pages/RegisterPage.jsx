import { useState } from 'react';
import { Link } from 'react-router-dom';
import { register } from '../api/client';
import { User, AtSign, Mail, Lock, Eye, EyeOff } from 'lucide-react';

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

function InputField({ icon: Icon, label, name, type = 'text', value, onChange, placeholder, required, rightElement }) {
  return (
    <div className="space-y-1.5">
      <label className="block text-xs font-medium text-zinc-400 tracking-wide">
        {label}
      </label>
      <div className="relative">
        <Icon size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-zinc-500 pointer-events-none" />
        <input
          name={name}
          type={type}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          required={required}
          className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-zinc-800/60 border border-zinc-700/60 text-zinc-100 placeholder-zinc-600 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/60 focus:border-indigo-500/50 transition-all"
        />
        {rightElement && (
          <div className="absolute right-3 top-1/2 -translate-y-1/2">
            {rightElement}
          </div>
        )}
      </div>
    </div>
  );
}

export default function RegisterPage() {
  const [form, setForm] = useState({ username: '', email: '', password: '', fullName: '' });
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState(null);
  const [showPassword, setShowPassword] = useState(false);

  const extractErrorMessage = (err) => {
    const responseData = err?.response?.data;
    if (typeof responseData?.message === 'string' && responseData.message.trim()) return responseData.message;
    if (typeof responseData === 'string' && responseData.trim()) return responseData;
    if (!err?.response) return 'Sunucuya bağlanılamadı.';
    return 'Kayıt oluşturulamadı.';
  };

  const handleChange = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await register(form);
      setRegisteredEmail(form.email);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-950 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        {registeredEmail ? (
          <div className="bg-zinc-900 rounded-2xl border border-zinc-800 px-8 py-10 text-center">
            <div className="flex justify-center mb-4">
              <div className="p-4 bg-indigo-500/10 rounded-full">
                <Mail size={32} className="text-indigo-400" />
              </div>
            </div>
            <h2 className="text-xl font-bold text-zinc-100 mb-2">E-postanızı doğrulayın</h2>
            <p className="text-sm text-zinc-500 mb-1">Doğrulama linki şu adrese gönderildi:</p>
            <p className="text-sm font-semibold text-indigo-400 mb-6">{registeredEmail}</p>
            <p className="text-xs text-zinc-600 mb-6">
              E-posta gelmezse spam klasörünü kontrol edin. Link 24 saat geçerlidir.
            </p>
            <Link to="/login" className="text-sm text-indigo-400 font-semibold hover:text-indigo-300 transition-colors">
              Giriş sayfasına dön
            </Link>
          </div>
        ) : (
          <div className="bg-zinc-900 rounded-2xl border border-zinc-800 px-8 py-10">
            <div className="flex flex-col items-center mb-8">
              <BrandMark size={40} />
              <h1 className="text-xl font-bold text-zinc-100 tracking-tight mt-3">Hesap oluştur</h1>
              <p className="text-sm text-zinc-500 mt-1">Smart Budget'a hoş geldiniz</p>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              <InputField
                icon={User}
                label="Ad Soyad"
                name="fullName"
                value={form.fullName}
                onChange={handleChange}
                placeholder="Adınız ve soyadınız"
                required
              />

              <InputField
                icon={AtSign}
                label="Kullanıcı Adı"
                name="username"
                value={form.username}
                onChange={handleChange}
                placeholder="kullaniciadi"
                required
              />

              <InputField
                icon={Mail}
                label="E-posta"
                name="email"
                type="email"
                value={form.email}
                onChange={handleChange}
                placeholder="ad@ornek.com"
                required
              />

              <InputField
                icon={Lock}
                label="Şifre"
                name="password"
                type={showPassword ? 'text' : 'password'}
                value={form.password}
                onChange={handleChange}
                placeholder="En az 6 karakter"
                required
                rightElement={
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    className="text-zinc-500 hover:text-zinc-300 transition-colors"
                  >
                    {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                  </button>
                }
              />

              {error && (
                <div className="bg-rose-500/10 text-rose-400 border border-rose-500/20 rounded-xl px-4 py-3 text-xs">
                  {error}
                </div>
              )}

              <button
                type="submit"
                disabled={loading}
                className="w-full bg-indigo-600 hover:bg-indigo-500 active:scale-[0.98] disabled:opacity-50 text-white font-semibold py-2.5 rounded-xl text-sm transition-all mt-2"
              >
                {loading ? 'Kaydediliyor...' : 'Kayıt Ol'}
              </button>
            </form>

            <p className="text-center text-xs text-zinc-600 mt-6">
              Zaten hesabınız var mı?{' '}
              <Link to="/login" className="text-indigo-400 font-semibold hover:text-indigo-300 transition-colors">
                Giriş Yap
              </Link>
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
