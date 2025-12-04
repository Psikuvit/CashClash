package me.psikuvit.cashClash.storage;

import com.google.gson.Gson;
import me.psikuvit.cashClash.player.PlayerData;
import me.psikuvit.cashClash.CashClashPlugin;

import java.io.IOException;
import java.sql.*;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal MySQL provider. Assumes a database is specified in the JDBC URL or via schema in config.
 */
public class MySQLProvider implements DatabaseProvider {

    private final String jdbcUrl;
    private final String user;
    private final String pass;
    private Connection conn;
    private final Gson gson = new Gson();

    public MySQLProvider(String jdbcUrl, String user, String pass) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.pass = pass;
    }

    @Override
    public void init() throws SQLException {
        conn = DriverManager.getConnection(jdbcUrl, user, pass);
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS players (uuid VARCHAR(36) PRIMARY KEY, json LONGTEXT)");
        }
    }

    @Override
    public Optional<PlayerData> loadPlayer(UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT json FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String json = rs.getString(1);
                PlayerData p = gson.fromJson(json, PlayerData.class);
                return Optional.ofNullable(p);
            }
        }
    }

    @Override
    public void savePlayer(PlayerData player) throws SQLException {
        String json = gson.toJson(player);
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO players(uuid,json) VALUES(?,?) ON DUPLICATE KEY UPDATE json = VALUES(json)")) {
            ps.setString(1, player.getUuid().toString());
            ps.setString(2, json);
            ps.executeUpdate();
        }
    }

    @Override
    public void close() throws IOException {
        try { if (conn != null && !conn.isClosed()) conn.close(); } catch (SQLException ignored) { CashClashPlugin.getInstance().getLogger().warning("Unable to close MySQL provider: " + ignored.getMessage()); }
    }
}
