package me.psikuvit.cashClash;

import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.command.CommandHandler;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.config.ShopConfig;
import me.psikuvit.cashClash.gui.builder.GuiListener;
import me.psikuvit.cashClash.listener.BlockListener;
import me.psikuvit.cashClash.listener.DamageListener;
import me.psikuvit.cashClash.listener.GameListener;
import me.psikuvit.cashClash.listener.InteractListener;
import me.psikuvit.cashClash.listener.MoveListener;
import me.psikuvit.cashClash.listener.PlayerConnectionListener;
import me.psikuvit.cashClash.listener.lobby.LobbyListener;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.manager.player.ScoreboardManager;
import me.psikuvit.cashClash.util.CooldownManager;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

public final class CashClashPlugin extends JavaPlugin {

    private static CashClashPlugin instance;
    private boolean initialized = false;

    @Override
    public void onEnable() {
        instance = this;

        try {
            ConfigManager.getInstance();
            ItemsConfig.getInstance();
            ShopConfig.getInstance();
            getLogger().info("Configuration files loaded successfully");

            // Step 2: Initialize player persistence (critical)
            PlayerDataManager.init(this);
            getLogger().info("Player data storage initialized");

            // Step 3: Initialize arena system
            ArenaManager.getInstance().initializeArenas();
            getLogger().info("Arena system initialized (5 arenas available)");

            // Step 4: Register events and commands
            registerEvents();
            registerCommands();

            // Mark as successfully initialized
            initialized = true;

            getLogger().info("=================================");
            getLogger().info("Cash Clash v" + getPluginMeta().getVersion() + " enabled!");
            getLogger().info("Debug mode: " + (ConfigManager.getInstance().isDebugEnabled() ? "ENABLED" : "disabled"));
            getLogger().info("=================================");

        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "CRITICAL: Failed to initialize player storage!", e);
            getLogger().severe("Plugin will be disabled due to database initialization failure.");
            getServer().getPluginManager().disablePlugin(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "CRITICAL: Unexpected error during plugin startup!", e);
            getLogger().severe("Plugin will be disabled due to initialization failure.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (!initialized) {
            getLogger().warning("Plugin was not fully initialized, skipping shutdown procedures.");
            return;
        }

        getLogger().info("Shutting down Cash Clash...");

        // Shutdown in reverse initialization order
        try {
            // Step 1: Stop all active games
            if (GameManager.getInstance() != null) {
                GameManager.getInstance().shutdown();
                getLogger().info("Game sessions terminated");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error shutting down GameManager", e);
        }

        try {
            // Step 2: Shutdown scoreboards
            if (ScoreboardManager.getInstance() != null) {
                ScoreboardManager.getInstance().shutdown();
                getLogger().info("Scoreboards cleared");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error shutting down ScoreboardManager", e);
        }

        try {
            // Step 3: Save and close player data
            if (PlayerDataManager.getInstance() != null) {
                PlayerDataManager.getInstance().shutdown();
                getLogger().info("Player data saved");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error shutting down PlayerDataManager", e);
        }

        try {
            // Step 4: Clear cooldowns
            if (CooldownManager.getInstance() != null) {
                CooldownManager.getInstance().clearAll();
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error clearing cooldowns", e);
        }

        getLogger().info("Cash Clash has been disabled!");
    }

    public static CashClashPlugin getInstance() {
        return instance;
    }

    private void registerEvents() {
        Listener[] listeners = {
                new GuiListener(),
                new BlockListener(),
                new DamageListener(),
                new InteractListener(),
                new MoveListener(),
                new GameListener(),
                new PlayerConnectionListener(),
                new LobbyListener(),
        };

        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
        getLogger().info("Registered " + listeners.length + " event listeners");
    }

    private void registerCommands() {
        getServer().getCommandMap().register("cashclash", new CommandHandler());
        getLogger().info("Commands registered");
    }
}
