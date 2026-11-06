package org.example.repository

import org.example.model.Loan
import org.example.model.LoanFilter
import org.example.model.PagedResult

interface LoanRepository {
    fun findAll(filter: LoanFilter): PagedResult<Loan>
    fun findById(id: Int): Loan?
    /** Unreturned loans of one account; no paging. */
    fun findActiveByUserId(userId: Int): List<Loan>
    fun save(loan: Loan): Loan
    fun update(loan: Loan): Loan   // usado só para registrar a devolução
}