package org.example.repository

import org.example.model.PagedResult
import org.example.model.User
import org.example.model.UserFilter

interface UserRepository {
    fun findAll(filter: UserFilter): PagedResult<User>
    fun findById(id: Int): User?
    fun findByEmail(email: String): User?
    fun save(user: User): User
    fun update(user: User): User?
    fun delete(id: Int): Boolean

}