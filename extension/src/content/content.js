import { isProductPage, extractProductDetails } from './extractor';
import './content.css';

(function initPriceHawkContentScript() {
  if (!isProductPage(window.location.href)) {
    return;
  }

  // Prevent duplicate injection
  if (document.getElementById('pricehawk-sentinel-badge')) {
    return;
  }

  const details = extractProductDetails(window.location.href);
  if (!details) return;

  const container = document.createElement('div');
  container.id = 'pricehawk-sentinel-badge';
  container.className = 'pricehawk-floating-sentinel';

  container.innerHTML = `
    <div class="ph-widget-inner">
      <div class="ph-badge-logo">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
          <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
          <line x1="12" y1="22.08" x2="12" y2="12"></line>
        </svg>
      </div>
      <div class="ph-badge-info">
        <span class="ph-badge-brand">PriceHawk</span>
        <span class="ph-badge-price">₹${details.currentPrice ? details.currentPrice.toLocaleString('en-IN') : 'Detecting...'}</span>
      </div>
      <button id="pricehawk-track-btn" class="ph-track-btn">
        <span>Track Price</span>
      </button>
    </div>
    <div id="pricehawk-toast" class="ph-toast hidden"></div>
  `;

  document.body.appendChild(container);

  const trackBtn = document.getElementById('pricehawk-track-btn');
  const toast = document.getElementById('pricehawk-toast');

  const showToast = (message, isError = false) => {
    toast.textContent = message;
    toast.className = `ph-toast ${isError ? 'error' : 'success'}`;
    setTimeout(() => {
      toast.className = 'ph-toast hidden';
    }, 4000);
  };

  trackBtn.addEventListener('click', async () => {
    trackBtn.disabled = true;
    trackBtn.innerHTML = '<span class="ph-spinner"></span>';

    try {
      chrome.storage.local.get(['token'], async (result) => {
        if (!result?.token) {
          showToast('Please sign in via PriceHawk extension first!', true);
          trackBtn.disabled = false;
          trackBtn.innerHTML = '<span>Track Price</span>';
          return;
        }

        try {
          const res = await fetch('http://localhost:8080/products/track', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${result.token}`,
            },
            body: JSON.stringify({
              url: details.url,
              title: details.title,
              currentPrice: details.currentPrice || 1.0,
              imageUrl: details.imageUrl,
            }),
          });

          if (!res.ok) {
            throw new Error('Failed to track product');
          }

          showToast('Product tracked! We will alert you on price drops.');
          trackBtn.innerHTML = '<span>Tracking ✓</span>';
          trackBtn.classList.add('tracked');
        } catch (err) {
          showToast('Tracking failed: ' + err.message, true);
          trackBtn.disabled = false;
          trackBtn.innerHTML = '<span>Track Price</span>';
        }
      });
    } catch (err) {
      showToast('Error: ' + err.message, true);
      trackBtn.disabled = false;
      trackBtn.innerHTML = '<span>Track Price</span>';
    }
  });
})();
