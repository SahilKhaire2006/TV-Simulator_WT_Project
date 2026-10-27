package com.simulator.dao;

import com.simulator.db.DBConnection;
import com.simulator.db.PasswordUtil;
import com.simulator.model.User;

import java.sql.*;
import java.util.Optional;

/**
 * Data Access Object for {@link User} – handles registration, login
 * verification, session tokens, and admin-user seeding.
 */
public class UserDAO {

    /**
     * Register a new user.
     * @return the created User (with generated id), or null if the username exists.
     */
    public User register(String username, String plainPassword) {
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, 'user')";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, username);
            ps.setString(2, PasswordUtil.hash(plainPassword));
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return new User(rs.getInt(1), username, "user");
                }
            }
        } catch (SQLException e) {
            // 1062 = duplicate username
            if (e.getErrorCode() != 1062) {
                System.err.println("[UserDAO] register error: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Authenticate a user by username + plain password.
     * @return the User if credentials match, else empty.
     */
    public Optional<User> login(String username, String plainPassword) {
        String sql = "SELECT id, username, password, role FROM users WHERE username = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password");
                    if (PasswordUtil.verify(plainPassword, storedHash)) {
                        return Optional.of(new User(
                                rs.getInt("id"),
                                rs.getString("username"),
                                rs.getString("role")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] login error: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Create a session token for a user and persist it.
     */
    public String createSession(int userId) {
        String token = PasswordUtil.generateToken();
        String sql = "INSERT INTO sessions (token, user_id, expires_at) " +
                     "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 24 HOUR))";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setInt(2, userId);
            ps.executeUpdate();
            return token;
        } catch (SQLException e) {
            System.err.println("[UserDAO] createSession error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Look up the user associated with a session token.
     * Removes expired sessions automatically.
     */
    public Optional<User> getUserByToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();

        // Delete expired sessions
        try (Connection c = DBConnection.getConnection();
             PreparedStatement del = c.prepareStatement(
                     "DELETE FROM sessions WHERE expires_at IS NOT NULL AND expires_at < NOW()")) {
            del.executeUpdate();
        } catch (SQLException ignored) {}

        String sql = "SELECT u.id, u.username, u.role " +
                     "FROM sessions s JOIN users u ON s.user_id = u.id " +
                     "WHERE s.token = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("role")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] getUserByToken error: " + e.getMessage());
        }
        return Optional.empty();
    }

    /** Invalidate (delete) a session token. */
    public void logout(String token) {
        if (token == null) return;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM sessions WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[UserDAO] logout error: " + e.getMessage());
        }
    }

    /** Ensure a default admin account exists (called at startup). */
    public void seedAdminIfAbsent() {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id FROM users WHERE username = 'admin'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    register("admin", "admin123");
                    // promote to admin role
                    try (PreparedStatement up = c.prepareStatement(
                            "UPDATE users SET role = 'admin' WHERE username = 'admin'")) {
                        up.executeUpdate();
                    }
                    System.out.println("[UserDAO] Seeded default admin (admin / admin123)");
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] seedAdmin error: " + e.getMessage());
        }
    }
}
