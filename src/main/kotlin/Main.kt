package org.example

import org.example.database.DatabaseFactory
import org.example.http.*
import org.example.repository.SQLiteAuthorRepository
import org.example.repository.SQLiteBookRepository
import org.example.repository.SQLiteLoanRepository
import org.example.repository.SQLiteUserRepository
import org.example.service.AuthorService
import org.example.service.BookService
import org.example.service.LoanService
import org.example.service.UserService
import org.http4k.core.Filter
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main() {
    val database         = DatabaseFactory.init()
    val authorRepository = SQLiteAuthorRepository(database)
    val authorService    = AuthorService(authorRepository)
    val authorHandler    = AuthorHandler(authorService)

    val bookRepository = SQLiteBookRepository(database)
    val bookService    = BookService(bookRepository, authorRepository)
    val bookHandler    = BookHandler(bookService)

    val loanRepository = SQLiteLoanRepository(database)
    val loanService    = LoanService(bookRepository, loanRepository)
    val loanHandler    = LoanHandler(loanService)

    val userRepository = SQLiteUserRepository(database)
    val userService    = UserService(userRepository)
    val userHandler    = UserHandler(userService)

    val uiHandler = UiHandler()

    // Scoped to this group only, so public routes that share a path with a
    // protected one (e.g. POST /users vs GET /users) still resolve correctly
    // (404/405) without ever running the filter.
    val protectedRoutes = routes(
        bookRoutes(bookHandler),
        authorRoutes(authorHandler),
        loanRoutes(loanHandler),
        userRoutes(userHandler)
    ).withFilter(Filter(::authFilter))

    // A single flat `routes()` call lets http4k resolve 404 vs 405 correctly
    // across *all* registered routes, public and protected alike. Combining
    // them via two separately-tried routers (checking only for 404) broke as
    // soon as a path existed in both groups with different methods — e.g.
    // POST /users (public) made GET /users (protected) return a bare 405
    // instead of ever reaching the protected handler.
    val app = routes(
        publicUserRoutes(userHandler),
        uiRoutes(uiHandler),
        protectedRoutes
    )

    app.asServer(Jetty(8080)).start()

    println("LibraryManager running on http://localhost:8080")
}




