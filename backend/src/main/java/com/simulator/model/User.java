package com.simulator.model;

/**
 * Represents a registered user of the TV Broadcast Simulator.
 * Used for authentication, favorites, visit history, and parental control.
 */
public class User {

    private int id;
    private String username;
    private String password;   // SHA-256 hashed (never stored in plain text)
    private String role;        // "user" or "admin"
    private String createdAt;

    public User() {}

    public User(int id, String username, String role) {
        this.id       = id;
        this.username = username;
        this.role     = role;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────
    public int    getId()            { return id; }
    public void   setId(int id)      { this.id = id; }
    public String getUsername()      { return username; }
    public void   setUsername(String u) { this.username = u; }
    public String getPassword()      { return password; }
    public void   setPassword(String p) { this.password = p; }
    public String getRole()          { return role; }
    public void   setRole(String r)  { this.role = r; }
    public String getCreatedAt()     { return createdAt; }
    public void   setCreatedAt(String c) { this.createdAt = c; }
}
