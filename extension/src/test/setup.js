import '@testing-library/jest-dom';
import { vi } from 'vitest';

// In-memory mock for chrome.storage.local
const storageStore = new Map();

globalThis.chrome = {
  storage: {
    local: {
      get: vi.fn((keys, callback) => {
        const result = {};
        if (typeof keys === 'string') {
          result[keys] = storageStore.get(keys);
        } else if (Array.isArray(keys)) {
          keys.forEach(k => { result[k] = storageStore.get(k); });
        } else if (keys && typeof keys === 'object') {
          Object.keys(keys).forEach(k => {
            result[k] = storageStore.has(k) ? storageStore.get(k) : keys[k];
          });
        } else {
          storageStore.forEach((v, k) => { result[k] = v; });
        }
        if (callback) callback(result);
        return Promise.resolve(result);
      }),
      set: vi.fn((items, callback) => {
        Object.entries(items).forEach(([k, v]) => storageStore.set(k, v));
        if (callback) callback();
        return Promise.resolve();
      }),
      remove: vi.fn((keys, callback) => {
        const list = Array.isArray(keys) ? keys : [keys];
        list.forEach(k => storageStore.delete(k));
        if (callback) callback();
        return Promise.resolve();
      }),
      clear: vi.fn(callback => {
        storageStore.clear();
        if (callback) callback();
        return Promise.resolve();
      }),
    },
  },
};

// Reset storage before each test
beforeEach(() => {
  storageStore.clear();
  vi.clearAllMocks();
});
