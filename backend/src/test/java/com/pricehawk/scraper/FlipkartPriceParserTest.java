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

class FlipkartPriceParserTest {

    private FlipkartPriceParser parser;

    @BeforeEach
    void setUp() {
        parser = new FlipkartPriceParser();
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
    @DisplayName("supports only Platform.FLIPKART")
    void supportsFlipkart() {
        assertThat(parser.supports(Platform.FLIPKART)).isTrue();
        assertThat(parser.supports(Platform.AMAZON)).isFalse();
        assertThat(parser.supports(Platform.OTHER)).isFalse();
    }

    @Test
    @DisplayName("parses in-stock Flipkart product with rupee symbol and formatted price")
    void parse_inStockProduct() throws Exception {
        String html = loadFixture("flipkart_product.html");
        String url = "https://www.flipkart.com/apple-macbook-air-m2/p/itm";

        ScrapedProduct product = parser.parse(html, url);

        assertThat(product).isNotNull();
        assertThat(product.getTitle()).isEqualTo("Apple MacBook Air M2 - (8 GB/256 GB SSD/macOS Monterey)");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("89990.00"));
        assertThat(product.getCurrency()).isEqualTo("INR");
        assertThat(product.getImageUrl()).isEqualTo("https://rukminim2.flixcart.com/image/832/832/xif0q/computer/m/b/n/-original-imagfde5ur6geuqz.jpeg");
        assertThat(product.isInStock()).isTrue();
        assertThat(product.getPlatform()).isEqualTo(Platform.FLIPKART);
    }

    @Test
    @DisplayName("parses sold out Flipkart product and flags inStock as false")
    void parse_soldOutProduct() throws Exception {
        String html = loadFixture("flipkart_sold_out.html");
        String url = "https://www.flipkart.com/sneaker/p/itm";

        ScrapedProduct product = parser.parse(html, url);

        assertThat(product).isNotNull();
        assertThat(product.isInStock()).isFalse();
    }
}
