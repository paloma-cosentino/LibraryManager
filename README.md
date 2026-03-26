# LibraryManager

A Kotlin study project to build a **library inventory manager**.

## Overview

The goal of this project is to practice domain modeling, Kotlin best practices, and Gradle-based project organization.

The project has a functional REST API with a layered architecture (model → repository → service → HTTP handler), SQLite persistence, and a comprehensive unit-test suite.

## Features

### Currently implemented
- Kotlin project structure with Gradle (Kotlin DSL).
- `Book` domain model (`id`, `title`, `author`, `category`, `quantity`) with `BookCategory` enum.
- `Author` domain model (`id`, `name`).
- `BookRepository` and `AuthorRepository` interfaces with full CRUD contract.
- `SQLiteBookRepository` and `SQLiteAuthorRepository` — Jetbrains Exposed-based implementations persisting to a local SQLite file (`library.db`).
- `DatabaseFactory` — initialises the SQLite connection and creates the schema on first run.
- `BookTable` and `AuthorTable` — Exposed `Table` definitions.
- `BookService` — service layer with auto-creation of authors on `addBook`/`updateBook` if the author does not yet exist.
- `AuthorService` — service layer for author management.
- `BookHandler` + `bookRoutes` — REST handlers for `/books` (GET all, GET by id, POST, PUT, DELETE).
- `AuthorHandler` + `authorRoutes` — REST handlers for `/authors` (GET all, GET by id, POST, PUT, DELETE).
- `InMemoryBookRepository` and `InMemoryAuthorRepository` — lightweight in-memory implementations used exclusively in tests.
- Unit tests for `BookService`, `BookHandler`, and `SQLiteBookRepository`.
- `Main.kt` bootstraps the database and starts an HTTP server on port **8080**.

### Planned (next increments)
- Inventory control (stock-in, stock-out, and stock adjustments).
- Search and filtering by title, author, and category.
- Low-stock alerts.

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/books` | List all books |
| GET | `/books/{id}` | Get book by id |
| POST | `/books` | Create a book (auto-creates author if needed) |
| PUT | `/books/{id}` | Update a book |
| DELETE | `/books/{id}` | Delete a book |
| GET | `/authors` | List all authors |
| GET | `/authors/{id}` | Get author by id |
| POST | `/authors` | Create an author |
| PUT | `/authors/{id}` | Update an author |
| DELETE | `/authors/{id}` | Delete an author |

### POST `/books` — request body example
```json
{
  "title": "Duna",
  "author": "Frank Herbert",
  "category": "Science Fiction",
  "quantity": 4
}
```

Valid categories: `Fiction`, `Non-Fiction`, `Science Fiction`, `Fantasy`, `Horror`, `Mystery`, `Biography`, `History`, `Science`, `Technology`.

## Technologies

| Technology | Version |
|---|---|
| Kotlin | 2.3.0 |
| Gradle (Kotlin DSL) | Wrapper |
| JDK | 17 |
| Jetbrains Exposed | 0.57.0 |
| SQLite JDBC | 3.47.1.0 |
| http4k-core | 5.35.0.0 |
| http4k-format-jackson | 5.35.0.0 |
| http4k-server-jetty | 5.35.0.0 |
| JUnit (via `kotlin("test")`) | — |

## Folder structure

```text
LibraryManager/
├── build.gradle.kts
├── settings.gradle.kts
├── library.db                              # SQLite database (auto-created on first run)
└── src/
    ├── main/
    │   └── kotlin/
    │       ├── Main.kt                     # Entry point — starts HTTP server on :8080
    │       ├── database/
    │       │   ├── AuthorTable.kt
    │       │   ├── BookTable.kt
    │       │   └── DatabaseFactory.kt
    │       ├── http/
    │       │   ├── AuthorHandler.kt
    │       │   ├── AuthorRoutes.kt
    │       │   ├── BookHandler.kt
    │       │   ├── BookRoutes.kt
    │       │   └── CreateBookRequest.kt
    │       ├── model/
    │       │   ├── Author.kt
    │       │   ├── Book.kt
    │       │   ├── BookCategory.kt
    │       │   └── Exceptions.kt
    │       ├── repository/
    │       │   ├── AuthorRepository.kt
    │       │   ├── BookRepository.kt
    │       │   ├── SQLiteAuthorRepository.kt
    │       │   └── SQLiteBookRepository.kt
    │       └── service/
    │           ├── AuthorService.kt
    │           └── BookService.kt
    └── test/
        └── kotlin/
            ├── http/
            │   └── BookHandlerTest.kt
            ├── repository/
            │   ├── InMemoryAuthorRepository.kt
            │   ├── InMemoryBookRepository.kt
            │   └── SQLiteBookRepositoryTest.kt
            └── service/
                └── BookServiceTest.kt
```

## Prerequisites

- JDK 17 installed.
- Execution permission for the Gradle Wrapper (`gradlew`).

## How to run

On macOS/Linux:

```bash
cd /Users/pcu4808/IdeaProjects/LibraryManager
./gradlew run
```

The API will be available at `http://localhost:8080`.

To run tests:

```bash
./gradlew test
```

On Windows (PowerShell):

```powershell
cd C:\path\to\LibraryManager
.\gradlew.bat run
.\gradlew.bat test
```

## Next steps

- Implement stock transaction logic (`StockTransaction`).
- Add search/filter capabilities in `BookService`.
- Add low-stock threshold alerts.

## Contributing

This is a study project, but contributions are welcome:

1. Fork the repository.
2. Create a branch for your feature (`git checkout -b feat/my-feature`).
3. Commit your changes (`git commit -m "feat: describe the feature"`).
4. Push to your fork (`git push origin feat/my-feature`).
5. Open a Pull Request.

## License

Define your preferred license (for example, MIT) before publishing to production.
