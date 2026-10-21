import { describe, it, expect, beforeEach } from 'vitest';
import { extractProductDetails, isProductPage } from '../content/extractor';

describe('Content Script Extractor', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
  });

  it('detects product page on Amazon and Flipkart URLs', () => {
    expect(isProductPage('https://www.amazon.in/Apple-iPhone-15-128-GB/dp/B0CHX1W1XY/ref=sr_1_1')).toBe(true);
    expect(isProductPage('https://www.flipkart.com/apple-iphone-15/p/itm12345?pid=MOBGTAG')).toBe(true);
    expect(isProductPage('https://www.amazon.in/s?k=iphone')).toBe(false);
    expect(isProductPage('https://www.flipkart.com/search?q=iphone')).toBe(false);
  });

  it('extracts product title, price, and image from Amazon DOM', () => {
    document.body.innerHTML = `
      <span id="productTitle">Apple iPhone 15 (128 GB)</span>
      <div id="corePriceDisplay_desktop_feature_div">
        <span class="a-price a-offscreen">₹69,999.00</span>
      </div>
      <img id="landingImage" src="https://m.media-amazon.com/iphone.jpg" />
    `;

    const product = extractProductDetails('https://www.amazon.in/dp/B0CHX1W1XY');

    expect(product).not.toBeNull();
    expect(product.title).toBe('Apple iPhone 15 (128 GB)');
    expect(product.currentPrice).toBe(69999.00);
    expect(product.imageUrl).toBe('https://m.media-amazon.com/iphone.jpg');
    expect(product.platform).toBe('AMAZON');
  });

  it('extracts product title, price, and image from Flipkart DOM', () => {
    document.body.innerHTML = `
      <span class="VU-ZEz">Apple MacBook Air M2</span>
      <div class="Nx9bqj CxhGGd">₹89,990</div>
      <img class="DByuf4" src="https://rukminim2.flixcart.com/macbook.jpg" />
    `;

    const product = extractProductDetails('https://www.flipkart.com/macbook/p/itm123');

    expect(product).not.toBeNull();
    expect(product.title).toBe('Apple MacBook Air M2');
    expect(product.currentPrice).toBe(89990.00);
    expect(product.imageUrl).toBe('https://rukminim2.flixcart.com/macbook.jpg');
    expect(product.platform).toBe('FLIPKART');
  });
});
