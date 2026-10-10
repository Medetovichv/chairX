import { describe, expect, it } from 'vitest';
import { ApiClientError } from '../shared/api/api-error';
import { createQueryClient, queryKeys } from '../shared/api/query';

describe('Query foundation', () => {
  it('exposes stable, scoped keys and does not retry 401/403/409', () => {
    expect(queryKeys.sales).toEqual(['sales']);
    const retry = createQueryClient().getDefaultOptions().queries?.retry;
    expect(typeof retry).toBe('function');
    if (typeof retry !== 'function') throw new Error('Retry function is required');
    for (const status of [401, 403, 409]) {
      expect(retry(0, new ApiClientError('http', 'TEST_ERROR', status))).toBe(false);
    }
    expect(retry(0, new ApiClientError('network', 'NETWORK_ERROR'))).toBe(true);
    expect(retry(1, new ApiClientError('network', 'NETWORK_ERROR'))).toBe(false);
  });
});

