package org.example

import org.example.repository.InMemoryBookRepository
import org.example.service.BookService

fun main() {
    val service = BookService(InMemoryBookRepository())

    val book1 = service.addBook("Clean Code", "Robert C. Martin", "Programming", 3)
    val book2 = service.addBook("The Pragmatic Programmer", "David Thomas", "Programming", 5)

    println("All books:")
    service.getAllBooks().forEach { println("  $it") }

   println("\nFind by id=1: ${service.getBookById(1)}")

   val updated = service.updateBook(book1.copy(quantity = 10))
    println("\nUpdated: $updated")

    val deleted = service.removeBook(book2.id)
    println("\nDeleted id=2: $deleted")

    println("\nBooks after delete:")
    service.getAllBooks().forEach { println("  $it") }
}
