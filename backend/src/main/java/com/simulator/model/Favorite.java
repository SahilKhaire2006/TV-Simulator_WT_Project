package com.simulator.model;

/**
 * Represents a user's favorited channel.
 * channelName / channelGenre are populated from the channel catalog
 * when building API responses (not stored in the database).
 */
public class Favorite {

    private int     id;
    private int     userId;
    private int     channelNumber;
    private String  channelName;     // populated from catalog
    private String  channelGenre;    // populated from catalog
    private String  addedAt;

    public Favorite() {}

    public Favorite(int userId, int channelNumber) {
        this.userId       = userId;
        this.channelNumber = channelNumber;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────
    public int    getId()              { return id; }
    public void   setId(int id)        { this.id = id; }
    public int    getUserId()          { return userId; }
    public void   setUserId(int u)     { this.userId = u; }
    public int    getChannelNumber()   { return channelNumber; }
    public void   setChannelNumber(int c) { this.channelNumber = c; }
    public String getChannelName()    { return channelName; }
    public void   setChannelName(String n) { this.channelName = n; }
    public String getChannelGenre()   { return channelGenre; }
    public void   setChannelGenre(String g) { this.channelGenre = g; }
    public String getAddedAt()         { return addedAt; }
    public void   setAddedAt(String a)  { this.addedAt = a; }
}
