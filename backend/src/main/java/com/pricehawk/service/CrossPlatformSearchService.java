package com.pricehawk.service;

import com.pricehawk.model.Platform;
import com.pricehawk.scraper.ScrapedProduct;

import java.math.BigDecimal;

public interface CrossPlatformSearchService {

    ScrapedProduct findCounterpartProduct(Platform currentPlatform, String productTitle);

    default ScrapedProduct findCounterpartProduct(Platform currentPlatform, String productTitle, String productUrl, BigDecimal currentPrice) {
        return findCounterpartProduct(currentPlatform, productTitle);
    }
}
