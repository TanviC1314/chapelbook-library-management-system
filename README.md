# ChapelBook – Library Management & Booking System

> **Status: early development.** This repository is built incrementally, one small reviewed
> commit at a time. Nothing below is claimed as working until it is ticked in
> [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md).

ChapelBook is a full-stack web application that simplifies library operations. Students browse
books, reserve copies, track what they have borrowed and book study seats. Librarians manage the
catalogue, issue and return books and monitor overdue items. Administrators manage users and
see how the library is used.

It is a portfolio project that demonstrates REST API design, database design, authentication and
role-based access control, business logic with real concurrency concerns, and a React frontend.

## Planned features

### Student portal
- Register and log in
- Browse and search books by title, author and category; check availability
- Reserve and cancel book reservations
- View current borrowings, borrowing history, due dates and overdue fines
- Book and cancel library study seats

### Librarian portal
- Add, update and manage books and individual book copies
- Issue books and process returns
- Manage reservations
- Monitor overdue books

### Admin portal
- Manage students and librarians
- View library statistics
- Monitor book circulation and seat bookings

## Planned technology stack

| Area | Technology |
| --- | --- |
| Frontend | React, TypeScript |
| Backend | Java 21, Spring Boot 3, Spring Data JPA, Hibernate, Spring Security with JWT |
| Database | PostgreSQL, Flyway migrations |
| Testing | JUnit 5, Mockito, Spring Boot Test, Testcontainers |
| API documentation | Swagger / OpenAPI |
| Build and delivery | Maven, Docker, Docker Compose, GitHub Actions |

## Architecture (planned)

Layered backend, organised by feature: **Controller → Service → Repository → Database**, with DTOs
at the API boundary and a security layer for JWT authentication and role-based access control. The
React frontend talks to the backend only through the REST API.

## Getting started

The database, authentication, catalogue, reservations, borrowing, fines, seat booking and the admin dashboard data exist so far (Phases 1 to 5). You need JDK 21 and Docker.

```bash
cp .env.example .env        # then set POSTGRES_PASSWORD in .env
docker compose up -d postgres
```

Run the backend tests (they start their own throwaway PostgreSQL through Testcontainers, so Docker must be running):

```bash
cd backend
./mvnw verify
```

To run the API locally, start the database, then set the secrets in your shell (see `.env.example`):

```bash
cd backend
export DB_PASSWORD=<your POSTGRES_PASSWORD> JWT_SECRET=<at least 32 characters>
export BOOTSTRAP_ADMIN_EMAIL=admin@example.com BOOTSTRAP_ADMIN_PASSWORD=<min 8 characters>   # optional first admin
./mvnw spring-boot:run
```

Implemented endpoints so far:

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

Account safety rules: student accounts always stay students; an admin cannot deactivate or demote themselves; the system
always keeps at least one active admin, even when two admins act on each other at the same instant. A deactivated user
is locked out on their next request. A password reset does not revoke tokens already issued (they last one hour).

Library rules (all configurable under `app.library` in `application.yml`): 14-day loans, 5 books at once, 2 renewals of
14 days (not when overdue or when someone is waiting), a fine of 0.25 for each started day late capped at 10.00 per loan,
no new loans while a book is overdue or unpaid fines are above 5.00, seat bookings of 15 minutes to 8 hours up to
14 days ahead, 3 upcoming per student. A seat can never be double booked: the database enforces it with an exclusion
constraint, and a student's bookings are serialised so concurrent requests cannot deadlock it.

How reservations work: each title has a first-come first-served queue. If a copy is on the shelf the student is
held it for three days; otherwise they join the queue. A returned, added, cancelled or expired copy goes to the next
student in line. Every queue change locks that title first, so concurrent requests cannot hand out a copy twice.

The database design, with its ER diagram and the rules PostgreSQL enforces itself (no double-booked seats, one
open loan per copy), is in [docs/DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md).

## Disclaimer

A learning and portfolio project using simulated data only. Not security audited and not
production ready.
