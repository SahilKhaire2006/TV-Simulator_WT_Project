package com.simulator.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.simulator.dao.UserDAO;
import com.simulator.model.User;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API for user authentication.
 *
 * <pre>
 * POST /api/auth/register   { "username":"...", "password":"..." }
 * POST /api/auth/login       { "username":"...", "password":"..." }
 * POST /api/auth/logout      (requires X-Auth-Token header)
 * GET  /api/auth/me          (requires X-Auth-Token header)
 * </pre>
 *
 * Authentication is token-based (not cookie-based) because the React
 * frontend is deployed on Vercel (cross-origin). The token is returned
 * in the JSON response and sent back via the {@code X-Auth-Token}
 * header on subsequent requests.
 */
@WebServlet(urlPatterns = {"/api/auth/register", "/api/auth/login",
                            "/api/auth/logout",  "/api/auth/me"})
public class AuthServlet extends HttpServlet {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TOKEN_HEADER = "X-Auth-Token";

    private final UserDAO userDAO = new UserDAO();

    // ── POST: register / login / logout ──────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        setCORS(resp);
        resp.setContentType("application/json;charset=UTF-8");

        String path = req.getServletPath();
        try (PrintWriter out = resp.getWriter()) {
            switch (path) {
                case "/api/auth/register" -> handleRegister(req, resp, out);
                case "/api/auth/login"    -> handleLogin(req, resp, out);
                case "/api/auth/logout"   -> handleLogout(req, resp, out);
                default -> {
                    resp.setStatus(404);
                    out.print("{\"error\":\"Not found\"}");
                }
            }
        }
    }

    // ── GET: me ────────────────────────────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        setCORS(resp);
        resp.setContentType("application/json;charset=UTF-8");

        try (PrintWriter out = resp.getWriter()) {
            if ("/api/auth/me".equals(req.getServletPath())) {
                handleMe(req, resp, out);
            } else {
                resp.setStatus(404);
                out.print("{\"error\":\"Not found\"}");
            }
        }
    }

    // ── Handlers ───────────────────────────────────────────────────────────────

    private void handleRegister(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) {
        Map<String, String> body = parseBody(req);
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank() || password == null || password.length() < 3) {
            resp.setStatus(400);
            out.print("{\"error\":\"Username required and password must be at least 3 characters\"}");
            return;
        }

        User user = userDAO.register(username.trim(), password);
        if (user == null) {
            resp.setStatus(409);
            out.print("{\"error\":\"Username already exists\"}");
            return;
        }

        String token = userDAO.createSession(user.getId());
        out.print(GSON.toJson(Map.of(
            "message", "Registration successful",
            "token",   token,
            "user",    userMap(user)
        )));
    }

    private void handleLogin(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) {
        Map<String, String> body = parseBody(req);
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            resp.setStatus(400);
            out.print("{\"error\":\"Username and password required\"}");
            return;
        }

        var opt = userDAO.login(username.trim(), password);
        if (opt.isEmpty()) {
            resp.setStatus(401);
            out.print("{\"error\":\"Invalid username or password\"}");
            return;
        }

        User user = opt.get();
        String token = userDAO.createSession(user.getId());
        out.print(GSON.toJson(Map.of(
            "message", "Login successful",
            "token",   token,
            "user",    userMap(user)
        )));
    }

    private void handleLogout(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) {
        String token = req.getHeader(TOKEN_HEADER);
        userDAO.logout(token);
        out.print("{\"message\":\"Logged out\"}");
    }

    private void handleMe(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) {
        String token = req.getHeader(TOKEN_HEADER);
        var opt = userDAO.getUserByToken(token);
        if (opt.isEmpty()) {
            resp.setStatus(401);
            out.print("{\"error\":\"Not authenticated\"}");
            return;
        }
        out.print(GSON.toJson(Map.of("user", userMap(opt.get()))));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Map<String, String> parseBody(HttpServletRequest req) {
        Map<String, String> result = new LinkedHashMap<>();
        try (BufferedReader r = req.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            if (!sb.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, String> parsed = GSON.fromJson(sb.toString(), Map.class);
                if (parsed != null) result.putAll(parsed);
            }
        } catch (Exception e) {
            // ignore parse errors
        }
        return result;
    }

    private Map<String, Object> userMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("role", u.getRole());
        return m;
    }

    private void setCORS(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Auth-Token");
        resp.setHeader("Cache-Control", "no-cache");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        setCORS(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}
