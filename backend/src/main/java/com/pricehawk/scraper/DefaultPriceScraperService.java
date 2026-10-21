package com.pricehawk.scraper;

import com.pricehawk.model.Platform;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class DefaultPriceScraperService implements PriceScraperService {

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private final List<PriceParser> parsers;

    public DefaultPriceScraperService(List<PriceParser> parsers) {
        this.parsers = parsers;
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
        try {
            String html = Jsoup.connect(url)
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

            return parseHtml(platform, html, url);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch product from URL: " + url + " - " + e.getMessage(), e);
        }
    }
}
