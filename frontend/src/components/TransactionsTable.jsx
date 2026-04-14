const CATEGORY_COLORS = {
  'Kira':    'bg-violet-100 text-violet-700 dark:bg-violet-900/40 dark:text-violet-300',
  'Market':  'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
  'Kafe':    'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  'Fatura':  'bg-sky-100 text-sky-700 dark:bg-sky-900/40 dark:text-sky-300',
  'Ulaşım':  'bg-pink-100 text-pink-700 dark:bg-pink-900/40 dark:text-pink-300',
  'Yemek':   'bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-300',
  'Diğer':   'bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400',
};

function CategoryBadge({ category }) {
  const cls = CATEGORY_COLORS[category] || CATEGORY_COLORS['Diğer'];
  return (
    <span className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-semibold ${cls}`}>
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
                {tx.description || '—'}
              </td>
              <td className="px-4 py-3 border-b border-zinc-50 dark:border-zinc-800/50">
                <CategoryBadge category={tx.category} />
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
