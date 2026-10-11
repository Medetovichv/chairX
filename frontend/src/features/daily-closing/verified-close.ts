import { ApiClientError } from '../../shared/api/api-error';
import { closingApi } from './api';
import { closingInputIssue } from './validation';
import { amountCents } from '../finance/validation';
import type { ClosingInput } from './types';
import type { Money } from '../finance/types';

/** Freeze the expectations the operator actually approved.
 * No extra approval fields are sent to the backend request DTO.
 */
export type ApprovedClosing = ClosingInput & {
  approvedExpectedCash: Money;
  approvedExpectedBank: Money;
};

/**
 * Perform GET-only checks BEFORE the financial closing POST.
 * If a preflight GET fails, we know no POST was submitted in this attempt,
 * so convert the failure to an unambiguous 400 and let GuardedPost release
 * the pending tab marker rather than trapping an operator in "unknown".
 * Backend still holds final authority in its closing transaction.
 */
export async function postVerifiedClosing(date:string,approved:ApprovedClosing) {
  let currentAccess:Awaited<ReturnType<typeof closingApi.access>>;
  let currentPreview:Awaited<ReturnType<typeof closingApi.preview>>;
  try {
    [currentAccess,currentPreview]=await Promise.all([
      closingApi.access(date),closingApi.preview(date),
    ]);
  } catch {
    throw new ApiClientError('http','CLOSING_PREFLIGHT_UNAVAILABLE',400);
  }
  if(currentAccess.reportExists||!currentAccess.canEdit||currentAccess.businessDate!==date||
    currentPreview.businessDate!==date) {
    throw new ApiClientError('http','CLOSING_PREFLIGHT_DENIED_OR_CHANGED',400);
  }
  const checkedCash=amountCents(approved.approvedExpectedCash);
  const checkedBank=amountCents(approved.approvedExpectedBank);
  const latestCash=amountCents(currentPreview.expectedCash);
  const latestBank=amountCents(currentPreview.expectedBank);
  if(checkedCash===null||checkedBank===null||latestCash===null||latestBank===null||
    checkedCash!==latestCash||checkedBank!==latestBank) {
    throw new ApiClientError('http','CLOSING_EXPECTED_BALANCE_CHANGED',400);
  }
  const payload:ClosingInput={
    actualCash:approved.actualCash,actualBank:approved.actualBank,
    cashNote:approved.cashNote,bankNote:approved.bankNote,
  };
  if(closingInputIssue(payload,currentPreview)) {
    throw new ApiClientError('http','CLOSING_PREFLIGHT_INVALID_INPUT',400);
  }
  return closingApi.close(date,payload);
}
