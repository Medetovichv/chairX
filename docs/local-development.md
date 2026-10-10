# ChairX F01.1 — локальное окружение

Только для разработки на собственном компьютере, **не для production**.

## Требования

Docker Desktop (macOS / Apple Silicon поддерживается) или Docker Engine +
Compose v2.22+. Порты 5432, 8080 и 5173 должны быть свободны.
Node.js и Maven не нужно ставить на Mac ради контейнерного запуска.

F01 ещё в PR #12, F01.1 создан поверх F01. До слияния работайте с веткой
feat/frontend-f01-1-local-dev, а не с main. Чтобы не менять текущую рабочую
копию, можно использовать отдельный git worktree:

~~~sh
git fetch origin
git worktree add --detach ../chairx-local origin/feat/frontend-f01-1-local-dev
cd ../chairx-local
~~~

## Первый запуск

В корне checkout:

~~~sh
bash scripts/init-local-env.sh
~~~

Скрипт создаёт .env с четырьмя **различными** случайными паролями. При уже существующем .env он не меняет данные. Пароли можно посмотреть и при необходимости настроить локально. Переменные:
CHAIRX_CATALOG_PASSWORD, CHAIRX_BOOTSTRAP_ADMIN_PASSWORD,
CHAIRX_DEV_MANAGER_PASSWORD, CHAIRX_DEV_EMPLOYEE_PASSWORD.
Пароли сотрудников должны содержать 12–128 символов; генератор использует криптографически случайные 48-символьные значения.
Файл .env не попадает в GitHub благодаря .gitignore.
Никогда не записывайте пароли в frontend/.env* или VITE_*.

~~~sh
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps
~~~

Адреса: UI http://localhost:5173; backend http://localhost:8080;
PostgreSQL — 127.0.0.1:5432. Vite проксирует /api в контейнер backend.
Docker healthchecks последовательно ждут PostgreSQL, авторизованный
GET /api/csrf и frontend HTTP-сервер.

Для повторного запуска: docker compose up -d.

Для активной разработки: docker compose up --watch.
Compose Watch синхронизирует изменения frontend/src и Vite HMR обновляет
страницу; изменения backend/src или pom.xml инициируют пересборку Docker
образа и перезапуск backend (медленнее HMR). Без watch для изменений
исходников выполните docker compose up -d --build.

## Пользователи и безопасный bootstrap

| Логин | Роль | Способ создания |
|---|---|---|
| admin | ADMIN | Уже существующий AdminBootstrapService — однократно |
| manager | MANAGER | LocalDevUsersService — только в профиле local |
| employee | EMPLOYEE | LocalDevUsersService — только в профиле local |

Технический catalog — отдельный пользователь только для чтения каталога,
его нет в таблице app_users; не используйте его для входа сотрудников.

Seeder включён только при SPRING_PROFILES_ACTIVE=local и явном
CHAIRX_DEV_SEED_USERS=true. Вне local он не регистрируется.
Используются реальные UserManagementService, RoleAssignmentService,
разрешения и аудит; создание обоих сотрудников транзакционное.

Пользователи и пароли остаются в PostgreSQL volume после перезапуска.
Повторное создание не перезаписывает UUID, пароль, роли и активность.
Если пользователь уже существует, но с другой ролью или деактивирован,
инициализация завершится явной ошибкой без исправления данных.

Если администратор в существующей БД называется иначе, dev-bootstrap не
сможет безопасно подменить его; для такой базы требуется ручная настройка
или отдельная тестовая БД. После первого bootstrap замена пароля admin
в .env **не меняет его пароль в PostgreSQL**. Аналогично, DB_PASSWORD
нужно оставить равным паролю ранее инициализированного DB-volume.

В F01 ещё нет формы входа; она появится в F02. Наличие пользователей в
БД не означает, что в UI уже работает авторизация.

## Остановка, диагностика, данные

~~~sh
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=100 frontend
docker compose logs --tail=100 postgres
docker compose down
~~~

docker compose down **сохраняет volume**. Не запускайте docker compose down -v
на базе с ценными данными: команда удаляет volume PostgreSQL.

Если на Mac уже работает отдельный Vite или Spring Boot на тех же портах,
сначала остановите старые процессы. Первый Docker build загрузит Maven
и npm зависимости.

## Проверки

Backend: cd backend && ./mvnw clean verify (Testcontainers, отдельная БД).
Frontend: cd frontend && npm ci && npm run lint && npm run typecheck &&
npm run test && npm run build.
F01.1 Compose smoke: .github/workflows/local-dev-smoke.yml.
