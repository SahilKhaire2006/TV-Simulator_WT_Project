package com.simulator.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.simulator.dao.UserDAO;
import com.simulator.dao.VisitHistoryDAO;
import com.simulator.model.User;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API for channel visit history.
 *
 * <pre>
 * POST   /api/visits          { "channel": N }     → record a visit (logged-in or anonymous)
 * GET    /api/visits                                → current user's visit history
 * GET    /api/visits/aggregate                       → aggregate across all users (admin)
 * </pre>
 */
@WebServlet(urlPatterns = {"/api/visits", "/api/visits/aggregate"})
public class VisitHistoryServlet extends HttpServlet {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TOKEN_HEADER = "X-Auth-Token";

    private final VisitHistoryDAO visitDAO = new VisitHistoryDAO();
    private final UserDAO         userDAO  = new UserDAO();

    // ── POST: record a visit ──────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        setCORS(resp);
        resp.setContentType("application/json;charset=UTF-8");

        Map<String, Object> body = parseBody(req);
        Object chObj = body.get("channel");
        if (chObj == null) {
            resp.setStatus(400);
            try (PrintWriter out = resp.getWriter()) { out.print("{\"error\":\"channel required\"}"); }
            return;
        }
        int channel = ((Number) chObj).intValue();

        // Try to identify the user (may be anonymous)
        String token = req.getHeader(TOKEN_HEADER);
        User user = userDAO.getUserByToken(token).orElse(null);

        Integer userId = user != null ? user.getId() : null;
        visitDAO.recordVisit(userId, channel);

        try (PrintWriter out = resp.getWriter()) {
            out.print(GSON.toJson(Map.of(
                "message", "Visit recorded",
                "channel", channel,
                "user",    user != null ? user.getUsername() : "anonymous"
            )));
        }
    }

    // ── GET: user history or aggregate ────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        setCORS(resp);
        resp.setContentType("application/json;charset=UTF-8");

        String path = req.getServletPath();
        try (PrintWriter out = resp.getWriter()) {
            if ("/api/visits/aggregate".equals(path)) {
                // Aggregate (admin panel)
                out.print(GSON.toJson(visitDAO.getAggregateVisits()));
            } else {
                // Per-user history
                User user = authenticate(req);
                if (user == null) {
                    resp.setStatus(401);
                    out.print("{\"error\":\"Not authenticated\"}");
                    return;
                }
                out.print(GSON.toJson(visitDAO.getUserVisits(user.getId())));
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User authenticate(HttpServletRequest req) {
        String token = req.getHeader(TOKEN_HEADER);
        return userDAO.getUserByToken(token).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(HttpServletRequest req) {
        Map<String, Object> result = new LinkedHashMap<>();
        try (BufferedReader r = req.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            if (!sb.isEmpty()) {
                Map<String, Object> parsed = GSON.fromJson(sb.toString(), Map.class);
                if (parsed != null) result.putAll(parsed);
            }
        } catch (Exception e) { /* ignore */ }
        return result;
    }

    private void setCORS(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Auth-Token");
        resp.setHeader("Cache-Control", "no-cache");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        setCORS(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}
