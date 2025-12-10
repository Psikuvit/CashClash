package me.psikuvit.cashClash.manager;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages in-world shop NPCs (Villagers) per GameSession.
 */
public class ShopManager {

    private static ShopManager instance;

    // sessionId -> spawned entity UUIDs
    private final Map<UUID, List<UUID>> sessionShops = new HashMap<>();
    private final Map<UUID, UUID> entityToSession = new HashMap<>();
    private final Map<UUID, Integer> entityTeam = new HashMap<>();


    private ShopManager() {}

    public static ShopManager getInstance() {
        if (instance == null) instance = new ShopManager();
        return instance;
    }

    /**
     * Create shops (villagers) for the given session using the arena's configured shop template locations.
     * Spawns villagers in the session world adjusted from the template coordinates.
     */
    public void createShopsForSession(GameSession session) {
        Arena arena = ArenaManager.getInstance().getArena(session.getArenaNumber());
        if (arena == null) {
            CashClashPlugin.getInstance().getLogger().warning("Cannot create shops: Arena not found for session " + session.getSessionId());
            return;
        }

        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
        if (tpl == null) {
            CashClashPlugin.getInstance().getLogger().warning("Cannot create shops: Template not configured for arena " + arena.getName());
            return;
        }

        World world = session.getGameWorld();
        if (world == null) {
            CashClashPlugin.getInstance().getLogger().warning("Cannot create shops: Game world not found for session " + session.getSessionId());
            return;
        }

        List<Location> villagerSpawns = tpl.getVillagersSpawnPoint();
        if (villagerSpawns.isEmpty()) {
            CashClashPlugin.getInstance().getLogger().warning("No villager spawn points configured for template " + tpl.getId());
            return;
        }

        List<UUID> spawned = new ArrayList<>();

        for (Location templateLoc : villagerSpawns) {
            // Adjust location to the copied world
            Location spawnLoc = LocationUtils.adjustLocationToWorld(templateLoc, world);

            Villager villager = world.spawn(spawnLoc, Villager.class);

            villager.setInvulnerable(true);
            villager.setAI(false);
            villager.setSilent(true);
            villager.setPersistent(true);
            // set a Component name and disable italics using Messages.parse
            Component comp = Messages.parse("<green>Shop</green>");
            villager.customName(comp);
            villager.setCustomNameVisible(true);

            villager.getPersistentDataContainer().set(Keys.SHOP_NPC_KEY, PersistentDataType.BYTE, (byte) 1);
            spawned.add(villager.getUniqueId());
            entityToSession.put(villager.getUniqueId(), session.getSessionId());
            entityTeam.put(villager.getUniqueId(), 1);

            if (!spawned.isEmpty()) {
                sessionShops.put(session.getSessionId(), spawned);
                CashClashPlugin.getInstance().getLogger().info("Spawned " + spawned.size() + " shop villagers for session " + session.getSessionId());
            }
        }
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
        }
    }

    public UUID getSessionIdForEntity(UUID entityId) {
        return entityToSession.get(entityId);
    }

    public Integer getTeamForEntity(UUID entityId) {
        return entityTeam.get(entityId);
    }

    /**
     * Handle player clicking a shop entity. Opens GUI if in same session.
     */
    public void onPlayerInteractShop(Player player, Entity entity) {
        if (entity == null) return;
        UUID id = entity.getUniqueId();
        UUID sessionId = entityToSession.get(id);

        if (sessionId == null) return;

        var sess = GameManager.getInstance().getActiveSessions().stream()
                .filter(s -> s.getSessionId().equals(sessionId)).findFirst().orElse(null);
        if (sess == null) return;

        if (!sess.getPlayers().contains(player.getUniqueId())) {
            Messages.send(player, "<red>You are not part of this game session.</red>");
            return;
        }

        ShopGUI.openMain(player);
    }
}
