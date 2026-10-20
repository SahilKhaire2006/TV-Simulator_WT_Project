package com.simulator.websocket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.simulator.model.Channel;
import com.simulator.model.ClientState;
import com.simulator.model.WsMessage;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central WebSocket hub for the TV Broadcast Simulator.
 *
 * <p>Two categories of clients connect here:</p>
 * <ol>
 *   <li><b>TV Clients</b> – browser windows running {@code client-player.jsp}.
 *       They register with type = "TV" and receive channel/volume commands.</li>
 *   <li><b>Remote Controls</b> – PWA React app sessions.
 *       They register with type = "REMOTE" and can send commands to any TV.</li>
 * </ol>
 *
 * <p>WebSocket URL: {@code ws://<host>:<port>/tv-simulator/ws/tv}</p>
 */
@ServerEndpoint(value = "/ws/tv")
public class TVServerEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(TVServerEndpoint.class);

    // ── Shared channel catalog (loaded once) ─────────────────────────────────
    private static final List<Channel> CHANNELS = buildChannelCatalog();

    /**
     * Server epoch (millis) used as the universal broadcast clock.
     * Every client receives this in STATE_UPDATE messages and uses it to
     * compute the correct playback position for true multi-TV synchronisation.
     * Using a fixed epoch (server start time) means all clients independently
     * derive the identical seek target – just like a real broadcast tower.
     */
    private static final long BROADCAST_EPOCH = System.currentTimeMillis();

    // ── Thread-safe session maps ──────────────────────────────────────────────
    /** sessionId → ClientState (TV clients only) */
    private static final ConcurrentHashMap<String, ClientState> TV_SESSIONS =
            new ConcurrentHashMap<>();

    /** sessionId → Session (ALL sessions: TV + Remote) */
    private static final ConcurrentHashMap<String, Session> ALL_SESSIONS =
            new ConcurrentHashMap<>();

    /** clientId (int) → sessionId – quick reverse-lookup for targeting */
    private static final ConcurrentHashMap<Integer, String> CLIENT_ID_MAP =
            new ConcurrentHashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle callbacks
    // ─────────────────────────────────────────────────────────────────────────

    @OnOpen
    public void onOpen(Session session) {
        ALL_SESSIONS.put(session.getId(), session);
        LOG.info("[WS] New connection: sessionId={}", session.getId());
        // Ask client to self-register
        sendToSession(session, buildTextMessage("{\"type\":\"HELLO\",\"payload\":\"Send REGISTER message\"}"));
    }

    @OnMessage
    public void onMessage(String rawJson, Session sender) {
        LOG.debug("[WS] Message from {}: {}", sender.getId(), rawJson);

        WsMessage msg;
        try {
            msg = GSON.fromJson(rawJson, WsMessage.class);
        } catch (Exception ex) {
            LOG.error("[WS] Bad JSON from {}: {}", sender.getId(), rawJson);
            sendToSession(sender, GSON.toJson(WsMessage.error("Invalid JSON payload")));
            return;
        }

        if (msg.getType() == null) {
            sendToSession(sender, GSON.toJson(WsMessage.error("Missing 'type' field")));
            return;
        }

        switch (msg.getType()) {
            case WsMessage.TYPE_REGISTER  -> handleRegister(msg, sender);
            case WsMessage.TYPE_COMMAND   -> handleCommand(msg, sender);
            case WsMessage.TYPE_PING      -> sendToSession(sender, GSON.toJson(WsMessage.pong()));
            default -> LOG.warn("[WS] Unknown message type '{}' from {}", msg.getType(), sender.getId());
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        ALL_SESSIONS.remove(session.getId());
        ClientState cs = TV_SESSIONS.remove(session.getId());
        if (cs != null) {
            CLIENT_ID_MAP.remove(cs.getClientId());
            LOG.info("[WS] TV client disconnected: clientId={}, label={}", cs.getClientId(), cs.getClientLabel());
            broadcastClientList(); // notify remotes
        } else {
            LOG.info("[WS] Remote/unknown session closed: {}", session.getId());
        }
    }

    @OnError
    public void onError(Session session, Throwable ex) {
        LOG.error("[WS] Error on session {}: {}", session.getId(), ex.getMessage(), ex);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Handlers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles a REGISTER message.
     * Expected payload shape:
     * <pre>{"type":"REGISTER","clientLabel":"TV-1 / Port 8081","payload":"TV"}</pre>
     * payload="TV"     → registers as a TV client
     * payload="REMOTE" → registers as a remote (no state stored)
     */
    private void handleRegister(WsMessage msg, Session sender) {
        String role  = msg.getPayload() != null ? msg.getPayload().toUpperCase() : "TV";
        String label = msg.getClientLabel() != null ? msg.getClientLabel() : "Unknown Device";

        if ("TV".equals(role)) {
            ClientState cs = new ClientState(sender.getId(), label);
            TV_SESSIONS.put(sender.getId(), cs);
            CLIENT_ID_MAP.put(cs.getClientId(), sender.getId());
            LOG.info("[WS] TV registered: clientId={}, label={}", cs.getClientId(), label);

            // Send initial state back to the TV (includes broadcastEpoch for sync)
            Channel ch = getChannel(cs.getCurrentChannel());
            WsMessage stateMsg = WsMessage.stateUpdate(cs, ch);
            stateMsg.setType(WsMessage.TYPE_STATE_UPDATE);
            stateMsg.setBroadcastEpoch(BROADCAST_EPOCH);
            sendToSession(sender, GSON.toJson(stateMsg));
        }

        // Notify ALL remote controls about the updated client list
        broadcastClientList();
    }

    /**
     * Handles a COMMAND message from a remote control.
     * msg.getClientId() == -1  → broadcast to ALL TVs
     * msg.getClientId() > 0   → target specific TV
     */
    private void handleCommand(WsMessage msg, Session sender) {
        String cmd      = msg.getCommand();
        int    targetId = msg.getClientId();

        if (cmd == null) {
            sendToSession(sender, GSON.toJson(WsMessage.error("Missing 'command' field")));
            return;
        }

        if (targetId == -1) {
            // Broadcast command to all TVs
            TV_SESSIONS.forEach((sid, cs) -> applyCommand(cmd, msg, cs, ALL_SESSIONS.get(sid)));
        } else {
            String sid = CLIENT_ID_MAP.get(targetId);
            if (sid == null) {
                sendToSession(sender, GSON.toJson(WsMessage.error("No TV with clientId=" + targetId)));
                return;
            }
            ClientState cs  = TV_SESSIONS.get(sid);
            Session     tvS = ALL_SESSIONS.get(sid);
            if (cs != null && tvS != null) {
                applyCommand(cmd, msg, cs, tvS);
            }
        }
    }

    /**
     * Applies a single command to a TV's {@link ClientState} and pushes
     * the resulting {@link WsMessage#TYPE_STATE_UPDATE} to that TV session.
     */
    private void applyCommand(String cmd, WsMessage msg, ClientState cs, Session tvSession) {
        int maxChannel = 24;

        switch (cmd) {
            case WsMessage.CMD_CHANNEL_SET -> {
                int ch = Math.max(1, Math.min(99, msg.getChannel()));
                cs.setCurrentChannel(ch);
            }
            case WsMessage.CMD_CHANNEL_UP -> {
                int next = cs.getCurrentChannel() + 1;
                cs.setCurrentChannel(next > maxChannel ? 1 : next);
            }
            case WsMessage.CMD_CHANNEL_DOWN -> {
                int prev = cs.getCurrentChannel() - 1;
                cs.setCurrentChannel(prev < 1 ? maxChannel : prev);
            }
            case WsMessage.CMD_VOLUME_SET -> cs.setVolume(msg.getVolume());
            case WsMessage.CMD_VOLUME_UP  -> cs.setVolume(cs.getVolume() + 5);
            case WsMessage.CMD_VOLUME_DOWN -> cs.setVolume(cs.getVolume() - 5);
            case WsMessage.CMD_MUTE_TOGGLE -> cs.setMuted(!cs.isMuted());
            case WsMessage.CMD_POWER_TOGGLE -> cs.setPowered(!cs.isPowered());
            case WsMessage.CMD_POWER_ON  -> cs.setPowered(true);
            case WsMessage.CMD_POWER_OFF -> cs.setPowered(false);
            default -> LOG.warn("[WS] Unknown command '{}' for clientId={}", cmd, cs.getClientId());
        }

        LOG.info("[WS] Applied '{}' → {}", cmd, cs);

        // Push STATE_UPDATE to the TV
        Channel ch      = getChannel(cs.getCurrentChannel());
        WsMessage reply = WsMessage.stateUpdate(cs, ch);
        reply.setBroadcastEpoch(BROADCAST_EPOCH);
        if (tvSession != null && tvSession.isOpen()) {
            sendToSession(tvSession, GSON.toJson(reply));
        }

        // Push STATE_UPDATE to ALL remotes so LCD display refreshes
        broadcastStateUpdate(cs, ch);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Broadcast helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends the current client list to every connected remote control session
     * (i.e., all sessions that are NOT in TV_SESSIONS).
     */
    private void broadcastClientList() {
        List<Map<String, Object>> clientInfoList = new ArrayList<>();
        TV_SESSIONS.forEach((sid, cs) -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("clientId",    cs.getClientId());
            info.put("clientLabel", cs.getClientLabel());
            info.put("channel",     cs.getCurrentChannel());
            info.put("volume",      cs.getVolume());
            info.put("powered",     cs.isPowered());
            info.put("muted",       cs.isMuted());
            clientInfoList.add(info);
        });

        Map<String, Object> listMsg = new LinkedHashMap<>();
        listMsg.put("type",    WsMessage.TYPE_CLIENT_LIST);
        listMsg.put("clients", clientInfoList);
        String json = GSON.toJson(listMsg);

        // Send only to non-TV sessions (remotes)
        ALL_SESSIONS.forEach((sid, s) -> {
            if (!TV_SESSIONS.containsKey(sid) && s.isOpen()) {
                sendToSession(s, json);
            }
        });
    }

    /** Sends a STATE_UPDATE event to all remote sessions. */
    private void broadcastStateUpdate(ClientState cs, Channel ch) {
        WsMessage su = WsMessage.stateUpdate(cs, ch);
        String json  = GSON.toJson(su);
        ALL_SESSIONS.forEach((sid, s) -> {
            if (!TV_SESSIONS.containsKey(sid) && s.isOpen()) {
                sendToSession(s, json);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void sendToSession(Session session, String text) {
        if (session == null || !session.isOpen()) return;
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(text);
                }
            }
        } catch (Exception ex) {
            LOG.error("[WS] Failed to send message to session {}: {}", session.getId(), ex.getMessage());
        }
    }

    private String buildTextMessage(String rawJson) { return rawJson; }

    private Channel getChannel(int number) {
        return CHANNELS.stream()
                .filter(c -> c.getNumber() == number)
                .findFirst()
                .orElseGet(() -> new Channel(
                        number,
                        "Signal Lost",
                        "/tv-simulator/assets/logos/signal_lost.svg",
                        "/tv-simulator/videos/signal_lost.mp4",
                        "No Signal",
                        "Channel " + number + " Unavailable - Signal Lost"
                ));
    }

    // ── Exposed for REST endpoints ────────────────────────────────────────────
    public static List<Channel>                       getChannelCatalog() { return Collections.unmodifiableList(CHANNELS); }
    public static ConcurrentHashMap<String, ClientState> getTvSessions()  { return TV_SESSIONS; }

    // ─────────────────────────────────────────────────────────────────────────
    // Channel catalog definition
    // ─────────────────────────────────────────────────────────────────────────

    private static List<Channel> buildChannelCatalog() {
        List<Channel> list = new ArrayList<>();
        list.add(new Channel(1,  "NovaStar News",    "/tv-simulator/assets/logos/ch1.svg",
                "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                "News",         "24/7 live news coverage (Default Rabbit Stream)"));
        list.add(new Channel(2,  "Galaxy Cinema",    "/tv-simulator/assets/logos/ch2.svg",
                "/tv-simulator/videos/ch2.mp4",
                "Movies",       "Blockbuster films around the clock"));
        list.add(new Channel(3,  "SportsPulse HD",   "/tv-simulator/assets/logos/ch3.svg",
                "/tv-simulator/videos/ch3.mp4",
                "Sports",       "Live sports and highlights"));
        list.add(new Channel(4,  "DocuWorld",         "/tv-simulator/assets/logos/ch4.svg",
                "/tv-simulator/videos/ch4.mp4",
                "Documentary",  "Nature, oceans, and wildlife"));
        list.add(new Channel(5,  "KidsZone",          "/tv-simulator/assets/logos/ch5.svg",
                "/tv-simulator/videos/ch5.mp4",
                "Kids",         "Family-friendly cartoons & shows"));
        list.add(new Channel(6,  "MusicBox",          "/tv-simulator/assets/logos/ch6.svg",
                "/tv-simulator/videos/ch6.mp4",
                "Music",        "Non-stop music videos & concerts"));
        list.add(new Channel(7,  "TechVision",        "/tv-simulator/assets/logos/ch7.svg",
                "/tv-simulator/videos/ch7.mp4",
                "Technology",   "Gadgets, AI, and the future"));
        list.add(new Channel(8,  "FoodieTV",          "/tv-simulator/assets/logos/ch8.svg",
                "/tv-simulator/videos/ch8.mp4",
                "Lifestyle",    "Cooking shows and culinary arts"));
        list.add(new Channel(9,  "HealthFirst",       "/tv-simulator/assets/logos/ch9.svg",
                "/tv-simulator/videos/ch9.mp4",
                "Health",       "Wellness, fitness, and exploration"));
        list.add(new Channel(10, "NightOwl Classic",  "/tv-simulator/assets/logos/ch10.svg",
                "/tv-simulator/videos/ch10.mp4",
                "Classic",      "Timeless movies and retro cinema"));
        list.add(new Channel(11, "Discovery Max",     "/tv-simulator/assets/logos/ch11.svg",
                "/tv-simulator/videos/ch11.mp4",
                "Discovery",    "Deep science and world wonders"));
        list.add(new Channel(12, "Action Horizon",    "/tv-simulator/assets/logos/ch12.svg",
                "/tv-simulator/videos/ch12.mp4",
                "Action",       "High-octane thrills and action"));
        list.add(new Channel(13, "Travel Planet",     "/tv-simulator/assets/logos/ch13.svg",
                "/tv-simulator/videos/ch13.mp4",
                "Travel",       "World travel destinations"));
        list.add(new Channel(14, "Science Quest",     "/tv-simulator/assets/logos/ch14.svg",
                "/tv-simulator/videos/ch14.mp4",
                "Science",      "Cosmology, physics, and future tech"));
        list.add(new Channel(15, "Retro Toons",       "/tv-simulator/assets/logos/ch15.svg",
                "/tv-simulator/videos/ch15.mp4",
                "Animation",    "Classic cartoons & hand-drawn anime"));
        list.add(new Channel(16, "Beat Wave",         "/tv-simulator/assets/logos/ch16.svg",
                "/tv-simulator/videos/ch16.mp4",
                "Music",        "Electronic, pop, and indie charts"));
        list.add(new Channel(17, "Cyber Space",       "/tv-simulator/assets/logos/ch17.svg",
                "/tv-simulator/videos/ch17.mp4",
                "Gaming",       "Gaming culture and esports"));
        list.add(new Channel(18, "Prime Premiere",    "/tv-simulator/assets/logos/ch18.svg",
                "/tv-simulator/videos/ch18.mp4",
                "Cinema",       "Award-winning world cinema"));
        list.add(new Channel(19, "Epic Wildlife",     "/tv-simulator/assets/logos/ch19.svg",
                "/tv-simulator/videos/ch19.mp4",
                "Nature",       "Untamed animal kingdoms"));
        list.add(new Channel(20, "Speed Racing",      "/tv-simulator/assets/logos/ch20.svg",
                "/tv-simulator/videos/ch20.mp4",
                "Motorsports",  "Fast track circuits and drag races"));
        list.add(new Channel(21, "Urban Beats",       "/tv-simulator/assets/logos/ch21.svg",
                "/tv-simulator/videos/ch21.mp4",
                "Urban",        "Street culture and live sessions"));
        list.add(new Channel(22, "Comedy Central",    "/tv-simulator/assets/logos/ch22.svg",
                "/tv-simulator/videos/ch22.mp4",
                "Comedy",       "Standup, sketches, and laughs"));
        list.add(new Channel(23, "Anime Hub",         "/tv-simulator/assets/logos/ch23.svg",
                "/tv-simulator/videos/ch23.mp4",
                "Anime",        "Trending anime sagas and series"));
        list.add(new Channel(24, "Indie Shorts",      "/tv-simulator/assets/logos/ch24.svg",
                "/tv-simulator/videos/ch24.mp4",
                "Indie",        "Independent short films and features"));
        return list;
    }
}
