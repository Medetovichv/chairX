# ChairX frontend — актуальный интерфейс

**На 11.10.2026** React 19 + TypeScript + Vite 8 + Tailwind 4 + React Router + TanStack Query. Это **реальный frontend F03–F12 + UI-v2 (PR #40–#46)**, а не F01-заглушка. Backend — источник данных, бизнес-ограничений и прав.

Список всех экранов, URL, функций, permission и бизнес-процессов: **[Функции и разделы](../docs/system-features.md)**. Полная штатная ролевая матрица: **[Роли и разрешения](../docs/roles-and-permissions.md)**. Слой API и архитектура: **[Технический справочник](../docs/technical-reference.md)**.

## Основные разделы

- **Работа:** главная, продажи с канбаном/черновиками, доставки и карточки, остатки/перемещения, клиенты, закрытие дня.
- **Управление:** каталог с вариациями и SKU, закупки/частичная приёмка/платежи, поставщики, возвраты/обмены/брак, расходы, CashFlow, CASH/BANK.
- **Система:** сотрудник, роли/разрешения, активация/сброс пароля и security audit (только system ADMIN).

Маршруты в `src/app/router/AppRouter.tsx`; меню и route permission в `src/shared/lib/navigation.ts`; бизнес-клиенты в `src/features/*/api.ts`.

## Запуск и проверка

```bash
# из корня: PostgreSQL+Spring+Vite
bash scripts/init-local-env.sh
docker compose up -d --build --wait

# или при запущенном backend — из frontend/
cd frontend
npm ci
npm run dev

# качество
npm run lint
npm run typecheck
npm test
npm run build
```

UI: `http://localhost:5173`; `/api` проксируется к backend. Локальные учетные данные хранятся в корневом `.env`, **не** во frontend/VITE переменных. Авторизация HTTP Basic и CSRF действует через in-memory session; UI guards — только удобство, серверные права обязательны. `docker compose down` сохраняет БД, `down -v` удаляет volume.

**Ограничение:** текущий экран дневного закрытия относится ко всей CASH/BANK компании, а не к кассе конкретного employee. Подробнее в [аудите](../docs/audit/2026-10-11-documentation-audit.md). Также не путайте green CI с production/UAT signoff.
