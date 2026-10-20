import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import App from '../App';

describe('App - Extension Login UI', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders login form by default with email and password fields', async () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: /PriceHawk/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Sign In/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Register/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/Email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Sign In$/i })).toBeInTheDocument();
  });

  it('shows client-side validation error for invalid email or short password', async () => {
    render(<App />);

    const emailInput = screen.getByLabelText(/Email/i);
    const passwordInput = screen.getByLabelText(/Password/i);
    const submitBtn = screen.getByRole('button', { name: /^Sign In$/i });

    // Try submitting empty
    fireEvent.click(submitBtn);
    expect(await screen.findByText(/Please enter a valid email address/i)).toBeInTheDocument();

    // Try short password
    fireEvent.change(emailInput, { target: { value: 'valid@example.com' } });
    fireEvent.change(passwordInput, { target: { value: '123' } });
    fireEvent.click(submitBtn);
    expect(await screen.findByText(/Password must be at least 6 characters/i)).toBeInTheDocument();
  });

  it('switches to Register tab, requires username, and allows creating an account', async () => {
    globalThis.fetch = vi.fn().mockResolvedValueOnce({
      ok: true,
      status: 201,
      json: async () => ({ id: 1, username: 'ronak', email: 'ronak@example.com' }),
    }).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        token: 'registered.jwt.token',
        tokenType: 'Bearer',
        id: 1,
        username: 'ronak',
        email: 'ronak@example.com',
      }),
    });

    render(<App />);

    // Switch to Register tab
    const registerTab = screen.getByRole('tab', { name: /Register/i });
    fireEvent.click(registerTab);

    expect(screen.getByLabelText(/Username/i)).toBeInTheDocument();
    const submitBtn = screen.getByRole('button', { name: /Create Account/i });

    fireEvent.change(screen.getByLabelText(/Username/i), { target: { value: 'ronak' } });
    fireEvent.change(screen.getByLabelText(/Email/i), { target: { value: 'ronak@example.com' } });
    fireEvent.change(screen.getByLabelText(/Password/i), { target: { value: 'securePass123' } });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/Welcome, ronak/i)).toBeInTheDocument();
    });

    // Verify token stored
    expect(chrome.storage.local.set).toHaveBeenCalledWith(
      expect.objectContaining({
        token: 'registered.jwt.token',
        user: expect.objectContaining({ email: 'ronak@example.com', username: 'ronak' }),
      })
    );
  });

  it('successful login stores token in chrome.storage.local and displays logged-in state', async () => {
    globalThis.fetch = vi.fn().mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        token: 'mock.jwt.token',
        tokenType: 'Bearer',
        id: 42,
        username: 'alice',
        email: 'alice@example.com',
      }),
    });

    render(<App />);

    fireEvent.change(screen.getByLabelText(/Email/i), { target: { value: 'alice@example.com' } });
    fireEvent.change(screen.getByLabelText(/Password/i), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: /^Sign In$/i }));

    await waitFor(() => {
      expect(screen.getByText(/Welcome, alice/i)).toBeInTheDocument();
    });

    expect(chrome.storage.local.set).toHaveBeenCalledWith({
      token: 'mock.jwt.token',
      user: { id: 42, username: 'alice', email: 'alice@example.com' },
    });

    expect(screen.getByRole('button', { name: /Sign Out/i })).toBeInTheDocument();
  });

  it('failed login displays server error message without storing token', async () => {
    globalThis.fetch = vi.fn().mockResolvedValueOnce({
      ok: false,
      status: 401,
      json: async () => ({ message: 'Invalid email or password' }),
    });

    render(<App />);

    fireEvent.change(screen.getByLabelText(/Email/i), { target: { value: 'alice@example.com' } });
    fireEvent.change(screen.getByLabelText(/Password/i), { target: { value: 'wrongPassword' } });
    fireEvent.click(screen.getByRole('button', { name: /^Sign In$/i }));

    expect(await screen.findByText(/Invalid email or password/i)).toBeInTheDocument();
    expect(chrome.storage.local.set).not.toHaveBeenCalled();
    expect(screen.queryByText(/Welcome/i)).not.toBeInTheDocument();
  });

  it('logout clears token from chrome.storage.local and returns to login form', async () => {
    // Simulate stored auth session
    chrome.storage.local.get.mockImplementationOnce((keys, cb) => {
      const res = {
        token: 'existing.jwt.token',
        user: { id: 10, username: 'bob', email: 'bob@example.com' },
      };
      if (cb) cb(res);
      return Promise.resolve(res);
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/Welcome, bob/i)).toBeInTheDocument();
    });

    const logoutBtn = screen.getByRole('button', { name: /Sign Out/i });
    fireEvent.click(logoutBtn);

    await waitFor(() => {
      expect(screen.getByRole('tab', { name: /Sign In/i })).toBeInTheDocument();
      expect(screen.queryByText(/Welcome, bob/i)).not.toBeInTheDocument();
    });

    expect(chrome.storage.local.remove).toHaveBeenCalledWith(['token', 'user']);
  });
});
