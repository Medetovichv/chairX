import { describe, expect, it } from 'vitest';
import { ApiClientError } from '../../../shared/api/api-error';
import { unknownCustomerWriteOutcome } from './customer-write-outcome';

describe('customer POST result uncertainty in sale quick create', () => {
  it.each([408,409,500,502,503,504])('locks nested creation and phone resolution on HTTP %i', status => {
    expect(unknownCustomerWriteOutcome(new ApiClientError('http','REQUEST_TIMEOUT',status))).toBe(true);
  });
  it.each([400,401,403,404,422])('recognizes a definitive HTTP %i rejection', status => {
    expect(unknownCustomerWriteOutcome(new ApiClientError('http','VALIDATION_FAILED',status))).toBe(false);
  });
  it('treats network disconnection after a possible commit as unknown', () => {
    expect(unknownCustomerWriteOutcome(new TypeError('Connection lost after POST'))).toBe(true);
  });
});
