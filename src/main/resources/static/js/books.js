requireAuth();

let currentPage = 1;
const pageSize = 10;
let totalPages = 1;
let admin = false;

function currentFilters() {
    return {
        title: document.getElementById("filterTitle").value.trim(),
        author: document.getElementById("filterAuthor").value.trim(),
        category: document.getElementById("filterCategory").value,
        page: currentPage,
        size: pageSize
    };
}

async function loadBooks() {
    const response = await apiFetch("/books" + qs(currentFilters()));
    if (!response.ok) return;

    const result = await response.json();
    totalPages = result.totalPages || 1;
    renderBooks(result.data);

    document.getElementById("pageIndicator").textContent = `Page ${result.page} of ${totalPages}`;
    document.getElementById("prevPageBtn").disabled = result.page <= 1;
    document.getElementById("nextPageBtn").disabled = result.page >= totalPages;
}

function renderBooks(books) {
    const tbody = document.getElementById("booksBody");
    tbody.innerHTML = "";

    if (books.length === 0) {
        tbody.innerHTML = `<tr><td colspan="${admin ? 7 : 6}" class="empty">No books found.</td></tr>`;
        return;
    }

    books.forEach(book => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${escapeHtml(book.title)}</td>
            <td>${escapeHtml(book.author.name)}</td>
            <td>${escapeHtml(book.category)}</td>
            <td>${book.quantity}</td>
            <td>${book.publishedYear ?? "—"}</td>
            <td>${book.edition ? escapeHtml(book.edition) : "—"}</td>
        `;
        if (admin) tr.appendChild(adminActions(book));
        tbody.appendChild(tr);
    });
}

// Edit (incl. the stock-change control inside the edit modal) and delete are ADMIN-only.
function adminActions(book) {
    const actionsCell = document.createElement("td");
    actionsCell.className = "actions";

    const editBtn = document.createElement("button");
    editBtn.type = "button";
    editBtn.title = "Edit";
    editBtn.textContent = "✏️";
    editBtn.addEventListener("click", () => editBook(book));

    const deleteBtn = document.createElement("button");
    deleteBtn.type = "button";
    deleteBtn.title = "Delete";
    deleteBtn.textContent = "🗑️";
    deleteBtn.addEventListener("click", () => deleteBook(book.id));

    actionsCell.append(editBtn, deleteBtn);
    return actionsCell;
}

function changePage(delta) {
    const next = currentPage + delta;
    if (next < 1 || next > totalPages) return;
    currentPage = next;
    loadBooks();
}

function applyFilters() {
    currentPage = 1;
    loadBooks();
}

// Create sets absolute stock; edit shows current stock read-only and changes it by a relative amount
// (PATCH), so an edit can never overwrite stock moved by concurrent borrows/returns.
function setQuantityMode(editing) {
    document.getElementById("quantity").readOnly = editing;
    document.getElementById("quantityChangeGroup").hidden = !editing;
    document.getElementById("quantityChange").value = "0";
}

function openCreateModal() {
    document.getElementById("modalTitle").textContent = "Add Book";
    document.getElementById("bookForm").reset();
    document.getElementById("bookId").value = "";
    setQuantityMode(false);
    document.getElementById("bookFormError").textContent = "";
    document.getElementById("bookModal").classList.remove("hidden");
}

function editBook(book) {
    document.getElementById("modalTitle").textContent = "Edit Book";
    document.getElementById("bookId").value = book.id;
    document.getElementById("title").value = book.title;
    document.getElementById("author").value = book.author.name;
    document.getElementById("category").value = book.category;
    document.getElementById("quantity").value = book.quantity;
    setQuantityMode(true);
    document.getElementById("publishedYear").value = book.publishedYear ?? "";
    document.getElementById("edition").value = book.edition ?? "";
    document.getElementById("bookFormError").textContent = "";
    document.getElementById("bookModal").classList.remove("hidden");
}

function closeModal() {
    document.getElementById("bookModal").classList.add("hidden");
}

async function deleteBook(id) {
    if (!confirm("Remove this book?")) return;
    const response = await apiFetch(`/books/${id}`, { method: "DELETE" });
    if (!response.ok) await alertFailure(response, "Error deleting book.");
    loadBooks();
}

function showFormError(message) {
    document.getElementById("bookFormError").textContent = message;
}

async function createBook(details) {
    const payload = { ...details, quantity: parseInt(document.getElementById("quantity").value, 10) };
    const response = await apiFetch("/books", { method: "POST", body: JSON.stringify(payload) });
    if (!response.ok) {
        showFormError(await response.text() || "Error saving book.");
        return false;
    }
    return true;
}

async function updateBook(id, details) {
    const detailsResponse = await apiFetch(`/books/${id}`, { method: "PUT", body: JSON.stringify(details) });
    if (!detailsResponse.ok) {
        showFormError(await detailsResponse.text() || "Error saving book.");
        return false;
    }

    const change = parseInt(document.getElementById("quantityChange").value, 10) || 0;
    if (change === 0) return true;

    const stockResponse = await apiFetch(`/books/${id}/quantity`, {
        method: "PATCH",
        body: JSON.stringify({ change })
    });
    if (!stockResponse.ok) {
        // Details are already saved: say so, keep the modal open, and show the saved details behind it.
        showFormError(`Details saved, but stock change failed: ${await stockResponse.text() || stockResponse.status}`);
        loadBooks();
        return false;
    }
    return true;
}

document.getElementById("bookForm").addEventListener("submit", async (e) => {
    e.preventDefault();

    const id = document.getElementById("bookId").value;
    const details = {
        title: document.getElementById("title").value,
        author: document.getElementById("author").value,
        category: document.getElementById("category").value,
        publishedYear: document.getElementById("publishedYear").value
            ? parseInt(document.getElementById("publishedYear").value, 10) : null,
        edition: document.getElementById("edition").value || null
    };

    showFormError("");
    const saved = id ? await updateBook(id, details) : await createBook(details);
    if (saved) {
        closeModal();
        loadBooks();
    }
});

async function init() {
    admin = isAdmin(await refreshCurrentUser());
    document.getElementById("addBookBtn").hidden = !admin;
    document.getElementById("actionsHeader").hidden = !admin;
    loadBooks();
}

init();


