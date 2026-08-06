package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.CashClashPlugin;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Radiating Lotus: hold right-click to charge, release (or hit the hard cap) to
 * detonate. Detonation knocks the player back, heals self + same-team members in
 * a charge-scaled radius, and briefly grants knockback protection. While charging
 * the holder is slowed by an attribute modifier.
 */
public class RadiatingLotusHandler extends CustomItemHandler {

    // Radiating Lotus - charge-hold state
    private final Map<UUID, Integer> lotusChargeTicks;
    private final Map<UUID, BukkitTask> lotusChargeTasks;
    private static final NamespacedKey LOTUS_SLOW_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "radiating_lotus_slow");

    public RadiatingLotusHandler(CustomItemManager manager) {
        super(manager);
        this.lotusChargeTicks = new HashMap<>();
        this.lotusChargeTasks = new HashMap<>();
    }

    /**
     * Starts the "hold right-click to charge" window. The item is food-eligible so right-click
     * raises the hand (see GameplayItemFactory), letting us poll isHandRaised() every tick to
     * detect when the player releases - there is no generic held-right-click event in Bukkit.
     */
    public void startRadiatingLotusCharge(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        if (lotusChargeTasks.containsKey(uuid)) return; // already charging

        lotusChargeTicks.put(uuid, 0);
        applyLotusSlow(player);

        int maxTicks = cfg.getLotusMaxChargeSeconds() * 20;
        int hardCapTicks = maxTicks + cfg.getLotusGraceSeconds() * 20;

        BukkitTask task = SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            @Override
            public void run() {
                Integer ticks = lotusChargeTicks.get(uuid);
                boolean stillCharging = ticks != null && player.isOnline() && player.isHandRaised()
                        && PDCDetection.getCustomItem(player.getInventory().getItemInMainHand()) == CustomItem.RADIATING_LOTUS;

                if (!stillCharging) {
                    finishLotusCharge(player, item, ticks == null ? 0 : Math.min(ticks, maxTicks));
                    cancel();
                    return;
                }

                int next = ticks + 1;
                lotusChargeTicks.put(uuid, next);
                if (next >= hardCapTicks) {
                    finishLotusCharge(player, item, maxTicks); // hard timeout at cap - auto-fires per spec's grace period
                    cancel();
                }
            }
        }, 0L, 1L);
        lotusChargeTasks.put(uuid, task);
    }

    /**
     * Detonates the lotus: knocks the player back, heals self + teammates, and plays the
     * knockback/heal visuals - only at detonation, never during the charge-up.
     */
    private void finishLotusCharge(Player player, ItemStack item, int chargeTicks) {
        UUID uuid = player.getUniqueId();
        lotusChargeTicks.remove(uuid);
        lotusChargeTasks.remove(uuid);
        removeLotusSlow(player);
        consumeItem(player, item);

        double chargeSeconds = chargeTicks / 20.0;

        // Flat knockback regardless of charge time - charging now only affects heal radius.
        double knockbackDistance = cfg.getLotusKnockbackDistance();
        // Mostly horizontal - a tiny vertical lift keeps this from reading as a launch, but a
        // fully flat velocity gets killed almost instantly by ground friction while grounded,
        // which made the knockback invisible/non-functional.
        Vector back = player.getLocation().getDirection().clone().setY(0).normalize().multiply(-knockbackDistance * 0.45);
        back.setY(0.1);
        player.setVelocity(back);
        cooldownManager.setCooldownSeconds(uuid, "WIND_CHARGE_PROTECTION", 2);

        Messages.send(player, "customitem.lotus-detonated");
        SoundUtils.playAt(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.4f);

        Location loc = player.getLocation();
        // Floor of 3 blocks (3x3) so the heal radius/diamond are never tiny even on a short charge.
        double healRadius = Math.max(3.0, chargeSeconds * cfg.getLotusHealRadiusPerSecond());
        double healAmount = cfg.getLotusHealAmount();

        ParticleUtils.spawnDust(loc.clone().add(0, 1, 0), Color.fromRGB(60, 200, 60), 2.0f, 40, 0.5);
        spawnHealRadiusDiamond(loc, healRadius);

        World world = loc.getWorld();
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        Team team = session != null ? session.getPlayerTeam(player) : null;
        if (team == null || world == null) return;

        for (Entity entity : world.getNearbyEntities(loc, healRadius, healRadius, healRadius)) {
            if (!(entity instanceof Player target)) continue;
            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam == null || targetTeam.getTeamNumber() != team.getTeamNumber()) continue;

            double heal = healAmount * manager.getHealingMultiplier(target.getUniqueId());
            CashClashPlayer.heal(target, heal);
        }
    }

    /**
     * Draws the pink diamond marking the heal radius for a few ticks so it's actually
     * visible rather than a single-frame flash.
     */
    private void spawnHealRadiusDiamond(Location loc, double healRadius) {
        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                ParticleUtils.groundDiamond(loc, healRadius, Color.fromRGB(255, 105, 180));
                if (++tick >= 6) {
                    cancel();
                }
            }
        }, 0L, 2L);
    }

    private void applyLotusSlow(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        double reduction = cfg.getLotusSlowPercentWhileCharging() / 100.0;
        speed.addModifier(new AttributeModifier(LOTUS_SLOW_KEY, -reduction, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void removeLotusSlow(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(LOTUS_SLOW_KEY);
    }

    @Override
    public void cleanup() {
        lotusChargeTasks.forEach((uuid, task) -> {
            task.cancel();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) removeLotusSlow(player);
        });
        lotusChargeTasks.clear();
        lotusChargeTicks.clear();
    }
}
