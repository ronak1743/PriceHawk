package com.pricehawk.service;

import com.pricehawk.dto.ItemSourceResponse;
import com.pricehawk.dto.TrackProductRequest;
import com.pricehawk.dto.TrackedItemResponse;
import com.pricehawk.exception.ResourceNotFoundException;
import com.pricehawk.model.ItemSource;
import com.pricehawk.model.Platform;
import com.pricehawk.model.TrackedItem;
import com.pricehawk.model.User;
import com.pricehawk.repository.ItemSourceRepository;
import com.pricehawk.repository.TrackedItemRepository;
import com.pricehawk.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DefaultProductService implements ProductService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DefaultProductService.class);

    private final UserRepository userRepository;
    private final TrackedItemRepository trackedItemRepository;
    private final ItemSourceRepository itemSourceRepository;
    private final com.pricehawk.repository.PriceHistoryRepository priceHistoryRepository;
    private final com.pricehawk.repository.PriceAlertRepository priceAlertRepository;
    private final com.pricehawk.scraper.PriceScraperService priceScraperService;
    private final PriceTrackingService priceTrackingService;
    private final CrossPlatformSearchService crossPlatformSearchService;

    public DefaultProductService(UserRepository userRepository,
                                 TrackedItemRepository trackedItemRepository,
                                 ItemSourceRepository itemSourceRepository,
                                 com.pricehawk.repository.PriceHistoryRepository priceHistoryRepository,
                                 com.pricehawk.repository.PriceAlertRepository priceAlertRepository,
                                 com.pricehawk.scraper.PriceScraperService priceScraperService,
                                 PriceTrackingService priceTrackingService,
                                 CrossPlatformSearchService crossPlatformSearchService) {
        this.userRepository = userRepository;
        this.trackedItemRepository = trackedItemRepository;
        this.itemSourceRepository = itemSourceRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.priceAlertRepository = priceAlertRepository;
        this.priceScraperService = priceScraperService;
        this.priceTrackingService = priceTrackingService;
        this.crossPlatformSearchService = crossPlatformSearchService;
    }

    @Override
    @Transactional
    public TrackedItemResponse trackProduct(Long userId, TrackProductRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Platform platform = Platform.fromUrl(request.getUrl());

        BigDecimal resolvedPrice = request.getCurrentPrice();
        String resolvedTitle = request.getTitle();
        String resolvedImage = request.getImageUrl();

        // If client sent a placeholder or fallback price (<= 1.0) or generic title, scrape the live page on backend
        if (resolvedPrice == null || resolvedPrice.compareTo(BigDecimal.ONE) <= 0) {
            try {
                com.pricehawk.scraper.ScrapedProduct scraped = priceScraperService.scrapeProduct(request.getUrl());
                if (scraped != null && scraped.getPrice() != null && scraped.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                    resolvedPrice = scraped.getPrice();
                    if (resolvedTitle == null || resolvedTitle.isBlank() || "Tracked Product".equalsIgnoreCase(resolvedTitle)) {
                        resolvedTitle = scraped.getTitle();
                    }
                    if (resolvedImage == null || resolvedImage.isBlank()) {
                        resolvedImage = scraped.getImageUrl();
                    }
                    log.info("Live scraped original price {} during trackProduct for URL: {}", resolvedPrice, request.getUrl());
                }
            } catch (Exception e) {
                log.warn("Could not scrape live price during trackProduct: {}", e.getMessage());
            }
        }

        if (resolvedPrice == null || resolvedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            resolvedPrice = BigDecimal.ONE;
        }

        TrackedItem trackedItem = new TrackedItem(
                user,
                resolvedTitle != null ? resolvedTitle : request.getTitle(),
                request.getTargetPrice(),
                request.getNotifyOnAnyDrop()
        );
        trackedItem.setPreviousPrice(resolvedPrice);

        ItemSource source = new ItemSource(
                trackedItem,
                platform,
                request.getUrl(),
                resolvedPrice,
                resolvedPrice,
                request.getCurrency(),
                resolvedImage
        );

        trackedItem.addSource(source);

        TrackedItem savedItem = trackedItemRepository.save(trackedItem);

        // Record initial price history snapshot so price history chart is populated immediately
        try {
            priceHistoryRepository.save(new com.pricehawk.model.PriceHistory(source, resolvedPrice, true));
        } catch (Exception e) {
            log.warn("Could not save initial price history during trackProduct: {}", e.getMessage());
        }

        return mapToResponse(savedItem);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackedItemResponse> getTrackedProducts(Long userId) {
        return trackedItemRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteTrackedProduct(Long userId, Long productId) {
        TrackedItem item = trackedItemRepository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked item not found with id: " + productId));

        // Delete dependent price alerts and price history records first to preserve database FK integrity
        priceAlertRepository.deleteByTrackedItemId(productId);
        priceHistoryRepository.deleteByItemSourceTrackedItemId(productId);

        trackedItemRepository.delete(item);
        log.info("Successfully deleted tracked item {} for user {}", productId, userId);
    }

    @Override
    @Transactional
    public TrackedItemResponse refreshProductPrice(Long userId, Long productId) {
        TrackedItem item = trackedItemRepository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked item not found with id: " + productId));

        if (item.getSources() != null && !item.getSources().isEmpty()) {
            List<ItemSource> sourcesCopy = new ArrayList<>(item.getSources());
            for (ItemSource source : sourcesCopy) {
                if (source.getUrl() != null && !source.getUrl().isBlank()) {
                    boolean updatedSuccessfully = false;
                    try {
                        com.pricehawk.scraper.ScrapedProduct scraped = priceScraperService.scrapeProduct(source.getUrl());
                        if (scraped != null && scraped.getPrice() != null && scraped.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                            priceTrackingService.recordPriceUpdate(source, scraped.getPrice(), scraped.isInStock());
                            log.info("Refreshed price for item {} ({}) to {}", item.getId(), source.getPlatform(), scraped.getPrice());
                            updatedSuccessfully = true;
                        }
                    } catch (Exception e) {
                        log.warn("Live scrape attempt failed for item {} ({}) via URL: {} - {}",
                                item.getId(), source.getPlatform(), source.getUrl(), e.getMessage());
                    }

                    // If live scrape failed or returned empty price (e.g. anti-bot 503 or transient network failure),
                    // record a snapshot using the source's last verified price so BOTH Amazon and Flipkart data points
                    // stay synchronized in price history on every refresh!
                    if (!updatedSuccessfully) {
                        BigDecimal fallbackPrice = source.getCurrentPrice() != null ? source.getCurrentPrice() : source.getOriginalPrice();
                        if (fallbackPrice != null && fallbackPrice.compareTo(BigDecimal.ZERO) > 0) {
                            priceTrackingService.recordPriceUpdate(source, fallbackPrice, source.isInStock());
                            log.info("Recorded synchronized fallback snapshot with existing price {} for {} on item {}",
                                    fallbackPrice, source.getPlatform(), item.getId());
                        } else {
                            source.setLastCheckedAt(java.time.LocalDateTime.now());
                            itemSourceRepository.save(source);
                        }
                    }
                }
            }
        }

        item.setUpdatedAt(java.time.LocalDateTime.now());
        trackedItemRepository.save(item);

        TrackedItem refreshed = trackedItemRepository.findByIdAndUserId(productId, userId).orElse(item);
        return mapToResponse(refreshed);
    }

    @Override
    @Transactional
    public TrackedItemResponse updateTargetPrice(Long userId, Long productId, java.math.BigDecimal targetPrice) {
        TrackedItem item = trackedItemRepository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked item not found with id: " + productId));

        item.setTargetPrice(targetPrice);
        if (targetPrice == null) {
            item.setNotifyOnAnyDrop(true);
        }
        TrackedItem saved = trackedItemRepository.save(item);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public TrackedItemResponse updateAlertSettings(Long userId, Long productId, com.pricehawk.dto.UpdateAlertSettingsRequest request) {
        TrackedItem item = trackedItemRepository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked item not found with id: " + productId));

        // Allow setting targetPrice to null to remove/clear the limit
        item.setTargetPrice(request.getTargetPrice());

        if (request.getNotifyOnAnyDrop() != null) {
            item.setNotifyOnAnyDrop(request.getNotifyOnAnyDrop());
        } else if (request.getTargetPrice() == null) {
            // When removing target price without specifying notifyOnAnyDrop, default back to normal alert mode (true)
            item.setNotifyOnAnyDrop(true);
        }

        TrackedItem saved = trackedItemRepository.save(item);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public TrackedItemResponse addProductSource(Long userId, Long productId, com.pricehawk.dto.AddProductSourceRequest request) {
        TrackedItem item = trackedItemRepository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked item not found with id: " + productId));

        if (request.isAutoDiscover()) {
            Platform currentPlatform = item.getSources() != null && !item.getSources().isEmpty()
                    ? item.getSources().get(0).getPlatform()
                    : Platform.AMAZON;
            Platform counterpartPlatform = currentPlatform == Platform.AMAZON ? Platform.FLIPKART : Platform.AMAZON;

            String currentUrl = item.getSources() != null && !item.getSources().isEmpty()
                    ? item.getSources().get(0).getUrl()
                    : null;
            BigDecimal currentPrice = item.getSources() != null && !item.getSources().isEmpty()
                    ? item.getSources().get(0).getCurrentPrice()
                    : null;

            com.pricehawk.scraper.ScrapedProduct counterpart = crossPlatformSearchService.findCounterpartProduct(
                    currentPlatform, item.getTitle(), currentUrl, currentPrice);
            if (counterpart == null || counterpart.getUrl() == null) {
                throw new ResourceNotFoundException("No matching counterpart found on " + counterpartPlatform + ". You can link it manually with '+ Paste URL'.");
            }

            boolean alreadyLinked = item.getSources().stream()
                    .anyMatch(s -> s.getPlatform() == counterpart.getPlatform());
            if (!alreadyLinked) {
                ItemSource source = new ItemSource(
                        item,
                        counterpart.getPlatform(),
                        counterpart.getUrl(),
                        counterpart.getPrice(),
                        counterpart.getPrice(),
                        counterpart.getCurrency(),
                        counterpart.getImageUrl()
                );
                item.addSource(source);
                ItemSource savedSource = itemSourceRepository.save(source);
                trackedItemRepository.save(item);

                try {
                    priceHistoryRepository.save(new com.pricehawk.model.PriceHistory(savedSource, counterpart.getPrice(), true));
                } catch (Exception e) {
                    log.warn("Could not save initial price history for counterpart {}: {}", counterpart.getPlatform(), e.getMessage());
                }
            }
        } else if (request.getUrl() != null && !request.getUrl().isBlank()) {
            Platform platform = Platform.fromUrl(request.getUrl());
            BigDecimal price = request.getCurrentPrice();
            String imageUrl = request.getImageUrl();

            if (price == null || imageUrl == null || imageUrl.isBlank()) {
                try {
                    com.pricehawk.scraper.ScrapedProduct scraped = priceScraperService.scrapeProduct(request.getUrl());
                    if (scraped != null) {
                        if (price == null) price = scraped.getPrice();
                        if (imageUrl == null || imageUrl.isBlank()) imageUrl = scraped.getImageUrl();
                    }
                } catch (Exception e) {
                    log.warn("Failed to scrape counterpart URL {}: {}", request.getUrl(), e.getMessage());
                }
            }
            if (price == null) {
                price = BigDecimal.ZERO;
            }

            boolean alreadyLinked = item.getSources().stream()
                    .anyMatch(s -> s.getPlatform() == platform);
            if (!alreadyLinked) {
                ItemSource source = new ItemSource(
                        item,
                        platform,
                        request.getUrl(),
                        price,
                        price,
                        "INR",
                        imageUrl
                );
                item.addSource(source);
                ItemSource savedSource = itemSourceRepository.save(source);
                trackedItemRepository.save(item);

                try {
                    priceHistoryRepository.save(new com.pricehawk.model.PriceHistory(savedSource, price, true));
                } catch (Exception e) {
                    log.warn("Could not save initial price history for source {}: {}", platform, e.getMessage());
                }
            } else {
                ItemSource existing = item.getSources().stream()
                        .filter(s -> s.getPlatform() == platform)
                        .findFirst().orElse(null);
                if (existing != null) {
                    existing.setUrl(request.getUrl());
                    if (price.compareTo(BigDecimal.ZERO) > 0) {
                        existing.setCurrentPrice(price);
                    }
                    if (imageUrl != null && !imageUrl.isBlank()) {
                        existing.setImageUrl(imageUrl);
                    }
                    itemSourceRepository.save(existing);
                    trackedItemRepository.save(item);
                }
            }
        }

        return mapToResponse(item);
    }

    private TrackedItemResponse mapToResponse(TrackedItem item) {
        List<ItemSourceResponse> sourceResponses = item.getSources() != null
                ? item.getSources().stream().map(this::mapSourceToResponse).collect(Collectors.toList())
                : new ArrayList<>();

        return new TrackedItemResponse(
                item.getId(),
                item.getTitle(),
                item.getTargetPrice(),
                item.getLastNotifiedPrice(),
                item.getPreviousPrice(),
                item.getNotifyOnAnyDrop(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                sourceResponses
        );
    }

    private ItemSourceResponse mapSourceToResponse(ItemSource source) {
        return new ItemSourceResponse(
                source.getId(),
                source.getPlatform(),
                source.getUrl(),
                source.getCurrentPrice(),
                source.getOriginalPrice(),
                source.getCurrency(),
                source.getImageUrl(),
                source.isInStock(),
                source.getLastCheckedAt()
        );
    }
}
