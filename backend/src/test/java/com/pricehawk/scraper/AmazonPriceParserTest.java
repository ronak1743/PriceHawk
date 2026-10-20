package com.pricehawk.scraper;

import com.pricehawk.model.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AmazonPriceParserTest {

    private AmazonPriceParser parser;

    @BeforeEach
    void setUp() {
        parser = new AmazonPriceParser();
    }

    private String loadFixture(String filename) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/" + filename)) {
            if (is == null) {
                throw new IllegalArgumentException("Fixture not found: " + filename);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("supports only Platform.AMAZON")
    void supportsAmazon() {
        assertThat(parser.supports(Platform.AMAZON)).isTrue();
        assertThat(parser.supports(Platform.FLIPKART)).isFalse();
        assertThat(parser.supports(Platform.OTHER)).isFalse();
    }

    @Test
    @DisplayName("parses in-stock Amazon India product with rupee symbol and formatted price")
    void parse_inStockProduct() throws Exception {
        String html = loadFixture("amazon_product.html");
        String url = "https://www.amazon.in/dp/B0CHX1W1XY";

        ScrapedProduct product = parser.parse(html, url);

        assertThat(product).isNotNull();
        assertThat(product.getTitle()).isEqualTo("Apple iPhone 15 (128 GB) - Black");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("69999.00"));
        assertThat(product.getCurrency()).isEqualTo("INR");
        assertThat(product.getImageUrl()).isEqualTo("https://m.media-amazon.com/images/I/71657TiFeHL._SL1500_.jpg");
        assertThat(product.isInStock()).isTrue();
        assertThat(product.getPlatform()).isEqualTo(Platform.AMAZON);
    }

    @Test
    @DisplayName("parses out-of-stock Amazon product and flags inStock as false")
    void parse_outOfStockProduct() throws Exception {
        String html = loadFixture("amazon_out_of_stock.html");
        String url = "https://www.amazon.in/dp/B000000000";

        ScrapedProduct product = parser.parse(html, url);

        assertThat(product).isNotNull();
        assertThat(product.getTitle()).isEqualTo("Vintage Rare Collectible Watch");
        assertThat(product.isInStock()).isFalse();
    }
}
