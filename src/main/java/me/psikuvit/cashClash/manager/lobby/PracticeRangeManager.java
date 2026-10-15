package me.psikuvit.cashClash.manager.lobby;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.config.MessagesConfig;
import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.manager.Shutdownable;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The practice range: a spot outside any game where players try out the shop's items for free.
 * Each player gets a practice wallet ({@link CashClashPlayer#from} resolves to it) that's topped
 * back up every second, and a shop villager to spend it at. The targets - stationary skeletons
 * with extra health and zombies pacing a straight line, pausing at each end - only exist while
 * someone is practicing, and come back a few seconds after dying. Locations are set in game
 * (/cc practice) and saved to practice-range.yml.
 */
public class PracticeRangeManager implements Shutdownable, Listener {

    private record ZombiePath(Location start, Location end) {}

    /**
     * Which configured spot a live mob belongs to, so it comes back in the same place.
     */
    private record MobSlot(boolean zombie, int index) {}

    /**
     * A zombie's walk along its path: which end it's heading for, and while it waits at an end,
     * until when.
     */
    private static final class Patrol {
        final int pathIndex;
        boolean towardsEnd = true;
        long pausedUntil;

        Patrol(int pathIndex) {
            this.pathIndex = pathIndex;
        }
    }

    private final GameManager gameManager;
    private final ConfigManager configManager;
    private final LobbyManager lobbyManager;
    private final QueueManager queueManager;
    private final MessagesConfig messagesConfig;

    private final File file;
    private Location spawn;
    private Location villagerLocation;
    private final List<Location> skeletonSpots = new ArrayList<>();
    private final List<ZombiePath> zombiePaths = new ArrayList<>();
    private final Map<UUID, Location> pendingZombieStarts = new HashMap<>();

    private final Map<UUID, CashClashPlayer> practicing = new HashMap<>();
    private final Map<UUID, MobSlot> mobs = new HashMap<>();
    private final Map<UUID, Patrol> patrols = new HashMap<>();
    private final List<BukkitTask> respawnTasks = new ArrayList<>();
    private Villager villager;
    private BukkitTask tickTask;

    public PracticeRangeManager(GameManager gameManager, ConfigManager configManager, LobbyManager lobbyManager,
                                QueueManager queueManager, MessagesConfig messagesConfig) {
        this.gameManager = gameManager;
        this.configManager = configManager;
        this.lobbyManager = lobbyManager;
        this.queueManager = queueManager;
        this.messagesConfig = messagesConfig;
        this.file = new File(CashClashPlugin.getInstance().getDataFolder(), "practice-range.yml");
        load();
    }

    public void start() {
        tickTask = SchedulerUtils.runTaskTimer(this::tick, 5L, 5L);
    }

    public boolean isPracticing(Player player) {
        return practicing.containsKey(player.getUniqueId());
    }

    public CashClashPlayer getPracticePlayer(UUID uuid) {
        return practicing.get(uuid);
    }

    // ==================== ENTERING / LEAVING ====================

    /**
     * Sends a player into the practice range with a fresh inventory and a full practice wallet.
     */
    public void enter(Player player) {
        if (gameManager.getPlayerSession(player) != null) {
            Messages.send(player, "arena.already-in-game");
            return;
        }
        if (spawn == null || spawn.getWorld() == null) {
            Messages.send(player, "practice.not-set-up");
            return;
        }
        if (isPracticing(player)) {
            Messages.send(player, "practice.already-in");
            return;
        }

        if (queueManager.leave(player, false) == QueueManager.LeaveResult.PARTY_MEMBER) return;
        boolean firstPlayer = practicing.isEmpty();

        CashClashPlayer wallet = new CashClashPlayer(player);
        wallet.setCoins(configManager.getPracticeCoins());
        practicing.put(player.getUniqueId(), wallet);

        player.getInventory().clear();
        CashClashPlayer.clearAllEffects(player);
        CashClashPlayer.healToFull(player);
        player.setFoodLevel(20);
        player.teleport(spawn);
        lobbyManager.givePracticeItems(player);

        SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        Messages.sendTitle(player, "practice.title", "practice.subtitle");

        if (firstPlayer) spawnRange();
    }

    /**
     * Takes a player out of the practice range and back to the hub.
     *
     * @return false if they weren't practicing
     */
    public boolean leave(Player player) {
        if (!remove(player)) return false;
        lobbyManager.sendToLobby(player);
        return true;
    }

    /**
     * Takes a player out of the practice range without sending them anywhere.
     *
     * @return false if they weren't practicing
     */
    public boolean remove(Player player) {
        if (practicing.remove(player.getUniqueId()) == null) return false;
        if (practicing.isEmpty()) despawnRange();
        return true;
    }

    // ==================== MOBS & VILLAGER ====================

    private void spawnRange() {
        for (int i = 0; i < skeletonSpots.size(); i++) spawnSkeleton(i);
        for (int i = 0; i < zombiePaths.size(); i++) spawnZombie(i);
        spawnVillager();
    }

    private void despawnRange() {
        respawnTasks.forEach(BukkitTask::cancel);
        respawnTasks.clear();
        for (UUID uuid : mobs.keySet()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) entity.remove();
        }
        mobs.clear();
        patrols.clear();
        if (villager != null) {
            villager.remove();
            villager = null;
        }
    }

    private void spawnSkeleton(int index) {
        Location loc = skeletonSpots.get(index);
        if (loc.getWorld() == null) return;

        Skeleton skeleton = loc.getWorld().spawn(loc, Skeleton.class, mob -> {
            prepareMob(mob);
            mob.setAI(false);
            AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(maxHealth.getBaseValue() * configManager.getPracticeSkeletonHealthMultiplier());
                mob.setHealth(maxHealth.getValue());
            }
        });
        mobs.put(skeleton.getUniqueId(), new MobSlot(false, index));
    }

    private void spawnZombie(int index) {
        ZombiePath path = zombiePaths.get(index);
        if (path.start().getWorld() == null) return;

        Zombie zombie = path.start().getWorld().spawn(path.start(), Zombie.class, mob -> {
            prepareMob(mob);
            mob.setAdult();
        });
        Bukkit.getMobGoals().removeAllGoals(zombie);
        mobs.put(zombie.getUniqueId(), new MobSlot(true, index));
        patrols.put(zombie.getUniqueId(), new Patrol(index));
        zombie.getPathfinder().moveTo(path.end(), configManager.getPracticeZombieSpeed());
    }

    /**
     * Tags a practice mob and strips everything a natural spawn gives it. It isn't saved with its
     * chunk: the range respawns its mobs itself, so a saved copy would come back as a duplicate.
     */
    private void prepareMob(Mob mob) {
        mob.getPersistentDataContainer().set(Keys.PRACTICE_MOB_KEY, PersistentDataType.BYTE, (byte) 1);
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        if (mob.getEquipment() != null) mob.getEquipment().clear();
    }

    private void spawnVillager() {
        if (villagerLocation == null || villagerLocation.getWorld() == null) return;

        villager = villagerLocation.getWorld().spawn(villagerLocation, Villager.class, npc -> {
            npc.setAI(false);
            npc.setInvulnerable(true);
            npc.setSilent(true);
            npc.setPersistent(false);
            npc.customName(Messages.parse(messagesConfig.getMessage("practice.villager-name")));
            npc.setCustomNameVisible(true);
            npc.getPersistentDataContainer().set(Keys.PRACTICE_SHOP_KEY, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /**
     * Every 5 ticks: tops practice wallets back up and walks the zombies - on to the far end of
     * their path, then a pause, then back.
     */
    private void tick() {
        if (practicing.isEmpty()) return;

        long coins = configManager.getPracticeCoins();
        practicing.values().forEach(wallet -> wallet.setCoins(coins));

        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Patrol> entry : patrols.entrySet()) {
            if (!(Bukkit.getEntity(entry.getKey()) instanceof Zombie zombie)) continue;
            Patrol patrol = entry.getValue();
            ZombiePath path = zombiePaths.get(patrol.pathIndex);
            Location target = patrol.towardsEnd ? path.end() : path.start();

            if (patrol.pausedUntil > 0) {
                if (now < patrol.pausedUntil) continue;
                patrol.pausedUntil = 0;
                patrol.towardsEnd = !patrol.towardsEnd;
                zombie.getPathfinder().moveTo(patrol.towardsEnd ? path.end() : path.start(), configManager.getPracticeZombieSpeed());
                continue;
            }

            if (horizontalDistanceSquared(zombie.getLocation(), target) < 1.0) {
                zombie.getPathfinder().stopPathfinding();
                patrol.pausedUntil = now + configManager.getPracticeZombiePauseSeconds() * 1000L;
            } else if (!zombie.getPathfinder().hasPath()) {
                zombie.getPathfinder().moveTo(target, configManager.getPracticeZombieSpeed());
            }
        }
    }

    private static double horizontalDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    // ==================== EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPracticeMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!PDCDetection.isPracticeMob(entity)) return;

        event.getDrops().clear();
        event.setDroppedExp(0);

        MobSlot slot = mobs.remove(entity.getUniqueId());
        patrols.remove(entity.getUniqueId());
        if (slot == null) return;

        BukkitTask[] task = new BukkitTask[1];
        task[0] = SchedulerUtils.runTaskLater(() -> {
            respawnTasks.remove(task[0]);
            if (practicing.isEmpty()) return;
            if (slot.zombie() && slot.index() < zombiePaths.size()) spawnZombie(slot.index());
            if (!slot.zombie() && slot.index() < skeletonSpots.size()) spawnSkeleton(slot.index());
        }, configManager.getPracticeMobRespawnSeconds() * 20L);
        respawnTasks.add(task[0]);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPracticeMobCombust(EntityCombustEvent event) {
        if (PDCDetection.isPracticeMob(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPracticeMobTarget(EntityTargetEvent event) {
        if (PDCDetection.isPracticeMob(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onShopVillagerClick(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager npc)) return;
        if (!npc.getPersistentDataContainer().has(Keys.PRACTICE_SHOP_KEY, PersistentDataType.BYTE)) return;

        event.setCancelled(true);
        if (isPracticing(event.getPlayer())) {
            ShopGUI.openMain(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (spawn != null && !event.getPlayer().getWorld().equals(spawn.getWorld())) {
            remove(event.getPlayer());
        }
    }

    // ==================== SETUP ====================

    public void setSpawn(Location loc) {
        spawn = loc;
        save();
    }

    public void setVillagerLocation(Location loc) {
        villagerLocation = loc;
        save();
        if (!practicing.isEmpty()) {
            if (villager != null) villager.remove();
            spawnVillager();
        }
    }

    public int addSkeleton(Location loc) {
        skeletonSpots.add(loc);
        save();
        if (!practicing.isEmpty()) spawnSkeleton(skeletonSpots.size() - 1);
        return skeletonSpots.size();
    }

    public void setPendingZombieStart(Player admin, Location loc) {
        pendingZombieStarts.put(admin.getUniqueId(), loc);
    }

    /**
     * Finishes a zombie path from the admin's earlier zombiestart to {@code end}.
     *
     * @return how many paths there are now, or -1 if no start was set
     */
    public int addZombiePath(Player admin, Location end) {
        Location start = pendingZombieStarts.remove(admin.getUniqueId());
        if (start == null) return -1;
        zombiePaths.add(new ZombiePath(start, end));
        save();
        if (!practicing.isEmpty()) spawnZombie(zombiePaths.size() - 1);
        return zombiePaths.size();
    }

    /**
     * Removes every skeleton spot and zombie path, and their live mobs.
     */
    public void clearMobs() {
        boolean active = !practicing.isEmpty();
        despawnRange();
        skeletonSpots.clear();
        zombiePaths.clear();
        save();
        if (active) spawnVillager();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);

        spawn = readLocation(data.getConfigurationSection("spawn"));
        villagerLocation = readLocation(data.getConfigurationSection("villager"));

        ConfigurationSection skeletons = data.getConfigurationSection("skeletons");
        if (skeletons != null) {
            for (String key : skeletons.getKeys(false)) {
                Location loc = readLocation(skeletons.getConfigurationSection(key));
                if (loc != null) skeletonSpots.add(loc);
            }
        }

        ConfigurationSection paths = data.getConfigurationSection("zombie-paths");
        if (paths != null) {
            for (String key : paths.getKeys(false)) {
                Location start = readLocation(paths.getConfigurationSection(key + ".start"));
                Location end = readLocation(paths.getConfigurationSection(key + ".end"));
                if (start != null && end != null) zombiePaths.add(new ZombiePath(start, end));
            }
        }
    }

    private static Location readLocation(ConfigurationSection section) {
        return section == null ? null : LocationUtils.deserializeLocation(section);
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        LocationUtils.serializeLocation(data, "spawn", spawn);
        LocationUtils.serializeLocation(data, "villager", villagerLocation);
        for (int i = 0; i < skeletonSpots.size(); i++) {
            LocationUtils.serializeLocation(data, "skeletons." + i, skeletonSpots.get(i));
        }
        for (int i = 0; i < zombiePaths.size(); i++) {
            LocationUtils.serializeLocation(data, "zombie-paths." + i + ".start", zombiePaths.get(i).start());
            LocationUtils.serializeLocation(data, "zombie-paths." + i + ".end", zombiePaths.get(i).end());
        }
        try {
            data.save(file);
        } catch (IOException e) {
            CashClashPlugin.getInstance().getLogger().severe("Failed to save practice-range.yml: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        despawnRange();
        practicing.clear();
    }
}
