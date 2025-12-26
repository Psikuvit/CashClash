package me.psikuvit.cashClash.storage;

import com.google.gson.Gson;
import me.psikuvit.cashClash.player.PlayerData;
import me.psikuvit.cashClash.util.Messages;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite implementation using a simple json blob for complex fields.
 */
public class SQLiteProvider implements DatabaseProvider {

    private final File dbFile;
    private Connection conn;
    private final Gson gson = new Gson();

    public SQLiteProvider(File dbFile) {
        this.dbFile = dbFile;
    }

    @Override
    public void init() throws SQLException {
        try {
            if (!dbFile.exists()) {
                Files.createDirectories(dbFile.getParentFile().toPath());
                dbFile.createNewFile();
            }
        } catch (Exception e) {
            throw new SQLException("Unable to create SQLite file", e);
        }

        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        conn = DriverManager.getConnection(url);

        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, json TEXT)");
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
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO players(uuid,json) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET json=excluded.json")) {
            ps.setString(1, player.getUuid().toString());
            ps.setString(2, json);
            ps.executeUpdate();
        }
    }

    @Override
    public void close() throws IOException {
        try { if (conn != null && !conn.isClosed()) conn.close(); } catch (SQLException sqlException) {
            Messages.debug("STORAGE", "Unable to close SQLite provider: " + sqlException.getMessage());
        }
    }
}
