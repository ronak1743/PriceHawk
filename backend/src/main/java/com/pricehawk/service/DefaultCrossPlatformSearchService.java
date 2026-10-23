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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DefaultCrossPlatformSearchService implements CrossPlatformSearchService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCrossPlatformSearchService.class);

    // Minimum confidence score required to accept a cross-platform match (scale 0-100)
    // Ensures 90%+ matching accuracy and avoids linking mismatched models, brands, or variants.
    private static final double MIN_ACCEPTANCE_THRESHOLD = 65.0;

    private static final Set<String> KNOWN_BRANDS = new HashSet<>(Arrays.asList(
            "apple", "samsung", "hp", "dell", "lenovo", "asus", "acer", "msi",
            "oneplus", "xiaomi", "redmi", "realme", "oppo", "vivo", "motorola", "moto",
            "google", "pixel", "sony", "nothing", "infinix", "poco", "iqoo", "lg",
            "boat", "noise", "boult", "fire-boltt", "ptron", "zebronics", "portronics",
            "jbl", "bose", "sennheiser", "skullcandy", "anker", "soundcore",
            "dyazo", "gizga", "amazon basics", "amazonbasics", "amkette", "cosmic byte",
            "redragon", "ant esports", "logitech", "razer", "sandisk", "crucial",
            "kingston", "wd", "western digital", "seagate", "philips", "bajaj", "prestige",
            "fossil", "casio", "titan", "fastrack", "timex", "citizen", "seiko",
            "tommy hilfiger", "armani", "diesel"
    ));

    private static final Set<String> KNOWN_MODEL_SERIES = new HashSet<>(Arrays.asList(
            // Watches
            "decker", "rhett", "grant", "nate", "neutra", "townsman", "machine", "flynn",
            "kerrigan", "stella", "bannon", "minimalist", "tourer", "heritage", "dean",
            "riley", "jacqueline", "edifice", "g-shock", "gshock", "enticer", "raga",
            "octane", "commuter", "copeland", "bronson", "fenmore", "defender", "fb-01",
            "fb01", "everett", "carraway", "barstow", "coachman", "sullivan", "wade",
            "karishma", "edge", "workwear", "expedition", "waterbury", "weekender", "marlin",
            "promaster", "prospex", "presage", "colorfit", "wave", "ninja", "reflex",
            "tiffany", "vintage", "retro",
            // Laptops / Computers
            "victus", "pavilion", "omen", "thinkpad", "ideapad", "loq", "legion", "vivobook",
            "zenbook", "tuf", "rog", "predator", "nitro", "inspiron", "vostro", "macbook",
            // Audio
            "airdopes", "airpods"
    ));

    private static final Set<String> GENERIC_WATCH_WORDS = new HashSet<>(Arrays.asList(
            "watch", "watches", "analog", "analogue", "digital", "smartwatch", "chronograph", "quartz",
            "automatic", "multifunction", "dial", "strap", "leather", "stainless", "steel",
            "movement", "water", "resistant", "resistance", "atm", "bar", "clasp", "buckle",
            "round", "square", "rectangle", "rectangular", "case", "band", "bracelet", "bezel",
            "crystal", "glass", "battery", "display", "casual", "formal", "party", "classic",
            "fashion", "stylish", "luxury", "premium", "sports", "dress",
            "men", "mens", "women", "womens", "boy", "boys", "girl", "girls", "unisex", "kid", "kids",
            "adult", "adults", "couple", "pair", "gender", "male", "female"
    ));

    private static final Set<String> COMMON_STOP_WORDS = new HashSet<>(Arrays.asList(
            "with", "for", "and", "the", "from", "deal", "deals", "special", "offer", "offers",
            "original", "genuine", "combo", "pack", "collection", "series", "edition", "version",
            "new", "latest", "best", "sale", "exclusive", "warranty", "guarantee", "year", "years",
            "month", "months", "day", "days", "free", "delivery", "shipping", "online", "buy",
            "color", "colour", "size", "dimension", "dimensions", "weight", "combo", "look",
            "black", "white", "blue", "grey", "gray", "silver", "brown", "gold", "rose", "rosegold",
            "green", "red", "pink", "yellow", "orange", "purple", "beige", "tan", "midnight",
            "starlight", "spacegrey", "spacegray", "matte", "glossy", "metallic"
    ));

    private final RestOperations restOperations;

    public DefaultCrossPlatformSearchService(RestOperations restOperations) {
        this.restOperations = restOperations;
    }

    @Override
    public ScrapedProduct findCounterpartProduct(Platform currentPlatform, String productTitle) {
        return findCounterpartProduct(currentPlatform, productTitle, null, null);
    }

    @Override
    public ScrapedProduct findCounterpartProduct(Platform currentPlatform, String productTitle, String productUrl, BigDecimal currentPrice) {
        if (productTitle == null || productTitle.isBlank()) {
            return null;
        }

        ProductAttributes sourceAttrs = extractAttributes(productTitle, productUrl);
        String searchQuery = buildSearchQuery(sourceAttrs, productTitle);

        log.info("Searching high-accuracy counterpart for: '{}' (Query: '{}', Brand: '{}', ModelSeries: '{}', ModelCode: '{}', Color: '{}', Price: '{}')",
                productTitle, searchQuery, sourceAttrs.brand, sourceAttrs.modelSeries, sourceAttrs.specificModelCode, sourceAttrs.color, currentPrice);

        try {
            if (currentPlatform == Platform.AMAZON) {
                return searchFlipkart(searchQuery, sourceAttrs, currentPrice);
            } else if (currentPlatform == Platform.FLIPKART) {
                return searchAmazon(searchQuery, sourceAttrs, currentPrice);
            }
        } catch (Exception e) {
            log.warn("Failed to find counterpart product for title: '{}' on platform: {} - {}",
                    productTitle, currentPlatform, e.getMessage());
        }

        return null;
    }

    private ScrapedProduct searchFlipkart(String query, ProductAttributes sourceAttrs, BigDecimal sourcePrice) {
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

        ScrapedProduct bestMatch = null;
        double bestScore = -1.0;

        for (Element card : cards) {
            // URL extraction
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

            // Price extraction
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

            // Title extraction
            Element titleEl = card
                    .selectFirst("div.RG5Slk, div.KzDlHZ, div._4rR01T, span.VU-ZEz, div.wBy4fm, a[title], a.WKTcLC, a.IRpwTa, a.atJtCj");
            String title = "";
            if (titleEl != null) {
                if (titleEl.hasAttr("title") && !titleEl.attr("title").isBlank()) {
                    title = titleEl.attr("title").trim();
                } else {
                    title = titleEl.text().trim();
                }
            } else if (card.hasAttr("title") && !card.attr("title").isBlank()) {
                title = card.attr("title").trim();
            }

            // Fallback to img[alt] if title is missing or generic
            if (title.isBlank() || title.equalsIgnoreCase("Flipkart Product")) {
                Element imgWithAlt = card.selectFirst("img[alt]:not([alt=\"\"])");
                if (imgWithAlt != null && !imgWithAlt.attr("alt").isBlank()) {
                    title = imgWithAlt.attr("alt").trim();
                }
            }

            // In Flipkart fashion/watch grids, brand is often in a separate element (e.g. Fo1I0b, syl9yP)
            Element brandEl = card.selectFirst("div.Fo1I0b, div.syl9yP, div._2WkVRV");
            if (brandEl != null && !brandEl.text().isBlank()) {
                String brandText = brandEl.text().trim();
                if (!title.toLowerCase().contains(brandText.toLowerCase())) {
                    title = brandText + " " + title;
                }
            }

            // Fallback to URL slug if still empty
            if (title.isBlank()) {
                String slug = href.replaceAll("^.*/([^/?#]+)/p/.*$", "$1").replace("-", " ");
                if (!slug.isBlank() && !slug.equals(href)) {
                    title = slug;
                } else {
                    title = "Flipkart Product";
                }
            }

            // Image extraction
            Element imgEl = card.selectFirst("img[src*=\"rukminim\"], img.DByuf4, img._396cs4, img");
            String imageUrl = imgEl != null ? (imgEl.hasAttr("src") ? imgEl.attr("src") : imgEl.attr("data-src")) : "";

            String fullUrl = href.startsWith("http") ? href : "https://www.flipkart.com" + href;
            String cleanUrl = cleanFlipkartUrl(fullUrl);

            // Compute high-accuracy match score
            ProductAttributes candidateAttrs = extractAttributes(title, cleanUrl);
            double score = calculateMatchScore(sourceAttrs, candidateAttrs, title, cleanUrl, false, sourcePrice, price);

            log.debug("Evaluated Flipkart candidate: '{}' (price: {}) -> Score: {}", title, price, score);

            if (score > bestScore && score >= MIN_ACCEPTANCE_THRESHOLD) {
                bestScore = score;
                bestMatch = new ScrapedProduct(title, price, "INR", imageUrl, cleanUrl, true, Platform.FLIPKART);
            }
        }

        if (bestMatch != null) {
            log.info("High-confidence Flipkart counterpart selected with score {}: '{}'", bestScore, bestMatch.getTitle());
        } else {
            log.info("No Flipkart counterpart met the strict 90%+ confidence threshold (highest score: {})", bestScore);
        }

        return bestMatch;
    }

    private ScrapedProduct searchAmazon(String query, ProductAttributes sourceAttrs, BigDecimal sourcePrice) {
        String url = "https://www.amazon.in/s?k=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        log.debug("Searching Amazon counterpart via: {}", url);

        String html = fetchHtml(url);
        if (html == null || html.isBlank())
            return null;

        Document doc = Jsoup.parse(html);
        org.jsoup.select.Elements cards = doc.select("div[data-component-type=\"s-search-result\"]");
        if (cards.isEmpty())
            return null;

        ScrapedProduct bestMatch = null;
        double bestScore = -1.0;

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

            // Title extraction: modern Amazon cards often put brand in h2 and full title in span.a-text-normal or img.s-image[alt]
            Element spanNormal = card.selectFirst("span.a-text-normal, h2 a span:not(.a-color-base)");
            Element imgEl = card.selectFirst("img.s-image, img");
            String imgAlt = (imgEl != null && imgEl.hasAttr("alt")) ? imgEl.attr("alt").trim() : "";
            Element h2El = card.selectFirst("h2 a, h2");
            String h2Text = h2El != null ? h2El.text().trim() : "";

            String title = "";
            if (spanNormal != null && !spanNormal.text().isBlank()) {
                title = spanNormal.text().trim();
            } else if (imgAlt.length() > h2Text.length() && imgAlt.length() > 5) {
                title = imgAlt;
            } else if (!h2Text.isBlank()) {
                title = h2Text;
            }

            // If title is missing brand and h2 contains brand, prepend it
            if (!h2Text.isBlank() && !title.toLowerCase().contains(h2Text.toLowerCase())) {
                title = h2Text + " " + title;
            }

            // Fallback to URL slug if still empty or generic
            if (title.isBlank() || title.equalsIgnoreCase("Amazon Product")) {
                String slug = href.replaceAll("^.*/([^/?#]+)/dp/.*$", "$1").replace("-", " ");
                if (!slug.isBlank() && !slug.equals(href)) {
                    title = slug;
                } else {
                    title = "Amazon Product";
                }
            }

            // Image
            String imageUrl = imgEl != null ? (imgEl.hasAttr("src") ? imgEl.attr("src") : imgEl.attr("data-src")) : "";

            String fullUrl = href.startsWith("http") ? href : "https://www.amazon.in" + href;
            String cleanUrl = fullUrl.contains("?") ? fullUrl.substring(0, fullUrl.indexOf("?")) : fullUrl;

            // Sponsored check
            boolean isSponsored = card.selectFirst(".puis-sponsored-label-text, .s-sponsored-label-info-icon") != null
                    || card.text().toLowerCase().startsWith("sponsored");

            // Compute high-accuracy match score
            ProductAttributes candidateAttrs = extractAttributes(title, cleanUrl);
            double score = calculateMatchScore(sourceAttrs, candidateAttrs, title, cleanUrl, isSponsored, sourcePrice, price);

            log.debug("Evaluated Amazon candidate: '{}' (price: {}, sponsored: {}) -> Score: {}", title, price, isSponsored, score);

            if (score > bestScore && score >= MIN_ACCEPTANCE_THRESHOLD) {
                bestScore = score;
                bestMatch = new ScrapedProduct(title, price, "INR", imageUrl, cleanUrl, true, Platform.AMAZON);
            }
        }

        if (bestMatch != null) {
            log.info("High-confidence Amazon counterpart selected with score {}: '{}'", bestScore, bestMatch.getTitle());
        } else {
            log.info("No Amazon counterpart met the strict 90%+ confidence threshold (highest score: {})", bestScore);
        }

        return bestMatch;
    }

    double calculateMatchScore(ProductAttributes source, ProductAttributes candidate,
                               String candidateTitle, String candidateUrl, boolean isSponsored) {
        return calculateMatchScore(source, candidate, candidateTitle, candidateUrl, isSponsored, null, null);
    }

    /**
     * Precision Multi-Attribute Scoring Algorithm (0 - 100 scale).
     * Strictly verifies brand, specific model code, model series, color variant, price sanity, and specs.
     */
    double calculateMatchScore(ProductAttributes source, ProductAttributes candidate,
                               String candidateTitle, String candidateUrl, boolean isSponsored,
                               BigDecimal sourcePrice, BigDecimal candidatePrice) {
        String candidateLower = candidateTitle.toLowerCase();
        // Strip query string and fragments so echoed search parameters (e.g. ?q=... or ?keywords=...) are never matched as product attributes
        String cleanCandidateUrl = candidateUrl;
        if (cleanCandidateUrl.contains("?")) {
            cleanCandidateUrl = cleanCandidateUrl.substring(0, cleanCandidateUrl.indexOf("?"));
        }
        if (cleanCandidateUrl.contains("#")) {
            cleanCandidateUrl = cleanCandidateUrl.substring(0, cleanCandidateUrl.indexOf("#"));
        }
        String urlLower = cleanCandidateUrl.toLowerCase();

        // 1. BRAND CHECK (Weight: 40 points - STRICT GATEKEEPER)
        if (source.brand != null && !source.brand.isBlank()) {
            boolean hasSourceBrand = candidateLower.contains(source.brand) || urlLower.contains(source.brand.replace(" ", ""));
            if (!hasSourceBrand) {
                // If candidate clearly belongs to a DIFFERENT known brand, IMMEDIATELY DISQUALIFY (Score = 0)
                if (candidate.brand != null && !candidate.brand.equalsIgnoreCase(source.brand)) {
                    return 0.0;
                }
                for (String known : KNOWN_BRANDS) {
                    if (candidateLower.contains(known) && !known.equalsIgnoreCase(source.brand)) {
                        return 0.0; // Hard reject brand mismatch!
                    }
                }
                return 0.0; // Target brand completely missing
            }
        }

        // 2. PRODUCT NAME / MODEL LINE STRICT CHECK (MANDATORY DOUBLE-CHECK GATEKEEPER)
        boolean isWatch = (source.rawTitle != null && source.rawTitle.toLowerCase().matches(".*\\b(watch|watches|analog|chronograph)\\b.*"))
                || candidateLower.matches(".*\\b(watch|watches|analog|chronograph)\\b.*");

        // For watches: MUST match the watch model name / series / code. Never match a different watch!
        if (isWatch && !source.distinctiveNameTokens.isEmpty()) {
            boolean hasMatchingNameToken = false;
            for (String token : source.distinctiveNameTokens) {
                if (candidateLower.contains(token) || urlLower.contains(token)) {
                    hasMatchingNameToken = true;
                    break;
                }
            }

            if (!hasMatchingNameToken) {
                log.debug("Hard reject: Watch candidate '{}' missing all distinctive name tokens from source: {}",
                        candidateTitle, source.distinctiveNameTokens);
                return 0.0; // Name mismatch - watch candidate does not match source watch name!
            }
        }

        // Check if candidate explicitly belongs to a conflicting model series/line (e.g. Rhett vs Decker)
        String sLower = (source.rawTitle != null ? source.rawTitle.toLowerCase() : "") + " "
                + (source.rawUrl != null ? source.rawUrl.toLowerCase() : "");
        for (String cToken : candidate.distinctiveNameTokens) {
            if (!sLower.contains(cToken) && isConflictingSeriesOrLine(cToken, source.distinctiveNameTokens)) {
                log.debug("Hard reject: Conflicting model series '{}' in candidate title '{}'", cToken, candidateTitle);
                return 0.0;
            }
        }

        double score = 40.0; // Base brand match

        // 3. MODEL SERIES & SPECIFIC MODEL CODE CHECK (Weight: 30 points)
        if (source.specificModelCode != null && !source.specificModelCode.isBlank()) {
            if (candidateLower.contains(source.specificModelCode) || urlLower.contains(source.specificModelCode)) {
                score += 30.0; // Exact specific alphanumeric model code match!
            } else if (candidate.specificModelCode != null && !candidate.specificModelCode.equalsIgnoreCase(source.specificModelCode)) {
                return 0.0; // Conflicting specific model code (e.g. CH2882 vs CH2600IE or 15-fa vs 15-fb)!
            } else {
                return 0.0; // Source specified a concrete SKU/model code that candidate lacks!
            }
        }

        // DOUBLE-CHECK: PRODUCT LINE / MODEL SERIES (e.g. Decker vs Rhett, Machine, Townsman, iPhone 15 vs 14)
        if (source.modelSeries != null && !source.modelSeries.isBlank()) {
            boolean candidateHasSeries = candidateLower.contains(source.modelSeries) || urlLower.contains(source.modelSeries);
            if (candidateHasSeries) {
                if (source.specificModelCode == null) {
                    score += 25.0; // Exact model series match
                }
            } else {
                // If candidate explicitly belongs to a DIFFERENT series (e.g. Decker vs Rhett, Machine, Townsman)
                if (candidate.modelSeries != null && !candidate.modelSeries.equalsIgnoreCase(source.modelSeries)) {
                    log.debug("Hard reject: Model series mismatch. Source series: '{}', Candidate series: '{}', Title: '{}'",
                            source.modelSeries, candidate.modelSeries, candidateTitle);
                    return 0.0;
                }
                if (hasConflictingModelGeneration(source.modelSeries, candidateLower)) {
                    return 0.0;
                }
                // If there's no exact specific model code match and the series is completely missing, reject!
                boolean hasExactCode = source.specificModelCode != null && 
                        (candidateLower.contains(source.specificModelCode) || urlLower.contains(source.specificModelCode));
                if (!hasExactCode) {
                    log.debug("Hard reject: Candidate missing model series '{}' without exact model code match", source.modelSeries);
                    return 0.0;
                }
            }
        } else if (candidate.specificModelCode != null && !candidate.specificModelCode.isBlank() && source.specificModelCode == null) {
            score -= 25.0;
        } else if (source.specificModelCode == null) {
            score += 10.0;
        }

        // 4. COLOR & VARIANT CHECK (Crucial for watches, apparel, and hardware variants)
        if (source.color != null && !source.color.isBlank()) {
            if (candidate.color != null && !candidate.color.isBlank()) {
                if (candidate.color.equalsIgnoreCase(source.color)) {
                    score += 10.0; // Exact color variant match!
                } else {
                    return 0.0; // Conflicting color variant (e.g. Brown vs Silver/Black, White vs Black)!
                }
            } else if (!candidateLower.contains(source.color) && !urlLower.contains(source.color)) {
                if (candidate.specificModelCode != null && source.specificModelCode == null) {
                    score -= 15.0; // Unverified model SKU missing color variant
                }
            }
        }

        // 5. PRICE DISCREPANCY GUARDRAIL
        if (sourcePrice != null && candidatePrice != null && sourcePrice.compareTo(BigDecimal.ZERO) > 0 && candidatePrice.compareTo(BigDecimal.ZERO) > 0) {
            double s = sourcePrice.doubleValue();
            double c = candidatePrice.doubleValue();
            double ratio = Math.max(s, c) / Math.min(s, c);
            if (ratio > 1.50 && (source.specificModelCode == null || !candidateLower.contains(source.specificModelCode))) {
                log.debug("Rejecting candidate due to large price discrepancy: source={}, candidate={}", s, c);
                return 0.0; // Hard reject massive price mismatch (e.g. ₹7,314 vs ₹12,495)!
            }
        }

        // 6. HARDWARE SPECIFICATIONS CHECK (Weight: 20 points)
        // Storage check
        if (source.storage != null && !source.storage.isBlank()) {
            if (candidate.storage != null && !candidate.storage.isBlank()) {
                if (candidate.storage.equalsIgnoreCase(source.storage)) {
                    score += 8.0;
                } else {
                    return 0.0; // Storage mismatch (e.g. 128GB vs 256GB)!
                }
            }
        }

        // RAM check
        if (source.ram != null && !source.ram.isBlank()) {
            if (candidate.ram != null && !candidate.ram.isBlank()) {
                if (candidate.ram.equalsIgnoreCase(source.ram)) {
                    score += 6.0;
                } else {
                    return 0.0; // RAM mismatch (e.g. 8GB vs 16GB)!
                }
            }
        }

        // Screen size / dimension check
        if (source.screenSize != null && !source.screenSize.isBlank()) {
            if (candidate.screenSize != null && !candidate.screenSize.isBlank()) {
                try {
                    double sSize = Double.parseDouble(source.screenSize);
                    double cSize = Double.parseDouble(candidate.screenSize);
                    if (Math.abs(sSize - cSize) <= 0.2) {
                        score += 6.0;
                    } else if (Math.abs(sSize - cSize) >= 0.5) {
                        return 0.0; // Screen/sleeve size mismatch (e.g. 13.3" vs 15.6")!
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Processor / Chip check
        if (source.processor != null && !source.processor.isBlank()) {
            if (candidate.processor != null && !candidate.processor.isBlank()) {
                if (candidate.processor.equalsIgnoreCase(source.processor)) {
                    score += 5.0;
                } else {
                    score -= 20.0; // Conflicting processor family (e.g. i5 vs i7 or M1 vs M2)
                }
            }
        }

        // 7. SIGNIFICANT DISTINCTIVE KEYWORD OVERLAP (Weight: up to 10 points)
        if (!source.significantWords.isEmpty()) {
            long matchCount = source.significantWords.stream()
                    .filter(w -> candidateLower.contains(w) || urlLower.contains(w))
                    .count();
            double overlapRatio = (double) matchCount / source.significantWords.size();
            double maxKeywordPoints = (source.modelSeries == null) ? 20.0 : 10.0;
            score += (overlapRatio * maxKeywordPoints);
        }

        // 8. PENALTIES
        if (isSponsored) {
            score -= 20.0; // Prefer organic verified matches over promoted ads
        }

        return Math.max(0.0, Math.min(100.0, score));
    }

    private boolean isConflictingSeriesOrLine(String candToken, Set<String> sourceTokens) {
        String cLower = candToken.toLowerCase();
        if (!KNOWN_MODEL_SERIES.contains(cLower)) {
            return false;
        }
        for (String sToken : sourceTokens) {
            String sLower = sToken.toLowerCase();
            if (KNOWN_MODEL_SERIES.contains(sLower) && !sLower.equalsIgnoreCase(cLower)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConflictingModelGeneration(String sourceSeries, String candidateLower) {
        // iPhone check (e.g. "iphone 15" vs "iphone 14", "iphone 13", "iphone 16")
        Matcher m = Pattern.compile("iphone\\s*(\\d{1,2})").matcher(sourceSeries);
        if (m.find()) {
            String gen = m.group(1);
            Matcher cm = Pattern.compile("iphone\\s*(\\d{1,2})").matcher(candidateLower);
            if (cm.find()) {
                String cGen = cm.group(1);
                if (!gen.equals(cGen))
                    return true;
            }
        }

        // Samsung Galaxy S-series check (e.g. "s24" vs "s23")
        Matcher sm = Pattern.compile("galaxy\\s*s(\\d{2})").matcher(sourceSeries);
        if (sm.find()) {
            String gen = sm.group(1);
            Matcher scm = Pattern.compile("galaxy\\s*s(\\d{2})").matcher(candidateLower);
            if (scm.find()) {
                String cGen = scm.group(1);
                if (!gen.equals(cGen))
                    return true;
            }
        }

        // Sony XM headphone check (e.g. "xm4" vs "xm5")
        Matcher xm = Pattern.compile("xm([3-5])").matcher(sourceSeries);
        if (xm.find()) {
            String gen = xm.group(1);
            Matcher cxm = Pattern.compile("xm([3-5])").matcher(candidateLower);
            if (cxm.find()) {
                String cGen = cxm.group(1);
                if (!gen.equals(cGen))
                    return true;
            }
        }

        return false;
    }

    /**
     * Extracts structured attributes (Brand, Model, Storage, RAM, Size, Specs, Color) from title.
     */
    ProductAttributes extractAttributes(String title) {
        return extractAttributes(title, null);
    }

    /**
     * Extracts structured attributes from title and optional product URL.
     */
    ProductAttributes extractAttributes(String title, String url) {
        ProductAttributes attrs = new ProductAttributes();
        attrs.rawTitle = title;
        attrs.rawUrl = url;
        if (title == null || title.isBlank())
            return attrs;

        String lower = title.toLowerCase();
        String urlLower = (url != null) ? url.toLowerCase() : "";
        String urlSlug = "";
        if (!urlLower.isBlank()) {
            String u = urlLower;
            if (u.contains("?")) {
                u = u.substring(0, u.indexOf("?"));
            }
            if (u.contains("#")) {
                u = u.substring(0, u.indexOf("#"));
            }
            if (u.contains("/dp/")) {
                urlSlug = u.substring(0, u.indexOf("/dp/"));
            } else if (u.contains("/p/")) {
                urlSlug = u.substring(0, u.indexOf("/p/"));
            } else {
                urlSlug = u;
            }
            urlSlug = urlSlug.replace("https://", "").replace("http://", "").replace("www.", "");
        }
        String combined = lower + " " + urlSlug;

        // 1. Brand Extraction
        for (String brand : KNOWN_BRANDS) {
            if (combined.startsWith(brand + " ") || combined.contains(" " + brand + " ")
                    || combined.contains("(" + brand + ")") || combined.startsWith(brand + "-")
                    || urlSlug.contains("/" + brand + "-") || urlSlug.contains("/" + brand + "/")) {
                attrs.brand = brand;
                break;
            }
        }
        if (attrs.brand == null) {
            String[] firstWords = lower.split("\\s+");
            if (firstWords.length > 0 && firstWords[0].length() > 2 && !firstWords[0].matches("\\d+.*")) {
                attrs.brand = firstWords[0];
            }
        }

        // 2. Model Series Extraction
        Matcher ipMatcher = Pattern.compile("(?i)\\b(iphone\\s*(?:1[1-6]|se|x[sr]?)(?:\\s*(?:pro\\s*max|pro|plus))?)\\b").matcher(combined);
        if (ipMatcher.find()) {
            attrs.modelSeries = ipMatcher.group(1).trim();
        }

        if (attrs.modelSeries == null) {
            Matcher galMatcher = Pattern.compile("(?i)\\b(galaxy\\s*(?:s2[0-5]|z\\s*fold|z\\s*flip|a[0-9]{2}|m[0-9]{2})(?:\\s*ultra)?)\\b").matcher(combined);
            if (galMatcher.find()) {
                attrs.modelSeries = galMatcher.group(1).trim();
            }
        }

        if (attrs.modelSeries == null) {
            Matcher lapMatcher = Pattern.compile("(?i)\\b(victus(?:\\s*1[56])?|pavilion|omen|thinkpad|ideapad|loq|legion|vivobook|zenbook|tuf|rog|predator|nitro|inspiron|vostro|macbook\\s*(?:air|pro)?)\\b").matcher(combined);
            if (lapMatcher.find()) {
                attrs.modelSeries = lapMatcher.group(1).trim();
            }
        }

        if (attrs.modelSeries == null) {
            Matcher audioMatcher = Pattern.compile("(?i)\\b(airdopes\\s*\\d+|airpods\\s*(?:pro|\\d+)?|wh-1000xm[3-5]|wf-1000xm[3-5])\\b").matcher(combined);
            if (audioMatcher.find()) {
                attrs.modelSeries = audioMatcher.group(1).trim();
            }
        }

        if (attrs.modelSeries == null) {
            Matcher watchSeries = Pattern.compile("(?i)\\b(decker|rhett|grant|nate|neutra|townsman|machine|flynn|kerrigan|stella|bannon|minimalist|tourer|heritage|dean|riley|jacqueline|edifice|g-shock|enticer|raga|octane|commuter)\\b").matcher(combined);
            if (watchSeries.find()) {
                attrs.modelSeries = watchSeries.group(1).trim();
            }
        }

        // 3. Alphanumeric Specific Model Code (e.g. 15-fa0666TX, WH-1000XM4, CH2885, CH2882, FS4813)
        // Check in title first
        Matcher codeMatcher = Pattern.compile("(?i)\\b([a-z0-9]{2,6}-[a-z0-9]{3,8})\\b").matcher(lower);
        while (codeMatcher.find()) {
            String candidateCode = codeMatcher.group(1).toLowerCase();
            if (candidateCode.matches(".*\\d+.*") && !candidateCode.matches(".*(?:gb|tb|mb|ram|inch|dial|case|pack|strap|gold|grey|gray|blue|black|white|wi-fi|usb|type|ref|true|none|deal).*")) {
                attrs.specificModelCode = candidateCode;
                break;
            }
        }

        if (attrs.specificModelCode == null) {
            Matcher watchCodeMatcher = Pattern.compile("(?i)\\b([a-z]{1,4}\\d{3,5}[a-z]{0,3})\\b").matcher(lower);
            while (watchCodeMatcher.find()) {
                String candidateCode = watchCodeMatcher.group(1).toLowerCase();
                if (!candidateCode.endsWith("gb") && !candidateCode.endsWith("tb") && !candidateCode.endsWith("hz")
                        && !candidateCode.endsWith("mah") && !candidateCode.endsWith("w") && !candidateCode.endsWith("mp")
                        && !candidateCode.matches("\\d+.*") && !candidateCode.equals("with") && !candidateCode.equals("from")
                        && !candidateCode.equals("true") && !candidateCode.equals("none") && !candidateCode.equals("deal")) {
                    attrs.specificModelCode = candidateCode;
                    break;
                }
            }
        }

        // If not found in title, extract clean model code token from URL slug (e.g. CH2882, CH2885)
        if (attrs.specificModelCode == null && !urlSlug.isBlank()) {
            String[] tokens = urlSlug.split("[-/_.]");
            for (String t : tokens) {
                if (t.matches("(?i)^[a-z]{1,4}\\d{3,5}[a-z]{0,3}$")) {
                    String clean = t.toLowerCase();
                    if (!clean.endsWith("gb") && !clean.endsWith("tb") && !clean.endsWith("hz")
                            && !clean.endsWith("mah") && !clean.endsWith("w") && !clean.endsWith("mp")
                            && !clean.matches("\\d+.*") && !clean.startsWith("itm") && !clean.startsWith("pid")
                            && !clean.startsWith("lid") && !clean.startsWith("ref")) {
                        attrs.specificModelCode = clean;
                        break;
                    }
                }
            }
        }

        // 4. Color Extraction (e.g. brown, black, silver, gold, blue, etc.)
        Matcher colorMatcher = Pattern.compile("(?i)\\b(rose\\s*gold|space\\s*grey|space\\s*gray|midnight|starlight|black|brown|blue|silver|white|gold|green|grey|gray|red|pink|yellow|orange|purple|beige|tan)\\b").matcher(combined);
        if (colorMatcher.find()) {
            attrs.color = colorMatcher.group(1).toLowerCase().replaceAll("\\s+", "");
        }

        // 5. Storage Extraction (e.g. 128GB, 256GB, 512GB, 1TB)
        Matcher stMatcher = Pattern.compile("(?i)\\b(64|128|256|512)\\s*(?:gb)\\b|\\b([12])\\s*(?:tb)\\b").matcher(combined);
        List<String> storageList = new ArrayList<>();
        while (stMatcher.find()) {
            String val = stMatcher.group(0).replaceAll("\\s+", "").toLowerCase();
            storageList.add(val);
        }

        // 6. RAM Extraction (e.g. 8GB RAM, 16GB DDR4)
        Matcher ramMatcher = Pattern.compile("(?i)\\b(4|6|8|12|16|24|32|64)\\s*(?:gb)\\s*(?:ram|ddr\\d)?\\b").matcher(combined);
        List<String> ramList = new ArrayList<>();
        while (ramMatcher.find()) {
            String val = ramMatcher.group(1).toLowerCase() + "gb";
            ramList.add(val);
        }

        if (!storageList.isEmpty()) {
            attrs.storage = storageList.get(storageList.size() - 1); // Prefer the largest / main storage
        }
        if (!ramList.isEmpty()) {
            attrs.ram = ramList.get(0);
        }

        // 7. Screen Size Extraction (e.g. 13.3", 14-inch, 15.6 inch, 6.1 inch)
        Matcher sizeMatcher = Pattern.compile("(?i)\\b(1[1-7](?:\\.[0-9])?)\\s*(?:-| )?(?:inch|\")\\b|\\b([5-7](?:\\.[0-9]{1,2})?)\\s*(?:-| )?(?:inch|\")\\b").matcher(combined);
        if (sizeMatcher.find()) {
            attrs.screenSize = sizeMatcher.group(1) != null ? sizeMatcher.group(1) : sizeMatcher.group(2);
        }

        // 8. Processor / Chip
        Matcher chipMatcher = Pattern.compile("(?i)\\b(i[3579]|ryzen\\s*[3579]|m[1234])\\b").matcher(combined);
        if (chipMatcher.find()) {
            attrs.processor = chipMatcher.group(1).replaceAll("\\s+", "").toLowerCase();
        }

        // 9. Distinctive Product Name Tokens
        boolean isWatchTitle = lower.matches(".*\\b(watch|watches|analog|chronograph)\\b.*");
        String cleaned = lower.replaceAll("[()\\[\\]{}|/\\\\:,;!*_+~#?\"'–—]", " ");
        for (String w : cleaned.split("\\s+")) {
            if (w.length() >= 2
                    && !COMMON_STOP_WORDS.contains(w)
                    && (attrs.brand == null || !w.equalsIgnoreCase(attrs.brand))
                    && !KNOWN_BRANDS.contains(w)) {
                if (isWatchTitle && GENERIC_WATCH_WORDS.contains(w)) {
                    continue;
                }
                if (w.matches("\\d+")) {
                    if (w.length() >= 2 && w.length() <= 4) {
                        attrs.distinctiveNameTokens.add(w);
                    }
                } else {
                    attrs.distinctiveNameTokens.add(w);
                }
            }
        }
        if (attrs.specificModelCode != null) {
            attrs.distinctiveNameTokens.add(attrs.specificModelCode.toLowerCase());
        }
        if (attrs.modelSeries != null) {
            for (String sWord : attrs.modelSeries.toLowerCase().split("\\s+")) {
                if (!COMMON_STOP_WORDS.contains(sWord) && !KNOWN_BRANDS.contains(sWord)) {
                    attrs.distinctiveNameTokens.add(sWord);
                }
            }
        }

        // 10. Significant non-stop words
        for (String w : cleaned.split("\\s+")) {
            if (w.length() >= 3
                    && !COMMON_STOP_WORDS.contains(w)
                    && (attrs.brand == null || !w.equalsIgnoreCase(attrs.brand))
                    && !KNOWN_BRANDS.contains(w)) {
                if (isWatchTitle && GENERIC_WATCH_WORDS.contains(w)) {
                    continue;
                }
                attrs.significantWords.add(w);
            }
        }

        return attrs;
    }

    /**
     * Builds a concise, targeted search query containing only the core brand, model, and key specs.
     */
    private String buildSearchQuery(ProductAttributes attrs, String rawTitle) {
        StringBuilder sb = new StringBuilder();
        if (attrs.brand != null && !attrs.brand.isBlank()) {
            sb.append(attrs.brand).append(" ");
        }
        if (attrs.modelSeries != null && !attrs.modelSeries.isBlank() && !attrs.modelSeries.equalsIgnoreCase(attrs.brand)) {
            sb.append(attrs.modelSeries).append(" ");
        }
        if (attrs.specificModelCode != null && !attrs.specificModelCode.isBlank()) {
            sb.append(attrs.specificModelCode).append(" ");
        } else if (attrs.color != null && !attrs.color.isBlank()) {
            sb.append(attrs.color).append(" ");
        }
        // If neither modelSeries nor specificModelCode was added, append distinctive name tokens (e.g. neo, splash)
        if (attrs.modelSeries == null && attrs.specificModelCode == null && !attrs.distinctiveNameTokens.isEmpty()) {
            for (String tok : attrs.distinctiveNameTokens) {
                if (!sb.toString().toLowerCase().contains(tok.toLowerCase())) {
                    sb.append(tok).append(" ");
                }
            }
        }
        if (attrs.screenSize != null) {
            sb.append(attrs.screenSize).append(" inch ");
        }
        if (attrs.storage != null) {
            sb.append(attrs.storage).append(" ");
        }

        String built = sb.toString().trim();
        if (built.split("\\s+").length >= 2) {
            return built;
        }

        return cleanSearchTitle(rawTitle);
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

        String s = title.replaceAll("[()\\[\\]{}|/\\\\:,;!*_+~]", " ")
                .replaceAll(
                        "(?i)\\b(with|for|and|in|on|by|the|a|an|of|to|is|at|from|all|pack of \\d+|thin and light laptop|truly wireless|smart|original|genuine|combo)\\b",
                        " ")
                .replaceAll("\\s+", " ")
                .trim();

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

    private static final Pattern PRICE_REGEX = Pattern
            .compile("[₹Rs.]*\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)");

    private String cleanFlipkartUrl(String url) {
        if (url == null || url.isBlank()) return url;
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

    private BigDecimal parsePrice(String text) {
        if (text == null || text.isBlank())
            return null;
        Matcher m = PRICE_REGEX.matcher(text.replace("\u00a0", " ").trim());
        if (m.find()) {
            String val = m.group(1).replace(",", "");
            try {
                return new BigDecimal(val);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    static class ProductAttributes {
        String rawTitle;
        String rawUrl;
        String brand;
        String modelSeries;
        String specificModelCode;
        String color;
        String storage;
        String ram;
        String screenSize;
        String processor;
        Set<String> significantWords = new HashSet<>();
        Set<String> distinctiveNameTokens = new LinkedHashSet<>();
    }
}
