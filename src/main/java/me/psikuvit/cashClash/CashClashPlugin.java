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
import me.psikuvit.cashClash.manager.Shutdownable;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.game.GamemodeManager;
import me.psikuvit.cashClash.manager.game.RejoinManager;
import me.psikuvit.cashClash.manager.items.armor.CustomArmorManager;
import me.psikuvit.cashClash.manager.items.custom.CustomItemManager;
import me.psikuvit.cashClash.manager.items.mythic.MythicItemManager;
import me.psikuvit.cashClash.manager.items.weapon.WeaponItemManager;
import me.psikuvit.cashClash.manager.lobby.LayoutManager;
import me.psikuvit.cashClash.manager.lobby.LobbyManager;
import me.psikuvit.cashClash.manager.lobby.MannequinManager;
import me.psikuvit.cashClash.manager.player.AfkManager;
import me.psikuvit.cashClash.manager.player.LeaderboardManager;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.manager.player.ScoreboardManager;
import me.psikuvit.cashClash.manager.player.TabListManager;
import me.psikuvit.cashClash.manager.shop.ShopManager;
import me.psikuvit.cashClash.party.PartyManager;
import me.psikuvit.cashClash.shop.ShopService;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.items.ItemFactory;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;

public final class CashClashPlugin extends JavaPlugin {

    private static CashClashPlugin instance;
    private boolean initialized = false;
    private BukkitTask afkTask;

    @Override
    public void onEnable() {
        instance = this;

        try {
            // Composition root: every manager is constructed here, once, in dependency order,
            // instead of lazily self-constructing on first getInstance() call. Each constructor
            // takes the managers it depends on as explicit parameters; getInstance() on each
            // class remains for the many call sites not worth threading a constructor reference
            // through, but it now just exposes what was built here rather than deciding when
            // construction happens.

            // Tier 0: no dependencies on any other manager.
            ConfigManager configManager = new ConfigManager();
            MessagesConfig messagesConfig = new MessagesConfig();
            new SequencesConfig();
            ItemsConfig itemsConfig = new ItemsConfig();
            new ShopConfig();
            getLogger().info("Configuration files loaded successfully");

            PlayerDataManager.init(this);
            getLogger().info("Player data storage initialized");

            GameManager gameManager = new GameManager();
            new GamemodeManager();
            TabListManager tabListManager = new TabListManager();
            PartyManager partyManager = new PartyManager();
            ItemFactory itemFactory = new ItemFactory();
            new CooldownManager();

            // Tier 1: depend only on tier 0 managers.
            ArenaManager arenaManager = new ArenaManager(gameManager, configManager);
            new ChatManager(partyManager, gameManager);
            new RejoinManager(configManager, gameManager);
            new LeaderboardManager(configManager, PlayerDataManager.getInstance());
            new AfkManager(configManager, gameManager, messagesConfig);
            new MannequinManager(configManager);
            LobbyManager lobbyManager = new LobbyManager(itemsConfig);
            new ScoreboardManager(gameManager, tabListManager);
            new ShopService(gameManager, itemFactory);
            new TransferInputListener(gameManager);

            // Tier 2: depend on tier 1 managers.
            new ShopManager(arenaManager, gameManager);
            new LayoutManager(PlayerDataManager.getInstance(), lobbyManager);
            CustomArmorManager customArmorManager = new CustomArmorManager(CooldownManager.getInstance(), itemsConfig);
            new MythicItemManager(itemsConfig, CooldownManager.getInstance(), itemFactory);

            // Tier 3: depend on tier 2 managers.
            new CustomItemManager(CooldownManager.getInstance(), itemsConfig, customArmorManager);
            new WeaponItemManager(CooldownManager.getInstance(), itemsConfig, customArmorManager);

            // Step 3: Initialize arena system
            arenaManager.initializeArenas();
            getLogger().info("Arena system initialized (5 arenas available)");

            // Step 4: Register events and commands
            registerEvents();
            registerCommands();

            // Step 4.5: Start the periodic AFK lobby kicker
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
            getLogger().info("Debug mode: " + (configManager.isDebugEnabled() ? "ENABLED" : "disabled"));
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

        // Order matters here (game sessions torn down before the systems they reference,
        // player data saved last) - a LinkedHashSet keeps that order instead of leaving it
        // to hash iteration.
        Set<Shutdownable> managers = new LinkedHashSet<>();
        managers.add(LeaderboardManager.getInstance());
        managers.add(GameManager.getInstance());
        managers.add(RejoinManager.getInstance());
        managers.add(GamemodeManager.getInstance());
        managers.add(ScoreboardManager.getInstance());
        managers.add(CooldownManager.getInstance());
        managers.add(MannequinManager.getInstance());
        managers.add(PartyManager.getInstance());
        managers.add(ChatManager.getInstance());
        managers.add(PlayerDataManager.getInstance());

        for (Shutdownable manager : managers) {
            String name = manager.getClass().getSimpleName();
            shutdownStep("shutting down " + name, name + " shut down", manager::shutdown);
        }

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
