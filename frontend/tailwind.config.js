/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', 'Segoe UI', 'Roboto', 'sans-serif'],
      },
      colors: {
        canvas: {
          DEFAULT: '#050507',
          50:  '#0d0d12',
          100: '#111118',
          200: '#16161e',
          300: '#1c1c26',
        },
        neon: {
          green:         '#10b981',
          'green-bright':'#34d399',
          amber:         '#f59e0b',
          'amber-bright':'#fbbf24',
          rose:          '#f43f5e',
          indigo:        '#6366f1',
        },
        zinc: {
          925: '#111113',
          950: '#09090b',
        },
      },
      boxShadow: {
        card:               '0 1px 3px rgba(0,0,0,0.4), 0 1px 2px rgba(0,0,0,0.3)',
        'card-glow-green':  '0 0 28px rgba(16,185,129,0.13), inset 0 1px 0 rgba(255,255,255,0.06)',
        'card-glow-amber':  '0 0 28px rgba(245,158,11,0.13), inset 0 1px 0 rgba(255,255,255,0.06)',
        'card-glass':       'inset 0 1px 0 rgba(255,255,255,0.06), 0 4px 24px rgba(0,0,0,0.5)',
        sidebar:            '1px 0 0 rgba(255,255,255,0.05)',
        'neon-green':       '0 0 16px rgba(16,185,129,0.55)',
        'neon-amber':       '0 0 16px rgba(245,158,11,0.55)',
      },
      keyframes: {
        'fade-up': {
          '0%':   { opacity: '0', transform: 'translateY(16px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'pulse-dot': {
          '0%, 100%': { transform: 'scale(1)', opacity: '1' },
          '50%':      { transform: 'scale(1.4)', opacity: '0.7' },
        },
      },
      animation: {
        'fade-up':   'fade-up 0.5s ease-out forwards',
        'pulse-dot': 'pulse-dot 2s ease-in-out infinite',
      },
    },
  },
  plugins: [],
};
