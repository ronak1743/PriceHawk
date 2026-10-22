package com.pricehawk.service.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrevoEmailServiceTest {

    @Mock
    private RestOperations restTemplate;

    @Mock
    private EmailService fallbackService;

    @Test
    @DisplayName("sendPriceDropAlert - delegates to fallback Resend service when Brevo API key is empty")
    void sendPriceDropAlert_delegatesToFallbackWhenApiKeyEmpty() {
        BrevoEmailService service = new BrevoEmailService(
                "",
                "ronakg036@gmail.com",
                "PriceHawk",
                restTemplate,
                fallbackService
        );

        service.sendPriceDropAlert(
                "user@example.com", "HP 15 Laptop", new BigDecimal("76769.00"),
                new BigDecimal("71850.00"), "https://amazon.in/dp/test"
        );

        verify(fallbackService, times(1)).sendPriceDropAlert(
                "user@example.com", "HP 15 Laptop", new BigDecimal("76769.00"),
                new BigDecimal("71850.00"), "https://amazon.in/dp/test"
        );
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("sendPriceDropAlert - sends POST request to Brevo API when API key is present")
    void sendPriceDropAlert_sendsEmailViaBrevoApi() {
        BrevoEmailService service = new BrevoEmailService(
                "xkeysib-test-key-12345",
                "alerts@pricehawk.com",
                "PriceHawk Sentinel",
                restTemplate,
                fallbackService
        );

        when(restTemplate.exchange(
                eq("https://api.brevo.com/v3/smtp/email"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"messageId\": \"<msg-123@smtp-relay.mailin.fr>\"}"));

        service.sendPriceDropAlert(
                "buyer@gmail.com",
                "HP 15 Laptop",
                new BigDecimal("76769.00"),
                new BigDecimal("71850.00"),
                "https://amazon.in/dp/hp15"
        );

        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://api.brevo.com/v3/smtp/email"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(String.class)
        );

        HttpEntity<Map<String, Object>> captured = entityCaptor.getValue();
        HttpHeaders headers = captured.getHeaders();
        assertThat(headers.getFirst("api-key")).isEqualTo("xkeysib-test-key-12345");
        assertThat(headers.getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/json");

        Map<String, Object> body = captured.getBody();
        assertThat(body).isNotNull();

        Map<String, String> sender = (Map<String, String>) body.get("sender");
        assertThat(sender.get("name")).isEqualTo("PriceHawk Sentinel");
        assertThat(sender.get("email")).isEqualTo("alerts@pricehawk.com");

        List<Map<String, String>> recipients = (List<Map<String, String>>) body.get("to");
        assertThat(recipients).hasSize(1);
        assertThat(recipients.get(0).get("email")).isEqualTo("buyer@gmail.com");

        assertThat((String) body.get("subject")).contains("HP 15 Laptop").contains("71850.00");
        assertThat((String) body.get("htmlContent")).contains("HP 15 Laptop").contains("71850.00");

        // Verify fallback is never invoked when Brevo key is configured
        verify(fallbackService, never()).sendPriceDropAlert(any(), any(), any(), any(), any());
    }
}
