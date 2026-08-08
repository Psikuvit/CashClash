package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hunter's Mark: a single right-click within range of an enemy instantly marks them,
 * making them take a flat 15% more damage for the mark's duration. The mark is tracked
 * by a rotating coal block over the target's head plus a floating vulnerability % and
 * self-tears down on expiry or death.
 */
public class HuntersMarkHandler extends CustomItemHandler {

    // Hunter's Mark - active marks, keyed by target
    private final Map<UUID, HunterMarkInfo> hunterMarks;
    private final Map<UUID, Long> markedUntil;

    /**
     * Active mark on a target: the tracking/rotation task plus its two display entities
     * (a rotating coal block on the head and a floating vulnerability % above it).
     */
    private record HunterMarkInfo(BukkitTask task, UUID targetUuid, ItemDisplay coalDisplay, TextDisplay textDisplay, long expiresAt) {
    }

    public HuntersMarkHandler(CustomItemManager manager) {
        super(manager);
        this.hunterMarks = new HashMap<>();
        this.markedUntil = new HashMap<>();
    }

    /**
     * Right-click within range of an enemy instantly applies Hunter's Mark - no charge-up.
     */
    public void useHuntersMark(Player player, ItemStack item) {
        Player target = findNearestMarkTarget(player, cfg.getHuntersMarkRange());
        if (target == null) {
            Messages.send(player, "customitem.hunters-mark-no-target");
            return;
        }
        applyHunterMark(player, target, item);
    }

    private void applyHunterMark(Player hunter, Player target, ItemStack item) {
        consumeItem(hunter, item);

        clearHunterMark(target.getUniqueId());

        long durationMillis = cfg.getHuntersMarkDurationSeconds() * 1000L;
        markedUntil.put(target.getUniqueId(), System.currentTimeMillis() + durationMillis);

        Messages.send(hunter, "customitem.hunters-mark-applied", "player_name", target.getName());
        Messages.send(target, "customitem.hunters-mark-target");
        SoundUtils.play(hunter, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.2f);
        SoundUtils.play(target, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.4f);

        spawnHunterMarkDisplay(target, durationMillis);
    }

    /**
     * Tears down an active mark: cancels its task and removes its display entities. Safe to call
     * even when the target has no mark.
     */
    public void clearHunterMark(UUID targetUuid) {
        HunterMarkInfo info = hunterMarks.remove(targetUuid);
        if (info != null) {
            info.task().cancel();
            if (!info.coalDisplay().isDead()) info.coalDisplay().remove();
            if (!info.textDisplay().isDead()) info.textDisplay().remove();
        }
        markedUntil.remove(targetUuid);
    }

    /**
     * @return damage-in multiplier for the target: 1.0 when not marked, otherwise 1 + the flat
     * mark vulnerability %.
     */
    public double getVulnerabilityMultiplier(UUID targetUuid) {
        Long until = markedUntil.get(targetUuid);
        if (until == null || System.currentTimeMillis() >= until) {
            markedUntil.remove(targetUuid);
            return 1.0;
        }
        return 1.0 + hunterMarkPercent() / 100.0;
    }

    private void spawnHunterMarkDisplay(Player target, long durationMillis) {
        long expiresAt = System.currentTimeMillis() + durationMillis;
        World world = target.getWorld();

        ItemDisplay coalDisplay = world.spawn(target.getEyeLocation(), ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.COAL_BLOCK));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
            Transformation t = display.getTransformation();
            display.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(), new Vector3f(0.6f, 0.6f, 0.6f), t.getRightRotation()));
        });

        TextDisplay textDisplay = world.spawn(target.getEyeLocation(), TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(false);
            display.text(Messages.parse("<red><bold>+" + hunterMarkPercent() + "%</bold></red>"));
        });

        UUID targetUuid = target.getUniqueId();
        BukkitTask task = SchedulerUtils.runTaskTimer(() -> {
            if (!target.isOnline() || target.isDead() || System.currentTimeMillis() >= expiresAt) {
                clearHunterMark(targetUuid);
                return;
            }
            Location eye = target.getEyeLocation();
            coalDisplay.teleport(eye.clone().add(0, 0.25, 0));
            coalDisplay.setRotation(coalDisplay.getYaw() + 12f, 0f);
            textDisplay.teleport(eye.clone().add(0, 0.7, 0));
        }, 0L, 1L);

        hunterMarks.put(targetUuid, new HunterMarkInfo(task, targetUuid, coalDisplay, textDisplay, expiresAt));
    }

    private int hunterMarkPercent() {
        return cfg.getHuntersMarkBaseVulnerabilityPercent();
    }

    private Player findNearestMarkTarget(Player player, double range) {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        Team team = session != null ? session.getPlayerTeam(player) : null;

        Player nearest = null;
        double best = range;
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), range, range, range)) {
            if (!(entity instanceof Player target) || target.equals(player)) continue;
            if (team != null) {
                Team targetTeam = session.getPlayerTeam(target);
                if (targetTeam == null || targetTeam.getTeamNumber() == team.getTeamNumber()) continue;
            }
            double distance = target.getLocation().distance(player.getLocation());
            if (distance <= best) {
                best = distance;
                nearest = target;
            }
        }
        return nearest;
    }

    @Override
    public void cleanup() {
        new ArrayList<>(hunterMarks.keySet()).forEach(this::clearHunterMark);
        hunterMarks.clear();
        markedUntil.clear();
    }
}
