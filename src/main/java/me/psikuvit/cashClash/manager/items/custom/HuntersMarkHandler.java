package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hunter's Mark: a single right-click within range of an enemy instantly marks them,
 * making them take a flat 15% more damage for the mark's duration. The mark shows as a floating
 * vulnerability % over the target's head and tears itself down on expiry or death.
 */
public class HuntersMarkHandler extends CustomItemHandler {

    // Hunter's Mark - active marks, keyed by target
    private final Map<UUID, HunterMarkInfo> hunterMarks;
    private final Map<UUID, Long> markedUntil;
    private final Map<UUID, Integer> lastUseTicks;

    /**
     * Active mark on a target: the tracking task plus the floating vulnerability % above them.
     */
    private record HunterMarkInfo(BukkitTask task, UUID targetUuid, TextDisplay textDisplay, long expiresAt) {
    }

    public HuntersMarkHandler(CustomItemManager manager) {
        super(manager);
        this.hunterMarks = new HashMap<>();
        this.markedUntil = new HashMap<>();
        this.lastUseTicks = new HashMap<>();
    }

    /**
     * Right-click within range of an enemy instantly applies Hunter's Mark - no charge-up.
     */
    public void useHuntersMark(Player player, ItemStack item) {
        useHuntersMark(player, item, EquipmentSlot.HAND);
    }

    /**
     * Uses a Hunter's Mark held in {@code hand} - it works from the off-hand too. A second use
     * within a tick is the same right-click reaching the Mark again ({@link #useFromOffHand}), and
     * does nothing.
     */
    public void useHuntersMark(Player player, ItemStack item, EquipmentSlot hand) {
        Integer lastTick = lastUseTicks.put(player.getUniqueId(), Bukkit.getCurrentTick());
        if (lastTick != null && Bukkit.getCurrentTick() - lastTick <= 1) return;

        LivingEntity target = findNearestMarkTarget(player, cfg.getHuntersMarkRange());
        if (target == null) {
            Messages.send(player, "customitem.hunters-mark-no-target");
            return;
        }
        applyHunterMark(player, target, item, hand);
    }

    /**
     * Whether a right-click by this player goes to the Hunter's Mark in their off hand instead of
     * their main-hand item: there's one there, and custom items can be used (not in the buy phase).
     */
    public boolean takesRightClick(Player player) {
        if (PDCDetection.getCustomItem(player.getInventory().getItemInOffHand()) != CustomItem.HUNTERS_MARK) return false;
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        return session == null || session.getState() != GameState.SHOPPING && !session.isActionsRestricted();
    }

    /**
     * Uses the off hand's Mark from the main hand's right-click. Vanilla only tries the off hand
     * once the main hand has nothing to do, so a main-hand item with a use of its own (a bow, a
     * block) would otherwise keep the press; the off hand's own click that may follow is ignored.
     */
    public void useFromOffHand(Player player) {
        useHuntersMark(player, player.getInventory().getItemInOffHand(), EquipmentSlot.OFF_HAND);
    }

    private void applyHunterMark(Player hunter, LivingEntity target, ItemStack item, EquipmentSlot hand) {
        consumeItem(hunter, item, hand);

        clearHunterMark(target.getUniqueId());

        long durationMillis = cfg.getHuntersMarkDurationSeconds() * 1000L;
        markedUntil.put(target.getUniqueId(), System.currentTimeMillis() + durationMillis);

        Messages.send(hunter, "customitem.hunters-mark-applied", "player_name", target.getName());
        SoundUtils.play(hunter, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.2f);
        if (target instanceof Player targetPlayer) {
            Messages.send(targetPlayer, "customitem.hunters-mark-target");
            SoundUtils.play(targetPlayer, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.4f);
        }

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
            if (!info.textDisplay().isDead()) info.textDisplay().remove();
        }
        markedUntil.remove(targetUuid);
    }

    /**
     * Whether the target is marked right now.
     */
    public boolean isMarked(UUID targetUuid) {
        return getVulnerabilityMultiplier(targetUuid) > 1.0;
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

    private void spawnHunterMarkDisplay(LivingEntity target, long durationMillis) {
        long expiresAt = System.currentTimeMillis() + durationMillis;
        World world = target.getWorld();

        TextDisplay textDisplay = world.spawn(target.getEyeLocation(), TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(false);
            display.text(Messages.parse("<red><bold>+" + hunterMarkPercent() + "%</bold></red>"));
        });

        UUID targetUuid = target.getUniqueId();
        BukkitTask task = SchedulerUtils.runTaskTimer(() -> {
            if (!target.isValid() || target.isDead() || System.currentTimeMillis() >= expiresAt) {
                clearHunterMark(targetUuid);
                return;
            }
            textDisplay.teleport(target.getEyeLocation().add(0, cfg.getHuntersMarkPercentTextHeight(), 0));
        }, 0L, 1L);

        hunterMarks.put(targetUuid, new HunterMarkInfo(task, targetUuid, textDisplay, expiresAt));
    }

    private int hunterMarkPercent() {
        return cfg.getHuntersMarkBaseVulnerabilityPercent();
    }

    private LivingEntity findNearestMarkTarget(Player player, double range) {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        Team team = session != null ? session.getPlayerTeam(player) : null;

        LivingEntity nearest = null;
        double best = range;
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), range, range, range)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            if (!canHit(session != null, target)) continue;
            if (team != null) {
                Team targetTeam = session.getPlayerTeam((Player) target);
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
        lastUseTicks.clear();
    }
}
