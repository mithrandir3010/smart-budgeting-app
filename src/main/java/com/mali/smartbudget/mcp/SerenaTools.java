package com.mali.smartbudget.mcp;

import com.mali.smartbudget.dto.AnalyticsSummaryDto;
import com.mali.smartbudget.model.Transaction;
import com.mali.smartbudget.service.AnalyticsService;
import com.mali.smartbudget.service.TransactionService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serena'nın MCP tool tanımları.
 *
 * <h3>Token Optimizasyon Stratejisi</h3>
 * <pre>
 * ÖNCE (standart LLM):
 *   System prompt → [500 işlem satırı] [tüm kategoriler] [tüm geçmiş] → ~4000 token/istek
 *
 * SONRA (MCP):
 *   System prompt → "Serena, bütçe asistanısın. Veri için tool'ları kullan." → ~50 token
 *   Claude gerektiğinde → serena_get_budget_summary()     → sadece özet
 *                       → serena_get_category_breakdown() → sadece kategoriler
 *                       → serena_get_transactions(limit=5) → en son 5 işlem
 * </pre>
 *
 * <h3>Araçlar</h3>
 * <ol>
 *   <li>{@code serena_get_budget_summary}     — Toplam harcama, tahmin, uyarı, koçluk tavsiyesi</li>
 *   <li>{@code serena_get_category_breakdown} — Kategori bazlı dökümü (yüzdeler dahil)</li>
 *   <li>{@code serena_get_transactions}       — İşlem listesi (opsiyonel: kategori ve limit filtresi)</li>
 *   <li>{@code serena_get_savings_tips}       — Kural tabanlı tasarruf önerileri</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SerenaTools {

    private final AnalyticsService analyticsService;
    private final TransactionService transactionService;

    // -------------------------------------------------------------------------
    // Tool listesi — McpServerConfig tarafından çekilir
    // -------------------------------------------------------------------------

    public List<McpServerFeatures.SyncToolSpecification> allTools() {
        return List.of(
                budgetSummaryTool(),
                categoryBreakdownTool(),
                transactionsTool(),
                savingsTipsTool(),
                subscriptionsTool()
        );
    }

    // =========================================================================
    // TOOL 1 — serena_get_budget_summary
    // =========================================================================

    /**
     * Kullanıcının anlık bütçe durumunu döner.
     * Token açısından en verimli araçtır; çoğu soruya bu tool tek başına yetişir.
     */
    private McpServerFeatures.SyncToolSpecification budgetSummaryTool() {
        Tool tool = buildTool(
                "serena_get_budget_summary",
                "Kullanıcının mevcut bütçe özetini döner: toplam harcama, aylık limit," +
                " ay sonu tahmini, günlük harcama hızı, limit aşım uyarısı ve" +
                " Serena'nın kişisel koçluk tavsiyesi. Kullanıcıyla harcama hakkında" +
                " konuşmadan önce bu tool'u çağır.",
                """
                {
                  "type": "object",
                  "properties": {
                    "userId": {
                      "type": "number",
                      "description": "Sorgulanacak kullanıcı ID'si"
                    }
                  },
                  "required": ["userId"]
                }
                """
        );

        return new McpServerFeatures.SyncToolSpecification(tool,
                (McpSyncServerExchange exchange, Map<String, Object> args) -> {
                    Long userId = toLong(args.get("userId"));
                    log.debug("[MCP] serena_get_budget_summary → userId={}", userId);

                    try {
                        AnalyticsSummaryDto summary = analyticsService.getSummary(userId);
                        String result = formatBudgetSummary(summary);
                        return ok(result);
                    } catch (Exception e) {
                        log.error("[MCP] Budget summary hatası userId={}", userId, e);
                        return error("Bütçe özeti alınamadı: " + e.getMessage());
                    }
                });
    }

    private String formatBudgetSummary(AnalyticsSummaryDto s) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Serena Bütçe Özeti\n\n");
        sb.append("- **Toplam Harcama**: ").append(formatTRY(s.totalSpending())).append("\n");
        sb.append("- **Günlük Harcama Hızı**: ").append(formatTRY(s.dailyRate())).append("/gün\n");
        sb.append("- **Ay Sonu Tahmini**: ").append(formatTRY(s.projectedSpending())).append("\n");

        if (s.monthlyBudget() != null) {
            sb.append("- **Aylık Limit**: ").append(formatTRY(s.monthlyBudget())).append("\n");

            BigDecimal remaining = s.monthlyBudget().subtract(s.totalSpending());
            sb.append("- **Kalan Limit**: ").append(formatTRY(remaining)).append("\n");

            if (s.monthlyBudget().compareTo(BigDecimal.ZERO) > 0) {
                double usedPct = s.totalSpending()
                        .divide(s.monthlyBudget(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
                sb.append("- **Limit Kullanımı**: %.0f%%\n".formatted(usedPct));
            }
        } else {
            sb.append("- **Aylık Limit**: Belirlenmemiş (Dashboard'dan ayarlayabilirsin)\n");
        }

        if (s.warning() != null) {
            sb.append("\n⚠️ **UYARI**: ").append(s.warning()).append("\n");
        }

        if (s.coachAdvice() != null) {
            sb.append("\n💬 **Serena'nın Tavsiyesi**: ").append(s.coachAdvice()).append("\n");
        }

        return sb.toString();
    }

    // =========================================================================
    // TOOL 2 — serena_get_category_breakdown
    // =========================================================================

    /**
     * Kategoriler bazında harcama dökümünü yüzdelerle birlikte döner.
     * Hangi kategorinin bütçeyi yaktığını bulmak için kullan.
     */
    private McpServerFeatures.SyncToolSpecification categoryBreakdownTool() {
        Tool tool = buildTool(
                "serena_get_category_breakdown",
                "Kullanıcının harcamalarını kategorilere göre sıralar. Her kategori için" +
                " toplam tutar ve toplam harcama içindeki yüzde değerini döner." +
                " Hangi kategorinin bütçeyi en çok tükettiğini analiz etmek için kullan.",
                """
                {
                  "type": "object",
                  "properties": {
                    "userId": {
                      "type": "number",
                      "description": "Sorgulanacak kullanıcı ID'si"
                    }
                  },
                  "required": ["userId"]
                }
                """
        );

        return new McpServerFeatures.SyncToolSpecification(tool,
                (McpSyncServerExchange exchange, Map<String, Object> args) -> {
                    Long userId = toLong(args.get("userId"));
                    log.debug("[MCP] serena_get_category_breakdown → userId={}", userId);

                    try {
                        AnalyticsSummaryDto summary = analyticsService.getSummary(userId);
                        String result = formatCategoryBreakdown(
                                summary.categoryBreakdown(), summary.totalSpending());
                        return ok(result);
                    } catch (Exception e) {
                        log.error("[MCP] Category breakdown hatası userId={}", userId, e);
                        return error("Kategori dökümü alınamadı: " + e.getMessage());
                    }
                });
    }

    private String formatCategoryBreakdown(Map<String, BigDecimal> breakdown,
                                           BigDecimal totalSpending) {
        if (breakdown.isEmpty()) {
            return "Henüz harcama verisi yok.";
        }

        StringBuilder sb = new StringBuilder("## Kategori Bazlı Harcama Dökümü\n\n");
        sb.append("| Kategori | Tutar | Pay |\n");
        sb.append("|----------|-------|-----|\n");

        breakdown.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(entry -> {
                    double pct = totalSpending.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                            entry.getValue()
                                 .divide(totalSpending, 4, RoundingMode.HALF_UP)
                                 .multiply(BigDecimal.valueOf(100))
                                 .doubleValue();
                    sb.append("| %-18s | %12s | %5.1f%% |\n"
                            .formatted(entry.getKey(), formatTRY(entry.getValue()), pct));
                });

        sb.append("\n**Toplam**: ").append(formatTRY(totalSpending));
        return sb.toString();
    }

    // =========================================================================
    // TOOL 3 — serena_get_transactions
    // =========================================================================

    /**
     * İşlem listesini döner. Opsiyonel kategori filtresi ve limit parametresi desteklenir.
     * Tüm geçmişi değil, ihtiyaç kadarını çek — token tasarrufu burada kritik.
     */
    private McpServerFeatures.SyncToolSpecification transactionsTool() {
        Tool tool = buildTool(
                "serena_get_transactions",
                "Kullanıcının banka işlemlerini listeler. İsteğe bağlı olarak belirli" +
                " bir kategori ile filtrelenebilir ve döndürülecek kayıt sayısı" +
                " limit parametresi ile sınırlandırılabilir. Token verimliliği için" +
                " varsayılan limit 20'dir; tüm veri için limit=0 gönder.",
                """
                {
                  "type": "object",
                  "properties": {
                    "userId": {
                      "type": "number",
                      "description": "Kullanıcı ID'si"
                    },
                    "category": {
                      "type": "string",
                      "description": "Opsiyonel kategori filtresi (ör: Market, Kafe, Kira)"
                    },
                    "limit": {
                      "type": "number",
                      "description": "Döndürülecek maksimum işlem sayısı (varsayılan: 20, tümü için: 0)"
                    }
                  },
                  "required": ["userId"]
                }
                """
        );

        return new McpServerFeatures.SyncToolSpecification(tool,
                (McpSyncServerExchange exchange, Map<String, Object> args) -> {
                    Long userId = toLong(args.get("userId"));
                    String categoryFilter = args.get("category") != null
                            ? (String) args.get("category") : null;
                    int limit = args.get("limit") != null
                            ? ((Number) args.get("limit")).intValue() : 20;

                    log.debug("[MCP] serena_get_transactions → userId={}, category={}, limit={}",
                            userId, categoryFilter, limit);

                    try {
                        List<Transaction> txList =
                                transactionService.getTransactionsByUser(userId);

                        if (categoryFilter != null && !categoryFilter.isBlank()) {
                            txList = txList.stream()
                                    .filter(t -> categoryFilter.equalsIgnoreCase(t.getCategory()))
                                    .collect(Collectors.toList());
                        }

                        if (limit > 0 && txList.size() > limit) {
                            txList = txList.subList(0, limit);
                        }

                        String result = formatTransactions(txList, categoryFilter, limit);
                        return ok(result);
                    } catch (Exception e) {
                        log.error("[MCP] Transaction listesi hatası userId={}", userId, e);
                        return error("İşlem listesi alınamadı: " + e.getMessage());
                    }
                });
    }

    private String formatTransactions(List<Transaction> txList,
                                      String categoryFilter, int limit) {
        if (txList.isEmpty()) {
            return categoryFilter != null
                    ? "'%s' kategorisinde işlem bulunamadı.".formatted(categoryFilter)
                    : "Henüz işlem kaydı yok.";
        }

        StringBuilder sb = new StringBuilder();
        if (categoryFilter != null) {
            sb.append("## '%s' Kategorisi İşlemleri".formatted(categoryFilter));
        } else {
            sb.append("## İşlem Listesi");
        }
        if (limit > 0) sb.append(" (son %d)".formatted(limit));
        sb.append("\n\n");

        sb.append("| Tarih | Açıklama | Tutar | Kategori |\n");
        sb.append("|-------|----------|-------|----------|\n");

        txList.forEach(t -> sb.append("| %s | %-35s | %12s | %s |\n".formatted(
                t.getDate(),
                truncate(t.getDescription(), 35),
                formatTRY(t.getAmount()),
                t.getCategory() != null ? t.getCategory() : "—"
        )));

        BigDecimal total = txList.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        sb.append("\n**%d işlem toplamı**: %s".formatted(txList.size(), formatTRY(total)));

        return sb.toString();
    }

    // =========================================================================
    // TOOL 4 — serena_get_savings_tips
    // =========================================================================

    /**
     * Kullanıcının harcama profiline göre kural tabanlı tasarruf önerileri üretir.
     * Değiştirilebilir harcamalar (kira hariç) üzerinden akıllı tavsiye üretilir.
     */
    private McpServerFeatures.SyncToolSpecification savingsTipsTool() {
        Tool tool = buildTool(
                "serena_get_savings_tips",
                "Kullanıcının harcama alışkanlıklarına göre kişisel tasarruf önerileri" +
                " oluşturur. Sabit giderler (kira vb.) hariç tutulur; yalnızca" +
                " değiştirilebilir kategoriler analiz edilir. Opsiyonel hedef tasarruf" +
                " miktarı belirtilirse, o hedefe nasıl ulaşılacağı hesaplanır.",
                """
                {
                  "type": "object",
                  "properties": {
                    "userId": {
                      "type": "number",
                      "description": "Kullanıcı ID'si"
                    },
                    "targetSavings": {
                      "type": "number",
                      "description": "Opsiyonel: bu ay tasarruf etmek istenen TL miktarı"
                    }
                  },
                  "required": ["userId"]
                }
                """
        );

        return new McpServerFeatures.SyncToolSpecification(tool,
                (McpSyncServerExchange exchange, Map<String, Object> args) -> {
                    Long userId = toLong(args.get("userId"));
                    BigDecimal target = args.get("targetSavings") != null
                            ? new BigDecimal(args.get("targetSavings").toString()) : null;

                    log.debug("[MCP] serena_get_savings_tips → userId={}, target={}", userId, target);

                    try {
                        AnalyticsSummaryDto summary = analyticsService.getSummary(userId);
                        String result = buildSavingsTips(summary, target);
                        return ok(result);
                    } catch (Exception e) {
                        log.error("[MCP] Savings tips hatası userId={}", userId, e);
                        return error("Tasarruf önerileri oluşturulamadı: " + e.getMessage());
                    }
                });
    }

    private static final java.util.Set<String> FIXED_CATEGORIES =
            java.util.Set.of("Kira", "Sigorta", "Kredi");

    private String buildSavingsTips(AnalyticsSummaryDto summary, BigDecimal target) {
        Map<String, BigDecimal> variable = summary.categoryBreakdown().entrySet().stream()
                .filter(e -> !FIXED_CATEGORIES.contains(e.getKey()))
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        java.util.LinkedHashMap::new
                ));

        if (variable.isEmpty()) {
            return "Değiştirilebilir harcama kategorisi bulunamadı.";
        }

        StringBuilder sb = new StringBuilder("## Serena'nın Tasarruf Önerileri\n\n");

        if (target != null) {
            sb.append("**Hedef**: Bu ay ").append(formatTRY(target)).append(" tasarruf et\n\n");
        }

        sb.append("### Kısılabilecek Değişken Harcamalar\n\n");

        BigDecimal totalVariable = variable.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal accumulated = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entry : variable.entrySet()) {
            double pct = entry.getValue()
                    .divide(totalVariable, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();

            BigDecimal saving20 = entry.getValue()
                    .multiply(BigDecimal.valueOf(0.20))
                    .setScale(2, RoundingMode.HALF_UP);

            sb.append("**%s** (%s — %%.0f%% pay)\n".formatted(
                    entry.getKey(), formatTRY(entry.getValue()), pct));
            sb.append("→ %%20 kısılırsa: **%s tasarruf**\n\n".formatted(formatTRY(saving20)));

            if (target != null) {
                accumulated = accumulated.add(saving20);
                if (accumulated.compareTo(target) >= 0) {
                    sb.append("✅ Bu kategorilere %%20 kısıtlama uygulanırsa hedefe ulaşılır!\n\n");
                    break;
                }
            }
        }

        if (target != null && accumulated.compareTo(target) < 0) {
            sb.append("ℹ️ %%20 kısıntıyla toplam **%s** tasarruf edilebilir. ".formatted(
                    formatTRY(accumulated)));
            sb.append("Hedefe ulaşmak için daha agresif kısıntı ya da ek gelir gerekiyor.\n");
        }

        return sb.toString();
    }

    // =========================================================================
    // TOOL 5 — serena_get_subscriptions
    // =========================================================================

    /**
     * Kullanıcının tüm aboneliklerini (isSubscription=true) listeler ve aylık toplam tutar hesaplar.
     * Netflix, Spotify, iCloud gibi tekrarlayan ödemeleri tespit eder.
     */
    private McpServerFeatures.SyncToolSpecification subscriptionsTool() {
        Tool tool = buildTool(
                "serena_get_subscriptions",
                "Kullanıcının aktif aboneliklerini listeler (Netflix, Spotify, iCloud vb.)." +
                " Her bir aboneliğin tutarını ve aylık toplam abonelik masrafını döner." +
                " Otomatik ödemeleri analiz etmek veya kullanıcıya farkında olmadığı" +
                " abonelikleri göstermek için bu tool'u kullan.",
                """
                {
                  "type": "object",
                  "properties": {
                    "userId": {
                      "type": "number",
                      "description": "Sorgulanacak kullanıcı ID'si"
                    }
                  },
                  "required": ["userId"]
                }
                """
        );

        return new McpServerFeatures.SyncToolSpecification(tool,
                (McpSyncServerExchange exchange, Map<String, Object> args) -> {
                    Long userId = toLong(args.get("userId"));
                    log.debug("[MCP] serena_get_subscriptions → userId={}", userId);

                    try {
                        List<Transaction> subs = transactionService.getSubscriptionsByUser(userId);
                        String result = formatSubscriptions(subs);
                        return ok(result);
                    } catch (Exception e) {
                        log.error("[MCP] Subscriptions hatası userId={}", userId, e);
                        return error("Abonelik listesi alınamadı: " + e.getMessage());
                    }
                });
    }

    private String formatSubscriptions(List<Transaction> subs) {
        if (subs.isEmpty()) {
            return "Henüz tespit edilmiş bir abonelik yok.";
        }

        BigDecimal total = subs.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder sb = new StringBuilder("## Abonelik Listesi\n\n");
        sb.append("| Abonelik | Kategori | Tutar |\n");
        sb.append("|----------|----------|-------|\n");

        subs.forEach(t -> sb.append("| %-30s | %-15s | %12s |\n".formatted(
                truncate(t.getDescription(), 30),
                t.getCategory() != null ? t.getCategory() : "—",
                formatTRY(t.getAmount())
        )));

        sb.append("\n**Aylık Toplam Abonelik Masrafı**: ").append(formatTRY(total));
        sb.append("\n**Abonelik Sayısı**: ").append(subs.size());
        return sb.toString();
    }

    // =========================================================================
    // Yardımcı metodlar
    // =========================================================================

    /**
     * JSON şema string'ini kullanarak {@link Tool} objesi oluşturur.
     * SDK 0.8.1'de Tool(String, String, String) constructor'ı şemayı
     * dahili olarak McpSchema.JsonSchema'ya dönüştürür.
     */
    private Tool buildTool(String name, String description, String schemaJson) {
        return new Tool(name, description, schemaJson);
    }

    /** Başarılı tool sonucu döner. */
    private CallToolResult ok(String text) {
        return new CallToolResult(List.of(new TextContent(text)), false);
    }

    /** Hata sonucu döner (isError=true). */
    private CallToolResult error(String message) {
        return new CallToolResult(List.of(new TextContent(message)), true);
    }

    /** JSON'dan gelen Number veya String'i Long'a çevirir. */
    private Long toLong(Object value) {
        if (value == null) throw new IllegalArgumentException("userId eksik");
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    private String formatTRY(BigDecimal amount) {
        if (amount == null) return "0,00 TL";
        return "%,.2f TL".formatted(amount.doubleValue()).replace(",", ".");
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
