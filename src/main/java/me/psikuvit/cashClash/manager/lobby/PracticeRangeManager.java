package me.psikuvit.cashClash.manager.lobby;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.config.MessagesConfig;
import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.manager.Shutdownable;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.Powerable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The practice range: a spot outside any game where players try out the shop's items for free.
 * Each player gets a practice wallet ({@link CashClashPlayer#from} resolves to it) that's topped
 * back up every few ticks, and a shop villager to spend it at. Its mobs stand in for a game's
 * players: skeletons stay on their spot and shoot practicing players, zombie dummies pace a
 * straight line, pausing at each end, piglins are the player's teammates, and a button spawns
 * or removes a boss iron golem that hunts practicing players. Item abilities hit and help them
 * like players (see {@code AbilityTargets}). They only exist while someone is practicing and,
 * the boss aside, come back a few seconds after dying. A hit that would kill a practicing player
 * sends them back to the range's spawn on full health instead. Locations are set in game
 * (/cc practice) and saved to practice-range.yml.
 */
public class PracticeRangeManager implements Shutdownable, Listener {

    private enum MobKind { SKELETON, ZOMBIE, PIGLIN }

    private record ZombiePath(Location start, Location end) {}

    /**
     * Which configured spot a live mob belongs to, so it comes back in the same place.
     */
    private record MobSlot(MobKind kind, int index) {}

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
    private Location bossSpawn;
    private Location bossButton;
    private final List<Location> skeletonSpots = new ArrayList<>();
    private final List<Location> piglinSpots = new ArrayList<>();
    private final List<ZombiePath> zombiePaths = new ArrayList<>();
    private final Map<UUID, Location> pendingZombieStarts = new HashMap<>();

    private final Map<UUID, CashClashPlayer> practicing = new HashMap<>();
    private final Map<UUID, MobSlot> mobs = new HashMap<>();
    private final Map<UUID, Patrol> patrols = new HashMap<>();
    private final List<BukkitTask> respawnTasks = new ArrayList<>();
    private Villager villager;
    private IronGolem boss;
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

    /**
     * The mythics a practicing player has bought since entering - each can be bought once.
     * Empty if none, or if they're not practicing.
     */
    public Set<MythicItem> getPracticeMythics(Player player) {
        Set<MythicItem> owned = EnumSet.noneOf(MythicItem.class);
        CashClashPlayer wallet = practicing.get(player.getUniqueId());
        if (wallet == null) return owned;
        for (PurchaseRecord record : wallet.getPurchaseHistory()) {
            if (record.item() instanceof MythicItem mythic) owned.add(mythic);
        }
        return owned;
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

        if (queueManager.leave(player) == QueueManager.LeaveResult.PARTY_MEMBER) return;
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
        for (int i = 0; i < piglinSpots.size(); i++) spawnPiglin(i);
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
        removeBoss();
    }

    /**
     * An enemy that stays on its spot - its walking speed is zero, so it only turns to aim -
     * with a bow, and shoots practicing players.
     */
    private void spawnSkeleton(int index) {
        Location loc = skeletonSpots.get(index);
        if (loc.getWorld() == null) return;

        Skeleton skeleton = loc.getWorld().spawn(loc, Skeleton.class, mob -> {
            prepareMob(mob, "practice.name-skeleton");
            tagEnemy(mob);
            mob.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
            mob.getEquipment().setItemInMainHandDropChance(0f);
            AttributeInstance speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) speed.setBaseValue(0);
            AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(maxHealth.getBaseValue() * configManager.getPracticeSkeletonHealthMultiplier());
                mob.setHealth(maxHealth.getValue());
            }
        });
        mobs.put(skeleton.getUniqueId(), new MobSlot(MobKind.SKELETON, index));
    }

    private void spawnZombie(int index) {
        ZombiePath path = zombiePaths.get(index);
        if (path.start().getWorld() == null) return;

        Zombie zombie = path.start().getWorld().spawn(path.start(), Zombie.class, mob -> {
            prepareMob(mob, "practice.name-zombie");
            tagEnemy(mob);
            mob.setAdult();
        });
        Bukkit.getMobGoals().removeAllGoals(zombie);
        mobs.put(zombie.getUniqueId(), new MobSlot(MobKind.ZOMBIE, index));
        patrols.put(zombie.getUniqueId(), new Patrol(index));
        zombie.getPathfinder().moveTo(path.end(), configManager.getPracticeZombieSpeed());
    }

    /**
     * A teammate: stands on its spot without AI - piglins run on a brain rather than goals, and
     * that brain would otherwise attack players and wander.
     */
    private void spawnPiglin(int index) {
        Location loc = piglinSpots.get(index);
        if (loc.getWorld() == null) return;

        Piglin piglin = loc.getWorld().spawn(loc, Piglin.class, mob -> {
            prepareMob(mob, "practice.name-piglin");
            mob.getPersistentDataContainer().set(Keys.PRACTICE_TEAMMATE_KEY, PersistentDataType.BYTE, (byte) 1);
            mob.setAdult();
            mob.setImmuneToZombification(true);
            mob.setAI(false);
        });
        mobs.put(piglin.getUniqueId(), new MobSlot(MobKind.PIGLIN, index));
    }

    /**
     * Strips everything a natural spawn gives a range mob and names it. It isn't saved with its
     * chunk: the range respawns its mobs itself, so a saved copy would come back as a duplicate.
     */
    private void prepareMob(Mob mob, String nameKey) {
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        mob.getEquipment().clear();
        mob.customName(Messages.parse(messagesConfig.getMessage(nameKey)));
        mob.setCustomNameVisible(true);
    }

    /**
     * Marks a range mob as an enemy - what item abilities hit and players can damage outside a
     * game.
     */
    private void tagEnemy(Mob mob) {
        mob.getPersistentDataContainer().set(Keys.PRACTICE_MOB_KEY, PersistentDataType.BYTE, (byte) 1);
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

    // ==================== BOSS ====================

    /**
     * The boss button: spawns the boss iron golem, or removes it if it's already out.
     */
    private void toggleBoss() {
        if (boss != null && boss.isValid()) {
            removeBoss();
            Messages.broadcast(practicing.keySet(), "practice.boss-despawned");
            return;
        }
        if (bossSpawn == null || bossSpawn.getWorld() == null) {
            Messages.broadcast(practicing.keySet(), "practice.boss-not-set");
            return;
        }

        boss = bossSpawn.getWorld().spawn(bossSpawn, IronGolem.class, mob -> {
            prepareMob(mob, "practice.name-boss");
            tagEnemy(mob);
            mob.setPlayerCreated(false);
        });
        SoundUtils.playAt(bossSpawn, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.8f);
        Messages.broadcast(practicing.keySet(), "practice.boss-spawned");
    }

    private void removeBoss() {
        if (boss != null) {
            boss.remove();
            boss = null;
        }
    }

    private boolean isBoss(Entity entity) {
        return boss != null && entity.getUniqueId().equals(boss.getUniqueId());
    }

    /**
     * Whether an entity is one of the range's mobs (not its shop villager).
     */
    private boolean isRangeMob(Entity entity) {
        return entity != null && (mobs.containsKey(entity.getUniqueId()) || isBoss(entity));
    }

    // ==================== TICK ====================

    /**
     * Every 5 ticks: tops practice wallets back up, walks the zombies - on to the far end of
     * their path, then a pause, then back - puts skeletons knocked off their spot back on it,
     * and points the boss at the nearest practicing player.
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

        for (Map.Entry<UUID, MobSlot> entry : mobs.entrySet()) {
            MobSlot slot = entry.getValue();
            if (slot.kind() != MobKind.SKELETON || slot.index() >= skeletonSpots.size()) continue;
            if (!(Bukkit.getEntity(entry.getKey()) instanceof Skeleton skeleton) || !skeleton.isOnGround()) continue;

            Location spot = skeletonSpots.get(slot.index());
            if (horizontalDistanceSquared(skeleton.getLocation(), spot) > 1.0) {
                Location back = spot.clone();
                back.setYaw(skeleton.getYaw());
                back.setPitch(skeleton.getPitch());
                skeleton.teleport(back);
            }
        }

        if (boss != null) {
            if (!boss.isValid()) {
                boss = null;
            } else if (!(boss.getTarget() instanceof Player target) || !isPracticing(target)) {
                boss.setTarget(nearestPracticingPlayer(boss.getLocation(), configManager.getPracticeBossTargetRange()));
            }
        }
    }

    private Player nearestPracticingPlayer(Location from, double range) {
        Player nearest = null;
        double best = range * range;
        for (UUID uuid : practicing.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.getWorld().equals(from.getWorld())) continue;
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) continue;
            double distance = player.getLocation().distanceSquared(from);
            if (distance <= best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
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
        boolean wasBoss = isBoss(entity);
        MobSlot slot = mobs.remove(entity.getUniqueId());
        if (slot == null && !wasBoss) return;

        event.getDrops().clear();
        event.setDroppedExp(0);
        patrols.remove(entity.getUniqueId());

        if (wasBoss) {
            boss = null;
            return;
        }

        BukkitTask[] task = new BukkitTask[1];
        task[0] = SchedulerUtils.runTaskLater(() -> {
            respawnTasks.remove(task[0]);
            if (practicing.isEmpty()) return;
            switch (slot.kind()) {
                case SKELETON -> { if (slot.index() < skeletonSpots.size()) spawnSkeleton(slot.index()); }
                case ZOMBIE -> { if (slot.index() < zombiePaths.size()) spawnZombie(slot.index()); }
                case PIGLIN -> { if (slot.index() < piglinSpots.size()) spawnPiglin(slot.index()); }
            }
        }, configManager.getPracticeMobRespawnSeconds() * 20L);
        respawnTasks.add(task[0]);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPracticeMobCombust(EntityCombustEvent event) {
        if (isRangeMob(event.getEntity())) event.setCancelled(true);
    }

    /**
     * Skeletons and the boss only ever go after practicing players; zombie dummies and piglins
     * never pick a target at all.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPracticeMobTarget(EntityTargetEvent event) {
        Entity entity = event.getEntity();
        if (!isRangeMob(entity) || event.getTarget() == null) return;

        boolean hunts = entity instanceof Skeleton || entity instanceof IronGolem;
        if (!hunts || !(event.getTarget() instanceof Player target) || !isPracticing(target)) {
            event.setCancelled(true);
        }
    }

    /**
     * The range's mobs only hurt practicing players and the piglin teammates - not each other,
     * and not anyone walking past in the hub.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPracticeMobHit(EntityDamageByEntityEvent event) {
        Entity attacker = event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
                ? shooter : event.getDamager();
        if (!isRangeMob(attacker)) return;

        Entity victim = event.getEntity();
        boolean allowed = victim instanceof Player player ? isPracticing(player) : PDCDetection.isPracticeTeammate(victim);
        if (!allowed) event.setCancelled(true);
    }

    /**
     * Nobody dies in the practice range: a hit that would kill a practicing player sends them
     * back to the spawn on full health instead.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPracticePlayerLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isPracticing(player)) return;
        if (event.getFinalDamage() < player.getHealth()) return;

        event.setCancelled(true);
        CashClashPlayer.healToFull(player);
        player.setFireTicks(0);
        player.setFallDistance(0);
        if (spawn != null && spawn.getWorld() != null) player.teleport(spawn);
        Messages.send(player, "practice.knocked-out");
        SoundUtils.play(player, Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBossButton(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null || bossButton == null || !block.getLocation().equals(bossButton)) return;
        if (!isPracticing(event.getPlayer())) return;
        if (block.getBlockData() instanceof Powerable button && button.isPowered()) return;

        toggleBoss();
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

    public int addPiglin(Location loc) {
        piglinSpots.add(loc);
        save();
        if (!practicing.isEmpty()) spawnPiglin(piglinSpots.size() - 1);
        return piglinSpots.size();
    }

    public void setBossSpawn(Location loc) {
        bossSpawn = loc;
        save();
    }

    /**
     * @param loc the button block's location
     */
    public void setBossButton(Location loc) {
        bossButton = loc;
        save();
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
     * Removes every skeleton spot, zombie path and piglin spot, and their live mobs. The boss's
     * spawn and button stay.
     */
    public void clearMobs() {
        boolean active = !practicing.isEmpty();
        despawnRange();
        skeletonSpots.clear();
        zombiePaths.clear();
        piglinSpots.clear();
        save();
        if (active) spawnVillager();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);

        spawn = readLocation(data.getConfigurationSection("spawn"));
        villagerLocation = readLocation(data.getConfigurationSection("villager"));
        bossSpawn = readLocation(data.getConfigurationSection("boss"));
        bossButton = readLocation(data.getConfigurationSection("boss-button"));
        readLocations(data.getConfigurationSection("skeletons"), skeletonSpots);
        readLocations(data.getConfigurationSection("piglins"), piglinSpots);

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

    private static void readLocations(ConfigurationSection section, List<Location> into) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Location loc = readLocation(section.getConfigurationSection(key));
            if (loc != null) into.add(loc);
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        LocationUtils.serializeLocation(data, "spawn", spawn);
        LocationUtils.serializeLocation(data, "villager", villagerLocation);
        LocationUtils.serializeLocation(data, "boss", bossSpawn);
        LocationUtils.serializeLocation(data, "boss-button", bossButton);
        for (int i = 0; i < skeletonSpots.size(); i++) {
            LocationUtils.serializeLocation(data, "skeletons." + i, skeletonSpots.get(i));
        }
        for (int i = 0; i < piglinSpots.size(); i++) {
            LocationUtils.serializeLocation(data, "piglins." + i, piglinSpots.get(i));
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
