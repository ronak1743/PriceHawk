package com.pricehawk.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "item_sources")
public class ItemSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracked_item_id", nullable = false)
    private TrackedItem trackedItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(nullable = false, length = 10)
    private String currency = "INR";

    @Column(length = 2048)
    private String imageUrl;

    @Column(nullable = false)
    private Boolean inStock = true;

    private LocalDateTime lastCheckedAt;
    private LocalDateTime createdAt;

    public ItemSource() {}

    public ItemSource(TrackedItem trackedItem, Platform platform, String url, BigDecimal currentPrice,
                      BigDecimal originalPrice, String currency, String imageUrl) {
        this.trackedItem = trackedItem;
        this.platform = platform;
        this.url = url;
        this.currentPrice = currentPrice;
        this.originalPrice = originalPrice;
        this.currency = (currency == null || currency.isBlank()) ? "INR" : currency;
        this.imageUrl = imageUrl;
        this.inStock = true;
        this.lastCheckedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.lastCheckedAt == null) {
            this.lastCheckedAt = LocalDateTime.now();
        }
        if (this.currency == null || this.currency.isBlank()) {
            this.currency = "INR";
        }
        if (this.inStock == null) {
            this.inStock = true;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TrackedItem getTrackedItem() {
        return trackedItem;
    }

    public void setTrackedItem(TrackedItem trackedItem) {
        this.trackedItem = trackedItem;
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

    public Boolean isInStock() {
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
