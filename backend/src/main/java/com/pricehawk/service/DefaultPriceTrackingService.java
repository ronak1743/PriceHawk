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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DefaultPriceTrackingService implements PriceTrackingService {

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

        // 3. Evaluate alert condition
        TrackedItem item = source.getTrackedItem();
        if (item != null && item.getTargetPrice() != null && newPrice != null) {
            if (newPrice.compareTo(item.getTargetPrice()) <= 0) {
                PriceAlert alert = new PriceAlert(item, item.getUser(), oldPrice, newPrice, item.getTargetPrice());
                priceAlertRepository.save(alert);

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
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceHistoryResponse> getPriceHistory(Long userId, Long trackedItemId) {
        trackedItemRepository.findByIdAndUserId(trackedItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked item not found with id: " + trackedItemId));

        return priceHistoryRepository.findByItemSourceTrackedItemIdOrderByRecordedAtAsc(trackedItemId)
                .stream()
                .map(h -> new PriceHistoryResponse(h.getId(), h.getPrice(), h.getInStock(), h.getRecordedAt()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceAlertResponse> getUserAlerts(Long userId) {
        return priceAlertRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToAlertResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void markAlertAsRead(Long userId, Long alertId) {
        PriceAlert alert = priceAlertRepository.findByIdAndUserId(alertId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));

        alert.setRead(true);
        priceAlertRepository.save(alert);
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
                alert.getCreatedAt()
        );
    }
}
