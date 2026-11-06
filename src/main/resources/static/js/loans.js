requireAuth();

let currentPage = 1;
const pageSize = 10;
let totalPages = 1;

function currentFilters() {
    return {
        borrower: document.getElementById("filterBorrower").value.trim(),
        onlyActive: document.getElementById("filterActive").checked,
        page: currentPage,
        size: pageSize
    };
}

async function loadLoans() {
    const response = await apiFetch("/loans" + qs(currentFilters()));
    if (!response.ok) return;

    const result = await response.json();
    totalPages = result.totalPages || 1;
    renderLoans(result.data);

    document.getElementById("pageIndicator").textContent = `Page ${result.page} of ${totalPages}`;
    document.getElementById("prevPageBtn").disabled = result.page <= 1;
    document.getElementById("nextPageBtn").disabled = result.page >= totalPages;
}

function statusBadge(loan) {
    if (loan.isReturned) return `<span class="badge badge-returned">Returned</span>`;
    if (loan.isOverdue) {
        const days = loan.daysOverdue;
        return `<span class="badge badge-overdue">Overdue by ${days} ${days === 1 ? "day" : "days"}</span>`;
    }
    return `<span class="badge badge-active">Active</span>`;
}

function renderLoans(loans) {
    const tbody = document.getElementById("loansBody");
    tbody.innerHTML = "";

    if (loans.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="empty">No loans found.</td></tr>`;
        return;
    }

    loans.forEach(loan => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${escapeHtml(loan.book.title)}</td>
            <td>${escapeHtml(loan.borrower.name)}</td>
            <td>${loan.borrowedAt}</td>
            <td>${loan.dueDate}</td>
            <td>${statusBadge(loan)}</td>
            <td class="actions"></td>
        `;

        if (!loan.isReturned) {
            const actionsCell = tr.querySelector(".actions");
            const returnBtn = document.createElement("button");
            returnBtn.type = "button";
            returnBtn.title = "Mark as returned";
            returnBtn.textContent = "↩️ Return";
            returnBtn.addEventListener("click", () => returnLoan(loan.id));
            actionsCell.appendChild(returnBtn);
        }

        tbody.appendChild(tr);
    });
}

function changePage(delta) {
    const next = currentPage + delta;
    if (next < 1 || next > totalPages) return;
    currentPage = next;
    loadLoans();
}

function applyFilters() {
    currentPage = 1;
    loadLoans();
}

async function loadBookOptions() {
    const select = document.getElementById("bookId");
    select.innerHTML = `<option value="">Loading books…</option>`;

    const response = await apiFetch("/books?size=100");
    if (!response.ok) {
        select.innerHTML = `<option value="">Could not load books</option>`;
        return;
    }

    const result = await response.json();
    select.innerHTML = "";
    result.data.forEach(book => {
        const option = document.createElement("option");
        option.value = book.id;
        option.textContent = `${book.title} — ${book.author.name} (qty: ${book.quantity})`;
        if (book.quantity <= 0) option.disabled = true;
        select.appendChild(option);
    });
}

async function openCreateModal() {
    document.getElementById("loanForm").reset();
    document.getElementById("loanFormError").textContent = "";
    document.getElementById("loanModal").classList.remove("hidden");
    await loadBookOptions();
}

function closeModal() {
    document.getElementById("loanModal").classList.add("hidden");
}

async function returnLoan(id) {
    if (!confirm("Mark this loan as returned?")) return;
    await apiFetch(`/loans/${id}/return`, { method: "PATCH" });
    loadLoans();
}

document.getElementById("loanForm").addEventListener("submit", async (e) => {
    e.preventDefault();

    const payload = {
        bookId: parseInt(document.getElementById("bookId").value, 10),
        // Loans are booked to the signed-in account.
        userId: currentUser().id,
        daysToReturn: parseInt(document.getElementById("daysToReturn").value, 10)
    };

    const response = await apiFetch("/loans", { method: "POST", body: JSON.stringify(payload) });

    if (response.ok) {
        closeModal();
        loadLoans();
    } else {
        document.getElementById("loanFormError").textContent = await response.text() || "Error creating loan.";
    }
});

loadLoans();

