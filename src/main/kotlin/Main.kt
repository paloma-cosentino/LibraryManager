package org.example

import org.example.database.DatabaseFactory
import org.example.repository.SQLiteBookRepository
import org.example.service.BookService

fun main() {
    val database = DatabaseFactory.init()
    val service = BookService(SQLiteBookRepository(database))

    println("LibraryManager started successfully.")
    println("Books in inventory: ${service.getAllBooks().size}")

}
