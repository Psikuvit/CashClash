package me.psikuvit.cashClash;

import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.command.CommandHandler;
import me.psikuvit.cashClash.listener.*;
import me.psikuvit.cashClash.manager.GameManager;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class CashClashPlugin extends JavaPlugin {

    private static CashClashPlugin instance;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        ArenaManager.getInstance().initializeArenas();

        registerEvents();
        registerCommands();

        getLogger().info("Cash Clash has been enabled!");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("Initialized 5 arenas for players to join");
    }

    @Override
    public void onDisable() {
        // Cleanup all active sessions
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
                new GUIListener(),
                new CustomArmorListener(),
                new ShopListener()
        };

        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    private void registerCommands() {
        getServer().getCommandMap().register("cashclash", new CommandHandler());

    }
}
