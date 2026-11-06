requireAuth();

let currentPage = 1;
const pageSize = 10;
let totalPages = 1;
let me = null;
let admin = false;

function currentFilters() {
    return {
        name: document.getElementById("filterName").value.trim(),
        page: currentPage,
        size: pageSize
    };
}

// ADMIN: paginated list of every account. USER: only their own row (from /auth/me) —
// GET /users is admin-only on the server anyway.
async function loadUsers() {
    if (!admin) {
        renderUsers([me]);
        return;
    }

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
        tbody.innerHTML = `<tr><td colspan="${admin ? 4 : 3}" class="empty">No users found.</td></tr>`;
        return;
    }

    users.forEach(user => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${escapeHtml(user.name)}</td>
            <td>${escapeHtml(user.email)}</td>
            ${admin ? `<td>${escapeHtml(user.role)}</td>` : ""}
            <td class="actions"></td>
        `;
        tr.querySelector(".actions").append(...actionButtons(user));
        tbody.appendChild(tr);
    });
}

function button(title, text, onClick) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.title = title;
    btn.textContent = text;
    btn.addEventListener("click", onClick);
    return btn;
}

function actionButtons(user) {
    const isMe = me && me.id === user.id;
    const buttons = [button("Edit", "✏️", () => editUser(user))];
    // Password change is self-only on the server, admins included.
    if (isMe) buttons.push(button("Change password", "🔑", () => openPasswordModal(user.id)));
    if (admin) {
        const target = user.role === "ADMIN" ? "USER" : "ADMIN";
        buttons.push(button(`Make ${target}`, target === "ADMIN" ? "⬆️ Admin" : "⬇️ User", () => changeRole(user.id, target)));
        if (!isMe) buttons.push(button("Delete", "🗑️", () => deleteUser(user.id)));
    }
    return buttons;
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

// 409 = the last admin cannot be demoted; the server's message says so.
async function changeRole(id, role) {
    if (!confirm(`Change this account's role to ${role}?`)) return;
    const response = await apiFetch(`/users/${id}/role`, { method: "PATCH", body: JSON.stringify({ role }) });
    if (!response.ok) await alertFailure(response, "Error changing role.");
    // Re-read our own role too: an admin may just have demoted themself.
    await init();
}

// 409 = last admin, or the account has loans (returned ones included).
async function deleteUser(id) {
    if (!confirm("Remove this user?")) return;
    const response = await apiFetch(`/users/${id}`, { method: "DELETE" });
    if (!response.ok) await alertFailure(response, "Error deleting user.");
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
        await init();
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

async function init() {
    me = await refreshCurrentUser();
    if (!me) return;
    admin = isAdmin(me);
    document.getElementById("usersToolbar").hidden = !admin;
    document.getElementById("usersPagination").hidden = !admin;
    document.getElementById("roleHeader").hidden = !admin;
    await loadUsers();
}

init();
