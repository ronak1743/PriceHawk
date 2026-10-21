package com.pricehawk.service;

import com.pricehawk.model.Platform;
import com.pricehawk.scraper.ScrapedProduct;

public interface CrossPlatformSearchService {

    ScrapedProduct findCounterpartProduct(Platform currentPlatform, String productTitle);
}
