package org.example.repository

import org.example.model.Book
import org.example.model.Loan

/** Every update loses the optimistic-lock race, so retries are always exhausted. */
class AlwaysConflictingBookRepository(delegate: BookRepository) : BookRepository by delegate {
    override fun update(book: Book): Book? = throw ConcurrentUpdateException("Book", book.id)
}

/** Every update loses the optimistic-lock race, so retries are always exhausted. */
class AlwaysConflictingLoanRepository(delegate: LoanRepository) : LoanRepository by delegate {
    override fun update(loan: Loan): Loan = throw ConcurrentUpdateException("Loan", loan.id)
}
