requireAuth();

let currentPage = 1;
const pageSize = 10;
let totalPages = 1;

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
        tbody.innerHTML = `<tr><td colspan="7" class="empty">No books found.</td></tr>`;
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
            <td class="actions"></td>
        `;

        const actionsCell = tr.querySelector(".actions");

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
        tbody.appendChild(tr);
    });
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

function openCreateModal() {
    document.getElementById("modalTitle").textContent = "Add Book";
    document.getElementById("bookForm").reset();
    document.getElementById("bookId").value = "";
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
    await apiFetch(`/books/${id}`, { method: "DELETE" });
    loadBooks();
}

document.getElementById("bookForm").addEventListener("submit", async (e) => {
    e.preventDefault();

    const id = document.getElementById("bookId").value;
    const payload = {
        title: document.getElementById("title").value,
        author: document.getElementById("author").value,
        category: document.getElementById("category").value,
        quantity: parseInt(document.getElementById("quantity").value, 10),
        publishedYear: document.getElementById("publishedYear").value
            ? parseInt(document.getElementById("publishedYear").value, 10) : null,
        edition: document.getElementById("edition").value || null
    };

    const response = id
        ? await apiFetch(`/books/${id}`, { method: "PUT", body: JSON.stringify(payload) })
        : await apiFetch("/books", { method: "POST", body: JSON.stringify(payload) });

    if (response.ok) {
        closeModal();
        loadBooks();
    } else {
        document.getElementById("bookFormError").textContent = await response.text() || "Error saving book.";
    }
});

loadBooks();

