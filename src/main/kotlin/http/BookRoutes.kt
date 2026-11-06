package org.example.http

import org.example.model.Book
import org.example.model.BookFilter
import org.example.model.PagedResult
import org.example.service.IBookService
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.routing.bind
import org.http4k.routing.routes

fun bookRoutes(handler: BookHandler) = routes(
    "/books"      bind GET    to handler.getAll,
    "/books"      bind POST   to requireAdmin.then(handler.create),
    "/books/{id}" bind GET    to handler.getById,
    "/books/{id}" bind PUT    to requireAdmin.then(handler.update),
    "/books/{id}/quantity" bind PATCH to requireAdmin.then(handler.adjustQuantity),
    "/books/{id}" bind DELETE to requireAdmin.then(handler.delete)
)

class BookHandler(private val service: IBookService) {
    private val bookLens        = Body.auto<Book>().toLens()
    private val bookRequestLens = Body.auto<BookRequest>().toLens()
    private val bookDetailsRequestLens = Body.auto<BookDetailsRequest>().toLens()
    private val quantityChangeRequestLens = Body.auto<QuantityChangeRequest>().toLens()
    private val pagedBooksLens  = Body.auto<PagedResult<Book>>().toLens()
    private val idPath          = Path.int().of("id")

    private val titleQuery    = Query.optional("title")
    private val authorQuery   = Query.optional("author")
    private val categoryQuery = Query.optional("category")
    private val pageQuery     = Query.int().defaulted("page", 1)
    private val sizeQuery     = Query.int().defaulted("size", 10)

    val getAll: HttpHandler = { request ->
        val filter = BookFilter(
            title    = titleQuery(request),
            author   = authorQuery(request),
            category = categoryQuery(request),
            page     = pageQuery(request).coerceAtLeast(1),
            size     = sizeQuery(request).coerceIn(1, 100)
        )
        pagedBooksLens(service.getAllBooks(filter), Response(OK))
    }

    val getById: HttpHandler = { request ->
        handleBookExceptions {
            bookLens(service.getBookById(idPath(request)), Response(OK))
        }
    }

    val create: HttpHandler = { request ->
        handleBookExceptions {
            val body = bookRequestLens(request)
            bookLens(service.addBook(body.title, body.author, body.category, body.quantity, body.publishedYear, body.edition), Response(OK))
        }
    }

    val update: HttpHandler = { request ->
        handleBookExceptions {
            val body    = bookDetailsRequestLens(request)
            if (body.quantity != null) {
                return@handleBookExceptions Response(BAD_REQUEST)
                    .body("PUT /books/{id} updates details only; use PATCH /books/{id}/quantity to change stock")
            }
            val updated = service.updateBook(idPath(request), body.title, body.author, body.category, body.publishedYear, body.edition)
            if (updated == null) Response(NOT_FOUND) else bookLens(updated, Response(OK))
        }
    }

    val adjustQuantity: HttpHandler = { request ->
        handleBookExceptions {
            val body = quantityChangeRequestLens(request)
            bookLens(service.adjustBookQuantity(idPath(request), body.change), Response(OK))
        }
    }

    val delete: HttpHandler = { request ->
        handleBookExceptions {
            if (service.removeBook(idPath(request))) Response(NO_CONTENT)
            else Response(NOT_FOUND)
        }
    }
}

