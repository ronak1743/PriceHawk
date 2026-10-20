package com.pricehawk.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class ResendEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final String apiKey;
    private final String fromEmail;
    private final RestOperations restTemplate;

    public ResendEmailService(
            @Value("${resend.api.key:}") String apiKey,
            @Value("${resend.from.email:PriceHawk <onboarding@resend.dev>}") String fromEmail,
            RestOperations restTemplate) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.fromEmail = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail.trim() : "PriceHawk <onboarding@resend.dev>";
        this.restTemplate = restTemplate;
    }

    @Override
    public void sendPriceDropAlert(String recipientEmail, String productTitle, BigDecimal oldPrice,
                                   BigDecimal newPrice, String productUrl) {
        if (apiKey.isEmpty()) {
            log.info("Resend API key is not configured. Skipping email to: {}", recipientEmail);
            return;
        }

        String subject = String.format("🚨 Price Drop Alert: %s is now ₹%s!", productTitle, newPrice);
        String htmlBody = buildHtmlEmail(productTitle, oldPrice, newPrice, productUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> payload = new HashMap<>();
        payload.put("from", fromEmail);
        payload.put("to", new String[]{recipientEmail});
        payload.put("subject", subject);
        payload.put("html", htmlBody);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    RESEND_API_URL,
                    HttpMethod.POST,
                    request,
                    String.class
            );
            log.info("Price drop alert email sent successfully via Resend to {}: HTTP {}", recipientEmail, response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to send price drop alert email to {}: {}", recipientEmail, e.getMessage());
        }
    }

    private String buildHtmlEmail(String productTitle, BigDecimal oldPrice, BigDecimal newPrice, String productUrl) {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset='utf-8'></head>" +
                "<body style='font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,sans-serif;background-color:#090d16;color:#f8fafc;padding:30px;'>" +
                "<div style='max-width:540px;margin:0 auto;background:#111827;border:1px solid rgba(255,255,255,0.1);border-radius:14px;padding:28px;'>" +
                "<div style='display:flex;align-items:center;margin-bottom:20px;'>" +
                "<span style='font-size:22px;font-weight:800;background:linear-gradient(135deg,#6366f1,#d946ef);-webkit-background-clip:text;-webkit-text-fill-color:transparent;'>PriceHawk</span>" +
                "<span style='margin-left:10px;font-size:12px;background:rgba(99,102,241,0.2);color:#a5b4fc;padding:3px 8px;border-radius:9999px;'>Price Drop Alert</span>" +
                "</div>" +
                "<h2 style='margin:0 0 12px;font-size:18px;color:#f8fafc;'>" + productTitle + "</h2>" +
                "<p style='color:#94a3b8;font-size:14px;margin-bottom:20px;'>Great news! A product you're tracking just dropped in price.</p>" +
                "<div style='background:rgba(15,23,42,0.8);border:1px solid rgba(255,255,255,0.06);border-radius:10px;padding:16px;margin-bottom:24px;'>" +
                "<div style='display:flex;align-items:baseline;gap:12px;'>" +
                "<span style='color:#ef4444;text-decoration:line-through;font-size:16px;'>₹" + oldPrice + "</span>" +
                "<span style='color:#10b981;font-size:26px;font-weight:800;margin-left:12px;'>₹" + newPrice + "</span>" +
                "</div>" +
                "</div>" +
                "<a href='" + productUrl + "' style='display:inline-block;background:linear-gradient(135deg,#6366f1,#8b5cf6,#d946ef);color:#ffffff;text-decoration:none;font-weight:700;padding:12px 24px;border-radius:8px;font-size:15px;'>View & Buy Now</a>" +
                "<hr style='border:none;border-top:1px solid rgba(255,255,255,0.08);margin:28px 0 16px;'/>" +
                "<p style='color:#64748b;font-size:12px;margin:0;'>PriceHawk Sentinel • Automated Price Drop Protection</p>" +
                "</div>" +
                "</body></html>";
    }
}
