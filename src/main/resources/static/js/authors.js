requireAuth();

let currentPage = 1;
const pageSize = 10;
let totalPages = 1;

function currentFilters() {
    return {
        name: document.getElementById("filterName").value.trim(),
        page: currentPage,
        size: pageSize
    };
}

async function loadAuthors() {
    const response = await apiFetch("/authors" + qs(currentFilters()));
    if (!response.ok) return;

    const result = await response.json();
    totalPages = result.totalPages || 1;
    renderAuthors(result.data);

    document.getElementById("pageIndicator").textContent = `Page ${result.page} of ${totalPages}`;
    document.getElementById("prevPageBtn").disabled = result.page <= 1;
    document.getElementById("nextPageBtn").disabled = result.page >= totalPages;
}

function renderAuthors(authors) {
    const tbody = document.getElementById("authorsBody");
    tbody.innerHTML = "";

    if (authors.length === 0) {
        tbody.innerHTML = `<tr><td colspan="2" class="empty">No authors found.</td></tr>`;
        return;
    }

    authors.forEach(author => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${escapeHtml(author.name)}</td>
            <td class="actions"></td>
        `;

        const actionsCell = tr.querySelector(".actions");

        const editBtn = document.createElement("button");
        editBtn.type = "button";
        editBtn.title = "Edit";
        editBtn.textContent = "✏️";
        editBtn.addEventListener("click", () => editAuthor(author));

        const deleteBtn = document.createElement("button");
        deleteBtn.type = "button";
        deleteBtn.title = "Delete";
        deleteBtn.textContent = "🗑️";
        deleteBtn.addEventListener("click", () => deleteAuthor(author.id));

        actionsCell.append(editBtn, deleteBtn);
        tbody.appendChild(tr);
    });
}

function changePage(delta) {
    const next = currentPage + delta;
    if (next < 1 || next > totalPages) return;
    currentPage = next;
    loadAuthors();
}

function applyFilters() {
    currentPage = 1;
    loadAuthors();
}

function openCreateModal() {
    document.getElementById("modalTitle").textContent = "Add Author";
    document.getElementById("authorForm").reset();
    document.getElementById("authorId").value = "";
    document.getElementById("authorFormError").textContent = "";
    document.getElementById("authorModal").classList.remove("hidden");
}

function editAuthor(author) {
    document.getElementById("modalTitle").textContent = "Edit Author";
    document.getElementById("authorId").value = author.id;
    document.getElementById("name").value = author.name;
    document.getElementById("authorFormError").textContent = "";
    document.getElementById("authorModal").classList.remove("hidden");
}

function closeModal() {
    document.getElementById("authorModal").classList.add("hidden");
}

async function deleteAuthor(id) {
    if (!confirm("Remove this author?")) return;
    const response = await apiFetch(`/authors/${id}`, { method: "DELETE" });
    if (!response.ok && response.status !== 204) {
        alert("Could not delete author (they may still have books linked to them).");
        return;
    }
    loadAuthors();
}

document.getElementById("authorForm").addEventListener("submit", async (e) => {
    e.preventDefault();

    const id = document.getElementById("authorId").value;
    const payload = { name: document.getElementById("name").value };

    const response = id
        ? await apiFetch(`/authors/${id}`, { method: "PUT", body: JSON.stringify(payload) })
        : await apiFetch("/authors", { method: "POST", body: JSON.stringify(payload) });

    if (response.ok) {
        closeModal();
        loadAuthors();
    } else {
        document.getElementById("authorFormError").textContent = await response.text() || "Error saving author.";
    }
});

loadAuthors();

