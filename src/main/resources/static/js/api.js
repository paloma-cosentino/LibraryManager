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

