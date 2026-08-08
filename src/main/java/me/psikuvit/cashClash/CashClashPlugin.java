package me.psikuvit.cashClash;

import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.chat.ChatManager;
import me.psikuvit.cashClash.command.CommandHandler;
import me.psikuvit.cashClash.command.PartyCommandHandler;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.config.MessagesConfig;
import me.psikuvit.cashClash.config.SequencesConfig;
import me.psikuvit.cashClash.config.ShopConfig;
import me.psikuvit.cashClash.gui.builder.GuiListener;
import me.psikuvit.cashClash.listener.BlockListener;
import me.psikuvit.cashClash.listener.ChatListener;
import me.psikuvit.cashClash.listener.DamageListener;
import me.psikuvit.cashClash.listener.GameListener;
import me.psikuvit.cashClash.listener.HungerListener;
import me.psikuvit.cashClash.listener.InteractListener;
import me.psikuvit.cashClash.listener.MoveListener;
import me.psikuvit.cashClash.listener.PlayerConnectionListener;
import me.psikuvit.cashClash.listener.RuneListener;
import me.psikuvit.cashClash.listener.TransferInputListener;
import me.psikuvit.cashClash.listener.lobby.ArenaNPCListener;
import me.psikuvit.cashClash.listener.lobby.AfkListener;
import me.psikuvit.cashClash.listener.lobby.LobbyListener;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.game.GamemodeManager;
import me.psikuvit.cashClash.manager.game.RejoinManager;
import me.psikuvit.cashClash.manager.lobby.MannequinManager;
import me.psikuvit.cashClash.manager.player.AfkManager;
import me.psikuvit.cashClash.manager.player.LeaderboardManager;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.manager.player.ScoreboardManager;
import me.psikuvit.cashClash.party.PartyManager;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.logging.Level;

public final class CashClashPlugin extends JavaPlugin {

    private static CashClashPlugin instance;
    private boolean initialized = false;
    private BukkitTask afkTask;

    @Override
    public void onEnable() {
        instance = this;

        try {
            ConfigManager.getInstance();
            MessagesConfig.getInstance();
            SequencesConfig.getInstance();
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

            // Step 4.5: Start the periodic AFK lobby kicker
            AfkManager.getInstance();
            afkTask = SchedulerUtils.runTaskTimer(AfkManager.getInstance()::checkAndKick, 20L * 30, 20L * 30);

            // Step 4.6: Start the async leaderboard worker
            LeaderboardManager.getInstance().start();

            // Step 5: Spawn persistent mannequins
            MannequinManager.getInstance().spawnAll();
            getLogger().info("Mannequin NPCs spawned");

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

        shutdownStep("cancelling AFK task", null, () -> {
            if (afkTask != null) {
                afkTask.cancel();
                afkTask = null;
            }
        });
        shutdownStep("stopping LeaderboardManager", null, () -> LeaderboardManager.getInstance().stop());
        shutdownStep("shutting down GameManager", "Game sessions terminated", () -> GameManager.getInstance().shutdown());
        shutdownStep("shutting down RejoinManager", "Rejoin manager shut down", () -> RejoinManager.getInstance().shutdown());
        shutdownStep("shutting down GamemodeManager", "Gamemode manager shut down", () -> GamemodeManager.getInstance().shutdown());
        shutdownStep("shutting down ScoreboardManager", "Scoreboards cleared", () -> ScoreboardManager.getInstance().shutdown());
        shutdownStep("shutting down PlayerDataManager", "Player data saved", () -> PlayerDataManager.getInstance().shutdown());
        shutdownStep("clearing cooldowns", null, () -> CooldownManager.getInstance().clearAll());
        shutdownStep("shutting down MannequinManager", "Mannequins removed", () -> MannequinManager.getInstance().shutdown());
        shutdownStep("shutting down PartyManager", "Party system shut down", () -> PartyManager.getInstance().shutdown());
        shutdownStep("shutting down ChatManager", "Chat manager shut down", () -> ChatManager.getInstance().shutdown());

        getLogger().info("Cash Clash has been disabled!");
    }

    /**
     * Runs one shutdown step in isolation: a failure is logged as a warning without aborting the
     * remaining steps. {@code successMessage} is logged at info level after {@code action} runs
     * without throwing; pass null for steps that don't need one (e.g. the AFK task cancel, which
     * has no manager to name).
     */
    private void shutdownStep(String errorContext, String successMessage, Runnable action) {
        try {
            action.run();
            if (successMessage != null) {
                getLogger().info(successMessage);
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error " + errorContext, e);
        }
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
                new HungerListener(),
                new PlayerConnectionListener(),
                new LobbyListener(),
                new AfkListener(),
                new ArenaNPCListener(),
                new ChatListener(),
                new RuneListener(),
                TransferInputListener.getInstance()
        };

        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
        getLogger().info("Registered " + listeners.length + " event listeners");
    }

    private void registerCommands() {
        getServer().getCommandMap().register("cashclash", new CommandHandler());
        getServer().getCommandMap().register("cashclash", new PartyCommandHandler());
        getLogger().info("Commands registered");
    }
}
