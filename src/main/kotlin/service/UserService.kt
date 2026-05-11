package org.example.service

import org.example.model.PagedResult
import org.example.model.User
import org.example.model.UserExceptions
import org.example.model.UserFilter
import org.example.repository.UserRepository

class UserService(private val repository: UserRepository): IUserService {
    override fun getAllUsers(filter: UserFilter): PagedResult<User> =
        repository.findAll(filter)

    override fun getUserById(id: Int) =
        repository.findById(id)
        ?: throw UserExceptions.UserIdNotFoundException(id)

    override fun getUserByEmail(email: String) = repository.findByEmail(email)
        ?: throw UserExceptions.UserEmailNotFoundException(email)

    override fun addUser(name: String, email: String): User {
        if (repository.findByEmail(email) != null){
            throw UserExceptions.DuplicateUserException(email)
        }
        return repository.save(User(name = name, email = email))
    }

    override fun updateUser(user: User) = repository.update(user)

    override fun removeUser(id: Int) = repository.delete(id)

}