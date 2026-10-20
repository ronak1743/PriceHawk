import React, { useState, useEffect } from 'react';
import { getAuth, setAuth as saveAuth, clearAuth } from './services/storage';
import { login, register } from './services/api';
import './App.css';

export default function App() {
  const [auth, setAuth] = useState(null);
  const [mode, setMode] = useState('login'); // 'login' | 'register'
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    getAuth().then((stored) => {
      if (stored?.token) {
        setAuth(stored);
      }
    });
  }, []);

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
        // Seamlessly log in after successful registration
        const loginRes = await login(email, password);
        const authData = {
          token: loginRes.token,
          user: { id: loginRes.id, username: loginRes.username, email: loginRes.email },
        };
        await saveAuth(authData);
        setAuth(authData);
      } else {
        const loginRes = await login(email, password);
        const authData = {
          token: loginRes.token,
          user: { id: loginRes.id, username: loginRes.username, email: loginRes.email },
        };
        await saveAuth(authData);
        setAuth(authData);
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
  };

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
        <div className="logged-in-container">
          <div className="avatar-ring">
            <div className="user-avatar">
              {(auth.user.username || auth.user.email || 'U').charAt(0).toUpperCase()}
            </div>
          </div>

          <div className="user-info">
            <h2 className="user-greeting">Welcome, {auth.user.username || auth.user.email}</h2>
            <p className="user-email">{auth.user.email}</p>
          </div>

          <div className="status-pill">
            <span className="status-dot"></span>
            Connected to PriceHawk Cloud
          </div>

          <div className="profile-card">
            <div className="profile-stat">
              <span className="profile-stat-value">0</span>
              <span className="profile-stat-label">Tracked Items</span>
            </div>
            <div style={{ width: '1px', height: '24px', background: 'var(--border-subtle)' }} />
            <div className="profile-stat">
              <span className="profile-stat-value">0</span>
              <span className="profile-stat-label">Price Drops</span>
            </div>
          </div>

          <button className="logout-btn" onClick={handleLogout}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
              <polyline points="16 17 21 12 16 7"></polyline>
              <line x1="21" y1="12" x2="9" y2="12"></line>
            </svg>
            Sign Out
          </button>
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
