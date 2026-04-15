export default function InstallmentCard({ transactions }) {
  // Jackson is-prefix stripping güvencesi: hem isInstallment hem de installment kontrol edilir
  const installments = (transactions || []).filter(
    (tx) => tx.isInstallment === true || tx.installment === true,
  );

  if (process.env.NODE_ENV !== 'production') {
    console.log('[InstallmentCard] Toplam işlem:', transactions?.length ?? 0);
    console.log('[InstallmentCard] Taksitli:', installments.length);
    if (transactions?.length > 0) {
      console.log(
        '[InstallmentCard] İlk 3 (isInstallment / installment):',
        transactions.slice(0, 3).map((t) => ({
          description: t.description,
          isInstallment: t.isInstallment,
          installment: t.installment,
          currentInstallment: t.currentInstallment,
          totalInstallments: t.totalInstallments,
        })),
      );
    }
  }

  if (installments.length === 0) return null;

  const sorted = [...installments].sort((a, b) => new Date(b.date) - new Date(a.date));

  const totalBurden = installments.reduce(
    (sum, tx) => sum + (Number(tx.amount) || 0),
    0,
  );

  return (
    <div className="bg-white dark:bg-zinc-900 rounded-xl shadow-sm border border-indigo-100 dark:border-indigo-900/40 p-6">

      {/* Başlık */}
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-sm font-semibold text-zinc-600 dark:text-zinc-400 uppercase tracking-widest">
          Taksitli İşlemler
        </h2>
        <span className="bg-indigo-100 dark:bg-indigo-900/40 text-indigo-700 dark:text-indigo-300 text-xs font-semibold px-2.5 py-0.5 rounded-full">
          {installments.length} işlem
        </span>
      </div>

      {/* Satırlar */}
      <div className="space-y-2.5">
        {sorted.map((tx, i) => {
          const hasFraction =
            tx.currentInstallment != null && tx.totalInstallments != null;

          return (
            <div
              key={i}
              className="flex flex-col sm:flex-row sm:items-center gap-2 px-4 py-3 rounded-lg bg-indigo-50/60 dark:bg-indigo-950/20 border border-indigo-100 dark:border-indigo-900/30"
            >
              {/* Açıklama + tarih */}
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-zinc-800 dark:text-zinc-200 truncate">
                  {tx.description || '—'}
                </p>
                <p className="text-xs text-zinc-400 dark:text-zinc-500 mt-0.5">
                  {new Date(tx.date).toLocaleDateString('tr-TR')}
                </p>
              </div>

              {/* Taksit rozeti */}
              {hasFraction ? (
                <span className="inline-flex items-center gap-1 bg-indigo-600 text-white text-xs font-bold px-2.5 py-1 rounded-full whitespace-nowrap self-start sm:self-auto">
                  {tx.currentInstallment}
                  <span className="opacity-60">/</span>
                  {tx.totalInstallments}
                  <span className="ml-0.5 font-normal opacity-80 tracking-wide">taksit</span>
                </span>
              ) : (
                <span className="inline-flex items-center bg-indigo-100 dark:bg-indigo-900/50 text-indigo-600 dark:text-indigo-300 text-xs font-semibold px-2.5 py-1 rounded-full whitespace-nowrap self-start sm:self-auto">
                  taksit
                </span>
              )}

              {/* Tutar */}
              <p className="text-sm font-bold text-zinc-800 dark:text-zinc-100 tabular-nums whitespace-nowrap sm:text-right sm:min-w-[100px]">
                {Number(tx.amount).toLocaleString('tr-TR', {
                  style: 'currency',
                  currency: tx.currency || 'TRY',
                })}
              </p>
            </div>
          );
        })}
      </div>

      {/* Alt toplam */}
      <div className="mt-4 pt-4 border-t border-indigo-100 dark:border-indigo-900/40 flex items-center justify-between">
        <span className="text-xs font-semibold text-zinc-500 dark:text-zinc-400 uppercase tracking-widest">
          Toplam Taksit Yükü
        </span>
        <span className="text-base font-bold text-indigo-600 dark:text-indigo-400 tabular-nums">
          {totalBurden.toLocaleString('tr-TR', {
            style: 'currency',
            currency: 'TRY',
          })}
        </span>
      </div>
    </div>
  );
}
