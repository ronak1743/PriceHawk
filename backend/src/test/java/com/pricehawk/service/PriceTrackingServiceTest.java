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
                priceAlertRepository
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
        verify(priceAlertRepository).save(alert);
    }
}
