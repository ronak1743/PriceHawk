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
        if (html == null || html.isBlank())
            return null;

        Document doc = Jsoup.parse(html);
        org.jsoup.select.Elements cards = doc
                .select("div[data-id], div.tUxRFH, div._75nlfW, div._1sdMkc, div.slAVV4, div._2kHMtA, a.CGtC5Q");
        if (cards.isEmpty())
            return null;

        String[] queryKeywords = query.toLowerCase().split("\\s+");
        String brandKeyword = queryKeywords.length > 0 ? queryKeywords[0] : "";

        ScrapedProduct bestMatch = null;
        int bestScore = -1;

        for (Element card : cards) {
            // URL
            Element linkEl = card.selectFirst("a[href*=\"/p/\"], a.CGtC5Q, a._1fQZEK, a.VJA3rP, a.WKTcLC");
            if (linkEl == null && card.tagName().equalsIgnoreCase("a"))
                linkEl = card;
            if (linkEl == null)
                linkEl = card.selectFirst("a[href]");
            if (linkEl == null)
                continue;

            String href = linkEl.attr("href");
            if (href == null || href.isBlank())
                continue;

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
                        if (price != null)
                            break;
                    }
                }
            }
            if (price == null)
                continue;

            // Title
            Element titleEl = card
                    .selectFirst("div.KzDlHZ, div._4rR01T, span.VU-ZEz, div.wBy4fm, a[title], a.WKTcLC, a.IRpwTa");
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
            String titleLower = title.toLowerCase();

            // Image
            Element imgEl = card.selectFirst("img[src*=\"rukminim\"], img.DByuf4, img._396cs4, img");
            String imageUrl = imgEl != null ? (imgEl.hasAttr("src") ? imgEl.attr("src") : imgEl.attr("data-src")) : "";

            String fullUrl = href.startsWith("http") ? href : "https://www.flipkart.com" + href;

            int score = 0;
            if (!brandKeyword.isEmpty()
                    && (titleLower.contains(brandKeyword) || fullUrl.toLowerCase().contains(brandKeyword))) {
                score += 50;
            }
            for (String kw : queryKeywords) {
                if (kw.length() > 2 && titleLower.contains(kw)) {
                    score += 15;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestMatch = new ScrapedProduct(title, price, "INR", imageUrl, fullUrl, true, Platform.FLIPKART);
            }
        }

        return bestMatch;
    }

    private ScrapedProduct searchAmazon(String query) {
        String url = "https://www.amazon.in/s?k=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        log.debug("Searching Amazon counterpart via: {}", url);

        String html = fetchHtml(url);
        if (html == null || html.isBlank())
            return null;

        Document doc = Jsoup.parse(html);
        org.jsoup.select.Elements cards = doc.select("div[data-component-type=\"s-search-result\"]");
        if (cards.isEmpty())
            return null;

        String[] queryKeywords = query.toLowerCase().split("\\s+");
        String brandKeyword = queryKeywords.length > 0 ? queryKeywords[0] : "";

        ScrapedProduct bestMatch = null;
        int bestScore = -1;

        for (Element card : cards) {
            // URL - must link to product detail page (/dp/)
            Element linkEl = card.selectFirst("h2 a[href*=\"/dp/\"], a.a-link-normal[href*=\"/dp/\"], h2 a[href]");
            if (linkEl == null)
                continue;
            String href = linkEl.attr("href");
            if (href == null || href.isBlank() || href.contains("/picassoRedirect.html"))
                continue;

            // Price
            Element priceEl = card.selectFirst(".a-price .a-offscreen, .a-price-whole, .a-price");
            if (priceEl == null)
                continue;
            BigDecimal price = parsePrice(priceEl.text());
            if (price == null)
                continue;

            // Title
            Element titleEl = card.selectFirst("h2 a span, h2 a, h2");
            String title = titleEl != null ? titleEl.text().trim() : "Amazon Product";
            String titleLower = title.toLowerCase();

            // Image
            Element imgEl = card.selectFirst("img.s-image, img");
            String imageUrl = imgEl != null ? (imgEl.hasAttr("src") ? imgEl.attr("src") : imgEl.attr("data-src")) : "";

            String fullUrl = href.startsWith("http") ? href : "https://www.amazon.in" + href;

            // Check if sponsored
            boolean isSponsored = card.selectFirst(".puis-sponsored-label-text, .s-sponsored-label-info-icon") != null
                    || card.text().toLowerCase().startsWith("sponsored");

            int score = 0;
            // Match brand (strongest match signal)
            if (!brandKeyword.isEmpty()
                    && (titleLower.contains(brandKeyword) || fullUrl.toLowerCase().contains(brandKeyword))) {
                score += 50;
            }

            // Keyword matches
            for (String kw : queryKeywords) {
                if (kw.length() > 2 && titleLower.contains(kw)) {
                    score += 15;
                }
            }

            // Prefer organic over sponsored ad results
            if (!isSponsored) {
                score += 30;
            }

            if (score > bestScore) {
                bestScore = score;
                bestMatch = new ScrapedProduct(title, price, "INR", imageUrl, fullUrl, true, Platform.AMAZON);
            }
        }

        return bestMatch;
    }

    private String fetchHtml(String url) {
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            headers.set("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
            headers.set("Accept-Language", "en-US,en;q=0.9");
            headers.set("sec-ch-ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"");
            headers.set("sec-ch-ua-mobile", "?0");
            headers.set("sec-ch-ua-platform", "\"macOS\"");
            headers.set("sec-fetch-dest", "document");
            headers.set("sec-fetch-mode", "navigate");
            headers.set("sec-fetch-site", "none");
            headers.set("sec-fetch-user", "?1");
            headers.set("upgrade-insecure-requests", "1");

            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
            org.springframework.http.ResponseEntity<String> res = restOperations.exchange(url,
                    org.springframework.http.HttpMethod.GET, entity, String.class);
            if (res != null && res.getBody() != null && !res.getBody().isBlank()
                    && !res.getBody().contains("503 - Service Unavailable")) {
                return res.getBody();
            }
        } catch (Exception e) {
            log.debug("Exchange failed for {}, trying getForObject: {}", url, e.getMessage());
        }

        try {
            String body = restOperations.getForObject(url, String.class);
            if (body != null && !body.isBlank() && !body.contains("503 - Service Unavailable")) {
                return body;
            }
        } catch (Exception e) {
            log.debug("getForObject failed for {}, trying Jsoup direct fallback: {}", url, e.getMessage());
        }

        try {
            return Jsoup.connect(url)
                    .userAgent(
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("sec-ch-ua",
                            "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"macOS\"")
                    .header("sec-fetch-dest", "document")
                    .header("sec-fetch-mode", "navigate")
                    .header("sec-fetch-site", "none")
                    .header("sec-fetch-user", "?1")
                    .header("upgrade-insecure-requests", "1")
                    .timeout(12000)
                    .get()
                    .html();
        } catch (Exception e) {
            log.warn("All search fetch strategies failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    private String cleanSearchTitle(String title) {
        if (title == null || title.isBlank())
            return "";

        // Normalize brackets and punctuation into spaces, while keeping essential
        // product specs
        String s = title.replaceAll("[()\\[\\]{}|/\\\\:,;!*_+~]", " ")
                .replaceAll(
                        "(?i)\\b(with|for|and|in|on|by|the|a|an|of|to|is|at|from|all|pack of \\d+|thin and light laptop|truly wireless|smart|original|genuine|combo)\\b",
                        " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Use the first 7 most prominent keywords for optimal query matching
        String[] words = s.split("\\s+");
        if (words.length > 7) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if (i > 0)
                    sb.append(" ");
                sb.append(words[i]);
            }
            return sb.toString();
        }
        return s;
    }

    private static final java.util.regex.Pattern PRICE_REGEX = java.util.regex.Pattern
            .compile("[₹Rs.]*\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)");

    private BigDecimal parsePrice(String text) {
        if (text == null || text.isBlank())
            return null;
        java.util.regex.Matcher m = PRICE_REGEX.matcher(text.replace("\u00a0", " ").trim());
        if (m.find()) {
            String val = m.group(1).replace(",", "");
            try {
                return new BigDecimal(val);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
