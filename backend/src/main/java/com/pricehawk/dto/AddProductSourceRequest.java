package com.pricehawk.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class AddProductSourceRequest {

    @Size(max = 2048, message = "URL cannot exceed 2048 characters")
    private String url;

    private BigDecimal currentPrice;

    private String imageUrl;

    private boolean autoDiscover;

    public AddProductSourceRequest() {}

    public AddProductSourceRequest(String url, BigDecimal currentPrice, String imageUrl, boolean autoDiscover) {
        this.url = url;
        this.currentPrice = currentPrice;
        this.imageUrl = imageUrl;
        this.autoDiscover = autoDiscover;
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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isAutoDiscover() {
        return autoDiscover;
    }

    public void setAutoDiscover(boolean autoDiscover) {
        this.autoDiscover = autoDiscover;
    }
}
