package org.example.http

import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.routing.bind
import org.http4k.routing.routes

class AuthorRoutes {
    fun authorRoutes(handler: AuthorHandler) = routes(
        "/authors"      bind GET    to handler.getAll,
        "/authors"      bind POST   to handler.create,
        "/authors/{id}" bind GET    to handler.getById,
        "/authors/{id}" bind PUT    to handler.update,
        "/authors/{id}" bind DELETE to handler.delete
    )
}

