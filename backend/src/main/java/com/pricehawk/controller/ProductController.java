package com.pricehawk.controller;

import com.pricehawk.dto.TrackProductRequest;
import com.pricehawk.dto.TrackedItemResponse;
import com.pricehawk.security.CustomUserPrincipal;
import com.pricehawk.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final com.pricehawk.service.PriceTrackingService priceTrackingService;

    public ProductController(ProductService productService,
                             com.pricehawk.service.PriceTrackingService priceTrackingService) {
        this.productService = productService;
        this.priceTrackingService = priceTrackingService;
    }

    @PostMapping("/track")
    public ResponseEntity<TrackedItemResponse> trackProduct(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody TrackProductRequest request) {
        TrackedItemResponse response = productService.trackProduct(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<TrackedItemResponse>> getTrackedProducts(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        List<TrackedItemResponse> response = productService.getTrackedProducts(principal.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<com.pricehawk.dto.PriceHistoryResponse>> getProductPriceHistory(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long id) {
        List<com.pricehawk.dto.PriceHistoryResponse> history = priceTrackingService.getPriceHistory(principal.getId(), id);
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrackedProduct(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long id) {
        productService.deleteTrackedProduct(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
