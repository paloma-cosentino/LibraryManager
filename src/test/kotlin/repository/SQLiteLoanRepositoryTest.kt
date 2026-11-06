package org.example.repository

import org.example.database.AuthorTable
import org.example.database.BookTable
import org.example.database.LoanTable
import org.example.database.UserTable
import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.Borrower
import org.example.model.Loan
import org.example.model.LoanFilter
import org.example.model.User
import org.example.model.toBorrower
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
    private lateinit var userRepository: SQLiteUserRepository
    private lateinit var database: Database
    private lateinit var dbFile: File
    private val usersByName = mutableMapOf<String, User>()

    @BeforeTest
    fun setUp() {
        dbFile = File.createTempFile("loan-test", ".db")
        dbFile.deleteOnExit()
        database = Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction(database) { SchemaUtils.create(AuthorTable, BookTable, UserTable, LoanTable) }
        repository     = SQLiteLoanRepository(database)
        bookRepository = SQLiteBookRepository(database)
        userRepository = SQLiteUserRepository(database)
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

    private fun user(name: String): User = usersByName.getOrPut(name) {
        userRepository.save(User(name = name, email = "${name.lowercase()}@example.com", passwordHash = "h"))
    }

    private fun insertLoan(book: Book, borrower: String = "Alice", daysToReturn: Int = 14): Loan {
        val today = LocalDate.now()
        return repository.save(
            Loan(
                book         = book,
                borrower     = user(borrower).toBorrower(),
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

        assertEquals("Bob",    saved.borrower.name)
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
        assertEquals("Alice",  found.borrower.name)
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
        assertEquals("Alice", result.first().borrower.name)
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
        assertEquals("Bob", active.first().borrower.name)
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

    @Test
    fun `update increments loan version and findById maps loan and book versions`() {
        val author = insertAuthor("Frank Herbert")
        val book   = insertBook("Dune", author)
        bookRepository.update(book.copy(quantity = 2))
        val saved  = insertLoan(book)

        val updated = repository.update(saved.copy(returnedAt = LocalDate.now()))

        assertEquals(1, updated.version)
        val fromDb = repository.findById(saved.id)
        assertEquals(1, fromDb?.version)
        assertEquals(1, fromDb?.book?.version)
    }

    @Test
    fun `stale loan update from an independent connection throws ConcurrentUpdateException`() {
        val author = insertAuthor("Frank Herbert")
        val saved  = insertLoan(insertBook("Dune", author))
        val otherRepository = SQLiteLoanRepository(
            Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        )
        val staleCopy = otherRepository.findById(saved.id)!!
        val firstReturn = LocalDate.now()

        repository.update(saved.copy(returnedAt = firstReturn))

        assertFailsWith<ConcurrentUpdateException> {
            otherRepository.update(staleCopy.copy(returnedAt = firstReturn.plusDays(1)))
        }
        assertEquals(firstReturn, repository.findById(saved.id)?.returnedAt)
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

    // ─── account-linked borrower ─────────────────────────────────────────────

    @Test
    fun `borrower is read from the users table, reflecting the current account details`() {
        val saved = insertLoan(insertBook("Dune", insertAuthor("Frank Herbert")), borrower = "Alice")
        val alice = user("Alice")
        userRepository.update(alice.copy(name = "Alice Liddell", email = "liddell@example.com"))

        assertEquals(Borrower(alice.id, "Alice Liddell", "liddell@example.com"), repository.findById(saved.id)?.borrower)
        assertEquals(
            listOf(Borrower(alice.id, "Alice Liddell", "liddell@example.com")),
            repository.findAll(LoanFilter(borrowerName = null, bookId = null, onlyActive = false)).data.map { it.borrower }
        )
    }

    @Test
    fun `findAll filters by exact userId`() {
        val book = insertBook("Dune", insertAuthor("Frank Herbert"), quantity = 5)
        insertLoan(book, borrower = "Al")
        insertLoan(book, borrower = "Alice")

        val result = repository.findAll(LoanFilter(borrowerName = null, bookId = null, onlyActive = false, userId = user("Al").id)).data

        assertEquals(listOf(user("Al").id), result.map { it.borrower.id })
    }

    @Test
    fun `findActiveByUserId returns only that account's unreturned loans`() {
        val book = insertBook("Dune", insertAuthor("Frank Herbert"), quantity = 5)
        val active = insertLoan(book, borrower = "Alice")
        val returned = insertLoan(book, borrower = "Alice")
        repository.update(returned.copy(returnedAt = LocalDate.now()))
        insertLoan(book, borrower = "Bob")

        assertEquals(listOf(active), repository.findActiveByUserId(user("Alice").id))
        assertTrue(repository.findActiveByUserId(999).isEmpty())
    }

    @Test
    fun `existsByUserId and existsByBookId count returned loans too`() {
        val author = insertAuthor("Frank Herbert")
        val dune = insertBook("Dune", author)
        val emma = insertBook("Emma", author)
        val loan = insertLoan(dune, borrower = "Alice")
        repository.update(loan.copy(returnedAt = LocalDate.now()))
        user("Bob")

        assertTrue(repository.existsByUserId(user("Alice").id))
        assertTrue(repository.existsByBookId(dune.id))
        assertFalse(repository.existsByUserId(user("Bob").id))
        assertFalse(repository.existsByBookId(emma.id))
    }
}
