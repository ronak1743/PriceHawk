package com.pricehawk.dto;

import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public class UpdateAlertSettingsRequest {

    @PositiveOrZero(message = "Target price must be zero or positive")
    private BigDecimal targetPrice;

    private Boolean notifyOnAnyDrop;

    public UpdateAlertSettingsRequest() {}

    public UpdateAlertSettingsRequest(BigDecimal targetPrice, Boolean notifyOnAnyDrop) {
        this.targetPrice = targetPrice;
        this.notifyOnAnyDrop = notifyOnAnyDrop;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public void setTargetPrice(BigDecimal targetPrice) {
        this.targetPrice = targetPrice;
    }

    public Boolean getNotifyOnAnyDrop() {
        return notifyOnAnyDrop;
    }

    public void setNotifyOnAnyDrop(Boolean notifyOnAnyDrop) {
        this.notifyOnAnyDrop = notifyOnAnyDrop;
    }
}
