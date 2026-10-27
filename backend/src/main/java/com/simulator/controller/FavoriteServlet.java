package com.simulator.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.simulator.dao.FavoriteDAO;
import com.simulator.dao.UserDAO;
import com.simulator.model.Favorite;
import com.simulator.model.User;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for per-user channel favourites.
 *
 * <pre>
 * GET    /api/favorites                      → list user's favourites
 * POST   /api/favorites    { "channel": N }  → add a favourite
 * DELETE /api/favorites    { "channel": N }  → remove a favourite
 * GET    /api/favorites/check?channel=N      → check if channel is favourited
 * </pre>
 *
 * All requests require the {@code X-Auth-Token} header for session-based
 * user identification – favourites are strictly per-user.
 */
@WebServlet(urlPatterns = {"/api/favorites", "/api/favorites/check"})
public class FavoriteServlet extends HttpServlet {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TOKEN_HEADER = "X-Auth-Token";

    private final FavoriteDAO favoriteDAO = new FavoriteDAO();
    private final UserDAO      userDAO     = new UserDAO();

    // ── GET ─────────────────────────────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        setCORS(resp);
        resp.setContentType("application/json;charset=UTF-8");

        User user = authenticate(req);
        if (user == null) {
            resp.setStatus(401);
            try (PrintWriter out = resp.getWriter()) {
                out.print("{\"error\":\"Not authenticated – please log in\"}");
            }
            return;
        }

        String path = req.getServletPath();
        try (PrintWriter out = resp.getWriter()) {
            if ("/api/favorites".equals(path)) {
                // List all favourites for this user
                List<Favorite> favs = favoriteDAO.getFavorites(user.getId());
                out.print(GSON.toJson(favs));

            } else if ("/api/favorites/check".equals(path)) {
                // Check a single channel
                int channel = parseInt(req.getParameter("channel"), 0);
                boolean isFav = favoriteDAO.isFavorite(user.getId(), channel);
                out.print(GSON.toJson(Map.of("channel", channel, "isFavorite", isFav)));
            }
        }
    }

    // ── POST (add favourite) ──────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        setCORS(resp);
        resp.setContentType("application/json;charset=UTF-8");

        User user = authenticate(req);
        if (user == null) {
            resp.setStatus(401);
            try (PrintWriter out = resp.getWriter()) { out.print("{\"error\":\"Not authenticated\"}"); }
            return;
        }

        Map<String, Object> body = parseBody(req);
        int channel = ((Number) body.get("channel")).intValue();
        if (channel < 1 || channel > 99) {
            resp.setStatus(400);
            try (PrintWriter out = resp.getWriter()) { out.print("{\"error\":\"Invalid channel\"}"); }
            return;
        }

        boolean added = favoriteDAO.addFavorite(user.getId(), channel);
        try (PrintWriter out = resp.getWriter()) {
            out.print(GSON.toJson(Map.of(
                "message", added ? "Added to favourites" : "Already in favourites",
                "channel", channel,
                "added",   added
            )));
        }
    }

    // ── DELETE (remove favourite) ─────────────────────────────────────────────

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        setCORS(resp);
        resp.setContentType("application/json;charset=UTF-8");

        User user = authenticate(req);
        if (user == null) {
            resp.setStatus(401);
            try (PrintWriter out = resp.getWriter()) { out.print("{\"error\":\"Not authenticated\"}"); }
            return;
        }

        Map<String, Object> body = parseBody(req);
        int channel = ((Number) body.get("channel")).intValue();
        boolean removed = favoriteDAO.removeFavorite(user.getId(), channel);

        try (PrintWriter out = resp.getWriter()) {
            out.print(GSON.toJson(Map.of(
                "message", removed ? "Removed from favourites" : "Was not in favourites",
                "channel", channel,
                "removed", removed
            )));
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

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private void setCORS(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Auth-Token");
        resp.setHeader("Cache-Control", "no-cache");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        setCORS(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}
