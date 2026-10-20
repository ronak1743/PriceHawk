package com.pricehawk.dto;

import com.pricehawk.model.Platform;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ItemSourceResponse {

    private Long id;
    private Platform platform;
    private String url;
    private BigDecimal currentPrice;
    private BigDecimal originalPrice;
    private String currency;
    private String imageUrl;
    private Boolean inStock;
    private LocalDateTime lastCheckedAt;

    public ItemSourceResponse() {}

    public ItemSourceResponse(Long id, Platform platform, String url, BigDecimal currentPrice,
                              BigDecimal originalPrice, String currency, String imageUrl,
                              Boolean inStock, LocalDateTime lastCheckedAt) {
        this.id = id;
        this.platform = platform;
        this.url = url;
        this.currentPrice = currentPrice;
        this.originalPrice = originalPrice;
        this.currency = currency;
        this.imageUrl = imageUrl;
        this.inStock = inStock;
        this.lastCheckedAt = lastCheckedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getInStock() {
        return inStock;
    }

    public void setInStock(Boolean inStock) {
        this.inStock = inStock;
    }

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(LocalDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }
}
