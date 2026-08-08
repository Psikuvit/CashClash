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

    // Composition root: every manager lives here as a field, constructed once in onEnable() in
    // dependency order, and exposed through the getters below - this is the single place that
    // resolves a manager instance; every other class receives it via constructor injection or
    // one of these getters instead of holding its own static instance.
    private ConfigManager configManager;
    private MessagesConfig messagesConfig;
    private SequencesConfig sequencesConfig;
    private ItemsConfig itemsConfig;
    private ShopConfig shopConfig;
    private PlayerDataManager playerDataManager;
    private GameManager gameManager;
    private GamemodeManager gamemodeManager;
    private TabListManager tabListManager;
    private PartyManager partyManager;
    private ItemFactory itemFactory;
    private CooldownManager cooldownManager;
    private ArenaManager arenaManager;
    private ChatManager chatManager;
    private RejoinManager rejoinManager;
    private LeaderboardManager leaderboardManager;
    private AfkManager afkManager;
    private MannequinManager mannequinManager;
    private LobbyManager lobbyManager;
    private ScoreboardManager scoreboardManager;
    private ShopService shopService;
    private TransferInputListener transferInputListener;
    private ShopManager shopManager;
    private LayoutManager layoutManager;
    private CustomArmorManager customArmorManager;
    private MythicItemManager mythicItemManager;
    private CustomItemManager customItemManager;
    private WeaponItemManager weaponItemManager;

    @Override
    public void onEnable() {
        instance = this;

        try {
            // Tier 0: no dependencies on any other manager.
            configManager = new ConfigManager();
            messagesConfig = new MessagesConfig();
            sequencesConfig = new SequencesConfig();
            itemsConfig = new ItemsConfig();
            shopConfig = new ShopConfig();
            getLogger().info("Configuration files loaded successfully");

            playerDataManager = PlayerDataManager.create(this);
            getLogger().info("Player data storage initialized");

            gameManager = new GameManager(this);
            gamemodeManager = new GamemodeManager();
            tabListManager = new TabListManager();
            partyManager = new PartyManager();
            itemFactory = new ItemFactory();
            cooldownManager = new CooldownManager();

            // Tier 1: depend only on tier 0 managers.
            arenaManager = new ArenaManager(gameManager, configManager);
            chatManager = new ChatManager(partyManager, gameManager);
            rejoinManager = new RejoinManager(configManager, gameManager);
            leaderboardManager = new LeaderboardManager(configManager, playerDataManager);
            afkManager = new AfkManager(configManager, gameManager, messagesConfig);
            mannequinManager = new MannequinManager(configManager);
            lobbyManager = new LobbyManager(itemsConfig);
            scoreboardManager = new ScoreboardManager(gameManager, tabListManager);
            shopService = new ShopService(gameManager, itemFactory);
            transferInputListener = new TransferInputListener(gameManager);

            // Tier 2: depend on tier 1 managers.
            shopManager = new ShopManager(arenaManager, gameManager);
            layoutManager = new LayoutManager(playerDataManager, lobbyManager);
            customArmorManager = new CustomArmorManager(cooldownManager, itemsConfig);
            mythicItemManager = new MythicItemManager(itemsConfig, cooldownManager, itemFactory);

            // Tier 3: depend on tier 2 managers.
            customItemManager = new CustomItemManager(cooldownManager, itemsConfig, customArmorManager);
            weaponItemManager = new WeaponItemManager(cooldownManager, itemsConfig, customArmorManager);

            // Step 3: Initialize arena system
            arenaManager.initializeArenas();
            getLogger().info("Arena system initialized (5 arenas available)");

            // Step 4: Register events and commands
            registerEvents();
            registerCommands();

            // Step 4.5: Start the periodic AFK lobby kicker
            afkTask = SchedulerUtils.runTaskTimer(afkManager::checkAndKick, 20L * 30, 20L * 30);

            // Step 4.6: Start the async leaderboard worker
            leaderboardManager.start();

            // Step 5: Spawn persistent mannequins
            mannequinManager.spawnAll();
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
        managers.add(leaderboardManager);
        managers.add(gameManager);
        managers.add(rejoinManager);
        managers.add(gamemodeManager);
        managers.add(scoreboardManager);
        managers.add(cooldownManager);
        managers.add(mannequinManager);
        managers.add(partyManager);
        managers.add(chatManager);
        managers.add(playerDataManager);

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

    public ConfigManager getConfigManager() { return configManager; }
    public MessagesConfig getMessagesConfig() { return messagesConfig; }
    public SequencesConfig getSequencesConfig() { return sequencesConfig; }
    public ItemsConfig getItemsConfig() { return itemsConfig; }
    public ShopConfig getShopConfig() { return shopConfig; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public GameManager getGameManager() { return gameManager; }
    public GamemodeManager getGamemodeManager() { return gamemodeManager; }
    public TabListManager getTabListManager() { return tabListManager; }
    public PartyManager getPartyManager() { return partyManager; }
    public ItemFactory getItemFactory() { return itemFactory; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public ChatManager getChatManager() { return chatManager; }
    public RejoinManager getRejoinManager() { return rejoinManager; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public AfkManager getAfkManager() { return afkManager; }
    public MannequinManager getMannequinManager() { return mannequinManager; }
    public LobbyManager getLobbyManager() { return lobbyManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public ShopService getShopService() { return shopService; }
    public TransferInputListener getTransferInputListener() { return transferInputListener; }
    public ShopManager getShopManager() { return shopManager; }
    public LayoutManager getLayoutManager() { return layoutManager; }
    public CustomArmorManager getCustomArmorManager() { return customArmorManager; }
    public MythicItemManager getMythicItemManager() { return mythicItemManager; }
    public CustomItemManager getCustomItemManager() { return customItemManager; }
    public WeaponItemManager getWeaponItemManager() { return weaponItemManager; }

    private void registerEvents() {
        Listener[] listeners = {
                new GuiListener(),
                new BlockListener(this),
                new DamageListener(this),
                new InteractListener(this),
                new MoveListener(this),
                new GameListener(this),
                new HungerListener(),
                new PlayerConnectionListener(this),
                new LobbyListener(this),
                new AfkListener(),
                new ArenaNPCListener(),
                new ChatListener(),
                new RuneListener(this),
                transferInputListener
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
