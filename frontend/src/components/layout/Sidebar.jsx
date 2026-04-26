import { NavLink, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  LayoutDashboard, Upload, UserCircle, LogOut,
  Wallet, ShieldAlert, FileDown, Trash2,
} from 'lucide-react';
import { cn } from '../../lib/utils';
import { clearAuth, getStoredUser } from '../../api/client';

const NAV = [
  { to: '/',        icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/upload',  icon: Upload,          label: 'Ekstre Yükle' },
  { to: '/profile', icon: UserCircle,      label: 'Profil' },
];

function NavItem({ to, icon: Icon, label }) {
  return (
    <NavLink
      to={to}
      end={to === '/'}
      className={({ isActive }) => cn(
        'group flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-200',
        isActive
          ? 'bg-emerald-500/10 text-emerald-400 shadow-[inset_0_0_0_1px_rgba(52,211,153,0.2)]'
          : 'text-zinc-400 hover:text-zinc-100 hover:bg-white/[0.05]',
      )}
    >
      <Icon size={17} strokeWidth={1.8} className="flex-shrink-0" />
      <span>{label}</span>
    </NavLink>
  );
}

export default function Sidebar({ onLimitClick, onDeleteAll, onDownloadPdf, pdfLoading }) {
  const navigate = useNavigate();
  const user = getStoredUser();

  const handleLogout = () => {
    clearAuth();
    navigate('/login');
  };

  return (
    <motion.aside
      initial={{ x: -20, opacity: 0 }}
      animate={{ x: 0, opacity: 1 }}
      transition={{ duration: 0.35, ease: 'easeOut' }}
      className="glass-sidebar fixed left-0 top-0 h-full w-[var(--sidebar-w,240px)] flex flex-col z-30 select-none"
    >
      {/* Logo */}
      <div className="px-4 pt-6 pb-5">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-xl bg-emerald-500/15 flex items-center justify-center shadow-neon-green">
            <Wallet size={16} className="text-emerald-400" strokeWidth={2} />
          </div>
          <div>
            <span className="text-sm font-bold text-zinc-100 tracking-tight">SmartBudget</span>
            <span className="block text-[10px] text-zinc-500 font-medium tracking-widest uppercase mt-px">Analytics</span>
          </div>
        </div>
      </div>

      {/* Divider */}
      <div className="mx-4 mb-4 h-px bg-white/[0.06]" />

      {/* Navigation */}
      <nav className="flex-1 px-2 space-y-1">
        {NAV.map((item) => (
          <NavItem key={item.to} {...item} />
        ))}
      </nav>

      {/* Quick actions */}
      <div className="px-2 pb-2 space-y-1">
        <div className="mx-2 mb-2 h-px bg-white/[0.06]" />
        <p className="px-3 text-[10px] font-semibold text-zinc-600 uppercase tracking-widest mb-1">
          Hızlı Eylemler
        </p>

        <button
          onClick={onLimitClick}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium text-zinc-400 hover:text-zinc-100 hover:bg-white/[0.05] transition-all"
        >
          <ShieldAlert size={16} strokeWidth={1.8} />
          Limitler
        </button>

        <button
          onClick={onDownloadPdf}
          disabled={pdfLoading}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium text-emerald-400/80 hover:text-emerald-300 hover:bg-emerald-500/[0.08] transition-all disabled:opacity-40"
        >
          <FileDown size={16} strokeWidth={1.8} />
          {pdfLoading ? 'Hazırlanıyor...' : 'PDF İndir'}
        </button>

        <button
          onClick={onDeleteAll}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium text-rose-400/70 hover:text-rose-300 hover:bg-rose-500/[0.08] transition-all"
        >
          <Trash2 size={16} strokeWidth={1.8} />
          Tüm Veriyi Sil
        </button>
      </div>

      {/* User profile + logout */}
      <div className="mx-4 mt-2 mb-4 pt-4 border-t border-white/[0.06]">
        {user && (
          <div className="mb-3 px-1">
            <p className="text-xs font-semibold text-zinc-200 truncate">{user.fullName}</p>
            <p className="text-[11px] text-zinc-500 truncate mt-0.5">{user.email}</p>
          </div>
        )}
        <button
          onClick={handleLogout}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium text-zinc-500 hover:text-zinc-200 hover:bg-white/[0.05] transition-all"
        >
          <LogOut size={16} strokeWidth={1.8} />
          Çıkış Yap
        </button>
      </div>
    </motion.aside>
  );
}
