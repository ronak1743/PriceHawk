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

  // Image - Support data-a-dynamic-image, data-old-hires, and multiple selectors
  const imgSelectors = [
    '#landingImage',
    '#imgBlkFront',
    '#main-image',
    '#imgTagWrapperId img',
    '#imageBlock img',
    '#main-image-container img',
    'img[data-a-dynamic-image]',
  ];

  let imageUrl = '';
  for (const sel of imgSelectors) {
    const el = document.querySelector(sel);
    if (!el) continue;

    // Check data-a-dynamic-image (JSON map containing full-res image URLs as keys)
    const dynamic = el.getAttribute('data-a-dynamic-image');
    if (dynamic) {
      try {
        const parsed = JSON.parse(dynamic);
        const keys = Object.keys(parsed);
        if (keys.length > 0 && keys[0].startsWith('http')) {
          imageUrl = keys[0];
          break;
        }
      } catch {}
    }

    // Check data-old-hires
    const hires = el.getAttribute('data-old-hires');
    if (hires && hires.startsWith('http')) {
      imageUrl = hires;
      break;
    }

    // Check src (ignore data: URIs or 1x1 transparent placeholders)
    const src = el.getAttribute('src') || el.src;
    if (src && src.startsWith('http') && !src.includes('transparent-pixel') && !src.includes('grey-pixel')) {
      imageUrl = src;
      break;
    }
  }

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
    'div.hl05eU div.Nx9bqj',
    'div._25b18c div.Nx9bqj',
    'div.Nx9bqj',
    'div._30jeq3',
    'div._16Jk6d',
    'div.hZ3P6w',
    'div[class*="Nx9bqj"]',
    'div[class*="30jeq3"]',
    'span[class*="Nx9bqj"]',
  ];

  let currentPrice = null;
  for (const selector of priceSelectors) {
    const el = document.querySelector(selector);
    if (el && el.textContent) {
      currentPrice = parsePriceNumber(el.textContent);
      if (currentPrice && currentPrice > 0) break;
    }
  }

  // Script tag fallback if dynamic client DOM hides price class
  if (!currentPrice) {
    try {
      const scripts = document.querySelectorAll('script');
      for (const s of scripts) {
        const text = s.textContent || '';
        if (text.includes('"finalPrice"') || text.includes('"fsp"') || text.includes('"specialPrice"')) {
          const m = text.match(/"(?:finalPrice|fsp|specialPrice)"\s*:\s*([0-9]+(?:\.[0-9]+)?)/);
          if (m && m[1]) {
            const val = parseFloat(m[1]);
            if (!isNaN(val) && val > 0) {
              currentPrice = val;
              break;
            }
          }
        }
      }
    } catch {}
  }

  // Image
  const imgSelectors = [
    'img.DByuf4',
    'img._396cs4._2amPTt',
    'img._396cs4',
    'img._53G40d',
    'img[src*="rukminim"]',
    'div._1kogOf img',
    'div._2rQ-NK img',
  ];

  let imageUrl = '';
  for (const sel of imgSelectors) {
    const el = document.querySelector(sel);
    if (!el) continue;
    const src = el.getAttribute('src') || el.src || el.getAttribute('data-src');
    if (src && src.startsWith('http')) {
      imageUrl = src;
      break;
    }
  }

  return {
    title: title || 'Flipkart Product',
    currentPrice: currentPrice || 0,
    imageUrl: imageUrl || '',
    platform: 'FLIPKART',
    url,
  };
}
