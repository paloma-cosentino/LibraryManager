package org.example.repository

import org.example.model.BookExceptions
import org.example.model.Loan
import org.example.model.LoanExceptions
import java.time.LocalDate

class LoanService(
    private val bookRepository: BookRepository,
    private val loanRepository: LoanRepository
) {
    fun loanBook (bookId: Int, borrowerName: String, daysToReturn: Int): Loan {
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
    fun returnBook(bookId: Int, borrowerName: String, daysToReturn: Int) {
        val book = bookRepository.findById(bookId)
            ?: throw BookExceptions.BookNotFoundException(bookId)
        bookRepository.update(book.copy(quantity = book.quantity + 1))


    }
}