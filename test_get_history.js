const http = require('http');

async function run() {
    try {
        const res = await fetch("http://localhost:8080/v1/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email: "sumitshreshtn@gmail.com", password: "Password123!", deviceId: "test-device-123" })
        });
        const data = await res.json();
        // Wait! We don't know sumitshreshtn's password!
        // But wait! We can just fetch the JWT token for sumitshreshtn using our get_token.js script!
    } catch (e) {
        console.error(e);
    }
}
run();
