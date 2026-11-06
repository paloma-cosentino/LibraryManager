package org.example.service

import org.example.model.Loan
import org.example.model.LoanFilter
import org.example.model.PagedResult

interface ILoanService {
    fun getAllLoans(filter: LoanFilter): PagedResult<Loan>
    fun getLoanById(id: Int): Loan
    fun borrowBook(bookId: Int, userId: Int, daysToReturn: Int): Loan
    fun returnBook(loanId: Int): Loan
}