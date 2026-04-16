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
    override fun borrowBook (bookId: Int, borrowerName: String, daysToReturn: Int): Loan {
        val book = bookRepository.findById(bookId)
            ?: throw BookExceptions.BookNotFoundException(bookId)
        if (book.quantity <= 0 ){
            throw LoanExceptions.BookNotAvailableException(book.title)
        }
        bookRepository.update(book.copy(quantity = book.quantity - 1))

        val loan = Loan(
            book = book,
            borrowerName = borrowerName,
            borrowedAt = LocalDate.now(),
            dueDate = LocalDate.now().plusDays(daysToReturn.toLong())
        )
        return loanRepository.save(loan)
    }
    override fun returnBook(loanId: Int): Loan {
        val loan = loanRepository.findById(loanId)
            ?: throw LoanExceptions.LoanNotFoundException(loanId)
        if (loan.isReturned)
            throw LoanExceptions.LoanAlreadyReturnedException(loanId)

        bookRepository.update(loan.book.copy(quantity = loan.book.quantity + 1))

        return loanRepository.update(loan.copy(returnedAt = LocalDate.now()))


    }
}