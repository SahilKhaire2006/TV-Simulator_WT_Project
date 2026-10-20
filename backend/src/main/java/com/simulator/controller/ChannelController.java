package com.simulator.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.simulator.model.Channel;
import com.simulator.model.ClientState;
import com.simulator.websocket.TVServerEndpoint;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API Servlet – exposes channel catalog and connected-client state.
 *
 * <pre>
 * GET  /api/channels          → JSON list of all channels
 * GET  /api/clients           → JSON list of connected TV clients
 * GET  /api/status            → server health / summary
 * </pre>
 */
@WebServlet(urlPatterns = {"/api/channels", "/api/clients", "/api/status"})
public class ChannelController extends HttpServlet {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Cache-Control", "no-cache");

        String path = req.getServletPath();
        try (PrintWriter out = resp.getWriter()) {
            switch (path) {
                case "/api/channels" -> out.print(GSON.toJson(buildChannelResponse()));
                case "/api/clients"  -> out.print(GSON.toJson(buildClientResponse()));
                case "/api/status"   -> out.print(GSON.toJson(buildStatusResponse()));
                default              -> {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    out.print("{\"error\":\"Not found\"}");
                }
            }
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    // ── Response builders ─────────────────────────────────────────────────────

    private List<Map<String, Object>> buildChannelResponse() {
        return TVServerEndpoint.getChannelCatalog().stream().map(ch -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("number",      ch.getNumber());
            map.put("name",        ch.getName());
            map.put("logoUrl",     ch.getLogoUrl());
            map.put("streamUrl",   ch.getStreamUrl());
            map.put("genre",       ch.getGenre());
            map.put("description", ch.getDescription());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildClientResponse() {
        List<Map<String, Object>> result = new ArrayList<>();
        TVServerEndpoint.getTvSessions().forEach((sid, cs) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("clientId",    cs.getClientId());
            map.put("clientLabel", cs.getClientLabel());
            map.put("channel",     cs.getCurrentChannel());
            map.put("volume",      cs.getVolume());
            map.put("powered",     cs.isPowered());
            map.put("muted",       cs.isMuted());
            map.put("connectedAt", cs.getConnectedAt().toString());
            result.add(map);
        });
        return result;
    }

    private Map<String, Object> buildStatusResponse() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status",          "OK");
        map.put("serverTime",      new Date().toString());
        map.put("totalChannels",   TVServerEndpoint.getChannelCatalog().size());
        map.put("connectedClients",TVServerEndpoint.getTvSessions().size());
        return map;
    }
}
