import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Upload, UserCircle } from 'lucide-react';
import { cn } from '../../utils/helpers';

const TABS = [
  { to: '/',        icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/upload',  icon: Upload,          label: 'Yükle' },
  { to: '/profile', icon: UserCircle,      label: 'Profil' },
];

export default function BottomNav() {
  return (
    <nav className="lg:hidden fixed bottom-0 left-0 right-0 z-40
      bg-white/85 dark:bg-zinc-950/92 backdrop-blur-md
      border-t border-zinc-200/70 dark:border-white/[0.06]
      pb-[env(safe-area-inset-bottom,0px)]"
    >
      <div className="flex items-center justify-around h-16 px-2">
        {TABS.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) => cn(
              'relative flex flex-col items-center justify-center gap-1 flex-1 py-2 mx-1 rounded-2xl transition-all duration-200 active:scale-95',
              isActive
                ? 'text-emerald-600 dark:text-emerald-400'
                : 'text-zinc-400 dark:text-zinc-500 hover:text-zinc-600 dark:hover:text-zinc-300',
            )}
          >
            {({ isActive }) => (
              <>
                {/* Aktif göstergesi — tab üstünde küçük pill */}
                <span className={cn(
                  'absolute top-0 left-1/2 -translate-x-1/2 h-0.5 rounded-full transition-all duration-300',
                  isActive ? 'w-8 bg-emerald-500' : 'w-0 bg-transparent',
                )} />
                <div className={cn(
                  'flex items-center justify-center w-10 h-7 rounded-xl transition-all duration-200',
                  isActive ? 'bg-emerald-500/12 dark:bg-emerald-500/15' : '',
                )}>
                  <Icon size={21} strokeWidth={isActive ? 2.1 : 1.8} />
                </div>
                <span className={cn('text-[10px] transition-all', isActive ? 'font-semibold' : 'font-medium')}>
                  {label}
                </span>
              </>
            )}
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
