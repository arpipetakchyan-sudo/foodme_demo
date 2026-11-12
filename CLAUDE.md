# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this project is

FoodMe — a small food-ordering app plus a monitoring stack, deployed on Render's
free tier. The root `README.md` is a step-by-step, no-coding deploy guide for
workshop participants; keep its tone simple and beginner-friendly if you edit it.

## Repository layout

| Path | What it is |
|---|---|
| `apps/backend/` | Spring Boot 3.3 API (Java 17, Gradle). Postgres + Flyway migrations, JWT auth, Actuator, Prometheus metrics, JSON logs to Loki, Sentry/GlitchTip. |
| `apps/web/` | Customer storefront — React + TypeScript + Vite, Tailwind, Radix UI, TanStack Query, react-hook-form + zod, i18next, Dexie (cart). |
| `apps/admin/` | Back-office panel (served at `/backoffice`) — React (JSX) + Vite, react-admin, MUI. |
| `infra/monitoring/` | One-container monitoring stack: Grafana + Prometheus + Loki + Grafana MCP behind nginx. See its `README.md`. |
| `render.yaml` | Render blueprint for the app (backend Docker image + Postgres). |
| `render-monitoring.yaml` | Render blueprint for the monitoring service. |
| `.env.example` | Shared env file template (copy to `.env`). |
| `MCP.JSON` | Local MCP server config (Qase). Contains an API token — never commit it or print it. |

## Common commands

Backend (`apps/backend`):
- Build: `./gradlew build`
- Run: `./gradlew bootRun`
- Tests: `./gradlew test`

Web storefront (`apps/web`):
- Install & run: `npm install && npm run dev`
- Build: `npm run build` (runs `tsc -b` first)
- Lint: `npm run lint` (oxlint)
- E2E tests: `npm run test:e2e` (Playwright); `npm run test:e2e:all` also runs admin E2E

Admin panel (`apps/admin`):
- Install & run: `npm install && npm run dev`
- Lint: `npm run lint` (eslint)
- E2E tests: `npm run test:e2e`

VS Code tasks in `.vscode/tasks.json` start the store and the admin panel.

## Conventions and gotchas

- Database schema changes go in new Flyway migrations under
  `apps/backend/src/main/resources/db/migration/` — never edit an existing migration.
- Chef/dish images are stored in Postgres and served at `/api/images/**`;
  seed images live in `apps/backend/src/main/resources/img-seed/`.
- Frontend Sentry DSNs (`VITE_SENTRY_DSN_*`) are read at **build** time by Vite.
- Render free services cannot receive private-network traffic, so service-to-service
  URLs must be public `*.onrender.com` URLs (e.g. `LOKI_PUSH_URL`).
- Free Render services sleep after ~15 min idle; monitoring data is wiped on restart.
- Some E2E specs are intentionally flaky (`flake-*.spec.ts`) — don't "fix" them
  unless asked.
- Never commit real secrets (`.env`, DSNs, tokens). Use `.env.example` for placeholders.
