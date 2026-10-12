# 🖥️ UI Implementation Guide

This guide explains what has been built in the UI layer and how to follow the same pattern to add further pages.

---

## What's implemented

| File | Description |
|------|-------------|
| `src/main/kotlin/http/UiHandler.kt` | `ViewModel`s + handlers that render Handlebars templates |
| `src/main/kotlin/http/UiRoutes.kt` | UI routes (`/ui/*`) and static file serving (`/static`) — all public, see note below |
| `src/main/resources/templates/layout.hbs` | Shared base layout (doctype/head/navbar/footer) used via Handlebars **partial blocks** |
| `src/main/resources/templates/header.hbs` | Navbar partial, with active-link highlighting per page |
| `src/main/resources/templates/footer.hbs` | Footer partial — just the shared `api.js` include |
| `src/main/resources/templates/login.hbs` | Login page (doesn't use `layout.hbs`, since it has no navbar) |
| `src/main/resources/templates/books.hbs` | Books list/search/create/edit/delete |
| `src/main/resources/templates/authors.hbs` | Authors list/search/create/edit/delete |
| `src/main/resources/templates/loans.hbs` | Loans list/search, borrow form, return action |
| `src/main/resources/templates/users.hbs` | Users list/search/edit/delete, plus a self-service "change password" modal |
| `src/main/resources/static/css/style.css` | Global styles: navbar, forms, tables, modals, pagination, badges |
| `src/main/resources/static/js/api.js` | `apiFetch` (JWT-aware fetch), `qs`, `escapeHtml`, `requireAuth`, `logout`, `currentUser` |
| `src/main/resources/static/js/*.js` | One script per page (`login.js`, `books.js`, `authors.js`, `loans.js`, `users.js`) |

| Page | Route | Template | JS | Status |
|------|-------|----------|----|--------|
| Login | `/ui/login` | `login.hbs` | `login.js` | ✅ |
| Books | `/ui/books` | `books.hbs` | `books.js` | ✅ |
| Authors | `/ui/authors` | `authors.hbs` | `authors.js` | ✅ |
| Loans | `/ui/loans` | `loans.hbs` | `loans.js` | ✅ |
| Users | `/ui/users` | `users.hbs` | `users.js` | ✅ |

---

## ⚠️ Public vs. protected routes — important lesson learned

**All `/ui/*` page routes (including books/authors/loans/users) are registered as public**, inside the same `uiRoutes(uiHandler)` call that's passed to `publicRoutes` in `Main.kt`. This is intentional, for two reasons:

1. **Browser navigation doesn't send the JWT.** The token lives in `localStorage` and is only attached manually by `apiFetch()` for `fetch()` calls. A plain `<a href="/ui/books">` click is a normal GET request with no `Authorization` header. If `/ui/books` were registered behind `authFilter`, every direct navigation without a token would get a bare `401 Unauthorized` response instead of the HTML page — so the client-side redirect-to-login logic would never even get a chance to run.
2. **The real security boundary is the REST API**, not the HTML shell. Each page's `.js` file calls `requireAuth()` on load (redirects to `/ui/login` if there's no token) and all data comes from `apiFetch()` calls to the already-protected `/books`, `/authors`, `/loans`, `/users` endpoints. Even if someone loaded the HTML directly, they'd see an empty page with no data, since every API call would 401.

**A real routing bug this uncovered:** the original `Main.kt` tried two separately-invoked routers and only treated a `404 NOT_FOUND` response as "try the other router":

```kotlin
// ❌ Don't do this — breaks as soon as a path is registered in both routers
// with different methods (e.g. POST /users public, GET /users protected).
val app = { request: Request ->
    val publicResponse = publicRoutes(request)
    if (publicResponse.status != Status.NOT_FOUND) publicResponse
    else authFilter(protectedRoutes)(request)
}
```

Because `POST /users` is public, a `GET /users` request matched that same path in the public router and got back `405 Method Not Allowed` — which is **not** `404`, so the code above returned the bare 405 and never even tried `protectedRoutes`, where the real `GET /users` handler lives. `GET /users` was completely broken until this was fixed.

**The fix** — scope the filter to just the protected group with `.withFilter(...)`, then combine everything into a **single flat `routes(...)` call** so http4k can correctly resolve 404 vs. 405 across *all* registered routes at once:

```kotlin
val protectedRoutes = routes(
    bookRoutes(bookHandler),
    authorRoutes(authorHandler),
    loanRoutes(loanHandler),
    userRoutes(userHandler)
).withFilter(Filter(::authFilter))

val app = routes(
    publicUserRoutes(userHandler),
    uiRoutes(uiHandler),
    protectedRoutes
)
```

---

## How the pattern works

### Render flow

```
GET /ui/books
     │
     ▼
UiHandler.books (Kotlin)        creates BooksView(...)
     │
     ▼
HandlebarsTemplates            renders books.hbs, wrapped in layout.hbs
     │
     ▼
Response (text/html)
```

### Shared layout via partial blocks

`layout.hbs` owns the `<!DOCTYPE>`, `<head>`, navbar and footer. Every page wraps its own content in a Handlebars **partial block** so it doesn't have to repeat that boilerplate:

```handlebars
{{#> layout pageTitle="Books" isBooks=true}}
    <h2>📚 Books</h2>
    ... page content ...
{{/layout}}
```

Inside `layout.hbs`, `{{> @partial-block}}` is replaced with everything between `{{#> layout ...}}` and `{{/layout}}`. Hash arguments (`pageTitle="Books"`, `isBooks=true`) become template variables visible both in `layout.hbs` and in the `{{> header}}` partial it includes (used for active-link highlighting — see `header.hbs`).

`login.hbs` is the one exception: it doesn't use `layout.hbs` (no navbar before login), but still reuses `{{> footer}}` for the shared `api.js` script tag.

### Data flow (after the HTML loads)

```
books.hbs loads in the browser
     │
     ▼
books.js runs — requireAuth() first
     │  calls apiFetch("/books" + qs(filters))
     ▼
api.js adds "Authorization: Bearer <token>"
     │
     ▼
GET /books (REST API, behind authFilter)
     │
     ▼
JSON → renders rows into the table (via DOM APIs + escapeHtml, not raw innerHTML
       interpolation, to avoid injecting unescaped user data)
```

---

## How to add a new page (recap, using the pattern above)

1. **ViewModel + handler** in `UiHandler.kt`:
   ```kotlin
   data class BooksView(val pageTitle: String = "Books") : ViewModel {
       val isBooks = true
       override fun template() = "books"
   }
   val books: HttpHandler = { Response(Status.OK).with(viewLens of BooksView()) }
   ```
2. **Route** in `UiRoutes.kt`: add `"/ui/books" bind GET to handler.books` inside `uiRoutes(...)`.
3. **Template** `books.hbs`: wrap content in `{{#> layout pageTitle="..." isBooks=true}} ... {{/layout}}`.
4. **Script** `books.js`: start with `requireAuth();`, then use `apiFetch`/`qs`/`escapeHtml` from `api.js` to load and render data.
5. **Styles**: reuse the existing `.toolbar`, `.data-table`, `.pagination`, `.modal` classes in `style.css` rather than inventing new ones per page.

---

## Reference: `api.js`

Available globally on every page (loaded via `{{> footer}}`):

```javascript
// GET with the JWT attached automatically
apiFetch("/books" + qs({ title: "Dune", page: 1, size: 10 }))

// POST / PUT with a JSON body
apiFetch("/books", { method: "POST", body: JSON.stringify({ title: "Dune", ... }) })
apiFetch(`/books/${id}`, { method: "PUT", body: JSON.stringify({ ... }) })

// DELETE
apiFetch(`/books/${id}`, { method: "DELETE" })

// Builds "?a=1&b=2", skipping empty/undefined/null values
qs({ title: "", category: "Fantasy", page: 1 })   // → "?category=Fantasy&page=1"

// Escapes user-controlled text before interpolating into innerHTML
escapeHtml(book.title)

// Current logged-in user from localStorage, or null
currentUser()   // → { id, name, email } | null

// Clears the token and redirects to /ui/login — also called automatically
// by apiFetch() whenever any API call responds with 401 (expired/invalid token)
logout()

// Redirects to /ui/login if there's no token saved — call this at the top
// of every page's .js file
requireAuth()
```

---

## Known limitations / follow-ups worth doing next

1. ~~**`GET /users` and `GET /users/email` leak `passwordHash`**~~ — **fixed.** Both now return `UserResponse` (`id`/`name`/`email` only), `PUT /users/{id}` takes a dedicated `UpdateUserRequest(name, email)` body (no password field, nothing to round-trip), and a new `PATCH /users/{id}/password` endpoint (`ChangePasswordRequest(currentPassword, newPassword)`) handles password changes — it verifies the current password server-side before re-hashing, so it's a *self-service* operation only. The Users page now has a 🔑 "Change password" button, shown only on the logged-in user's own row (in place of the delete button, which is likewise hidden for your own account), opening a small modal that calls the new endpoint directly with `fetch()` (not `apiFetch()`, since a wrong current-password legitimately returns `401` here and `apiFetch()` treats any `401` as an expired session and force-logs-out — see `users.js`). There's still no "admin reset someone else's password" capability — that would need a permissions model this project doesn't have yet — but that's an intentionally separate, bigger feature, not a leftover from this fix.
2. **No sign-up page** — `POST /users` (registration) is only reachable via direct API calls today; there's no `register.hbs`. `/ui/users` is an admin-style management screen for *existing* users, not a self-service sign-up form.
3. **Loan creation uses a plain `<select>` of all books** (fetched via `/books?size=100`). Fine for a small library; would need real pagination/search in the dropdown for a larger catalog.
4. **Pagination is simple Prev/Next**, no jump-to-page or page-size selector.




