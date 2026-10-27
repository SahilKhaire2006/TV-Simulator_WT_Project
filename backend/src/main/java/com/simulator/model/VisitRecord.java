package com.simulator.model;

/**
 * Represents a channel visit record – either per-user or aggregate.
 * Used by the admin panel for recommendation insights.
 */
public class VisitRecord {

    private int     id;
    private Integer userId;          // nullable for anonymous visits
    private int     channelNumber;
    private int     visitCount;
    private String  lastVisited;
    private String  channelName;     // populated from catalog
    private String  channelGenre;    // populated from catalog

    public VisitRecord() {}

    // ── Getters / Setters ──────────────────────────────────────────────────
    public int     getId()              { return id; }
    public void    setId(int id)        { this.id = id; }
    public Integer getUserId()          { return userId; }
    public void    setUserId(Integer u)  { this.userId = u; }
    public int     getChannelNumber()   { return channelNumber; }
    public void    setChannelNumber(int c) { this.channelNumber = c; }
    public int     getVisitCount()      { return visitCount; }
    public void    setVisitCount(int v)  { this.visitCount = v; }
    public String  getLastVisited()     { return lastVisited; }
    public void    setLastVisited(String l) { this.lastVisited = l; }
    public String  getChannelName()     { return channelName; }
    public void    setChannelName(String n) { this.channelName = n; }
    public String  getChannelGenre()    { return channelGenre; }
    public void    setChannelGenre(String g) { this.channelGenre = g; }
}
