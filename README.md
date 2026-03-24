# LibraryManager

A Kotlin study project to build a **library inventory manager**.

## Overview

The goal of this project is to practice domain modeling, Kotlin best practices, and Gradle-based project organization.

The project now has a functional foundation with a layered architecture (model → repository → service), SQLite persistence, and a comprehensive unit-test suite.

## Features

### Currently implemented
- Kotlin project structure with Gradle (Kotlin DSL).
- `Book` domain model (`id`, `title`, `author`, `category`, `quantity`).
- `BookRepository` interface with full CRUD contract (`findAll`, `findById`, `save`, `update`, `delete`).
- `SQLiteBookRepository` — Jetbrains Exposed-based implementation persisting to a local SQLite file (`library.db`).
- `DatabaseFactory` — initialises the SQLite connection and creates the schema on first run.
- `BookTable` — Exposed `Table` definition mapping to the `books` table.
- `BookService` — service layer wrapping the repository (`getAllBooks`, `getBookById`, `addBook`, `updateBook`, `removeBook`).
- `InMemoryBookRepository` — lightweight in-memory implementation used exclusively in tests.
- Unit tests for `BookService` (via `InMemoryBookRepository`) and `SQLiteBookRepository` (via a temporary SQLite file).
- Entry point in `Main.kt` that bootstraps the database and prints the current inventory count.

### Planned (next increments)
- Inventory control (stock-in, stock-out, and stock adjustments).
- Search and filtering by title, author, and category.
- Low-stock alerts.
- Interactive CLI or REST API.

## Technologies

| Technology | Version |
|---|---|
| Kotlin | 2.3.0 |
| Gradle (Kotlin DSL) | Wrapper |
| JDK | 17 |
| Jetbrains Exposed | 0.57.0 |
| SQLite JDBC | 3.47.1.0 |
| JUnit (via `kotlin("test")`) | — |

## Folder structure

```text
LibraryManager/
├── build.gradle.kts
├── settings.gradle.kts
├── library.db                          # SQLite database (auto-created on first run)
└── src/
    ├── main/
    │   └── kotlin/
    │       ├── Main.kt
    │       ├── database/
    │       │   ├── BookTable.kt        # Exposed table definition
    │       │   └── DatabaseFactory.kt  # DB initialisation
    │       ├── model/
    │       │   └── Book.kt             # Domain model
    │       ├── repository/
    │       │   ├── BookRepository.kt   # CRUD interface
    │       │   └── SQLiteBookRepository.kt
    │       └── service/
    │           └── BookService.kt
    └── test/
        └── kotlin/
            ├── repository/
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

To run tests:

```bash
cd /Users/pcu4808/IdeaProjects/LibraryManager
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
- Evolve `Main.kt` into an interactive CLI or expose functionality through a REST API.

## Contributing

This is a study project, but contributions are welcome:

1. Fork the repository.
2. Create a branch for your feature (`git checkout -b feat/my-feature`).
3. Commit your changes (`git commit -m "feat: describe the feature"`).
4. Push to your fork (`git push origin feat/my-feature`).
5. Open a Pull Request.

## License

Define your preferred license (for example, MIT) before publishing to production.
