package org.example.model

data class UserResponse (
    val id: Int = 0,
    val name: String,
    val email: String,
    val role: Role
)

// The only User -> wire mapping; passwordHash is deliberately absent.
fun User.toResponse() = UserResponse(id = id, name = name, email = email, role = role)