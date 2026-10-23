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

    private static final Pattern CURRENCY_PRICE_PATTERN =
            Pattern.compile("(?:₹|Rs\\.?|INR)\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)");

    private static final Pattern GENERIC_PRICE_PATTERN =
            Pattern.compile("^\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*$");

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
                String title = el.text().trim();
                if (title.toLowerCase().endsWith("...more")) {
                    title = title.substring(0, title.length() - "...more".length()).trim();
                }
                return title;
            }
        }

        Element ogTitle = doc.selectFirst("meta[property='og:title']");
        if (ogTitle != null && !ogTitle.attr("content").isBlank()) {
            return ogTitle.attr("content").trim();
        }
        return doc.title().trim();
    }

    private static final Pattern FSP_PPD_PATTERN =
            Pattern.compile("\"ppd\"\\s*:\\s*\\{[^}]*\"fsp\"\\s*:\\s*([1-9][0-9]*(?:\\.[0-9]+)?)");

    private static final Pattern PRODUCT_PRICE_PATTERN =
            Pattern.compile("\"(?:productPrice|primaryProductPrice)\"\\s*:\\s*\"?([1-9][0-9]*(?:\\.[0-9]+)?)\"?");

    private static final Pattern PPD_PATTERN =
            Pattern.compile("\"ppd\"\\s*:\\s*\\{[^}]*\"(?:fsp|finalPrice)\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

    private static final Pattern LP_PATTERN =
            Pattern.compile("(?:\\\\\"|\")lp(?:\\\\\"|\")\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

    private static final Pattern DESC_PATTERN =
            Pattern.compile("\"price_description_1\".*?\"label_2\".*?\"text\"\\s*:\\s*\"(?:\\\\u20b9|₹)?([0-9,]+)\"");

    private BigDecimal extractPrice(Document doc) {
        String html = doc.html();

        // 1. Special Selling Price (fsp) in primary product embedded state
        Matcher fspMatcher = FSP_PPD_PATTERN.matcher(html);
        if (fspMatcher.find()) {
            BigDecimal parsed = parsePriceString(fspMatcher.group(1));
            if (parsed != null && parsed.compareTo(BigDecimal.ZERO) > 0) {
                return parsed;
            }
        }

        Matcher prodPriceMatcher = PRODUCT_PRICE_PATTERN.matcher(html);
        if (prodPriceMatcher.find()) {
            BigDecimal parsed = parsePriceString(prodPriceMatcher.group(1));
            if (parsed != null && parsed.compareTo(BigDecimal.ZERO) > 0) {
                return parsed;
            }
        }

        // 2. Desktop & standard server-rendered selectors (including modern React Native Web classes)
        String[] selectors = {
                "div.Nx9bqj.CxhGGd",
                "div._30jeq3._16Jk6d",
                "div.v1zwn21n",
                "span.v1zwn21n",
                "div[font='default-fk-font-m']",
                "div.hl05eU div.Nx9bqj",
                "div._25b18c div.Nx9bqj",
                "div.Nx9bqj",
                "div._30jeq3",
                "div._16Jk6d",
                "div.hZ3P6w",
                "div[class*='Nx9bqj']",
                "div[class*='30jeq3']"
        };

        for (String selector : selectors) {
            for (Element el : doc.select(selector)) {
                if (el.text().isBlank()) continue;
                // Exclude elements inside recommendation / ad links
                boolean isInsideOtherProductLink = el.parents().stream().anyMatch(p ->
                        p.tagName().equalsIgnoreCase("a") && (p.attr("href").contains("/p/") || p.attr("href").contains("pid=")));
                if (isInsideOtherProductLink) continue;

                // Ignore elements with line-through (MRP / strike-through)
                String style = el.attr("style");
                if (style != null && style.contains("line-through")) {
                    continue;
                }
                String text = el.text().trim();
                if (!text.contains("₹") && !text.toLowerCase().contains("rs")) {
                    continue;
                }
                BigDecimal parsed = parsePriceString(text);
                if (parsed != null && parsed.compareTo(BigDecimal.ZERO) > 0) {
                    return parsed;
                }
            }
        }

        // 3. Fallback PPD finalPrice
        Matcher ppdMatcher = PPD_PATTERN.matcher(html);
        if (ppdMatcher.find()) {
            BigDecimal parsed = parsePriceString(ppdMatcher.group(1));
            if (parsed != null && parsed.compareTo(BigDecimal.ZERO) > 0) {
                return parsed;
            }
        }

        Matcher lpMatcher = LP_PATTERN.matcher(html);
        if (lpMatcher.find()) {
            BigDecimal parsed = parsePriceString(lpMatcher.group(1));
            if (parsed != null && parsed.compareTo(BigDecimal.ZERO) > 0) {
                return parsed;
            }
        }

        Matcher descMatcher = DESC_PATTERN.matcher(html);
        if (descMatcher.find()) {
            BigDecimal parsed = parsePriceString(descMatcher.group(1));
            if (parsed != null && parsed.compareTo(BigDecimal.ZERO) > 0) {
                return parsed;
            }
        }

        // 3. Fallback: inspect clean standalone ₹ element, strictly excluding links to other products (ads / carousels)
        for (Element el : doc.select("div, span")) {
            // Strictly exclude any element inside a link to another product (recommendation card or ad carousel)
            boolean isInsideOtherProductLink = el.parents().stream().anyMatch(p ->
                    p.tagName().equalsIgnoreCase("a") && (p.attr("href").contains("/p/") || p.attr("href").contains("pid=")));
            if (isInsideOtherProductLink) {
                continue;
            }

            // Exclude common recommendation / ad containers
            boolean isInsideRecoContainer = el.parents().stream().anyMatch(p -> {
                String cls = p.className().toLowerCase();
                return cls.contains("reco") || cls.contains("similar") || cls.contains("carousel") || cls.contains("pmu");
            });
            if (isInsideRecoContainer) {
                continue;
            }

            if (el.children().isEmpty() && el.text().trim().matches("₹\\s*[0-9,]+(?:\\.[0-9]+)?")) {
                BigDecimal parsed = parsePriceString(el.text());
                if (parsed != null && parsed.compareTo(BigDecimal.ZERO) > 0) {
                    return parsed;
                }
            }
        }

        return null;
    }

    private BigDecimal parsePriceString(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleanText = text.replace("\u00a0", " ").trim();
        Matcher currencyMatcher = CURRENCY_PRICE_PATTERN.matcher(cleanText);
        if (currencyMatcher.find()) {
            String clean = currencyMatcher.group(1).replace(",", "");
            try {
                return new BigDecimal(clean);
            } catch (NumberFormatException ignored) {}
        }

        Matcher genericMatcher = GENERIC_PRICE_PATTERN.matcher(cleanText);
        if (genericMatcher.find()) {
            String clean = genericMatcher.group(1).replace(",", "");
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
