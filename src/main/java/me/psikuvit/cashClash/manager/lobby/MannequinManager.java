package me.psikuvit.cashClash.manager.lobby;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages persistent mannequin NPCs that are saved/loaded with the server.
 * Mannequins are stored in mannequins.yml and respawned on server start.
 */
public class MannequinManager {

    private static MannequinManager instance;

    private final CashClashPlugin plugin;
    private final File dataFile;
    private final List<Villager> spawnedMannequins = new ArrayList<>();
    private YamlConfiguration data;

    private MannequinManager() {
        this.plugin = CashClashPlugin.getInstance();
        this.dataFile = new File(plugin.getDataFolder(), "mannequins.yml");
        loadData();
    }

    public static MannequinManager getInstance() {
        if (instance == null) {
            instance = new MannequinManager();
        }
        return instance;
    }

    /**
     * Load mannequin data from file.
     */
    private void loadData() {
        if (!dataFile.exists()) {
            data = new YamlConfiguration();
            saveData();
        } else {
            data = YamlConfiguration.loadConfiguration(dataFile);
        }
    }

    /**
     * Save mannequin data to file.
     */
    public void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save mannequins.yml", e);
        }
    }

    /**
     * Spawn all saved mannequins. Called on server start.
     */
    public void spawnAll() {
        removeAllSpawned();

        ConfigurationSection mannequins = data.getConfigurationSection("mannequins");
        if (mannequins == null) {
            plugin.getLogger().info("[MannequinManager] No mannequins to spawn");
            return;
        }

        int count = 0;
        for (String id : mannequins.getKeys(false)) {
            ConfigurationSection section = mannequins.getConfigurationSection(id);
            if (section == null) continue;

            String type = section.getString("type", "arena");
            String worldName = section.getString("world");
            double x = section.getDouble("x");
            double y = section.getDouble("y");
            double z = section.getDouble("z");

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("[MannequinManager] World '" + worldName + "' not found for mannequin " + id);
                continue;
            }

            Location loc = LocationUtils.adjustLocationToWorld(new Location(world, x, y, z), world);

            if ("arena".equals(type)) {
                spawnArenaMannequin(loc, id);
                count++;
            }
        }

        plugin.getLogger().info("[MannequinManager] Spawned " + count + " mannequin(s)");
    }

    /**
     * Remove all spawned mannequins from the world.
     */
    public void removeAllSpawned() {
        for (Villager mannequin : spawnedMannequins) {
            if (mannequin != null && !mannequin.isDead()) {
                mannequin.remove();
            }
        }
        spawnedMannequins.clear();

        // Also remove any orphaned mannequins with our key
        for (World world : Bukkit.getWorlds()) {
            for (Villager mannequin : world.getEntitiesByClass(Villager.class)) {
                if (PDCDetection.isArenaNPC(mannequin)) {
                    mannequin.remove();
                }
            }
        }
    }

    /**
     * Shutdown - remove all mannequins (they will be respawned on next start).
     */
    public void shutdown() {
        removeAllSpawned();
        plugin.getLogger().info("[MannequinManager] Removed all mannequins for shutdown");
    }

    /**
     * Reload mannequins - remove and respawn all.
     */
    public void reload() {
        loadData();
        spawnAll();
        plugin.getLogger().info("[MannequinManager] Reloaded mannequins");
    }

    /**
     * Create and save a new arena mannequin at the given location.
     */
    public void createArenaMannequin(Location loc, Player creator) {
        String id = UUID.randomUUID().toString().substring(0, 8);

        // Save to config
        String path = "mannequins." + id;
        data.set(path + ".type", "arena");
        data.set(path + ".world", loc.getWorld().getName());
        data.set(path + ".x", loc.getX());
        data.set(path + ".y", loc.getY());
        data.set(path + ".z", loc.getZ());
        data.set(path + ".created-by", creator.getName());
        data.set(path + ".created-at", System.currentTimeMillis());
        saveData();

        // Spawn the mannequin
        spawnArenaMannequin(loc, id);

        Messages.send(creator, "<green>Arena NPC spawned and saved!</green>");
        Messages.send(creator, "<gray>ID: " + id + " - Players can right-click it to open the arena selection menu.</gray>");
    }

    /**
     * Spawn an arena mannequin at the given location.
     */
    private void spawnArenaMannequin(Location loc, String id) {
        ConfigManager config = ConfigManager.getInstance();
        String skinUrl = config.getArenaNPCSkinURL();
        loc.setYaw(90);
        loc.setPitch(0);

        loc.getWorld().spawn(loc, Villager.class, mannequin -> {
            // Remove display name (no "arenanpc" above them)
            mannequin.setCustomNameVisible(false);
            mannequin.setAI(false);
            mannequin.setSilent(true);
            mannequin.getPersistentDataContainer().set(Keys.ARENA_NPC_KEY, PersistentDataType.BYTE, (byte) 1);

            // Apply villager skin (use default villager skin if no URL configured)
//            try {
//                PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "Villager");
//                PlayerTextures playerTextures = profile.getTextures();
//
//                // Use villager skin URL if configured, otherwise use default villager texture
//                if (skinUrl != null && !skinUrl.isEmpty()) {
//                    playerTextures.setSkin(URI.create(skinUrl).toURL());
//                } else {
//                    // Default villager skin texture
//                    playerTextures.setSkin(URI.create("http://textures.minecraft.net/texture/42291d82db22fb42e785f86d3f44a3268e4e75b429ace4e4c8c5a95e8e4eb66b").toURL());
//                }
//                profile.setTextures(playerTextures);
//                mannequin.setProfile(ResolvableProfile.resolvableProfile(profile));
//                Messages.debug("[MannequinManager] Applied villager skin to mannequin " + id);
//            } catch (MalformedURLException e) {
//                plugin.getLogger().warning("[MannequinManager] Failed to apply skin for mannequin " + id + ": " + e.getMessage());
//            }

            spawnedMannequins.add(mannequin);
        });
    }

    /**
     * Remove mannequins within a radius and delete from config.
     * @return Number of mannequins removed
     */
    public int removeNearby(Location center, double radius) {
        int removed = 0;

        ConfigurationSection mannequins = data.getConfigurationSection("mannequins");
        if (mannequins == null) return 0;

        List<String> toRemove = new ArrayList<>();

        for (String id : mannequins.getKeys(false)) {
            ConfigurationSection section = mannequins.getConfigurationSection(id);
            if (section == null) continue;

            String worldName = section.getString("world");
            if (!center.getWorld().getName().equals(worldName)) continue;

            double x = section.getDouble("x");
            double y = section.getDouble("y");
            double z = section.getDouble("z");

            Location mannequinLoc = new Location(center.getWorld(), x, y, z);
            if (mannequinLoc.distance(center) <= radius) {
                toRemove.add(id);
            }
        }

        for (String id : toRemove) {
            data.set("mannequins." + id, null);
            removed++;
        }

        if (removed > 0) {
            saveData();
            // Respawn to reflect changes
            spawnAll();
        }

        return removed;
    }

    /**
     * Get count of saved mannequins.
     */
    public int getCount() {
        ConfigurationSection mannequins = data.getConfigurationSection("mannequins");
        return mannequins == null ? 0 : mannequins.getKeys(false).size();
    }
}

