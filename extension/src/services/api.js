/**
 * API client module for communicating with PriceHawk backend
 */

export const API_BASE = 'http://localhost:8080';

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
