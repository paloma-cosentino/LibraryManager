package org.example.http

import org.example.model.Book
import org.example.model.BookCategory
import org.example.model.BookFilter
import org.example.model.Exceptions
import org.example.model.PagedResult
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
import org.http4k.lens.Query
import org.http4k.lens.int

class BookHandler(private val service: BookService) {
    private val bookLens        = Body.auto<Book>().toLens()
    private val bookRequestLens = Body.auto<BookRequest>().toLens()
    private val idPath          = Path.int().of("id")

    private val titleQuery    = Query.optional("title")
    private val authorQuery   = Query.optional("author")
    private val categoryQuery = Query.optional("category")
    private val pageQuery     = Query.int().defaulted("page", 1)
    private val sizeQuery     = Query.int().defaulted("size", 10)

    private val pagedBooksLens = Body.auto<PagedResult<Book>>().toLens()

    val getAll: HttpHandler = { request ->
        val filter = BookFilter(
            title = titleQuery(request),
            author = authorQuery(request),
            category = categoryQuery(request),
            page = pageQuery(request).coerceAtLeast(1),
            size = sizeQuery(request).coerceIn(1, 100)  // máximo 100 por página
        )
        pagedBooksLens(service.getAllBooks(filter), Response(OK))
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