package com.pricehawk.controller;

import com.pricehawk.dto.PriceAlertResponse;
import com.pricehawk.security.CustomUserPrincipal;
import com.pricehawk.service.PriceTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final PriceTrackingService priceTrackingService;

    public AlertController(PriceTrackingService priceTrackingService) {
        this.priceTrackingService = priceTrackingService;
    }

    @GetMapping
    public ResponseEntity<List<PriceAlertResponse>> getUserAlerts(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        List<PriceAlertResponse> alerts = priceTrackingService.getUserAlerts(principal.getId());
        return ResponseEntity.ok(alerts);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAlertAsRead(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long id) {
        priceTrackingService.markAlertAsRead(principal.getId(), id);
        return ResponseEntity.ok().build();
    }
}
