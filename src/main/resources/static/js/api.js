const API_BASE = "";

async function apiFetch(path, options = {}) {
    const token = localStorage.getItem("token");
    const response = await fetch(API_BASE + path, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(token ? { "Authorization": "Bearer " + token } : {}),
            ...(options.headers || {})
        }
    });
    if (response.status === 401) {
        logout();
    }
    return response;
}

function logout() {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    window.location.href = "/ui/login";
}

function requireAuth() {
    if (!localStorage.getItem("token")) {
        window.location.href = "/ui/login";
    }
}

function currentUser() {
    const raw = localStorage.getItem("user");
    return raw ? JSON.parse(raw) : null;
}

// The role always comes from the server: stored copies may be stale after a promotion/demotion.
// Hiding controls is convenience only; the API enforces every permission.
async function refreshCurrentUser() {
    const response = await apiFetch("/auth/me");
    if (!response.ok) return null;
    const user = await response.json();
    localStorage.setItem("user", JSON.stringify(user));
    return user;
}

function isAdmin(user) {
    return !!user && user.role === "ADMIN";
}

// Shows the server's explanation (e.g. 403/409) for an action that was refused.
async function alertFailure(response, fallback) {
    alert(await response.text() || fallback);
}

// Builds a "?a=1&b=2" query string, skipping empty/undefined/null values.
function qs(params) {
    const entries = Object.entries(params)
        .filter(([, v]) => v !== undefined && v !== null && v !== "");
    if (entries.length === 0) return "";
    return "?" + entries.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join("&");
}

// Minimal HTML-escaping to avoid injection when interpolating user data into innerHTML.
function escapeHtml(value) {
    if (value === null || value === undefined) return "";
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}



