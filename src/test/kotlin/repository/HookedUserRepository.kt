package org.example.repository

import org.example.model.Role

/**
 * One-shot hooks that run immediately before the next role write, role count or delete, simulating
 * a competing request that runs in that gap. Each hook is cleared before it runs, so calls made
 * inside it pass straight through to the delegate.
 */
class HookedUserRepository(private val delegate: UserRepository) : UserRepository by delegate {
    private var roleWriteHook: (() -> Unit)? = null
    private var countHook: (() -> Unit)? = null
    private var deleteHook: (() -> Unit)? = null

    fun beforeNextRoleWrite(hook: () -> Unit) {
        roleWriteHook = hook
    }

    fun beforeNextCount(hook: () -> Unit) {
        countHook = hook
    }

    fun beforeNextDelete(hook: () -> Unit) {
        deleteHook = hook
    }

    override fun delete(id: Int): Boolean {
        deleteHook?.also { deleteHook = null }?.invoke()
        return delegate.delete(id)
    }

    override fun updateRole(id: Int, role: Role): Boolean {
        roleWriteHook?.also { roleWriteHook = null }?.invoke()
        return delegate.updateRole(id, role)
    }

    override fun countByRole(role: Role): Int {
        countHook?.also { countHook = null }?.invoke()
        return delegate.countByRole(role)
    }
}
