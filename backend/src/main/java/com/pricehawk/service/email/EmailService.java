package com.pricehawk.service.email;

import java.math.BigDecimal;

public interface EmailService {

    void sendPriceDropAlert(String recipientEmail, String productTitle, BigDecimal oldPrice,
                            BigDecimal newPrice, String productUrl);
}
