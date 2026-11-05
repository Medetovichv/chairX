import { ApiClientError } from '../../../shared/api/api-error';

/** Customer POSTs have no idempotency key: timeouts and lost responses fail closed. */
export function unknownCustomerWriteOutcome(error: unknown): boolean {
  return !(error instanceof ApiClientError) || error.kind !== 'http' ||
    error.status === undefined || error.status === 408 ||
    error.status === 409 || error.status >= 500;
}
