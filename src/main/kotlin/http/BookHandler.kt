package org.example.http

import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.Exceptions
import org.example.service.BookService
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson.auto
import org.http4k.lens.LensFailure
import org.http4k.lens.Path
import org.http4k.lens.int

class BookHandler(private val service: BookService) {
    private val bookLens        = Body.auto<Book>().toLens()
    private val booksLens       = Body.auto<List<Book>>().toLens()
    private val bookRequestLens = Body.auto<BookRequest>().toLens()
    private val idPath          = Path.int().of("id")

    val getAll: HttpHandler = {
        booksLens(service.getAllBooks(), Response(OK))
    }

    val getById: HttpHandler = {request ->
        try {
        val book = service.getBookById(idPath(request))
        bookLens(book, Response(OK))
        } catch (e: Exceptions.BookNotFoundException) {
            Response(NOT_FOUND).body(e.message ?: "Book not found")
        }
    }

    val create: HttpHandler = { request ->
        try {
            val body = bookRequestLens(request)
            val saved = service.addBook(
                title      = body.title,
                authorName = body.author,
                category   = body.category,
                quantity   = body.quantity,
                publishedYear = body.publishedYear,
                edition   = body.edition
            )
            bookLens(saved, Response(OK))
        } catch (e: Exceptions.AuthorNotFoundException) {
            Response(NOT_FOUND).body(e.message ?: "Author not found")
        } catch (e: Exceptions.DuplicateBookException) {
            Response(CONFLICT).body(e.message ?: "Book already exists")
        } catch (e: Exceptions.InvalidCategoryException) {
            Response(BAD_REQUEST).body(e.message ?: "Invalid category")
        } catch (e: LensFailure) {
            Response(BAD_REQUEST).body(e.message ?:
                "Invalid category. Valid values: ${BookCategory.entries.map { it.displayName }}"
            )
        }
    }
    val update: HttpHandler = { request ->
        try {
            val body    = bookRequestLens(request)
            val updated = service.updateBook(
                id         = idPath(request),
                title      = body.title,
                authorName = body.author,
                category   = body.category,
                quantity   = body.quantity,
                publishedYear = body.publishedYear,
                edition   = body.edition
            )
            if (updated == null) Response(NOT_FOUND)
            else bookLens(updated, Response(OK))
        } catch (e: Exceptions.AuthorNotFoundException) {
            Response(NOT_FOUND).body(e.message ?: "Author not found")
        } catch (e: Exceptions.InvalidCategoryException) {
            Response(BAD_REQUEST).body(e.message ?: "Invalid category")
        }
    }

    val delete: HttpHandler = {request ->
        if (service.removeBook(idPath(request))) Response(NO_CONTENT)
        else Response(NOT_FOUND)
    }
}