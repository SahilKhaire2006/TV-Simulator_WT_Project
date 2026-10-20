package com.simulator.model;

import java.util.Objects;

/**
 * Represents a television broadcast channel with metadata and stream info.
 */
public class Channel {

    private final int number;
    private final String name;
    private final String logoUrl;
    private final String streamUrl;   // HLS / MP4 / public stream URL
    private final String genre;
    private final String description;

    public Channel(int number, String name, String logoUrl,
                   String streamUrl, String genre, String description) {
        this.number      = number;
        this.name        = name;
        this.logoUrl     = logoUrl;
        this.streamUrl   = streamUrl;
        this.genre       = genre;
        this.description = description;
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public int    getNumber()      { return number;      }
    public String getName()        { return name;        }
    public String getLogoUrl()     { return logoUrl;     }
    public String getStreamUrl()   { return streamUrl;   }
    public String getGenre()       { return genre;       }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return "Channel{number=" + number + ", name='" + name + "', genre='" + genre + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Channel)) return false;
        Channel c = (Channel) o;
        return number == c.number;
    }

    @Override
    public int hashCode() {
        return Objects.hash(number);
    }
}
