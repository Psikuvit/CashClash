package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.ActionBarQueue;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Warden Gloves - boxing punch ability with Speed I, a right-click shockwave cone, and a
 * shift+right-click Rising Fury ability. Base melee damage is 0 (see
 * {@link MythicItemManager#createMythicItem}); Rising Fury is the only source of real damage,
 * temporarily swapping the item's attack attributes to diamond-sword-equivalent and stacking
 * reach on landed hits. Holding the gloves occupies both hands - the off-hand item is stashed
 * and shown a cosmetic paired glove for as long as the gloves stay in the main hand.
 */
public class WardenGlovesHandler extends MythicItemHandler {

    /** Baseline (non-Rising-Fury) damage modifier key - zeroes total attack damage to 0. */
    public static final NamespacedKey WARDEN_DAMAGE_BASELINE_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "warden_damage_baseline");
    /** Netherite Sword base (8.0) -> 0.0 baseline damage. */
    public static final double WARDEN_BASELINE_DAMAGE_DELTA = -8.0;

    private static final NamespacedKey WARDEN_DAMAGE_RISING_FURY_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "warden_damage_rising_fury");
    /** Netherite Sword base (8.0) -> 7.0 diamond-sword-equivalent damage while Rising Fury is active. */
    private static final double RISING_FURY_DAMAGE_DELTA = -1.0;
    private static final NamespacedKey[] RISING_FURY_REACH_STACK_KEYS = {
            new NamespacedKey(CashClashPlugin.getInstance(), "warden_reach_stack_1"),
            new NamespacedKey(CashClashPlugin.getInstance(), "warden_reach_stack_2"),
            new NamespacedKey(CashClashPlugin.getInstance(), "warden_reach_stack_3"),
    };
    private static final int PRIORITY_RISING_FURY_TIMER = 4;

    // Warden Gloves boxing punch counter (UUID -> punch count)
    private final Map<UUID, Integer> wardenPunchCount;
    // Warden Gloves boxing ability active (UUID -> true if ability is active)
    private final Set<UUID> wardenBoxingActive;

    // Both-hands: off-hand item stashed while Warden Gloves is held in the main hand
    private final Map<UUID, ItemStack> wardenStashedOffhand;
    private final Set<UUID> wardenBothHandsActive;

    // Rising Fury
    private final Set<UUID> risingFuryActive;
    private final Map<UUID, Integer> risingFuryHitCount;
    private final Map<UUID, BukkitTask> risingFuryTimeoutTasks;

    public WardenGlovesHandler(MythicItemManager manager) {
        super(manager);
        this.wardenPunchCount = new ConcurrentHashMap<>();
        this.wardenBoxingActive = ConcurrentHashMap.newKeySet();
        this.wardenStashedOffhand = new ConcurrentHashMap<>();
        this.wardenBothHandsActive = ConcurrentHashMap.newKeySet();
        this.risingFuryActive = ConcurrentHashMap.newKeySet();
        this.risingFuryHitCount = new ConcurrentHashMap<>();
        this.risingFuryTimeoutTasks = new ConcurrentHashMap<>();
    }

    /**
     * Warden Gloves boxing ability - Left click to punch.
     * Ability lasts for 20 seconds, 35 second cooldown. Also feeds Rising Fury's landed-hit
     * tracking (no-op if Rising Fury isn't active).
     */
    public void useWardenPunch(Player player, Player victim) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "WARDEN_GLOVES: Punch attack on " + victim.getName());

        onRisingFuryHit(player, victim);

        // Check if boxing ability is on cooldown (ability hasn't been started yet)
        if (!wardenBoxingActive.contains(uuid) && cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WARDEN_BOXING)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_BOXING);
            Messages.send(player, "mythic.genericitem-cooldown", "{item_name}", "Boxing gloves", "{cooldown_seconds}", String.valueOf(remaining));
            return;
        }

        // Start boxing ability if not already active
        if (!wardenBoxingActive.contains(uuid)) {
            startWardenBoxingAbility(player);
        }

        // Increment punch count (kept for analytics/debug)
        int punchCount = wardenPunchCount.getOrDefault(uuid, 0) + 1;
        wardenPunchCount.put(uuid, punchCount);

        // Apply punch knockback
        Vector knockback = victim.getLocation().toVector()
                .subtract(player.getLocation().toVector())
                .normalize()
                .multiply(1.2)
                .setY(0.4);
        victim.setVelocity(knockback);

        // Maintain Speed I every punch to avoid ramping
        int durationTicks = cfg.getWardenBoxingDuration() * 20;
        CashClashPlayer.applyEffect(player, PotionEffectType.SPEED, durationTicks, 0, false, true);

        // Punch sound effect
        SoundUtils.play(victim, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.0f, 1.0f);
        ParticleUtils.sweep(victim.getLocation().add(0, 1, 0));

        Messages.debug(player, "WARDEN_GLOVES: Punch hit! Count: " + punchCount);
    }

    /**
     * Start the Warden boxing ability (20 second duration).
     */
    private void startWardenBoxingAbility(Player player) {
        UUID uuid = player.getUniqueId();

        wardenBoxingActive.add(uuid);
        wardenPunchCount.put(uuid, 0);

        // Start with Speed I immediately and keep it at Speed I
        int durationTicks = cfg.getWardenBoxingDuration() * 20;
        CashClashPlayer.applyEffect(player, PotionEffectType.SPEED, durationTicks, 0, false, true);

        Messages.send(player, "mythic.boxing-gloves-activated");
        Messages.send(player, "mythic.genericitem-punch");
        SoundUtils.play(player, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);

        // End the ability after duration
        BukkitTask endTask = SchedulerUtils.runTaskLater(() -> endWardenBoxingAbility(player), durationTicks);

        manager.trackTask(uuid, endTask);

        Messages.debug(player, "WARDEN_GLOVES: Boxing ability started with Speed I - " + cfg.getWardenBoxingDuration() + "s duration");
    }

    /**
     * End the Warden boxing ability and start cooldown.
     */
    private void endWardenBoxingAbility(Player player) {
        UUID uuid = player.getUniqueId();

        if (!wardenBoxingActive.contains(uuid)) return;

        wardenBoxingActive.remove(uuid);
        wardenPunchCount.remove(uuid);

        // Remove speed effect
        CashClashPlayer.removeEffect(player, PotionEffectType.SPEED);

        // Start cooldown
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_BOXING, cfg.getWardenBoxingCooldown());
        Messages.send(player, "mythic.boxing-gloves-cooldown", "seconds", String.valueOf(cfg.getWardenBoxingCooldown()));
        Messages.debug(player, "WARDEN_GLOVES: Boxing ability ended - cooldown " + cfg.getWardenBoxingCooldown() + "s");
    }

    /**
     * Check if player has boxing ability active.
     */
    public boolean isWardenBoxingActive(UUID playerId) {
        return wardenBoxingActive.contains(playerId);
    }

    /**
     * Warden Gloves shockwave attack (Right-click ability).
     * Unleashes shockwave dealing damage + big knockback in cone.
     */
    public void useWardenShockwave(Player player) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "WARDEN_GLOVES: Shockwave ability triggered");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE)) {
            Messages.debug(player, "WARDEN_GLOVES: Shockwave on cooldown - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE) + "s");
            Messages.send(player, "mythic.shockwave-cooldown", "cooldown_seconds",
                    String.valueOf(cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE)));
            return;
        }

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE, cfg.getWardenShockwaveCooldown());

        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        if (session == null) {
            Messages.debug(player, "WARDEN_GLOVES: No session");
            return;
        }

        Team playerTeam = session.getPlayerTeam(player);
        Location loc = player.getLocation();
        Vector direction = loc.getDirection().setY(0).normalize();
        World world = player.getWorld();

        // Sonic boom visual effect
        ParticleUtils.sonicBoom(loc.clone().add(direction.clone().multiply(2)).add(0, 1, 0));
        SoundUtils.playAt(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);

        int range = cfg.getWardenShockwaveRange();
        int hitCount = 0;

        // Damage and knockback enemies in cone
        for (Entity entity : world.getNearbyEntities(loc, range, 4, range)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;

            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam != null && playerTeam != null &&
                targetTeam.getTeamNumber() == playerTeam.getTeamNumber()) continue;

            // Check if target is in front of player (cone check)
            Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
            if (direction.dot(toTarget) < 0.3) continue; // Not in cone (about 70 degree cone)

            target.damage(cfg.getWardenShockwaveDamage(), player);

            Vector knockback = toTarget.multiply(cfg.getWardenKnockbackPower()).setY(0.8);
            target.setVelocity(knockback);
            hitCount++;
        }

        Messages.debug(player, "WARDEN_GLOVES: Shockwave hit " + hitCount + " enemies, damage: " + cfg.getWardenShockwaveDamage() + ", cooldown: " + cfg.getWardenShockwaveCooldown() + "s");
        Messages.send(player, "mythic.shockwave-activated");
    }

    // ==================== BOTH-HANDS OFF-HAND STASH ====================

    /**
     * Called on every main-hand slot switch: stashes the off-hand item and shows a cosmetic
     * paired glove there while Warden Gloves is held in the main hand, and restores it once
     * the player switches away. Also cancels Rising Fury on switching away.
     */
    public void onHandSwitch(Player player, ItemStack newMainHandItem) {
        UUID uuid = player.getUniqueId();
        boolean holdingWarden = PDCDetection.getMythic(newMainHandItem) == MythicItem.WARDEN_GLOVES;
        boolean wasBothHands = wardenBothHandsActive.contains(uuid);

        if (holdingWarden && !wasBothHands) {
            ItemStack currentOffhand = player.getInventory().getItemInOffHand();
            wardenStashedOffhand.put(uuid, currentOffhand != null ? currentOffhand.clone() : new ItemStack(Material.AIR));
            player.getInventory().setItemInOffHand(createPairedGloveCosmetic());
            wardenBothHandsActive.add(uuid);
        } else if (!holdingWarden && wasBothHands) {
            ItemStack stashed = wardenStashedOffhand.remove(uuid);
            player.getInventory().setItemInOffHand(stashed != null ? stashed : new ItemStack(Material.AIR));
            wardenBothHandsActive.remove(uuid);
            endRisingFury(player, false);
        }
    }

    private ItemStack createPairedGloveCosmetic() {
        ItemStack item = new ItemStack(MythicItem.WARDEN_GLOVES.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.parse("<light_purple><bold>Warden Gloves</bold></light_purple>"));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== RISING FURY ====================

    /**
     * Rising Fury (shift+right-click): temporarily swaps Warden Gloves' baseline-0 damage for
     * diamond-sword-equivalent, stacking reach every 3rd landed hit (max 3 stacks/9 hits).
     * Cancels on weapon swap or after {@code no-hit-timeout-seconds} without landing a hit.
     */
    public void useRisingFury(Player player) {
        UUID uuid = player.getUniqueId();

        if (risingFuryActive.contains(uuid)) return;

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WARDEN_RISING_FURY)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_RISING_FURY);
            Messages.send(player, "mythic.genericitem-cooldown", "{item_name}", "Rising Fury", "{cooldown_seconds}", String.valueOf(remaining));
            return;
        }

        if (PDCDetection.getMythic(player.getInventory().getItemInMainHand()) != MythicItem.WARDEN_GLOVES) return;

        risingFuryActive.add(uuid);
        risingFuryHitCount.put(uuid, 0);
        applyRisingFuryAttributes(player, true, 0);

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_RISING_FURY, cfg.getWardenRisingFuryCooldown());
        Messages.send(player, "mythic.warden-rising-fury-activated");
        SoundUtils.play(player, Sound.ENTITY_WARDEN_ROAR, 1.0f, 1.0f);

        resetRisingFuryTimeout(player);
        Messages.debug(player, "WARDEN_GLOVES: Rising Fury activated");
    }

    /**
     * Resets (or starts) the no-hit auto-cancel window, and refreshes the action-bar countdown
     * to match. The countdown text is purely visual - this scheduled task is what actually ends
     * the ability at 0.
     */
    private void resetRisingFuryTimeout(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask existing = risingFuryTimeoutTasks.remove(uuid);
        if (existing != null && !existing.isCancelled()) existing.cancel();

        int timeoutTicks = cfg.getWardenRisingFuryNoHitTimeoutSeconds() * 20;
        BukkitTask task = SchedulerUtils.runTaskLater(() -> endRisingFury(player, true), timeoutTicks);
        risingFuryTimeoutTasks.put(uuid, task);
        manager.trackTask(uuid, task);

        ActionBarQueue.get().startCountdownTimer(player, timeoutTicks * 50L, PRIORITY_RISING_FURY_TIMER,
                seconds -> "<gold>⚔ Rising Fury: <yellow>" + seconds + "s</yellow> to land a hit</gold>", null);
    }

    /**
     * Called from {@link #useWardenPunch} on every landed hit - no-op if Rising Fury isn't
     * active. Resets the no-hit timeout and advances the reach-stacking counter.
     */
    private void onRisingFuryHit(Player player, Player victim) {
        UUID uuid = player.getUniqueId();
        if (!risingFuryActive.contains(uuid)) return;

        resetRisingFuryTimeout(player);

        int maxStacks = cfg.getWardenRisingFuryMaxStacks();
        int maxHits = maxStacks * 3;
        int hitCount = Math.min(maxHits, risingFuryHitCount.getOrDefault(uuid, 0) + 1);
        risingFuryHitCount.put(uuid, hitCount);
        int stacks = hitCount / 3;

        applyRisingFuryAttributes(player, true, stacks);

        if (stacks >= maxStacks) {
            tryBreakShield(victim);
        }
    }

    private void tryBreakShield(Player victim) {
        if (victim == null || !victim.isOnline() || !victim.isBlocking()) return;

        victim.setCooldown(Material.SHIELD, cfg.getWardenShieldDisableTicks());
        Messages.send(victim, "mythic.warden-shield-broken");
        SoundUtils.play(victim, Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f);
    }

    /**
     * Ends Rising Fury, reverting the item's attributes back to baseline-0 damage/no reach.
     * @param natural true when ended by the no-hit timeout (sends a message/sound); false when
     *                cancelled by a weapon swap (silent - the item is about to change anyway).
     */
    private void endRisingFury(Player player, boolean natural) {
        UUID uuid = player.getUniqueId();
        if (!risingFuryActive.remove(uuid)) return;

        risingFuryHitCount.remove(uuid);
        BukkitTask task = risingFuryTimeoutTasks.remove(uuid);
        if (task != null && !task.isCancelled()) task.cancel();
        ActionBarQueue.get().stopCountdownTimer(player);

        applyRisingFuryAttributes(player, false, 0);

        if (natural && player.isOnline()) {
            Messages.send(player, "mythic.warden-rising-fury-ended");
            SoundUtils.play(player, Sound.ENTITY_WARDEN_DEATH, 0.6f, 1.2f);
        }
    }

    /**
     * Whether Rising Fury is currently active for a player - used by {@code BunnyShoesHandler}
     * to block Bunny Shoes activation for the duration.
     */
    public boolean isRisingFuryActive(UUID uuid) {
        return risingFuryActive.contains(uuid);
    }

    /**
     * Swaps the Warden Gloves item's damage modifier between baseline-0 and diamond-sword-
     * equivalent, and applies/clears reach-stack modifiers, by re-issuing the held ItemStack's
     * meta - mythic attribute modifiers live on the item itself, not a runtime AttributeInstance.
     * No-ops if the player is no longer actually holding Warden Gloves in their main hand.
     */
    private void applyRisingFuryAttributes(Player player, boolean active, int stacks) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (PDCDetection.getMythic(item) != MythicItem.WARDEN_GLOVES) return;

        ItemMeta meta = item.getItemMeta();
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE);

        if (active) {
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    WARDEN_DAMAGE_RISING_FURY_KEY, RISING_FURY_DAMAGE_DELTA, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

            double reachPerStack = cfg.getWardenRisingFuryReachPerStack();
            for (int i = 0; i < stacks && i < RISING_FURY_REACH_STACK_KEYS.length; i++) {
                meta.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE, new AttributeModifier(
                        RISING_FURY_REACH_STACK_KEYS[i], reachPerStack, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            }
        } else {
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    WARDEN_DAMAGE_BASELINE_KEY, WARDEN_BASELINE_DAMAGE_DELTA, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }

        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
    }

    @Override
    public void cleanup() {
        wardenPunchCount.clear();
        wardenBoxingActive.clear();

        risingFuryActive.clear();
        risingFuryHitCount.clear();
        risingFuryTimeoutTasks.values().forEach(task -> {
            if (task != null && !task.isCancelled()) task.cancel();
        });
        risingFuryTimeoutTasks.clear();

        wardenBothHandsActive.clear();
        wardenStashedOffhand.clear();
    }

    @Override
    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        wardenPunchCount.remove(uuid);
        wardenBoxingActive.remove(uuid);

        risingFuryActive.remove(uuid);
        risingFuryHitCount.remove(uuid);
        BukkitTask task = risingFuryTimeoutTasks.remove(uuid);
        if (task != null && !task.isCancelled()) task.cancel();
        ActionBarQueue.get().stopCountdownTimer(player);

        wardenBothHandsActive.remove(uuid);
        wardenStashedOffhand.remove(uuid);
    }
}
