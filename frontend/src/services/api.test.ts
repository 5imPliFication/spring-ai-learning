import { describe, expect, it } from 'vitest';
import { isSessionExpired } from './api';

describe('isSessionExpired', () => {
  it('treats a 401 from a normal API call as an expired session', () => {
    expect(isSessionExpired(401, '/rooms/joined')).toBe(true);
    expect(isSessionExpired(401, 'users/me')).toBe(true);
    expect(isSessionExpired(401, undefined)).toBe(true);
  });

  it('treats a 401 from login/register as wrong credentials, not an expired session', () => {
    expect(isSessionExpired(401, '/auth/login')).toBe(false);
    expect(isSessionExpired(401, 'auth/register')).toBe(false);
  });

  it('ignores other statuses', () => {
    expect(isSessionExpired(400, '/rooms')).toBe(false);
    expect(isSessionExpired(403, '/rooms')).toBe(false);
  });
});
