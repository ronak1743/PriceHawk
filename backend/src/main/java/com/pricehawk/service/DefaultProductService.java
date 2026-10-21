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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DefaultProductService implements ProductService {

    private final UserRepository userRepository;
    private final TrackedItemRepository trackedItemRepository;
    private final ItemSourceRepository itemSourceRepository;
    private final com.pricehawk.scraper.PriceScraperService priceScraperService;
    private final PriceTrackingService priceTrackingService;

    public DefaultProductService(UserRepository userRepository,
                                 TrackedItemRepository trackedItemRepository,
                                 ItemSourceRepository itemSourceRepository,
                                 com.pricehawk.scraper.PriceScraperService priceScraperService,
                                 PriceTrackingService priceTrackingService) {
        this.userRepository = userRepository;
        this.trackedItemRepository = trackedItemRepository;
        this.itemSourceRepository = itemSourceRepository;
        this.priceScraperService = priceScraperService;
        this.priceTrackingService = priceTrackingService;
    }

    @Override
    @Transactional
    public TrackedItemResponse trackProduct(Long userId, TrackProductRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Platform platform = Platform.fromUrl(request.getUrl());

        TrackedItem trackedItem = new TrackedItem(user, request.getTitle(), request.getTargetPrice());

        ItemSource source = new ItemSource(
                trackedItem,
                platform,
                request.getUrl(),
                request.getCurrentPrice(),
                request.getCurrentPrice(),
                request.getCurrency(),
                request.getImageUrl()
        );

        trackedItem.addSource(source);

        TrackedItem savedItem = trackedItemRepository.save(trackedItem);

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

        trackedItemRepository.delete(item);
    }

    @Override
    @Transactional
    public TrackedItemResponse refreshProductPrice(Long userId, Long productId) {
        TrackedItem item = trackedItemRepository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked item not found with id: " + productId));

        if (item.getSources() != null) {
            for (ItemSource source : item.getSources()) {
                if (source.getUrl() != null && !source.getUrl().isBlank()) {
                    try {
                        com.pricehawk.scraper.ScrapedProduct scraped = priceScraperService.scrapeProduct(source.getUrl());
                        if (scraped != null && scraped.getPrice() != null) {
                            priceTrackingService.recordPriceUpdate(source, scraped.getPrice(), scraped.isInStock());
                        }
                    } catch (Exception e) {
                        // Logging or continuing on error
                    }
                }
            }
        }

        return mapToResponse(item);
    }

    @Override
    @Transactional
    public TrackedItemResponse updateTargetPrice(Long userId, Long productId, java.math.BigDecimal targetPrice) {
        TrackedItem item = trackedItemRepository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked item not found with id: " + productId));

        item.setTargetPrice(targetPrice);
        TrackedItem saved = trackedItemRepository.save(item);
        return mapToResponse(saved);
    }

    private TrackedItemResponse mapToResponse(TrackedItem item) {
        List<ItemSourceResponse> sourceResponses = item.getSources() != null
                ? item.getSources().stream().map(this::mapSourceToResponse).collect(Collectors.toList())
                : new ArrayList<>();

        return new TrackedItemResponse(
                item.getId(),
                item.getTitle(),
                item.getTargetPrice(),
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
