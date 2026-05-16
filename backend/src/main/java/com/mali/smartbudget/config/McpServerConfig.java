package com.mali.smartbudget.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mali.smartbudget.mcp.SerenaTools;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

/**
 * Serena MCP Server konfigürasyonu.
 *
 * <p>Bu sınıf, Spring Boot uygulamasını bir MCP (Model Context Protocol) sunucusuna
 * dönüştürür. Claude Desktop veya Claude Code bu sunucuya bağlanarak Serena'nın
 * finansal analiz araçlarına erişebilir.
 *
 * <h3>Token Optimizasyonu</h3>
 * Geleneksel yaklaşımda tüm işlem geçmişi her prompt'a eklenirdi (~2000+ token).
 * MCP sayesinde Claude, ihtiyaç duyduğu veriyi on-demand olarak çeker:
 * <ul>
 *   <li>Sadece bütçe özeti istiyorsa → {@code serena_get_budget_summary}</li>
 *   <li>Kategori detayına bakacaksa → {@code serena_get_category_breakdown}</li>
 *   <li>İşlem listesi lazımsa → {@code serena_get_transactions}</li>
 * </ul>
 *
 * <h3>Claude Desktop Bağlantısı</h3>
 * {@code ~/Library/Application Support/Claude/claude_desktop_config.json} dosyasına
 * eklenecek konfigürasyon:
 * <pre>{@code
 * {
 *   "mcpServers": {
 *     "serena-budget": {
 *       "url": "http://localhost:8080/mcp/sse"
 *     }
 *   }
 * }
 * }</pre>
 *
 * <h3>Claude Code Bağlantısı</h3>
 * Terminalde:
 * <pre>{@code
 * claude mcp add serena-budget --url http://localhost:8080/mcp/sse
 * }</pre>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class McpServerConfig {

    /**
     * MCP transportu: Spring MVC üzerinde SSE + HTTP mesajlaşma sağlar.
     *
     * <p>SDK 0.8.1'de 3-parametreli constructor kullanarak hem SSE hem mesaj
     * endpoint'lerini tam olarak belirtiyoruz:
     * <ul>
     *   <li>GET  /mcp/sse      — Claude'un bağlandığı SSE akışı</li>
     *   <li>POST /mcp/message  — Claude'un tool-call mesajlarını gönderdiği endpoint</li>
     * </ul>
     *
     * <p>NOT: 2-parametreli constructor {@code DEFAULT_SSE_ENDPOINT = "/sse"} kullanır.
     */
    @Bean
    public WebMvcSseServerTransportProvider mcpTransportProvider(ObjectMapper objectMapper) {
        log.info("Serena MCP Server transport başlatıldı — SSE: /mcp/sse | Message: /mcp/message");
        // (objectMapper, messageEndpoint, sseEndpoint)
        return new WebMvcSseServerTransportProvider(objectMapper, "/mcp/message", "/mcp/sse");
    }

    /**
     * Spring MVC RouterFunction: transport'un SSE ve mesaj endpoint'lerini Spring'e kaydeder.
     */
    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(
            WebMvcSseServerTransportProvider transport) {
        return transport.getRouterFunction();
    }

    /**
     * MCP sync server: tool tanımlarını transport'a bağlar ve sunucuyu başlatır.
     *
     * <p>Sync (blocking) mod seçildi çünkü mevcut servisler (@Transactional + JPA)
     * zaten blocking I/O kullanıyor; reactive'e geçiş gereksiz karmaşıklık yaratırdı.
     */
    @Bean
    public McpSyncServer mcpSyncServer(
            WebMvcSseServerTransportProvider transport,
            SerenaTools serenaTools) {

        List<McpServerFeatures.SyncToolSpecification> tools = serenaTools.allTools();

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("serena-budget-server", "1.0.0")
                // tools(Boolean) → SDK dahili olarak ToolCapabilities(listChanged=false) oluşturur
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(false)
                        .build())
                .tools(tools)
                .build();

        log.info("Serena MCP Server hazır — {} tool kayıtlı: {}",
                tools.size(),
                tools.stream()
                     .map(t -> t.tool().name())
                     .toList());

        return server;
    }
}
