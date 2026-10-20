package com.simulator.model;

import java.time.Instant;

/**
 * Immutable envelope for WebSocket messages exchanged between the server,
 * TV clients, and the PWA remote control.
 *
 * JSON shape:
 * {
 *   "type":      "COMMAND | EVENT | STATE_UPDATE | BROADCAST | ERROR | PING | PONG | REGISTER",
 *   "clientId":  42,
 *   "channel":   5,
 *   "volume":    70,
 *   "powered":   true,
 *   "muted":     false,
 *   "payload":   "...",
 *   "timestamp": "2024-01-01T00:00:00Z"
 * }
 */
public class WsMessage {

    // ── Known message types ──────────────────────────────────────────────────
    public static final String TYPE_REGISTER      = "REGISTER";
    public static final String TYPE_COMMAND       = "COMMAND";
    public static final String TYPE_EVENT         = "EVENT";
    public static final String TYPE_STATE_UPDATE  = "STATE_UPDATE";
    public static final String TYPE_BROADCAST     = "BROADCAST";
    public static final String TYPE_ERROR         = "ERROR";
    public static final String TYPE_PING          = "PING";
    public static final String TYPE_PONG          = "PONG";
    public static final String TYPE_CLIENT_LIST   = "CLIENT_LIST";

    // ── Known commands ───────────────────────────────────────────────────────
    public static final String CMD_CHANNEL_SET    = "CHANNEL_SET";
    public static final String CMD_CHANNEL_UP     = "CHANNEL_UP";
    public static final String CMD_CHANNEL_DOWN   = "CHANNEL_DOWN";
    public static final String CMD_VOLUME_SET     = "VOLUME_SET";
    public static final String CMD_VOLUME_UP      = "VOLUME_UP";
    public static final String CMD_VOLUME_DOWN    = "VOLUME_DOWN";
    public static final String CMD_MUTE_TOGGLE    = "MUTE_TOGGLE";
    public static final String CMD_POWER_TOGGLE   = "POWER_TOGGLE";
    public static final String CMD_POWER_ON       = "POWER_ON";
    public static final String CMD_POWER_OFF      = "POWER_OFF";
    public static final String CMD_BROADCAST_ALL  = "BROADCAST_ALL";

    // ── Fields ───────────────────────────────────────────────────────────────
    private String  type;
    private int     clientId;       // target or source client id; -1 = broadcast all
    private String  command;        // sub-command within TYPE_COMMAND
    private int     channel;
    private int     volume;
    private boolean powered;
    private boolean muted;
    private String  payload;        // arbitrary string payload / error msg
    private String  timestamp;
    private String  clientLabel;    // optional human-readable label
    private long    serverTime;     // epoch millis for live broadcast clock sync
    private long    broadcastEpoch; // fixed server-start epoch; clients use this for sync

    public WsMessage() {
        this.timestamp  = Instant.now().toString();
        this.serverTime = System.currentTimeMillis();
    }

    // ── Static factory helpers ───────────────────────────────────────────────
    public static WsMessage command(String cmd, int targetClientId) {
        WsMessage m = new WsMessage();
        m.type     = TYPE_COMMAND;
        m.command  = cmd;
        m.clientId = targetClientId;
        return m;
    }

    public static WsMessage stateUpdate(ClientState s, Channel ch) {
        WsMessage m = new WsMessage();
        m.type        = TYPE_STATE_UPDATE;
        m.clientId    = s.getClientId();
        m.clientLabel = s.getClientLabel();
        m.channel     = s.getCurrentChannel();
        m.volume      = s.getVolume();
        m.powered     = s.isPowered();
        m.muted       = s.isMuted();
        m.payload     = ch != null ? ch.getName() : "Unknown";
        return m;
    }

    public static WsMessage error(String message) {
        WsMessage m = new WsMessage();
        m.type    = TYPE_ERROR;
        m.payload = message;
        return m;
    }

    public static WsMessage pong() {
        WsMessage m = new WsMessage();
        m.type = TYPE_PONG;
        return m;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public String  getType()        { return type;        }
    public void    setType(String t){ this.type = t;      }
    public int     getClientId()    { return clientId;    }
    public void    setClientId(int i){ this.clientId = i; }
    public String  getCommand()     { return command;     }
    public void    setCommand(String c){ this.command = c;}
    public int     getChannel()     { return channel;     }
    public void    setChannel(int c){ this.channel = c;   }
    public int     getVolume()      { return volume;      }
    public void    setVolume(int v) { this.volume = v;    }
    public boolean isPowered()      { return powered;     }
    public void    setPowered(boolean p){ this.powered = p;}
    public boolean isMuted()        { return muted;       }
    public void    setMuted(boolean m){ this.muted = m;   }
    public String  getPayload()     { return payload;     }
    public void    setPayload(String p){ this.payload = p;}
    public String  getTimestamp()   { return timestamp;   }
    public void    setTimestamp(String t){ this.timestamp = t;}
    public String  getClientLabel() { return clientLabel; }
    public void    setClientLabel(String l){ this.clientLabel = l;}
    public long    getServerTime()   { return serverTime; }
    public void    setServerTime(long s){ this.serverTime = s; }
    public long    getBroadcastEpoch()  { return broadcastEpoch; }
    public void    setBroadcastEpoch(long e){ this.broadcastEpoch = e; }
}
