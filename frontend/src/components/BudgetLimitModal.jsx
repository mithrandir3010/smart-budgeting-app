import { useEffect, useState, useCallback } from 'react';
import { motion } from 'framer-motion';
import { toast } from 'sonner';
import { X, Trash2, Plus, Loader2, ShieldAlert } from 'lucide-react';
import {
  getBudgetLimits,
  upsertBudgetLimit,
  deleteBudgetLimit,
} from '../api/client';

const CATEGORIES = [
  'Market', 'Restoran', 'Ulaşım', 'Sağlık', 'Eğlence',
  'Giyim', 'Faturalar', 'Abonelik', 'Eğitim', 'Diğer',
];

function formatTRY(v) {
  return Number(v).toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' });
}

const isMobileBreakpoint = () =>
  typeof window !== 'undefined' && window.innerWidth < 768;

export default function BudgetLimitModal({ onClose, onSaved }) {
  const [limits, setLimits]           = useState([]);
  const [loadingList, setLoadingList] = useState(true);
  const [deleting, setDeleting]       = useState(null);
  const [saving, setSaving]           = useState(false);
  const [form, setForm]               = useState({ category: CATEGORIES[0], limitAmount: '' });
  const [formError, setFormError]     = useState('');
  const [isMobile, setIsMobile]       = useState(isMobileBreakpoint);

  const fetchLimits = useCallback(async () => {
    try {
      const res = await getBudgetLimits();
      setLimits(res.data);
    } catch {
      toast.error('Limitler yüklenirken hata oluştu.');
    } finally {
      setLoadingList(false);
    }
  }, []);

  useEffect(() => {
    fetchLimits();
    const onKey    = (e) => { if (e.key === 'Escape') onClose(); };
    const onResize = () => setIsMobile(isMobileBreakpoint());
    window.addEventListener('keydown', onKey);
    window.addEventListener('resize', onResize);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('resize', onResize);
    };
  }, [fetchLimits, onClose]);

  const handleDelete = async (id, category) => {
    setDeleting(id);
    try {
      await deleteBudgetLimit(id);
      setLimits((prev) => prev.filter((l) => l.id !== id));
      toast.success(`"${category}" limiti silindi.`);
      onSaved?.();
    } catch {
      toast.error('Limit silinirken hata oluştu.');
    } finally {
      setDeleting(null);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError('');
    const amount = parseFloat(form.limitAmount);
    if (!form.category) { setFormError('Kategori seçin.'); return; }
    if (!form.limitAmount || isNaN(amount) || amount <= 0) {
      setFormError('Geçerli bir limit tutarı girin (₺ cinsinden pozitif sayı).');
      return;
    }
    setSaving(true);
    try {
      await upsertBudgetLimit({ category: form.category, limitAmount: amount });
      toast.success(`"${form.category}" için limit kaydedildi.`);
      setForm({ category: CATEGORIES[0], limitAmount: '' });
      await fetchLimits();
      onSaved?.();
    } catch (err) {
      if (err.response?.status !== 409) toast.error('Limit kaydedilirken hata oluştu.');
    } finally {
      setSaving(false);
    }
  };

  const panelVariants = isMobile
    ? { initial: { y: '100%' }, animate: { y: 0 }, exit: { y: '100%' },
        transition: { type: 'spring', damping: 30, stiffness: 320 } }
    : { initial: { opacity: 0, scale: 0.96 }, animate: { opacity: 1, scale: 1 },
        exit: { opacity: 0, scale: 0.96 }, transition: { duration: 0.18 } };

  return (
    <motion.div
      key="budget-overlay"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.2 }}
      className={`fixed inset-0 z-50 bg-black/50 backdrop-blur-sm
        ${isMobile ? 'flex items-end' : 'flex items-center justify-center p-4'}`}
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <motion.div
        key="budget-panel"
        {...panelVariants}
        className={`w-full bg-white dark:bg-zinc-900 shadow-2xl
          border border-zinc-100 dark:border-zinc-800 overflow-hidden
          ${isMobile ? 'rounded-t-3xl max-h-[90vh]' : 'max-w-md rounded-2xl'}`}
      >
        {/* Drag handle — mobile only */}
        {isMobile && (
          <div className="flex justify-center pt-3 pb-1">
            <div className="w-10 h-1 rounded-full bg-zinc-300 dark:bg-zinc-700" />
          </div>
        )}

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-100 dark:border-zinc-800">
          <div className="flex items-center gap-2.5">
            <ShieldAlert size={18} className="text-indigo-500" strokeWidth={2} />
            <h2 className="text-base font-bold text-zinc-900 dark:text-zinc-100 tracking-tight">
              Bütçe Limitleri
            </h2>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
          >
            <X size={16} strokeWidth={2.5} />
          </button>
        </div>

        <div className="px-6 py-5 space-y-5 max-h-[70vh] overflow-y-auto">

          {/* Existing limits */}
          <div>
            <p className="text-xs font-semibold text-zinc-400 dark:text-zinc-500 uppercase tracking-widest mb-3">
              Mevcut Limitler
            </p>
            {loadingList ? (
              <div className="flex items-center justify-center py-6">
                <Loader2 size={20} className="text-indigo-400 animate-spin" />
              </div>
            ) : limits.length === 0 ? (
              <p className="text-sm text-zinc-400 dark:text-zinc-500 italic text-center py-4">
                Henüz hiç limit tanımlanmamış.
              </p>
            ) : (
              <ul className="space-y-2">
                {limits.map((l) => (
                  <li
                    key={l.id}
                    className="flex items-center justify-between gap-3 bg-zinc-50 dark:bg-zinc-800 rounded-xl px-4 py-3"
                  >
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-zinc-900 dark:text-zinc-100 truncate">{l.category}</p>
                      <p className="text-xs text-zinc-400 dark:text-zinc-500 mt-0.5">{formatTRY(l.limitAmount)}</p>
                    </div>
                    <button
                      onClick={() => handleDelete(l.id, l.category)}
                      disabled={deleting === l.id}
                      className="flex-shrink-0 p-1.5 rounded-lg text-zinc-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-950/30 transition-colors disabled:opacity-40"
                      title="Limiti sil"
                    >
                      {deleting === l.id
                        ? <Loader2 size={14} className="animate-spin" />
                        : <Trash2 size={14} strokeWidth={2} />}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* Add / update form */}
          <div>
            <p className="text-xs font-semibold text-zinc-400 dark:text-zinc-500 uppercase tracking-widest mb-3">
              Limit Ekle / Güncelle
            </p>
            <form onSubmit={handleSubmit} className="space-y-3">
              <div>
                <label className="block text-xs font-medium text-zinc-600 dark:text-zinc-400 mb-1.5">Kategori</label>
                <select
                  value={form.category}
                  onChange={(e) => setForm((p) => ({ ...p, category: e.target.value }))}
                  className="w-full bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-xl px-3.5 py-2.5 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition"
                >
                  {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-zinc-600 dark:text-zinc-400 mb-1.5">Limit (₺)</label>
                <input
                  type="number" min="1" step="0.01" placeholder="Örn: 3000"
                  value={form.limitAmount}
                  onChange={(e) => { setFormError(''); setForm((p) => ({ ...p, limitAmount: e.target.value })); }}
                  className="w-full bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-xl px-3.5 py-2.5 text-sm text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-2 focus:ring-indigo-500 transition placeholder:text-zinc-400"
                />
              </div>
              {formError && <p className="text-xs text-rose-500 font-medium">{formError}</p>}
              <button
                type="submit" disabled={saving}
                className="w-full flex items-center justify-center gap-2 bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold py-2.5 rounded-xl text-sm transition-colors"
              >
                {saving
                  ? <><Loader2 size={14} className="animate-spin" /> Kaydediliyor…</>
                  : <><Plus size={14} strokeWidth={2.5} /> Kaydet</>}
              </button>
            </form>
          </div>
        </div>
      </motion.div>
    </motion.div>
  );
}
