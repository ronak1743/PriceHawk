package com.pricehawk.scheduler;

import com.pricehawk.model.ItemSource;
import com.pricehawk.model.Platform;
import com.pricehawk.model.TrackedItem;
import com.pricehawk.model.User;
import com.pricehawk.repository.ItemSourceRepository;
import com.pricehawk.scraper.PriceScraperService;
import com.pricehawk.scraper.ScrapedProduct;
import com.pricehawk.service.PriceTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceCheckSchedulerTest {

    @Mock
    private ItemSourceRepository itemSourceRepository;

    @Mock
    private PriceScraperService priceScraperService;

    @Mock
    private PriceTrackingService priceTrackingService;

    @InjectMocks
    private PriceCheckScheduler priceCheckScheduler;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = new User("alice", "alice@example.com", "hash");
        userB = new User("bob", "bob@example.com", "hash");
    }

    @Test
    @DisplayName("Deduplicates sources by exact URL: calls scraper exactly once and updates all subscribers")
    void deduplicatesByExactUrlAndUpdatesAllSources() {
        String exactUrl = "https://www.amazon.in/dp/B0FYQN4K1C";

        TrackedItem item1 = new TrackedItem(userA, "HP 15 Laptop", new BigDecimal("70000.00"));
        ItemSource source1 = new ItemSource(item1, Platform.AMAZON, exactUrl, new BigDecimal("75000.00"), new BigDecimal("75000.00"), "INR", null);

        TrackedItem item2 = new TrackedItem(userB, "HP 15 Laptop - Bob", new BigDecimal("72000.00"));
        ItemSource source2 = new ItemSource(item2, Platform.AMAZON, exactUrl, new BigDecimal("75000.00"), new BigDecimal("75000.00"), "INR", null);

        when(itemSourceRepository.findAllActiveSourcesWithUser()).thenReturn(List.of(source1, source2));
        when(priceScraperService.scrapeProduct(exactUrl))
                .thenReturn(new ScrapedProduct("HP 15 Laptop", new BigDecimal("71850.00"), "INR", "https://img.jpg", true, Platform.AMAZON));

        priceCheckScheduler.checkAllPrices();

        // Must call scraper ONLY ONCE for the exact URL
        verify(priceScraperService, times(1)).scrapeProduct(exactUrl);

        // Must update both subscribers
        verify(priceTrackingService, times(1)).recordPriceUpdate(eq(source1), eq(new BigDecimal("71850.00")), eq(true));
        verify(priceTrackingService, times(1)).recordPriceUpdate(eq(source2), eq(new BigDecimal("71850.00")), eq(true));
    }

    @Test
    @DisplayName("Handles scraper exception for one URL gracefully without halting other distinct URLs")
    void handlesScraperExceptionGracefullyWithoutHaltingOtherUrls() {
        String failedUrl = "https://www.amazon.in/dp/FAILEPASIN";
        String successUrl = "https://www.flipkart.com/laptop/p/itm123?pid=LAPTOP123";

        TrackedItem item1 = new TrackedItem(userA, "Failed Item", null);
        ItemSource source1 = new ItemSource(item1, Platform.AMAZON, failedUrl, new BigDecimal("1000.00"), new BigDecimal("1000.00"), "INR", null);

        TrackedItem item2 = new TrackedItem(userB, "Success Item", null);
        ItemSource source2 = new ItemSource(item2, Platform.FLIPKART, successUrl, new BigDecimal("50000.00"), new BigDecimal("50000.00"), "INR", null);

        when(itemSourceRepository.findAllActiveSourcesWithUser()).thenReturn(List.of(source1, source2));
        when(priceScraperService.scrapeProduct(failedUrl)).thenThrow(new RuntimeException("Anti-bot 503 error"));
        when(priceScraperService.scrapeProduct(successUrl))
                .thenReturn(new ScrapedProduct("Success Item", new BigDecimal("48999.00"), "INR", "https://img2.jpg", true, Platform.FLIPKART));

        priceCheckScheduler.checkAllPrices();

        // Source 1 failed
        verify(priceScraperService).scrapeProduct(failedUrl);
        verify(priceTrackingService, never()).recordPriceUpdate(eq(source1), any(), anyBoolean());

        // Source 2 still processed successfully
        verify(priceScraperService).scrapeProduct(successUrl);
        verify(priceTrackingService, times(1)).recordPriceUpdate(eq(source2), eq(new BigDecimal("48999.00")), eq(true));
    }

    @Test
    @DisplayName("Empty source list completes without calling scraper or tracking service")
    void emptySourceListCompletesWithoutScraping() {
        when(itemSourceRepository.findAllActiveSourcesWithUser()).thenReturn(Collections.emptyList());

        priceCheckScheduler.checkAllPrices();

        verifyNoInteractions(priceScraperService);
        verifyNoInteractions(priceTrackingService);
    }
}
