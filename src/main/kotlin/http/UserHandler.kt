package org.example.http

import org.example.model.LoginResponse
import org.example.model.PagedResult
import org.example.model.User
import org.example.model.UserFilter
import org.example.model.UserResponse
import org.example.model.toResponse
import org.example.service.IUserService
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.*
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.routing.bind
import org.http4k.routing.routes

class UserHandler(private val service: IUserService) {
    private val userLens = Body.auto<User>().toLens()
    private val userResponseLens = Body.auto<UserResponse>().toLens()
    private val updateUserRequestLens = Body.auto<UpdateUserRequest>().toLens()
    private val changePasswordRequestLens = Body.auto<ChangePasswordRequest>().toLens()
    private val loginRequestLens = Body.auto<LoginRequest>().toLens()
    private val loginResponseLens = Body.auto<LoginResponse>().toLens()
    private val idPath   = Path.int().of("id")
    
    private val nameQuery = Query.optional("name")
    private val emailQuery = Query.required("email")
    private val pageQuery = Query.int().defaulted("page", 1)
    private val sizeQuery = Query.int().defaulted("size", 10)
    
    private val pagedUserResponseLens = Body.auto<PagedResult<UserResponse>>().toLens()
    
    val getAll: HttpHandler = { request ->
        val filter = UserFilter(
            name = nameQuery(request),
            page = pageQuery(request).coerceAtLeast(1),
            size = sizeQuery(request).coerceIn(1, 100)
        )
        val result = service.getAllUsers(filter)
        // Never serialize the full User (and its passwordHash) over the wire.
        val response = PagedResult(
            data = result.data.map { it.toResponse() },
            page = result.page,
            pageSize = result.pageSize,
            total = result.total,
            totalPages = result.totalPages
        )
        pagedUserResponseLens(response, Response.Companion(Status.OK))
    }

    val getByEmail: HttpHandler = { request ->
        handleUserExceptions {
            val user = service.getUserByEmail(emailQuery(request))
            userResponseLens(user.toResponse(), Response.Companion(Status.OK))
        }
    }

    // Only name/email/password are taken from the sign-up body; any "role" it carries is ignored.
    val create: HttpHandler = { request ->
        handleUserExceptions {
            val saved = service.addUser(
                name = userLens(request).name,
                email = userLens(request).email,
                password = userLens(request).passwordHash
            )
            userResponseLens(saved.toResponse(), Response.Companion(Status.OK))
        }
    }

    val update: HttpHandler = { request ->
        handleUserExceptions {
            val body = updateUserRequestLens(request)
            val updated = service.updateUserProfile(idPath(request), body.name, body.email)
            if (updated == null) Response.Companion(Status.NOT_FOUND)
            else userResponseLens(updated.toResponse(), Response.Companion(Status.OK))
        }
    }

    val changePassword: HttpHandler = { request ->
        handleUserExceptions {
            val body = changePasswordRequestLens(request)
            service.changePassword(idPath(request), body.currentPassword, body.newPassword)
            Response.Companion(Status.NO_CONTENT)
        }
    }

    val delete: HttpHandler = { request ->
        handleUserExceptions {
            if (service.removeUser(idPath(request))) Response.Companion(Status.NO_CONTENT)
            else Response.Companion(Status.NOT_FOUND)
        }
    }
    val login: HttpHandler = { request ->
        val body = loginRequestLens(request)
        handleUserExceptions {
            val response = service.login(body.email, body.password)
            loginResponseLens(response, Response(Status.OK))
        }
    }
}

fun userRoutes(handler: UserHandler) = routes(
    "/users" bind GET to handler.getAll,
    "/users/email" bind GET to handler.getByEmail,
    "/users/{id}" bind PUT to handler.update,
    "/users/{id}/password" bind PATCH to handler.changePassword,
    "/users/{id}" bind DELETE to handler.delete,
)

fun publicUserRoutes(handler: UserHandler) = routes(
    "/auth/login" bind POST to handler.login,
    "/users" bind POST to handler.create
)
