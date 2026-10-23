import React, { useState, useEffect, useCallback } from 'react';
import { getAuth, setAuth as saveAuth, clearAuth } from './services/storage';
import {
  login,
  register,
  getProducts,
  deleteProduct,
  getAlerts,
  markAlertRead,
  trackProduct,
  refreshProduct,
  addProductSource,
  isTokenExpired,
  setUnauthorizedHandler,
} from './services/api';
import PriceHistoryChart from './components/PriceHistoryChart';
import './App.css';

function formatAlertTimestamp(isoDate) {
  if (!isoDate) return '';
  const date = new Date(isoDate);
  if (isNaN(date.getTime())) return '';
  const now = new Date();
  const diffMs = now - date;
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffMins < 1) return 'Just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays === 1) return 'Yesterday';
  if (diffDays < 7) return `${diffDays}d ago`;
  return date.toLocaleDateString('en-IN', { month: 'short', day: 'numeric' });
}

function formatFullDateTime(isoDate) {
  if (!isoDate) return '';
  const date = new Date(isoDate);
  if (isNaN(date.getTime())) return '';
  return date.toLocaleString('en-IN', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function App() {
  const [auth, setAuth] = useState(null);
  const [mode, setMode] = useState('login'); // 'login' | 'register'
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const [dashboardTab, setDashboardTab] = useState('items'); // 'items' | 'alerts'
  const [products, setProducts] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [fetchingData, setFetchingData] = useState(false);
  const [refreshingId, setRefreshingId] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [matchingId, setMatchingId] = useState(null);
  const [linkingId, setLinkingId] = useState(null);
  const [manualUrl, setManualUrl] = useState('');
  const [expandedProductId, setExpandedProductId] = useState(null);
  const [actionSuccess, setActionSuccess] = useState(null);
  const [chartRefreshTrigger, setChartRefreshTrigger] = useState(0);
  const [trackingTab, setTrackingTab] = useState(false);
  const [markingReadId, setMarkingReadId] = useState(null);

  const handleSessionExpired = React.useCallback(async () => {
    await clearAuth();
    setAuth(null);
    setProducts([]);
    setAlerts([]);
    setMode('login');
    setError('Your session has expired. Please sign in again.');
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(handleSessionExpired);
    return () => setUnauthorizedHandler(null);
  }, [handleSessionExpired]);

  useEffect(() => {
    getAuth().then((stored) => {
      if (stored?.token) {
        if (isTokenExpired(stored.token)) {
          handleSessionExpired();
        } else {
          setAuth(stored);
          loadDashboardData(stored.token);
        }
      }
    });
  }, [handleSessionExpired]);

  const loadDashboardData = async (token) => {
    if (isTokenExpired(token)) {
      handleSessionExpired();
      return;
    }
    setFetchingData(true);
    try {
      const [fetchedProducts, fetchedAlerts] = await Promise.all([
        getProducts(token).catch((err) => {
          if (err.message && err.message.includes('session has expired')) throw err;
          return [];
        }),
        getAlerts(token).catch((err) => {
          if (err.message && err.message.includes('session has expired')) throw err;
          return [];
        }),
      ]);
      setProducts(fetchedProducts || []);
      setAlerts(fetchedAlerts || []);
    } catch (err) {
      if (err.message && err.message.includes('session has expired')) {
        handleSessionExpired();
      } else {
        console.error('Error loading dashboard data', err);
      }
    } finally {
      setFetchingData(false);
    }
  };

  const handleTabChange = (newMode) => {
    setMode(newMode);
    setError(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    // Client-side validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!email || !emailRegex.test(email.trim())) {
      setError('Please enter a valid email address');
      return;
    }

    if (mode === 'register' && (!username || username.trim().length < 3)) {
      setError('Username must be at least 3 characters');
      return;
    }

    if (!password || password.length < 6) {
      setError('Password must be at least 6 characters');
      return;
    }

    setLoading(true);

    try {
      if (mode === 'register') {
        await register(username, email, password);
        const loginRes = await login(email, password);
        const authData = {
          token: loginRes.token,
          user: { id: loginRes.id, username: loginRes.username, email: loginRes.email },
        };
        await saveAuth(authData);
        setAuth(authData);
        loadDashboardData(authData.token);
      } else {
        const loginRes = await login(email, password);
        const authData = {
          token: loginRes.token,
          user: { id: loginRes.id, username: loginRes.username, email: loginRes.email },
        };
        await saveAuth(authData);
        setAuth(authData);
        loadDashboardData(authData.token);
      }
    } catch (err) {
      setError(err.message || 'Authentication failed');
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    await clearAuth();
    setAuth(null);
    setUsername('');
    setEmail('');
    setPassword('');
    setError(null);
    setProducts([]);
    setAlerts([]);
  };

  const handleDeleteProduct = async (productId) => {
    if (!auth?.token || deletingId) return;
    setDeletingId(productId);
    try {
      await deleteProduct(auth.token, productId);
      setProducts((prev) => prev.filter((p) => p.id !== productId));
      setActionSuccess('Item removed from tracking');
      setTimeout(() => setActionSuccess(null), 3000);
    } catch (err) {
      setError(err.message || 'Failed to delete item');
    } finally {
      setDeletingId(null);
    }
  };

  const handleRefreshProduct = async (productId) => {
    if (!auth?.token || refreshingId) return;
    setRefreshingId(productId);
    setError(null);
    try {
      const updated = await refreshProduct(auth.token, productId);
      setProducts((prev) => prev.map((p) => (p.id === productId ? updated : p)));
      setChartRefreshTrigger(Date.now());
      setActionSuccess('Price refreshed successfully!');
      setTimeout(() => setActionSuccess(null), 3000);
    } catch (err) {
      setError(err.message || 'Failed to refresh price');
    } finally {
      setRefreshingId(null);
    }
  };

  const handleAutoMatch = async (productId) => {
    if (!auth?.token || matchingId) return;
    setMatchingId(productId);
    setError(null);
    try {
      const current = products.find((p) => p.id === productId);
      const currentSource = current?.sources?.[0];
      const isFromAmazon = currentSource?.platform === 'AMAZON';
      const targetPlatformName = isFromAmazon ? 'Flipkart' : 'Amazon';
      const targetDomain = isFromAmazon ? 'flipkart.com' : 'amazon.in';

      // 1. Smart Tab Discovery: Check if user already has the counterpart page open in Chrome
      let matchingTabUrl = null;
      if (typeof chrome !== 'undefined' && chrome.tabs?.query && current) {
        try {
          const tabs = await chrome.tabs.query({});
          const combinedSource = ((current.title || '') + ' ' + (currentSource?.url || '')).toLowerCase();

          // Look for model code (e.g. CH2885, CH2599, FS4813, 15-fa0666TX)
          const modelMatch = combinedSource.match(/\b([a-z]{1,4}\d{3,5}[a-z]{0,3}|\d{1,2}-[a-z0-9]{3,8})\b/i);
          const modelCode = modelMatch ? modelMatch[1].toLowerCase() : null;

          const brandMatch = combinedSource.match(/\b(apple|samsung|hp|dell|lenovo|asus|acer|msi|oneplus|boat|noise|fossil|casio|titan|timex|sony)\b/i);
          const brand = brandMatch ? brandMatch[1].toLowerCase() : null;

          const KNOWN_SERIES_LIST = [
            'decker', 'rhett', 'grant', 'nate', 'neutra', 'townsman', 'machine', 'flynn',
            'kerrigan', 'stella', 'bannon', 'minimalist', 'tourer', 'heritage', 'dean',
            'riley', 'jacqueline', 'edifice', 'enticer', 'karishma', 'raga', 'octane',
            'victus', 'pavilion', 'omen', 'thinkpad', 'ideapad', 'loq', 'legion', 'vivobook',
            'zenbook', 'tuf', 'rog', 'predator', 'nitro', 'inspiron', 'vostro', 'macbook',
            'iphone', 'galaxy', 'airdopes', 'airpods'
          ];
          const seriesMatch = combinedSource.match(new RegExp('\\b(' + KNOWN_SERIES_LIST.join('|') + ')\\b', 'i'));
          const series = seriesMatch ? seriesMatch[1].toLowerCase() : null;

          for (const t of tabs) {
            const tUrl = (t.url || '').toLowerCase();
            const tTitle = (t.title || '').toLowerCase();
            if (!tUrl.includes(targetDomain) && !tTitle.includes(targetDomain)) continue;

            const isProduct = isFromAmazon
              ? (tUrl.includes('/p/') || tUrl.includes('/itm'))
              : (tUrl.includes('/dp/') || tUrl.includes('/gp/product'));
            if (!isProduct) continue;

            const tFull = tUrl + ' ' + tTitle;

            // Strict series conflict check: If source has a series (e.g. decker),
            // tab must NEVER belong to a conflicting series (e.g. rhett, grant, machine)!
            if (series) {
              const conflictingSeries = KNOWN_SERIES_LIST.find((s) => s !== series && tFull.includes(s));
              if (conflictingSeries) {
                continue;
              }
            }

            if (modelCode && tFull.includes(modelCode)) {
              matchingTabUrl = t.url;
              break;
            } else if (brand && series && tFull.includes(brand) && tFull.includes(series)) {
              matchingTabUrl = t.url;
              break;
            }
          }
        } catch (tabErr) {
          console.warn('Tab discovery skipped', tabErr);
        }
      }

      let updated;
      if (matchingTabUrl) {
        // Instantly link the counterpart from user's open tab
        updated = await addProductSource(auth.token, productId, { url: matchingTabUrl, autoDiscover: false });
      } else {
        // Fallback to automated catalog search
        updated = await addProductSource(auth.token, productId, { autoDiscover: true });
      }

      const isNewSourceAdded = updated.sources && updated.sources.length > (current?.sources?.length || 0);

      setProducts((prev) => prev.map((p) => (p.id === productId ? updated : p)));
      setChartRefreshTrigger(Date.now());

      if (isNewSourceAdded) {
        setActionSuccess(`Counterpart product auto-matched & linked on ${targetPlatformName}!`);
        setTimeout(() => setActionSuccess(null), 3000);
      } else {
        setError(`No counterpart found on ${targetPlatformName}. Try pasting the link directly with "+ Paste URL".`);
      }
    } catch (err) {
      setError(err.message || 'Could not auto-match counterpart on other platform');
    } finally {
      setMatchingId(null);
    }
  };

  const handleManualLink = async (e, productId) => {
    e.preventDefault();
    if (!auth?.token || !manualUrl.trim() || matchingId) return;
    setMatchingId(productId);
    setError(null);
    try {
      const updated = await addProductSource(auth.token, productId, { url: manualUrl.trim(), autoDiscover: false });
      setProducts((prev) => prev.map((p) => (p.id === productId ? updated : p)));
      setChartRefreshTrigger(Date.now());
      setLinkingId(null);
      setManualUrl('');
      setActionSuccess('Counterpart URL linked successfully!');
      setTimeout(() => setActionSuccess(null), 3000);
    } catch (err) {
      setError(err.message || 'Could not link counterpart URL');
    } finally {
      setMatchingId(null);
    }
  };

  const handleMarkAlertRead = async (alertId) => {
    if (!auth?.token || markingReadId) return;
    setMarkingReadId(alertId);
    try {
      await markAlertRead(auth.token, alertId);
      setAlerts((prev) =>
        prev.map((a) => (a.id === alertId ? { ...a, isRead: true, readAt: new Date().toISOString() } : a))
      );
    } catch (err) {
      setError(err.message || 'Failed to mark alert as read');
    } finally {
      setMarkingReadId(null);
    }
  };

  const handleTrackCurrentTab = async () => {
    if (!auth?.token || trackingTab) return;
    setTrackingTab(true);
    setError(null);
    try {
      if (typeof chrome !== 'undefined' && chrome.tabs?.query) {
        const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
        if (!tab?.url) {
          setError('No active tab found.');
          return;
        }

        const isAmazon = tab.url.includes('amazon.');
        const isFlipkart = tab.url.includes('flipkart.');
        if (!isAmazon && !isFlipkart) {
          setError('Please open an Amazon or Flipkart product page to track.');
          return;
        }

        // 1. Try communicating with content script
        let details = null;
        try {
          const resp = await chrome.tabs.sendMessage(tab.id, { type: 'PRICEHAWK_GET_DETAILS' });
          if (resp?.details) {
            details = resp.details;
          }
        } catch (msgErr) {
          // Content script may not be loaded yet or tab was open before extension installed
        }

        // 2. Fallback: Extract directly via chrome.scripting.executeScript
        if ((!details || !details.currentPrice) && chrome.scripting?.executeScript) {
          try {
            const results = await chrome.scripting.executeScript({
              target: { tabId: tab.id },
              func: () => {
                const url = window.location.href;
                const isAmz = url.includes('amazon.');
                let title = document.title.replace(/ : (Amazon|Flipkart).*$/i, '').trim();
                let price = null;
                let imageUrl = '';

                if (isAmz) {
                  const tEl = document.querySelector('#productTitle') || document.querySelector('#title');
                  if (tEl) title = tEl.textContent.trim();

                  const pSelectors = [
                    '#corePriceDisplay_desktop_feature_div .a-offscreen',
                    '#corePriceDisplay_desktop_feature_div .a-price .a-offscreen',
                    '.priceToPay .a-offscreen',
                    '.a-price .a-offscreen',
                    '.a-price.a-offscreen',
                    '#priceblock_ourprice',
                    '#priceblock_dealprice',
                    '.a-price.aok-align-center .a-offscreen',
                  ];
                  for (const s of pSelectors) {
                    const el = document.querySelector(s);
                    if (el && el.textContent) {
                      const clean = el.textContent.replace(/[^0-9.]/g, '');
                      const val = parseFloat(clean);
                      if (!isNaN(val)) { price = val; break; }
                    }
                  }

                  const imgSelectors = ['#landingImage', '#imgBlkFront', '#main-image', '#imgTagWrapperId img', '#imageBlock img'];
                  for (const s of imgSelectors) {
                    const el = document.querySelector(s);
                    if (!el) continue;
                    const dyn = el.getAttribute('data-a-dynamic-image');
                    if (dyn) {
                      try {
                        const keys = Object.keys(JSON.parse(dyn));
                        if (keys.length > 0 && keys[0].startsWith('http')) { imageUrl = keys[0]; break; }
                      } catch { }
                    }
                    const hires = el.getAttribute('data-old-hires');
                    if (hires && hires.startsWith('http')) { imageUrl = hires; break; }
                    const src = el.getAttribute('src') || el.src;
                    if (src && src.startsWith('http') && !src.includes('transparent-pixel')) { imageUrl = src; break; }
                  }
                } else {
                  const tEl = document.querySelector('span.VU-ZEz') || document.querySelector('span.B_NuCI') || document.querySelector('h1');
                  if (tEl) title = tEl.textContent.trim();

                  // 1. Primary Product Canonical Selling Price (fsp) from embedded state
                  try {
                    const scripts = document.querySelectorAll('script');
                    for (const s of scripts) {
                      const text = s.textContent || '';
                      if (text.includes('"ppd"') || text.includes('"fsp"') || text.includes('"primaryProductPrice"')) {
                        const fspMatch = text.match(/"fsp"\s*:\s*([1-9][0-9]*(?:\.[0-9]+)?)/);
                        if (fspMatch && fspMatch[1]) {
                          const val = parseFloat(fspMatch[1]);
                          if (!isNaN(val) && val > 0) { price = val; break; }
                        }
                        const prodMatch = text.match(/"(?:productPrice|primaryProductPrice)"\s*:\s*"?([1-9][0-9]*(?:\.[0-9]+)?)"?/);
                        if (prodMatch && prodMatch[1]) {
                          const val = parseFloat(prodMatch[1]);
                          if (!isNaN(val) && val > 0) { price = val; break; }
                        }
                      }
                    }
                  } catch { }

                  // 2. Standard Flipkart price selectors (excluding ad carousels and recommendation cards)
                  if (!price) {
                    const pSelectors = [
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
                    for (const s of pSelectors) {
                      const els = document.querySelectorAll(s);
                      for (const el of els) {
                        if (!el || !el.textContent) continue;
                        if (el.closest('a[href*="/p/"]') || el.closest('a[href*="pid="]') || el.closest('[data-tracking-id]') || el.closest('.recommendation-carousel')) {
                          continue;
                        }
                        const style = el.getAttribute('style') || '';
                        if (style.includes('line-through')) continue;
                        const text = el.textContent.trim();
                        if (!text.includes('₹') && !text.toLowerCase().includes('rs')) continue;
                        const m = text.match(/(?:₹|rs\.?)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)/i);
                        if (!m || !m[1]) continue;
                        const val = parseFloat(m[1].replace(/,/g, ''));
                        if (!isNaN(val) && val > 0) { price = val; break; }
                      }
                      if (price) break;
                    }
                  }

                  // 3. Fallback to finalPrice
                  if (!price) {
                    try {
                      const scripts = document.querySelectorAll('script');
                      for (const s of scripts) {
                        const text = s.textContent || '';
                        if (text.includes('"finalPrice"') || text.includes('"specialPrice"')) {
                          const finalMatch = text.match(/"(?:finalPrice|specialPrice)"\s*:\s*([1-9][0-9]*(?:\.[0-9]+)?)/);
                          if (finalMatch && finalMatch[1]) {
                            const val = parseFloat(finalMatch[1]);
                            if (!isNaN(val) && val > 0) { price = val; break; }
                          }
                        }
                      }
                    } catch { }
                  }

                  // 4. Standalone leaf element with ₹, strictly excluding recommendation/ad links and strike-through
                  if (!price) {
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
                          if (text.startsWith('₹') || text.toLowerCase().startsWith('rs')) {
                            const m = text.match(/(?:₹|rs\.?)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)/i);
                            if (m && m[1]) {
                              const val = parseFloat(m[1].replace(/,/g, ''));
                              if (!isNaN(val) && val > 0) { price = val; break; }
                            }
                          }
                        }
                      }
                    } catch {}
                  }

                  const imgEl = document.querySelector('img.DByuf4, img._396cs4._2amPTt, img._396cs4, img[src*="rukminim"]');
                  if (imgEl) {
                    const src = imgEl.getAttribute('src') || imgEl.src;
                    if (src && src.startsWith('http')) imageUrl = src;
                  }
                }

                return { title, currentPrice: price, imageUrl };
              },
            });

            if (results?.[0]?.result) {
              details = results[0].result;
            }
          } catch (scriptErr) {
            console.warn('Script execution fallback failed', scriptErr);
          }
        }

        const title = details?.title || tab.title?.replace(/ : (Amazon|Flipkart).*$/i, '') || 'Tracked Product';
        const currentPrice = (typeof details?.currentPrice === 'number' && !isNaN(details.currentPrice) && details.currentPrice > 0)
          ? details.currentPrice
          : 1.0;
        const imageUrl = details?.imageUrl || '';

        let trackUrl = tab.url;
        if (trackUrl && trackUrl.includes('flipkart.')) {
          try {
            const u = new URL(trackUrl);
            const pid = u.searchParams.get('pid');
            trackUrl = pid ? `${u.origin}${u.pathname}?pid=${pid}` : `${u.origin}${u.pathname}`;
          } catch {}
        }

        const item = await trackProduct(auth.token, {
          url: trackUrl,
          title,
          currentPrice,
          imageUrl,
        });
        setProducts((prev) => [item, ...prev]);
        setActionSuccess('Product tracked successfully!');
        setTimeout(() => setActionSuccess(null), 3000);
      }
    } catch (err) {
      setError(err.message || 'Failed to track current page');
    } finally {
      setTrackingTab(false);
    }
  };

  // Filter alerts: automatically remove read alerts older than 2 days (48 hours)
  const activeAlerts = alerts.filter((alert) => {
    if (!alert.isRead) return true;
    const readTimestamp = alert.readAt ? new Date(alert.readAt).getTime() : (alert.createdAt ? new Date(alert.createdAt).getTime() : 0);
    const twoDaysMs = 2 * 24 * 60 * 60 * 1000;
    return Date.now() - readTimestamp < twoDaysMs;
  });

  const unreadAlertsCount = activeAlerts.filter((a) => !a.isRead).length;

  return (
    <div className="popup-container">
      <header className="popup-header">
        <div className="brand-row">
          <div className="brand-left">
            <div className="logo-badge" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
                <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
                <line x1="12" y1="22.08" x2="12" y2="12"></line>
              </svg>
            </div>
            <div className="brand-text-col">
              <div className="brand-title-row">
                <h1 className="brand-title">PriceHawk</h1>
                <span className="version-pill">v1.0 Pro</span>
              </div>
              <p className="brand-subtitle">
                <span className="brand-subtitle-dot" aria-hidden="true"></span>
                Amazon &amp; Flipkart Tracker
              </p>
            </div>
          </div>

          {auth?.user ? (
            <div className="header-user-side">
              <div className="user-bar-left">
                <div className="user-avatar-small">
                  {(auth.user.username || auth.user.email || 'U').charAt(0).toUpperCase()}
                </div>
                <div className="user-bar-names">
                  <span className="user-greeting">Welcome, {auth.user.username || auth.user.email}</span>
                  <span className="user-email-badge">{auth.user.email}</span>
                </div>
              </div>
              <button className="logout-icon-btn" onClick={handleLogout} title="Sign Out">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
                  <polyline points="16 17 21 12 16 7"></polyline>
                  <line x1="21" y1="12" x2="9" y2="12"></line>
                </svg>
                <span>Sign Out</span>
              </button>
            </div>
          ) : (
            <span className="version-pill">v1.0 Pro</span>
          )}
        </div>
      </header>

      {auth?.user ? (
        <div className="dashboard-container">

          {/* Quick Track Bar */}
          <div className="quick-track-container">
            <button
              className="quick-track-btn"
              onClick={handleTrackCurrentTab}
              disabled={trackingTab}
            >
              {trackingTab ? (
                <>
                  <span className="btn-spinner" aria-hidden="true"></span>
                  <span>Tracking Product...</span>
                </>
              ) : (
                <>
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                    <line x1="12" y1="5" x2="12" y2="19"></line>
                    <line x1="5" y1="12" x2="19" y2="12"></line>
                  </svg>
                  <span>Track Current Tab Product</span>
                </>
              )}
            </button>
          </div>

          {/* Feedback messages */}
          {actionSuccess && <div className="success-alert">{actionSuccess}</div>}
          {error && <div className="error-alert">{error}</div>}

          {/* Section Navigation Tabs */}
          <div className="dashboard-nav-tabs">
            <button
              className={`dash-tab-btn ${dashboardTab === 'items' ? 'active' : ''}`}
              onClick={() => setDashboardTab('items')}
            >
              <span>Tracked Items</span>
              <span className="tab-count-badge">{products.length}</span>
            </button>
            <button
              className={`dash-tab-btn ${dashboardTab === 'alerts' ? 'active' : ''}`}
              onClick={() => setDashboardTab('alerts')}
            >
              <span>Alerts</span>
              {unreadAlertsCount > 0 ? (
                <span className="tab-count-badge unread">{unreadAlertsCount}</span>
              ) : (
                <span className="tab-count-badge zero">0</span>
              )}
            </button>
          </div>

          {/* Tab Content: Tracked Items */}
          {dashboardTab === 'items' && (
            <div className="items-tab-content">
              {fetchingData ? (
                <div className="loading-state">
                  <span className="spinner"></span>
                  <span>Fetching tracked products...</span>
                </div>
              ) : products.length === 0 ? (
                <div className="empty-state">
                  <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="12" cy="12" r="10"></circle>
                    <path d="m10 15 5-3-5-3v6z"></path>
                  </svg>
                  <p className="empty-title">No products tracked yet</p>
                  <p className="empty-subtitle">Navigate to any product on Amazon or Flipkart and click "Track Current Tab".</p>
                </div>
              ) : (
                <div className="products-list">
                  {products.map((item) => {
                    const sources = item.sources || [];
                    const primarySource = sources[0] || {};
                    const hasMultipleSources = sources.length > 1;

                    // Identify counterpart platform if only 1 source exists
                    const existingPlatforms = new Set(sources.map((s) => s.platform));
                    const counterpartPlatform = existingPlatforms.has('AMAZON') ? 'FLIPKART' : 'AMAZON';
                    const counterpartName = counterpartPlatform === 'FLIPKART' ? 'Flipkart' : 'Amazon';

                    // Compute best deal across platforms
                    let bestDeal = null;
                    if (hasMultipleSources) {
                      const validSources = sources.filter((s) => s.currentPrice && Number(s.currentPrice) > 0);
                      if (validSources.length > 1) {
                        const sorted = [...validSources].sort((a, b) => Number(a.currentPrice) - Number(b.currentPrice));
                        const lowest = sorted[0];
                        const highest = sorted[sorted.length - 1];
                        const diff = Number(highest.currentPrice) - Number(lowest.currentPrice);
                        if (diff > 0) {
                          bestDeal = {
                            platform: lowest.platform,
                            savings: diff,
                            url: lowest.url,
                          };
                        }
                      }
                    }

                    return (
                      <div key={item.id} className="product-card">
                        <div className="card-thumb-container">
                          {primarySource.imageUrl ? (
                            <img src={primarySource.imageUrl} alt={item.title} className="product-thumbnail" />
                          ) : (
                            <div className="thumbnail-placeholder">📦</div>
                          )}
                        </div>
                        <div className="card-details">
                          <div className="card-top-row">
                            <div className="platform-badges-group">
                              {sources.map((s) => (
                                <span key={s.id || s.platform} className={`platform-pill ${s.platform?.toLowerCase() || 'other'}`}>
                                  {s.platform || 'OTHER'}
                                </span>
                              ))}
                            </div>
                            <div className="card-actions-group">
                              <button
                                title={expandedProductId === item.id ? "Hide price history" : "View price history chart & analytics"}
                                className={`action-icon-btn chart-toggle-btn ${expandedProductId === item.id ? 'active' : ''}`}
                                onClick={() => setExpandedProductId((prev) => (prev === item.id ? null : item.id))}
                              >
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                                  <line x1="18" y1="20" x2="18" y2="10"></line>
                                  <line x1="12" y1="20" x2="12" y2="4"></line>
                                  <line x1="6" y1="20" x2="6" y2="14"></line>
                                </svg>
                              </button>
                              <button
                                title="Refresh live price"
                                className={`action-icon-btn refresh-btn ${refreshingId === item.id ? 'spinning' : ''}`}
                                onClick={() => handleRefreshProduct(item.id)}
                                disabled={refreshingId === item.id || deletingId === item.id}
                              >
                                {refreshingId === item.id ? (
                                  <span className="btn-spinner small primary" aria-hidden="true"></span>
                                ) : (
                                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                                    <polyline points="23 4 23 10 17 10"></polyline>
                                    <polyline points="1 20 1 14 7 14"></polyline>
                                    <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path>
                                  </svg>
                                )}
                              </button>
                              <button
                                title="Delete item"
                                className={`action-icon-btn delete-btn ${deletingId === item.id ? 'deleting' : ''}`}
                                onClick={() => handleDeleteProduct(item.id)}
                                disabled={deletingId === item.id || refreshingId === item.id}
                              >
                                {deletingId === item.id ? (
                                  <span className="btn-spinner small danger" aria-hidden="true"></span>
                                ) : (
                                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                    <polyline points="3 6 5 6 21 6"></polyline>
                                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                                  </svg>
                                )}
                              </button>
                            </div>
                          </div>
                          <h3 className="card-title" title={item.title}>{item.title}</h3>

                          {/* Best Deal Highlight Banner */}
                          {bestDeal && (
                            <div className="best-deal-banner">
                              <span className="trophy-icon">🏆</span>
                              <span>Best Deal on <strong>{bestDeal.platform}</strong> — Save ₹{bestDeal.savings.toLocaleString('en-IN')}!</span>
                            </div>
                          )}

                          {/* Cross-Platform Comparison or Single Price */}
                          {hasMultipleSources ? (
                            <div className="cross-platform-sources-row">
                              {sources.map((s) => {
                                const isLowest = bestDeal && s.platform === bestDeal.platform;
                                return (
                                  <a
                                    key={s.id || s.platform}
                                    href={s.url}
                                    target="_blank"
                                    rel="noreferrer"
                                    className={`source-chip ${s.platform?.toLowerCase()} ${isLowest ? 'is-best' : ''}`}
                                    title={`Open product page on ${s.platform}`}
                                  >
                                    <span className="chip-plat">{s.platform}</span>
                                    <span className="chip-price">₹{s.currentPrice ? Number(s.currentPrice).toLocaleString('en-IN') : '—'}</span>
                                    {isLowest && <span className="chip-best-pill">BEST</span>}
                                    <span className="chip-arrow">↗</span>
                                  </a>
                                );
                              })}
                            </div>
                          ) : (
                            <div className="card-price-row">
                              <span className="current-price">
                                ₹{primarySource.currentPrice ? Number(primarySource.currentPrice).toLocaleString('en-IN') : '—'}
                              </span>
                              {item.targetPrice && (
                                <span className="target-price-label">
                                  Target: ₹{Number(item.targetPrice).toLocaleString('en-IN')}
                                </span>
                              )}
                            </div>
                          )}

                          {item.targetPrice && hasMultipleSources && (
                            <div className="card-target-row">
                              <span className="target-price-label">
                                🎯 Target Alert: ₹{Number(item.targetPrice).toLocaleString('en-IN')} (Any store)
                              </span>
                            </div>
                          )}

                          {/* Counterpart Auto-Match Action Bar (shown when only 1 store is linked) */}
                          {!hasMultipleSources && (
                            <div className="counterpart-container">
                              <div className="counterpart-actions-row">
                                <button
                                  type="button"
                                  className="auto-match-btn"
                                  disabled={matchingId === item.id}
                                  onClick={() => handleAutoMatch(item.id)}
                                  title={`Auto-discover and link counterpart on ${counterpartName}`}
                                >
                                  {matchingId === item.id ? (
                                    <>
                                      <span className="mini-spinner"></span>
                                      <span>Finding on {counterpartName}...</span>
                                    </>
                                  ) : (
                                    <>
                                      <span className="zap-icon">⚡</span>
                                      <span>Auto-Match on {counterpartName}</span>
                                    </>
                                  )}
                                </button>
                                <button
                                  type="button"
                                  className="manual-link-toggle-btn"
                                  onClick={() => {
                                    setLinkingId((prev) => (prev === item.id ? null : item.id));
                                    setManualUrl('');
                                  }}
                                  title="Paste counterpart URL manually"
                                >
                                  {linkingId === item.id ? '✕' : '+ Paste URL'}
                                </button>
                              </div>

                              {linkingId === item.id && (
                                <form className="manual-link-form" onSubmit={(e) => handleManualLink(e, item.id)}>
                                  <input
                                    type="url"
                                    className="manual-link-input"
                                    placeholder={`Paste ${counterpartName} URL...`}
                                    value={manualUrl}
                                    onChange={(e) => setManualUrl(e.target.value)}
                                    required
                                  />
                                  <button type="submit" className="manual-link-submit-btn" disabled={matchingId === item.id}>
                                    {matchingId === item.id ? (
                                      <>
                                        <span className="btn-spinner mini" aria-hidden="true"></span>
                                        <span>Linking...</span>
                                      </>
                                    ) : (
                                      'Link'
                                    )}
                                  </button>
                                </form>
                              )}
                            </div>
                          )}
                        </div>
                        {expandedProductId === item.id && (
                          <PriceHistoryChart
                            token={auth.token}
                            product={item}
                            refreshTrigger={chartRefreshTrigger}
                            onTargetPriceUpdated={(updated) => {
                              setProducts((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
                              setChartRefreshTrigger(Date.now());
                            }}
                          />
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* Tab Content: Alerts */}
          {dashboardTab === 'alerts' && (
            <div className="alerts-tab-content">
              {activeAlerts.length === 0 ? (
                <div className="empty-state">
                  <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                    <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                  </svg>
                  <p className="empty-title">No price drop alerts yet</p>
                  <p className="empty-subtitle">When prices on your tracked items drop, you'll see alerts here and via Brevo email.</p>
                </div>
              ) : (
                <div className="alerts-list">
                  {activeAlerts.map((alert) => (
                    <div key={alert.id} className={`alert-card ${alert.isRead ? 'read' : 'unread'}`}>
                      <div className="alert-card-header">
                        <div className="alert-header-left">
                          <span className="alert-drop-badge">🚨 Price Drop</span>
                          {alert.createdAt && (
                            <span
                              className="alert-timestamp"
                              title={formatFullDateTime(alert.createdAt)}
                            >
                              🕒 {formatAlertTimestamp(alert.createdAt)}
                            </span>
                          )}
                        </div>
                        {!alert.isRead ? (
                          <button
                            className="mark-read-btn"
                            onClick={() => handleMarkAlertRead(alert.id)}
                            disabled={markingReadId === alert.id}
                            title="Mark as read (will be auto-removed after 2 days)"
                          >
                            {markingReadId === alert.id ? (
                              <>
                                <span className="btn-spinner mini" aria-hidden="true"></span>
                                <span>Marking...</span>
                              </>
                            ) : (
                              'Mark as Read'
                            )}
                          </button>
                        ) : (
                          <span
                            className="alert-read-pill"
                            title="Read alerts are automatically removed after 2 days"
                          >
                            ✓ Read · Clears in 2d
                          </span>
                        )}
                      </div>
                      <h4 className="alert-item-title">{alert.itemTitle}</h4>
                      <div className="alert-prices">
                        <span className="old-price">₹{Number(alert.oldPrice).toLocaleString('en-IN')}</span>
                        <span className="price-arrow">→</span>
                        <span className="new-price">₹{Number(alert.newPrice).toLocaleString('en-IN')}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      ) : (
        <>
          <div className="tabs-container">
            <nav className="tabs" role="tablist">
              <button
                type="button"
                role="tab"
                aria-selected={mode === 'login'}
                className={`tab-btn ${mode === 'login' ? 'active' : ''}`}
                onClick={() => handleTabChange('login')}
              >
                Sign In
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={mode === 'register'}
                className={`tab-btn ${mode === 'register' ? 'active' : ''}`}
                onClick={() => handleTabChange('register')}
              >
                Register
              </button>
            </nav>
          </div>

          <form className="auth-form" onSubmit={handleSubmit}>
            {error && (
              <div className="error-alert" role="alert">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10"></circle>
                  <line x1="12" y1="8" x2="12" y2="12"></line>
                  <line x1="12" y1="16" x2="12.01" y2="16"></line>
                </svg>
                <span>{error}</span>
              </div>
            )}

            {mode === 'register' && (
              <div className="form-group">
                <label className="form-label" htmlFor="username">
                  Username
                </label>
                <div className="input-wrapper">
                  <svg className="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                    <circle cx="12" cy="7" r="4"></circle>
                  </svg>
                  <input
                    id="username"
                    className="form-input"
                    type="text"
                    placeholder="Your display name"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                  />
                </div>
              </div>
            )}

            <div className="form-group">
              <label className="form-label" htmlFor="email">
                Email
              </label>
              <div className="input-wrapper">
                <svg className="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path>
                  <polyline points="22,6 12,13 2,6"></polyline>
                </svg>
                <input
                  id="email"
                  className="form-input"
                  type="email"
                  placeholder="name@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="password">
                Password
              </label>
              <div className="input-wrapper">
                <svg className="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                </svg>
                <input
                  id="password"
                  className="form-input"
                  type="password"
                  placeholder="Minimum 6 characters"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
            </div>

            <button type="submit" className="submit-btn" disabled={loading}>
              {loading ? (
                <>
                  <span className="spinner" aria-hidden="true"></span>
                  <span>Please wait...</span>
                </>
              ) : mode === 'login' ? (
                <>
                  <span>Sign In</span>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <line x1="5" y1="12" x2="19" y2="12"></line>
                    <polyline points="12 5 19 12 12 19"></polyline>
                  </svg>
                </>
              ) : (
                <>
                  <span>Create Account</span>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <line x1="5" y1="12" x2="19" y2="12"></line>
                    <polyline points="12 5 19 12 12 19"></polyline>
                  </svg>
                </>
              )}
            </button>
          </form>
        </>
      )}

      {/* Bottom Centered Status Footer */}
      <footer className="popup-footer">
        <span>🛡️ Real-time Price Drop Alerts</span>
      </footer>
    </div>
  );
}

