// Backend origin, e.g. https://example.up.railway.app.
// Leave VITE_API_BASE_URL unset in dev so requests stay relative and go through the Vite proxy.
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').trim().replace(/\/+$/, '');
