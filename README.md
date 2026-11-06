# LibraryManager

A Kotlin study project to build a **library inventory manager** with a REST API, user authentication, and a server-rendered web UI.

## Overview

**Status:** the REST API, JWT authentication with USER/ADMIN roles, account-linked loans and the web UI (login, books, authors, loans, users) are complete and covered by 416 passing tests. Remaining work is limited to minor follow-ups listed below.

The project follows a layered architecture (model → repository → service → HTTP handler), with SQLite persistence, JWT-based authentication, and a comprehensive unit-test suite.

---

## Features

### ✅ Implemented

- **Books** — full CRUD (`GET`, `POST`, `PUT`, `DELETE`) with category validation and author auto-creation; stock is changed only through `PATCH /books/{id}/quantity` and can never go negative; writes are ADMIN-only
- **Authors** — full CRUD; writes are ADMIN-only
- **Loans** — linked to user accounts (`borrower` = `{id, name, email}`); borrow, list, get by id, register return (ADMIN-only); tracks availability and overdue status (`isOverdue`, `daysOverdue`); borrowing is refused while the borrower has an overdue loan
- **Roles** — `USER` and `ADMIN`; sign-up always creates `USER`; admins are bootstrapped from `ADMIN_EMAILS` and managed via `PATCH /users/{id}/role`; at least one admin always remains
- **Referential integrity** — SQLite foreign keys are enforced; users/books with any loans and authors with books cannot be deleted (`409`)
- **Concurrency safety** — books and loans use optimistic locking (`version` column); conflicting writes are retried up to 3 times, then answered with `409`
- **Users** — create, list (ADMIN), get by email, update profile, change password (self-service, requires current password), delete (ADMIN)
- **Authentication** — `POST /auth/login` returns a signed JWT token; all REST data endpoints are protected by `authFilter`, which re-reads the account (and its role) from the database on every request
- **Pagination** — all list endpoints support `page` and `size` query params
- **Filtering** — books by title/category/author, authors by name, loans by borrower name/`userId`/bookId/active status, users by name
- **Web UI** — full set of Handlebars-rendered pages (`/ui/login`, `/ui/books`, `/ui/authors`, `/ui/loans`, `/ui/users`), each with search, pagination, and role-aware actions wired to the REST API via vanilla JS (`static/js/*.js`); the JWT is stored in `localStorage` and attached automatically to every API call
- **Unit tests** — handler, service, model, migration, UI-template and SQLite repository tests for all entities, plus deterministic race tests (416 tests)

### 🔲 Known follow-ups

See [UI_GUIDE.md](UI_GUIDE.md#known-limitations--follow-ups-worth-doing-next) for details:

- No self-service sign-up page (`POST /users` is API-only for now)
- Loan creation loads all books into a single `<select>` (fine for a small catalog, not paginated)
- Pagination in the UI is simple Prev/Next (no numbered pages or page-size selector)


---

## Authentication

All endpoints except `POST /users`, `POST /auth/login` and the `/ui/*` + `/static/*` web pages require a valid JWT token.

**Register a user:**
```http
POST /users
Content-Type: application/json

{
  "name": "Alice",
  "email": "alice@example.com",
  "passwordHash": "mypassword"
}
```

**Login:**
```http
POST /auth/login
Content-Type: application/json

{
  "email": "alice@example.com",
  "password": "mypassword"
}
```

Response:
```json
{
  "token": "eyJ...",
  "user": { "id": 1, "name": "Alice", "email": "alice@example.com", "role": "USER" }
}
```

**Use the token in subsequent requests:**
```http
GET /books
Authorization: Bearer eyJ...
```

`GET /auth/me` returns the signed-in account (`UserResponse`, including its current `role`). The token only identifies the account; the role is always read from the database, so promotions, demotions and deletions take effect on the next request. A request body field `"role"` on sign-up is ignored.

---

## Roles and permissions

| Action | USER | ADMIN |
| --- | --- | --- |
| Read books/authors | Allowed | Allowed |
| Write books/authors/stock | Forbidden | Allowed |
| List/get loans | Own only | All |
| Borrow | Self only | Self or another account |
| Return | Forbidden | Allowed |
| List users | Forbidden | Allowed |
| Get user by email / update profile | Own only | Any |
| Change password | Own, with current password | Own, with current password |
| Set role / delete user | Forbidden | Allowed (guards below) |
| Register/login | Public; role cannot be requested | Public |

Forbidden actions return `403`. A USER asking for someone else's email gets `403` whether or not it exists. The UI hides controls a role cannot use, but the server is the authorization boundary.

**Bootstrapping admins:** at startup, `ADMIN_EMAILS` (comma-separated) promotes matching *existing* accounts to ADMIN. It never creates accounts or demotes anyone; unknown emails are printed.

```bash
export ADMIN_EMAILS="alice@example.com,bob@example.com"
./gradlew run
```

**Guards (`409`):** demoting or deleting the last ADMIN; deleting a user who has any loans (returned ones included).

---

## REST API

### Auth
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/auth/login` | ❌ | Login — returns JWT token and `UserResponse` (with `role`) |
| GET | `/auth/me` | ✅ | Current account (`UserResponse`) — the UI uses it to refresh the role |

### Users
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/users` | ❌ | Create user (always `USER`) |
| GET | `/users` | ADMIN | List users (paginated, filter by `name`) — returns `UserResponse` (no password hash) |
| GET | `/users/email?email=` | ✅ | Get user by email — own account only unless ADMIN |
| PUT | `/users/{id}` | ✅ | Update name/email (`UpdateUserRequest`) — own account only unless ADMIN |
| PATCH | `/users/{id}/password` | ✅ | Change own password; requires the current password (`ChangePasswordRequest`) |
| PATCH | `/users/{id}/role` | ADMIN | `{"role": "ADMIN" \| "USER"}`; invalid role → `400`; demoting the last admin → `409` |
| DELETE | `/users/{id}` | ADMIN | Delete user; last admin or user with any loans → `409` |

### Books
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/books` | ✅ | List books (paginated, filter by `title`, `category`, `author`) |
| GET | `/books/{id}` | ✅ | Get book by id |
| POST | `/books` | ADMIN | Create book (auto-creates author if it doesn't exist); negative `quantity` → `400` |
| PUT | `/books/{id}` | ADMIN | Update book details only (`title`, `author`, `category`, `publishedYear`, `edition`); sending `quantity` → `400` |
| PATCH | `/books/{id}/quantity` | ADMIN | Change stock by a relative amount: `{"change": n}` (may be negative or 0); result below zero → `409` |
| DELETE | `/books/{id}` | ADMIN | Delete book; book with any loans → `409` |

### Authors
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/authors` | ✅ | List authors (paginated, filter by `name`) |
| GET | `/authors/{id}` | ✅ | Get author by id |
| POST | `/authors` | ADMIN | Create author |
| PUT | `/authors/{id}` | ADMIN | Update author |
| DELETE | `/authors/{id}` | ADMIN | Delete author; author with books → `409` |

### Loans
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/loans` | ✅ | List loans (paginated, filter by `borrower` name, exact `userId`, `bookId`, `onlyActive`); a USER always gets only their own loans, whatever `userId` they send |
| GET | `/loans/{id}` | ✅ | Get loan by id; another user's loan → `403` for a USER |
| POST | `/loans` | ✅ | Borrow: `{"bookId": 1, "userId": 2, "daysToReturn": 14}`; omit `userId` to borrow for yourself; a USER naming another account → `403`; unknown account → `404`; borrower with an overdue loan → `409`; a negative `daysToReturn` is allowed and creates an already-overdue loan |
| PATCH | `/loans/{id}/return` | ADMIN | Register return |

### Concurrency and response fields

- `Book` and `Loan` responses include a technical `version` field; clients never send it.
- `Loan` responses include `borrower` (`{"id", "name", "email"}` — never the password hash), `isOverdue` and `daysOverdue` (`0` when not yet due, due today, or returned).
- When a concurrent write keeps winning after 3 attempts, book and loan endpoints return `409` with a "modified concurrently, try again" message; retrying the request is safe.
- Borrow and return are two separate writes (loan, then stock). A crash between them can under-count stock; it is never over-counted.
- Foreign keys are enforced on every app connection. Delete guards are checked in the services; a reference that races in between the check and the delete still yields the same `409`.

### Database migrations

On startup `DatabaseFactory.init()` runs `DatabaseMigrations` before creating tables, in a single transaction with foreign keys off:

- adds `version` to `books` and `loans` (default `0`) and `role` to `users` (default `USER`);
- rebuilds `loans` to replace `borrower_name` with `user_id` (a foreign key to `users`). Each borrower name is matched to exactly one account by name (Unicode-normalized, trimmed, case-insensitive). If any loan matches no account or several, or references a missing book, startup fails with a message listing those loan ids, and **nothing is changed**. Fix the names (or accounts) and restart. Loan ids and the id counter are preserved.

Fresh databases skip every step. **Back up `library.db` before first running a new version.**

### Web UI
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/ui/login` | ❌ | Login page |
| GET | `/ui/books` | ❌* | Books list/search; create/edit/stock/delete for ADMIN |
| GET | `/ui/authors` | ❌* | Authors list/search; create/edit/delete for ADMIN |
| GET | `/ui/loans` | ❌* | Loans list (own loans for USER), borrow form (account picker for ADMIN), return action for ADMIN |
| GET | `/ui/users` | ❌* | USER: own profile + change password. ADMIN: users list/search with role toggle and delete |
| GET | `/static/*` | ❌ | Serves static CSS/JS assets |

> \* The HTML shell itself isn't behind `authFilter` (a plain browser navigation never sends the `Authorization` header; see `UI_GUIDE.md`), but each page's JS calls `requireAuth()` and `refreshCurrentUser()` (`GET /auth/me`) on load, and all of its data comes from the already-protected REST endpoints above.

### Valid Book Categories
`Fiction`, `Non-Fiction`, `Science Fiction`, `Fantasy`, `Horror`, `Mystery`, `Biography`, `History`, `Science`, `Technology`

---

## Technologies

| Technology | Version | Role |
|---|---|---|
| Kotlin | 2.3.0 | Main language |
| Gradle (Kotlin DSL) | Wrapper | Build tool |
| JDK | 17 | Runtime |
| http4k-core | 5.35.0.0 | HTTP framework (functional, lenses) |
| http4k-format-jackson | 5.35.0.0 | JSON serialization |
| http4k-server-jetty | 5.35.0.0 | Embedded HTTP server |
| http4k-template-handlebars | 5.35.0.0 | Server-side HTML templating (UI) |
| Jetbrains Exposed | 0.57.0 | SQL DSL / ORM |
| SQLite JDBC | 3.47.1.0 | Embedded database |
| jbcrypt | 0.4 | Password hashing (BCrypt) |
| java-jwt (Auth0) | 4.4.0 | JWT generation and validation |
| Kotlin Test | — | Test framework (JUnit 5) |

---

## Folder Structure

```
LibraryManager/
├── library.db                              # SQLite database (auto-created on first run)
└── src/
    ├── main/
    │   ├── kotlin/
    │   │   ├── Main.kt                         # Entry point — starts HTTP server on :8080
    │   │   ├── database/
    │   │   │   ├── DatabaseFactory.kt              # Connects with foreign keys ON per connection
    │   │   │   ├── DatabaseMigrations.kt           # Idempotent startup migrations (one transaction)
    │   │   │   ├── LoansUserIdMigration.kt         # Rebuilds loans: borrower_name -> user_id FK
    │   │   │   ├── MigrationException.kt
    │   │   │   ├── AuthorTable.kt
    │   │   │   ├── BookTable.kt
    │   │   │   ├── LoanTable.kt
    │   │   │   └── UserTable.kt
    │   │   ├── http/
    │   │   │   ├── AuthFilter.kt               # authFilter (JWT + DB lookup -> Actor), requireAdmin, actorLens
    │   │   │   ├── AuthorHandler.kt            # Author handler + authorRoutes
    │   │   │   ├── BookDetailsRequest.kt       # PUT body (details only)
    │   │   │   ├── BookRequest.kt
    │   │   │   ├── BookRoutes.kt               # Book handler + bookRoutes
    │   │   │   ├── ChangePasswordRequest.kt
    │   │   │   ├── HandlerUtils.kt             # Exception -> status mapping
    │   │   │   ├── LoanRequest.kt              # { bookId, userId?, daysToReturn }
    │   │   │   ├── LoanRoutes.kt               # Loan handler + loanRoutes
    │   │   │   ├── LoginRequest.kt
    │   │   │   ├── QuantityChangeRequest.kt    # PATCH stock body { change }
    │   │   │   ├── RoleChangeRequest.kt        # PATCH role body { role }
    │   │   │   ├── UiHandler.kt                # Renders Handlebars templates
    │   │   │   ├── UiRoutes.kt                 # /ui/* and /static/* routes
    │   │   │   ├── UpdateUserRequest.kt
    │   │   │   └── UserHandler.kt              # User handler + userRoutes/publicUserRoutes (+ /auth/me)
    │   │   ├── model/
    │   │   │   ├── Actor.kt                    # Authenticated user id + role (from the DB)
    │   │   │   ├── Author.kt
    │   │   │   ├── AuthorExceptions.kt
    │   │   │   ├── AuthorFilter.kt
    │   │   │   ├── AuthorizationExceptions.kt  # ForbiddenException (403)
    │   │   │   ├── Book.kt
    │   │   │   ├── BookCategory.kt
    │   │   │   ├── BookExceptions.kt
    │   │   │   ├── BookFilter.kt
    │   │   │   ├── Borrower.kt                 # Loan's safe view of a user
    │   │   │   ├── Loan.kt
    │   │   │   ├── LoanExceptions.kt
    │   │   │   ├── LoanFilter.kt
    │   │   │   ├── LoginResponse.kt
    │   │   │   ├── PagedResult.kt
    │   │   │   ├── Role.kt
    │   │   │   ├── User.kt
    │   │   │   ├── UserExceptions.kt
    │   │   │   ├── UserFilter.kt
    │   │   │   └── UserResponse.kt
    │   │   ├── repository/
    │   │   │   ├── AuthorRepository.kt
    │   │   │   ├── BookRepository.kt
    │   │   │   ├── ConcurrentUpdateException.kt # Stale optimistic-lock write
    │   │   │   ├── LoanRepository.kt
    │   │   │   ├── ReferencedRowException.kt    # Foreign-key violation on delete/insert
    │   │   │   ├── SQLiteAuthorRepository.kt
    │   │   │   ├── SQLiteBookRepository.kt
    │   │   │   ├── SQLiteLoanRepository.kt
    │   │   │   ├── SQLiteUserRepository.kt
    │   │   │   └── UserRepository.kt
    │   │   ├── security/
    │   │   │   └── JwtService.kt               # JWT generation & validation
    │   │   └── service/
    │   │       ├── AuthorService.kt
    │   │       ├── BookService.kt
    │   │       ├── IAuthorService.kt
    │   │       ├── IBookService.kt
    │   │       ├── ILoanService.kt
    │   │       ├── IUserService.kt
    │   │       ├── LoanService.kt
    │   │       ├── RetryOnConflict.kt          # Retries optimistic-lock conflicts (3 attempts)
    │   │       └── UserService.kt
    │   └── resources/
    │       ├── templates/                      # Handlebars templates (UI)
    │       │   ├── layout.hbs                       # base layout (partial-block pattern)
    │       │   ├── header.hbs                       # navbar partial (active-link state)
    │       │   ├── footer.hbs                       # shared api.js include
    │       │   ├── login.hbs
    │       │   ├── books.hbs
    │       │   ├── authors.hbs
    │       │   ├── loans.hbs
    │       │   └── users.hbs
    │       └── static/
    │           ├── css/
    │           │   └── style.css
    │           └── js/
    │               ├── api.js                       # apiFetch, refreshCurrentUser, isAdmin, qs, escapeHtml, requireAuth, logout
    │               ├── login.js
    │               ├── books.js
    │               ├── authors.js
    │               ├── loans.js
    │               └── users.js
    └── test/kotlin/                           # see the directory for the full list; highlights:
        ├── database/
        │   ├── DatabaseFactoryForeignKeysTest.kt
        │   └── DatabaseMigrationsTest.kt       # incl. the loans rebuild (Unicode, abort, rollback)
        ├── http/
        │   ├── ActorFixtures.kt                # handler.asActor(anAdmin / aUser)
        │   ├── AuthFilterTest.kt
        │   ├── AuthorHandlerTest.kt
        │   ├── BookHandlerTest.kt
        │   ├── CatalogAuthorizationTest.kt
        │   ├── ConcurrentUpdateHandlingTest.kt
        │   ├── LoanHandlerTest.kt
        │   ├── RequireAdminTest.kt
        │   ├── UiRoutesTest.kt
        │   ├── UserAuthorizationTest.kt
        │   ├── UserHandlerTest.kt
        │   └── UserRoleHandlerTest.kt
        ├── model/
        │   ├── BookTest.kt
        │   └── LoanTest.kt
        ├── repository/                     # SQLite + in-memory repos, Hooked* race doubles
        └── service/                        # service rules, authorization, last-admin and FK race tests
```

---

## Prerequisites

- JDK 17
- `JWT_SECRET` environment variable set
- Optional: `ADMIN_EMAILS` (comma-separated) to promote existing accounts to ADMIN at startup

## How to Run

**macOS/Linux:**

```bash
export JWT_SECRET="your-secret-key"
./gradlew run
```

The API will be available at `http://localhost:8080`.

**Run tests:**

```bash
JWT_SECRET=your-secret-key ./gradlew test
```

**Windows (PowerShell):**

```powershell
$env:JWT_SECRET = "your-secret-key"
.\gradlew.bat run
.\gradlew.bat test
```

> ⚠️ The application will fail to start if `JWT_SECRET` is not set.

---

## Running the Web UI Locally

The web UI is served by the very same embedded server as the REST API — there's no separate frontend process, dev server, or build step to run.

1. **Start the server** (see [How to Run](#how-to-run) above):
   ```bash
   export JWT_SECRET="your-secret-key"
   ./gradlew run
   ```
   Wait for the `LibraryManager running on http://localhost:8080` line in the console.

2. **Create a user.** There's no sign-up page yet (see [Known follow-ups](#-known-follow-ups)), so register your first account directly against the API, e.g. with `curl`:
   ```bash
   curl -X POST http://localhost:8080/users \
     -H "Content-Type: application/json" \
     -d '{"name":"Alice","email":"alice@example.com","passwordHash":"mypassword"}'
   ```
   (`passwordHash` here is just the request field name — send the plain password; the server hashes it with BCrypt before storing it.) New accounts are always `USER`.

3. **Make yourself an admin** (needed for catalog writes, returns and user management): restart with your email in `ADMIN_EMAILS`:
   ```bash
   ADMIN_EMAILS="alice@example.com" ./gradlew run
   ```

4. **Open the login page** in your browser:
   ```
   http://localhost:8080/ui/login
   ```
   Sign in with the email/password you just registered. On success you're redirected to `/ui/books`.

5. **Navigate the app** using the navbar — **Books**, **Authors**, **Loans**, **Users**. Every page supports search/filtering and pagination; actions your role can't perform are hidden (each page re-reads your role from `GET /auth/me` on load). The JWT is kept in the browser's `localStorage` and attached automatically to each API call; use the **Logout** link (or clear `localStorage`) to end the session.

> 💡 Both Handlebars templates (`src/main/resources/templates/*.hbs`) and static JS/CSS (`src/main/resources/static/**`) are served straight from disk with hot-reload — editing them and refreshing the browser is enough to see changes, no server restart needed.

See [UI_GUIDE.md](UI_GUIDE.md) for the full page-by-page implementation guide.

---

## Contributing

1. Fork the repository.
2. Create a branch for your feature (`git checkout -b feat/my-feature`).
3. Commit your changes (`git commit -m "feat: describe the feature"`).
4. Push to your fork (`git push origin feat/my-feature`).
5. Open a Pull Request.

## License

MIT
