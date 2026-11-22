# ChapelBook – Library Management & Booking System

[![CI](https://github.com/TanviC1314/chapelbook-library-management-system/actions/workflows/ci.yml/badge.svg)](https://github.com/TanviC1314/chapelbook-library-management-system/actions/workflows/ci.yml)

ChapelBook is a full-stack web application that simplifies library operations. Students browse books, reserve
copies, track what they have borrowed and book study seats. Librarians manage the catalogue, issue and return books
and monitor overdue items. Administrators manage users and see how the library is used.

It is a portfolio project that demonstrates REST API design, database design, authentication and role-based access
control, business logic with real concurrency concerns, and a React frontend. Build progress is tracked in
[PROJECT_PROGRESS.md](PROJECT_PROGRESS.md).

## What it does

**Students** register, search the catalogue by title, author or category, see live availability, reserve a title (a
free copy is held for three days, otherwise they join a first-come first-served queue), watch loans, due dates and
fines, renew loans, and book and cancel study seats.

**Librarians** manage titles, categories and individual copies (by barcode), issue and return books at a desk screen
that works with a barcode scanner, and handle reservations, overdue loans, fines and seats.

**Administrators** also manage accounts (create librarians, deactivate, reset passwords), see a dashboard, and run
circulation and seat-usage reports for any period up to a year.

## Technology

| Area | Technology |
| --- | --- |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS, TanStack Query, React Hook Form + zod, Recharts |
| Backend | Java 21, Spring Boot 3.5, Spring Data JPA / Hibernate, Spring Security with JWT |
| Database | PostgreSQL 16, Flyway migrations |
| Testing | JUnit 5, Mockito, Spring Boot Test, Testcontainers; Vitest and Testing Library |
| API documentation | OpenAPI 3.1 and Swagger UI (springdoc) |
| Delivery | Maven, Docker, Docker Compose, nginx, GitHub Actions |

## Architecture

```
 browser ──► nginx (frontend container) ──┬─► static React app
                  port 8081               └─► /api, /v3/api-docs, /swagger-ui ──► Spring Boot ──► PostgreSQL
```

The backend is layered by feature: **Controller → Service → Repository → Database**, with DTOs at the API boundary and a
stateless JWT security layer. Who may call what is declared with `@PreAuthorize` next to the code it protects. The
browser only ever talks to one origin (nginx in Docker, the Vite dev server locally), so no CORS configuration exists
and the backend is not exposed directly.

Decisions worth knowing about:

- **PostgreSQL enforces the hard rules itself.** A seat cannot be double booked and a student cannot hold two
  overlapping bookings (exclusion constraints), a copy has at most one open loan, and a student has at most one open
  reservation per title (partial unique indexes). The
  application logic sits on top; the database is the last line of defence. See
  [docs/DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md) for the ER diagram.
- **Concurrency is handled, then tested.** Every change to a title's reservation queue locks the title row first, then
  the copy (always in that order, so locks cannot deadlock); a student's seat bookings are serialised; concurrent
  admin demotions cannot leave the system without an admin. The concurrency tests were checked by removing each lock
  or constraint and watching the test fail.
- **Flyway owns the schema.** Hibernate only validates it (`ddl-auto=validate`).
- **Errors are RFC 9457 problem details**, with a list of bad fields for validation errors.
- **The token is not trusted for permissions.** The role and active flag are read from the database on every request,
  so a deactivated or demoted user loses access on their very next call.

## Run everything with Docker

You need Docker. No Java or Node is required.

```bash
cp .env.example .env
```

Edit `.env` and set the three values Compose will not start without (there are no default secrets on purpose):

| Variable | What to put |
| --- | --- |
| `POSTGRES_PASSWORD` | any strong password |
| `JWT_SECRET` | at least 32 characters, for example `openssl rand -base64 48` |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | the first administrator (password: 8+ characters). Optional, but without it there is no way to create librarians |

```bash
docker compose up -d --build
```

Open <http://localhost:8081>. Sign in with the administrator, create a librarian under **Users**, and register a
student from the sign-in page. API documentation is at <http://localhost:8081/swagger-ui.html>.

```bash
docker compose logs -f backend   # watch the API start (Flyway migrates the database on first start)
docker compose down              # stop, keeping the data
docker compose down -v           # stop and delete the database
```

The web port can be changed with `WEB_PORT`. PostgreSQL is published on `127.0.0.1:5433` only.

## Develop locally

You need JDK 21, Node 20+ and Docker.

```bash
docker compose up -d postgres      # just the database
```

Backend (set the secrets in your shell, see `.env.example`):

```bash
cd backend
export DB_PASSWORD=<your POSTGRES_PASSWORD> JWT_SECRET=<at least 32 characters>
export BOOTSTRAP_ADMIN_EMAIL=admin@example.com BOOTSTRAP_ADMIN_PASSWORD=<min 8 characters>   # optional first admin
./mvnw spring-boot:run             # http://localhost:8080, Swagger UI at /swagger-ui.html
```

Frontend (Vite proxies `/api` to the backend):

```bash
cd frontend
npm install
npm run dev                        # http://localhost:5173
```

If the backend is on another port, set `VITE_API_PROXY_TARGET` (for example `http://localhost:8085`).

## Tests

```bash
cd backend && ./mvnw verify        # unit tests, then integration tests on a real PostgreSQL (Docker must be running)
cd frontend && npm test            # Vitest + Testing Library
cd frontend && npm run lint && npm run build   # lint, then type-check and bundle
```

The backend has 288 automated tests; integration tests start a throwaway PostgreSQL 16 through Testcontainers rather
than using an in-memory substitute, and a JaCoCo coverage report is written to `backend/target/site/jacoco`. The
frontend has 311. GitHub Actions runs all of the above on every push and pull request, then builds the Docker images,
starts the whole stack and smoke-tests it through the web port (see [.github/workflows/ci.yml](.github/workflows/ci.yml)).

## Deploying

The Compose file is a complete single-server deployment. For a real server:

1. **Put HTTPS in front.** The nginx container speaks plain HTTP. Terminate TLS with a reverse proxy (Caddy, Traefik,
   or your host's load balancer) that forwards to `127.0.0.1:WEB_PORT`. Passwords and tokens must not cross the
   internet unencrypted.
2. **Use real secrets.** A fresh `POSTGRES_PASSWORD` and `JWT_SECRET`, kept out of version control. Changing
   `JWT_SECRET` signs everybody out.
3. **Create the first admin once**, then remove `BOOTSTRAP_ADMIN_*` from `.env` and run `docker compose up -d` again.
   The bootstrap only runs when no admin exists, but there is no reason to keep the password lying around.
4. **Decide whether to publish the API docs.** They are public by default; set `SPRINGDOC_ENABLED=false` to switch
   off `/v3/api-docs` and Swagger UI.
5. **Back up the database.** All data is in the `chapelbook-pgdata` volume. For example:
   `docker compose exec postgres pg_dump -U chapelbook chapelbook > backup.sql`
6. **Upgrade** with `git pull && docker compose up -d --build`. Flyway applies any new migrations on startup.

Not included: automatic TLS certificates, log shipping, monitoring dashboards or a managed database. The backend has a
`/actuator/health` endpoint for whatever monitoring you use.

## API

Interactive documentation: Swagger UI at `/swagger-ui.html`, the OpenAPI document at `/v3/api-docs`. Each operation
lists the role it requires (generated from the same annotations that enforce it) and the errors it can return. To try
it, call `POST /api/v1/auth/login`, copy the `accessToken`, press **Authorize** and paste it.

| Endpoint | Who |
| --- | --- |
| `POST /api/v1/auth/register` (creates a student) | anyone |
| `POST /api/v1/auth/login` | anyone |
| `GET /api/v1/users/me` | any signed-in user |
| `GET /api/v1/users` (search `q`, filter `role` and `active`, paged), `GET /api/v1/users/{id}` | librarian, admin |
| `PATCH /api/v1/users/{id}` (rename, activate or deactivate, LIBRARIAN to ADMIN and back), `POST /api/v1/users/{id}/password` | admin |
| `POST /api/v1/users` (create an account with a role) | admin |
| `GET /api/v1/categories` | any signed-in user |
| `POST`, `PUT`, `DELETE /api/v1/categories` | librarian, admin |
| `GET /api/v1/books` (search `q`, `categoryId`, `availableOnly`, `sort`, paging), `GET /api/v1/books/{id}` | any signed-in user |
| `POST`, `PUT`, `DELETE /api/v1/books` | librarian, admin |
| `GET`, `POST /api/v1/books/{id}/copies`, `PATCH /api/v1/copies/{id}` | librarian, admin |
| `POST /api/v1/reservations`, `GET /api/v1/reservations/mine` | student |
| `GET /api/v1/reservations` (filter by status) | librarian, admin |
| `GET`, `DELETE /api/v1/reservations/{id}` (cancel) | owner, or librarian and admin |
| `POST /api/v1/loans` (lend a copy by barcode), `POST /api/v1/loans/return` | librarian, admin |
| `GET /api/v1/loans/mine`, `GET`, `POST /api/v1/loans/{id}`, `/{id}/renew` | student (own); staff for any |
| `GET /api/v1/loans` (scope `ALL`/`ACTIVE`/`OVERDUE`, `userId`) | librarian, admin |
| `GET /api/v1/fines/mine`, `/mine/balance` | student |
| `GET /api/v1/fines`, `POST /api/v1/fines/{id}/pay` | librarian, admin |
| `POST /api/v1/fines/{id}/waive` | admin |
| `GET /api/v1/seats` (zone, free-in-window `from`/`to`) | any signed-in user |
| `POST`, `PUT /api/v1/seats` | librarian, admin |
| `POST /api/v1/seat-bookings`, `GET /api/v1/seat-bookings/mine` | student |
| `DELETE /api/v1/seat-bookings/{id}`, `GET /api/v1/seat-bookings` | owner or staff; list is staff |
| `GET /api/v1/statistics/overview` (live counts) | librarian, admin |
| `GET /api/v1/statistics/circulation`, `GET /api/v1/statistics/seats` (`from`, `to` UTC dates, default last 30 days, max 366) | admin |

## Rules the system enforces

Account safety: student accounts always stay students; an admin cannot deactivate or demote themselves; the system
always keeps at least one active admin, even when two admins act on each other at the same instant. A deactivated user
is locked out on their next request.

Library policy (all configurable under `app.library` in `application.yml`): 14-day loans, 5 books at once, 2 renewals of
14 days (not when overdue or when someone is waiting), a fine of 0.25 for each started day late capped at 10.00 per loan,
no new loans while a book is overdue or unpaid fines are above 5.00, seat bookings of 15 minutes to 8 hours up to
14 days ahead, 3 upcoming per student.

Reservations: each title has a first-come first-served queue. If a copy is on the shelf the student is held it for
three days; otherwise they join the queue. A returned, added, cancelled or expired copy goes to the next student in
line. Every queue change locks that title first, so concurrent requests cannot hand out a copy twice.

## Known limitations

- **Resetting a password does not revoke tokens already issued**; they expire after one hour.
- **Fines are paid at the desk.** There is no online payment. Amounts are shown in euros (a constant in
  `frontend/src/lib/format.ts`).
- **The access token is kept in the browser's `localStorage`**, so a cross-site scripting bug would expose it. There are
  no refresh tokens: after an hour you sign in again.
- **No email.** There are no password-reset or reminder emails; an admin resets passwords.
- **Report days are UTC days.** Times are stored in UTC and the app shows them in the viewer's own time zone, but the
  daily buckets in the reports follow UTC.
- **Accessibility and phones are only partly checked.** Automated tests use roles and labels, and every chart has a
  hidden data table, but there has been no screen-reader testing, and the layout was only checked by hand at desktop
  width.

## Disclaimer

A learning and portfolio project using simulated data only. Not security audited and not production ready.
