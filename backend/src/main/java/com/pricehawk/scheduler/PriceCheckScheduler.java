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

    @Scheduled(fixedDelayString = "${pricehawk.scheduler.check-interval-ms:3600000}", initialDelay = 10000)
    public void checkAllPrices() {
        log.info("Starting scheduled URL-based price check run...");

        List<ItemSource> allSources = itemSourceRepository.findAllActiveSourcesWithUser();
        if (allSources == null || allSources.isEmpty()) {
            log.info("No active item sources found for price check.");
            return;
        }

        // Deduplicate scraping calls by grouping strictly by the exact product URL
        Map<String, List<ItemSource>> sourcesByUrl = allSources.stream()
                .filter(s -> s.getUrl() != null && !s.getUrl().isBlank())
                .collect(Collectors.groupingBy(ItemSource::getUrl));

        log.info("Found {} total sources across {} unique product URLs to check.", allSources.size(), sourcesByUrl.size());

        for (Map.Entry<String, List<ItemSource>> entry : sourcesByUrl.entrySet()) {
            String exactUrl = entry.getKey();
            List<ItemSource> subscribers = entry.getValue();

            try {
                // Exactly 1 scraping call per distinct URL
                log.debug("Scraping price for URL: {}", exactUrl);
                ScrapedProduct scraped = priceScraperService.scrapeProduct(exactUrl);

                if (scraped != null && scraped.getPrice() != null) {
                    for (ItemSource source : subscribers) {
                        priceTrackingService.recordPriceUpdate(source, scraped.getPrice(), scraped.isInStock());
                    }
                    log.info("Successfully updated price to {} for {} subscribers on URL: {}",
                            scraped.getPrice(), subscribers.size(), exactUrl);
                }
            } catch (Exception e) {
                log.warn("Failed to scrape price for URL: {} - reason: {}", exactUrl, e.getMessage());
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
