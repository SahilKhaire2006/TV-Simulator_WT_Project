package com.simulator.dao;

import com.simulator.db.DBConnection;
import com.simulator.model.Channel;
import com.simulator.model.Favorite;
import com.simulator.websocket.TVServerEndpoint;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for {@link Favorite} – manages per-user channel
 * favourites stored in MySQL via JDBC.
 */
public class FavoriteDAO {

    /**
     * Add a channel to the user's favourites.
     * Uses INSERT IGNORE so duplicates don't throw.
     */
    public boolean addFavorite(int userId, int channelNumber) {
        String sql = "INSERT IGNORE INTO favorites (user_id, channel_number) VALUES (?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, channelNumber);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[FavoriteDAO] add error: " + e.getMessage());
        }
        return false;
    }

    /** Remove a channel from the user's favourites. */
    public boolean removeFavorite(int userId, int channelNumber) {
        String sql = "DELETE FROM favorites WHERE user_id = ? AND channel_number = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, channelNumber);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[FavoriteDAO] remove error: " + e.getMessage());
        }
        return false;
    }

    /** Get all favourite channel numbers for a user. */
    public List<Integer> getFavoriteChannelNumbers(int userId) {
        List<Integer> nums = new ArrayList<>();
        String sql = "SELECT channel_number FROM favorites WHERE user_id = ? ORDER BY added_at DESC";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) nums.add(rs.getInt("channel_number"));
            }
        } catch (SQLException e) {
            System.err.println("[FavoriteDAO] list nums error: " + e.getMessage());
        }
        return nums;
    }

    /**
     * Get all favourite channels for a user, enriched with channel name/genre
     * from the in-memory catalog.
     */
    public List<Favorite> getFavorites(int userId) {
        List<Favorite> list = new ArrayList<>();
        String sql = "SELECT id, channel_number, added_at FROM favorites " +
                     "WHERE user_id = ? ORDER BY added_at DESC";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Favorite f = new Favorite();
                    f.setId(rs.getInt("id"));
                    f.setUserId(userId);
                    f.setChannelNumber(rs.getInt("channel_number"));
                    f.setAddedAt(rs.getString("added_at"));

                    // Enrich with catalog info
                    Channel ch = TVServerEndpoint.getChannelCatalog().stream()
                            .filter(c2 -> c2.getNumber() == f.getChannelNumber())
                            .findFirst().orElse(null);
                    if (ch != null) {
                        f.setChannelName(ch.getName());
                        f.setChannelGenre(ch.getGenre());
                    } else {
                        f.setChannelName("Channel " + f.getChannelNumber());
                        f.setChannelGenre("Unknown");
                    }
                    list.add(f);
                }
            }
        } catch (SQLException e) {
            System.err.println("[FavoriteDAO] list error: " + e.getMessage());
        }
        return list;
    }

    /** Check if a channel is already favourited by the user. */
    public boolean isFavorite(int userId, int channelNumber) {
        String sql = "SELECT 1 FROM favorites WHERE user_id = ? AND channel_number = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, channelNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[FavoriteDAO] isFavorite error: " + e.getMessage());
        }
        return false;
    }
}
