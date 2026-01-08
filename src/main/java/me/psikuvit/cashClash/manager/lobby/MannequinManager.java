package me.psikuvit.cashClash.manager.lobby;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
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
    private final List<Mannequin> spawnedMannequins = new ArrayList<>();
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

            Location loc = new Location(world, x, y, z);

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
        for (Mannequin mannequin : new ArrayList<>(spawnedMannequins)) {
            if (mannequin != null && !mannequin.isDead()) {
                mannequin.remove();
            }
        }
        spawnedMannequins.clear();

        // Also remove any orphaned mannequins with our key
        for (World world : Bukkit.getWorlds()) {
            for (Mannequin mannequin : world.getEntitiesByClass(Mannequin.class)) {
                if (mannequin.getPersistentDataContainer().has(Keys.ARENA_NPC_KEY, PersistentDataType.BYTE)) {
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

        loc.getWorld().spawn(loc, Mannequin.class, mannequin -> {
            mannequin.customName(Messages.parse(config.getArenaNPCDisplayName()));
            mannequin.setCustomNameVisible(true);
            mannequin.setImmovable(true);
            mannequin.getPersistentDataContainer().set(Keys.ARENA_NPC_KEY, PersistentDataType.BYTE, (byte) 1);

            // Apply skin if URL is configured
            if (skinUrl != null && !skinUrl.isEmpty()) {
                Messages.debug(skinUrl);
                try {
                    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "ArenaNPC");
                    PlayerTextures playerTextures = profile.getTextures();
                    playerTextures.setSkin(URI.create(skinUrl).toURL());
                    profile.setTextures(playerTextures);
                    mannequin.setProfile(ResolvableProfile.resolvableProfile(profile));
                    Messages.debug("[MannequinManager] Applied skin to mannequin " + id);
                } catch (MalformedURLException e) {
                    plugin.getLogger().warning("[MannequinManager] Failed to apply skin for mannequin " + id + ": " + e.getMessage());
                }
            }

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

