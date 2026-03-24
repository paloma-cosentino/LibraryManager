package org.example.http

import org.http4k.core.HttpHandler
import org.example.model.Book
import org.example.service.BookService
import org.http4k.core.Body
import org.http4k.core.Response
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.int
import org.http4k.core.Status.Companion.OK

class BookHandler(private val service: BookService) {
    private val bookLens  = Body.auto<Book>().toLens()
    private val booksLens = Body.auto<List<Book>>().toLens()
    private val idPath    = Path.int().of("id")

    val getAll: HttpHandler = {
        booksLens(service.getAllBooks(), Response(OK))
    }

    val getById: HttpHandler = {request ->
        val book = service.getBookById(idPath(request))
        if (book == null) Response(NOT_FOUND)
        else bookLens(book, Response(OK))
    }

    val create: HttpHandler = { request ->
        val body = bookLens(request)
        val saved = service.addBook(
            title = body.title,
            author = body.author,
            category = body.category,
            quantity = body.quantity
        )
        bookLens(saved, Response(OK))

    }
    val update: HttpHandler = {request ->
        val update = service.updateBook(bookLens(request).copy(id = idPath(request)))
        if (update == null) Response(NOT_FOUND)
        else bookLens(update, Response(OK))
    }

    val delete: HttpHandler = {request ->
        if (service.removeBook(idPath(request))) Response(NO_CONTENT)
        else Response(NOT_FOUND)
    }
}