import { useEffect, useState } from 'react';
import { getSubscriptions } from '../api/client';

const SERVICE_ICONS = {
  netflix:    '🎬',
  spotify:    '🎵',
  icloud:     '☁️',
  'apple tv': '📺',
  youtube:    '▶️',
  disney:     '🏰',
  amazon:     '📦',
  prime:      '📦',
  dropbox:    '📂',
  google:     '🔵',
  onedrive:   '☁️',
  adobe:      '🎨',
  microsoft:  '💻',
  gym:        '💪',
  dergi:      '📰',
  default:    '🔄',
};

function getIcon(description) {
  if (!description) return SERVICE_ICONS.default;
  const lower = description.toLowerCase();
  for (const [key, icon] of Object.entries(SERVICE_ICONS)) {
    if (key !== 'default' && lower.includes(key)) return icon;
  }
  return SERVICE_ICONS.default;
}

const formatTRY = (amount) =>
  Number(amount).toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' });

export default function SubscriptionCard() {
  const [subscriptions, setSubscriptions] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getSubscriptions()
      .then((res) => setSubscriptions(res.data))
      .catch(() => setSubscriptions([]))
      .finally(() => setLoading(false));
  }, []);

  const monthlyTotal = subscriptions.reduce(
    (sum, s) => sum + parseFloat(s.amount || 0),
    0
  );

  if (loading) {
    return (
      <div className="bg-white dark:bg-zinc-900 rounded-xl shadow-sm border border-zinc-100 dark:border-zinc-800 border-l-4 border-l-violet-500 p-6">
        <p className="text-sm text-zinc-400 dark:text-zinc-500">Abonelikler yükleniyor...</p>
      </div>
    );
  }

  return (
    <div className="bg-white dark:bg-zinc-900 rounded-xl shadow-sm border border-zinc-100 dark:border-zinc-800 border-l-4 border-l-violet-500 p-6">

      {/* Başlık */}
      <div className="flex justify-between items-start mb-3">
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center w-10 h-10 bg-violet-100 dark:bg-violet-900/40 rounded-xl text-lg flex-shrink-0">
            🔄
          </div>
          <div>
            <p className="font-bold text-sm text-zinc-900 dark:text-zinc-100">Aylık Abonelikler</p>
            <p className="text-xs text-zinc-400 dark:text-zinc-500">Otomatik yenilenen ödemeler</p>
          </div>
        </div>
        <div className="text-right flex-shrink-0">
          <p className="text-xs font-semibold text-zinc-400 dark:text-zinc-500 uppercase tracking-wide mb-0.5">
            Aylık Toplam
          </p>
          <p className="text-xl font-bold text-violet-600 dark:text-violet-400 tracking-tight tabular-nums">
            {formatTRY(monthlyTotal)}
          </p>
        </div>
      </div>

      {/* Sayaç rozeti */}
      <div className="mb-4">
        <span className="inline-block bg-violet-100 dark:bg-violet-900/40 text-violet-700 dark:text-violet-300 text-xs font-semibold px-2.5 py-0.5 rounded-full">
          {subscriptions.length} abonelik tespit edildi
        </span>
      </div>

      {/* Liste */}
      {subscriptions.length === 0 ? (
        <p className="text-sm text-zinc-400 dark:text-zinc-500 italic">Abonelik bulunamadı.</p>
      ) : (
        <div className="flex flex-col gap-1.5">
          {subscriptions.map((sub, i) => (
            <div
              key={i}
              className="flex justify-between items-center px-3 py-2.5 bg-zinc-50 dark:bg-zinc-800/50 hover:bg-violet-50 dark:hover:bg-violet-950/20 rounded-lg transition-colors"
            >
              <div className="flex items-center gap-2.5">
                <span className="text-xl min-w-[24px] text-center leading-none">
                  {getIcon(sub.description)}
                </span>
                <div>
                  <p className="text-sm font-semibold text-zinc-800 dark:text-zinc-200">
                    {sub.description}
                  </p>
                  {sub.category && (
                    <p className="text-xs text-zinc-400 dark:text-zinc-500">{sub.category}</p>
                  )}
                </div>
              </div>
              <p className="text-sm font-bold text-zinc-700 dark:text-zinc-300 whitespace-nowrap tabular-nums">
                {formatTRY(sub.amount)}
              </p>
            </div>
          ))}
        </div>
      )}

      {/* Alt not */}
      {subscriptions.length > 0 && (
        <p className="mt-4 pt-3 border-t border-zinc-100 dark:border-zinc-800 text-xs text-zinc-400 dark:text-zinc-500 italic">
          💡 Bu harcamalar ekstre yüklendiğinde AI tarafından otomatik tespit edildi.
        </p>
      )}
    </div>
  );
}
