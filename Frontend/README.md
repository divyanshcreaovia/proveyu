# ProveYu frontend

Angular 21 app for all ProveYu portals: public website, college portal, student portal, company Drive Room and platform admin.
Standalone components, signals, OnPush and zoneless change detection, Bootstrap 5 and Bootstrap Icons.

For the whole project (backend, database, AI services, tests) see [../README.md](../README.md) and [../RUN_AND_TEST.md](../RUN_AND_TEST.md).

## Run

```bash
npm install
npm start -- --port 4300      # http://localhost:4300 (port 4200 is used by another local project)
```

The backend must run on `http://localhost:8080` (`src/app/core/environments/environment.ts`). The page reloads when a file changes.

## Build and test

```bash
npx ng build                               # production build into dist/ (uses environment.prod.ts: same-origin API, wss:// WebSocket)
npx ng build --configuration development   # quick build, used by scripts/run_all_tests.sh
npx ng test --watch=false                  # unit tests (Vitest)
```

## Where things are

```text
src/app/
├── core/            auth and token storage, guards (auth, role, guest), interceptors (token, errors, loading), API helpers, models
├── shared/ui/       UI kit: modal / drawer, confirm dialog, tables, status badge, empty / error / skeleton states, toasts, notification panel
└── features/
    ├── public/      website: home, For Colleges, For Students, How It Works, sign-in chooser
    ├── college/     college portal (/college): dashboard, students, companies, drives, interviews, offers, communication,
    │                mock tests, off-campus and NOC requests, reports, team, audit log, settings
    ├── student/     student portal (/student): dashboard, campus drives, off-campus jobs, applications, interviews, offers,
    │                mock tests and test player, AI interview, announcements, chat, documents, profile
    ├── company/     Drive Room for company panels (/company)
    ├── admin/       platform admin portal (/admin): dashboard, colleges, users, admins, payments, off-campus jobs, mock tests,
    │                proctoring, support, broadcast, settings, audit log
    ├── offcampus/   off-campus jobs service shared by the admin, student and college pages
    └── support/     Help & support popup shared by the college and student portals
```

Routes are in `src/app/app.routes.ts`; each portal has its own shell (side menu, header, notification bell) in `features/<portal>/layout`.

## Conventions

- Pages call the backend only through services; components never use `HttpClient` directly.
- Every API call goes through `api(path)` from `core/api.ts`; responses are `ApiResponse<T>` and services return `data`.
- File downloads and previews use `FileService` (the token never goes in a URL).
- Colours come from the theme tokens (`--pm-*`), so light and dark mode both work; layouts are checked down to 390 px width.
