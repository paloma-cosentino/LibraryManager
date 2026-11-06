package org.example.repository

import org.example.model.PagedResult
import org.example.model.Role
import org.example.model.User
import org.example.model.UserFilter

interface UserRepository {
    fun findAll(filter: UserFilter): PagedResult<User>
    fun findById(id: Int): User?
    fun findByEmail(email: String): User?
    fun save(user: User): User
    /** Updates name, email and passwordHash only; the role is written solely via [updateRole]. */
    fun update(user: User): User?
    /** Returns true when a row was updated. */
    fun updateRole(id: Int, role: Role): Boolean
    fun countByRole(role: Role): Int
    fun delete(id: Int): Boolean

}