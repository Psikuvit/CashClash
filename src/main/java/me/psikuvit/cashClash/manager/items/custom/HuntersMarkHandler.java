package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Bukkit;
import org.bukkit.Color;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hunter's Mark: hold right-click within range of an enemy to charge a mark that
 * makes them take bonus damage (base + 2% per missing heart, clamped by the
 * display). The mark is tracked by a rotating coal block over the target's head
 * plus a floating vulnerability % and self-tears down on expiry or death.
 */
public class HuntersMarkHandler extends CustomItemHandler {

    // Hunter's Mark - hold-to-charge state (per attacker) and active marks (per target). The
    // target's damage-in multiplier is derived live from their missing hearts.
    private final Map<UUID, Integer> hunterMarkChargeTicks;
    private final Map<UUID, BukkitTask> hunterMarkChargeTasks;
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
        this.hunterMarkChargeTicks = new HashMap<>();
        this.hunterMarkChargeTasks = new HashMap<>();
        this.hunterMarks = new HashMap<>();
        this.markedUntil = new HashMap<>();
    }

    /**
     * Starts the "hold right-click within range of an enemy" charge. The item is food-eligible so
     * right-click raises the hand (see GameplayItemFactory), letting us poll isHandRaised() every
     * tick like Radiating Lotus - release before the timer completes, or an out-of-range/no-target
     * tick, cancels the charge.
     */
    public void startHunterMarkCharge(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        if (hunterMarkChargeTasks.containsKey(uuid)) return; // already charging

        hunterMarkChargeTicks.put(uuid, 0);
        int requiredTicks = cfg.getHuntersMarkChargeSeconds() * 20;

        BukkitTask task = SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            @Override
            public void run() {
                Integer ticks = hunterMarkChargeTicks.get(uuid);
                boolean stillCharging = ticks != null && player.isOnline() && player.isHandRaised()
                        && PDCDetection.getCustomItem(player.getInventory().getItemInMainHand()) == CustomItem.HUNTERS_MARK;

                if (!stillCharging) {
                    cancelHunterMarkCharge(uuid);
                    cancel();
                    return;
                }

                Player target = findNearestMarkTarget(player, cfg.getHuntersMarkRange());
                if (target == null) {
                    cancelHunterMarkCharge(uuid);
                    cancel();
                    Messages.send(player, "customitem.hunters-mark-no-target");
                    return;
                }

                int next = ticks + 1;
                hunterMarkChargeTicks.put(uuid, next);
                ParticleUtils.spawnDust(target.getLocation().add(0, 1, 0), Color.fromRGB(230, 40, 40), 0.8f, 3, 0.2);

                if (next >= requiredTicks) {
                    applyHunterMark(player, target, item);
                    cancelHunterMarkCharge(uuid);
                    cancel();
                }
            }
        }, 0L, 1L);
        hunterMarkChargeTasks.put(uuid, task);
    }

    private void cancelHunterMarkCharge(UUID uuid) {
        hunterMarkChargeTicks.remove(uuid);
        hunterMarkChargeTasks.remove(uuid);
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
     * @return damage-in multiplier for the target: 1.0 when not marked, otherwise 1 + the live
     * vulnerability % (base + 2% per missing heart), clamped so a dead-health target can't exceed
     * the display's cap.
     */
    public double getVulnerabilityMultiplier(UUID targetUuid) {
        Long until = markedUntil.get(targetUuid);
        if (until == null || System.currentTimeMillis() >= until) {
            markedUntil.remove(targetUuid);
            return 1.0;
        }
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) return 1.0;
        return 1.0 + hunterMarkPercent(target) / 100.0;
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
            textDisplay.text(Messages.parse("<red><bold>+" + hunterMarkPercent(target) + "%</bold></red>"));
        }, 0L, 1L);

        hunterMarks.put(targetUuid, new HunterMarkInfo(task, targetUuid, coalDisplay, textDisplay, expiresAt));
    }

    private int hunterMarkPercent(Player target) {
        double maxHealth = getMaxHealth(target);
        double missingHearts = Math.max(0, (maxHealth - target.getHealth()) / 2.0);
        return cfg.getHuntersMarkBaseVulnerabilityPercent()
                + (int) Math.round(missingHearts * cfg.getHuntersMarkVulnerabilityPerMissingHeart());
    }

    private Player findNearestMarkTarget(Player player, double range) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
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
        hunterMarkChargeTasks.values().forEach(BukkitTask::cancel);
        hunterMarkChargeTasks.clear();
        hunterMarkChargeTicks.clear();

        new ArrayList<>(hunterMarks.keySet()).forEach(this::clearHunterMark);
        hunterMarks.clear();
        markedUntil.clear();
    }
}
