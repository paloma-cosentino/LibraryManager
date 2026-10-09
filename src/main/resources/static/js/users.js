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

async function loadUsers() {
    const response = await apiFetch("/users" + qs(currentFilters()));
    if (!response.ok) return;

    const result = await response.json();
    totalPages = result.totalPages || 1;
    renderUsers(result.data);

    document.getElementById("pageIndicator").textContent = `Page ${result.page} of ${totalPages}`;
    document.getElementById("prevPageBtn").disabled = result.page <= 1;
    document.getElementById("nextPageBtn").disabled = result.page >= totalPages;
}

function renderUsers(users) {
    const tbody = document.getElementById("usersBody");
    tbody.innerHTML = "";

    if (users.length === 0) {
        tbody.innerHTML = `<tr><td colspan="3" class="empty">No users found.</td></tr>`;
        return;
    }

    const me = currentUser();

    users.forEach(user => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${escapeHtml(user.name)}</td>
            <td>${escapeHtml(user.email)}</td>
            <td class="actions"></td>
        `;

        const actionsCell = tr.querySelector(".actions");

        const editBtn = document.createElement("button");
        editBtn.type = "button";
        editBtn.title = "Edit";
        editBtn.textContent = "✏️";
        editBtn.addEventListener("click", () => editUser(user));
        actionsCell.appendChild(editBtn);

        if (me && me.id === user.id) {
            const passwordBtn = document.createElement("button");
            passwordBtn.type = "button";
            passwordBtn.title = "Change password";
            passwordBtn.textContent = "🔑";
            passwordBtn.addEventListener("click", () => openPasswordModal(user.id));
            actionsCell.appendChild(passwordBtn);
        } else {
            const deleteBtn = document.createElement("button");
            deleteBtn.type = "button";
            deleteBtn.title = "Delete";
            deleteBtn.textContent = "🗑️";
            deleteBtn.addEventListener("click", () => deleteUser(user.id));
            actionsCell.appendChild(deleteBtn);
        }

        tbody.appendChild(tr);
    });
}

function changePage(delta) {
    const next = currentPage + delta;
    if (next < 1 || next > totalPages) return;
    currentPage = next;
    loadUsers();
}

function applyFilters() {
    currentPage = 1;
    loadUsers();
}

function editUser(user) {
    document.getElementById("userId").value = user.id;
    document.getElementById("name").value = user.name;
    document.getElementById("email").value = user.email;
    document.getElementById("userFormError").textContent = "";
    document.getElementById("userModal").classList.remove("hidden");
}

function closeModal() {
    document.getElementById("userModal").classList.add("hidden");
}

function openPasswordModal(userId) {
    document.getElementById("passwordUserId").value = userId;
    document.getElementById("currentPassword").value = "";
    document.getElementById("newPassword").value = "";
    document.getElementById("confirmPassword").value = "";
    document.getElementById("passwordFormError").textContent = "";
    document.getElementById("passwordModal").classList.remove("hidden");
}

function closePasswordModal() {
    document.getElementById("passwordModal").classList.add("hidden");
}

async function deleteUser(id) {
    if (!confirm("Remove this user?")) return;
    await apiFetch(`/users/${id}`, { method: "DELETE" });
    loadUsers();
}

document.getElementById("userForm").addEventListener("submit", async (e) => {
    e.preventDefault();

    const id = document.getElementById("userId").value;
    const payload = {
        name: document.getElementById("name").value,
        email: document.getElementById("email").value
    };

    const response = await apiFetch(`/users/${id}`, { method: "PUT", body: JSON.stringify(payload) });

    if (response.ok) {
        closeModal();
        loadUsers();
    } else {
        document.getElementById("userFormError").textContent = await response.text() || "Error saving user.";
    }
});

document.getElementById("passwordForm").addEventListener("submit", async (e) => {
    e.preventDefault();

    const id = document.getElementById("passwordUserId").value;
    const currentPassword = document.getElementById("currentPassword").value;
    const newPassword = document.getElementById("newPassword").value;
    const confirmPassword = document.getElementById("confirmPassword").value;
    const errorEl = document.getElementById("passwordFormError");

    if (newPassword !== confirmPassword) {
        errorEl.textContent = "New password and confirmation do not match.";
        return;
    }

    // Uses a plain fetch (not apiFetch) because a wrong "current password" legitimately
    // returns 401 here, and apiFetch treats any 401 as an expired/invalid session and
    // force-logs-out the user — which would be wrong in this case.
    const token = localStorage.getItem("token");
    const response = await fetch(`/users/${id}/password`, {
        method: "PATCH",
        headers: {
            "Content-Type": "application/json",
            ...(token ? { "Authorization": "Bearer " + token } : {})
        },
        body: JSON.stringify({ currentPassword, newPassword })
    });

    if (response.ok) {
        closePasswordModal();
        alert("Password changed successfully.");
    } else if (response.status === 401) {
        errorEl.textContent = "Current password is incorrect.";
    } else {
        errorEl.textContent = await response.text() || "Error changing password.";
    }
});

loadUsers();

