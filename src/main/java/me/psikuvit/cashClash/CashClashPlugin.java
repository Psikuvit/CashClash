package me.psikuvit.cashClash;

import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.command.CommandHandler;
import me.psikuvit.cashClash.gui.builder.GuiListener;
import me.psikuvit.cashClash.listener.PlayerConnectionListener;
import me.psikuvit.cashClash.listener.game.BlockProtectionListener;
import me.psikuvit.cashClash.listener.game.DamageListener;
import me.psikuvit.cashClash.listener.game.DeathListener;
import me.psikuvit.cashClash.listener.game.EnderPearlListener;
import me.psikuvit.cashClash.listener.game.InteractListener;
import me.psikuvit.cashClash.listener.game.ShopListener;
import me.psikuvit.cashClash.listener.game.ShopPhaseProtectionListener;
import me.psikuvit.cashClash.listener.game.SupplyDropListener;
import me.psikuvit.cashClash.listener.items.ConsumableListener;
import me.psikuvit.cashClash.listener.items.CustomArmorListener;
import me.psikuvit.cashClash.listener.items.CustomItemListener;
import me.psikuvit.cashClash.listener.items.MythicItemListener;
import me.psikuvit.cashClash.listener.lobby.LobbyProtectionListener;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.manager.player.ScoreboardManager;
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
                new GuiListener(),
                new BlockProtectionListener(),
                new DamageListener(),
                new DeathListener(),
                new EnderPearlListener(),
                new InteractListener(),
                new ShopListener(),
                new SupplyDropListener(),
                new ConsumableListener(),
                new CustomArmorListener(),
                new CustomItemListener(),
                new MythicItemListener(),
                new LobbyProtectionListener(),
                new PlayerConnectionListener(),
                new ShopPhaseProtectionListener(),
        };

        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    private void registerCommands() {
        getServer().getCommandMap().register("cashclash", new CommandHandler());

    }
}
