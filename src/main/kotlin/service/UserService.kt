package org.example.service

import org.example.model.Actor
import org.example.model.AuthorizationExceptions
import org.example.model.LoginResponse
import org.example.model.PagedResult
import org.example.model.Role
import org.example.model.User
import org.example.model.UserExceptions
import org.example.model.UserFilter
import org.example.model.toResponse
import org.example.repository.UserRepository
import org.example.security.JwtService
import org.mindrot.jbcrypt.BCrypt

class UserService(private val repository: UserRepository): IUserService {

    override fun getAllUsers(filter: UserFilter): PagedResult<User> =
        repository.findAll(filter)

    override fun getUserById(id: Int) =
        repository.findById(id)
        ?: throw UserExceptions.UserIdNotFoundException(id)

    // A USER may only look up their own account; any other email is forbidden whether or not it
    // exists, so the endpoint cannot be used to probe which emails are registered.
    override fun getUserByEmail(actor: Actor, email: String): User {
        val user = repository.findByEmail(email)
        if (!actor.isAdmin && user?.id != actor.userId) throw AuthorizationExceptions.ForbiddenException()
        return user ?: throw UserExceptions.UserEmailNotFoundException(email)
    }

    override fun addUser(name: String, email: String, password: String): User {
        if (repository.findByEmail(email) != null) {
            throw UserExceptions.DuplicateUserException(email)
        }
        val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())
        // Sign-up never grants privilege; ADMIN only via ADMIN_EMAILS bootstrap or an admin action.
        return repository.save(User(name = name, email = email, passwordHash = passwordHash, role = Role.USER))
    }

    override fun updateUser(user: User) = repository.update(user)

    // Used by the HTTP layer: never accepts/touches passwordHash, so clients
    // (and GET responses, which no longer expose the hash) don't need to
    // know it just to rename a user or change their email.
    override fun updateUserProfile(actor: Actor, id: Int, name: String, email: String): User? {
        requireSelfOrAdmin(actor, id)
        val current = repository.findById(id) ?: return null
        return repository.update(current.copy(name = name, email = email))
    }

    // Checked before any lookup so a USER cannot learn whether another id exists.
    private fun requireSelfOrAdmin(actor: Actor, id: Int) {
        if (!actor.isAdmin && actor.userId != id) throw AuthorizationExceptions.ForbiddenException()
    }

    // Self-service password change: requires proving the current password,
    // since this is reachable by any authenticated user for their own id.
    override fun changePassword(actor: Actor, id: Int, currentPassword: String, newPassword: String) {
        // Own account only, admins included: admins manage profiles, never other people's credentials.
        if (actor.userId != id) throw AuthorizationExceptions.ForbiddenException()
        val current = repository.findById(id)
            ?: throw UserExceptions.UserIdNotFoundException(id)

        if (!BCrypt.checkpw(currentPassword, current.passwordHash)) {
            throw UserExceptions.InvalidCredentialsException()
        }

        val newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt())
        repository.update(current.copy(passwordHash = newHash))
    }

    override fun removeUser(id: Int) = repository.delete(id)

    // Startup bootstrap from ADMIN_EMAILS: only promotes existing accounts (exact email match),
    // never creates accounts and never demotes anyone, so rerunning it is harmless.
    fun promoteAdmins(emails: Collection<String>): List<String> =
        emails.map(String::trim).filter(String::isNotEmpty).filterNot { email ->
            val user = repository.findByEmail(email)
            user != null && (user.role == Role.ADMIN || repository.updateRole(user.id, Role.ADMIN))
        }

    override fun login(email: String, password: String): LoginResponse {
        val user = repository.findByEmail(email)
            ?: throw UserExceptions.UserEmailNotFoundException(email)

        if (!BCrypt.checkpw(password, user.passwordHash)) {
            throw UserExceptions.InvalidCredentialsException()
        }

        val token = JwtService.generateToken(user.id, user.email)
        return LoginResponse(token = token, user = user.toResponse())
    }
}