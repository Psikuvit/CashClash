package me.psikuvit.cashClash;

import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.command.CommandHandler;
import me.psikuvit.cashClash.listener.*;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.manager.PlayerDataManager;
import me.psikuvit.cashClash.manager.ScoreboardManager;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class CashClashPlugin extends JavaPlugin {

    private static CashClashPlugin instance;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize player persistence
        try {
            PlayerDataManager.init(this);
            getLogger().info("PlayerDataManager initialized");
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize player storage: " + e.getMessage());
        }

        ArenaManager.getInstance().initializeArenas();

        registerEvents();
        registerCommands();

        getLogger().info("Cash Clash has been enabled!");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("Initialized 5 arenas for players to join");
    }

    @Override
    public void onDisable() {
        // Shutdown scoreboard system first
        ScoreboardManager.getInstance().shutdown();

        PlayerDataManager.getInstance().shutdown();
        GameManager.getInstance().shutdown();

        getLogger().info("Cash Clash has been disabled!");
    }

    public static CashClashPlugin getInstance() {
        return instance;
    }

    private void registerEvents() {
        Listener[] listeners = {
                new DeathListener(),
                new DamageListener(),
                new BlockListener(),
                new PlayerConnectionListener(),
                new LobbyProtectionListener(),
                new ArenaGuiListener(),
                new ShopGuiListener(),
                new CustomArmorListener(),
                new ShopListener(),
                new SupplyDropListener()
        };

        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    private void registerCommands() {
        getServer().getCommandMap().register("cashclash", new CommandHandler());

    }
}
