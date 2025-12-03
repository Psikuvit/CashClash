package me.psikuvit.cashClash.shop;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
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

    private final NamespacedKey shopKey = new NamespacedKey(CashClashPlugin.getInstance(), "shop_npc");

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
        if (arena == null) return;

        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
        if (tpl == null) return; // template not configured

        World world = session.getGameWorld();
        if (world == null) return;

        List<UUID> spawned = new ArrayList<>();

        for (Location location : tpl.getVillagersSpawnPoint()) {
            world.spawn(location, Villager.class, villager -> {
                villager.setInvulnerable(true);
                villager.setAI(false);
                villager.setSilent(true);
                villager.setPersistent(true);
                // set a Component name and disable italics using Messages.parse
                Component comp = Messages.parse("<green>Shop - Team 1</green>");
                villager.customName(comp);

                villager.getPersistentDataContainer().set(shopKey, PersistentDataType.BYTE, (byte) 1);
                spawned.add(villager.getUniqueId());
                entityToSession.put(villager.getUniqueId(), session.getSessionId());
                entityTeam.put(villager.getUniqueId(), 1);
            });

        }

        if (!spawned.isEmpty()) sessionShops.put(session.getSessionId(), spawned);
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

        ShopGUI.openCategories(player);
    }
}
