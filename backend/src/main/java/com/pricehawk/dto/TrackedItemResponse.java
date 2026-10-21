package com.pricehawk.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TrackedItemResponse {

    private Long id;
    private String title;
    private BigDecimal targetPrice;
    private BigDecimal lastNotifiedPrice;
    private BigDecimal previousPrice;
    private Boolean notifyOnAnyDrop = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ItemSourceResponse> sources = new ArrayList<>();

    public TrackedItemResponse() {}

    public TrackedItemResponse(Long id, String title, BigDecimal targetPrice, BigDecimal lastNotifiedPrice,
                               BigDecimal previousPrice, Boolean notifyOnAnyDrop, LocalDateTime createdAt,
                               LocalDateTime updatedAt, List<ItemSourceResponse> sources) {
        this.id = id;
        this.title = title;
        this.targetPrice = targetPrice;
        this.lastNotifiedPrice = lastNotifiedPrice;
        this.previousPrice = previousPrice;
        this.notifyOnAnyDrop = notifyOnAnyDrop != null ? notifyOnAnyDrop : true;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.sources = sources != null ? sources : new ArrayList<>();
    }

    public TrackedItemResponse(Long id, String title, BigDecimal targetPrice, LocalDateTime createdAt,
                               LocalDateTime updatedAt, List<ItemSourceResponse> sources) {
        this(id, title, targetPrice, null, null, true, createdAt, updatedAt, sources);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public void setTargetPrice(BigDecimal targetPrice) {
        this.targetPrice = targetPrice;
    }

    public BigDecimal getLastNotifiedPrice() {
        return lastNotifiedPrice;
    }

    public void setLastNotifiedPrice(BigDecimal lastNotifiedPrice) {
        this.lastNotifiedPrice = lastNotifiedPrice;
    }

    public BigDecimal getPreviousPrice() {
        return previousPrice;
    }

    public void setPreviousPrice(BigDecimal previousPrice) {
        this.previousPrice = previousPrice;
    }

    public Boolean getNotifyOnAnyDrop() {
        return notifyOnAnyDrop != null ? notifyOnAnyDrop : true;
    }

    public void setNotifyOnAnyDrop(Boolean notifyOnAnyDrop) {
        this.notifyOnAnyDrop = notifyOnAnyDrop != null ? notifyOnAnyDrop : true;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ItemSourceResponse> getSources() {
        return sources;
    }

    public void setSources(List<ItemSourceResponse> sources) {
        this.sources = sources;
    }
}
