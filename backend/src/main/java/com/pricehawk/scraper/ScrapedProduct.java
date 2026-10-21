package com.pricehawk.scraper;

import com.pricehawk.model.Platform;

import java.math.BigDecimal;
import java.util.Objects;

public class ScrapedProduct {

    private String title;
    private BigDecimal price;
    private String currency;
    private String imageUrl;
    private String url;
    private boolean inStock;
    private Platform platform;

    public ScrapedProduct() {}

    public ScrapedProduct(String title, BigDecimal price, String currency, String imageUrl, boolean inStock, Platform platform) {
        this(title, price, currency, imageUrl, null, inStock, platform);
    }

    public ScrapedProduct(String title, BigDecimal price, String currency, String imageUrl, String url, boolean inStock, Platform platform) {
        this.title = title;
        this.price = price;
        this.currency = (currency == null || currency.isBlank()) ? "INR" : currency;
        this.imageUrl = imageUrl;
        this.url = url;
        this.inStock = inStock;
        this.platform = platform;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isInStock() {
        return inStock;
    }

    public void setInStock(boolean inStock) {
        this.inStock = inStock;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScrapedProduct that = (ScrapedProduct) o;
        return inStock == that.inStock &&
                Objects.equals(title, that.title) &&
                (price == null ? that.price == null : that.price != null && price.compareTo(that.price) == 0) &&
                Objects.equals(currency, that.currency) &&
                Objects.equals(imageUrl, that.imageUrl) &&
                platform == that.platform;
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, price, currency, imageUrl, inStock, platform);
    }

    @Override
    public String toString() {
        return "ScrapedProduct{" +
                "title='" + title + '\'' +
                ", price=" + price +
                ", currency='" + currency + '\'' +
                ", inStock=" + inStock +
                ", platform=" + platform +
                '}';
    }
}
