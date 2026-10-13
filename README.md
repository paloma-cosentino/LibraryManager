# LibraryManager

A Kotlin study project to build a **library inventory manager** with a REST API, user authentication, and a server-rendered web UI.

## Overview

**Status:** the REST API, JWT authentication and the web UI (login, books, authors, loans, users) are complete and covered by 206 passing tests. Remaining work is limited to minor follow-ups listed below.

The project follows a layered architecture (model → repository → service → HTTP handler), with SQLite persistence, JWT-based authentication, and a comprehensive unit-test suite.

---

## Features

### ✅ Implemented

- **Books** — full CRUD (`GET`, `POST`, `PUT`, `DELETE`) with category validation and author auto-creation
- **Authors** — full CRUD
- **Loans** — create loan (borrow), list, get by id, register return; tracks availability and overdue status
- **Users** — create, list, get by email, update profile, change password (self-service, requires current password), delete
- **Authentication** — `POST /auth/login` returns a signed JWT token; all REST data endpoints are protected by `AuthFilter`
- **Pagination** — all list endpoints support `page` and `size` query params
- **Filtering** — books by title/category/author, authors by name, loans by borrower/bookId/active status, users by name
- **Web UI** — full set of Handlebars-rendered pages (`/ui/login`, `/ui/books`, `/ui/authors`, `/ui/loans`, `/ui/users`), each with search, pagination, and create/edit/delete actions wired to the REST API via vanilla JS (`static/js/*.js`); the JWT is stored in `localStorage` and attached automatically to every API call
- **Unit tests** — handler, service and SQLite repository tests for all entities (206 tests)

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
  "user": { "id": 1, "name": "Alice", "email": "alice@example.com" }
}
```

**Use the token in subsequent requests:**
```http
GET /books
Authorization: Bearer eyJ...
```

---

## REST API

### Auth
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/auth/login` | ❌ | Login — returns JWT token |

### Users
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/users` | ❌ | Create user |
| GET | `/users` | ✅ | List users (paginated, filter by `name`) — returns `UserResponse` (no password hash) |
| GET | `/users/email?email=` | ✅ | Get user by email — returns `UserResponse` |
| PUT | `/users/{id}` | ✅ | Update user's name/email (`UpdateUserRequest`) |
| PATCH | `/users/{id}/password` | ✅ | Change password; requires the current password (`ChangePasswordRequest`) |
| DELETE | `/users/{id}` | ✅ | Delete user |

### Books
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/books` | ✅ | List books (paginated, filter by `title`, `category`, `author`) |
| GET | `/books/{id}` | ✅ | Get book by id |
| POST | `/books` | ✅ | Create book (auto-creates author if it doesn't exist) |
| PUT | `/books/{id}` | ✅ | Update book |
| DELETE | `/books/{id}` | ✅ | Delete book |

### Authors
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/authors` | ✅ | List authors (paginated, filter by `name`) |
| GET | `/authors/{id}` | ✅ | Get author by id |
| POST | `/authors` | ✅ | Create author |
| PUT | `/authors/{id}` | ✅ | Update author |
| DELETE | `/authors/{id}` | ✅ | Delete author |

### Loans
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/loans` | ✅ | List loans (paginated, filter by `borrower`, `bookId`, `onlyActive`) |
| GET | `/loans/{id}` | ✅ | Get loan by id |
| POST | `/loans` | ✅ | Borrow a book (creates a new loan) |
| PATCH | `/loans/{id}/return` | ✅ | Register return |

### Web UI
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/ui/login` | ❌ | Login page |
| GET | `/ui/books` | ❌* | Books list/search/create/edit/delete |
| GET | `/ui/authors` | ❌* | Authors list/search/create/edit/delete |
| GET | `/ui/loans` | ❌* | Loans list/search, borrow form, return action |
| GET | `/ui/users` | ❌* | Users list/search/edit/delete, plus self-service change password |
| GET | `/static/*` | ❌ | Serves static CSS/JS assets |

> \* The HTML shell itself isn't behind `AuthFilter` (a plain browser navigation never sends the `Authorization` header; see `UI_GUIDE.md`), but each page's JS calls `requireAuth()` on load and all of its data comes from the already-protected REST endpoints above.

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
    │   │   │   ├── DatabaseFactory.kt
    │   │   │   ├── AuthorTable.kt
    │   │   │   ├── BookTable.kt
    │   │   │   ├── LoanTable.kt
    │   │   │   └── UserTable.kt
    │   │   ├── http/
    │   │   │   ├── AuthFilter.kt               # JWT middleware
    │   │   │   ├── AuthorHandler.kt            # Author handler + authorRoutes
    │   │   │   ├── BookRequest.kt
    │   │   │   ├── BookRoutes.kt               # Book handler + bookRoutes
    │   │   │   ├── ChangePasswordRequest.kt
    │   │   │   ├── HandlerUtils.kt
    │   │   │   ├── LoanRequest.kt
    │   │   │   ├── LoanRoutes.kt               # Loan handler + loanRoutes
    │   │   │   ├── LoginRequest.kt
    │   │   │   ├── UiHandler.kt                # Renders Handlebars templates (login)
    │   │   │   ├── UiRoutes.kt                 # /ui/login and /static/* routes
    │   │   │   ├── UpdateUserRequest.kt
    │   │   │   └── UserHandler.kt              # User handler + userRoutes/publicUserRoutes
    │   │   ├── model/
    │   │   │   ├── Author.kt
    │   │   │   ├── AuthorExceptions.kt
    │   │   │   ├── AuthorFilter.kt
    │   │   │   ├── Book.kt
    │   │   │   ├── BookCategory.kt
    │   │   │   ├── BookExceptions.kt
    │   │   │   ├── BookFilter.kt
    │   │   │   ├── Loan.kt
    │   │   │   ├── LoanExceptions.kt
    │   │   │   ├── LoanFilter.kt
    │   │   │   ├── LoginResponse.kt
    │   │   │   ├── PagedResult.kt
    │   │   │   ├── User.kt
    │   │   │   ├── UserExceptions.kt
    │   │   │   ├── UserFilter.kt
    │   │   │   └── UserResponse.kt
    │   │   ├── repository/
    │   │   │   ├── AuthorRepository.kt
    │   │   │   ├── BookRepository.kt
    │   │   │   ├── LoanRepository.kt
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
    │               ├── api.js                       # apiFetch, qs, escapeHtml, requireAuth, logout
    │               ├── login.js
    │               ├── books.js
    │               ├── authors.js
    │               ├── loans.js
    │               └── users.js
    └── test/kotlin/
        ├── http/
        │   ├── AuthorHandlerTest.kt
        │   ├── BookHandlerTest.kt
        │   ├── LoanHandlerTest.kt
        │   └── UserHandlerTest.kt
        ├── repository/
        │   ├── InMemoryAuthorRepository.kt
        │   ├── InMemoryBookRepository.kt
        │   ├── InMemoryLoanRepository.kt
        │   ├── InMemoryUserRepository.kt
        │   ├── SQLiteAuthorRepositoryTest.kt
        │   ├── SQLiteBookRepositoryTest.kt
        │   ├── SQLiteLoanRepositoryTest.kt
        │   └── SQLiteUserRepositoryTest.kt
        └── service/
            ├── AuthorServiceTest.kt
            ├── BookServiceTest.kt
            ├── BookServiceExtensions.kt
            ├── LoanServiceTest.kt
            └── UserServiceTest.kt
```

---

## Prerequisites

- JDK 17
- `JWT_SECRET` environment variable set

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
   (`passwordHash` here is just the request field name — send the plain password; the server hashes it with BCrypt before storing it.)

3. **Open the login page** in your browser:
   ```
   http://localhost:8080/ui/login
   ```
   Sign in with the email/password you just registered. On success you're redirected to `/ui/books`.

4. **Navigate the app** using the navbar — **Books**, **Authors**, **Loans**, **Users**. Every page supports search/filtering and pagination, with create/edit/delete handled through modals. The JWT is kept in the browser's `localStorage` and attached automatically to each API call; use the **Logout** link (or clear `localStorage`) to end the session.

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
