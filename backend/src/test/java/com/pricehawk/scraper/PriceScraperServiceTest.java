package com.pricehawk.scraper;

import com.pricehawk.model.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceScraperServiceTest {

    @Mock
    private PriceParser amazonParser;

    @Mock
    private PriceParser flipkartParser;

    private PriceScraperService priceScraperService;

    @BeforeEach
    void setUp() {
        priceScraperService = new DefaultPriceScraperService(List.of(amazonParser, flipkartParser));
    }

    @Test
    @DisplayName("parseHtml routes Amazon platform to AmazonPriceParser")
    void parseHtml_routesToAmazonParser() {
        when(amazonParser.supports(Platform.AMAZON)).thenReturn(true);

        String html = "<html></html>";
        String url = "https://www.amazon.in/dp/B0000000";
        ScrapedProduct mockProduct = new ScrapedProduct(
                "Amazon Item", new BigDecimal("100.00"), "INR", "http://img.jpg", true, Platform.AMAZON);

        when(amazonParser.parse(html, url)).thenReturn(mockProduct);

        ScrapedProduct result = priceScraperService.parseHtml(Platform.AMAZON, html, url);

        assertThat(result).isEqualTo(mockProduct);
        verify(amazonParser).parse(html, url);
    }

    @Test
    @DisplayName("parseHtml routes Flipkart platform to FlipkartPriceParser")
    void parseHtml_routesToFlipkartParser() {
        when(amazonParser.supports(Platform.FLIPKART)).thenReturn(false);
        when(flipkartParser.supports(Platform.FLIPKART)).thenReturn(true);

        String html = "<html></html>";
        String url = "https://www.flipkart.com/item";
        ScrapedProduct mockProduct = new ScrapedProduct(
                "Flipkart Item", new BigDecimal("200.00"), "INR", "http://img.jpg", true, Platform.FLIPKART);

        when(flipkartParser.parse(html, url)).thenReturn(mockProduct);

        ScrapedProduct result = priceScraperService.parseHtml(Platform.FLIPKART, html, url);

        assertThat(result).isEqualTo(mockProduct);
        verify(flipkartParser).parse(html, url);
    }
}
