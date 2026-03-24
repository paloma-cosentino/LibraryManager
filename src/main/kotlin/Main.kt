package org.example

import org.example.database.DatabaseFactory
import org.example.http.BookHandler
import org.example.http.BookRoutes
import org.example.repository.SQLiteBookRepository
import org.example.service.BookService
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main() {
    val database = DatabaseFactory.init()
    val service = BookService(SQLiteBookRepository(database))
    val bookHandler = BookHandler(service)
    val bookRoute = BookRoutes().bookRoutes(bookHandler)
    bookRoute.asServer(Jetty(8080)).start()

    println("LibraryManager running on http://localhost:8080")

}
