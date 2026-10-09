document.getElementById("loginForm").addEventListener("submit", async (e) => {
    e.preventDefault();

    const email    = document.getElementById("email").value;
    const password = document.getElementById("password").value;

    const response = await fetch("/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password })
    });

    if (response.ok) {
        const data = await response.json();
        localStorage.setItem("token", data.token);
        localStorage.setItem("user", JSON.stringify(data.user));
        window.location.href = "/ui/books";
    } else {
        const container = document.querySelector(".login-container");
        const existing  = container.querySelector(".alert");
        if (existing) existing.remove();

        const alert = document.createElement("div");
        alert.className = "alert alert-error";
        alert.textContent = response.status === 401
            ? "Incorrect email or password."
            : "Error logging in. Please try again.";

        container.insertBefore(alert, container.querySelector("form"));
    }
});

