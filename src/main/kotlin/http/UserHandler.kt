package org.example.http

import org.example.model.PagedResult
import org.example.model.User
import org.example.model.UserFilter
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
    private val idPath   = Path.int().of("id")
    
    private val nameQuery = Query.optional("name")
    private val emailQuery = Query.required("email")
    private val pageQuery = Query.int().defaulted("page", 1)
    private val sizeQuery = Query.int().defaulted("size", 10)
    
    private val pagedUserLens = Body.auto<PagedResult<User>>().toLens()
    
    val getAll: HttpHandler = { request ->
        val filter = UserFilter(
            name = nameQuery(request),
            page = pageQuery(request).coerceAtLeast(1),
            size = sizeQuery(request).coerceIn(1, 100)
        )
        pagedUserLens(service.getAllUsers(filter), Response.Companion(Status.OK))
    }

    val getByEmail: HttpHandler = { request ->
        handleUserExceptions {
            userLens(service.getUserByEmail(emailQuery(request)), Response.Companion(Status.OK))
        }
    }

    val create: HttpHandler = { request ->
        handleUserExceptions {
            val saved = service.addUser(
                name = userLens(request).name,
                email = userLens(request).email
            )
            userLens(saved, Response.Companion(Status.OK))
        }
    }

    val update: HttpHandler = { request ->
        handleUserExceptions {
            val updated = service.updateUser(userLens(request).copy(id = idPath(request)))
            if (updated == null) Response.Companion(Status.NOT_FOUND)
            else userLens(updated, Response.Companion(Status.OK))
        }
    }

    val delete: HttpHandler = { request ->
        handleUserExceptions {
            if (service.removeUser(idPath(request))) Response.Companion(Status.NO_CONTENT)
            else Response.Companion(Status.NOT_FOUND)
        }
    }
}

fun userRoutes(handler: UserHandler) = routes(
    "/users" bind GET to handler.getAll,
    "/users/email" bind GET to handler.getByEmail,
    "/users" bind POST to handler.create,
    "/users/{id}" bind PUT to handler.update,
    "/users/{id}" bind DELETE to handler.delete
)
