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

The database, entities and authentication exist so far (Phases 1 and 2). You need JDK 21 and Docker.

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
| `GET /api/v1/users` (filter by role, paged) | librarian, admin |
| `POST /api/v1/users` (create an account with a role) | admin |

The database design, with its ER diagram and the rules PostgreSQL enforces itself (no double-booked seats, one
open loan per copy), is in [docs/DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md).

## Disclaimer

A learning and portfolio project using simulated data only. Not security audited and not
production ready.
