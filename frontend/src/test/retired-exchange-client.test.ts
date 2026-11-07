import { describe, expect, it } from 'vitest';
import { financeApi } from '../features/finance/api';

/** The Exchange workflow is retired; do not surface a dead HTTP settlement route. */
describe('retired Exchange financial contract', () => {
  it('does not expose a legacy Exchange settlement action', () => {
    expect(Object.keys(financeApi)).not.toContain('settle');
  });
});
