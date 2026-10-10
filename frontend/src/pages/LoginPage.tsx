import { useState, type FormEvent } from 'react';
import { Armchair, Eye, EyeOff, LockKeyhole } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router';
import { useAuth } from '../features/auth/AuthProvider';
import { ApiClientError, userFacingApiError } from '../shared/api/api-error';
import { Button } from '../shared/ui/button';
import { Input } from '../shared/ui/input';

export function safeRedirect(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')
      || value.startsWith('/\\') || /[\r\n\\]/.test(value) || value.startsWith('/login')) {
    return '/';
  }
  return value;
}

export function LoginPage() {
  const { login, status } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const busy = status === 'authenticating';

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    if (!username.trim() || !password) {
      setError('Введите логин и пароль.');
      return;
    }
    setError('');
    try {
      await login(username, password);
      // React Router state comes from a protected internal route, never an external URL.
      const state = location.state as { from?: unknown } | null;
      navigate(safeRedirect(state?.from), { replace: true });
    } catch (reason) {
      if (reason instanceof ApiClientError && reason.kind === 'http' && reason.status === 401) {
        setError('Неверный логин или пароль.');
      } else if (reason instanceof ApiClientError && reason.kind === 'http' && reason.status === 403) {
        setError('У этой учётной записи нет доступа к ChairX.');
      } else {
        setError(userFacingApiError(reason));
      }
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10">
      <div className="w-full max-w-md rounded-3xl border border-slate-200 bg-white p-7 shadow-xl sm:p-10">
        <div className="mb-7 flex items-center gap-3">
          <div className="flex size-13 items-center justify-center rounded-2xl bg-indigo-600 text-white">
            <Armchair className="size-7" aria-hidden="true" />
          </div>
          <div>
            <p className="text-2xl font-extrabold tracking-tight text-slate-900">ChairX</p>
            <p className="text-xs font-semibold tracking-wider text-slate-400 uppercase">Система управления</p>
          </div>
        </div>
        <div className="mb-6 flex size-11 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
          <LockKeyhole className="size-5" aria-hidden="true" />
        </div>
        <h1 className="text-2xl font-bold text-slate-900">Вход для сотрудников</h1>
        <p className="mt-2 text-sm text-slate-500">Войдите под своей учётной записью ChairX.</p>
        <form className="mt-7 space-y-5" onSubmit={(event) => { void onSubmit(event); }} noValidate>
          <div>
            <label htmlFor="login-username" className="mb-2 block text-sm font-semibold text-slate-700">Логин</label>
            <Input id="login-username" name="username" autoComplete="username" value={username}
              onChange={(event) => setUsername(event.target.value)} required disabled={busy} />
          </div>
          <div>
            <label htmlFor="login-password" className="mb-2 block text-sm font-semibold text-slate-700">Пароль</label>
            <div className="relative">
              <Input id="login-password" name="password" autoComplete="current-password" required
                type={showPassword ? 'text' : 'password'} value={password}
                onChange={(event) => setPassword(event.target.value)} disabled={busy} className="pr-12" />
              <button type="button" onClick={() => setShowPassword((value) => !value)}
                className="absolute inset-y-0 right-0 flex min-w-11 items-center justify-center text-slate-500"
                aria-label={showPassword ? 'Скрыть пароль' : 'Показать пароль'}>
                {showPassword ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
              </button>
            </div>
          </div>
          {error && <p role="alert" className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
          <Button type="submit" className="w-full" disabled={busy} aria-busy={busy}>
            {busy ? 'Проверяем данные…' : 'Войти'}
          </Button>
        </form>
      </div>
    </div>
  );
}

