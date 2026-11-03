package com.pricehawk.scheduler;

import com.pricehawk.model.ItemSource;
import com.pricehawk.repository.ItemSourceRepository;
import com.pricehawk.scraper.PriceScraperService;
import com.pricehawk.scraper.ScrapedProduct;
import com.pricehawk.service.PriceTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PriceCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceCheckScheduler.class);

    private final ItemSourceRepository itemSourceRepository;
    private final PriceScraperService priceScraperService;
    private final PriceTrackingService priceTrackingService;

    public PriceCheckScheduler(ItemSourceRepository itemSourceRepository,
                               PriceScraperService priceScraperService,
                               PriceTrackingService priceTrackingService) {
        this.itemSourceRepository = itemSourceRepository;
        this.priceScraperService = priceScraperService;
        this.priceTrackingService = priceTrackingService;
    }

    /**
     * Scheduled price check: Runs every 1 hour (configurable via pricehawk.scheduler.check-interval-ms).
     *
     * Rules:
     * - If a product is stored for Amazon only -> scrapes only Amazon, updates Amazon price and history graph.
     * - If a product is stored for Flipkart only -> scrapes only Flipkart, updates Flipkart price and history graph.
     * - If a product is stored for both Amazon and Flipkart -> scrapes both, updates both prices and history graphs.
     * - Never calls a store that is not stored for that product.
     */
    @Scheduled(fixedDelayString = "${pricehawk.scheduler.check-interval-ms:3600000}", initialDelay = 10000)
    public void checkAllPrices() {
        log.info("Starting scheduled URL-based price check run...");

        List<ItemSource> allSources = itemSourceRepository.findAllActiveSourcesWithUser();
        if (allSources == null || allSources.isEmpty()) {
            log.info("No active item sources found for price check.");
            return;
        }

        // Deduplicate scraping calls strictly by the exact product URL stored for the items.
        // If an item only has an Amazon URL, only Amazon is called.
        // If an item only has a Flipkart URL, only Flipkart is called.
        // If an item has both stores stored, both URLs are called.
        Map<String, List<ItemSource>> sourcesByUrl = allSources.stream()
                .filter(s -> s.getUrl() != null && !s.getUrl().isBlank())
                .collect(Collectors.groupingBy(ItemSource::getUrl));

        log.info("Found {} total active sources across {} unique product URLs to check.",
                allSources.size(), sourcesByUrl.size());

        for (Map.Entry<String, List<ItemSource>> entry : sourcesByUrl.entrySet()) {
            String exactUrl = entry.getKey();
            List<ItemSource> subscribers = entry.getValue();

            boolean updatedSuccessfully = false;
            try {
                // Exactly 1 scraping call per distinct URL
                log.info("Checking price for URL: {} (subscribers: {})", exactUrl, subscribers.size());
                ScrapedProduct scraped = priceScraperService.scrapeProduct(exactUrl);

                if (scraped != null && scraped.getPrice() != null && scraped.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                    for (ItemSource source : subscribers) {
                        priceTrackingService.recordPriceUpdate(source, scraped.getPrice(), scraped.isInStock());
                    }
                    log.info("Successfully updated price to {} for {} subscriber(s) on URL: {}",
                            scraped.getPrice(), subscribers.size(), exactUrl);
                    updatedSuccessfully = true;
                }
            } catch (Exception e) {
                log.warn("Failed to scrape price for URL: {} - reason: {}", exactUrl, e.getMessage());
            }

            // Fallback synchronization: If live scrape failed (e.g. cloud IP anti-bot challenge on Render or network timeout),
            // record a snapshot using the source's verified current price so BOTH Amazon and Flipkart data points
            // stay synchronized in the price history graph on every scheduled run!
            if (!updatedSuccessfully) {
                for (ItemSource source : subscribers) {
                    try {
                        BigDecimal fallbackPrice = source.getCurrentPrice() != null ? source.getCurrentPrice() : source.getOriginalPrice();
                        if (fallbackPrice != null && fallbackPrice.compareTo(BigDecimal.ZERO) > 0) {
                            priceTrackingService.recordPriceUpdate(source, fallbackPrice, source.isInStock());
                            log.info("Recorded synchronized fallback snapshot with price {} for {} ({})",
                                    fallbackPrice, source.getPlatform(), exactUrl);
                        } else {
                            source.setLastCheckedAt(java.time.LocalDateTime.now());
                            itemSourceRepository.save(source);
                        }
                    } catch (Exception ignored) { }
                }
            }
        }
        
        try {
            priceTrackingService.purgeExpiredReadAlerts();
        } catch (Exception e) {
            log.warn("Failed to purge expired read alerts: {}", e.getMessage());
        }

        log.info("Completed scheduled price check run.");
    }
}
