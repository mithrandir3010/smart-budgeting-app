package com.mali.smartbudget.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmailService {

    private static final String RESEND_EMAILS_URL = "https://api.resend.com/emails";
    private static final String FROM_ADDRESS = "Smart Budget <noreply@smartbudgetr.com>";

    private final RestClient restClient;
    private final String baseUrl;

    public EmailService(
            @Value("${resend.api-key}") String apiKey,
            @Value("${app.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public void sendVerificationEmail(String toEmail, String username, String token) {
        String verifyUrl = baseUrl + "/verify-email?token=" + token;
        String html = buildHtml(username, verifyUrl);

        Map<String, Object> payload = Map.of(
                "from", FROM_ADDRESS,
                "to", List.of(toEmail),
                "subject", "Smart Budget — E-posta adresinizi doğrulayın",
                "html", html
        );

        try {
            restClient.post()
                    .uri(RESEND_EMAILS_URL)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Doğrulama e-postası gönderildi: {}", toEmail);
        } catch (Exception e) {
            log.error("Doğrulama e-postası gönderilemedi [{}]: {}", toEmail, e.getMessage());
            throw new RuntimeException("E-posta gönderilemedi. Lütfen daha sonra tekrar deneyin.");
        }
    }

    public void sendPasswordResetEmail(String toEmail, String username, String token) {
        String resetUrl = baseUrl + "/reset-password?token=" + token;

        Map<String, Object> payload = Map.of(
                "from", FROM_ADDRESS,
                "to", List.of(toEmail),
                "subject", "Smart Budget — Şifre Sıfırlama",
                "html", buildResetHtml(username, resetUrl)
        );

        try {
            restClient.post()
                    .uri(RESEND_EMAILS_URL)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Şifre sıfırlama e-postası gönderildi: {}", toEmail);
        } catch (Exception e) {
            log.error("Şifre sıfırlama e-postası gönderilemedi [{}]: {}", toEmail, e.getMessage());
            throw new RuntimeException("E-posta gönderilemedi. Lütfen daha sonra tekrar deneyin.");
        }
    }

    private String buildResetHtml(String username, String resetUrl) {
        return """
                <!DOCTYPE html>
                <html lang="tr">
                <head><meta charset="UTF-8"></head>
                <body style="font-family:Arial,sans-serif;background:#f4f4f4;margin:0;padding:0">
                  <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr><td align="center" style="padding:40px 0">
                      <table width="560" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:8px;padding:40px">
                        <tr><td>
                          <h2 style="color:#1a1a2e;margin-top:0">Şifre Sıfırlama</h2>
                          <p style="color:#555;font-size:15px">Merhaba <strong>%s</strong>,</p>
                          <p style="color:#555;font-size:15px">
                            Şifrenizi sıfırlamak için aşağıdaki butona tıklayın.
                            Link <strong>1 saat</strong> geçerlidir.
                          </p>
                          <div style="text-align:center;margin:32px 0">
                            <a href="%s"
                               style="background:#4f46e5;color:#ffffff;padding:14px 32px;
                                      border-radius:6px;text-decoration:none;font-size:15px;
                                      font-weight:bold">
                              Şifremi Sıfırla
                            </a>
                          </div>
                          <p style="color:#888;font-size:13px">
                            Butona tıklayamıyorsanız şu linki tarayıcınıza yapıştırın:<br>
                            <a href="%s" style="color:#4f46e5">%s</a>
                          </p>
                          <hr style="border:none;border-top:1px solid #eee;margin:24px 0">
                          <p style="color:#aaa;font-size:12px;text-align:center">
                            Bu isteği siz yapmadıysanız dikkate almayınız. Şifreniz değişmeyecektir.
                          </p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(username, resetUrl, resetUrl, resetUrl);
    }

    private String buildHtml(String username, String verifyUrl) {
        return """
                <!DOCTYPE html>
                <html lang="tr">
                <head><meta charset="UTF-8"></head>
                <body style="font-family:Arial,sans-serif;background:#f4f4f4;margin:0;padding:0">
                  <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr><td align="center" style="padding:40px 0">
                      <table width="560" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:8px;padding:40px">
                        <tr><td>
                          <h2 style="color:#1a1a2e;margin-top:0">Smart Budget'a Hoş Geldiniz</h2>
                          <p style="color:#555;font-size:15px">Merhaba <strong>%s</strong>,</p>
                          <p style="color:#555;font-size:15px">
                            Hesabınızı etkinleştirmek için aşağıdaki butona tıklayın.
                            Link <strong>24 saat</strong> geçerlidir.
                          </p>
                          <div style="text-align:center;margin:32px 0">
                            <a href="%s"
                               style="background:#4f46e5;color:#ffffff;padding:14px 32px;
                                      border-radius:6px;text-decoration:none;font-size:15px;
                                      font-weight:bold">
                              E-postamı Doğrula
                            </a>
                          </div>
                          <p style="color:#888;font-size:13px">
                            Butona tıklayamıyorsanız şu linki tarayıcınıza yapıştırın:<br>
                            <a href="%s" style="color:#4f46e5">%s</a>
                          </p>
                          <hr style="border:none;border-top:1px solid #eee;margin:24px 0">
                          <p style="color:#aaa;font-size:12px;text-align:center">
                            Bu e-postayı siz talep etmediyseniz dikkate almayınız.
                          </p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(username, verifyUrl, verifyUrl, verifyUrl);
    }
}
