const http = require('http');

async function run() {
    const res = await fetch("http://localhost:8080/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: "sumitshreshtn@gmail.com", password: "Password123!", deviceId: "test-device-123" })
    });
    const data = await res.json();
    if(!data.data || !data.data.accessToken) {
        console.error("Login failed:", data);
        return;
    }
    const token = data.data.accessToken;
    
    const histRes = await fetch("http://localhost:8080/v1/chats/conversations/8804fb1e-e1cd-4a3d-81f0-b60930604cea/messages", {
        headers: { "Authorization": `Bearer ${token}` }
    });
    const histData = await histRes.json();
    console.log("HISTORY:", JSON.stringify(histData, null, 2));
}
run();
