package org.example.service

import org.example.model.Actor
import org.example.model.AuthorizationExceptions
import org.example.model.BookExceptions
import org.example.model.Loan
import org.example.model.LoanExceptions
import org.example.model.LoanFilter
import org.example.model.PagedResult
import org.example.model.UserExceptions
import org.example.model.toBorrower
import org.example.repository.BookRepository
import org.example.repository.LoanRepository
import org.example.repository.ReferencedRowException
import org.example.repository.UserRepository
import java.time.LocalDate

class LoanService(
    private val bookRepository: BookRepository,
    private val loanRepository: LoanRepository,
    private val userRepository: UserRepository
): ILoanService {
    // A USER's query is always pinned to their own account, whatever userId they asked for.
    override fun getAllLoans(actor: Actor, filter: LoanFilter): PagedResult<Loan> =
        loanRepository.findAll(if (actor.isAdmin) filter else filter.copy(userId = actor.userId))

    override fun getLoanById(actor: Actor, id: Int): Loan {
        val loan = loanRepository.findById(id) ?: throw LoanExceptions.LoanNotFoundException(id)
        if (!actor.isAdmin && loan.borrower.id != actor.userId) throw AuthorizationExceptions.ForbiddenException()
        return loan
    }

    override fun borrowBook(actor: Actor, bookId: Int, userId: Int?, daysToReturn: Int): Loan {
        val borrowerId = userId ?: actor.userId
        // Before the lookup, so a USER cannot probe which account ids exist.
        if (!actor.isAdmin && borrowerId != actor.userId) throw AuthorizationExceptions.ForbiddenException()
        // Resolve the account before touching stock, so an unknown user can never cost a copy.
        val borrower = userRepository.findById(borrowerId)?.toBorrower()
            ?: throw UserExceptions.UserIdNotFoundException(borrowerId)
        // Applies to whoever borrows on the account's behalf, admins included; checked before stock.
        if (loanRepository.findActiveByUserId(borrower.id).any { it.isOverdue }) {
            throw LoanExceptions.BorrowerHasOverdueLoansException()
        }
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
            borrower = borrower,
            borrowedAt = LocalDate.now(),
            dueDate = LocalDate.now().plusDays(daysToReturn.toLong())
        )
        return try {
            loanRepository.save(loan)
        } catch (e: ReferencedRowException) {
            // The user or book was deleted after our checks; the FK refused the loan. Put the copy
            // back (unless the book itself is gone) and report whichever row is missing.
            restockIfBookExists(bookId)
            if (userRepository.findById(borrowerId) == null) throw UserExceptions.UserIdNotFoundException(borrowerId)
            if (bookRepository.findById(bookId) == null) throw BookExceptions.BookNotFoundException(bookId)
            throw e
        }
    }

    private fun restockIfBookExists(bookId: Int) {
        retryOnConflict {
            val current = bookRepository.findById(bookId) ?: return@retryOnConflict
            bookRepository.update(current.withQuantityChangedBy(+1))
        }
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