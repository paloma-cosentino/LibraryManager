package org.example.repository

import org.example.model.Book

/**
 * Runs a one-shot hook immediately before the next update, simulating a competing request that
 * commits between our read and our write. The hook is cleared before it runs, so writes made
 * inside it pass straight through to the delegate.
 */
class HookedBookRepository(private val delegate: BookRepository) : BookRepository by delegate {
    private var hook: (() -> Unit)? = null

    fun beforeNextWrite(hook: () -> Unit) {
        this.hook = hook
    }

    override fun update(book: Book): Book? {
        hook?.also { hook = null }?.invoke()
        return delegate.update(book)
    }
}
