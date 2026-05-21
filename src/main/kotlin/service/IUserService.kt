package org.example.service

import org.example.model.LoginResponse
import org.example.model.PagedResult
import org.example.model.User
import org.example.model.UserFilter

interface IUserService {
    fun getAllUsers(filter: UserFilter): PagedResult<User>
    fun getUserById(id: Int): User
    fun getUserByEmail(email: String): User
    fun addUser(name: String, email: String, password: String): User
    fun updateUser(user: User): User?
    fun removeUser(id: Int): Boolean
    fun login(email: String, password: String): LoginResponse
}