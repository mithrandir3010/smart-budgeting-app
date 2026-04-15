export default function InstallmentCard({ transactions }) {
  // Debug: gelen transaction verisini konsola yaz
  const installments = (transactions || []).filter((tx) => tx.isInstallment === true);

  if (process.env.NODE_ENV !== 'production') {
    console.log('[InstallmentCard] Toplam işlem:', transactions?.length ?? 0);
    console.log('[InstallmentCard] isInstallment===true olan:', installments.length);
    if (transactions?.length > 0) {
      console.log('[InstallmentCard] İlk 3 işlem (isInstallment alanı):', transactions.slice(0, 3).map((t) => ({
        description: t.description,
        isInstallment: t.isInstallment,
        currentInstallment: t.currentInstallment,
        totalInstallments: t.totalInstallments,
      })));
    }
  }

  if (installments.length === 0) return null;

  const sorted = [...installments].sort((a, b) => new Date(b.date) - new Date(a.date));

  return (
    <div className="bg-white dark:bg-zinc-900 rounded-xl shadow-sm border border-zinc-100 dark:border-zinc-800 p-6">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-sm font-semibold text-zinc-600 dark:text-zinc-400 uppercase tracking-widest">
          Taksitli İşlemler
        </h2>
        <span className="bg-indigo-100 dark:bg-indigo-900/40 text-indigo-700 dark:text-indigo-300 text-xs font-semibold px-2.5 py-0.5 rounded-full">
          {installments.length} taksit
        </span>
      </div>

      <div className="space-y-3">
        {sorted.map((tx, i) => {
          const hasFraction =
            tx.currentInstallment != null && tx.totalInstallments != null;
          const progress = hasFraction
            ? Math.round((tx.currentInstallment / tx.totalInstallments) * 100)
            : null;

          return (
            <div
              key={i}
              className="flex flex-col sm:flex-row sm:items-center gap-2 p-3 rounded-lg bg-zinc-50 dark:bg-zinc-800/50 border border-zinc-100 dark:border-zinc-800"
            >
              {/* Description + date */}
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-zinc-800 dark:text-zinc-200 truncate">
                  {tx.description || '—'}
                </p>
                <p className="text-xs text-zinc-400 dark:text-zinc-500 mt-0.5">
                  {new Date(tx.date).toLocaleDateString('tr-TR')}
                </p>
              </div>

              {/* Progress bar + fraction */}
              {hasFraction ? (
                <div className="flex items-center gap-2 min-w-[140px]">
                  <div className="flex-1 bg-zinc-200 dark:bg-zinc-700 rounded-full h-1.5 overflow-hidden">
                    <div
                      className="bg-indigo-500 h-1.5 rounded-full transition-all duration-500"
                      style={{ width: `${progress}%` }}
                    />
                  </div>
                  <span className="text-xs font-mono text-indigo-600 dark:text-indigo-400 whitespace-nowrap">
                    {tx.currentInstallment}/{tx.totalInstallments}
                  </span>
                </div>
              ) : (
                <span className="text-xs text-zinc-400 dark:text-zinc-500 italic">taksit</span>
              )}

              {/* Amount */}
              <p className="text-sm font-bold text-zinc-800 dark:text-zinc-100 tabular-nums whitespace-nowrap sm:text-right">
                {Number(tx.amount).toLocaleString('tr-TR', {
                  style: 'currency',
                  currency: tx.currency || 'TRY',
                })}
              </p>
            </div>
          );
        })}
      </div>
    </div>
  );
}
