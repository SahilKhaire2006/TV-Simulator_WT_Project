<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8"/>
    <title><%= response.getStatus() %> Error – TV Simulator</title>
    <link href="https://fonts.googleapis.com/css2?family=Orbitron:wght@700&family=Inter&display=swap" rel="stylesheet"/>
    <style>
        body{background:#08080f;color:#e2e8f0;font-family:'Inter',sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;flex-direction:column;gap:16px}
        h1{font-family:'Orbitron',monospace;font-size:4rem;color:#ef4444}
        p{color:#64748b}
        a{color:#00d4ff;text-decoration:none}
        a:hover{text-decoration:underline}
    </style>
</head>
<body>
<h1><%= response.getStatus() %></h1>
<p><%= request.getAttribute("javax.servlet.error.message") != null
        ? request.getAttribute("javax.servlet.error.message")
        : "An unexpected error occurred." %></p>
<a href="<%= request.getContextPath() %>/">← Back to Dashboard</a>
</body>
</html>
