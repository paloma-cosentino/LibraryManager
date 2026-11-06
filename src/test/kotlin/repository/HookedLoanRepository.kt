package org.example.repository

import org.example.model.Loan

/**
 * Runs a one-shot hook immediately before the next update, simulating a competing request that
 * commits between our read and our write. The hook is cleared before it runs, so writes made
 * inside it pass straight through to the delegate.
 */
class HookedLoanRepository(private val delegate: LoanRepository) : LoanRepository by delegate {
    private var hook: (() -> Unit)? = null

    fun beforeNextWrite(hook: () -> Unit) {
        this.hook = hook
    }

    override fun update(loan: Loan): Loan {
        hook?.also { hook = null }?.invoke()
        return delegate.update(loan)
    }
}
