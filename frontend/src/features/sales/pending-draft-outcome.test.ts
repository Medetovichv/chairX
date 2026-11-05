import { describe, expect, it } from 'vitest';
import { ApiClientError } from '../../shared/api/api-error';
import { isUnknownDraftSubmission } from './pending-draft';

describe('sale draft POST outcome classification', () => {
  it.each([408,409,500,502,503,504])('preserves the draft idempotency key after HTTP %i', status => {
    expect(isUnknownDraftSubmission(new ApiClientError('http','REQUEST_FAILED',status))).toBe(true);
  });
  it.each([400,401,403,404,422])('permits clearing the pending key after definitive HTTP %i', status => {
    expect(isUnknownDraftSubmission(new ApiClientError('http','REQUEST_REJECTED',status))).toBe(false);
  });
  it('preserves the key when the response never arrives', () => {
    expect(isUnknownDraftSubmission(new TypeError('Connection lost after commit'))).toBe(true);
  });
});
