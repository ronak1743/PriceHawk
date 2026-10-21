import React, { useState, useEffect } from 'react';
import { getProductHistory, updateAlertSettings } from '../services/api';
import './PriceHistoryChart.css';

export default function PriceHistoryChart({ token, product, onTargetPriceUpdated }) {
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [targetInput, setTargetInput] = useState(product.targetPrice || '');
  const [notifyAnyDrop, setNotifyAnyDrop] = useState(product.notifyOnAnyDrop ?? true);
  const [savingTarget, setSavingTarget] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let isMounted = true;
    setLoading(true);
    getProductHistory(token, product.id)
      .then((data) => {
        if (isMounted) {
          setHistory(data || []);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(err.message || 'Could not load price history');
          setLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [token, product.id]);

  const handleSaveAlertSettings = async (e) => {
    e.preventDefault();
    let val = targetInput === '' ? null : parseFloat(targetInput);
    if (val !== null && (isNaN(val) || val < 0)) {
      setError('Please enter a valid target price or leave blank');
      return;
    }

    setSavingTarget(true);
    setError(null);
    try {
      const updated = await updateAlertSettings(token, product.id, {
        targetPrice: val,
        notifyOnAnyDrop: notifyAnyDrop,
      });
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 2500);
      if (onTargetPriceUpdated) {
        onTargetPriceUpdated(updated);
      }
    } catch (err) {
      setError(err.message || 'Failed to update alert settings');
    } finally {
      setSavingTarget(false);
    }
  };

  // Unique platforms tracked for this product
  const availablePlatforms = Array.from(
    new Set((product.sources || []).map((s) => s.platform).filter(Boolean))
  );

  const [selectedPlatform, setSelectedPlatform] = useState(() => {
    return product.sources?.[0]?.platform || 'AMAZON';
  });

  const activePlatform = availablePlatforms.includes(selectedPlatform)
    ? selectedPlatform
    : (product.sources?.[0]?.platform || 'AMAZON');

  const activeSource = product.sources?.find((s) => s.platform === activePlatform) || product.sources?.[0] || {};

  // Filter history points for selected platform
  const platformHistory = history.filter(
    (h) => !h.platform || h.platform === activePlatform
  );

  // Build points for chart
  const points = (platformHistory.length > 0
    ? platformHistory
    : [{ price: activeSource.currentPrice || 0, recordedAt: new Date().toISOString() }]
  ).map((h) => ({
    price: Number(h.price),
    date: new Date(h.recordedAt).toLocaleDateString('en-IN', { month: 'short', day: 'numeric' }),
  }));

  const prices = points.map((p) => p.price);
  const minPrice = Math.min(...prices);
  const maxPrice = Math.max(...prices);
  const currentPrice = prices[prices.length - 1];

  // SVG dimensions
  const svgWidth = 460;
  const svgHeight = 210;
  const padX = 20;
  const padY = 20;

  const getX = (idx) => {
    if (points.length <= 1) return svgWidth / 2;
    return padX + (idx / (points.length - 1)) * (svgWidth - 2 * padX);
  };

  const getY = (price) => {
    if (maxPrice === minPrice) return svgHeight / 2;
    return svgHeight - padY - ((price - minPrice) / (maxPrice - minPrice)) * (svgHeight - 2 * padY);
  };

  const polylinePoints = points.map((p, idx) => `${getX(idx)},${getY(p.price)}`).join(' ');
  const areaPoints = points.length > 1
    ? `${getX(0)},${svgHeight - padY} ${polylinePoints} ${getX(points.length - 1)},${svgHeight - padY}`
    : '';

  const targetY = product.targetPrice && maxPrice !== minPrice && product.targetPrice >= minPrice && product.targetPrice <= maxPrice
    ? getY(Number(product.targetPrice))
    : null;

  // Platform specific color palette
  const theme = activePlatform === 'FLIPKART'
    ? { line: '#3b82f6', dot: '#60a5fa', grad: '#3b82f6' }
    : activePlatform === 'AMAZON'
    ? { line: '#f59e0b', dot: '#fbbf24', grad: '#f59e0b' }
    : { line: '#6366f1', dot: '#818cf8', grad: '#818cf8' };

  return (
    <div className="history-chart-card">
      {/* Platform Graph Tabs (shown when multiple stores are tracked) */}
      {availablePlatforms.length > 1 && (
        <div className="chart-platform-tabs" role="tablist">
          {availablePlatforms.map((plat) => {
            const platSource = product.sources?.find((s) => s.platform === plat);
            const isActive = activePlatform === plat;
            return (
              <button
                key={plat}
                type="button"
                role="tab"
                aria-selected={isActive}
                className={`chart-plat-tab ${plat.toLowerCase()} ${isActive ? 'active' : ''}`}
                onClick={() => setSelectedPlatform(plat)}
              >
                <span className="plat-tab-dot">●</span>
                <span className="plat-tab-name">{plat}</span>
                {platSource?.currentPrice && (
                  <span className="plat-tab-price">
                    ₹{Number(platSource.currentPrice).toLocaleString('en-IN')}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      )}

      {/* Analytics stats row for active platform */}
      <div className="chart-stats-grid">
        <div className="stat-pill">
          <span className="stat-label">All-Time Low</span>
          <span className="stat-value low">₹{minPrice ? minPrice.toLocaleString('en-IN') : '—'}</span>
        </div>
        <div className="stat-pill">
          <span className="stat-label">Current ({activePlatform})</span>
          <span className="stat-value">₹{currentPrice ? currentPrice.toLocaleString('en-IN') : '—'}</span>
        </div>
        <div className="stat-pill">
          <span className="stat-label">All-Time High</span>
          <span className="stat-value high">₹{maxPrice ? maxPrice.toLocaleString('en-IN') : '—'}</span>
        </div>
      </div>

      {/* SVG Chart */}
      <div className="chart-wrapper">
        {loading ? (
          <div className="chart-loading">
            <span className="mini-spinner"></span>
            <span>Loading price history...</span>
          </div>
        ) : points.length > 1 ? (
          <svg className="price-svg" viewBox={`0 0 ${svgWidth} ${svgHeight}`} preserveAspectRatio="none">
            <defs>
              <linearGradient id={`grad-${product.id}-${activePlatform}`} x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stopColor={theme.grad} stopOpacity="0.45" />
                <stop offset="100%" stopColor={theme.grad} stopOpacity="0.0" />
              </linearGradient>
            </defs>

            {/* Subtle Horizontal Reference Gridlines */}
            <line
              x1={padX}
              y1={padY}
              x2={svgWidth - padX}
              y2={padY}
              stroke="rgba(255, 255, 255, 0.05)"
              strokeDasharray="4 4"
            />
            <line
              x1={padX}
              y1={svgHeight / 2}
              x2={svgWidth - padX}
              y2={svgHeight / 2}
              stroke="rgba(255, 255, 255, 0.05)"
              strokeDasharray="4 4"
            />
            <line
              x1={padX}
              y1={svgHeight - padY}
              x2={svgWidth - padX}
              y2={svgHeight - padY}
              stroke="rgba(255, 255, 255, 0.05)"
              strokeDasharray="4 4"
            />

            {/* Target price dashed line */}
            {targetY !== null && (
              <line
                x1={padX}
                y1={targetY}
                x2={svgWidth - padX}
                y2={targetY}
                stroke="#10b981"
                strokeWidth="1.2"
                strokeDasharray="4 3"
              />
            )}

            {/* Shaded area */}
            {areaPoints && (
              <polygon points={areaPoints} fill={`url(#grad-${product.id}-${activePlatform})`} />
            )}

            {/* Price trend line */}
            <polyline
              points={polylinePoints}
              fill="none"
              stroke={theme.line}
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />

            {/* Data circles */}
            {points.map((p, idx) => (
              <g key={idx} className="chart-point">
                <circle
                  cx={getX(idx)}
                  cy={getY(p.price)}
                  r="3.5"
                  fill={theme.dot}
                  stroke="#0f172a"
                  strokeWidth="1.5"
                />
                <title>{`${p.date} (${activePlatform}): ₹${p.price.toLocaleString('en-IN')}`}</title>
              </g>
            ))}
          </svg>
        ) : (
          <div className="chart-empty-notice">
            <span>📊 Initial {activePlatform} price logged at ₹{currentPrice.toLocaleString('en-IN')}. Trend line will grow as price checks run.</span>
          </div>
        )}
      </div>

      {/* Target Price & Smart Alert Configuration */}
      <form className="target-price-form" onSubmit={handleSaveAlertSettings}>
        <div className="target-input-row">
          <label htmlFor={`target-${product.id}`} className="target-label">
            Target Price:
          </label>
          <div className="target-input-wrapper">
            <span className="target-currency-symbol">₹</span>
            <input
              id={`target-${product.id}`}
              type="number"
              className="target-input"
              placeholder="e.g. 65000"
              value={targetInput}
              onChange={(e) => setTargetInput(e.target.value)}
              disabled={savingTarget}
            />
          </div>
          <button type="submit" className="save-target-btn" disabled={savingTarget}>
            {savingTarget ? '...' : saveSuccess ? '✓ Saved' : 'Set'}
          </button>
        </div>

        <div className="alert-toggle-row">
          <label className="toggle-label" htmlFor={`anydrop-${product.id}`}>
            <input
              id={`anydrop-${product.id}`}
              type="checkbox"
              className="toggle-checkbox"
              checked={notifyAnyDrop}
              onChange={(e) => setNotifyAnyDrop(e.target.checked)}
              disabled={savingTarget}
            />
            <span className="toggle-text">🔔 Alert on any price drop</span>
          </label>
        </div>

        {product.lastNotifiedPrice && (
          <div className="alert-meta-status">
            <span>🛡️ Anti-spam active · Last notified: ₹{Number(product.lastNotifiedPrice).toLocaleString('en-IN')}</span>
          </div>
        )}

        {error && <span className="target-error-msg">{error}</span>}
      </form>
    </div>
  );
}
