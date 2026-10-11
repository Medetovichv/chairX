export function isAdminRole(roles: readonly string[] | undefined): boolean {
  return roles?.includes('ADMIN') ?? false;
}
export function validateUser(username: string, name: string, password: string, confirmation: string): string | null {
  if (!/^[a-z0-9._-]{3,100}$/.test(username.trim().toLowerCase())) return 'Логин: от 3 до 100 символов a-z, 0-9, точка, дефис или подчёркивание.';
  if (username.trim().toLowerCase() === 'catalog') return 'Системный логин catalog зарезервирован.';
  if (!name.trim() || name.trim().length > 200) return 'Имя сотрудника обязательно (до 200 символов).';
  if (password.length < 12 || password.length > 128) return 'Пароль должен содержать от 12 до 128 символов.';
  if (confirmation !== password) return 'Повтор пароля не совпадает.';
  return null;
}
export function validatePassword(value: string, confirm: string): string | null {
  if (value.length < 12 || value.length > 128) return 'Пароль должен содержать от 12 до 128 символов.';
  if (value !== confirm) return 'Повтор пароля не совпадает.';
  return null;
}
export function validateRole(code: string, name: string, permissions: string[]): string | null {
  const normalized = code.trim().toUpperCase();
  if (!/^[A-Z][A-Z0-9_]{2,99}$/.test(normalized) ||
    ['ADMIN','MANAGER','EMPLOYEE'].includes(normalized)) return 'Код роли должен быть уникальным, от 3 до 100 символов (A-Z, 0-9, _), не ADMIN/MANAGER/EMPLOYEE.';
  if (!name.trim() || name.trim().length > 200) return 'Название роли обязательно (до 200 символов).';
  if (new Set(permissions).size !== permissions.length) return 'Разрешения не должны повторяться.';
  return null;
}
export function canChoosePermission(code: string, adminOnly: boolean): boolean {
  return !adminOnly && code !== 'CATALOG_ACCESS' && code !== 'ADMIN_ACCESS' &&
    code !== 'DAILY_CLOSING_UNLOCK_ADMIN' && !code.startsWith('USERS_') && !code.startsWith('ROLES_');
}
export function errorText(err: unknown): string {
  // Never interpolate server error payload, request body, passwords or hash into UI.
  if (err instanceof Error && err.name === 'ApiClientError') {
    const code = (err as Error & {code?:string}).code;
    if (code === 'VERSION_CONFLICT') return 'Роль изменена другим администратором. Проверьте актуальную версию и внесите изменения заново.';
  }
  return 'Операция не выполнена. Проверьте права доступа или состояние данных.';
}
