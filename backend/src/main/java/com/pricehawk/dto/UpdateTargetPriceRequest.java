package com.pricehawk.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public class UpdateTargetPriceRequest {

    @NotNull(message = "Target price cannot be null")
    @PositiveOrZero(message = "Target price must be zero or positive")
    private BigDecimal targetPrice;

    public UpdateTargetPriceRequest() {}

    public UpdateTargetPriceRequest(BigDecimal targetPrice) {
        this.targetPrice = targetPrice;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public void setTargetPrice(BigDecimal targetPrice) {
        this.targetPrice = targetPrice;
    }
}
