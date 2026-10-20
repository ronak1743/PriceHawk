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
public class FlipkartPriceParser implements PriceParser {

    private static final Pattern PRICE_PATTERN = Pattern.compile("([0-9]+(?:,[0-9]+)*(?:\\.[0-9]{1,2})?)");

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.FLIPKART;
    }

    @Override
    public ScrapedProduct parse(String html, String url) {
        Document doc = Jsoup.parse(html, url);

        String title = extractTitle(doc);
        BigDecimal price = extractPrice(doc);
        String currency = "INR";
        String imageUrl = extractImageUrl(doc);
        boolean inStock = extractInStock(doc, price);

        return new ScrapedProduct(title, price, currency, imageUrl, inStock, Platform.FLIPKART);
    }

    private String extractTitle(Document doc) {
        String[] titleSelectors = {
                "span.VU-ZEz",
                "span.B_NuCI",
                "h1.yhB1nd span",
                "h1"
        };

        for (String selector : titleSelectors) {
            Element el = doc.selectFirst(selector);
            if (el != null && !el.text().isBlank()) {
                return el.text().trim();
            }
        }

        Element ogTitle = doc.selectFirst("meta[property='og:title']");
        if (ogTitle != null && !ogTitle.attr("content").isBlank()) {
            return ogTitle.attr("content").trim();
        }
        return doc.title().trim();
    }

    private BigDecimal extractPrice(Document doc) {
        String[] selectors = {
                "div.Nx9bqj.CxhGGd",
                "div._30jeq3._16Jk6d",
                "div.Nx9bqj",
                "div._30jeq3",
                "div._16Jk6d"
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

    private String extractImageUrl(Document doc) {
        String[] imgSelectors = {
                "img.DByuf4",
                "img._396cs4._2amPTt",
                "img._396cs4",
                "img._2r_T1I"
        };

        for (String selector : imgSelectors) {
            Element el = doc.selectFirst(selector);
            if (el != null) {
                String src = el.attr("src");
                if (!src.isBlank()) return src.trim();
            }
        }

        Element ogImg = doc.selectFirst("meta[property='og:image']");
        if (ogImg != null && !ogImg.attr("content").isBlank()) {
            return ogImg.attr("content").trim();
        }
        return null;
    }

    private boolean extractInStock(Document doc, BigDecimal price) {
        Element soldOut = doc.selectFirst("div._16FRp0, div._1V3wBu, div._9-sL7L");
        if (soldOut != null) {
            String text = soldOut.text().toLowerCase();
            if (text.contains("sold out") || text.contains("currently unavailable") || text.contains("out of stock")) {
                return false;
            }
        }
        String bodyText = doc.body().text().toLowerCase();
        if (bodyText.contains("sold out") || bodyText.contains("this item is currently out of stock")) {
            return false;
        }
        return price != null;
    }
}
