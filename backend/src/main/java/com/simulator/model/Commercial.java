package com.simulator.model;

/**
 * Represents a commercial advertisement shown in the TV client.
 * Commercials can target all channels (targetChannel = null) or a
 * specific channel, and are stored in the database via JDBC.
 */
public class Commercial {

    private int     id;
    private String  title;
    private String  content;
    private String  imageUrl;
    private Integer targetChannel;   // null = all channels
    private int     durationSeconds;
    private boolean active;
    private String  createdAt;

    public Commercial() {}

    // ── Getters / Setters ──────────────────────────────────────────────────
    public int     getId()              { return id; }
    public void    setId(int id)         { this.id = id; }
    public String  getTitle()            { return title; }
    public void    setTitle(String t)    { this.title = t; }
    public String  getContent()          { return content; }
    public void    setContent(String c)  { this.content = c; }
    public String  getImageUrl()         { return imageUrl; }
    public void    setImageUrl(String u)  { this.imageUrl = u; }
    public Integer getTargetChannel()   { return targetChannel; }
    public void    setTargetChannel(Integer c) { this.targetChannel = c; }
    public int     getDurationSeconds() { return durationSeconds; }
    public void    setDurationSeconds(int d) { this.durationSeconds = d; }
    public boolean isActive()           { return active; }
    public void    setActive(boolean a)  { this.active = a; }
    public String  getCreatedAt()        { return createdAt; }
    public void    setCreatedAt(String c) { this.createdAt = c; }
}
