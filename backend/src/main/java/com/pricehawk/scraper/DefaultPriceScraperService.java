package com.pricehawk.scraper;

import com.pricehawk.model.Platform;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DefaultPriceScraperService implements PriceScraperService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPriceScraperService.class);

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private static final String MOBILE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";

    private final List<PriceParser> parsers;
    private final RestOperations restOperations;

    @Value("${pricehawk.scraper.api-key:}")
    private String scraperApiKey;

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
    public ScrapedProduct scrapeProduct(String rawUrl) {
        Platform platform = Platform.fromUrl(rawUrl);
        String targetUrl = cleanTargetUrl(rawUrl, platform);
        String html = null;

        // 1. Optional Cloud Proxy / ScraperAPI (supports SCRAPER_API_KEY on Render/cloud hosting)
        if (scraperApiKey != null && !scraperApiKey.isBlank() && restOperations != null) {
            try {
                String proxyEndpoint = "https://api.scraperapi.com/?api_key=" + scraperApiKey.trim()
                        + "&url=" + URLEncoder.encode(targetUrl, StandardCharsets.UTF_8)
                        + "&country_code=in";
                log.debug("Routing scrape via ScraperAPI for: {}", targetUrl);
                String body = restOperations.getForObject(proxyEndpoint, String.class);
                if (body != null && !body.isBlank() && !body.contains("503 - Service Unavailable")
                        && !body.contains("Robot Check")) {
                    html = body;
                }
            } catch (Exception e) {
                log.debug("ScraperAPI fetch failed for {}: {}", targetUrl, e.getMessage());
            }
        }

        // 2. Direct RestOperations with realistic Google-referred browser headers
        if (html == null && restOperations != null) {
            html = fetchViaRestOperations(targetUrl, DESKTOP_UA);
            // If desktop is blocked (403/503), retry with Mobile Safari UA (bypasses Akamai carrier restrictions)
            if (html == null && platform == Platform.FLIPKART) {
                log.debug("Desktop fetch failed for Flipkart. Retrying with Mobile UA...");
                html = fetchViaRestOperations(targetUrl, MOBILE_UA);
            }
        }

        // 3. Jsoup connection fallback with redirect following and lenient error handling
        if (html == null) {
            html = fetchViaJsoup(targetUrl, DESKTOP_UA);
            if (html == null && platform == Platform.FLIPKART) {
                html = fetchViaJsoup(targetUrl, MOBILE_UA);
            }
        }

        if (html == null || html.isBlank()) {
            throw new RuntimeException("Failed to fetch product content from URL: " + targetUrl);
        }

        return parseHtml(platform, html, targetUrl);
    }

    private String fetchViaRestOperations(String url, String userAgent) {
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", userAgent);
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
            headers.set("Accept-Language", "en-US,en;q=0.9,hi;q=0.8");
            headers.set("Referer", "https://www.google.com/");
            headers.set("sec-ch-ua", "\"Chromium\";v=\"128\", \"Google Chrome\";v=\"128\", \"Not-A.Brand\";v=\"99\"");
            headers.set("sec-ch-ua-mobile", userAgent.contains("iPhone") ? "?1" : "?0");
            headers.set("sec-ch-ua-platform", userAgent.contains("iPhone") ? "\"iOS\"" : "\"Windows\"");
            headers.set("sec-fetch-dest", "document");
            headers.set("sec-fetch-mode", "navigate");
            headers.set("sec-fetch-site", "cross-site");
            headers.set("sec-fetch-user", "?1");
            headers.set("upgrade-insecure-requests", "1");
            headers.set("Cache-Control", "max-age=0");

            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
            org.springframework.http.ResponseEntity<String> res = restOperations.exchange(url,
                    org.springframework.http.HttpMethod.GET, entity, String.class);
            if (res != null && res.getBody() != null && !res.getBody().isBlank()
                    && !res.getBody().contains("503 - Service Unavailable")
                    && !res.getBody().contains("<title>Robot Check</title>")) {
                return res.getBody();
            }
        } catch (Exception e) {
            log.debug("RestOperations fetch attempt failed for {}: {}", url, e.getMessage());
        }
        return null;
    }

    private String fetchViaJsoup(String url, String userAgent) {
        try {
            org.jsoup.Connection.Response resp = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer("https://www.google.com/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9,hi;q=0.8")
                    .header("sec-ch-ua", "\"Chromium\";v=\"128\", \"Google Chrome\";v=\"128\", \"Not-A.Brand\";v=\"99\"")
                    .header("sec-ch-ua-mobile", userAgent.contains("iPhone") ? "?1" : "?0")
                    .header("sec-ch-ua-platform", userAgent.contains("iPhone") ? "\"iOS\"" : "\"Windows\"")
                    .header("sec-fetch-dest", "document")
                    .header("sec-fetch-mode", "navigate")
                    .header("sec-fetch-site", "cross-site")
                    .header("sec-fetch-user", "?1")
                    .header("upgrade-insecure-requests", "1")
                    .header("Cache-Control", "max-age=0")
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .timeout(20000)
                    .execute();

            if (resp != null) {
                String body = resp.body();
                if (body != null && !body.isBlank()
                        && !body.contains("503 - Service Unavailable")
                        && !body.contains("<title>Robot Check</title>")) {
                    return body;
                }
            }
        } catch (IOException e) {
            log.debug("Jsoup fetch attempt failed for {}: {}", url, e.getMessage());
        }
        return null;
    }

    /**
     * Strips volatile tracking and expired session tokens from Flipkart product URLs.
     */
    private String cleanTargetUrl(String url, Platform platform) {
        if (platform == Platform.FLIPKART && url != null) {
            String base = url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
            if (url.contains("?")) {
                String query = url.substring(url.indexOf("?") + 1);
                Matcher m = Pattern.compile("(?i)(?:^|&)(pid=[^&]+)").matcher(query);
                if (m.find()) {
                    return base + "?" + m.group(1);
                }
            }
            return base;
        }
        return url;
    }
}
