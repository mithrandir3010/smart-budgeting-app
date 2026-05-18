package com.mali.smartbudget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String FROM_ADDRESS = "Smart Budget <noreply@smartbudgetr.com>";

    private final SesClient sesClient;

    @Value("${app.base-url}")
    private String baseUrl;

    @Async("emailTaskExecutor")
    public void sendVerificationEmail(String toEmail, String username, String token) {
        String verifyUrl = baseUrl + "/verify-email?token=" + token;
        send(toEmail, "Smart Budget — E-posta adresinizi doğrulayın", buildVerifyHtml(username, verifyUrl));
        log.info("Doğrulama e-postası gönderildi: {}", toEmail);
    }

    @Async("emailTaskExecutor")
    public void sendPasswordResetEmail(String toEmail, String username, String token) {
        String resetUrl = baseUrl + "/reset-password?token=" + token;
        send(toEmail, "Smart Budget — Şifre Sıfırlama", buildResetHtml(username, resetUrl));
        log.info("Şifre sıfırlama e-postası gönderildi: {}", toEmail);
    }

    private void send(String toEmail, String subject, String html) {
        SendEmailRequest request = SendEmailRequest.builder()
                .source(FROM_ADDRESS)
                .destination(Destination.builder().toAddresses(toEmail).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder()
                                .html(Content.builder().data(html).charset("UTF-8").build())
                                .build())
                        .build())
                .build();

        try {
            sesClient.sendEmail(request);
        } catch (SesException e) {
            log.error("E-posta gönderilemedi [{}]: {}", toEmail, e.awsErrorDetails().errorMessage());
        }
    }

    private String buildVerifyHtml(String username, String verifyUrl) {
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
}
