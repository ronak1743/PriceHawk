package com.pricehawk.service;

import com.pricehawk.model.Platform;
import com.pricehawk.scraper.ScrapedProduct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class DefaultCrossPlatformSearchService implements CrossPlatformSearchService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCrossPlatformSearchService.class);

    private final RestOperations restOperations;

    public DefaultCrossPlatformSearchService(RestOperations restOperations) {
        this.restOperations = restOperations;
    }

    @Override
    public ScrapedProduct findCounterpartProduct(Platform currentPlatform, String productTitle) {
        if (productTitle == null || productTitle.isBlank()) {
            return null;
        }

        String searchKeywords = cleanSearchTitle(productTitle);

        try {
            if (currentPlatform == Platform.AMAZON) {
                return searchFlipkart(searchKeywords);
            } else if (currentPlatform == Platform.FLIPKART) {
                return searchAmazon(searchKeywords);
            }
        } catch (Exception e) {
            log.warn("Failed to find counterpart product for title: '{}' on platform: {} - {}",
                    productTitle, currentPlatform, e.getMessage());
        }

        return null;
    }

    private ScrapedProduct searchFlipkart(String query) {
        String url = "https://www.flipkart.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        log.debug("Searching Flipkart counterpart via: {}", url);

        String html = restOperations.getForObject(url, String.class);
        if (html == null || html.isBlank()) return null;

        Document doc = Jsoup.parse(html);

        // Find primary product card
        Element card = doc.selectFirst("div.tUxRFH, div._75nlfW, div._1sdMkc, div.slAVV4, div._2kHMtA, a.CGtC5Q");
        if (card == null) return null;

        // Title
        Element titleEl = card.selectFirst("div.KzDlHZ, div._4rR01T, span.VU-ZEz, div.wBy4fm, a[title]");
        String title = titleEl != null ? titleEl.text().trim() : card.text().trim();

        // Price
        Element priceEl = card.selectFirst("div.Nx9bqj, div._30jeq3, div._4b5DiR");
        if (priceEl == null) return null;
        BigDecimal price = parsePrice(priceEl.text());
        if (price == null) return null;

        // URL
        Element linkEl = card.selectFirst("a.CGtC5Q, a._1fQZEK, a[href*=\"/p/\"]");
        if (linkEl == null && card.tagName().equalsIgnoreCase("a")) linkEl = card;
        if (linkEl == null) return null;

        String href = linkEl.attr("href");
        String fullUrl = href.startsWith("http") ? href : "https://www.flipkart.com" + href;

        // Image
        Element imgEl = card.selectFirst("img.DByuf4, img._396cs4, img[src*=\"rukminim\"]");
        String imageUrl = imgEl != null ? (imgEl.hasAttr("src") ? imgEl.attr("src") : imgEl.attr("data-src")) : "";

        return new ScrapedProduct(title, price, "INR", imageUrl, fullUrl, true, Platform.FLIPKART);
    }

    private ScrapedProduct searchAmazon(String query) {
        String url = "https://www.amazon.in/s?k=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        log.debug("Searching Amazon counterpart via: {}", url);

        String html = restOperations.getForObject(url, String.class);
        if (html == null || html.isBlank()) return null;

        Document doc = Jsoup.parse(html);

        Element card = doc.selectFirst("div[data-component-type=\"s-search-result\"]");
        if (card == null) return null;

        // Title
        Element titleEl = card.selectFirst("h2 a span, h2 a, h2");
        String title = titleEl != null ? titleEl.text().trim() : "Amazon Product";

        // Price
        Element priceEl = card.selectFirst(".a-price .a-offscreen, .a-price-whole");
        if (priceEl == null) return null;
        BigDecimal price = parsePrice(priceEl.text());
        if (price == null) return null;

        // URL
        Element linkEl = card.selectFirst("h2 a, a.a-link-normal[href*=\"/dp/\"]");
        if (linkEl == null) return null;
        String href = linkEl.attr("href");
        String fullUrl = href.startsWith("http") ? href : "https://www.amazon.in" + href;

        // Image
        Element imgEl = card.selectFirst("img.s-image, img");
        String imageUrl = imgEl != null ? imgEl.attr("src") : "";

        return new ScrapedProduct(title, price, "INR", imageUrl, fullUrl, true, Platform.AMAZON);
    }

    private String cleanSearchTitle(String title) {
        // Strip brand marketing fluff and parentheticals to get core search keywords
        String cleaned = title.replaceAll("\\(.*?\\)", "")
                .replaceAll("\\[.*?\\]", "")
                .replaceAll("(?i)(with|for|and|in|on|by)\\b", " ")
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Limit to first 6-7 prominent keywords for highest search match rate
        String[] words = cleaned.split("\\s+");
        if (words.length > 7) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if (i > 0) sb.append(" ");
                sb.append(words[i]);
            }
            return sb.toString();
        }
        return cleaned;
    }

    private BigDecimal parsePrice(String text) {
        if (text == null) return null;
        String clean = text.replaceAll("[^0-9.]", "");
        try {
            return new BigDecimal(clean);
        } catch (Exception e) {
            return null;
        }
    }
}
