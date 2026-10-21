package com.pricehawk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class TrackProductRequest {

    @NotBlank(message = "Product URL is required")
    @Size(max = 10000, message = "URL cannot exceed 10000 characters")
    private String url;

    @NotBlank(message = "Product title is required")
    @Size(max = 5000, message = "Title cannot exceed 5000 characters")
    private String title;

    @NotNull(message = "Current price is required")
    @PositiveOrZero(message = "Price must be zero or positive")
    private BigDecimal currentPrice;

    @PositiveOrZero(message = "Target price must be zero or positive")
    private BigDecimal targetPrice;

    private Boolean notifyOnAnyDrop = true;

    private String currency;

    private String imageUrl;

    public TrackProductRequest() {}

    public TrackProductRequest(String url, String title, BigDecimal currentPrice, BigDecimal targetPrice,
                               String currency, String imageUrl) {
        this.url = url;
        this.title = title;
        this.currentPrice = currentPrice;
        this.targetPrice = targetPrice;
        this.notifyOnAnyDrop = true;
        this.currency = currency;
        this.imageUrl = imageUrl;
    }

    public TrackProductRequest(String url, String title, BigDecimal currentPrice, BigDecimal targetPrice,
                               Boolean notifyOnAnyDrop, String currency, String imageUrl) {
        this.url = url;
        this.title = title;
        this.currentPrice = currentPrice;
        this.targetPrice = targetPrice;
        this.notifyOnAnyDrop = notifyOnAnyDrop != null ? notifyOnAnyDrop : true;
        this.currency = currency;
        this.imageUrl = imageUrl;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public void setTargetPrice(BigDecimal targetPrice) {
        this.targetPrice = targetPrice;
    }

    public Boolean getNotifyOnAnyDrop() {
        return notifyOnAnyDrop != null ? notifyOnAnyDrop : true;
    }

    public void setNotifyOnAnyDrop(Boolean notifyOnAnyDrop) {
        this.notifyOnAnyDrop = notifyOnAnyDrop != null ? notifyOnAnyDrop : true;
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
}
