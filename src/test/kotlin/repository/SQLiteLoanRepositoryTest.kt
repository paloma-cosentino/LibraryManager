package org.example.repository

import org.example.database.AuthorTable
import org.example.database.BookTable
import org.example.database.LoanTable
import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.Loan
import org.example.model.LoanFilter
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.LocalDate
import kotlin.test.*

class SQLiteLoanRepositoryTest {

    private lateinit var repository: SQLiteLoanRepository
    private lateinit var bookRepository: SQLiteBookRepository
    private lateinit var database: Database
    private lateinit var dbFile: File

    @BeforeTest
    fun setUp() {
        dbFile = File.createTempFile("loan-test", ".db")
        dbFile.deleteOnExit()
        database = Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction(database) { SchemaUtils.create(AuthorTable, BookTable, LoanTable) }
        repository     = SQLiteLoanRepository(database)
        bookRepository = SQLiteBookRepository(database)
    }

    @AfterTest
    fun tearDown() { dbFile.delete() }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun insertAuthor(name: String): Author = transaction(database) {
        val id = AuthorTable.insert { it[AuthorTable.name] = name } get AuthorTable.id
        Author(id, name)
    }

    private fun insertBook(title: String, author: Author, quantity: Int = 3): Book =
        bookRepository.save(Book(0, title, author, BookCategory.SCIENCE_FICTION, quantity))

    private fun insertLoan(book: Book, borrower: String = "Alice", daysToReturn: Int = 14): Loan {
        val today = LocalDate.now()
        return repository.save(
            Loan(
                book         = book,
                borrowerName = borrower,
                borrowedAt   = today,
                dueDate      = today.plusDays(daysToReturn.toLong())
            )
        )
    }

    // ─── save ────────────────────────────────────────────────────────────────

    @Test
    fun `save returns loan with generated id`() {
        val author = insertAuthor("Frank Herbert")
        val book   = insertBook("Dune", author)
        val saved  = insertLoan(book)

        assertTrue(saved.id > 0)
    }

    @Test
    fun `save persists loan data correctly`() {
        val author = insertAuthor("Frank Herbert")
        val book   = insertBook("Dune", author)
        val saved  = insertLoan(book, borrower = "Bob")

        assertEquals("Bob",    saved.borrowerName)
        assertEquals(book.id,  saved.book.id)
        assertNull(saved.returnedAt)
    }

    // ─── findById ────────────────────────────────────────────────────────────

    @Test
    fun `findById returns loan when it exists`() {
        val author = insertAuthor("Frank Herbert")
        val book   = insertBook("Dune", author)
        val saved  = insertLoan(book)

        val found = repository.findById(saved.id)

        assertNotNull(found)
        assertEquals(saved.id, found.id)
        assertEquals("Alice",  found.borrowerName)
    }

    @Test
    fun `findById returns null when loan does not exist`() {
        assertNull(repository.findById(999))
    }

    // ─── findAll — filters ────────────────────────────────────────────────────

    @Test
    fun `findAll returns empty when no loans exist`() {
        assertTrue(repository.findAll(LoanFilter(borrowerName = null, bookId = null, onlyActive = false)).data.isEmpty())
    }

    @Test
    fun `findAll returns all saved loans`() {
        val author = insertAuthor("Frank Herbert")
        val book   = insertBook("Dune", author, quantity = 5)
        insertLoan(book, borrower = "Alice")
        insertLoan(book, borrower = "Bob")

        assertEquals(2, repository.findAll(LoanFilter(borrowerName = null, bookId = null, onlyActive = false)).data.size)
    }

    @Test
    fun `findAll filters by borrower name (partial, case-insensitive)`() {
        val author = insertAuthor("Frank Herbert")
        val book   = insertBook("Dune", author, quantity = 5)
        insertLoan(book, borrower = "Alice")
        insertLoan(book, borrower = "Bob")

        val result = repository.findAll(LoanFilter(borrowerName = "ali", bookId = null, onlyActive = false)).data

        assertEquals(1, result.size)
        assertEquals("Alice", result.first().borrowerName)
    }

    @Test
    fun `findAll filters by bookId`() {
        val author = insertAuthor("Frank Herbert")
        val dune   = insertBook("Dune",          author, quantity = 3)
        val found  = insertBook("Foundation",    author, quantity = 3)
        insertLoan(dune,  borrower = "Alice")
        insertLoan(found, borrower = "Bob")

        val result = repository.findAll(LoanFilter(borrowerName = null, bookId = found.id, onlyActive = false)).data

        assertEquals(1, result.size)
        assertEquals(found.id, result.first().book.id)
    }

    @Test
    fun `findAll with onlyActive=true returns only unreturned loans`() {
        val author = insertAuthor("Frank Herbert")
        val book   = insertBook("Dune", author, quantity = 5)
        val loan1  = insertLoan(book, borrower = "Alice")
        insertLoan(book, borrower = "Bob")

        // Mark loan1 as returned
        repository.update(loan1.copy(returnedAt = LocalDate.now()))

        val active = repository.findAll(LoanFilter(borrowerName = null, bookId = null, onlyActive = true)).data

        assertEquals(1, active.size)
        assertEquals("Bob", active.first().borrowerName)
    }

    // ─── update ──────────────────────────────────────────────────────────────

    @Test
    fun `update persists returnedAt`() {
        val author  = insertAuthor("Frank Herbert")
        val book    = insertBook("Dune", author)
        val saved   = insertLoan(book)
        val today   = LocalDate.now()

        val updated = repository.update(saved.copy(returnedAt = today))

        assertNotNull(updated.returnedAt)
        assertEquals(today, updated.returnedAt)

        // Verify persisted in DB
        val fromDb = repository.findById(saved.id)
        assertEquals(today, fromDb?.returnedAt)
    }

    // ─── pagination ───────────────────────────────────────────────────────────

    @Test
    fun `findAll pagination returns correct page`() {
        val author = insertAuthor("Frank Herbert")
        val book   = insertBook("Dune", author, quantity = 10)
        repeat(5) { insertLoan(book, borrower = "Borrower$it") }

        val page2 = repository.findAll(LoanFilter(borrowerName = null, bookId = null, onlyActive = false, page = 2, size = 2))

        assertEquals(2,  page2.data.size)
        assertEquals(2,  page2.page)
        assertEquals(5,  page2.total)
        assertEquals(3,  page2.totalPages)
    }
}

