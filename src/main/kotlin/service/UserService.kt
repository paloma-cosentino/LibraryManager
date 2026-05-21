package org.example.service

import org.example.model.LoginResponse
import org.example.model.PagedResult
import org.example.model.User
import org.example.model.UserExceptions
import org.example.model.UserFilter
import org.example.model.UserResponse
import org.example.repository.UserRepository
import org.example.security.JwtService
import org.mindrot.jbcrypt.BCrypt

class UserService(private val repository: UserRepository): IUserService {

    override fun getAllUsers(filter: UserFilter): PagedResult<User> =
        repository.findAll(filter)

    override fun getUserById(id: Int) =
        repository.findById(id)
        ?: throw UserExceptions.UserIdNotFoundException(id)

    override fun getUserByEmail(email: String) =
        repository.findByEmail(email)
        ?: throw UserExceptions.UserEmailNotFoundException(email)

    override fun addUser(name: String, email: String, password: String): User {
        if (repository.findByEmail(email) != null) {
            throw UserExceptions.DuplicateUserException(email)
        }
        val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())
        return repository.save(User(name = name, email = email, passwordHash = passwordHash))
    }

    override fun updateUser(user: User) = repository.update(user)

    override fun removeUser(id: Int) = repository.delete(id)

    override fun login(email: String, password: String): LoginResponse {
        val user = repository.findByEmail(email)
            ?: throw UserExceptions.UserEmailNotFoundException(email)

        if (!BCrypt.checkpw(password, user.passwordHash)) {
            throw UserExceptions.InvalidCredentialsException()
        }

        val token = JwtService.generateToken(user.id, user.email)
        val userResponse = UserResponse(id = user.id, name = user.name, email = user.email)
        return LoginResponse(token = token, user = userResponse)
    }
}