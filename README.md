# LibraryManager

A Kotlin study project to build a **library inventory manager**.

## Overview

The goal of this project is to practice domain modeling, Kotlin best practices, and Gradle-based project organization.

At the moment, the project foundation has been initialized and is ready to evolve with business rules.

## Features

### Currently implemented
- Initial Kotlin project structure with Gradle.
- Entry point in `src/main/kotlin/Main.kt`.
- Test support with `kotlin("test")`.

### Planned (next increments)
- Book registration (title, author, ISBN, category, quantity).
- Inventory control (stock-in, stock-out, and stock adjustments).
- Search and filtering by title, author, and category.
- Low-stock alerts.
- Data persistence (local file or database).

## Technologies

- Kotlin 2.3.0
- Gradle (Wrapper)
- JDK 17
- JUnit (via `kotlin("test")`)

## Folder structure

```text
LibraryManager/
|- build.gradle.kts
|- settings.gradle.kts
|- src/
|  |- main/
|  |  |- kotlin/
|  |     |- Main.kt
|  |- test/
|     |- kotlin/
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

- Create domain classes (`Book`, `InventoryItem`, `StockTransaction`).
- Implement inventory services and validations.
- Add unit tests for core rules.
- Evolve `Main.kt` into a real registration and query flow.

## Contributing

This is a study project, but contributions are welcome:

1. Fork the repository.
2. Create a branch for your feature (`git checkout -b feat/my-feature`).
3. Commit your changes (`git commit -m "feat: describe the feature"`).
4. Push to your fork (`git push origin feat/my-feature`).
5. Open a Pull Request.

## License

Define your preferred license (for example, MIT) before publishing to production.




