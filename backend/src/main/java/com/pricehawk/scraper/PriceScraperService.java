package com.pricehawk.scraper;

import com.pricehawk.model.Platform;

public interface PriceScraperService {

    ScrapedProduct parseHtml(Platform platform, String html, String url);

    ScrapedProduct scrapeProduct(String url);
}
