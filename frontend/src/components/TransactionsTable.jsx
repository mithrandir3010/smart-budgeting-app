const CATEGORY_COLORS = {
  'Kira':      'bg-violet-100 text-violet-700 dark:bg-violet-900/40 dark:text-violet-300',
  'Market':    'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
  'Kafe':      'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  'Restoran':  'bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-300',
  'Fatura':    'bg-sky-100 text-sky-700 dark:bg-sky-900/40 dark:text-sky-300',
  'Ulaşım':    'bg-pink-100 text-pink-700 dark:bg-pink-900/40 dark:text-pink-300',
  'Akaryakıt': 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300',
  'Sağlık':    'bg-rose-100 text-rose-700 dark:bg-rose-900/40 dark:text-rose-300',
  'Eğlence':   'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300',
  'Teknoloji': 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300',
  'Giyim':     'bg-fuchsia-100 text-fuchsia-700 dark:bg-fuchsia-900/40 dark:text-fuchsia-300',
  'Eğitim':    'bg-cyan-100 text-cyan-700 dark:bg-cyan-900/40 dark:text-cyan-300',
  'Sigorta':   'bg-teal-100 text-teal-700 dark:bg-teal-900/40 dark:text-teal-300',
  'Diğer':     'bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400',
};

const ENUM_ICONS = {
  FOOD:          '🍽️',
  TRANSPORT:     '🚗',
  HOUSING:       '🏠',
  SHOPPING:      '🛍️',
  HEALTH:        '💊',
  EDUCATION:     '📚',
  ENTERTAINMENT: '🎬',
  OTHER:         '📦',
};

function CategoryBadge({ category, categoryEnum }) {
  const cls = CATEGORY_COLORS[category] || CATEGORY_COLORS['Diğer'];
  const icon = categoryEnum ? (ENUM_ICONS[categoryEnum] ?? '📦') : null;
  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold ${cls}`}>
      {icon && <span className="text-sm leading-none">{icon}</span>}
      {category || 'Diğer'}
    </span>
  );
}

export default function TransactionsTable({ transactions }) {
  if (!transactions || transactions.length === 0) {
    return (
      <p className="text-center py-10 text-sm text-zinc-400 dark:text-zinc-500">
        Gösterilecek işlem bulunamadı.
      </p>
    );
  }

  const sorted = [...transactions].sort(
    (a, b) => new Date(b.date) - new Date(a.date)
  );

  return (
    <div className="overflow-x-auto rounded-xl border border-zinc-100 dark:border-zinc-800">
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="bg-zinc-50 dark:bg-zinc-800/60">
            {['Tarih', 'Açıklama', 'Kategori', 'Tutar'].map((h) => (
              <th
                key={h}
                className="px-4 py-3 text-left text-xs font-bold text-zinc-400 dark:text-zinc-500 uppercase tracking-widest border-b border-zinc-100 dark:border-zinc-800"
              >
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sorted.map((tx, i) => (
            <tr
              key={i}
              className={`transition-colors hover:bg-indigo-50/60 dark:hover:bg-indigo-950/20 ${
                i % 2 === 0
                  ? 'bg-white dark:bg-zinc-900'
                  : 'bg-zinc-50/50 dark:bg-zinc-900/40'
              }`}
            >
              <td className="px-4 py-3 text-zinc-500 dark:text-zinc-400 border-b border-zinc-50 dark:border-zinc-800/50 whitespace-nowrap font-mono text-xs">
                {new Date(tx.date).toLocaleDateString('tr-TR')}
              </td>
              <td className="px-4 py-3 font-medium text-zinc-800 dark:text-zinc-200 border-b border-zinc-50 dark:border-zinc-800/50">
                <span>{tx.description || '—'}</span>
                {tx.isInstallment && (
                  <span className="ml-2 inline-flex items-center gap-0.5 bg-indigo-100 dark:bg-indigo-900/40 text-indigo-700 dark:text-indigo-300 text-xs font-semibold px-1.5 py-0.5 rounded-full">
                    Taksit
                    {tx.currentInstallment != null && tx.totalInstallments != null && (
                      <span className="font-mono"> {tx.currentInstallment}/{tx.totalInstallments}</span>
                    )}
                  </span>
                )}
              </td>
              <td className="px-4 py-3 border-b border-zinc-50 dark:border-zinc-800/50">
                <CategoryBadge category={tx.category} categoryEnum={tx.categoryEnum} />
              </td>
              <td className="px-4 py-3 text-right font-semibold text-zinc-800 dark:text-zinc-200 border-b border-zinc-50 dark:border-zinc-800/50 whitespace-nowrap tabular-nums">
                {Number(tx.amount).toLocaleString('tr-TR', {
                  style: 'currency',
                  currency: tx.currency || 'TRY',
                })}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
