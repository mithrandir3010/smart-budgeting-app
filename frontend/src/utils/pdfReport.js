import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import { getSubscriptions } from '../api/client';

// ─── Renk paleti (Dashboard indigo teması) ───────────────────────────────────
const INDIGO       = [99, 102, 241];   // #6366f1
const INDIGO_DARK  = [67,  56, 202];   // #4338ca  — header gradient
const INDIGO_LIGHT = [238, 242, 255];  // #eef2ff  — alternate row
const ZINC_700     = [63,  63,  70];   // #3f3f46  — body text
const WHITE        = [255, 255, 255];

// ─── Yardımcı: Helvetica desteklemediği Türkçe karakterleri dönüştür ─────────
// (₺ yerine "TL" kullanılıyor; ğ/ş/ı vs. → latin karşılıkları)
const tr = (s) =>
  String(s ?? '')
    .replace(/İ/g, 'I').replace(/Ğ/g, 'G').replace(/Ş/g, 'S')
    .replace(/Ü/g, 'U').replace(/Ö/g, 'O').replace(/Ç/g, 'C')
    .replace(/ı/g, 'i').replace(/ğ/g, 'g').replace(/ş/g, 's')
    .replace(/ü/g, 'u').replace(/ö/g, 'o').replace(/ç/g, 'c');

// Para formatı — ₺ yerine "TL" (Helvetica'da ₺ render edilmez)
const fmt = (amount) => {
  const n = Number(amount) || 0;
  return n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' TL';
};

// Bölüm başlığı çizen yardımcı
function sectionTitle(doc, text, y, margin) {
  doc.setFillColor(...INDIGO);
  doc.roundedRect(margin, y - 4, 4, 8, 1, 1, 'F');
  doc.setTextColor(...INDIGO);
  doc.setFontSize(11);
  doc.setFont('helvetica', 'bold');
  doc.text(text, margin + 7, y + 0.5);
  return y + 7;
}

// Ortak autoTable ayarları
const tableDefaults = (doc, startY, margin) => ({
  startY,
  margin: { left: margin, right: margin },
  styles: {
    font: 'helvetica',
    fontSize: 9,
    textColor: ZINC_700,
    cellPadding: { top: 3, bottom: 3, left: 4, right: 4 },
  },
  headStyles: {
    fillColor: INDIGO,
    textColor: WHITE,
    fontStyle: 'bold',
    fontSize: 9.5,
  },
  alternateRowStyles: { fillColor: INDIGO_LIGHT },
  tableLineColor: [220, 220, 230],
  tableLineWidth: 0.1,
});

// ─────────────────────────────────────────────────────────────────────────────
// Ana fonksiyon
// ─────────────────────────────────────────────────────────────────────────────
export async function generateReport({ summary, transactions }) {
  const doc      = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
  const PW       = doc.internal.pageSize.getWidth();   // 210
  const PH       = doc.internal.pageSize.getHeight();  // 297
  const margin   = 14;
  let y = 0;

  // ── Abonelikleri çek ────────────────────────────────────────────────────────
  let subscriptions = [];
  try {
    const res = await getSubscriptions();
    subscriptions = res.data || [];
  } catch { /* non-critical */ }

  // ── Hesaplamalar ────────────────────────────────────────────────────────────
  const installments = (transactions || []).filter(
    (tx) => tx.isInstallment === true || tx.installment === true,
  );
  const installmentTotal  = installments.reduce((s, tx) => s + (Number(tx.amount) || 0), 0);
  const subscriptionTotal = subscriptions.reduce((s, tx) => s + (Number(tx.amount) || 0), 0);
  const totalSpending     = Number(summary?.totalSpending) || 0;

  // ════════════════════════════════════════════════════════════════════════════
  // 1. HEADER BANDI
  // ════════════════════════════════════════════════════════════════════════════
  // Gradient hissi: koyu bant + açık şerit
  doc.setFillColor(...INDIGO_DARK);
  doc.rect(0, 0, PW, 30, 'F');
  doc.setFillColor(...INDIGO);
  doc.rect(0, 24, PW, 6, 'F');

  // Rapor başlığı
  doc.setTextColor(...WHITE);
  doc.setFontSize(16);
  doc.setFont('helvetica', 'bold');
  doc.text('Smart Budget', margin, 13);

  // Alt başlık
  doc.setFontSize(9.5);
  doc.setFont('helvetica', 'normal');
  doc.text('Finansal Analiz Raporu', margin, 21);

  // Tarih (sağ)
  const now     = new Date();
  const dateStr = tr(now.toLocaleDateString('tr-TR', { day: '2-digit', month: 'long', year: 'numeric' }));
  doc.setFontSize(9);
  doc.text(dateStr, PW - margin, 13, { align: 'right' });

  const timeStr = now.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
  doc.setFontSize(8);
  doc.setTextColor(200, 210, 255);
  doc.text(timeStr, PW - margin, 21, { align: 'right' });

  y = 40;

  // ════════════════════════════════════════════════════════════════════════════
  // 2. ÖZET TABLOSU
  // ════════════════════════════════════════════════════════════════════════════
  y = sectionTitle(doc, 'OZET', y, margin);

  const summaryBody = [
    ['Toplam Harcama',         fmt(totalSpending)],
    ['Toplam Taksit Yuku',     fmt(installmentTotal)],
    ['Aylik Abonelik Gideri',  fmt(subscriptionTotal)],
  ];
  if (summary?.monthlyBudget) {
    summaryBody.push(['Aylik Butce Hedefi', fmt(summary.monthlyBudget)]);
    const remaining = Number(summary.monthlyBudget) - totalSpending;
    summaryBody.push([
      remaining >= 0 ? 'Kalan Butce' : 'Bütçe Asimi',
      fmt(Math.abs(remaining)),
    ]);
  }

  autoTable(doc, {
    ...tableDefaults(doc, y, margin),
    head: [['Metrik', 'Tutar']],
    body: summaryBody,
    columnStyles: {
      0: { cellWidth: 80 },
      1: { halign: 'right', fontStyle: 'bold' },
    },
  });
  y = doc.lastAutoTable.finalY + 12;

  // ════════════════════════════════════════════════════════════════════════════
  // 3. KATEGORİ DAĞILIMI
  // ════════════════════════════════════════════════════════════════════════════
  if (y > 235) { doc.addPage(); y = 20; }
  y = sectionTitle(doc, 'KATEGORI DAGILIMI', y, margin);

  const categoryRows = Object.entries(summary?.categoryBreakdown || {})
    .sort(([, a], [, b]) => Number(b) - Number(a))
    .map(([name, value]) => {
      const pct = totalSpending > 0
        ? ((Number(value) / totalSpending) * 100).toFixed(1)
        : '0.0';
      return [tr(name), fmt(value), `%${pct}`];
    });

  autoTable(doc, {
    ...tableDefaults(doc, y, margin),
    head: [['Kategori', 'Tutar', 'Pay']],
    body: categoryRows,
    columnStyles: {
      1: { halign: 'right' },
      2: { halign: 'right', fontStyle: 'bold', textColor: INDIGO },
    },
    foot: [[
      { content: 'TOPLAM', styles: { fontStyle: 'bold' } },
      { content: fmt(totalSpending), styles: { fontStyle: 'bold', halign: 'right' } },
      { content: '%100', styles: { fontStyle: 'bold', halign: 'right' } },
    ]],
    footStyles: { fillColor: [240, 242, 255], textColor: ZINC_700 },
  });
  y = doc.lastAutoTable.finalY + 12;

  // ════════════════════════════════════════════════════════════════════════════
  // 4. TAKSİTLİ İŞLEMLER
  // ════════════════════════════════════════════════════════════════════════════
  if (installments.length > 0) {
    if (y > 235) { doc.addPage(); y = 20; }
    y = sectionTitle(doc, 'TAKSITLİ ISLEMLER', y, margin);

    const sorted = [...installments].sort((a, b) => new Date(b.date) - new Date(a.date));
    const installmentRows = sorted.map((tx) => {
      const fraction =
        tx.currentInstallment != null && tx.totalInstallments != null
          ? `${tx.currentInstallment} / ${tx.totalInstallments}`
          : 'Taksit';
      return [
        tr(tx.description || '—'),
        new Date(tx.date).toLocaleDateString('tr-TR'),
        fraction,
        tr(tx.category || '—'),
        fmt(tx.amount),
      ];
    });

    autoTable(doc, {
      ...tableDefaults(doc, y, margin),
      head: [['Islem', 'Tarih', 'Taksit No', 'Kategori', 'Tutar']],
      body: installmentRows,
      columnStyles: {
        4: { halign: 'right' },
        2: { halign: 'center', fontStyle: 'bold', textColor: INDIGO },
      },
      foot: [[
        { content: 'Toplam Taksit Yuku', colSpan: 4, styles: { fontStyle: 'bold', halign: 'right' } },
        { content: fmt(installmentTotal), styles: { fontStyle: 'bold', halign: 'right', textColor: INDIGO } },
      ]],
      footStyles: { fillColor: [240, 242, 255] },
    });
    y = doc.lastAutoTable.finalY + 12;
  }

  // ════════════════════════════════════════════════════════════════════════════
  // 5. ABONELİKLER
  // ════════════════════════════════════════════════════════════════════════════
  if (subscriptions.length > 0) {
    if (y > 235) { doc.addPage(); y = 20; }
    y = sectionTitle(doc, 'ABONELIKLER', y, margin);

    const subRows = subscriptions.map((tx) => [
      tr(tx.description || '—'),
      tr(tx.category   || '—'),
      fmt(tx.amount),
    ]);

    autoTable(doc, {
      ...tableDefaults(doc, y, margin),
      head: [['Abonelik', 'Kategori', 'Aylik Tutar']],
      body: subRows,
      columnStyles: { 2: { halign: 'right' } },
      foot: [[
        { content: 'Aylik Toplam', colSpan: 2, styles: { fontStyle: 'bold', halign: 'right' } },
        { content: fmt(subscriptionTotal), styles: { fontStyle: 'bold', halign: 'right', textColor: INDIGO } },
      ]],
      footStyles: { fillColor: [240, 242, 255] },
    });
  }

  // ════════════════════════════════════════════════════════════════════════════
  // 6. FOOTER — tüm sayfalara
  // ════════════════════════════════════════════════════════════════════════════
  const pageCount = doc.internal.getNumberOfPages();
  for (let i = 1; i <= pageCount; i++) {
    doc.setPage(i);

    // İnce çizgi
    doc.setDrawColor(220, 220, 230);
    doc.setLineWidth(0.3);
    doc.line(margin, PH - 14, PW - margin, PH - 14);

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(7.5);
    doc.setTextColor(160, 160, 170);
    doc.text(
      `Smart Budget  |  ${dateStr}  |  Sayfa ${i} / ${pageCount}`,
      PW / 2,
      PH - 9,
      { align: 'center' },
    );
  }

  // ── İndir ──────────────────────────────────────────────────────────────────
  doc.save('smart-budgeting-analiz-raporu.pdf');
}
