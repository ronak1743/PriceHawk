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

        String html = fetchHtml(url);
        if (html == null || html.isBlank()) return null;

        Document doc = Jsoup.parse(html);

        // Find primary product card
        Element card = doc.selectFirst("div[data-id], div.tUxRFH, div._75nlfW, div._1sdMkc, div.slAVV4, div._2kHMtA, a.CGtC5Q");
        if (card == null) return null;

        // Title
        Element titleEl = card.selectFirst("div.KzDlHZ, div._4rR01T, span.VU-ZEz, div.wBy4fm, a[title], a.WKTcLC, a.IRpwTa");
        String title = "Flipkart Product";
        if (titleEl != null) {
            if (titleEl.hasAttr("title") && !titleEl.attr("title").isBlank()) {
                title = titleEl.attr("title").trim();
            } else {
                title = titleEl.text().trim();
            }
        } else if (card.hasAttr("title") && !card.attr("title").isBlank()) {
            title = card.attr("title").trim();
        }

        // Price
        Element priceEl = card.selectFirst("div.Nx9bqj, div.hZ3P6w, div._30jeq3, div._4b5DiR");
        BigDecimal price = null;
        if (priceEl != null) {
            price = parsePrice(priceEl.text());
        }
        if (price == null) {
            for (Element el : card.select("div, span, a")) {
                if (el.children().isEmpty() && el.text().contains("₹")) {
                    price = parsePrice(el.text());
                    if (price != null) break;
                }
            }
        }
        if (price == null) return null;

        // URL
        Element linkEl = card.selectFirst("a[href*=\"/p/\"], a.CGtC5Q, a._1fQZEK, a.VJA3rP, a.WKTcLC");
        if (linkEl == null && card.tagName().equalsIgnoreCase("a")) linkEl = card;
        if (linkEl == null) linkEl = card.selectFirst("a[href]");
        if (linkEl == null) return null;

        String href = linkEl.attr("href");
        String fullUrl = href.startsWith("http") ? href : "https://www.flipkart.com" + href;

        // Image
        Element imgEl = card.selectFirst("img[src*=\"rukminim\"], img.DByuf4, img._396cs4, img");
        String imageUrl = imgEl != null ? (imgEl.hasAttr("src") ? imgEl.attr("src") : imgEl.attr("data-src")) : "";

        return new ScrapedProduct(title, price, "INR", imageUrl, fullUrl, true, Platform.FLIPKART);
    }

    private ScrapedProduct searchAmazon(String query) {
        String url = "https://www.amazon.in/s?k=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        log.debug("Searching Amazon counterpart via: {}", url);

        String html = fetchHtml(url);
        if (html == null || html.isBlank()) return null;

        Document doc = Jsoup.parse(html);

        Element card = doc.selectFirst("div[data-component-type=\"s-search-result\"]");
        if (card == null) return null;

        // Title
        Element titleEl = card.selectFirst("h2 a span, h2 a, h2");
        String title = titleEl != null ? titleEl.text().trim() : "Amazon Product";

        // Price
        Element priceEl = card.selectFirst(".a-price .a-offscreen, .a-price-whole, .a-price");
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

    private String fetchHtml(String url) {
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
            headers.set("Accept-Language", "en-US,en;q=0.9");
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
            org.springframework.http.ResponseEntity<String> res = restOperations.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class);
            if (res != null && res.getBody() != null) {
                return res.getBody();
            }
        } catch (Exception e) {
            log.debug("Direct exchange failed for {}, attempting getForObject fallback: {}", url, e.getMessage());
        }

        try {
            return restOperations.getForObject(url, String.class);
        } catch (Exception e) {
            log.warn("Failed to fetch search page from {}: {}", url, e.getMessage());
            return null;
        }
    }

    private String cleanSearchTitle(String title) {
        // Strip brand marketing fluff and parentheticals to get core search keywords
        String cleaned = title.replaceAll("\\(.*?\\)", "")
                .replaceAll("\\[.*?\\]", "")
                .replaceAll("(?i)(with|for|and|in|on|by)\\b", " ")
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Limit to first 5 prominent keywords for highest e-commerce search match rate
        String[] words = cleaned.split("\\s+");
        if (words.length > 5) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                if (i > 0) sb.append(" ");
                sb.append(words[i]);
            }
            return sb.toString();
        }
        return cleaned;
    }

    private static final java.util.regex.Pattern PRICE_REGEX =
            java.util.regex.Pattern.compile("[₹Rs.]*\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)");

    private BigDecimal parsePrice(String text) {
        if (text == null || text.isBlank()) return null;
        java.util.regex.Matcher m = PRICE_REGEX.matcher(text.replace("\u00a0", " ").trim());
        if (m.find()) {
            String val = m.group(1).replace(",", "");
            try {
                return new BigDecimal(val);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
