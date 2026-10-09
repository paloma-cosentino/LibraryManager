package org.example.http

import org.http4k.core.Method.GET
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static

fun uiRoutes(handler: UiHandler) = routes(
    "/ui/login"   bind GET to handler.login,
    "/ui/books"   bind GET to handler.books,
    "/ui/authors" bind GET to handler.authors,
    "/ui/loans"   bind GET to handler.loans,
    "/ui/users"   bind GET to handler.users,
    "/static"     bind static(ResourceLoader.Directory("src/main/resources/static"))
)



