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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResendEmailServiceTest {

    @Mock
    private RestOperations restTemplate;

    @Test
    @DisplayName("sendPriceDropAlert - skips sending when API key is empty or null")
    void sendPriceDropAlert_skipsWhenApiKeyEmpty() {
        ResendEmailService serviceWithNoKey = new ResendEmailService("", "PriceHawk <onboarding@resend.dev>", restTemplate);

        assertThatCode(() -> serviceWithNoKey.sendPriceDropAlert(
                "user@example.com", "iPhone 15", new BigDecimal("70000.00"),
                new BigDecimal("64999.00"), "https://amazon.in/dp/test"
        )).doesNotThrowAnyException();

        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("sendPriceDropAlert - sends POST request to Resend API when API key is present")
    void sendPriceDropAlert_sendsEmailViaResendApi() {
        ResendEmailService service = new ResendEmailService(
                "re_test_key_12345",
                "PriceHawk <alerts@pricehawk.com>",
                restTemplate
        );

        when(restTemplate.exchange(
                eq("https://api.resend.com/emails"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"id\": \"msg_123\"}"));

        service.sendPriceDropAlert(
                "buyer@example.com",
                "Apple MacBook Air M2",
                new BigDecimal("99999.00"),
                new BigDecimal("89999.00"),
                "https://amazon.in/dp/macbook"
        );

        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://api.resend.com/emails"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(String.class)
        );

        HttpEntity<Map<String, Object>> captured = entityCaptor.getValue();
        HttpHeaders headers = captured.getHeaders();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer re_test_key_12345");
        assertThat(headers.getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/json");

        Map<String, Object> body = captured.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("from")).isEqualTo("PriceHawk <alerts@pricehawk.com>");
        assertThat(body.get("to")).isEqualTo(new String[]{"buyer@example.com"});
        assertThat((String) body.get("subject")).contains("Apple MacBook Air M2").contains("89999.00");
        assertThat((String) body.get("html")).contains("Apple MacBook Air M2").contains("89999.00").contains("https://amazon.in/dp/macbook");
    }
}
