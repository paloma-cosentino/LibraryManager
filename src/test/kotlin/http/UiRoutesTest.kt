package org.example.http

import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.OK
import kotlin.test.*

/** Templates render and still carry the element ids the role-aware scripts depend on. */
class UiRoutesTest {

    private val ui = uiRoutes(UiHandler())

    private fun assertPage(path: String, vararg ids: String) {
        val response = ui(Request(GET, path))
        assertEquals(OK, response.status, path)
        val html = response.bodyString()
        ids.forEach { assertTrue("id=\"$it\"" in html, "$path is missing #$it") }
    }

    @Test
    fun `books page`() = assertPage("/ui/books", "addBookBtn", "actionsHeader", "booksBody", "bookModal", "quantityChangeGroup")

    @Test
    fun `authors page`() = assertPage("/ui/authors", "addAuthorBtn", "actionsHeader", "authorsBody", "authorModal")

    @Test
    fun `loans page has the admin account picker and no free-text borrower`() {
        assertPage("/ui/loans", "filterBorrower", "actionsHeader", "loansBody", "loanForm", "bookId", "borrowerGroup", "borrowerId", "daysToReturn")
        assertFalse("borrowerName" in ui(Request(GET, "/ui/loans")).bodyString())
    }

    @Test
    fun `users page`() = assertPage(
        "/ui/users", "usersToolbar", "usersPagination", "roleHeader", "usersBody", "userForm", "passwordForm",
        "pageIndicator", "prevPageBtn", "nextPageBtn"
    )

    @Test
    fun `login page`() = assertPage("/ui/login", "loginForm", "email", "password")

    @Test
    fun `admin-only controls start hidden until the server confirms the role`() {
        listOf(
            "/ui/books" to "addBookBtn", "/ui/authors" to "addAuthorBtn",
            "/ui/loans" to "borrowerGroup", "/ui/users" to "roleHeader"
        ).forEach { (path, id) ->
            val tag = Regex("<[^>]*id=\"$id\"[^>]*>").find(ui(Request(GET, path)).bodyString())?.value
            assertTrue(tag != null && " hidden" in tag, "$path #$id should start hidden: $tag")
        }
    }
}
