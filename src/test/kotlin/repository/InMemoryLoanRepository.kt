package org.example.repository

import org.example.model.Loan
import org.example.model.LoanFilter
import org.example.model.PagedResult
import kotlin.math.ceil

class InMemoryLoanRepository : LoanRepository {
    private val loans  = mutableMapOf<Int, Loan>()
    private var nextId = 1

    override fun findAll(filter: LoanFilter): PagedResult<Loan> {
        var result = loans.values.toList()

        filter.borrowerName?.let { result = result.filter { l -> l.borrowerName.contains(it, ignoreCase = true) } }
        filter.bookId?.let       { result = result.filter { l -> l.book.id == it } }
        if (filter.onlyActive)   result = result.filter { !it.isReturned }

        val total      = result.size
        val pageNum    = filter.page ?: 1
        val pageSize   = filter.size ?: 10
        val offset     = (pageNum - 1) * pageSize
        val paged      = result.drop(offset).take(pageSize)
        val totalPages = if (total == 0) 0 else ceil(total.toDouble() / pageSize).toInt()

        return PagedResult(data = paged, page = pageNum, pageSize = pageSize, total = total, totalPages = totalPages)
    }

    override fun findById(id: Int): Loan? = loans[id]

    override fun save(loan: Loan): Loan {
        val newLoan = loan.copy(id = nextId++)
        loans[newLoan.id] = newLoan
        return newLoan
    }

    override fun update(loan: Loan): Loan {
        loans[loan.id] = loan
        return loan
    }
}

