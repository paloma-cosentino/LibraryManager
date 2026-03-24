package org.example.http

import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.routing.bind
import org.http4k.routing.routes

class BookRoutes {
    fun bookRoutes(handler: BookHandler) = routes(
        "/books"      bind GET    to handler.getAll,
        "/books"      bind POST   to handler.create,
        "/books/{id}" bind GET    to handler.getById,
        "/books/{id}" bind PUT    to handler.update,
        "/books/{id}" bind DELETE to handler.delete
    )
}