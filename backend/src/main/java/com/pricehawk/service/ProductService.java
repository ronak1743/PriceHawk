package com.pricehawk.service;

import com.pricehawk.dto.TrackProductRequest;
import com.pricehawk.dto.TrackedItemResponse;

import java.util.List;

public interface ProductService {

    TrackedItemResponse trackProduct(Long userId, TrackProductRequest request);

    List<TrackedItemResponse> getTrackedProducts(Long userId);

    void deleteTrackedProduct(Long userId, Long productId);

    TrackedItemResponse refreshProductPrice(Long userId, Long productId);

    TrackedItemResponse updateTargetPrice(Long userId, Long productId, java.math.BigDecimal targetPrice);

    TrackedItemResponse updateAlertSettings(Long userId, Long productId, com.pricehawk.dto.UpdateAlertSettingsRequest request);

    TrackedItemResponse addProductSource(Long userId, Long productId, com.pricehawk.dto.AddProductSourceRequest request);
}
