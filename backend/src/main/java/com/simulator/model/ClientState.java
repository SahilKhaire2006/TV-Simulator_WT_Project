package com.simulator.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a connected TV-client session: tracks its current channel,
 * volume, power state, and WebSocket session ID.
 */
public class ClientState {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);

    private final int    clientId;
    private final String sessionId;          // WebSocket session ID
    private final Instant connectedAt;

    // Mutable TV state
    private volatile int     currentChannel;
    private volatile int     volume;          // 0 – 100
    private volatile boolean powered;
    private volatile boolean muted;
    private volatile String  clientLabel;     // human-readable label, e.g. "TV-3 / Port 8083"

    public ClientState(String sessionId, String clientLabel) {
        this.clientId      = ID_COUNTER.getAndIncrement();
        this.sessionId     = sessionId;
        this.connectedAt   = Instant.now();
        this.clientLabel   = clientLabel;
        this.currentChannel = 1;
        this.volume         = 50;
        this.powered        = true;
        this.muted          = false;
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public int     getClientId()       { return clientId;       }
    public String  getSessionId()      { return sessionId;      }
    public Instant getConnectedAt()    { return connectedAt;    }
    public int     getCurrentChannel() { return currentChannel; }
    public int     getVolume()         { return volume;         }
    public boolean isPowered()         { return powered;        }
    public boolean isMuted()           { return muted;          }
    public String  getClientLabel()    { return clientLabel;    }

    // ── Setters ──────────────────────────────────────────────────────────────
    public void setCurrentChannel(int ch) { this.currentChannel = ch;   }
    public void setVolume(int v)          { this.volume = Math.max(0, Math.min(100, v)); }
    public void setPowered(boolean p)     { this.powered = p;           }
    public void setMuted(boolean m)       { this.muted = m;             }
    public void setClientLabel(String l)  { this.clientLabel = l;       }

    @Override
    public String toString() {
        return "ClientState{id=" + clientId + ", label='" + clientLabel +
               "', ch=" + currentChannel + ", vol=" + volume +
               ", powered=" + powered + ", muted=" + muted + "}";
    }
}
