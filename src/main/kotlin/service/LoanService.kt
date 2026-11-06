package org.example.service

import org.example.model.BookExceptions
import org.example.model.Loan
import org.example.model.LoanExceptions
import org.example.model.LoanFilter
import org.example.model.PagedResult
import org.example.repository.BookRepository
import org.example.repository.LoanRepository
import java.time.LocalDate

class LoanService(
    private val bookRepository: BookRepository,
    private val loanRepository: LoanRepository
): ILoanService {
    override fun getAllLoans(filter: LoanFilter): PagedResult<Loan> =
        loanRepository.findAll(filter)

    override fun getLoanById(id: Int): Loan {
        return loanRepository.findById(id)
            ?: throw LoanExceptions.LoanNotFoundException(id)
    }

    override fun borrowBook(bookId: Int, borrowerName: String, daysToReturn: Int): Loan {
        val book = findBook(bookId)
        retryOnConflict {
            // Re-read on every attempt: availability must be judged against the latest committed stock.
            val current = findBook(bookId)
            if (current.quantity <= 0) throw LoanExceptions.BookNotAvailableException(current.title)
            bookRepository.update(current.withQuantityChangedBy(-1))
                ?: throw BookExceptions.BookNotFoundException(bookId)
        }

        val loan = Loan(
            book = book,
            borrowerName = borrowerName,
            borrowedAt = LocalDate.now(),
            dueDate = LocalDate.now().plusDays(daysToReturn.toLong())
        )
        return loanRepository.save(loan)
    }

    private fun findBook(bookId: Int) =
        bookRepository.findById(bookId) ?: throw BookExceptions.BookNotFoundException(bookId)

    override fun returnBook(loanId: Int): Loan {
        val loan = loanRepository.findById(loanId)
            ?: throw LoanExceptions.LoanNotFoundException(loanId)
        if (loan.isReturned)
            throw LoanExceptions.LoanAlreadyReturnedException(loanId)

        findBook(loan.book.id)
        // Mark returned before restocking: a crash between the two writes under-counts stock
        // rather than letting a loan be returned (and restocked) twice.
        val returned = retryOnConflict {
            val current = loanRepository.findById(loanId)
                ?: throw LoanExceptions.LoanNotFoundException(loanId)
            if (current.isReturned) throw LoanExceptions.LoanAlreadyReturnedException(loanId)
            loanRepository.update(current.copy(returnedAt = LocalDate.now()))
        }
        retryOnConflict {
            bookRepository.update(findBook(loan.book.id).withQuantityChangedBy(+1))
                ?: throw BookExceptions.BookNotFoundException(loan.book.id)
        }
        return returned
    }
}