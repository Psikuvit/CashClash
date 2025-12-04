package me.psikuvit.cashClash.storage;

import me.psikuvit.cashClash.player.PlayerData;

import java.io.Closeable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Abstraction for a player persistence provider. Implementations should
 * handle connection management and SQL specifics.
 */
public interface DatabaseProvider extends Closeable {

    /** Open / initialize connections and tables. */
    void init() throws SQLException;

    /** Load a player from the database by UUID. Return empty if not found. */
    Optional<PlayerData> loadPlayer(UUID uuid) throws SQLException;

    /** Save or update a player into the database. */
    void savePlayer(PlayerData player) throws SQLException;

    @Override
    void close() throws IOException;

}
