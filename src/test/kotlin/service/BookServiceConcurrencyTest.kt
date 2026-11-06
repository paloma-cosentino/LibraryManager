package org.example.service

import org.example.repository.HookedBookRepository
import org.example.repository.InMemoryAuthorRepository
import org.example.repository.InMemoryBookRepository
import org.example.repository.InMemoryLoanRepository
import kotlin.test.*

class BookServiceConcurrencyTest {

    private val bookRepository = HookedBookRepository(InMemoryBookRepository())
    private val service = BookService(bookRepository, InMemoryAuthorRepository(), InMemoryLoanRepository())

    @Test
    fun `a borrow during an edit is not lost`() {
        val added = service.addBook("Clean Code", "Robert C. Martin", "Technology", 3)
        bookRepository.beforeNextWrite {
            bookRepository.update(bookRepository.findById(added.id)!!.withQuantityChangedBy(-1))
        }

        service.updateBook(added.id, "Clean Code 2nd Ed", added.author.name, added.category.displayName)

        val stored = service.getBookById(added.id)
        assertEquals("Clean Code 2nd Ed", stored.title)
        assertEquals(2, stored.quantity)
    }

    @Test
    fun `a borrow during a stock adjustment is not lost`() {
        val added = service.addBook("Clean Code", "Robert C. Martin", "Technology", 3)
        bookRepository.beforeNextWrite {
            bookRepository.update(bookRepository.findById(added.id)!!.withQuantityChangedBy(-1))
        }

        val adjusted = service.adjustBookQuantity(added.id, 5)

        assertEquals(7, adjusted.quantity)
        assertEquals(7, service.getBookById(added.id).quantity)
    }
}
