package org.example.http

import org.example.model.AuthorExceptions
import org.example.model.BookExceptions
import org.example.model.LoanExceptions
import org.example.model.UserExceptions
import org.example.repository.ConcurrentUpdateException
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.lens.LensFailure

// Reached only once retryOnConflict has given up; the client may safely retry the whole request.
private fun conflictAfterRetries(e: ConcurrentUpdateException) =
    Response(CONFLICT).body("${e.message}, try again")

fun handleBookExceptions(block: () -> Response): Response = try {
    block()
} catch (e: BookExceptions.BookNotFoundException)    { Response(NOT_FOUND).body(e.message!!) }
catch (e: BookExceptions.DuplicateBookException)   { Response(CONFLICT).body(e.message!!) }
catch (e: BookExceptions.InvalidCategoryException) { Response(BAD_REQUEST).body(e.message!!) }
catch (e: BookExceptions.InvalidQuantityException) { Response(BAD_REQUEST).body(e.message!!) }
catch (e: BookExceptions.InsufficientQuantityException) { Response(CONFLICT).body(e.message!!) }
catch (e: ConcurrentUpdateException)               { conflictAfterRetries(e) }
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
catch (e: ConcurrentUpdateException)                  { conflictAfterRetries(e) }
catch (e: BookExceptions.BookNotFoundException)       { Response(NOT_FOUND).body(e.message!!) }
catch (e: LensFailure)                                { Response(BAD_REQUEST).body(e.message ?: "Invalid request body")
}

fun handleUserExceptions(block: () -> Response): Response = try {
    block()
} catch (e: UserExceptions.UserIdNotFoundException)      { Response(NOT_FOUND).body(e.message!!) }
catch (e: UserExceptions.UserEmailNotFoundException)   { Response(NOT_FOUND).body(e.message!!) }
catch (e: UserExceptions.DuplicateUserException)       { Response(CONFLICT).body(e.message!!) }
catch (e: UserExceptions.InvalidCredentialsException) { Response(UNAUTHORIZED).body(e.message!!) }
catch (e: LensFailure)                                 { Response(BAD_REQUEST).body(e.message ?: "Invalid request body") }
