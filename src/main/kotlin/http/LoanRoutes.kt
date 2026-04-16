package org.example.http

import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.PATCH
import org.http4k.routing.bind
import org.http4k.routing.routes

fun loanRoutes(handler: LoanHandler) = routes(
    "/loans"              bind GET   to handler.getAll,
    "/loans"              bind POST  to handler.borrow,
    "/loans/{id}"         bind GET   to handler.getById,
    "/loans/{id}/return"  bind PATCH to handler.returnBook
)

