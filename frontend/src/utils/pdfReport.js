import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import { getSubscriptions } from '../api/client';

// ─── Renk paleti ─────────────────────────────────────────────────────────────
const INDIGO       = [79, 70, 229];    // #4F46E5 — Indigo 600
const INDIGO_DARK  = [67, 56, 202];    // #4338CA
const INDIGO_LIGHT = [238, 242, 255];  // stripe satır
const ZINC_700     = [63, 63, 70];
const ZINC_400     = [161, 161, 170];
const WHITE        = [255, 255, 255];

const PIE_COLORS = [
  [99, 102, 241],  // indigo
  [244, 63, 94],   // rose
  [16, 185, 129],  // emerald
  [245, 158, 11],  // amber
  [59, 130, 246],  // blue
  [139, 92, 246],  // violet
  [20, 184, 166],  // teal
  [132, 204, 22],  // lime
  [249, 115, 22],  // orange
  [236, 72, 153],  // pink
];

// ─── Türkçe karakter dönüşümü (Helvetica fallback için) ──────────────────────
const tr = (s) =>
  String(s ?? '')
    .replace(/İ/g, 'I').replace(/Ğ/g, 'G').replace(/Ş/g, 'S')
    .replace(/Ü/g, 'U').replace(/Ö/g, 'O').replace(/Ç/g, 'C')
    .replace(/ı/g, 'i').replace(/ğ/g, 'g').replace(/ş/g, 's')
    .replace(/ü/g, 'u').replace(/ö/g, 'o').replace(/ç/g, 'c');

// Roboto yüklenirse direkt, helvetica fallback ise tr() dönüşümü
const safe = (font, s) => font === 'helvetica' ? tr(s) : String(s ?? '');

// ─── Para formatı ─────────────────────────────────────────────────────────────
const fmt = (amount) => {
  const n = Number(amount) || 0;
  return n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' TL';
};

// ─── Açıklama temizleyici ─────────────────────────────────────────────────────
// "Spotify AB" → "Spotify", "Spotifyab" → "Spotify", "Netflix taksidi" → "Netflix"
function cleanDescription(desc) {
  if (!desc) return '—';
  return desc
    .replace(/\s+AB\s*$/i, '')        // trailing " AB" (İsveç şirket eki)
    .replace(/\s+A\.Ş\.\s*$/i, '')    // trailing " A.Ş."
    .replace(/\s+LTD\s*$/i, '')       // trailing " LTD"
    .replace(/\s+taksidi\s*$/i, '')   // trailing " taksidi"
    .replace(/ab\s*$/i, '')            // yapışık "ab" (Spotifyab → Spotify)
    .replace(/\s+/g, ' ')
    .trim() || '—';
}

// ─── Roboto font yükleme (Türkçe karakter desteği) ───────────────────────────
// public/fonts/ klasöründen yüklenir — CDN veya CORS sorunu yok
async function loadFont(doc) {
  const REGULAR = '/fonts/Roboto-Regular.ttf';
  const BOLD    = '/fonts/Roboto-Bold.ttf';
  try {
    const [rRes, bRes] = await Promise.all([fetch(REGULAR), fetch(BOLD)]);
    if (!rRes.ok || !bRes.ok) throw new Error('Font indirilemedi');

    const toB64 = async (res) => {
      const bytes = new Uint8Array(await res.arrayBuffer());
      let s = '';
      for (let i = 0; i < bytes.length; i += 8192)
        s += String.fromCharCode(...bytes.subarray(i, i + 8192));
      return btoa(s);
    };

    const [rb64, bb64] = await Promise.all([toB64(rRes), toB64(bRes)]);
    doc.addFileToVFS('Roboto-Regular.ttf', rb64);
    doc.addFont('Roboto-Regular.ttf', 'Roboto', 'normal');
    doc.addFileToVFS('Roboto-Bold.ttf', bb64);
    doc.addFont('Roboto-Bold.ttf', 'Roboto', 'bold');
    return 'Roboto';
  } catch {
    return 'helvetica'; // CORS veya ağ hatası → ASCII fallback
  }
}

// ─── Bölüm başlığı ───────────────────────────────────────────────────────────
function sectionTitle(doc, font, text, y, margin) {
  doc.setFillColor(...INDIGO);
  doc.roundedRect(margin, y - 4, 4, 8, 1, 1, 'F');
  doc.setTextColor(...INDIGO);
  doc.setFontSize(11);
  doc.setFont(font, 'bold');
  doc.text(text, margin + 7, y + 0.5);
  return y + 7;
}

// ─── Hero kartlar (Mali Özet üstteki büyük metrikler) ────────────────────────
function drawHeroCards(doc, font, cards, y, margin, PW) {
  const gap   = 5;
  const cardW = (PW - 2 * margin - gap * (cards.length - 1)) / cards.length;
  const cardH = 24;

  cards.forEach(({ label, value, accent }, i) => {
    const x = margin + i * (cardW + gap);

    // Kart arka planı
    doc.setFillColor(244, 245, 255);
    doc.roundedRect(x, y, cardW, cardH, 2, 2, 'F');

    // Renkli üst şerit
    doc.setFillColor(...accent);
    doc.roundedRect(x, y, cardW, 3.5, 1, 1, 'F');

    // Tutar
    doc.setFont(font, 'bold');
    doc.setFontSize(10);
    doc.setTextColor(...accent);
    doc.text(value, x + cardW / 2, y + 13, { align: 'center' });

    // Etiket
    doc.setFont(font, 'normal');
    doc.setFontSize(7.5);
    doc.setTextColor(...ZINC_400);
    doc.text(safe(font, label), x + cardW / 2, y + 20, { align: 'center' });
  });

  return y + cardH + 8;
}

// ─── Pasta dilimi çizer (triangle yaklaşımı) ─────────────────────────────────
function drawPieSlice(doc, cx, cy, r, startDeg, endDeg, color) {
  doc.setFillColor(...color);
  const steps = Math.max(4, Math.ceil(Math.abs(endDeg - startDeg) / 4));
  for (let i = 0; i < steps; i++) {
    const a1 = ((startDeg + (endDeg - startDeg) * i / steps - 90) * Math.PI) / 180;
    const a2 = ((startDeg + (endDeg - startDeg) * (i + 1) / steps - 90) * Math.PI) / 180;
    doc.triangle(
      cx, cy,
      cx + r * Math.cos(a1), cy + r * Math.sin(a1),
      cx + r * Math.cos(a2), cy + r * Math.sin(a2),
      'F',
    );
  }
}

// ─── Pasta grafik + renk anahtarı ────────────────────────────────────────────
function drawPieChart(doc, font, categories, totalSpending, y, margin, PW) {
  const R   = 26;          // yarıçap (mm)
  const cx  = PW / 2;
  const cy  = y + R + 3;

  // Dilimler
  const sorted = Object.entries(categories)
    .sort(([, a], [, b]) => Number(b) - Number(a));

  let angle = 0;
  sorted.forEach(([, value], i) => {
    const pct = totalSpending > 0 ? Number(value) / totalSpending : 0;
    const deg = pct * 360;
    if (deg > 0.5) {
      drawPieSlice(doc, cx, cy, R, angle, angle + deg, PIE_COLORS[i % PIE_COLORS.length]);
      angle += deg;
    }
  });

  // Donut deliği
  doc.setFillColor(...WHITE);
  doc.circle(cx, cy, R * 0.42, 'F');

  // Orta yazı
  doc.setFont(font, 'bold');
  doc.setFontSize(8);
  doc.setTextColor(...ZINC_700);
  doc.text('Toplam', cx, cy - 2, { align: 'center' });
  doc.setFont(font, 'normal');
  doc.setFontSize(7);
  doc.text(fmt(totalSpending), cx, cy + 3.5, { align: 'center' });

  // Renk anahtarı — pasta altında 2 sütun
  const legendY0  = cy + R + 8;
  const colW      = (PW - 2 * margin) / 2;
  doc.setFontSize(7.5);

  sorted.forEach(([name, value], i) => {
    const pct   = totalSpending > 0 ? ((Number(value) / totalSpending) * 100).toFixed(1) : '0.0';
    const col   = i % 2;
    const row   = Math.floor(i / 2);
    const lx    = margin + col * colW;
    const ly    = legendY0 + row * 6.5;
    const color = PIE_COLORS[i % PIE_COLORS.length];

    // Renk karesi
    doc.setFillColor(...color);
    doc.roundedRect(lx, ly - 2.5, 3.5, 3.5, 0.8, 0.8, 'F');

    // İsim + yüzde
    doc.setFont(font, 'normal');
    doc.setTextColor(...ZINC_700);
    doc.text(
      `${safe(font, name)}  %${pct}`,
      lx + 6, ly,
      { maxWidth: colW - 10 },
    );
  });

  const rows = Math.ceil(sorted.length / 2);
  return legendY0 + rows * 6.5 + 8;
}

// ─── Ortak autoTable ayarları ─────────────────────────────────────────────────
const tableDefaults = (doc, font, startY, margin) => ({
  startY,
  margin: { left: margin, right: margin },
  theme: 'striped',
  styles: {
    font,
    fontSize: 9,
    textColor: ZINC_700,
    cellPadding: { top: 4, bottom: 4, left: 5, right: 5 },
  },
  headStyles: {
    fillColor: INDIGO,
    textColor: WHITE,
    fontStyle: 'bold',
    fontSize: 9.5,
  },
  alternateRowStyles: { fillColor: INDIGO_LIGHT },
  tableLineColor: [220, 220, 230],
  tableLineWidth: 0.15,
});

// ─────────────────────────────────────────────────────────────────────────────
// Ana fonksiyon
// ─────────────────────────────────────────────────────────────────────────────
export async function generateReport({ summary, transactions, user }) {
  const doc    = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
  const PW     = doc.internal.pageSize.getWidth();   // 210
  const PH     = doc.internal.pageSize.getHeight();  // 297
  const margin = 14;
  let y = 0;

  // ── Font yükle ───────────────────────────────────────────────────────────────
  const font = await loadFont(doc);

  // ── Abonelikler ───────────────────────────────────────────────────────────────
  let subscriptions = [];
  try {
    const res = await getSubscriptions();
    subscriptions = res.data || [];
  } catch { /* non-critical */ }

  // ── Hesaplamalar ──────────────────────────────────────────────────────────────
  const installments      = (transactions || []).filter(tx => tx.isInstallment || tx.installment);
  const installmentTotal  = installments.reduce((s, tx) => s + (Number(tx.amount) || 0), 0);
  const subscriptionTotal = subscriptions.reduce((s, tx) => s + (Number(tx.amount) || 0), 0);
  const totalSpending     = Number(summary?.totalSpending) || 0;

  // ═══════════════════════════════════════════════════════════════════════════
  // 1. HEADER BANDI
  // ═══════════════════════════════════════════════════════════════════════════
  doc.setFillColor(...INDIGO_DARK);
  doc.rect(0, 0, PW, 30, 'F');
  doc.setFillColor(...INDIGO);
  doc.rect(0, 24, PW, 6, 'F');

  doc.setTextColor(...WHITE);
  doc.setFontSize(16);
  doc.setFont(font, 'bold');
  doc.text('Smart Budget', margin, 13);

  doc.setFontSize(9.5);
  doc.setFont(font, 'normal');
  const subtitle = user?.fullName
    ? safe(font, `Finansal Analiz Raporu  ·  ${user.fullName}`)
    : safe(font, 'Finansal Analiz Raporu');
  doc.text(subtitle, margin, 21);

  const now     = new Date();
  const dateStr = now.toLocaleDateString('tr-TR', { day: '2-digit', month: 'long', year: 'numeric' });
  const timeStr = now.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });

  doc.setFontSize(9);
  doc.setTextColor(...WHITE);
  doc.text(safe(font, dateStr), PW - margin, 13, { align: 'right' });

  doc.setFontSize(8);
  doc.setTextColor(200, 210, 255);
  doc.text(timeStr, PW - margin, 21, { align: 'right' });

  y = 40;

  // ═══════════════════════════════════════════════════════════════════════════
  // 2. HERO KARTLAR — Mali Özet
  // ═══════════════════════════════════════════════════════════════════════════
  y = sectionTitle(doc, font, safe(font, 'Mali Özet'), y, margin);

  y = drawHeroCards(doc, font, [
    { label: 'Toplam Harcama', value: fmt(totalSpending),     accent: INDIGO },
    { label: 'Taksit Yükü',    value: fmt(installmentTotal),  accent: [220, 38, 38] },
    { label: safe(font, 'Aylık Abonelik'), value: fmt(subscriptionTotal), accent: [5, 150, 105] },
  ], y + 2, margin, PW);

  // Bütçe bilgisi varsa ince bir satır ekle
  if (summary?.monthlyBudget) {
    const remaining = Number(summary.monthlyBudget) - totalSpending;
    doc.setFont(font, 'normal');
    doc.setFontSize(8.5);
    doc.setTextColor(...ZINC_700);
    const budgetLine = safe(font,
      `Aylık Bütçe: ${fmt(summary.monthlyBudget)}  ·  ` +
      `${remaining >= 0 ? 'Kalan' : 'Bütçe Aşımı'}: ${fmt(Math.abs(remaining))}`
    );
    doc.text(budgetLine, PW / 2, y, { align: 'center' });
    y += 9;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // 3. KATEGORİ DAĞILIMI — Pasta grafik + tablo
  // ═══════════════════════════════════════════════════════════════════════════
  if (y > 200) { doc.addPage(); y = 20; }
  y = sectionTitle(doc, font, safe(font, 'Kategori Dağılımı'), y, margin);

  const categories = summary?.categoryBreakdown || {};
  if (Object.keys(categories).length > 0 && totalSpending > 0) {
    y = drawPieChart(doc, font, categories, totalSpending, y + 2, margin, PW);
  }

  if (y > 215) { doc.addPage(); y = 20; }

  const categoryRows = Object.entries(categories)
    .sort(([, a], [, b]) => Number(b) - Number(a))
    .map(([name, value]) => {
      const pct = totalSpending > 0
        ? ((Number(value) / totalSpending) * 100).toFixed(1)
        : '0.0';
      return [safe(font, name), fmt(value), `%${pct}`];
    });

  autoTable(doc, {
    ...tableDefaults(doc, font, y, margin),
    head: [[safe(font, 'Kategori'), 'Tutar', 'Pay']],
    body: categoryRows,
    columnStyles: {
      1: { halign: 'right' },
      2: { halign: 'right', fontStyle: 'bold', textColor: INDIGO },
    },
    foot: [[
      { content: 'Toplam', styles: { fontStyle: 'bold' } },
      { content: fmt(totalSpending), styles: { fontStyle: 'bold', halign: 'right' } },
      { content: '%100', styles: { fontStyle: 'bold', halign: 'right' } },
    ]],
    footStyles: { fillColor: [240, 242, 255], textColor: ZINC_700 },
  });
  y = doc.lastAutoTable.finalY + 12;

  // ═══════════════════════════════════════════════════════════════════════════
  // 4. TAKSİTLİ İŞLEMLER
  // ═══════════════════════════════════════════════════════════════════════════
  if (installments.length > 0) {
    if (y > 235) { doc.addPage(); y = 20; }
    y = sectionTitle(doc, font, safe(font, 'Taksitli İşlemler'), y, margin);

    const installmentRows = [...installments]
      .sort((a, b) => new Date(b.date) - new Date(a.date))
      .map((tx) => [
        safe(font, cleanDescription(tx.description)),
        new Date(tx.date).toLocaleDateString('tr-TR'),
        tx.currentInstallment != null && tx.totalInstallments != null
          ? `${tx.currentInstallment} / ${tx.totalInstallments}` : '—',
        safe(font, tx.category || '—'),
        fmt(tx.amount),
      ]);

    autoTable(doc, {
      ...tableDefaults(doc, font, y, margin),
      head: [[safe(font, 'İşlem'), 'Tarih', 'Taksit', safe(font, 'Kategori'), 'Tutar']],
      body: installmentRows,
      columnStyles: {
        4: { halign: 'right' },
        2: { halign: 'center', fontStyle: 'bold', textColor: INDIGO },
      },
      foot: [[
        { content: safe(font, 'Toplam Taksit Yükü'), colSpan: 4, styles: { fontStyle: 'bold', halign: 'right' } },
        { content: fmt(installmentTotal), styles: { fontStyle: 'bold', halign: 'right', textColor: INDIGO } },
      ]],
      footStyles: { fillColor: [240, 242, 255] },
    });
    y = doc.lastAutoTable.finalY + 12;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // 5. ABONELİKLER
  // ═══════════════════════════════════════════════════════════════════════════
  if (subscriptions.length > 0) {
    if (y > 235) { doc.addPage(); y = 20; }
    y = sectionTitle(doc, font, safe(font, 'Abonelikler'), y, margin);

    const subRows = subscriptions.map((tx) => [
      safe(font, cleanDescription(tx.description)),
      safe(font, tx.category || '—'),
      fmt(tx.amount),
    ]);

    autoTable(doc, {
      ...tableDefaults(doc, font, y, margin),
      head: [[safe(font, 'Abonelik'), safe(font, 'Kategori'), safe(font, 'Aylık Tutar')]],
      body: subRows,
      columnStyles: { 2: { halign: 'right' } },
      foot: [[
        { content: safe(font, 'Aylık Toplam'), colSpan: 2, styles: { fontStyle: 'bold', halign: 'right' } },
        { content: fmt(subscriptionTotal), styles: { fontStyle: 'bold', halign: 'right', textColor: INDIGO } },
      ]],
      footStyles: { fillColor: [240, 242, 255] },
    });
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // 6. FOOTER — tüm sayfalara, sağa hizalı
  // ═══════════════════════════════════════════════════════════════════════════
  const pageCount = doc.internal.getNumberOfPages();
  for (let i = 1; i <= pageCount; i++) {
    doc.setPage(i);

    doc.setDrawColor(220, 220, 230);
    doc.setLineWidth(0.3);
    doc.line(margin, PH - 14, PW - margin, PH - 14);

    doc.setFont(font, 'normal');
    doc.setFontSize(7);
    doc.setTextColor(...ZINC_400);

    // Sol: marka adı + tarih
    doc.text(`Smart Budget  ·  ${safe(font, dateStr)}`, margin, PH - 9);

    // Sağ: Sayfa N / M
    doc.text(`Sayfa ${i} / ${pageCount}`, PW - margin, PH - 9, { align: 'right' });
  }

  doc.save('smart-budgeting-analiz-raporu.pdf');
}
