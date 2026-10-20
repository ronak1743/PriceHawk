package com.pricehawk.scraper;

import com.pricehawk.model.Platform;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AmazonPriceParser implements PriceParser {

    private static final Pattern PRICE_PATTERN = Pattern.compile("([0-9]+(?:,[0-9]+)*(?:\\.[0-9]{1,2})?)");

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.AMAZON;
    }

    @Override
    public ScrapedProduct parse(String html, String url) {
        Document doc = Jsoup.parse(html, url);

        String title = extractTitle(doc);
        BigDecimal price = extractPrice(doc);
        String currency = extractCurrency(doc, url);
        String imageUrl = extractImageUrl(doc);
        boolean inStock = extractInStock(doc, price);

        return new ScrapedProduct(title, price, currency, imageUrl, inStock, Platform.AMAZON);
    }

    private String extractTitle(Document doc) {
        Element titleEl = doc.selectFirst("#productTitle");
        if (titleEl != null && !titleEl.text().isBlank()) {
            return titleEl.text().trim();
        }
        Element metaTitle = doc.selectFirst("meta[name='title'], meta[property='og:title']");
        if (metaTitle != null && !metaTitle.attr("content").isBlank()) {
            return metaTitle.attr("content").trim();
        }
        return doc.title().trim();
    }

    private BigDecimal extractPrice(Document doc) {
        // Try Amazon selectors in order of specificity
        String[] selectors = {
                "#corePriceDisplay_desktop_feature_div .a-price .a-offscreen",
                "#priceblock_ourprice",
                "#priceblock_dealprice",
                "#corePrice_desktop .a-price .a-offscreen",
                ".priceToPay .a-price .a-offscreen",
                ".a-price.aok-align-center .a-offscreen",
                ".a-price .a-offscreen"
        };

        for (String selector : selectors) {
            Element el = doc.selectFirst(selector);
            if (el != null && !el.text().isBlank()) {
                BigDecimal parsed = parsePriceString(el.text());
                if (parsed != null) {
                    return parsed;
                }
            }
        }

        // Fallback: whole + fraction
        Element whole = doc.selectFirst(".a-price-whole");
        if (whole != null && !whole.text().isBlank()) {
            Element fraction = doc.selectFirst(".a-price-fraction");
            String frac = (fraction != null && !fraction.text().isBlank()) ? fraction.text() : "00";
            return parsePriceString(whole.text() + "." + frac);
        }

        return null;
    }

    private BigDecimal parsePriceString(String text) {
        Matcher matcher = PRICE_PATTERN.matcher(text.replace("\u00a0", " ").trim());
        if (matcher.find()) {
            String clean = matcher.group(1).replace(",", "");
            try {
                return new BigDecimal(clean);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private String extractCurrency(Document doc, String url) {
        if (url != null && url.contains(".in")) {
            return "INR";
        }
        Element symbolEl = doc.selectFirst(".a-price-symbol");
        if (symbolEl != null) {
            String symbol = symbolEl.text().trim();
            if (symbol.equals("₹")) return "INR";
            if (symbol.equals("$")) return "USD";
            if (symbol.equals("£")) return "GBP";
            if (symbol.equals("€")) return "EUR";
        }
        return "INR";
    }

    private String extractImageUrl(Document doc) {
        Element img = doc.selectFirst("#landingImage, #imgBlkFront, #main-image");
        if (img != null) {
            String src = img.attr("src");
            if (!src.isBlank()) return src.trim();
        }
        Element ogImg = doc.selectFirst("meta[property='og:image']");
        if (ogImg != null && !ogImg.attr("content").isBlank()) {
            return ogImg.attr("content").trim();
        }
        return null;
    }

    private boolean extractInStock(Document doc, BigDecimal price) {
        Element avail = doc.selectFirst("#availability");
        if (avail != null) {
            String text = avail.text().toLowerCase();
            if (text.contains("currently unavailable") || text.contains("out of stock") || text.contains("cannot be shipped")) {
                return false;
            }
            if (text.contains("in stock") || text.contains("left in stock")) {
                return true;
            }
        }
        return price != null;
    }
}
