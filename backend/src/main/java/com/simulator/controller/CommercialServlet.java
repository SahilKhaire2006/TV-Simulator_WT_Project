package com.simulator.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.simulator.dao.CommercialDAO;
import com.simulator.dao.UserDAO;
import com.simulator.model.Commercial;
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
 * REST API for commercials (ads).
 *
 * <pre>
 * GET    /api/commercials                    → list active commercials (public)
 * GET    /api/commercials?channel=N          → active commercials for a channel
 * GET    /api/commercials?all=true           → all commercials (admin only)
 * POST   /api/commercials                    → create (admin only)
 * PUT    /api/commercials                    → update (admin only)
 * DELETE /api/commercials?id=N                → delete (admin only)
 * </pre>
 */
@WebServlet(urlPatterns = {"/api/commercials"})
public class CommercialServlet extends HttpServlet {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TOKEN_HEADER = "X-Auth-Token";

    private final CommercialDAO commercialDAO = new CommercialDAO();
    private final UserDAO        userDAO       = new UserDAO();

    // ── GET ─────────────────────────────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        setCORS(resp);
        resp.setContentType("application/json;charset=UTF-8");

        String channelParam = req.getParameter("channel");
        String allParam     = req.getParameter("all");

        try (PrintWriter out = resp.getWriter()) {
            if ("true".equalsIgnoreCase(allParam)) {
                // Admin: list all
                User user = authenticate(req);
                if (user == null || !"admin".equals(user.getRole())) {
                    resp.setStatus(403);
                    out.print("{\"error\":\"Admin access required\"}");
                    return;
                }
                out.print(GSON.toJson(commercialDAO.getAllCommercials()));
            } else if (channelParam != null) {
                int ch = parseInt(channelParam, 0);
                List<Commercial> ads = commercialDAO.getCommercialsForChannel(ch);
                out.print(GSON.toJson(ads));
            } else {
                out.print(GSON.toJson(commercialDAO.getActiveCommercials()));
            }
        }
    }

    // ── POST (create) ──────────────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        setCORS(resp);
        resp.setContentType("application/json;charset=UTF-8");

        User user = authenticate(req);
        if (user == null || !"admin".equals(user.getRole())) {
            resp.setStatus(403);
            try (PrintWriter out = resp.getWriter()) { out.print("{\"error\":\"Admin access required\"}"); }
            return;
        }

        Commercial ad = parseBody(req);
        if (ad == null || ad.getTitle() == null) {
            resp.setStatus(400);
            try (PrintWriter out = resp.getWriter()) { out.print("{\"error\":\"Title required\"}"); }
            return;
        }

        Commercial created = commercialDAO.create(ad);
        try (PrintWriter out = resp.getWriter()) {
            if (created != null) {
                out.print(GSON.toJson(created));
            } else {
                resp.setStatus(500);
                out.print("{\"error\":\"Failed to create commercial\"}");
            }
        }
    }

    // ── PUT (update) ──────────────────────────────────────────────────────────

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        setCORS(resp);
        resp.setContentType("application/json;charset=UTF-8");

        User user = authenticate(req);
        if (user == null || !"admin".equals(user.getRole())) {
            resp.setStatus(403);
            try (PrintWriter out = resp.getWriter()) { out.print("{\"error\":\"Admin access required\"}"); }
            return;
        }

        Commercial ad = parseBody(req);
        if (ad == null || ad.getId() <= 0) {
            resp.setStatus(400);
            try (PrintWriter out = resp.getWriter()) { out.print("{\"error\":\"id required\"}"); }
            return;
        }

        boolean ok = commercialDAO.update(ad);
        try (PrintWriter out = resp.getWriter()) {
            out.print(GSON.toJson(Map.of("message", ok ? "Updated" : "Not found", "success", ok)));
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        setCORS(resp);
        resp.setContentType("application/json;charset=UTF-8");

        User user = authenticate(req);
        if (user == null || !"admin".equals(user.getRole())) {
            resp.setStatus(403);
            try (PrintWriter out = resp.getWriter()) { out.print("{\"error\":\"Admin access required\"}"); }
            return;
        }

        int id = parseInt(req.getParameter("id"), 0);
        boolean ok = commercialDAO.delete(id);
        try (PrintWriter out = resp.getWriter()) {
            out.print(GSON.toJson(Map.of("message", ok ? "Deleted" : "Not found", "success", ok)));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User authenticate(HttpServletRequest req) {
        String token = req.getHeader(TOKEN_HEADER);
        return userDAO.getUserByToken(token).orElse(null);
    }

    private Commercial parseBody(HttpServletRequest req) {
        try (BufferedReader r = req.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            if (!sb.isEmpty()) {
                return GSON.fromJson(sb.toString(), Commercial.class);
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private void setCORS(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Auth-Token");
        resp.setHeader("Cache-Control", "no-cache");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        setCORS(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}
