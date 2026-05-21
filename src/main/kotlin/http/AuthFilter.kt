package org.example.http

import org.example.security.JwtService
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status

fun authFilter(next: HttpHandler): HttpHandler = { request ->
    val token = request.header("Authorization")?.removePrefix("Bearer ")
    if (token == null || !JwtService.validateToken(token)) {
        Response(Status.UNAUTHORIZED)
    } else {
        next(request)
    }
}