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
    // Called from UserHandler.update / .changePassword via the IUserService-typed `service`
    // field — the IDE's "unused" inspection doesn't always resolve interface-member call
    // sites back to the declaration, so these two are false positives.
    @Suppress("unused")
    fun updateUserProfile(id: Int, name: String, email: String): User?
    @Suppress("unused")
    fun changePassword(id: Int, currentPassword: String, newPassword: String)
    fun removeUser(id: Int): Boolean
    fun login(email: String, password: String): LoginResponse
}