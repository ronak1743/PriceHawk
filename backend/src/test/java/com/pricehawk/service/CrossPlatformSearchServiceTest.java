package com.pricehawk.service;

import com.pricehawk.model.Platform;
import com.pricehawk.scraper.ScrapedProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestOperations;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrossPlatformSearchServiceTest {

    @Mock
    private RestOperations restOperations;

    private CrossPlatformSearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new DefaultCrossPlatformSearchService(restOperations);
    }

    @Test
    @DisplayName("When current platform is AMAZON, searches FLIPKART and extracts matching product")
    void findCounterpart_fromAmazonToFlipkart() {
        String flipkartSearchHtml = """
                <html>
                <body>
                  <div class="tUxRFH">
                    <a class="CGtC5Q" href="/apple-iphone-15-black-128-gb/p/itm12345?pid=MOBGTAG">
                      <div class="KzDlHZ">Apple iPhone 15 (Black, 128 GB)</div>
                      <div class="Nx9bqj _4b5DiR">₹65,999</div>
                      <img class="DByuf4" src="https://rukminim2.flixcart.com/iphone.jpg" />
                    </a>
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(flipkartSearchHtml);

        ScrapedProduct result = searchService.findCounterpartProduct(Platform.AMAZON, "Apple iPhone 15 (128 GB) - Black");

        assertThat(result).isNotNull();
        assertThat(result.getPlatform()).isEqualTo(Platform.FLIPKART);
        assertThat(result.getTitle()).contains("Apple iPhone 15");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("65999.00"));
        assertThat(result.getUrl()).contains("flipkart.com/apple-iphone-15");
        assertThat(result.getImageUrl()).isEqualTo("https://rukminim2.flixcart.com/iphone.jpg");
    }

    @Test
    @DisplayName("When current platform is FLIPKART, searches AMAZON and extracts matching product")
    void findCounterpart_fromFlipkartToAmazon() {
        String amazonSearchHtml = """
                <html>
                <body>
                  <div data-component-type="s-search-result">
                    <h2><a class="a-link-normal" href="/Apple-iPhone-15-128-GB/dp/B0CHX1W1XY/ref=sr_1_1"><span>Apple iPhone 15 (128 GB)</span></a></h2>
                    <span class="a-price"><span class="a-offscreen">₹66,990.00</span></span>
                    <img class="s-image" src="https://m.media-amazon.com/iphone.jpg" />
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(amazonSearchHtml);

        ScrapedProduct result = searchService.findCounterpartProduct(Platform.FLIPKART, "Apple iPhone 15 128GB");

        assertThat(result).isNotNull();
        assertThat(result.getPlatform()).isEqualTo(Platform.AMAZON);
        assertThat(result.getTitle()).contains("Apple iPhone 15");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("66990.00"));
        assertThat(result.getUrl()).contains("amazon.in/dp/B0CHX1W1XY");
        assertThat(result.getImageUrl()).isEqualTo("https://m.media-amazon.com/iphone.jpg");
    }

    @Test
    @DisplayName("Returns null when no search results found on counterpart platform")
    void findCounterpart_returnsNullWhenNoResults() {
        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn("<html><body><div>No products found</div></body></html>");

        ScrapedProduct result = searchService.findCounterpartProduct(Platform.AMAZON, "Non-existent item xyz 999");

        assertThat(result).isNull();
    }
}
