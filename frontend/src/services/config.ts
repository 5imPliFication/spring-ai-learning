/** Trims whitespace and trailing slashes; an unset value becomes '' (relative URLs, so the Vite dev proxy is used). */
export function normalizeBaseUrl(raw: string | undefined | null): string {
  return (raw ?? '').trim().replace(/\/+$/, '');
}

// Backend origin, e.g. https://example.up.railway.app.
// Leave VITE_API_BASE_URL unset in dev so requests stay relative and go through the Vite proxy.
export const API_BASE_URL = normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL);
