export interface ApiErrorBody {
  code: string;
  message: string;
  details: Record<string, unknown>;
}

export type ApiErrorKind = 'http' | 'network' | 'abort' | 'parse';

const statusMessages: Record<number, string> = {
  400: 'Проверьте введённые данные.',
  401: 'Для продолжения необходима авторизация.',
  403: 'У вас недостаточно прав для этой операции.',
  404: 'Запрашиваемый объект не найден.',
  409: 'Операция противоречит текущему состоянию данных.',
  500: 'Внутренняя ошибка сервера. Повторите попытку позже.',
};

export class ApiClientError extends Error {
  readonly name = 'ApiClientError';

  constructor(
    readonly kind: ApiErrorKind,
    readonly code: string,
    readonly status?: number,
    readonly details: Record<string, unknown> = {},
  ) {
    super(
      kind === 'abort'
        ? 'Запрос отменён.'
        : kind === 'network'
          ? 'Не удалось подключиться к серверу. Проверьте соединение.'
          : kind === 'parse'
            ? 'Сервер вернул ответ в неизвестном формате.'
            : (status !== undefined && statusMessages[status]) ||
              'Не удалось выполнить запрос. Повторите попытку.',
    );
  }
}

export function userFacingApiError(error: unknown): string {
  if (error instanceof ApiClientError) {
    const suffix =
      error.kind === 'http' && error.code && error.code !== 'HTTP_ERROR'
        ? ' (код: ' + error.code + ')'
        : '';
    return error.message + suffix;
  }
  return 'Произошла непредвиденная ошибка. Повторите попытку.';
}

export function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false;
  const candidate: Record<string, unknown> = value as Record<string, unknown>;
  return (
    typeof candidate.code === 'string' &&
    typeof candidate.message === 'string' &&
    typeof candidate.details === 'object' &&
    candidate.details !== null &&
    !Array.isArray(candidate.details)
  );
}

