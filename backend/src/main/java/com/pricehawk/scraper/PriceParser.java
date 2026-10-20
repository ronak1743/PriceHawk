package com.pricehawk.scraper;

import com.pricehawk.model.Platform;

public interface PriceParser {

    boolean supports(Platform platform);

    ScrapedProduct parse(String html, String url);
}
