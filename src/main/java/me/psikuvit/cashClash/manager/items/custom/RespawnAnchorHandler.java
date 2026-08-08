package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Respawn Anchor: lets a living player revive a dead (0-lives) same-team player.
 * The reviver must stay within 3 blocks of their start position and 5 of the
 * target for the 10-second channel; completing grants 1 life, +4 max health, a
 * teleport to spawn and 3s of invincibility. Limited to 2 uses per round per
 * reviver, and each target can only be revived once per round.
 */
public class RespawnAnchorHandler extends CustomItemHandler {

    // Respawn anchor tracking - stores reviver UUID -> target UUID and task
    private final Map<UUID, UUID> respawnAnchorTargets;
    private final Map<UUID, BukkitTask> respawnAnchorTasks;
    private final Map<UUID, Integer> respawnAnchorsUsedThisRound;
    private final Set<UUID> playersRevivedThisRound;

    public RespawnAnchorHandler(CustomItemManager manager) {
        super(manager);
        this.respawnAnchorTargets = new HashMap<>();
        this.respawnAnchorTasks = new HashMap<>();
        this.respawnAnchorsUsedThisRound = new HashMap<>();
        this.playersRevivedThisRound = new HashSet<>();
    }

    /**
     * Start reviving a dead teammate with respawn anchor.
     */
    public void useRespawnAnchor(Player reviver, Player target, ItemStack item) {
        UUID reviverUuid = reviver.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(reviver);
        if (session == null) {
            Messages.send(reviver, "gamestate.must-be-in-game");
            return;
        }

        // Check if same team
        Team reviverTeam = session.getPlayerTeam(reviver);
        Team targetTeam = session.getPlayerTeam(target);
        if (reviverTeam == null || targetTeam == null || reviverTeam.getTeamNumber() != targetTeam.getTeamNumber()) {
            Messages.send(reviver, "customitem.revive-teammates-only");
            return;
        }

        // Check if target actually needs reviving (has 0 lives)
        CashClashPlayer targetCcp = session.getCashClashPlayer(targetUuid);
        if (targetCcp == null || targetCcp.getLives() > 0) {
            Messages.send(reviver, "customitem.revive-target-has-lives", "player_name", target.getName());
            return;
        }

        // Check max 2 uses per round
        int usesThisRound = respawnAnchorsUsedThisRound.getOrDefault(reviverUuid, 0);
        if (usesThisRound >= 2) {
            Messages.send(reviver, "customitem.revive-max-anchors");
            return;
        }

        // Check if target was already revived this round
        if (playersRevivedThisRound.contains(targetUuid)) {
            Messages.send(reviver, "customitem.revive-already-revived", "player_name", target.getName());
            return;
        }

        // Check if already reviving someone
        if (respawnAnchorTargets.containsKey(reviverUuid)) {
            Messages.send(reviver, "customitem.revive-already-reviving");
            return;
        }

        // Start the revive process
        respawnAnchorTargets.put(reviverUuid, targetUuid);
        consumeItem(reviver, item);
        respawnAnchorsUsedThisRound.merge(reviverUuid, 1, Integer::sum);

        Messages.send(reviver, "customitem.revive-start-reviver", "player_name", target.getName());
        Messages.send(target, "customitem.revive-start-target", "player_name", reviver.getName());
        SoundUtils.play(reviver, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.0f);

        final Location startLoc = reviver.getLocation();

        // Progress task - check distance every second
        BukkitTask progressTask = SchedulerUtils.runTaskTimer(() -> {
            // Check if reviver moved too far
            if (reviver.getLocation().distance(startLoc) > 3) {
                cancelRevive(reviverUuid, "Revive cancelled - you moved too far!");
                return;
            }
            // Check if target moved too far from reviver
            if (reviver.getLocation().distance(target.getLocation()) > 5) {
                cancelRevive(reviverUuid, "Revive cancelled - target moved too far!");
                return;
            }
            // Particle effect
            ParticleUtils.portal(target.getLocation().add(0, 1, 0), 10, 0.5);
        }, 20L, 20L);

        respawnAnchorTasks.put(reviverUuid, progressTask);

        // Complete revive after 10 seconds
        SchedulerUtils.runTaskLater(() -> {
            if (!respawnAnchorTargets.containsKey(reviverUuid)) return; // Was cancelled

            BukkitTask task = respawnAnchorTasks.remove(reviverUuid);
            if (task != null) task.cancel();
            respawnAnchorTargets.remove(reviverUuid);

            // Final distance check
            if (reviver.getLocation().distance(target.getLocation()) > 5) {
                Messages.send(reviver, "customitem.revive-failed-distance");
                return;
            }

            // Complete the revive
            completeRevive(session, reviver, target);
        }, 10 * 20L);

    }

    private void cancelRevive(UUID reviverUuid, String message) {
        respawnAnchorTargets.remove(reviverUuid);
        BukkitTask task = respawnAnchorTasks.remove(reviverUuid);
        if (task != null) task.cancel();

        Player reviver = Bukkit.getPlayer(reviverUuid);
        if (reviver != null) {
            Messages.send(reviver, "customitem.revive-failed-generic", "reason", message);
            SoundUtils.play(reviver, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.5f);
        }
    }

    private void completeRevive(GameSession session, Player reviver, Player target) {
        UUID targetUuid = target.getUniqueId();
        CashClashPlayer targetCcp = session.getCashClashPlayer(targetUuid);

        if (targetCcp == null) return;

        // Grant 1 life
        targetCcp.setLives(targetCcp.getLives() + 1);
        playersRevivedThisRound.add(targetUuid);

        // Grant +2 bonus hearts (4 max health increase) via the centralized health system
        targetCcp.addHealthModifier(4.0);

        // Get spawn location for the revived player
        Location spawnLocation = session.getSpawnForPlayer(targetUuid);
        if (spawnLocation == null) {
            spawnLocation = reviver.getLocation(); // Fallback to reviver's location
        }

        // Teleport and change game mode to SURVIVAL
        target.teleport(spawnLocation);
        target.setGameMode(GameMode.SURVIVAL);

        // Set health to full after teleport
        targetCcp.healToFull();
        target.setFoodLevel(20);

        // 3 seconds of invincibility
        CashClashPlayer.applyEffect(target, PotionEffectType.RESISTANCE, 3 * 20, 4, false, true); // Resistance V = invincible
        CashClashPlayer.applyEffect(target, PotionEffectType.GLOWING, 3 * 20, 0, false, true);

        Messages.send(reviver, "customitem.revive-success-reviver", "player_name", target.getName());
        Messages.send(target, "customitem.revive-success-target", "player_name", reviver.getName());

        SoundUtils.play(reviver, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 1.0f);
        SoundUtils.play(target, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);

        // Visual effect at spawn location
        ParticleUtils.totem(target.getLocation().add(0, 1, 0), 50, 0.5);
    }

    /**
     * Check if a player can be targeted for revive (has 0 lives, same team, not already revived)
     */
    public boolean canBeRevived(Player reviver, Player target) {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(reviver);
        if (session == null) return false;

        Team reviverTeam = session.getPlayerTeam(reviver);
        Team targetTeam = session.getPlayerTeam(target);
        if (reviverTeam == null || targetTeam == null) return false;
        if (reviverTeam.getTeamNumber() != targetTeam.getTeamNumber()) return false;

        CashClashPlayer targetCcp = session.getCashClashPlayer(target.getUniqueId());
        if (targetCcp == null || targetCcp.getLives() > 0) return false;

        return !playersRevivedThisRound.contains(target.getUniqueId());
    }

    @Override
    public void cleanup() {
        respawnAnchorTasks.values().forEach(BukkitTask::cancel);
        respawnAnchorTasks.clear();
        respawnAnchorTargets.clear();
        respawnAnchorsUsedThisRound.clear();
        playersRevivedThisRound.clear();
    }
}
