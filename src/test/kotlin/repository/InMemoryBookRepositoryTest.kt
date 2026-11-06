package org.example.repository

import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import kotlin.test.*

class InMemoryBookRepositoryTest {
    private val repository = InMemoryBookRepository()
    private val author = Author(1, "Robert C. Martin")

    @Test
    fun `update increments version and stale update throws ConcurrentUpdateException`() {
        val saved = repository.save(Book(0, "Clean Code", author, BookCategory.TECHNOLOGY, 3))

        val updated = repository.update(saved.copy(quantity = 2))

        assertEquals(1, updated?.version)
        assertEquals(1, repository.findById(saved.id)?.version)
        assertFailsWith<ConcurrentUpdateException> { repository.update(saved.copy(quantity = 10)) }
        assertEquals(2, repository.findById(saved.id)?.quantity)
    }

    @Test
    fun `update returns null when book does not exist`() {
        assertNull(repository.update(Book(999, "Ghost Book", author, BookCategory.FICTION, 0)))
    }
}
