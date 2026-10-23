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
  const trimmed = text.trim();
  if (!trimmed.includes('₹') && !trimmed.toLowerCase().includes('rs')) {
    return null;
  }
  const match = trimmed.match(/(?:₹|rs\.?)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)/i);
  if (!match || !match[1]) return null;
  const clean = match[1].replace(/,/g, '');
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
  let currentPrice = null;

  // 1. Primary Product Canonical Selling Price (fsp) from embedded state
  // fsp in ppd belongs strictly to the primary product on the page, avoiding ad carousels and recommendation cards
  try {
    const scripts = document.querySelectorAll('script');
    for (const s of scripts) {
      const text = s.textContent || '';
      if (text.includes('"ppd"') || text.includes('"fsp"') || text.includes('"primaryProductPrice"')) {
        const fspMatch = text.match(/"fsp"\s*:\s*([1-9][0-9]*(?:\.[0-9]+)?)/);
        if (fspMatch && fspMatch[1]) {
          const val = parseFloat(fspMatch[1]);
          if (!isNaN(val) && val > 0) {
            currentPrice = val;
            break;
          }
        }
        const prodMatch = text.match(/"(?:productPrice|primaryProductPrice)"\s*:\s*"?([1-9][0-9]*(?:\.[0-9]+)?)"?/);
        if (prodMatch && prodMatch[1]) {
          const val = parseFloat(prodMatch[1]);
          if (!isNaN(val) && val > 0) {
            currentPrice = val;
            break;
          }
        }
      }
    }
  } catch {}

  // 2. Standard Flipkart price selectors (excluding ad carousels and recommendation cards)
  if (!currentPrice) {
    const priceSelectors = [
      'div.Nx9bqj.CxhGGd',
      'div._30jeq3._16Jk6d',
      'div.v1zwn21n',
      'span.v1zwn21n',
      'div[font="default-fk-font-m"]',
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

    for (const selector of priceSelectors) {
      const els = document.querySelectorAll(selector);
      for (const el of els) {
        if (!el || !el.textContent) continue;
        // Check if inside recommendation card / another product link
        if (el.closest('a[href*="/p/"]') || el.closest('a[href*="pid="]') || el.closest('[data-tracking-id]') || el.closest('.recommendation-carousel')) {
          continue;
        }
        const style = el.getAttribute('style') || '';
        if (style.includes('line-through')) continue;
        const parsed = parsePriceNumber(el.textContent);
        if (parsed && parsed > 0) {
          currentPrice = parsed;
          break;
        }
      }
      if (currentPrice) break;
    }
  }

  // 3. Script tag fallback for finalPrice
  if (!currentPrice) {
    try {
      const scripts = document.querySelectorAll('script');
      for (const s of scripts) {
        const text = s.textContent || '';
        if (text.includes('"finalPrice"') || text.includes('"specialPrice"')) {
          const finalMatch = text.match(/"(?:finalPrice|specialPrice)"\s*:\s*([1-9][0-9]*(?:\.[0-9]+)?)/);
          if (finalMatch && finalMatch[1]) {
            const val = parseFloat(finalMatch[1]);
            if (!isNaN(val) && val > 0) {
              currentPrice = val;
              break;
            }
          }
        }
      }
    } catch {}
  }

  // 4. Standalone leaf element with ₹, strictly excluding recommendation/ad links and strike-through
  if (!currentPrice) {
    try {
      const candidates = document.querySelectorAll('div, span');
      for (const el of candidates) {
        if (el.children.length === 0) {
          if (el.closest('a[href*="/p/"]') || el.closest('a[href*="pid="]') || el.closest('[data-tracking-id]') || el.closest('.recommendation-carousel')) {
            continue;
          }
          const style = window.getComputedStyle ? window.getComputedStyle(el) : null;
          if (style && style.textDecorationLine && style.textDecorationLine.includes('line-through')) {
            continue;
          }
          const text = el.textContent?.trim() || '';
          if (text.startsWith('₹') && text.match(/^₹\s*[0-9,]+(?:\.[0-9]+)?$/)) {
            const val = parsePriceNumber(text);
            if (val && val > 0) {
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

  // Preserve pid parameter so exact variant/storage is tracked
  let cleanUrl = url;
  try {
    const u = new URL(url);
    const pid = u.searchParams.get('pid');
    cleanUrl = pid ? `${u.origin}${u.pathname}?pid=${pid}` : `${u.origin}${u.pathname}`;
  } catch {}

  return {
    title: title || 'Flipkart Product',
    currentPrice: currentPrice || 0,
    imageUrl: imageUrl || '',
    platform: 'FLIPKART',
    url: cleanUrl,
  };
}
