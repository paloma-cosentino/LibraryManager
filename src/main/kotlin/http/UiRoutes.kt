package org.example.http

import org.http4k.core.Method.GET
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static

fun uiRoutes(handler: UiHandler) = routes(
    "/ui/login" bind GET to handler.login,
    "/static"   bind static(ResourceLoader.Directory("src/main/resources/static"))
)

