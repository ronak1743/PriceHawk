package com.pricehawk.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PriceHistoryResponse {

    private Long id;
    private BigDecimal price;
    private Boolean inStock;
    private LocalDateTime recordedAt;

    public PriceHistoryResponse() {}

    public PriceHistoryResponse(Long id, BigDecimal price, Boolean inStock, LocalDateTime recordedAt) {
        this.id = id;
        this.price = price;
        this.inStock = inStock;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Boolean getInStock() {
        return inStock;
    }

    public void setInStock(Boolean inStock) {
        this.inStock = inStock;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }
}
