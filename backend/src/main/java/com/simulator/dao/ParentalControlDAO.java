package com.simulator.dao;

import com.simulator.db.DBConnection;
import com.simulator.db.PasswordUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for parental control – manages locked channels
 * per user and an optional 4-digit PIN override.
 */
public class ParentalControlDAO {

    // ── Channel locking ──────────────────────────────────────────────────────

    /** Lock a channel for a user. */
    public boolean lockChannel(int userId, int channelNumber) {
        String sql = "INSERT INTO parental_control (user_id, channel_number, is_locked) " +
                     "VALUES (?, ?, TRUE) ON DUPLICATE KEY UPDATE is_locked = TRUE";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, channelNumber);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ParentalControlDAO] lock error: " + e.getMessage());
        }
        return false;
    }

    /** Unlock a channel for a user. */
    public boolean unlockChannel(int userId, int channelNumber) {
        String sql = "DELETE FROM parental_control WHERE user_id = ? AND channel_number = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, channelNumber);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ParentalControlDAO] unlock error: " + e.getMessage());
        }
        return false;
    }

    /** Get all locked channel numbers for a user. */
    public List<Integer> getLockedChannels(int userId) {
        List<Integer> locked = new ArrayList<>();
        String sql = "SELECT channel_number FROM parental_control " +
                     "WHERE user_id = ? AND is_locked = TRUE";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) locked.add(rs.getInt("channel_number"));
            }
        } catch (SQLException e) {
            System.err.println("[ParentalControlDAO] getLocked error: " + e.getMessage());
        }
        return locked;
    }

    /** Check if a specific channel is locked for a user. */
    public boolean isChannelLocked(int userId, int channelNumber) {
        String sql = "SELECT 1 FROM parental_control " +
                     "WHERE user_id = ? AND channel_number = ? AND is_locked = TRUE";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, channelNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[ParentalControlDAO] isLocked error: " + e.getMessage());
        }
        return false;
    }

    // ── Parental PIN ─────────────────────────────────────────────────────────

    /** Set or update the parental control PIN for a user. */
    public boolean setPin(int userId, String plainPin) {
        String sql = "INSERT INTO user_settings (user_id, parental_pin, parental_enabled) " +
                     "VALUES (?, ?, TRUE) ON DUPLICATE KEY UPDATE " +
                     "parental_pin = VALUES(parental_pin), parental_enabled = TRUE";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, PasswordUtil.hashPin(plainPin));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ParentalControlDAO] setPin error: " + e.getMessage());
        }
        return false;
    }

    /** Verify a PIN against the stored hash. */
    public boolean verifyPin(int userId, String plainPin) {
        String sql = "SELECT parental_pin FROM user_settings WHERE user_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String stored = rs.getString("parental_pin");
                    return stored != null && PasswordUtil.verifyPin(plainPin, stored);
                }
            }
        } catch (SQLException e) {
            System.err.println("[ParentalControlDAO] verifyPin error: " + e.getMessage());
        }
        return false;
    }

    /** Enable / disable parental control globally for a user. */
    public boolean setEnabled(int userId, boolean enabled) {
        String sql = "UPDATE user_settings SET parental_enabled = ? WHERE user_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, enabled);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ParentalControlDAO] setEnabled error: " + e.getMessage());
        }
        return false;
    }

    /** Check if parental control is enabled for a user. */
    public boolean isEnabled(int userId) {
        String sql = "SELECT parental_enabled FROM user_settings WHERE user_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("parental_enabled");
            }
        } catch (SQLException e) {
            System.err.println("[ParentalControlDAO] isEnabled error: " + e.getMessage());
        }
        return false;
    }

    /** Check if a PIN has been set (without revealing it). */
    public boolean hasPinSet(int userId) {
        String sql = "SELECT parental_pin FROM user_settings WHERE user_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString("parental_pin") != null;
            }
        } catch (SQLException e) {
            System.err.println("[ParentalControlDAO] hasPinSet error: " + e.getMessage());
        }
        return false;
    }
}
