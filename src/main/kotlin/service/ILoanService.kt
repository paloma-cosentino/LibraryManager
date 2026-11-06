package org.example.service

import org.example.model.Actor
import org.example.model.Loan
import org.example.model.LoanFilter
import org.example.model.PagedResult

interface ILoanService {
    fun getAllLoans(actor: Actor, filter: LoanFilter): PagedResult<Loan>
    fun getLoanById(actor: Actor, id: Int): Loan
    /** userId null means the actor borrows for themself. */
    fun borrowBook(actor: Actor, bookId: Int, userId: Int?, daysToReturn: Int): Loan
    fun returnBook(loanId: Int): Loan
}