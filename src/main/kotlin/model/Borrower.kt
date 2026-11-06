package org.example.model

// The only user data a loan exposes; never carries the password hash or role.
data class Borrower(val id: Int, val name: String, val email: String)

fun User.toBorrower() = Borrower(id = id, name = name, email = email)
