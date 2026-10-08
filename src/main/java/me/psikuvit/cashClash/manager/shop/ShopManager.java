package me.psikuvit.cashClash.manager.shop;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gui.MiniShopGui;
import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.enums.TeamColor;
import me.psikuvit.cashClash.util.items.PDCSetter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages in-world shop NPCs (Villagers) per GameSession.
 */
public class ShopManager {

    private static final double LOOK_RANGE = 4;
    private static final long LOOK_UPDATE_TICKS = 2L;

    // sessionId -> spawned entity UUIDs
    private final Map<UUID, List<UUID>> sessionShops;
    private final Map<UUID, UUID> entityToSession;
    private final Map<UUID, Integer> entityTeam;
    // Mini shop villagers, and the one team each serves
    private final Map<UUID, TeamColor> miniShopTeams;

    private BukkitTask lookAtPlayerTask;

    private final ArenaManager arenaManager;
    private final GameManager gameManager;

    public ShopManager(ArenaManager arenaManager, GameManager gameManager) {
        this.arenaManager = arenaManager;
        this.gameManager = gameManager;
        this.sessionShops = new HashMap<>();
        this.entityToSession = new HashMap<>();
        this.entityTeam = new HashMap<>();
        this.miniShopTeams = new HashMap<>();
        startLookAtPlayerTask();
    }

    /**
     * Create shops (villagers) for the given session using the arena's configured shop template locations.
     * Spawns villagers in the session world adjusted from the template coordinates.
     */
    public void createShopsForSession(GameSession session) {
        Arena arena = arenaManager.getArena(session.getArenaNumber());
        if (arena == null) {
            Messages.debug("SHOP", "Cannot create shops: Arena not found for session " + session.getSessionId());
            return;
        }

        TemplateWorld tpl = arenaManager.getTemplate(arena.getTemplateId());
        if (tpl == null) {
            Messages.debug("SHOP", "Cannot create shops: Template not configured for arena " + arena.getName());
            return;
        }

        World world = session.getGameWorld();
        if (world == null) {
            Messages.debug("SHOP", "Cannot create shops: Game world not found for session " + session.getSessionId());
            return;
        }

        List<Location> villagerSpawns = tpl.getVillagersSpawnPoint();
        if (villagerSpawns.isEmpty()) {
            Messages.debug("SHOP", "No villager spawn points configured for template " + tpl.getId());
        }

        List<UUID> spawned = new ArrayList<>();

        for (Location templateLoc : villagerSpawns) {
            Villager villager = spawnShopVillager(session, LocationUtils.copyToWorld(templateLoc, world), Messages.parse("<green>Shop</green>"));
            spawned.add(villager.getUniqueId());
            entityTeam.put(villager.getUniqueId(), 1);
        }

        for (TeamColor team : TeamColor.values()) {
            Location templateLoc = tpl.getMiniShopVillager(team);
            if (templateLoc == null) continue;

            Component name = Messages.parse(CashClashPlugin.getInstance().getMessagesConfig().getMessage("shop.mini-shop-villager-name",
                    "color", team.getDisplayName().toLowerCase(), "team", team.getDisplayName()));
            Villager villager = spawnShopVillager(session, LocationUtils.copyToWorld(templateLoc, world), name);
            spawned.add(villager.getUniqueId());
            entityTeam.put(villager.getUniqueId(), team.getTeamNumber());
            miniShopTeams.put(villager.getUniqueId(), team);
        }

        if (!spawned.isEmpty()) {
            sessionShops.put(session.getSessionId(), spawned);
            Messages.debug("SHOP", "Spawned " + spawned.size() + " shop villagers for session " + session.getSessionId());
        }
    }

    private Villager spawnShopVillager(GameSession session, Location spawnLoc, Component name) {
        Villager villager = spawnLoc.getWorld().spawn(spawnLoc, Villager.class);

        villager.setInvulnerable(true);
        villager.setAI(false);
        villager.setSilent(true);
        villager.setPersistent(true);
        villager.customName(name);
        villager.setCustomNameVisible(true);

        PDCSetter.of(villager).set(Keys.SHOP_NPC_KEY, PersistentDataType.BYTE, (byte) 1).apply();
        entityToSession.put(villager.getUniqueId(), session.getSessionId());
        return villager;
    }

    /**
     * Remove any spawned shop NPCs for the session.
     */
    public void removeShopsForSession(GameSession session) {
        List<UUID> list = sessionShops.remove(session.getSessionId());
        if (list == null || list.isEmpty()) return;

        for (UUID id : list) {
            Villager e = (Villager) Bukkit.getEntity(id);
            if (e != null && !e.isDead()) e.remove();

            entityToSession.remove(id);
            entityTeam.remove(id);
            miniShopTeams.remove(id);
        }
    }

    public UUID getSessionIdForEntity(UUID entityId) {
        return entityToSession.get(entityId);
    }

    public Integer getTeamForEntity(UUID entityId) {
        return entityTeam.get(entityId);
    }

    /**
     * Handle player clicking a shop entity in their session: the main shop, or - for a team's
     * mini shop villager - the mini shop, which serves only that team and only during combat.
     */
    public void onPlayerInteractShop(Player player, Entity entity) {
        if (entity == null) return;
        UUID id = entity.getUniqueId();
        UUID sessionId = entityToSession.get(id);

        if (sessionId == null) return;

        var sess = gameManager.getActiveSessions().stream()
                .filter(s -> s.getSessionId().equals(sessionId)).findFirst().orElse(null);
        if (sess == null) return;

        if (!sess.getPlayers().contains(player.getUniqueId())) {
            Messages.send(player, "shop.not-in-game");
            return;
        }

        TeamColor miniShopTeam = miniShopTeams.get(id);
        if (miniShopTeam == null) {
            ShopGUI.openMain(player);
            return;
        }

        Team playerTeam = sess.getPlayerTeam(player);
        if (playerTeam == null || playerTeam.getTeamNumber() != miniShopTeam.getTeamNumber()) {
            Messages.send(player, "shop.mini-shop-enemy");
            return;
        }
        if (sess.getState() != GameState.COMBAT) {
            Messages.send(player, "shop.mini-shop-combat-only");
            return;
        }
        new MiniShopGui(player).open();
    }

    /**
     * Start a repeating task that makes all shop villagers look at the nearest player.
     */
    private void startLookAtPlayerTask() {
        lookAtPlayerTask = SchedulerUtils.runTaskTimer(() -> {
            // Iterate through all tracked shop entities
            for (UUID entityId : entityToSession.keySet()) {
                Entity entity = Bukkit.getEntity(entityId);
                if (!(entity instanceof Villager villager)) continue;
                if (villager.isDead()) continue;

                // Find the nearest player within range
                Player nearestPlayer = null;
                double nearestDistance = LOOK_RANGE;

                for (Entity nearby : villager.getNearbyEntities(LOOK_RANGE, LOOK_RANGE, LOOK_RANGE)) {
                    if (!(nearby instanceof Player player)) continue;

                    double distance = villager.getLocation().distance(player.getLocation());
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestPlayer = player;
                    }
                }

                // Make the villager look at the nearest player
                if (nearestPlayer != null) {
                    Location villagerLoc = villager.getLocation();
                    Location playerLoc = nearestPlayer.getEyeLocation();

                    // Calculate direction to player
                    double dx = playerLoc.getX() - villagerLoc.getX();
                    double dy = playerLoc.getY() - (villagerLoc.getY() + 1.62); // Villager eye height
                    double dz = playerLoc.getZ() - villagerLoc.getZ();

                    // Calculate yaw and pitch
                    double distanceXZ = Math.sqrt(dx * dx + dz * dz);
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float pitch = (float) Math.toDegrees(-Math.atan2(dy, distanceXZ));

                    // Set the villager's rotation
                    villagerLoc.setYaw(yaw);
                    villagerLoc.setPitch(pitch);
                    villager.setRotation(yaw, pitch);
                }
            }
        }, 0L, LOOK_UPDATE_TICKS);
    }

    /**
     * Stop the look-at-player task.
     */
    public void cleanup() {
        if (lookAtPlayerTask != null) {
            lookAtPlayerTask.cancel();
            lookAtPlayerTask = null;
        }
    }
}
