package com.simulator.dao;

import com.simulator.db.DBConnection;
import com.simulator.model.Channel;
import com.simulator.model.VisitRecord;
import com.simulator.websocket.TVServerEndpoint;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for {@link VisitRecord} – tracks how many times a
 * user visits (tunes into) a particular channel.  This data feeds the
 * admin panel recommendation insights.
 */
public class VisitHistoryDAO {

    /**
     * Record a channel visit. If a row already exists for
     * (user_id, channel_number) the visit_count is incremented.
     * userId may be null for anonymous visits.
     */
    public void recordVisit(Integer userId, int channelNumber) {
        String sql = "INSERT INTO visit_history (user_id, channel_number, visit_count) " +
                     "VALUES (?, ?, 1) " +
                     "ON DUPLICATE KEY UPDATE visit_count = visit_count + 1, last_visited = NOW()";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (userId != null) {
                ps.setInt(1, userId);
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setInt(2, channelNumber);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[VisitHistoryDAO] record error: " + e.getMessage());
        }
    }

    /**
     * Get visit records for a specific user, enriched with channel info.
     */
    public List<VisitRecord> getUserVisits(int userId) {
        List<VisitRecord> list = new ArrayList<>();
        String sql = "SELECT id, channel_number, visit_count, last_visited " +
                     "FROM visit_history WHERE user_id = ? ORDER BY visit_count DESC";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(enrich(rs, userId));
                }
            }
        } catch (SQLException e) {
            System.err.println("[VisitHistoryDAO] user visits error: " + e.getMessage());
        }
        return list;
    }

    /**
     * Get aggregate visit counts across ALL users (for the admin panel).
     * Null userId rows are included.
     */
    public List<VisitRecord> getAggregateVisits() {
        List<VisitRecord> list = new ArrayList<>();
        String sql = "SELECT channel_number, SUM(visit_count) AS total_visits " +
                     "FROM visit_history GROUP BY channel_number ORDER BY total_visits DESC";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                VisitRecord v = new VisitRecord();
                v.setChannelNumber(rs.getInt("channel_number"));
                v.setVisitCount(rs.getInt("total_visits"));
                enrichChannel(v);
                list.add(v);
            }
        } catch (SQLException e) {
            System.err.println("[VisitHistoryDAO] aggregate error: " + e.getMessage());
        }
        return list;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private VisitRecord enrich(ResultSet rs, Integer userId) throws SQLException {
        VisitRecord v = new VisitRecord();
        v.setId(rs.getInt("id"));
        v.setUserId(userId);
        v.setChannelNumber(rs.getInt("channel_number"));
        v.setVisitCount(rs.getInt("visit_count"));
        v.setLastVisited(rs.getString("last_visited"));
        enrichChannel(v);
        return v;
    }

    private void enrichChannel(VisitRecord v) {
        Channel ch = TVServerEndpoint.getChannelCatalog().stream()
                .filter(c -> c.getNumber() == v.getChannelNumber())
                .findFirst().orElse(null);
        if (ch != null) {
            v.setChannelName(ch.getName());
            v.setChannelGenre(ch.getGenre());
        } else {
            v.setChannelName("Channel " + v.getChannelNumber());
            v.setChannelGenre("Unknown");
        }
    }
}
