package com.pricehawk.service;

import com.pricehawk.dto.PriceAlertResponse;
import com.pricehawk.dto.PriceHistoryResponse;
import com.pricehawk.exception.ResourceNotFoundException;
import com.pricehawk.model.ItemSource;
import com.pricehawk.model.PriceAlert;
import com.pricehawk.model.PriceHistory;
import com.pricehawk.model.TrackedItem;
import com.pricehawk.repository.ItemSourceRepository;
import com.pricehawk.repository.PriceAlertRepository;
import com.pricehawk.repository.PriceHistoryRepository;
import com.pricehawk.repository.TrackedItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DefaultPriceTrackingService implements PriceTrackingService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPriceTrackingService.class);

    private final TrackedItemRepository trackedItemRepository;
    private final ItemSourceRepository itemSourceRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceAlertRepository priceAlertRepository;
    private final com.pricehawk.service.email.EmailService emailService;

    public DefaultPriceTrackingService(TrackedItemRepository trackedItemRepository,
                                       ItemSourceRepository itemSourceRepository,
                                       PriceHistoryRepository priceHistoryRepository,
                                       PriceAlertRepository priceAlertRepository,
                                       com.pricehawk.service.email.EmailService emailService) {
        this.trackedItemRepository = trackedItemRepository;
        this.itemSourceRepository = itemSourceRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.priceAlertRepository = priceAlertRepository;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public void recordPriceUpdate(ItemSource source, BigDecimal newPrice, boolean inStock) {
        BigDecimal oldPrice = source.getCurrentPrice();

        // 1. Record snapshot in price_history
        PriceHistory history = new PriceHistory(source, newPrice, inStock);
        priceHistoryRepository.save(history);

        // 2. Update currentPrice on source
        source.setCurrentPrice(newPrice);
        source.setInStock(inStock);
        source.setLastCheckedAt(LocalDateTime.now());
        itemSourceRepository.save(source);

        // 3. Evaluate smart alert conditions with redundant spam prevention
        TrackedItem item = source.getTrackedItem();
        if (item != null && newPrice != null) {
            BigDecimal targetPrice = item.getTargetPrice();
            BigDecimal lastNotifiedPrice = item.getLastNotifiedPrice();
            BigDecimal previousPrice = item.getPreviousPrice() != null ? item.getPreviousPrice() : oldPrice;
            BigDecimal addedPrice = source.getOriginalPrice() != null ? source.getOriginalPrice() : oldPrice;
            boolean notifyOnAnyDrop = Boolean.TRUE.equals(item.getNotifyOnAnyDrop());

            boolean shouldAlert = false;

            // Scenario 1: Target Price reached (when explicit targetPrice is configured)
            if (targetPrice != null) {
                if (newPrice.compareTo(targetPrice) <= 0) {
                    // Prevent spam: only alert if not notified yet, dropped lower, or just crossed below target from above
                    if (lastNotifiedPrice == null
                            || newPrice.compareTo(lastNotifiedPrice) < 0
                            || (previousPrice != null && previousPrice.compareTo(targetPrice) > 0)) {
                        shouldAlert = true;
                    }
                }
            } else if (notifyOnAnyDrop) {
                // Scenario 2: Any price drop (when no targetPrice set, but any-drop alert enabled)
                boolean lowerThanAdded = addedPrice != null && newPrice.compareTo(addedPrice) < 0;
                boolean lowerThanPrevious = previousPrice != null && newPrice.compareTo(previousPrice) < 0;
                boolean lowerThanLastNotified = lastNotifiedPrice == null || newPrice.compareTo(lastNotifiedPrice) < 0;

                if (lowerThanAdded && lowerThanPrevious && lowerThanLastNotified) {
                    shouldAlert = true;
                }
            }

            // Track previous price for consecutive check evaluations
            item.setPreviousPrice(newPrice);

            if (shouldAlert) {
                try {
                    PriceAlert alert = new PriceAlert(item, item.getUser(), oldPrice, newPrice, targetPrice);
                    priceAlertRepository.save(alert);

                    // Update last notified price to avoid redundant emails on stagnant prices
                    item.setLastNotifiedPrice(newPrice);

                    // Send email alert via Resend
                    if (item.getUser() != null && item.getUser().getEmail() != null) {
                        emailService.sendPriceDropAlert(
                                item.getUser().getEmail(),
                                item.getTitle(),
                                oldPrice,
                                newPrice,
                                source.getUrl()
                        );
                    }
                } catch (Exception alertEx) {
                    log.error("Failed to persist or dispatch price drop alert: {}", alertEx.getMessage(), alertEx);
                }
            }

            trackedItemRepository.save(item);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceHistoryResponse> getPriceHistory(Long userId, Long trackedItemId) {
        trackedItemRepository.findByIdAndUserId(trackedItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked item not found with id: " + trackedItemId));

        return priceHistoryRepository.findByItemSourceTrackedItemIdOrderByRecordedAtAsc(trackedItemId)
                .stream()
                .map(h -> new PriceHistoryResponse(
                        h.getId(),
                        h.getPrice(),
                        h.getInStock(),
                        h.getRecordedAt(),
                        h.getItemSource() != null ? h.getItemSource().getPlatform() : null,
                        h.getItemSource() != null ? h.getItemSource().getId() : null
                ))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<PriceAlertResponse> getUserAlerts(Long userId) {
        LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
        try {
            priceAlertRepository.deleteExpiredReadAlerts(twoDaysAgo);
        } catch (Exception e) {
            log.debug("Note during purging expired read alerts: {}", e.getMessage());
        }

        return priceAlertRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(a -> !a.isRead() || a.getReadAt() == null || a.getReadAt().isAfter(twoDaysAgo))
                .map(this::mapToAlertResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void markAlertAsRead(Long userId, Long alertId) {
        PriceAlert alert = priceAlertRepository.findByIdAndUserId(alertId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));

        alert.setRead(true);
        alert.setReadAt(LocalDateTime.now());
        priceAlertRepository.save(alert);
    }

    @Override
    @Transactional
    public void purgeExpiredReadAlerts() {
        LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
        try {
            int deleted = priceAlertRepository.deleteExpiredReadAlerts(twoDaysAgo);
            if (deleted > 0) {
                log.info("Purged {} expired read alerts older than 2 days.", deleted);
            }
        } catch (Exception e) {
            log.warn("Error purging expired read alerts: {}", e.getMessage());
        }
    }

    private PriceAlertResponse mapToAlertResponse(PriceAlert alert) {
        String itemTitle = alert.getTrackedItem() != null ? alert.getTrackedItem().getTitle() : "Tracked Product";
        Long itemId = alert.getTrackedItem() != null ? alert.getTrackedItem().getId() : null;

        return new PriceAlertResponse(
                alert.getId(),
                itemId,
                itemTitle,
                alert.getOldPrice(),
                alert.getNewPrice(),
                alert.getTargetPrice(),
                alert.isRead(),
                alert.getCreatedAt(),
                alert.getReadAt()
        );
    }
}
