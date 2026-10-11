import { amountCents, bishkekToday, nonnegativeSom, validLocalDate } from '../finance/validation';
import type { ClosingInput, ClosingPreview } from './types';

/** The frontend validates a read-only snapshot, not a financial ledger balance. */
export function validClosingDate(date: string, now = bishkekToday()): boolean {
  return validLocalDate(date) && date <= now;
}
export function closingInputIssue(input: ClosingInput, preview: ClosingPreview | undefined): string | null {
  if (!preview) return 'Нет актуального предварительного расчёта backend.';
  if (!nonnegativeSom(input.actualCash) || !nonnegativeSom(input.actualBank)) {
    return 'CASH и BANK должны быть целыми неотрицательными суммами (до 17 цифр).';
  }
  if ((input.cashNote?.length ?? 0) > 2000 || (input.bankNote?.length ?? 0) > 2000) {
    return 'Примечание по каждому счёту — максимум 2000 символов.';
  }
  const cashExpected = amountCents(preview.expectedCash);
  const bankExpected = amountCents(preview.expectedBank);
  if (cashExpected === null || bankExpected === null) {
    return 'Точность ожидаемых сумм недоступна. Закрытие заблокировано до сверки.';
  }
  if (BigInt(input.actualCash) * 100n !== cashExpected && !input.cashNote?.trim()) {
    return 'При расхождении CASH обязательно укажите причину.';
  }
  if (BigInt(input.actualBank) * 100n !== bankExpected && !input.bankNote?.trim()) {
    return 'При расхождении BANK обязательно укажите причину.';
  }
  return null;
}
