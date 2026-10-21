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
});
