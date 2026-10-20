package com.pricehawk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PriceAlertResponse {

    private Long id;
    private Long trackedItemId;
    private String itemTitle;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private BigDecimal targetPrice;

    @JsonProperty("isRead")
    private boolean isRead;
    private LocalDateTime createdAt;

    public PriceAlertResponse() {}

    public PriceAlertResponse(Long id, Long trackedItemId, String itemTitle, BigDecimal oldPrice,
                              BigDecimal newPrice, BigDecimal targetPrice, boolean isRead,
                              LocalDateTime createdAt) {
        this.id = id;
        this.trackedItemId = trackedItemId;
        this.itemTitle = itemTitle;
        this.oldPrice = oldPrice;
        this.newPrice = newPrice;
        this.targetPrice = targetPrice;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTrackedItemId() {
        return trackedItemId;
    }

    public void setTrackedItemId(Long trackedItemId) {
        this.trackedItemId = trackedItemId;
    }

    public String getItemTitle() {
        return itemTitle;
    }

    public void setItemTitle(String itemTitle) {
        this.itemTitle = itemTitle;
    }

    public BigDecimal getOldPrice() {
        return oldPrice;
    }

    public void setOldPrice(BigDecimal oldPrice) {
        this.oldPrice = oldPrice;
    }

    public BigDecimal getNewPrice() {
        return newPrice;
    }

    public void setNewPrice(BigDecimal newPrice) {
        this.newPrice = newPrice;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public void setTargetPrice(BigDecimal targetPrice) {
        this.targetPrice = targetPrice;
    }

    @JsonProperty("isRead")
    public boolean isRead() {
        return isRead;
    }

    @JsonProperty("isRead")
    public void setRead(boolean read) {
        isRead = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
