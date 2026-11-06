package org.example.model

data class Actor(val userId: Int, val role: Role) {
    val isAdmin get() = role == Role.ADMIN
}
