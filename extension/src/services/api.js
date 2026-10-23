/**
 * API client module for communicating with PriceHawk backend
 */

export const API_BASE = (import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');

let unauthorizedHandler = null;

export function setUnauthorizedHandler(fn) {
  unauthorizedHandler = fn;
}

export function handleUnauthorized() {
  if (typeof unauthorizedHandler === 'function') {
    unauthorizedHandler();
  }
}

/**
 * Check if an API response indicates expired/invalid JWT credentials (401 or 403)
 */
export function checkResponseAuth(response) {
  if (response && (response.status === 401 || response.status === 403)) {
    handleUnauthorized();
    throw new Error('Your session has expired. Please sign in again.');
  }
}

/**
 * Inspect client-side JWT token expiration without external libraries
 */
export function isTokenExpired(token) {
  if (!token || typeof token !== 'string') return true;
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return false;
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    const payload = JSON.parse(jsonPayload);
    if (payload && typeof payload.exp === 'number') {
      // Check if current time has passed expiry (with a 5 second safety margin)
      return Date.now() >= (payload.exp * 1000 - 5000);
    }
  } catch {
    return false;
  }
  return false;
}

export async function login(email, password) {
  const response = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ email: email.trim(), password }),
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const errorMsg = data?.message || (response.status === 401 ? 'Invalid email or password' : 'Login failed');
    throw new Error(errorMsg);
  }

  return data;
}

export async function register(username, email, password) {
  const response = await fetch(`${API_BASE}/auth/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ username: username.trim(), email: email.trim(), password }),
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const errorMsg = data?.message || (response.status === 409 ? 'Email is already registered' : 'Registration failed');
    throw new Error(errorMsg);
  }

  return data;
}

export async function getProducts(token) {
  const response = await fetch(`${API_BASE}/products`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  checkResponseAuth(response);
  if (!response.ok) throw new Error('Failed to fetch tracked products');
  return response.json();
}

export async function trackProduct(token, productData) {
  const response = await fetch(`${API_BASE}/products/track`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(productData),
  });
  checkResponseAuth(response);
  if (!response.ok) {
    let msg = 'Failed to track product';
    try {
      const data = await response.json();
      if (data.message) msg = data.message;
      else if (data.error) msg = data.error;
    } catch {}
    throw new Error(msg);
  }
  return response.json();
}

export async function deleteProduct(token, id) {
  const response = await fetch(`${API_BASE}/products/${id}`, {
    method: 'DELETE',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  checkResponseAuth(response);
  if (!response.ok) throw new Error('Failed to delete product');
  return true;
}

export async function getAlerts(token) {
  const response = await fetch(`${API_BASE}/alerts`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  checkResponseAuth(response);
  if (!response.ok) throw new Error('Failed to fetch alerts');
  return response.json();
}

export async function markAlertRead(token, id) {
  const response = await fetch(`${API_BASE}/alerts/${id}/read`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  checkResponseAuth(response);
  if (!response.ok) throw new Error('Failed to mark alert as read');
  return true;
}

export async function refreshProduct(token, id) {
  const response = await fetch(`${API_BASE}/products/${id}/refresh`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  checkResponseAuth(response);
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(data.message || 'Failed to refresh product price');
  }
  return response.json();
}

export async function getProductHistory(token, id) {
  const response = await fetch(`${API_BASE}/products/${id}/history`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  checkResponseAuth(response);
  if (!response.ok) throw new Error('Failed to fetch price history');
  return response.json();
}

export async function updateTargetPrice(token, id, targetPrice) {
  const response = await fetch(`${API_BASE}/products/${id}/target-price`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ targetPrice }),
  });
  checkResponseAuth(response);
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(data.message || 'Failed to update target price');
  }
  return response.json();
}

export async function addProductSource(token, id, { url, autoDiscover }) {
  const response = await fetch(`${API_BASE}/products/${id}/sources`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ url: url || null, autoDiscover: Boolean(autoDiscover) }),
  });
  checkResponseAuth(response);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || 'Failed to add counterpart source');
  }
  return data;
}

export async function updateAlertSettings(token, id, { targetPrice, notifyOnAnyDrop }) {
  const response = await fetch(`${API_BASE}/products/${id}/alert-settings`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ targetPrice, notifyOnAnyDrop }),
  });
  checkResponseAuth(response);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || 'Failed to update alert settings');
  }
  return data;
}
