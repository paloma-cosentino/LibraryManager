package org.example.http

import org.example.model.AuthorExceptions
import org.example.model.BookExceptions
import org.example.model.LoanExceptions
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.lens.LensFailure

fun handleBookExceptions(block: () -> Response): Response = try {
    block()
} catch (e: BookExceptions.BookNotFoundException)    { Response(NOT_FOUND).body(e.message!!) }
catch (e: BookExceptions.DuplicateBookException)   { Response(CONFLICT).body(e.message!!) }
catch (e: BookExceptions.InvalidCategoryException) { Response(BAD_REQUEST).body(e.message!!) }
catch (e: AuthorExceptions.AuthorNotFoundException)  { Response(NOT_FOUND).body(e.message!!) }
catch (e: LensFailure)                         { Response(BAD_REQUEST).body(e.message ?: "Invalid request body") }

fun handleAuthorExceptions(block: () -> Response): Response = try {
    block()
} catch (e: AuthorExceptions.AuthorNotFoundException)  { Response(NOT_FOUND).body(e.message!!) }
catch (e: AuthorExceptions.DuplicateAuthorException) { Response(CONFLICT).body(e.message!!) }

fun handleLoanExceptions(block: () -> Response): Response = try{
    block()
} catch (e: LoanExceptions.LoanNotFoundException)       { Response(NOT_FOUND).body(e.message!!) }
catch (e: LoanExceptions.BookNotAvailableException)   { Response(CONFLICT).body(e.message!!) }
catch (e: LoanExceptions.LoanAlreadyReturnedException){ Response(CONFLICT).body(e.message!!) }
catch (e: BookExceptions.BookNotFoundException)       { Response(NOT_FOUND).body(e.message!!) }
catch (e: LensFailure)                                { Response(BAD_REQUEST).body(e.message ?: "Invalid request body") }