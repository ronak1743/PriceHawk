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
        assertThat(result.getUrl()).contains("dp/B0CHX1W1XY");
        assertThat(result.getImageUrl()).isEqualTo("https://m.media-amazon.com/iphone.jpg");
    }

    @Test
    @DisplayName("Returns null when no search results found on counterpart platform")
    void findCounterpart_returnsNullWhenNoResults() {
        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn("<html><body><div>No products found</div></body></html>");

        ScrapedProduct result = searchService.findCounterpartProduct(Platform.AMAZON, "Non-existent item xyz 999");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("When Amazon search contains sponsored ads without links, skips them and matches organic candidate")
    void findCounterpart_fromFlipkartToAmazon_skipsSponsoredAdsAndMatchesOrganicCandidate() {
        String multiCardHtml = """
                <html>
                <body>
                  <!-- Card 1: Sponsored banner without direct dp link -->
                  <div data-component-type="s-search-result">
                    <span class="puis-sponsored-label-text">Sponsored</span>
                    <h2><span>Dyazo Brand Banner</span></h2>
                    <span class="a-price"><span class="a-offscreen">₹349.00</span></span>
                  </div>
                  <!-- Card 2: Competitor sponsored card -->
                  <div data-component-type="s-search-result">
                    <span class="puis-sponsored-label-text">Sponsored</span>
                    <h2><a class="a-link-normal" href="/Portronics-Mouse/dp/B000PORT"><span>Portronics Wireless Mouse</span></a></h2>
                    <span class="a-price"><span class="a-offscreen">₹598.00</span></span>
                  </div>
                  <!-- Card 3: Organic exact Dyazo match -->
                  <div data-component-type="s-search-result">
                    <h2><a class="a-link-normal" href="/Dyazo-Wireless-Mouse/dp/B0F21TC3MB"><span>Dyazo Transparent Wireless Mouse 2.4GHz</span></a></h2>
                    <span class="a-price"><span class="a-offscreen">₹349.00</span></span>
                    <img class="s-image" src="https://m.media-amazon.com/dyazo.jpg" />
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(multiCardHtml);

        ScrapedProduct result = searchService.findCounterpartProduct(Platform.FLIPKART, "dyazo Wireless Optical Mouse 2.4GHz (Black)");

        assertThat(result).isNotNull();
        assertThat(result.getPlatform()).isEqualTo(Platform.AMAZON);
        assertThat(result.getTitle()).contains("Dyazo Transparent Wireless Mouse");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("349.00"));
        assertThat(result.getUrl()).contains("dp/B0F21TC3MB");
        assertThat(result.getImageUrl()).isEqualTo("https://m.media-amazon.com/dyazo.jpg");
    }

    @Test
    @DisplayName("Rejects candidate when brand belongs to a different company (strict brand safety)")
    void findCounterpart_rejectsDifferentBrandProduct() {
        String flipkartSearchHtml = """
                <html>
                <body>
                  <div class="tUxRFH">
                    <a class="CGtC5Q" href="/dell-inspiron-laptop/p/itm123">
                      <div class="KzDlHZ">Dell Inspiron 15 (16GB RAM, 512GB SSD)</div>
                      <div class="Nx9bqj">₹55,000</div>
                    </a>
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(flipkartSearchHtml);

        // Source is HP Victus, candidate is Dell Inspiron -> Should reject!
        ScrapedProduct result = searchService.findCounterpartProduct(Platform.AMAZON, "HP Victus Gaming Laptop 15 (16GB RAM, 512GB SSD)");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Rejects candidate when model generation conflicts (e.g. iPhone 15 vs iPhone 14)")
    void findCounterpart_rejectsConflictingModelGeneration() {
        String flipkartSearchHtml = """
                <html>
                <body>
                  <div class="tUxRFH">
                    <a class="CGtC5Q" href="/apple-iphone-14/p/itm14">
                      <div class="KzDlHZ">Apple iPhone 14 (Blue, 128 GB)</div>
                      <div class="Nx9bqj">₹58,999</div>
                    </a>
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(flipkartSearchHtml);

        // Source is iPhone 15, candidate is iPhone 14 -> Should reject!
        ScrapedProduct result = searchService.findCounterpartProduct(Platform.AMAZON, "Apple iPhone 15 (128 GB) - Blue");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Rejects candidate when key size/specification conflicts (e.g. 13.3-inch vs 15.6-inch)")
    void findCounterpart_rejectsConflictingSizeSpecification() {
        String flipkartSearchHtml = """
                <html>
                <body>
                  <div class="tUxRFH">
                    <a class="CGtC5Q" href="/amazon-basics-15-inch/p/itm15">
                      <div class="KzDlHZ">Amazon Basics 15.6-inch Laptop Sleeve Case</div>
                      <div class="Nx9bqj">₹499</div>
                    </a>
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(flipkartSearchHtml);

        // Source is 13.3-inch, candidate is 15.6-inch -> Should reject!
        ScrapedProduct result = searchService.findCounterpartProduct(Platform.AMAZON, "Amazon Basics 13.3-Inch Slim Profile Laptop Sleeve");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Matches watch when brand is in card header and alphanumeric model code matches")
    void findCounterpart_matchesWatchWithSeparateBrandHeaderAndAlphanumericModelCode() {
        String flipkartSearchHtml = """
                <html>
                <body>
                  <div class="slAVV4">
                    <div class="Fo1I0b">FOSSIL</div>
                    <a class="atJtCj" href="/fossil-ch2885-decker-mens-analog-watch-men/p/itmf3zh2fehuf7x7" title="DECKER - MENS Analog Watch - For Men CH2885">
                      DECKER - MENS Analog Watch - For Men CH2885
                    </a>
                    <div class="Nx9bqj">₹6,322</div>
                    <img class="DByuf4" src="https://rukminim2.flixcart.com/fossil.jpg" />
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(flipkartSearchHtml);

        ScrapedProduct result = searchService.findCounterpartProduct(Platform.AMAZON, "Fossil Decker Brown Watch Ch2885 - Chronograph, Men, Leather");

        assertThat(result).isNotNull();
        assertThat(result.getPlatform()).isEqualTo(Platform.FLIPKART);
        assertThat(result.getTitle()).contains("FOSSIL DECKER");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("6322.00"));
        assertThat(result.getUrl()).contains("fossil-ch2885-decker");
    }

    @Test
    @DisplayName("Rejects candidate when watch model code conflicts (e.g. CH2885 vs CH2601)")
    void findCounterpart_rejectsConflictingWatchModelCode() {
        String flipkartSearchHtml = """
                <html>
                <body>
                  <div class="slAVV4">
                    <div class="Fo1I0b">FOSSIL</div>
                    <a class="atJtCj" href="/fossil-decker-analog-watch-men/p/itmf3zgjydezrtcg" title="DECKER Analog Watch - For Men CH2601">
                      DECKER Analog Watch - For Men CH2601
                    </a>
                    <div class="Nx9bqj">₹11,993</div>
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(flipkartSearchHtml);

        // Source is CH2885, candidate is CH2601 -> Should reject!
        ScrapedProduct result = searchService.findCounterpartProduct(Platform.AMAZON, "Fossil Decker Brown Watch Ch2885 - Chronograph, Men, Leather");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Rejects candidate when candidate has different watch model CH2600IE and price differs significantly from source CH2882")
    void findCounterpart_rejectsDifferentWatchVariantWithDifferentModelAndPrice() {
        String flipkartSearchHtml = """
                <html>
                <body>
                  <div class="slAVV4">
                    <div class="Fo1I0b">FOSSIL</div>
                    <a class="atJtCj" href="/fossil-decker-analog-watch-men/p/itmf3zhfzzrk7sug" title="FOSSIL Decker Analog Watch - For Men CH2600IE">
                      FOSSIL Decker Analog Watch - For Men CH2600IE
                    </a>
                    <div class="Nx9bqj">₹12,495</div>
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(flipkartSearchHtml);

        // Source title is "Fossil Decker Brown Watch", but URL contains CH2882, price is 7314
        ScrapedProduct result = searchService.findCounterpartProduct(
                Platform.AMAZON,
                "Fossil Decker Brown Watch",
                "https://www.amazon.in/Fossil-Chronograph-Silver-Dial-Watch-CH2882/dp/B00DUCIK7U",
                new BigDecimal("7314.00")
        );

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Rejects candidate when color variant conflicts (e.g. Brown watch vs Silver watch)")
    void findCounterpart_rejectsColorVariantMismatch() {
        String flipkartSearchHtml = """
                <html>
                <body>
                  <div class="slAVV4">
                    <div class="Fo1I0b">FOSSIL</div>
                    <a class="atJtCj" href="/fossil-decker-analog-watch-men/p/itm123" title="FOSSIL Decker Silver Dial Men Watch">
                      FOSSIL Decker Silver Dial Men Watch
                    </a>
                    <div class="Nx9bqj">₹7,200</div>
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(flipkartSearchHtml);

        // Source specifies Brown, candidate specifies Silver -> Should reject!
        ScrapedProduct result = searchService.findCounterpartProduct(
                Platform.AMAZON,
                "Fossil Decker Brown Watch",
                null,
                new BigDecimal("7314.00")
        );

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Rejects candidate when URL contains echoed search parameters but actual product is different (e.g. Rhett BQ1009)")
    void findCounterpart_rejectsCandidateWithEchoedSearchParametersInUrl() {
        // Flipkart search returns Rhett watch with echoed search query in href:
        String flipkartSearchHtml = """
                <html>
                <body>
                  <div class="slAVV4">
                    <div class="Fo1I0b">FOSSIL</div>
                    <a class="atJtCj" href="/fossil-rhett-analog-watch-men/p/itmf3zhjhxxncqbq?pid=WATFFDFZHZAVGZHJ&q=fossil+decker+ch2885+brown" title="FOSSIL RHETT Analog Watch - For Men BQ1009,BQ1009I">
                      FOSSIL RHETT Analog Watch - For Men BQ1009,BQ1009I
                    </a>
                    <div class="Nx9bqj">₹6,279</div>
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(flipkartSearchHtml);

        // Source is Fossil Decker CH2885 -> Should reject Rhett BQ1009 even if ?q=fossil+decker+ch2885 is in the URL!
        ScrapedProduct result = searchService.findCounterpartProduct(
                Platform.AMAZON,
                "Fossil Decker Brown Watch Ch2885 - Chronograph, Men, Leather",
                "https://www.amazon.in/Fossil-Decker-Chronograph-Black-Watch-CH2885/dp/B00EVZ83J0",
                new BigDecimal("6322.00")
        );

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("When searching from Flipkart to Amazon, matches exact watch model CH2891")
    void findCounterpart_fromFlipkartToAmazon_matchesExactWatchModel() {
        String amazonSearchHtml = """
                <html>
                <body>
                  <div data-component-type="s-search-result">
                    <h2><span class="a-size-medium a-color-base">Fossil</span></h2>
                    <h2><a class="a-link-normal" href="/Fossil-Chronograph-Black-Dial-Watch-CH2891/dp/B00DUCIMCI/ref=sr_1_1"><span class="a-text-normal">Fossil Chronograph Black Dial Men's Watch-CH2891</span></a></h2>
                    <span class="a-price"><span class="a-offscreen">₹7,797</span></span>
                    <img class="s-image" src="https://m.media-amazon.com/ch2891.jpg" alt="Fossil Chronograph Black Dial Men's Watch-CH2891" />
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(amazonSearchHtml);

        ScrapedProduct result = searchService.findCounterpartProduct(
                Platform.FLIPKART,
                "FOSSIL Decker Analog Watch - For Men CH2891",
                "https://www.flipkart.com/fossil-decker-analog-watch-men/p/itm123",
                new BigDecimal("7797.00")
        );

        assertThat(result).isNotNull();
        assertThat(result.getPlatform()).isEqualTo(Platform.AMAZON);
        assertThat(result.getTitle()).contains("CH2891");
        assertThat(result.getUrl()).contains("CH2891");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("7797.00"));
    }

    @Test
    @DisplayName("When searching from Flipkart for CH2599, rejects Amazon results containing only other models like CH2885 or FS6092")
    void findCounterpart_fromFlipkartToAmazon_rejectsWhenAmazonOnlyHasOtherModels() {
        String amazonSearchHtml = """
                <html>
                <body>
                  <div data-component-type="s-search-result">
                    <h2><a class="a-link-normal" href="/Fossil-Blue-GMT-Silver-Watch-FS6092/dp/B0CX21M9TY"><span class="a-text-normal">Fossil Blue GMT Silver Watch FS6092</span></a></h2>
                    <span class="a-price"><span class="a-offscreen">₹9,597</span></span>
                  </div>
                  <div data-component-type="s-search-result">
                    <h2><a class="a-link-normal" href="/Fossil-Decker-Chronograph-Black-Watch-CH2885/dp/B00EVZ83J0"><span class="a-text-normal">Fossil Decker Chronograph Black Watch CH2885</span></a></h2>
                    <span class="a-price"><span class="a-offscreen">₹6,322</span></span>
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(amazonSearchHtml);

        // Source is CH2599, Amazon results are FS6092 and CH2885 -> Must reject!
        ScrapedProduct result = searchService.findCounterpartProduct(
                Platform.FLIPKART,
                "FOSSIL Decker Analog Watch - For Men CH2599",
                "https://www.flipkart.com/fossil-ch2599-decker-watch-men/p/itmf3zh3b8pztfcm",
                new BigDecimal("9993.00")
        );

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Rejects first search result when it is a different watch line (e.g. Rhett BQ1009 vs Decker)")
    void findCounterpart_rejectsFirstSearchResultWhenDifferentWatch() {
        String flipkartSearchHtml = """
                <html>
                <body>
                  <div class="slAVV4">
                    <div class="Fo1I0b">FOSSIL</div>
                    <a class="atJtCj" href="/fossil-rhett-analog-watch-men/p/itmf3zhjhxxncqbq" title="FOSSIL RHETT Analog Watch - For Men BQ1009,BQ1009I">
                      FOSSIL RHETT Analog Watch - For Men BQ1009,BQ1009I
                    </a>
                    <div class="Nx9bqj">₹6,279</div>
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(flipkartSearchHtml);

        // Source is Fossil Decker Brown Watch (even without specific model code in title)
        ScrapedProduct result = searchService.findCounterpartProduct(
                Platform.AMAZON,
                "Fossil Decker Brown Watch",
                "https://www.amazon.in/Fossil-Decker-Chronograph-Black-Watch-CH2885/dp/B00EVZ83J0",
                new BigDecimal("6322.00")
        );

        // Must reject Rhett BQ1009 because name 'Decker' does not match 'Rhett'!
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Skips first search results with different watch names and selects the matching product further down")
    void findCounterpart_skipsMismatchedFirstResultsAndMatchesCorrectWatch() {
        String flipkartSearchHtml = """
                <html>
                <body>
                  <div class="slAVV4">
                    <div class="Fo1I0b">FOSSIL</div>
                    <a class="atJtCj" href="/fossil-rhett-analog-watch-men/p/itm1" title="FOSSIL RHETT Analog Watch - For Men BQ1009">
                      FOSSIL RHETT Analog Watch - For Men BQ1009
                    </a>
                    <div class="Nx9bqj">₹6,279</div>
                  </div>
                  <div class="slAVV4">
                    <div class="Fo1I0b">FOSSIL</div>
                    <a class="atJtCj" href="/fossil-machine-chronograph-watch-men/p/itm2" title="FOSSIL Machine Chronograph Brown Watch">
                      FOSSIL Machine Chronograph Brown Watch
                    </a>
                    <div class="Nx9bqj">₹6,495</div>
                  </div>
                  <div class="slAVV4">
                    <div class="Fo1I0b">FOSSIL</div>
                    <a class="atJtCj" href="/fossil-decker-brown-watch-men/p/itm3" title="FOSSIL Decker Brown Watch - For Men">
                      FOSSIL Decker Brown Watch - For Men
                    </a>
                    <div class="Nx9bqj">₹6,322</div>
                    <img class="DByuf4" src="https://rukminim2.flixcart.com/decker.jpg" />
                  </div>
                </body>
                </html>
                """;

        when(restOperations.getForObject(anyString(), eq(String.class))).thenReturn(flipkartSearchHtml);

        // Source is Fossil Decker Brown Watch
        ScrapedProduct result = searchService.findCounterpartProduct(
                Platform.AMAZON,
                "Fossil Decker Brown Watch",
                null,
                new BigDecimal("6322.00")
        );

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).contains("Decker");
        assertThat(result.getUrl()).contains("fossil-decker-brown-watch");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("6322.00"));
    }
}

