import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import App from '../App';

describe('App - Tracker Dashboard UI', () => {
  const mockAuthUser = {
    token: 'valid.jwt.token',
    user: { id: 1, username: 'ronak', email: 'ronak@example.com' },
  };

  const mockProducts = [
    {
      id: 101,
      title: 'Apple iPhone 15 (128 GB) - Black',
      targetPrice: 65000.00,
      createdAt: '2026-10-20T10:00:00',
      sources: [
        {
          id: 201,
          platform: 'AMAZON',
          url: 'https://www.amazon.in/dp/B08L5WHJ2V',
          currentPrice: 69999.00,
          originalPrice: 72000.00,
          currency: 'INR',
          imageUrl: 'https://m.media-amazon.com/images/iphone.jpg',
          inStock: true,
        },
      ],
    },
  ];

  const mockAlerts = [
    {
      id: 301,
      trackedItemId: 101,
      itemTitle: 'Apple iPhone 15 (128 GB) - Black',
      oldPrice: 72000.00,
      newPrice: 64999.00,
      targetPrice: 65000.00,
      isRead: false,
      createdAt: '2026-10-20T12:00:00',
    },
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
    chrome.storage.local.get.mockImplementation((keys, cb) => {
      const res = { token: mockAuthUser.token, user: mockAuthUser.user };
      if (cb) cb(res);
      return Promise.resolve(res);
    });
  });

  it('renders tracked items tab by default with products list', async () => {
    globalThis.fetch = vi.fn().mockImplementation((url) => {
      if (url.includes('/products')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => mockProducts,
        });
      }
      if (url.includes('/alerts')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => mockAlerts,
        });
      }
      return Promise.reject(new Error('Unknown URL: ' + url));
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/Apple iPhone 15/i)).toBeInTheDocument();
    });

    expect(screen.getByText(/₹69,999/i)).toBeInTheDocument();
    expect(screen.getByText(/^AMAZON$/i)).toBeInTheDocument();
  });

  it('switches to alerts tab and renders price drop alerts', async () => {
    globalThis.fetch = vi.fn().mockImplementation((url) => {
      if (url.includes('/products')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => mockProducts,
        });
      }
      if (url.includes('/alerts')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => mockAlerts,
        });
      }
      return Promise.reject(new Error('Unknown URL: ' + url));
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/Apple iPhone 15/i)).toBeInTheDocument();
    });

    // Switch to Alerts tab
    const alertsTab = screen.getByRole('button', { name: /Alerts/i });
    fireEvent.click(alertsTab);

    await waitFor(() => {
      expect(screen.getByText(/₹64,999/i)).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: /Mark as Read/i })).toBeInTheDocument();
  });

  it('deletes a tracked item when delete button is clicked', async () => {
    globalThis.fetch = vi.fn().mockImplementation((url, opts) => {
      if (opts?.method === 'DELETE') {
        return Promise.resolve({ ok: true, status: 204 });
      }
      if (url.includes('/products')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => mockProducts,
        });
      }
      if (url.includes('/alerts')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => [],
        });
      }
      return Promise.reject(new Error('Unknown URL: ' + url));
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/Apple iPhone 15/i)).toBeInTheDocument();
    });

    const deleteBtn = screen.getByTitle(/Delete item/i);
    fireEvent.click(deleteBtn);

    await waitFor(() => {
      expect(screen.queryByText(/Apple iPhone 15/i)).not.toBeInTheDocument();
    });
  });

  it('marks an alert as read when button is clicked', async () => {
    globalThis.fetch = vi.fn().mockImplementation((url, opts) => {
      if (opts?.method === 'PATCH') {
        return Promise.resolve({ ok: true, status: 200 });
      }
      if (url.includes('/products')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => [],
        });
      }
      if (url.includes('/alerts')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => mockAlerts,
        });
      }
      return Promise.reject(new Error('Unknown URL: ' + url));
    });

    render(<App />);

    const alertsTab = await screen.findByRole('button', { name: /Alerts/i });
    fireEvent.click(alertsTab);

    const markReadBtn = await screen.findByRole('button', { name: /Mark as Read/i });
    fireEvent.click(markReadBtn);

    await waitFor(() => {
      expect(screen.queryByRole('button', { name: /Mark as Read/i })).not.toBeInTheDocument();
    });
  });

  it('refreshes a tracked item price when refresh button is clicked', async () => {
    const refreshedItem = {
      ...mockProducts[0],
      sources: [
        {
          ...mockProducts[0].sources[0],
          currentPrice: 62000.00,
        },
      ],
    };

    globalThis.fetch = vi.fn().mockImplementation((url, opts) => {
      if (url.includes('/products/101/refresh') && opts?.method === 'POST') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => refreshedItem,
        });
      }
      if (url.includes('/products')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => mockProducts,
        });
      }
      if (url.includes('/alerts')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => [],
        });
      }
      return Promise.reject(new Error('Unknown URL: ' + url));
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/₹69,999/i)).toBeInTheDocument();
    });

    const refreshBtn = screen.getByTitle(/Refresh live price/i);
    fireEvent.click(refreshBtn);

    await waitFor(() => {
      expect(screen.getByText(/₹62,000/i)).toBeInTheDocument();
    });
  });

  it('toggles price history chart and displays statistics', async () => {
    const mockHistory = [
      { id: 1, price: 72000.00, inStock: true, recordedAt: '2026-10-18T10:00:00' },
      { id: 2, price: 69999.00, inStock: true, recordedAt: '2026-10-20T10:00:00' },
    ];

    globalThis.fetch = vi.fn().mockImplementation((url) => {
      if (url.includes('/products/101/history')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => mockHistory,
        });
      }
      if (url.includes('/products')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => mockProducts,
        });
      }
      if (url.includes('/alerts')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => [],
        });
      }
      return Promise.reject(new Error('Unknown URL: ' + url));
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/Apple iPhone 15/i)).toBeInTheDocument();
    });

    const chartToggleBtn = screen.getByTitle(/View price history chart & analytics/i);
    fireEvent.click(chartToggleBtn);

    await waitFor(() => {
      expect(screen.getByText(/All-Time Low/i)).toBeInTheDocument();
      expect(screen.getByText(/All-Time High/i)).toBeInTheDocument();
    });
  });

  it('updates target price from the chart panel', async () => {
    const updatedWithTarget = {
      ...mockProducts[0],
      targetPrice: 63000.00,
    };

    globalThis.fetch = vi.fn().mockImplementation((url, opts) => {
      if ((url.includes('/products/101/target-price') || url.includes('/products/101/alert-settings')) && opts?.method === 'PATCH') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => updatedWithTarget,
        });
      }
      if (url.includes('/products/101/history')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => [],
        });
      }
      if (url.includes('/products')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => mockProducts,
        });
      }
      if (url.includes('/alerts')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => [],
        });
      }
      return Promise.reject(new Error('Unknown URL: ' + url));
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/Apple iPhone 15/i)).toBeInTheDocument();
    });

    const chartToggleBtn = screen.getByTitle(/View price history chart & analytics/i);
    fireEvent.click(chartToggleBtn);

    const targetInput = await screen.findByRole('spinbutton');
    fireEvent.change(targetInput, { target: { value: '63000' } });

    const setBtn = screen.getByRole('button', { name: /^Set$/i });
    fireEvent.click(setBtn);

    await waitFor(() => {
      expect(screen.getByText(/Target: ₹63,000/i)).toBeInTheDocument();
    });

    // Test removing the target price limit to restore Normal Alert Mode
    const updatedWithoutTarget = {
      ...mockProducts[0],
      targetPrice: null,
      notifyOnAnyDrop: true,
    };

    globalThis.fetch = vi.fn().mockImplementation((url, opts) => {
      if (url.includes('/products/101/alert-settings') && opts?.method === 'PATCH') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => updatedWithoutTarget,
        });
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => [] });
    });

    const removeLimitBtn = screen.getByRole('button', { name: /Remove Limit/i });
    fireEvent.click(removeLimitBtn);

    await waitFor(() => {
      expect(screen.queryByText(/Target: ₹63,000/i)).not.toBeInTheDocument();
      expect(screen.getByText(/NORMAL ALERT MODE/i)).toBeInTheDocument();
    });
  });

  it('auto-matches Flipkart counterpart and displays side-by-side pricing with best deal badge', async () => {
    const multiSourceProduct = {
      ...mockProducts[0],
      sources: [
        mockProducts[0].sources[0], // AMAZON: 69999.00
        {
          id: 202,
          platform: 'FLIPKART',
          url: 'https://www.flipkart.com/apple-iphone-15/p/itm123',
          currentPrice: 67990.00,
          originalPrice: 72000.00,
          currency: 'INR',
          imageUrl: 'https://img.flipkart.com/iphone15.jpg',
          inStock: true,
        },
      ],
    };

    globalThis.fetch = vi.fn().mockImplementation((url, opts) => {
      if (url.includes('/products/101/sources') && opts?.method === 'POST') {
        const body = JSON.parse(opts.body);
        expect(body.autoDiscover).toBe(true);
        return Promise.resolve({
          ok: true,
          status: 201,
          json: async () => multiSourceProduct,
        });
      }
      if (url.includes('/products')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => mockProducts,
        });
      }
      if (url.includes('/alerts')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => [],
        });
      }
      return Promise.reject(new Error('Unknown URL: ' + url));
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/Apple iPhone 15/i)).toBeInTheDocument();
    });

    // Verify auto-match button is visible for single Amazon item
    const autoMatchBtn = screen.getByRole('button', { name: /Auto-Match on Flipkart/i });
    expect(autoMatchBtn).toBeInTheDocument();

    fireEvent.click(autoMatchBtn);

    // Verify side-by-side sources and best deal badge appear
    await waitFor(() => {
      expect(screen.getByText(/Best Deal on/i)).toBeInTheDocument();
    });

    expect(screen.getByText(/Save ₹2,009/i)).toBeInTheDocument();
    expect(screen.getByText(/₹67,990/i)).toBeInTheDocument();
    expect(screen.getByText(/^BEST$/)).toBeInTheDocument();
  });

  it('manually links counterpart URL via paste URL form', async () => {
    const multiSourceProduct = {
      ...mockProducts[0],
      sources: [
        mockProducts[0].sources[0],
        {
          id: 202,
          platform: 'FLIPKART',
          url: 'https://www.flipkart.com/apple-iphone-15/p/itm123',
          currentPrice: 67990.00,
          currency: 'INR',
          inStock: true,
        },
      ],
    };

    globalThis.fetch = vi.fn().mockImplementation((url, opts) => {
      if (url.includes('/products/101/sources') && opts?.method === 'POST') {
        const body = JSON.parse(opts.body);
        expect(body.url).toBe('https://www.flipkart.com/apple-iphone-15/p/itm123');
        expect(body.autoDiscover).toBe(false);
        return Promise.resolve({
          ok: true,
          status: 201,
          json: async () => multiSourceProduct,
        });
      }
      if (url.includes('/products')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => mockProducts,
        });
      }
      if (url.includes('/alerts')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => [],
        });
      }
      return Promise.reject(new Error('Unknown URL: ' + url));
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/Apple iPhone 15/i)).toBeInTheDocument();
    });

    const togglePasteBtn = screen.getByRole('button', { name: /\+ Paste URL/i });
    fireEvent.click(togglePasteBtn);

    const urlInput = screen.getByPlaceholderText(/Paste Flipkart URL/i);
    fireEvent.change(urlInput, { target: { value: 'https://www.flipkart.com/apple-iphone-15/p/itm123' } });

    const linkSubmitBtn = screen.getByRole('button', { name: /^Link$/i });
    fireEvent.click(linkSubmitBtn);

    await waitFor(() => {
      expect(screen.getByText(/₹67,990/i)).toBeInTheDocument();
    });
  });
});
