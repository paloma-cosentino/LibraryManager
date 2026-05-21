package org.example.model

object UserExceptions {
    class UserIdNotFoundException(id: Int) : IllegalArgumentException("User [$id] not found")
    class UserEmailNotFoundException(email: String) : IllegalArgumentException("User [$email] not found")
    class DuplicateUserException(email: String) : IllegalArgumentException("User with email [$email] already exists")
    class InvalidCredentialsException : IllegalArgumentException("Invalid email or password")
}