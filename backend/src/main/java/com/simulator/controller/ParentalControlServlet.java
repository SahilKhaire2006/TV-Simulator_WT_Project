package com.simulator.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.simulator.dao.ParentalControlDAO;
import com.simulator.dao.UserDAO;
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
 * REST API for parental control.
 *
 * <pre>
 * GET    /api/parental/locked           → list locked channels for the user
 * POST   /api/parental/lock    { "channel": N }   → lock a channel
 * POST   /api/parental/unlock  { "channel": N }   → unlock a channel
 * POST   /api/parental/pin     { "pin": "1234" }  → set / update PIN
 * POST   /api/parental/verify  { "pin": "1234" }  → verify PIN (unlock session)
 * POST   /api/parental/enable   { "enabled": true/false } → toggle parental control
 * GET    /api/parental/status                        → full status (enabled, hasPin, locked)
 * </pre>
 *
 * All requests require the {@code X-Auth-Token} header.
 */
@WebServlet(urlPatterns = {
    "/api/parental/locked", "/api/parental/lock", "/api/parental/unlock",
    "/api/parental/pin", "/api/parental/verify", "/api/parental/enable",
    "/api/parental/status"
})
public class ParentalControlServlet extends HttpServlet {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TOKEN_HEADER = "X-Auth-Token";

    private final ParentalControlDAO parentalDAO = new ParentalControlDAO();
    private final UserDAO             userDAO    = new UserDAO();

    // ── Routing ────────────────────────────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        setCORS(resp);
        resp.setContentType("application/json;charset=UTF-8");

        User user = authenticate(req);
        if (user == null) {
            resp.setStatus(401);
            try (PrintWriter out = resp.getWriter()) { out.print("{\"error\":\"Not authenticated\"}"); }
            return;
        }

        String path = req.getServletPath();
        try (PrintWriter out = resp.getWriter()) {
            switch (path) {
                case "/api/parental/locked" -> {
                    List<Integer> locked = parentalDAO.getLockedChannels(user.getId());
                    out.print(GSON.toJson(Map.of("lockedChannels", locked)));
                }
                case "/api/parental/status" -> {
                    boolean enabled = parentalDAO.isEnabled(user.getId());
                    boolean hasPin  = parentalDAO.hasPinSet(user.getId());
                    List<Integer> locked = parentalDAO.getLockedChannels(user.getId());
                    out.print(GSON.toJson(Map.of(
                        "enabled",       enabled,
                        "hasPin",        hasPin,
                        "lockedChannels", locked
                    )));
                }
                default -> {
                    resp.setStatus(404);
                    out.print("{\"error\":\"Not found\"}");
                }
            }
        }
    }

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
        String path = req.getServletPath();
        try (PrintWriter out = resp.getWriter()) {
            switch (path) {
                case "/api/parental/lock" -> {
                    int ch = channel(body);
                    parentalDAO.lockChannel(user.getId(), ch);
                    out.print(GSON.toJson(Map.of("message", "Channel " + ch + " locked", "channel", ch)));
                }
                case "/api/parental/unlock" -> {
                    int ch = channel(body);
                    parentalDAO.unlockChannel(user.getId(), ch);
                    out.print(GSON.toJson(Map.of("message", "Channel " + ch + " unlocked", "channel", ch)));
                }
                case "/api/parental/pin" -> {
                    String pin = (String) body.get("pin");
                    if (pin == null || !pin.matches("\\d{4}")) {
                        resp.setStatus(400);
                        out.print("{\"error\":\"PIN must be exactly 4 digits\"}");
                        return;
                    }
                    parentalDAO.setPin(user.getId(), pin);
                    out.print("{\"message\":\"PIN updated successfully\"}");
                }
                case "/api/parental/verify" -> {
                    String pin = (String) body.get("pin");
                    boolean ok = pin != null && parentalDAO.verifyPin(user.getId(), pin);
                    if (ok) {
                        out.print("{\"verified\":true,\"message\":\"PIN accepted\"}");
                    } else {
                        resp.setStatus(403);
                        out.print("{\"verified\":false,\"error\":\"Invalid PIN\"}");
                    }
                }
                case "/api/parental/enable" -> {
                    Object en = body.get("enabled");
                    boolean enabled = en instanceof Boolean ? (Boolean) en : Boolean.parseBoolean(String.valueOf(en));
                    parentalDAO.setEnabled(user.getId(), enabled);
                    out.print(GSON.toJson(Map.of("message", "Parental control " + (enabled ? "enabled" : "disabled"), "enabled", enabled)));
                }
                default -> {
                    resp.setStatus(404);
                    out.print("{\"error\":\"Not found\"}");
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User authenticate(HttpServletRequest req) {
        String token = req.getHeader(TOKEN_HEADER);
        return userDAO.getUserByToken(token).orElse(null);
    }

    private int channel(Map<String, Object> body) {
        return ((Number) body.get("channel")).intValue();
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
