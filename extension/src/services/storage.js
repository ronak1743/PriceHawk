/**
 * Storage service abstracting chrome.storage.local
 */

export async function getAuth() {
  if (typeof chrome !== 'undefined' && chrome.storage?.local) {
    const data = await chrome.storage.local.get(['token', 'user']);
    return data?.token ? data : null;
  }
  return null;
}

export async function setAuth({ token, user }) {
  if (typeof chrome !== 'undefined' && chrome.storage?.local) {
    await chrome.storage.local.set({ token, user });
  }
}

export async function clearAuth() {
  if (typeof chrome !== 'undefined' && chrome.storage?.local) {
    await chrome.storage.local.remove(['token', 'user']);
  }
}
