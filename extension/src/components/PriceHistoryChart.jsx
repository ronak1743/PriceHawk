import React, { useState, useEffect } from 'react';
import { getProductHistory, updateTargetPrice } from '../services/api';
import './PriceHistoryChart.css';

export default function PriceHistoryChart({ token, product, onTargetPriceUpdated }) {
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [targetInput, setTargetInput] = useState(product.targetPrice || '');
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

  const handleSaveTarget = async (e) => {
    e.preventDefault();
    const val = parseFloat(targetInput);
    if (isNaN(val) || val < 0) {
      setError('Please enter a valid target price');
      return;
    }

    setSavingTarget(true);
    setError(null);
    try {
      const updated = await updateTargetPrice(token, product.id, val);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 2500);
      if (onTargetPriceUpdated) {
        onTargetPriceUpdated(updated);
      }
    } catch (err) {
      setError(err.message || 'Failed to update target price');
    } finally {
      setSavingTarget(false);
    }
  };

  // Build points for chart
  const points = (history.length > 0
    ? history
    : [{ price: product.sources?.[0]?.currentPrice || 0, recordedAt: new Date().toISOString() }]
  ).map((h) => ({
    price: Number(h.price),
    date: new Date(h.recordedAt).toLocaleDateString('en-IN', { month: 'short', day: 'numeric' }),
  }));

  const prices = points.map((p) => p.price);
  const minPrice = Math.min(...prices);
  const maxPrice = Math.max(...prices);
  const currentPrice = prices[prices.length - 1];

  // SVG dimensions
  const svgWidth = 310;
  const svgHeight = 110;
  const padX = 28;
  const padY = 18;

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

  return (
    <div className="history-chart-card">
      {/* Analytics stats row */}
      <div className="chart-stats-grid">
        <div className="stat-pill">
          <span className="stat-label">All-Time Low</span>
          <span className="stat-value low">₹{minPrice ? minPrice.toLocaleString('en-IN') : '—'}</span>
        </div>
        <div className="stat-pill">
          <span className="stat-label">Current</span>
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
          <svg className="price-svg" viewBox={`0 0 ${svgWidth} ${svgHeight}`}>
            <defs>
              <linearGradient id={`grad-${product.id}`} x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stopColor="#818cf8" stopOpacity="0.45" />
                <stop offset="100%" stopColor="#818cf8" stopOpacity="0.0" />
              </linearGradient>
            </defs>

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
              <polygon points={areaPoints} fill={`url(#grad-${product.id})`} />
            )}

            {/* Price trend line */}
            <polyline
              points={polylinePoints}
              fill="none"
              stroke="#6366f1"
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
                  fill="#818cf8"
                  stroke="#0f172a"
                  strokeWidth="1.5"
                />
                <title>{`${p.date}: ₹${p.price.toLocaleString('en-IN')}`}</title>
              </g>
            ))}
          </svg>
        ) : (
          <div className="chart-empty-notice">
            <span>📊 Initial price logged at ₹{currentPrice.toLocaleString('en-IN')}. Trend line will grow as price checks run.</span>
          </div>
        )}
      </div>

      {/* Target Price Configuration */}
      <form className="target-price-form" onSubmit={handleSaveTarget}>
        <div className="target-input-row">
          <label htmlFor={`target-${product.id}`} className="target-label">
            Target Alert:
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
        {error && <span className="target-error-msg">{error}</span>}
      </form>
    </div>
  );
}
