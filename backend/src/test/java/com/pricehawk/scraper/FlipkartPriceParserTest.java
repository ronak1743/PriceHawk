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

    @Test
    @DisplayName("does not parse 24 from 24x7 Customer Care or non-price div")
    void parse_doesNotMatch24x7CustomerCare() {
        String html = "<html><body>" +
                "<div class=\"css-g5y9jx\">24x7 Customer Care</div>" +
                "<span class=\"VU-ZEz\">Dyazo Laptop Sleeve</span>" +
                "<div class=\"Nx9bqj CxhGGd\">₹246</div>" +
                "</body></html>";
        ScrapedProduct product = parser.parse(html, "https://www.flipkart.com/dyazo/p/itm123");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("246"));
    }

    @Test
    @DisplayName("correctly extracts true product price on modern Flipkart page ignoring ad carousels")
    void parse_modernReactFlipkartPageWithAdCarousel() {
        String html = "<html><body>" +
                "<div class=\"recommendation-carousel\">" +
                "  <a href=\"/other-laptop-bag/p/itm456?pid=BAG123\">" +
                "    <span class=\"v1zwn21n\">₹370</span>" +
                "  </a>" +
                "</div>" +
                "<h1>Dyazo Plus Laptop Sleeve 15.6 inch...more</h1>" +
                "<script id=\"is_script\">" +
                "  window.__INITIAL_STATE__ = {\"ppd\":{\"fsp\":329,\"finalPrice\":329,\"mrp\":999}};" +
                "</script>" +
                "</body></html>";
        ScrapedProduct product = parser.parse(html, "https://www.flipkart.com/dyazo-laptop-sleeve/p/itm789?pid=ACCHJYHEUFGUVZWP");
        assertThat(product.getTitle()).isEqualTo("Dyazo Plus Laptop Sleeve 15.6 inch");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("329"));
    }

    @Test
    @DisplayName("accurately parses live scraped Flipkart Dyazo product fixture")
    void parse_realLiveFlipkartDyazoFixture() throws Exception {
        String html = loadFixture("flipkart_dyazo.html");
        String url = "https://www.flipkart.com/dyazo-plus-laptop-sleeve-15-6-inch-durable-shockproof-carrying-case-pocket-bag/p/itm711dcbae6461f?pid=ACCHJYHEUFGUVZWP";

        ScrapedProduct product = parser.parse(html, url);
        assertThat(product).isNotNull();
        assertThat(product.getTitle()).contains("Dyazo Plus Laptop Sleeve 15.6 inch");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("329"));
        assertThat(product.isInStock()).isTrue();
    }
}
