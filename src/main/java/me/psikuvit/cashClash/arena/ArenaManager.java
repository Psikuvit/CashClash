package me.psikuvit.cashClash.arena;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.util.LocationUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages exactly 5 fixed arenas that players can browse and join, and template worlds used by arenas
 */
public class ArenaManager {
    private static ArenaManager instance;
    private final Map<Integer, Arena> arenas; // Arena number (1-5) -> Arena
    private final Map<Integer, GameState> arenaStates; // Track the state of each arena
    private final Map<Integer, Integer> arenaPlayerCounts; // Track player count per arena

    // Template registry id -> TemplateWorld
    private final Map<String, TemplateWorld> templates;

    // Fixed the number of arenas
    private static final int MAX_ARENAS = 5;

    // Server-wide lobby spawn (optional)
    private Location serverLobbySpawn;

    private ArenaManager() {
        this.arenas = new LinkedHashMap<>();
        this.arenaStates = new HashMap<>();
        this.arenaPlayerCounts = new HashMap<>();
        this.templates = new HashMap<>();

        // Load any saved template and arena configurations
        loadTemplates();
        loadArenas();
        loadServerLobby();
    }

    public static ArenaManager getInstance() {
        if (instance == null) {
            instance = new ArenaManager();
        }
        return instance;
    }

    /**
     * Initialize arena objects (no templates assigned yet). Admin must configure template worlds.
     */
    public void initializeArenas() {
        // Ensure we have exactly MAX_ARENAS entries without overwriting ones loaded from disk
        for (int i = 1; i <= MAX_ARENAS; i++) {
            String arenaName = "Arena" + i;
            if (!arenas.containsKey(i)) {
                // create default empty arena placeholder only if missing
                try {
                    Arena arena = new Arena(arenaName, null);
                    arenas.put(i, arena);
                    CashClashPlugin.getInstance().getLogger().info("Created placeholder " + arenaName);
                } catch (Exception e) {
                    CashClashPlugin.getInstance().getLogger().severe("Failed to create placeholder " + arenaName + ": " + e.getMessage());
                }
            } else {
                CashClashPlugin.getInstance().getLogger().info("Arena slot " + i + " already loaded: " + arenas.get(i).getName());
            }

            arenaStates.putIfAbsent(i, GameState.WAITING);
            arenaPlayerCounts.putIfAbsent(i, 0);
        }

        CashClashPlugin.getInstance().getLogger().info("Arenas ensured (loaded or created placeholders). Configure templates and assign them via admin commands if needed.");
    }

    /**
     * Load templates from config (templates.<id>)
     */
    private void loadTemplates() {
        FileConfiguration cfg = config();
        if (!cfg.contains("templates")) return;

        var section = cfg.getConfigurationSection("templates");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String worldName = section.getString(id + ".world");
            if (worldName == null) continue;

            World w = Bukkit.getWorld(worldName);
            if (w == null) {

                File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
                if (worldFolder.exists() && worldFolder.isDirectory()) {
                    try { w = Bukkit.createWorld(new WorldCreator(worldName)); }
                    catch (Exception ex) { CashClashPlugin.getInstance().getLogger().warning("Failed to load world for template " + id + ": " + ex.getMessage()); }
                }
            }

            if (w == null) continue;
            TemplateWorld tpl = new TemplateWorld(id, w);

            if (cfg.contains("templates." + id + ".lobby")) {
                tpl.setSpawn(LocationUtils.deserializeLocation(cfg.getConfigurationSection("templates." + id + ".lobby")));
            }

            // load spectator
            if (cfg.contains("templates." + id + ".spectator")) {
                tpl.setSpectatorSpawn(LocationUtils.deserializeLocation(cfg.getConfigurationSection("templates." + id + ".spectator")));
            }

            // team spawns
            for (int i = 0; i < 3; i++) {
                String t1 = "templates." + id + ".team1." + i;
                if (cfg.contains(t1)) tpl.setTeam1Spawn(i, LocationUtils.deserializeLocation(cfg.getConfigurationSection(t1)));
                String t2 = "templates." + id + ".team2." + i;
                if (cfg.contains(t2)) tpl.setTeam2Spawn(i, LocationUtils.deserializeLocation(cfg.getConfigurationSection(t2)));
            }

            // shop spawns
            if (cfg.contains("templates." + id + ".shop.team1")) tpl.setTeam1ShopSpawn(LocationUtils.deserializeLocation(cfg.getConfigurationSection("templates." + id + ".shop.team1")));
            if (cfg.contains("templates." + id + ".shop.team2")) tpl.setTeam2ShopSpawn(LocationUtils.deserializeLocation(cfg.getConfigurationSection("templates." + id + ".shop.team2")));

            templates.put(id, tpl);
            CashClashPlugin.getInstance().getLogger().info("Loaded template: " + id + " -> " + w.getName());
        }
    }

    /**
     * Persist a template entry (world + lobby spawn) to config
     */
    public void saveTemplate(String id) {
        TemplateWorld tpl = templates.get(id);
        if (tpl == null) return;
        FileConfiguration cfg = config();

        String base = "templates." + id;
        cfg.set(base + ".world", tpl.getWorld() != null ? tpl.getWorld().getName() : null);

        if (tpl.getLobbySpawn() != null) LocationUtils.serializeLocation(cfg, base + ".lobby", tpl.getLobbySpawn());
        else cfg.set(base + ".lobby", null);

        if (tpl.getSpectatorSpawn() != null) LocationUtils.serializeLocation(cfg, base + ".spectator", tpl.getSpectatorSpawn());
        else cfg.set(base + ".spectator", null);

        for (int i = 0; i < 3; i++) {
            Location t1 = tpl.getTeam1Spawn(i);
            if (t1 != null) LocationUtils.serializeLocation(cfg, base + ".team1." + i, t1);
            else cfg.set(base + ".team1." + i, null);

            Location t2 = tpl.getTeam2Spawn(i);
            if (t2 != null) LocationUtils.serializeLocation(cfg, base + ".team2." + i, t2);
            else cfg.set(base + ".team2." + i, null);
        }

        if (tpl.getTeam1ShopSpawn() != null) LocationUtils.serializeLocation(cfg, base + ".shop.team1", tpl.getTeam1ShopSpawn());
        else cfg.set(base + ".shop.team1", null);

        if (tpl.getTeam2ShopSpawn() != null) LocationUtils.serializeLocation(cfg, base + ".shop.team2", tpl.getTeam2ShopSpawn());
        else cfg.set(base + ".shop.team2", null);

        CashClashPlugin.getInstance().saveConfig();
        CashClashPlugin.getInstance().getLogger().info("Saved template: " + id);
    }

    // Template management
    public boolean registerTemplate(String id, String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (worldFolder.exists() && worldFolder.isDirectory()) {
                try {
                    w = Bukkit.createWorld(new WorldCreator(worldName));
                } catch (Exception ex) {
                    CashClashPlugin.getInstance().getLogger().severe("Failed to load world for template '" + id + "': " + ex.getMessage());
                    return false;
                }
            } else {
                return false;
            }
        }
        TemplateWorld tpl = new TemplateWorld(id, w);
        templates.put(id, tpl);
        // persist the template mapping
        saveTemplate(id);
        return true;
    }

    public TemplateWorld getTemplate(String id) { return templates.get(id); }

    public Map<String, TemplateWorld> getAllTemplates() { return new HashMap<>(templates); }

    /**
     * Get arena by number (1-5)
     */
    public Arena getArena(int arenaNumber) {
        return arenas.get(arenaNumber);
    }

    /**
     * Get all arenas
     */
    public Map<Integer, Arena> getAllArenas() {
        return new HashMap<>(arenas);
    }

    /**
     * Get arena state
     */
    public GameState getArenaState(int arenaNumber) {
        return arenaStates.getOrDefault(arenaNumber, GameState.WAITING);
    }

    /**
     * Set arena state
     */
    public void setArenaState(int arenaNumber, GameState state) {
        arenaStates.put(arenaNumber, state);
    }

    /**
     * Get player count in arena
     */
    public int getArenaPlayerCount(int arenaNumber) {
        return arenaPlayerCounts.getOrDefault(arenaNumber, 0);
    }

    /**
     * Set player count in arena
     */
    public void setArenaPlayerCount(int arenaNumber, int count) {
        arenaPlayerCounts.put(arenaNumber, count);
    }

    /**
     * Increment player count
     */
    public void incrementPlayerCount(int arenaNumber) {
        arenaPlayerCounts.merge(arenaNumber, 1, Integer::sum);
    }

    /**
     * Decrement player count in arena
     */
    public void decrementPlayerCount(int arenaNumber) {
        arenaPlayerCounts.computeIfPresent(arenaNumber, (k, v) -> Math.max(0, v - 1));
    }

    /**
     * Check if an arena is joinable
     */
    public boolean isArenaJoinable(int arenaNumber) {
        GameState state = getArenaState(arenaNumber);
        int playerCount = getArenaPlayerCount(arenaNumber);
        Arena arena = getArena(arenaNumber);

        int maxPlayers = ConfigManager.getInstance().getMaxPlayers();

        return arena != null
            && arena.isReady()
            && state == GameState.WAITING
            && playerCount < maxPlayers;
    }

    /**
     * Find a configured arena (used as a template)
     */
    private Arena findConfiguredArena() {
        return arenas.values().stream()
            .filter(Arena::isReady)
            .findFirst()
            .orElse(null);
    }

    public Arena getArena(String name) {
        return arenas.values().stream()
            .filter(a -> a.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    private FileConfiguration config() {
        return CashClashPlugin.getInstance().getConfig();
    }

    /**
     * Loads' arena data from the plugin config (if present). Ensures we have 1.MAX_ARENAS entries.
     */
    private void loadArenas() {
        // Load per-arena YAML files from plugin-data/arenas/
        File dir = new File(CashClashPlugin.getInstance().getDataFolder(), "arenas");
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                CashClashPlugin.getInstance().getLogger().warning("Could not create arenas directory: " + dir.getAbsolutePath());
            }
        }

        for (int i = 1; i <= MAX_ARENAS; i++) {
            File f = new File(dir, "arena" + i + ".yml");
            if (f.exists()) {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                String name = cfg.getString("name", "Arena" + i);
                String templateId = cfg.getString("template", null);
                Arena arena = new Arena(name, templateId);

                // Note: spawn and shop positions are stored on templates now.
                arenas.put(i, arena);
                arena.setConfiguredFromFile(true);
                CashClashPlugin.getInstance().getLogger().info("Loaded arena file: " + f.getName() + " -> " + name);
            } else {
                // create default empty arena placeholder
                String arenaName = "Arena" + i;
                Arena arena = new Arena(arenaName, null);
                arenas.put(i, arena);
            }

            arenaStates.put(i, GameState.WAITING);
            arenaPlayerCounts.put(i, 0);
        }
    }

    /**
     * Save a single arena configuration back to the plugin config and persist to disk.
     */
    public void saveArena(int arenaNumber) {
        Arena arena = arenas.get(arenaNumber);
        if (arena == null) return;

        File dir = new File(CashClashPlugin.getInstance().getDataFolder(), "arenas");
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                CashClashPlugin.getInstance().getLogger().warning("Could not create arenas directory: " + dir.getAbsolutePath());
            }
        }
        File f = new File(dir, "arena" + arenaNumber + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();

        cfg.set("name", arena.getName());
        cfg.set("template", arena.getTemplateId());


        try {
            cfg.save(f);
            CashClashPlugin.getInstance().getLogger().info("Saved arena file: " + f.getName() + " -> " + arena.getName());
        } catch (IOException e) {
            CashClashPlugin.getInstance().getLogger().warning("Failed to save arena file: " + f.getAbsolutePath() + " - " + e.getMessage());
        }
    }

    private Location deserializeLocation(org.bukkit.configuration.ConfigurationSection section) {
        return LocationUtils.deserializeLocation(section);
    }

    /**
     * Get the arena number for a given arena instance (1.MAX_ARENAS)
     */
    public Integer getArenaNumber(Arena arena) {
        return arenas.entrySet().stream()
            .filter(e -> e.getValue().equals(arena))
            .map(Map.Entry::getKey)
            .findFirst().orElse(null);
    }

    // --- Server lobby spawn handling ---
    private void loadServerLobby() {
        FileConfiguration cfg = config();
        if (!cfg.contains("lobby")) return;

        var section = cfg.getConfigurationSection("lobby");
        if (section == null) return;

        Location loc = LocationUtils.deserializeLocation(section);
        if (loc != null && loc.getWorld() != null) {
            this.serverLobbySpawn = loc;
            CashClashPlugin.getInstance().getLogger().info("Loaded server lobby spawn: " + loc.getWorld().getName());
        }
    }

    public void saveServerLobby() {
        FileConfiguration cfg = config();

        if (serverLobbySpawn != null) LocationUtils.serializeLocation(cfg, "lobby", serverLobbySpawn);
        else cfg.set("lobby", null);

        CashClashPlugin.getInstance().saveConfig();
        CashClashPlugin.getInstance().getLogger().info("Saved server lobby spawn to config");
    }

    public Location getServerLobbySpawn() {
        return serverLobbySpawn;
    }

    public void setServerLobbySpawn(Location loc) {
        this.serverLobbySpawn = loc;
        saveServerLobby();
    }
}
