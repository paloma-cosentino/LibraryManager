package org.example

import org.example.database.DatabaseFactory
import org.example.http.AuthorHandler
import org.example.http.BookHandler
import org.example.http.LoanHandler
import org.example.http.UserHandler
import org.example.http.authorRoutes
import org.example.http.bookRoutes
import org.example.http.loanRoutes
import org.example.http.userRoutes

import org.example.repository.SQLiteAuthorRepository
import org.example.repository.SQLiteBookRepository
import org.example.repository.SQLiteLoanRepository
import org.example.repository.SQLiteUserRepository
import org.example.service.AuthorService
import org.example.service.BookService
import org.example.service.LoanService
import org.example.service.UserService
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

    routes(bookRoutes(bookHandler), authorRoutes(authorHandler), loanRoutes(loanHandler), userRoutes(userHandler))
        .asServer(Jetty(8080))
        .start()

    println("LibraryManager running on http://localhost:8080")
}
