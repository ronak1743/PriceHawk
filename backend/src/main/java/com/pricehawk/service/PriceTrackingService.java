package com.pricehawk.service;

import com.pricehawk.dto.PriceAlertResponse;
import com.pricehawk.dto.PriceHistoryResponse;
import com.pricehawk.model.ItemSource;

import java.math.BigDecimal;
import java.util.List;

public interface PriceTrackingService {

    void recordPriceUpdate(ItemSource source, BigDecimal newPrice, boolean inStock);

    List<PriceHistoryResponse> getPriceHistory(Long userId, Long trackedItemId);

    List<PriceAlertResponse> getUserAlerts(Long userId);

    void markAlertAsRead(Long userId, Long alertId);
}
