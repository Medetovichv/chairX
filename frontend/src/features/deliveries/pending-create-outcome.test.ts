import { describe, expect, it } from 'vitest';
import { ApiClientError } from '../../shared/api/api-error';
import { isUnknownDeliveryCreation } from './pending-create';

describe('delivery create POST outcome classification', () => {
  it.each([408, 409, 500, 502, 503, 504])('keeps new delivery creation locked after HTTP %i', status => {
    expect(isUnknownDeliveryCreation(new ApiClientError('http', 'REQUEST_FAILED', status))).toBe(true);
  });
  it.each([400, 401, 403, 404, 422])('permits clearing pending creation after HTTP %i', status => {
    expect(isUnknownDeliveryCreation(new ApiClientError('http', 'REQUEST_REJECTED', status))).toBe(false);
  });
  it('does not replay when the connection is lost after a possible commit', () => {
    expect(isUnknownDeliveryCreation(new TypeError('Disconnected after submission'))).toBe(true);
  });
});
