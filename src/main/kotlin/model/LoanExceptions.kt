package org.example.model

object LoanExceptions {
    class LoanNotFoundException(id: Int) : IllegalArgumentException("Loan [$id] not found")
    class BookNotAvailableException(title: String) : IllegalArgumentException("Book [$title] has no available copies")
    class LoanAlreadyReturnedException(id: Int) : IllegalArgumentException("Loan [$id] was already returned")
    class BorrowerHasOverdueLoansException : IllegalArgumentException("Borrower has overdue loans; return them before borrowing again")
}