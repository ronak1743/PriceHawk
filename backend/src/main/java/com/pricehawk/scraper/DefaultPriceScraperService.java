package com.pricehawk.scraper;

import com.pricehawk.model.Platform;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

import java.io.IOException;
import java.util.List;

@Service
public class DefaultPriceScraperService implements PriceScraperService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPriceScraperService.class);

    private final List<PriceParser> parsers;
    private final RestOperations restOperations;

    public DefaultPriceScraperService(List<PriceParser> parsers) {
        this(parsers, null);
    }

    @Autowired
    public DefaultPriceScraperService(List<PriceParser> parsers, @Autowired(required = false) RestOperations restOperations) {
        this.parsers = parsers;
        this.restOperations = restOperations;
    }

    @Override
    public ScrapedProduct parseHtml(Platform platform, String html, String url) {
        for (PriceParser parser : parsers) {
            if (parser.supports(platform)) {
                return parser.parse(html, url);
            }
        }
        throw new IllegalArgumentException("No parser registered for platform: " + platform);
    }

    @Override
    public ScrapedProduct scrapeProduct(String url) {
        Platform platform = Platform.fromUrl(url);
        String html = null;

        // 1. Try RestOperations with realistic browser headers first
        if (restOperations != null) {
            try {
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
                headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
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
                    html = res.getBody();
                }
            } catch (Exception e) {
                log.debug("RestOperations fetch failed for URL {}: {}", url, e.getMessage());
            }
        }

        // 2. Fallback to Jsoup connection
        if (html == null) {
            try {
                html = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("sec-ch-ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
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
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch product from URL: " + url + " - " + e.getMessage(), e);
            }
        }

        return parseHtml(platform, html, url);
    }
}
