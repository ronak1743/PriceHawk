import React, { useState, useEffect } from 'react';
import { getAuth, setAuth as saveAuth, clearAuth } from './services/storage';
import { login, register, getProducts, deleteProduct, getAlerts, markAlertRead, trackProduct } from './services/api';
import './App.css';

export default function App() {
  const [auth, setAuth] = useState(null);
  const [mode, setMode] = useState('login'); // 'login' | 'register'
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  // Dashboard states
  const [dashboardTab, setDashboardTab] = useState('items'); // 'items' | 'alerts'
  const [products, setProducts] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [fetchingData, setFetchingData] = useState(false);
  const [actionSuccess, setActionSuccess] = useState(null);

  useEffect(() => {
    getAuth().then((stored) => {
      if (stored?.token) {
        setAuth(stored);
        loadDashboardData(stored.token);
      }
    });
  }, []);

  const loadDashboardData = async (token) => {
    setFetchingData(true);
    try {
      const [fetchedProducts, fetchedAlerts] = await Promise.all([
        getProducts(token).catch(() => []),
        getAlerts(token).catch(() => []),
      ]);
      setProducts(fetchedProducts || []);
      setAlerts(fetchedAlerts || []);
    } catch (err) {
      console.error('Error loading dashboard data', err);
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
    if (!auth?.token) return;
    try {
      await deleteProduct(auth.token, productId);
      setProducts((prev) => prev.filter((p) => p.id !== productId));
      setActionSuccess('Item removed from tracking');
      setTimeout(() => setActionSuccess(null), 3000);
    } catch (err) {
      setError(err.message || 'Failed to delete item');
    }
  };

  const handleMarkAlertRead = async (alertId) => {
    if (!auth?.token) return;
    try {
      await markAlertRead(auth.token, alertId);
      setAlerts((prev) =>
        prev.map((a) => (a.id === alertId ? { ...a, isRead: true } : a))
      );
    } catch (err) {
      setError(err.message || 'Failed to mark alert as read');
    }
  };

  const handleTrackCurrentTab = async () => {
    if (!auth?.token) return;
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
                      } catch {}
                    }
                    const hires = el.getAttribute('data-old-hires');
                    if (hires && hires.startsWith('http')) { imageUrl = hires; break; }
                    const src = el.getAttribute('src') || el.src;
                    if (src && src.startsWith('http') && !src.includes('transparent-pixel')) { imageUrl = src; break; }
                  }
                } else {
                  const tEl = document.querySelector('span.VU-ZEz') || document.querySelector('span.B_NuCI') || document.querySelector('h1');
                  if (tEl) title = tEl.textContent.trim();

                  const pSelectors = ['div.Nx9bqj.CxhGGd', 'div._30jeq3._16Jk6d', 'div.Nx9bqj'];
                  for (const s of pSelectors) {
                    const el = document.querySelector(s);
                    if (el && el.textContent) {
                      const clean = el.textContent.replace(/[^0-9.]/g, '');
                      const val = parseFloat(clean);
                      if (!isNaN(val)) { price = val; break; }
                    }
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
        const currentPrice = details?.currentPrice || 1.0;
        const imageUrl = details?.imageUrl || '';

        const item = await trackProduct(auth.token, {
          url: tab.url,
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
    }
  };

  const unreadAlertsCount = alerts.filter((a) => !a.isRead).length;

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
            <h1 className="brand-title">PriceHawk</h1>
          </div>
          <span className="version-pill">v1.0 Pro</span>
        </div>
        <p className="brand-subtitle">
          <span className="brand-subtitle-dot" aria-hidden="true"></span>
          Intelligent Price Tracking for Amazon &amp; Flipkart
        </p>
      </header>

      {auth?.user ? (
        <div className="dashboard-container">
          {/* Top user bar */}
          <div className="dashboard-user-bar">
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
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
                <polyline points="16 17 21 12 16 7"></polyline>
                <line x1="21" y1="12" x2="9" y2="12"></line>
              </svg>
              <span>Sign Out</span>
            </button>
          </div>

          {/* Quick Track Bar */}
          <div className="quick-track-container">
            <button className="quick-track-btn" onClick={handleTrackCurrentTab}>
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="12" y1="5" x2="12" y2="19"></line>
                <line x1="5" y1="12" x2="19" y2="12"></line>
              </svg>
              <span>Track Current Tab Product</span>
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
                <span className="tab-count-badge">{alerts.length}</span>
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
                    const source = item.sources?.[0] || {};
                    return (
                      <div key={item.id} className="product-card">
                        <div className="card-thumb-container">
                          {source.imageUrl ? (
                            <img src={source.imageUrl} alt={item.title} className="product-thumbnail" />
                          ) : (
                            <div className="thumbnail-placeholder">📦</div>
                          )}
                        </div>
                        <div className="card-details">
                          <div className="card-top-row">
                            <span className={`platform-pill ${source.platform?.toLowerCase() || 'other'}`}>
                              {source.platform || 'OTHER'}
                            </span>
                            <button
                              title="Delete item"
                              className="delete-item-btn"
                              onClick={() => handleDeleteProduct(item.id)}
                            >
                              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <polyline points="3 6 5 6 21 6"></polyline>
                                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                              </svg>
                            </button>
                          </div>
                          <h3 className="card-title" title={item.title}>{item.title}</h3>
                          <div className="card-price-row">
                            <span className="current-price">
                              ₹{source.currentPrice ? Number(source.currentPrice).toLocaleString('en-IN') : '—'}
                            </span>
                            {item.targetPrice && (
                              <span className="target-price-label">
                                Target: ₹{Number(item.targetPrice).toLocaleString('en-IN')}
                              </span>
                            )}
                          </div>
                        </div>
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
              {alerts.length === 0 ? (
                <div className="empty-state">
                  <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                    <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                  </svg>
                  <p className="empty-title">No price drop alerts yet</p>
                  <p className="empty-subtitle">When prices on your tracked items drop, you'll see alerts here and via Resend email.</p>
                </div>
              ) : (
                <div className="alerts-list">
                  {alerts.map((alert) => (
                    <div key={alert.id} className={`alert-card ${alert.isRead ? 'read' : 'unread'}`}>
                      <div className="alert-card-header">
                        <span className="alert-drop-badge">🚨 Price Drop</span>
                        {!alert.isRead && (
                          <button
                            className="mark-read-btn"
                            onClick={() => handleMarkAlertRead(alert.id)}
                          >
                            Mark as Read
                          </button>
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

      <footer className="popup-footer">
        <span>🛡️ Real-time Price Drop Alerts</span>
      </footer>
    </div>
  );
}
