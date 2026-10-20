package com.pricehawk.model;

public enum Platform {
    AMAZON,
    FLIPKART,
    OTHER;

    public static Platform fromUrl(String url) {
        if (url == null || url.isBlank()) {
            return OTHER;
        }
        String lower = url.toLowerCase();
        if (lower.contains("amazon.") || lower.contains("amzn.")) {
            return AMAZON;
        } else if (lower.contains("flipkart.") || lower.contains("fkrt.")) {
            return FLIPKART;
        }
        return OTHER;
    }
}
