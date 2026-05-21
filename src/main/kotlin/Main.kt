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
import org.http4k.core.Request
import org.http4k.core.Status
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

    val publicRoutes    = loginRoutes(userHandler)
    val protectedRoutes = routes(
        bookRoutes(bookHandler),
        authorRoutes(authorHandler),
        loanRoutes(loanHandler),
        userRoutes(userHandler)
    )

    val app = { request: Request ->
        val publicResponse = publicRoutes(request)
        if (publicResponse.status != Status.NOT_FOUND) publicResponse
        else authFilter(protectedRoutes)(request)
    }

    app.asServer(Jetty(8080)).start()

    println("LibraryManager running on http://localhost:8080")
}
