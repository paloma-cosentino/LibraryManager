# LibraryManager

A Kotlin study project to build a **library inventory manager** with a REST API, user authentication, and a planned web UI.

## Overview

The project follows a layered architecture (model → repository → service → HTTP handler), with SQLite persistence, JWT-based authentication, and a comprehensive unit-test suite.

---

## Features

### ✅ Implemented

- **Books** — full CRUD (`GET`, `POST`, `PUT`, `DELETE`) with category validation and author auto-creation
- **Authors** — full CRUD
- **Loans** — create loan, list, get by id, register return; tracks availability and overdue status
- **Users** — full CRUD with BCrypt password hashing
- **Authentication** — `POST /auth/login` returns a signed JWT token; all routes except login are protected by `AuthFilter`
- **Pagination** — all list endpoints support `page` and `size` query params
- **Filtering** — books by title/category/author, authors by name, loans by active status, users by name
- **Unit tests** — handler, service and SQLite repository tests for all entities

### 🔲 Planned

- Web UI with Handlebars templates and JavaScript (see `TODOLIST.md`)

---

## Authentication

All endpoints except `POST /users` and `POST /auth/login` require a valid JWT token.

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
| GET | `/users` | ✅ | List users (paginated, filter by `name`) |
| GET | `/users/email?email=` | ✅ | Get user by email |
| PUT | `/users/{id}` | ✅ | Update user |
| DELETE | `/users/{id}` | ✅ | Delete user |

### Books
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/books` | ✅ | List books (paginated, filter by `title`, `category`, `authorId`) |
| GET | `/books/{id}` | ✅ | Get book by id |
| POST | `/books` | ✅ | Create book |
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
| GET | `/loans` | ✅ | List loans (paginated, filter by `onlyActive`) |
| GET | `/loans/{id}` | ✅ | Get loan by id |
| POST | `/loans` | ✅ | Create loan |
| PATCH | `/loans/{id}/return` | ✅ | Register return |

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
    ├── main/kotlin/
    │   ├── Main.kt                         # Entry point — starts HTTP server on :8080
    │   ├── database/
    │   │   ├── DatabaseFactory.kt
    │   │   ├── AuthorTable.kt
    │   │   ├── BookTable.kt
    │   │   ├── LoanTable.kt
    │   │   └── UserTable.kt
    │   ├── http/
    │   │   ├── AuthFilter.kt               # JWT middleware
    │   │   ├── AuthorHandler.kt
    │   │   ├── BookHandler.kt
    │   │   ├── BookRequest.kt
    │   │   ├── BookRoutes.kt
    │   │   ├── HandlerUtils.kt
    │   │   ├── LoanHandler.kt
    │   │   ├── LoanRequest.kt
    │   │   ├── LoanRoutes.kt
    │   │   ├── LoginRequest.kt
    │   │   └── UserHandler.kt
    │   ├── model/
    │   │   ├── Author.kt
    │   │   ├── AuthorExceptions.kt
    │   │   ├── AuthorFilter.kt
    │   │   ├── Book.kt
    │   │   ├── BookCategory.kt
    │   │   ├── BookExceptions.kt
    │   │   ├── BookFilter.kt
    │   │   ├── Loan.kt
    │   │   ├── LoanExceptions.kt
    │   │   ├── LoanFilter.kt
    │   │   ├── LoginResponse.kt
    │   │   ├── PagedResult.kt
    │   │   ├── User.kt
    │   │   ├── UserExceptions.kt
    │   │   ├── UserFilter.kt
    │   │   └── UserResponse.kt
    │   ├── repository/
    │   │   ├── AuthorRepository.kt
    │   │   ├── BookRepository.kt
    │   │   ├── LoanRepository.kt
    │   │   ├── SQLiteAuthorRepository.kt
    │   │   ├── SQLiteBookRepository.kt
    │   │   ├── SQLiteLoanRepository.kt
    │   │   ├── SQLiteUserRepository.kt
    │   │   └── UserRepository.kt
    │   ├── security/
    │   │   └── JwtService.kt               # JWT generation & validation
    │   └── service/
    │       ├── AuthorService.kt
    │       ├── BookService.kt
    │       ├── IAuthorService.kt
    │       ├── IBookService.kt
    │       ├── ILoanService.kt
    │       ├── IUserService.kt
    │       ├── LoanService.kt
    │       └── UserService.kt
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

## Contributing

1. Fork the repository.
2. Create a branch for your feature (`git checkout -b feat/my-feature`).
3. Commit your changes (`git commit -m "feat: describe the feature"`).
4. Push to your fork (`git push origin feat/my-feature`).
5. Open a Pull Request.

## License

MIT
