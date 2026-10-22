package com.pricehawk.service;

import com.pricehawk.dto.PriceAlertResponse;
import com.pricehawk.dto.PriceHistoryResponse;
import com.pricehawk.exception.ResourceNotFoundException;
import com.pricehawk.model.*;
import com.pricehawk.repository.ItemSourceRepository;
import com.pricehawk.repository.PriceAlertRepository;
import com.pricehawk.repository.PriceHistoryRepository;
import com.pricehawk.repository.TrackedItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceTrackingServiceTest {

    @Mock
    private TrackedItemRepository trackedItemRepository;

    @Mock
    private ItemSourceRepository itemSourceRepository;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private PriceAlertRepository priceAlertRepository;

    @Mock
    private com.pricehawk.service.email.EmailService emailService;

    private PriceTrackingService priceTrackingService;

    private User testUser;
    private TrackedItem testItem;
    private ItemSource testSource;

    @BeforeEach
    void setUp() {
        priceTrackingService = new DefaultPriceTrackingService(
                trackedItemRepository,
                itemSourceRepository,
                priceHistoryRepository,
                priceAlertRepository,
                emailService
        );

        testUser = new User("testuser", "test@example.com", "hash123");
        testUser.setId(1L);

        testItem = new TrackedItem(testUser, "Apple iPhone 15", new BigDecimal("65000.00"));
        testItem.setId(10L);

        testSource = new ItemSource(
                testItem,
                Platform.AMAZON,
                "https://www.amazon.in/dp/B0000",
                new BigDecimal("70000.00"),
                new BigDecimal("70000.00"),
                "INR",
                "http://img.jpg"
        );
        testSource.setId(20L);
    }

    @Test
    @DisplayName("recordPriceUpdate - saves PriceHistory and updates itemSource current price")
    void recordPriceUpdate_savesHistoryAndUpdatesCurrentPrice() {
        BigDecimal newPrice = new BigDecimal("68000.00");

        priceTrackingService.recordPriceUpdate(testSource, newPrice, true);

        // Verify history saved
        ArgumentCaptor<PriceHistory> historyCaptor = ArgumentCaptor.forClass(PriceHistory.class);
        verify(priceHistoryRepository).save(historyCaptor.capture());
        PriceHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getItemSource()).isEqualTo(testSource);
        assertThat(savedHistory.getPrice()).isEqualByComparingTo(newPrice);
        assertThat(savedHistory.getInStock()).isTrue();

        // Verify source updated
        assertThat(testSource.getCurrentPrice()).isEqualByComparingTo(newPrice);
        verify(itemSourceRepository).save(testSource);

        // Price (68000) > targetPrice (65000) -> no alert
        verify(priceAlertRepository, never()).save(any());
        verify(emailService, never()).sendPriceDropAlert(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("recordPriceUpdate - triggers PriceAlert when price drops at or below targetPrice")
    void recordPriceUpdate_triggersPriceAlert_whenPriceDropsBelowTarget() {
        BigDecimal droppedPrice = new BigDecimal("64999.00"); // below 65000 target

        priceTrackingService.recordPriceUpdate(testSource, droppedPrice, true);

        ArgumentCaptor<PriceAlert> alertCaptor = ArgumentCaptor.forClass(PriceAlert.class);
        verify(priceAlertRepository).save(alertCaptor.capture());

        PriceAlert alert = alertCaptor.getValue();
        assertThat(alert.getTrackedItem()).isEqualTo(testItem);
        assertThat(alert.getUser()).isEqualTo(testUser);
        assertThat(alert.getOldPrice()).isEqualByComparingTo(new BigDecimal("70000.00"));
        assertThat(alert.getNewPrice()).isEqualByComparingTo(droppedPrice);
        assertThat(alert.getTargetPrice()).isEqualByComparingTo(new BigDecimal("65000.00"));
        assertThat(alert.isRead()).isFalse();

        // Verify email alert sent via Resend
        verify(emailService).sendPriceDropAlert(
                eq("test@example.com"),
                eq("Apple iPhone 15"),
                eq(new BigDecimal("70000.00")),
                eq(droppedPrice),
                eq("https://www.amazon.in/dp/B0000")
        );
        assertThat(testItem.getLastNotifiedPrice()).isEqualByComparingTo(droppedPrice);
    }

    @Test
    @DisplayName("recordPriceUpdate - prevents duplicate redundant email on consecutive cron runs when price is unchanged")
    void recordPriceUpdate_preventsDuplicateAlertsOnConsecutiveCronRuns() {
        BigDecimal targetPrice = new BigDecimal("65000.00");
        BigDecimal lowPrice = new BigDecimal("64000.00");

        // 1st run: drops to 64000 -> alerts
        priceTrackingService.recordPriceUpdate(testSource, lowPrice, true);
        verify(emailService, times(1)).sendPriceDropAlert(any(), any(), any(), any(), any());
        verify(priceAlertRepository, times(1)).save(any());

        // 2nd run (1 hour later): price is still 64000 -> NO duplicate alert or email!
        priceTrackingService.recordPriceUpdate(testSource, lowPrice, true);
        verify(emailService, times(1)).sendPriceDropAlert(any(), any(), any(), any(), any());
        verify(priceAlertRepository, times(1)).save(any());

        // 3rd run (2 hours later): price drops further to 62000 -> new alert triggered!
        BigDecimal evenLowerPrice = new BigDecimal("62000.00");
        priceTrackingService.recordPriceUpdate(testSource, evenLowerPrice, true);
        verify(emailService, times(2)).sendPriceDropAlert(any(), any(), any(), any(), any());
        verify(priceAlertRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("recordPriceUpdate - triggers alert on any price drop when notifyOnAnyDrop is true even without targetPrice")
    void recordPriceUpdate_triggersOnAnyPriceDrop_whenNotifyOnAnyDropEnabled() {
        testItem.setTargetPrice(null);
        testItem.setNotifyOnAnyDrop(true);
        testItem.setPreviousPrice(new BigDecimal("70000.00"));

        BigDecimal droppedPrice = new BigDecimal("68500.00"); // dropped below added price 70000
        priceTrackingService.recordPriceUpdate(testSource, droppedPrice, true);

        verify(emailService, times(1)).sendPriceDropAlert(any(), any(), any(), any(), any());
        verify(priceAlertRepository, times(1)).save(any());

        // Next run with same price 68500 -> no duplicate email
        priceTrackingService.recordPriceUpdate(testSource, droppedPrice, true);
        verify(emailService, times(1)).sendPriceDropAlert(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("recordPriceUpdate - does not trigger alert when notifyOnAnyDrop is false and no targetPrice is set")
    void recordPriceUpdate_doesNotAlert_whenNotifyOnAnyDropIsFalse_andNoTargetPrice() {
        testItem.setTargetPrice(null);
        testItem.setNotifyOnAnyDrop(false);

        BigDecimal droppedPrice = new BigDecimal("68500.00");
        priceTrackingService.recordPriceUpdate(testSource, droppedPrice, true);

        verify(emailService, never()).sendPriceDropAlert(any(), any(), any(), any(), any());
        verify(priceAlertRepository, never()).save(any());
    }

    @Test
    @DisplayName("getPriceHistory - returns history points when product owned by user")
    void getPriceHistory_returnsHistory_whenOwnedByUser() {
        when(trackedItemRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(testItem));

        PriceHistory h1 = new PriceHistory(testSource, new BigDecimal("70000.00"), true);
        h1.setId(101L);
        h1.setRecordedAt(LocalDateTime.now().minusHours(2));

        PriceHistory h2 = new PriceHistory(testSource, new BigDecimal("68000.00"), true);
        h2.setId(102L);
        h2.setRecordedAt(LocalDateTime.now());

        when(priceHistoryRepository.findByItemSourceTrackedItemIdOrderByRecordedAtAsc(10L))
                .thenReturn(List.of(h1, h2));

        List<PriceHistoryResponse> history = priceTrackingService.getPriceHistory(1L, 10L);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getId()).isEqualTo(101L);
        assertThat(history.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("70000.00"));
        assertThat(history.get(1).getId()).isEqualTo(102L);
        assertThat(history.get(1).getPrice()).isEqualByComparingTo(new BigDecimal("68000.00"));
    }

    @Test
    @DisplayName("getPriceHistory - throws ResourceNotFoundException when item not owned by user")
    void getPriceHistory_throwsNotFound_whenNotOwned() {
        when(trackedItemRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> priceTrackingService.getPriceHistory(1L, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tracked item not found");
    }

    @Test
    @DisplayName("getUserAlerts - returns list of alerts for authenticated user")
    void getUserAlerts_returnsAlerts() {
        PriceAlert alert = new PriceAlert(testItem, testUser, new BigDecimal("70000.00"),
                new BigDecimal("64000.00"), new BigDecimal("65000.00"));
        alert.setId(50L);
        alert.setCreatedAt(LocalDateTime.now());

        when(priceAlertRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(alert));

        List<PriceAlertResponse> alerts = priceTrackingService.getUserAlerts(1L);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getId()).isEqualTo(50L);
        assertThat(alerts.get(0).getItemTitle()).isEqualTo("Apple iPhone 15");
        assertThat(alerts.get(0).getNewPrice()).isEqualByComparingTo(new BigDecimal("64000.00"));
    }

    @Test
    @DisplayName("markAlertAsRead - sets isRead to true for owned alert")
    void markAlertAsRead_updatesStatus() {
        PriceAlert alert = new PriceAlert(testItem, testUser, new BigDecimal("70000.00"),
                new BigDecimal("64000.00"), new BigDecimal("65000.00"));
        alert.setId(50L);

        when(priceAlertRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(alert));

        priceTrackingService.markAlertAsRead(1L, 50L);

        assertThat(alert.isRead()).isTrue();
        assertThat(alert.getReadAt()).isNotNull();
        verify(priceAlertRepository).save(alert);
    }

    @Test
    @DisplayName("getUserAlerts - purges and filters out read alerts older than 2 days")
    void getUserAlerts_filtersOutReadAlertsOlderThan2Days() {
        PriceAlert unreadAlert = new PriceAlert(testItem, testUser, new BigDecimal("70000.00"),
                new BigDecimal("64000.00"), new BigDecimal("65000.00"));
        unreadAlert.setId(51L);
        unreadAlert.setCreatedAt(LocalDateTime.now().minusDays(5));
        unreadAlert.setRead(false);

        PriceAlert recentReadAlert = new PriceAlert(testItem, testUser, new BigDecimal("70000.00"),
                new BigDecimal("64000.00"), new BigDecimal("65000.00"));
        recentReadAlert.setId(52L);
        recentReadAlert.setCreatedAt(LocalDateTime.now().minusDays(1));
        recentReadAlert.setRead(true);
        recentReadAlert.setReadAt(LocalDateTime.now().minusHours(12));

        PriceAlert expiredReadAlert = new PriceAlert(testItem, testUser, new BigDecimal("70000.00"),
                new BigDecimal("64000.00"), new BigDecimal("65000.00"));
        expiredReadAlert.setId(53L);
        expiredReadAlert.setCreatedAt(LocalDateTime.now().minusDays(4));
        expiredReadAlert.setRead(true);
        expiredReadAlert.setReadAt(LocalDateTime.now().minusDays(3));

        when(priceAlertRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(unreadAlert, recentReadAlert, expiredReadAlert));

        List<PriceAlertResponse> alerts = priceTrackingService.getUserAlerts(1L);

        assertThat(alerts).hasSize(2);
        assertThat(alerts.stream().map(PriceAlertResponse::getId)).containsExactly(51L, 52L);
        verify(priceAlertRepository).deleteExpiredReadAlerts(any(LocalDateTime.class));
    }
}
