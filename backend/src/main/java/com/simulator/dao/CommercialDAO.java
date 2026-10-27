package com.simulator.dao;

import com.simulator.db.DBConnection;
import com.simulator.model.Commercial;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for {@link Commercial} – manages ad content stored
 * in MySQL, shown in the TV client player.
 */
public class CommercialDAO {

    /** Get all active commercials. */
    public List<Commercial> getActiveCommercials() {
        return queryList("SELECT * FROM commercials WHERE is_active = TRUE ORDER BY created_at");
    }

    /** Get active commercials for a specific channel (or all-channel ads). */
    public List<Commercial> getCommercialsForChannel(int channelNumber) {
        String sql = "SELECT * FROM commercials WHERE is_active = TRUE " +
                     "AND (target_channel IS NULL OR target_channel = ?) ORDER BY created_at";
        return queryList(sql, ps -> ps.setInt(1, channelNumber));
    }

    /** Get all commercials (for admin panel). */
    public List<Commercial> getAllCommercials() {
        return queryList("SELECT * FROM commercials ORDER BY created_at DESC");
    }

    /** Create a new commercial. */
    public Commercial create(Commercial ad) {
        String sql = "INSERT INTO commercials " +
                     "(title, content, image_url, target_channel, duration_seconds, is_active) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ad.getTitle());
            ps.setString(2, ad.getContent());
            ps.setString(3, ad.getImageUrl());
            if (ad.getTargetChannel() != null) ps.setInt(4, ad.getTargetChannel());
            else ps.setNull(4, Types.INTEGER);
            ps.setInt(5, ad.getDurationSeconds());
            ps.setBoolean(6, ad.isActive());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) ad.setId(rs.getInt(1));
            }
            return ad;
        } catch (SQLException e) {
            System.err.println("[CommercialDAO] create error: " + e.getMessage());
        }
        return null;
    }

    /** Update an existing commercial. */
    public boolean update(Commercial ad) {
        String sql = "UPDATE commercials SET title=?, content=?, image_url=?, " +
                     "target_channel=?, duration_seconds=?, is_active=? WHERE id=?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ad.getTitle());
            ps.setString(2, ad.getContent());
            ps.setString(3, ad.getImageUrl());
            if (ad.getTargetChannel() != null) ps.setInt(4, ad.getTargetChannel());
            else ps.setNull(4, Types.INTEGER);
            ps.setInt(5, ad.getDurationSeconds());
            ps.setBoolean(6, ad.isActive());
            ps.setInt(7, ad.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[CommercialDAO] update error: " + e.getMessage());
        }
        return false;
    }

    /** Delete a commercial by id. */
    public boolean delete(int id) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM commercials WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[CommercialDAO] delete error: " + e.getMessage());
        }
        return false;
    }

    // ── internal helpers ─────────────────────────────────────────────────────

    @FunctionalInterface
    private interface ParamSetter { void set(PreparedStatement ps) throws SQLException; }

    private List<Commercial> queryList(String sql) {
        return queryList(sql, ps -> {});
    }

    private List<Commercial> queryList(String sql, ParamSetter setter) {
        List<Commercial> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            setter.set(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            System.err.println("[CommercialDAO] query error: " + e.getMessage());
        }
        return list;
    }

    private Commercial map(ResultSet rs) throws SQLException {
        Commercial ad = new Commercial();
        ad.setId(rs.getInt("id"));
        ad.setTitle(rs.getString("title"));
        ad.setContent(rs.getString("content"));
        ad.setImageUrl(rs.getString("image_url"));
        int tc = rs.getInt("target_channel");
        ad.setTargetChannel(rs.wasNull() ? null : tc);
        ad.setDurationSeconds(rs.getInt("duration_seconds"));
        ad.setActive(rs.getBoolean("is_active"));
        ad.setCreatedAt(rs.getString("created_at"));
        return ad;
    }
}
