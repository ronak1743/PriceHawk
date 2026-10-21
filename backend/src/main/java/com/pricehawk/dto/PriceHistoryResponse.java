package com.pricehawk.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PriceHistoryResponse {

    private Long id;
    private BigDecimal price;
    private Boolean inStock;
    private LocalDateTime recordedAt;
    private com.pricehawk.model.Platform platform;
    private Long sourceId;

    public PriceHistoryResponse() {}

    public PriceHistoryResponse(Long id, BigDecimal price, Boolean inStock, LocalDateTime recordedAt) {
        this(id, price, inStock, recordedAt, null, null);
    }

    public PriceHistoryResponse(Long id, BigDecimal price, Boolean inStock, LocalDateTime recordedAt, com.pricehawk.model.Platform platform, Long sourceId) {
        this.id = id;
        this.price = price;
        this.inStock = inStock;
        this.recordedAt = recordedAt;
        this.platform = platform;
        this.sourceId = sourceId;
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

    public com.pricehawk.model.Platform getPlatform() {
        return platform;
    }

    public void setPlatform(com.pricehawk.model.Platform platform) {
        this.platform = platform;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }
}
