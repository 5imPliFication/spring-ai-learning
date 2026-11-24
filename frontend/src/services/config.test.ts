import { describe, expect, it } from 'vitest';
import { normalizeBaseUrl } from './config';

describe('normalizeBaseUrl', () => {
  it('treats a missing value as relative (empty string), never "undefined"', () => {
    expect(normalizeBaseUrl(undefined)).toBe('');
    expect(normalizeBaseUrl(null)).toBe('');
    expect(`${normalizeBaseUrl(undefined)}/api/v1`).toBe('/api/v1');
  });

  it('strips trailing slashes so paths never contain "//"', () => {
    expect(normalizeBaseUrl('https://x.up.railway.app/')).toBe('https://x.up.railway.app');
    expect(normalizeBaseUrl('https://x.up.railway.app///')).toBe('https://x.up.railway.app');
  });

  it('trims whitespace from .env values', () => {
    expect(normalizeBaseUrl('  https://x.up.railway.app  ')).toBe('https://x.up.railway.app');
  });

  it('leaves a clean URL unchanged', () => {
    expect(normalizeBaseUrl('https://x.up.railway.app')).toBe('https://x.up.railway.app');
  });
});
