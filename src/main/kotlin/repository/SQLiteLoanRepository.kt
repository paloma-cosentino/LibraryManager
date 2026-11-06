package org.example.repository

import org.example.database.AuthorTable
import org.example.database.BookTable
import org.example.database.LoanTable
import org.example.model.Author
import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.Loan
import org.example.model.LoanFilter
import org.example.model.PagedResult
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import kotlin.math.ceil

class SQLiteLoanRepository(private val database: Database) : LoanRepository{
    private fun ResultRow.toLoan() = Loan(
        id = this[LoanTable.id],
        book = Book(
            id = this[BookTable.id],
            title = this[BookTable.title],
            author = Author(this[AuthorTable.id], this[AuthorTable.name]),
            category = BookCategory.entries.first {  it.displayName == this[BookTable.category]  },
            quantity = this[BookTable.quantity],
            publishedYear = this[BookTable.publishedYear],
            edition = this[BookTable.edition],
            version = this[BookTable.version]
        ),
        borrowerName = this[LoanTable.borrowerName],
        borrowedAt = LocalDate.parse(this[LoanTable.borrowedAt]),
        dueDate = LocalDate.parse(this[LoanTable.dueDate]),
        returnedAt = this[LoanTable.returnedAt]?.let { LocalDate.parse(it) },
        version = this[LoanTable.version]
    )
    override fun findAll(filter: LoanFilter): PagedResult<Loan> = transaction(database) {
        var query = (LoanTable innerJoin BookTable innerJoin AuthorTable).selectAll()

        filter.borrowerName?.let {query = query.andWhere { LoanTable.borrowerName like "%$it%" } } // Qual a diferença entre "%it%" e it
        filter.bookId?.let { query = query.andWhere { LoanTable.bookId eq it } }
        if (filter.onlyActive) query = query.andWhere { LoanTable.returnedAt.isNull() }

        val total = query.count().toInt()
        val pageNum = filter.page ?: 1
        val pageSize = filter.size ?: 10
        val offset = ((pageNum - 1) * pageSize).toLong()
        val loans = query.limit(pageSize).offset(offset).map { it.toLoan() }
        val totalPages = if (total != 0 ) ceil(total.toDouble() / pageSize).toInt() else 0

        PagedResult(
            data = loans,
            page = pageNum,
            pageSize = pageSize,
            total = total,
            totalPages = totalPages
        )
    }

    override fun findById(id: Int): Loan? = transaction(database) {
        (LoanTable innerJoin BookTable innerJoin AuthorTable).selectAll()
            .where{ LoanTable.id eq id }
            .map { it.toLoan() }
            .firstOrNull()
    }

    override fun save(loan: Loan): Loan = transaction(database) {
        loan.copy(
            id = LoanTable.insert {
                it[bookId] = loan.book.id
                it[borrowerName] = loan.borrowerName
                it[borrowedAt] = loan.borrowedAt.toString()
                it[dueDate] = loan.dueDate.toString()
                it[returnedAt] = loan.returnedAt?.toString()
                it[version] = 0
            }[LoanTable.id],
            version = 0
        )
    }

    override fun update(loan: Loan): Loan = transaction(database) {
        val nextVersion = loan.version + 1
        val updatedRows = LoanTable.update({ (LoanTable.id eq loan.id) and (LoanTable.version eq loan.version) }) {
            it[bookId] = loan.book.id
            it[borrowerName] = loan.borrowerName
            it[borrowedAt] = loan.borrowedAt.toString()
            it[dueDate] = loan.dueDate.toString()
            it[returnedAt] = loan.returnedAt?.toString()
            it[version] = nextVersion
        }
        when {
            updatedRows > 0 -> loan.copy(version = nextVersion)
            // Missing loan keeps the pre-existing contract: echo the input unchanged.
            LoanTable.selectAll().where { LoanTable.id eq loan.id }.empty() -> loan
            else -> throw ConcurrentUpdateException("Loan", loan.id)
        }
    }

}