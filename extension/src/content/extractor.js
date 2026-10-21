/**
 * Utility functions for extracting product metadata from Amazon and Flipkart pages.
 */

export function isProductPage(url) {
  if (!url) return false;
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.toLowerCase();
    const path = parsed.pathname;

    if (host.includes('amazon.')) {
      return path.includes('/dp/') || path.includes('/gp/product/');
    }
    if (host.includes('flipkart.')) {
      return path.includes('/p/');
    }
  } catch {
    return false;
  }
  return false;
}

export function extractProductDetails(url) {
  const currentUrl = url || window.location.href;
  const isAmazon = currentUrl.includes('amazon.');
  const isFlipkart = currentUrl.includes('flipkart.');

  if (isAmazon) {
    return extractAmazonDetails(currentUrl);
  }
  if (isFlipkart) {
    return extractFlipkartDetails(currentUrl);
  }
  return null;
}

function parsePriceNumber(text) {
  if (!text) return null;
  const clean = text.replace(/[^0-9.]/g, '');
  const parsed = parseFloat(clean);
  return isNaN(parsed) ? null : parsed;
}

function extractAmazonDetails(url) {
  // Title
  const titleEl = document.querySelector('#productTitle') || document.querySelector('#title');
  const title = titleEl ? titleEl.textContent.trim() : document.title.replace(/ : Amazon.*$/i, '').trim();

  // Price
  const priceSelectors = [
    '#corePriceDisplay_desktop_feature_div .a-offscreen',
    '#corePriceDisplay_desktop_feature_div .a-price .a-offscreen',
    '.priceToPay .a-offscreen',
    '.a-price .a-offscreen',
    '.a-price.a-offscreen',
    '#priceblock_ourprice',
    '#priceblock_dealprice',
    '.a-price.aok-align-center .a-offscreen',
  ];

  let currentPrice = null;
  for (const selector of priceSelectors) {
    const el = document.querySelector(selector);
    if (el && el.textContent) {
      currentPrice = parsePriceNumber(el.textContent);
      if (currentPrice) break;
    }
  }

  // Fallback whole + fraction
  if (!currentPrice) {
    const whole = document.querySelector('.a-price-whole');
    if (whole) {
      const frac = document.querySelector('.a-price-fraction');
      const fracText = frac ? frac.textContent.trim() : '00';
      currentPrice = parsePriceNumber(`${whole.textContent}.${fracText}`);
    }
  }

  // Image
  const imgEl = document.querySelector('#landingImage') || document.querySelector('#imgBlkFront') || document.querySelector('#main-image');
  const imageUrl = imgEl ? (imgEl.getAttribute('src') || imgEl.getAttribute('data-old-hires')) : null;

  return {
    title: title || 'Amazon Product',
    currentPrice: currentPrice || 0,
    imageUrl: imageUrl || '',
    platform: 'AMAZON',
    url,
  };
}

function extractFlipkartDetails(url) {
  // Title
  const titleEl = document.querySelector('span.VU-ZEz') || document.querySelector('span.B_NuCI') || document.querySelector('h1');
  const title = titleEl ? titleEl.textContent.trim() : document.title.replace(/ : Flipkart.*$/i, '').trim();

  // Price
  const priceSelectors = [
    'div.Nx9bqj.CxhGGd',
    'div._30jeq3._16Jk6d',
    'div.Nx9bqj',
    'div._30jeq3',
    'div._16Jk6d',
  ];

  let currentPrice = null;
  for (const selector of priceSelectors) {
    const el = document.querySelector(selector);
    if (el && el.textContent) {
      currentPrice = parsePriceNumber(el.textContent);
      if (currentPrice) break;
    }
  }

  // Image
  const imgEl = document.querySelector('img.DByuf4') || document.querySelector('img._396cs4._2amPTt') || document.querySelector('img._396cs4');
  const imageUrl = imgEl ? (imgEl.getAttribute('src') || '') : '';

  return {
    title: title || 'Flipkart Product',
    currentPrice: currentPrice || 0,
    imageUrl: imageUrl || '',
    platform: 'FLIPKART',
    url,
  };
}
