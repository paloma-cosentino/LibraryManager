package org.example.service

import org.example.model.AuthorExceptions
import org.example.model.AuthorFilter
import org.example.repository.InMemoryAuthorRepository
import org.example.repository.InMemoryBookRepository
import kotlin.test.*

class AuthorServiceTest {

    private lateinit var service: AuthorService
    private lateinit var repository: InMemoryAuthorRepository

    @BeforeTest
    fun setUp() {
        repository = InMemoryAuthorRepository()
        service    = AuthorService(repository, InMemoryBookRepository())
    }

    // ─── getAllAuthors ────────────────────────────────────────────────────────

    @Test
    fun `getAllAuthors returns empty list when no authors exist`() {
        assertTrue(service.getAllAuthors(AuthorFilter(name = null)).data.isEmpty())
    }

    @Test
    fun `getAllAuthors returns all added authors`() {
        service.addAuthor("Frank Herbert")
        service.addAuthor("Isaac Asimov")

        assertEquals(2, service.getAllAuthors(AuthorFilter(name = null)).data.size)
    }

    @Test
    fun `getAllAuthors filters by name`() {
        service.addAuthor("Frank Herbert")
        service.addAuthor("Isaac Asimov")

        val result = service.getAllAuthors(AuthorFilter(name = "Frank")).data

        assertEquals(1, result.size)
        assertEquals("Frank Herbert", result.first().name)
    }

    // ─── getAuthorById ────────────────────────────────────────────────────────

    @Test
    fun `getAuthorById returns author when it exists`() {
        val added = service.addAuthor("Frank Herbert")

        val found = service.getAuthorById(added.id)

        assertEquals(added.id, found.id)
        assertEquals("Frank Herbert", found.name)
    }

    @Test
    fun `getAuthorById throws AuthorNotFoundException when author does not exist`() {
        assertFailsWith<AuthorExceptions.AuthorNotFoundException> {
            service.getAuthorById(999)
        }
    }

    // ─── addAuthor ────────────────────────────────────────────────────────────

    @Test
    fun `addAuthor returns author with generated id`() {
        val author = service.addAuthor("Frank Herbert")

        assertTrue(author.id > 0)
    }

    @Test
    fun `addAuthor persists correct data`() {
        val author = service.addAuthor("Frank Herbert")

        assertEquals("Frank Herbert", author.name)
    }

    @Test
    fun `addAuthor assigns unique ids`() {
        val a1 = service.addAuthor("Frank Herbert")
        val a2 = service.addAuthor("Isaac Asimov")

        assertNotEquals(a1.id, a2.id)
    }

    @Test
    fun `addAuthor throws DuplicateAuthorException when name already exists`() {
        service.addAuthor("Frank Herbert")

        assertFailsWith<AuthorExceptions.DuplicateAuthorException> {
            service.addAuthor("Frank Herbert")
        }
    }

    @Test
    fun `addAuthor makes author retrievable afterwards`() {
        val added = service.addAuthor("Frank Herbert")

        assertNotNull(service.getAuthorById(added.id))
    }

    // ─── updateAuthor ─────────────────────────────────────────────────────────

    @Test
    fun `updateAuthor changes author name`() {
        val added   = service.addAuthor("Frank Herbert")
        val updated = service.updateAuthor(added.copy(name = "Frank Herbert Jr"))

        assertNotNull(updated)
        assertEquals("Frank Herbert Jr", updated.name)
    }

    @Test
    fun `updateAuthor persists changes so they are retrievable`() {
        val added = service.addAuthor("Frank Herbert")
        service.updateAuthor(added.copy(name = "Frank Herbert Jr"))

        val found = service.getAuthorById(added.id)

        assertEquals("Frank Herbert Jr", found.name)
    }

    @Test
    fun `updateAuthor returns null when author does not exist`() {
        val result = service.updateAuthor(org.example.model.Author(id = 999, name = "Ghost"))

        assertNull(result)
    }

    // ─── removeAuthor ─────────────────────────────────────────────────────────

    @Test
    fun `removeAuthor returns true and author is no longer retrievable`() {
        val added  = service.addAuthor("Frank Herbert")
        val result = service.removeAuthor(added.id)

        assertTrue(result)
        assertFailsWith<AuthorExceptions.AuthorNotFoundException> {
            service.getAuthorById(added.id)
        }
    }

    @Test
    fun `removeAuthor returns false when author does not exist`() {
        assertFalse(service.removeAuthor(999))
    }

    @Test
    fun `removeAuthor only removes the targeted author`() {
        val a1 = service.addAuthor("Frank Herbert")
        val a2 = service.addAuthor("Isaac Asimov")

        service.removeAuthor(a1.id)

        assertFailsWith<AuthorExceptions.AuthorNotFoundException> { service.getAuthorById(a1.id) }
        assertNotNull(service.getAuthorById(a2.id))
    }
}

