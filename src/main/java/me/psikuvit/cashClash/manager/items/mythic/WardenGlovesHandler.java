package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.game.TimerDisplayUtils;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Warden Gloves - boxing punch ability with Speed I, a right-click shockwave cone, and a
 * shift+right-click Rising Fury ability. Melee damage/speed is diamond-sword-equivalent at all
 * times (see {@link MythicItemManager#createMythicItem}); Rising Fury adds stacking reach on
 * landed hits and shield-breaking at max stacks, on top of that baseline. Holding the gloves
 * occupies both hands - the off-hand item is stashed and shown a cosmetic paired glove for as
 * long as the gloves stay in the main hand.
 */
public class WardenGlovesHandler extends MythicItemHandler {

    private static final NamespacedKey[] RISING_FURY_REACH_STACK_KEYS = {
            Keys.WARDEN_REACH_STACK_1,
            Keys.WARDEN_REACH_STACK_2,
            Keys.WARDEN_REACH_STACK_3,
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

    private final Set<UUID> shockwaveDamageActive;

    public WardenGlovesHandler(MythicItemManager manager) {
        super(manager);
        this.wardenPunchCount = new ConcurrentHashMap<>();
        this.wardenBoxingActive = ConcurrentHashMap.newKeySet();
        this.wardenStashedOffhand = new ConcurrentHashMap<>();
        this.wardenBothHandsActive = ConcurrentHashMap.newKeySet();
        this.risingFuryActive = ConcurrentHashMap.newKeySet();
        this.risingFuryHitCount = new ConcurrentHashMap<>();
        this.risingFuryTimeoutTasks = new ConcurrentHashMap<>();
        this.shockwaveDamageActive = ConcurrentHashMap.newKeySet();
    }

    /**
     * Warden Gloves boxing ability - Left click to punch.
     * Ability lasts for 20 seconds, 35 second cooldown. Also feeds Rising Fury's landed-hit
     * tracking (no-op if Rising Fury isn't active).
     * <p>
     * The item carries real attack damage so vanilla actually resolves the swing into a damage
     * event - a weapon at 0 attack damage is skipped outright by vanilla's attack path, so
     * nothing here would run at all. Outside Rising Fury the hit is therefore cancelled and
     * dropped entirely: no damage, no knockback, no boxing ability, no speed. Rising Fury is the
     * only state in which the gloves do anything on hit.
     */
    public void useWardenPunch(EntityDamageByEntityEvent event, Player player, Player victim) {
        UUID uuid = player.getUniqueId();

        if (shockwaveDamageActive.contains(uuid)) return;

        if (!risingFuryActive.contains(uuid)) {
            event.setCancelled(true);
            return;
        }

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

        // Rising Fury owns its own infinite Speed - don't strip it out from under a still-active
        // ability just because the shorter boxing window happened to lapse first.
        if (!risingFuryActive.contains(uuid)) {
            CashClashPlayer.removeEffect(player, PotionEffectType.SPEED);
        }

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

            shockwaveDamageActive.add(uuid);
            try {
                target.damage(cfg.getWardenShockwaveDamage(), player);
            } finally {
                shockwaveDamageActive.remove(uuid);
            }

            Vector knockback = toTarget.multiply(cfg.getWardenKnockbackPower()).setY(0.8);
            target.setVelocity(knockback);
            hitCount++;
        }

        Messages.debug(player, "WARDEN_GLOVES: Shockwave hit " + hitCount + " enemies, damage: " + cfg.getWardenShockwaveDamage() + ", cooldown: " + cfg.getWardenShockwaveCooldown() + "s");
        Messages.send(player, "mythic.shockwave-activated");
    }

    // ==================== BOTH-HANDS OFF-HAND STASH ====================

    /**
     * Called on a main-hand slot switch. Deferred a tick because {@code PlayerItemHeldEvent}
     * fires <em>before</em> the held slot actually moves - reconciling inline would read the old
     * main-hand item, conclude the gloves are still held, and immediately re-stash the off-hand
     * and put the cosmetic glove back, leaving a glove visibly stuck in the off-hand after
     * switching away.
     */
    public void onHandSwitch(Player player) {
        SchedulerUtils.runTask(() -> {
            if (player.isOnline()) reconcileBothHands(player);
        });
    }

    /**
     * Self-healing both-hands reconciliation, driven entirely by what's actually in the
     * player's hands right now rather than a one-shot transition diff off a mutable "was this
     * active" flag. That flag alone used to be the only source of truth, so anything that moved
     * the real gloves or the untagged cosmetic without going through {@link #onHandSwitch}
     * (inventory drag/shift-click/number-key hotbar-swap while a GUI is open, dropping the item,
     * etc.) desynced it - the real, PDC-tagged item and the cosmetic could then both end up
     * sitting in the inventory at once, looking like a duplicate. Tagging the cosmetic
     * ({@link Keys#WARDEN_GLOVES_COSMETIC}) and re-deriving state from it plus the current
     * main-hand item on every relevant event closes that gap: whatever triggered the call, this
     * converges the off-hand back to "stashed item" xor "cosmetic" to match the main hand.
     */
    public void reconcileBothHands(Player player) {
        UUID uuid = player.getUniqueId();
        boolean holdingWarden = PDCDetection.getMythic(player.getInventory().getItemInMainHand()) == MythicItem.WARDEN_GLOVES;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        boolean offhandIsCosmetic = isCosmeticGlove(offhand);

        if (holdingWarden && !offhandIsCosmetic) {
            wardenStashedOffhand.put(uuid, offhand.getType() != Material.AIR ? offhand.clone() : new ItemStack(Material.AIR));
            player.getInventory().setItemInOffHand(createPairedGloveCosmetic());
            wardenBothHandsActive.add(uuid);
        } else if (!holdingWarden && offhandIsCosmetic) {
            ItemStack stashed = wardenStashedOffhand.remove(uuid);
            player.getInventory().setItemInOffHand(stashed != null ? stashed : new ItemStack(Material.AIR));
            wardenBothHandsActive.remove(uuid);
            endRisingFury(player);
        }
    }

    private boolean isCosmeticGlove(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(Keys.WARDEN_GLOVES_COSMETIC, PersistentDataType.BYTE);
    }

    /**
     * Whether Warden Gloves' both-hands mode is currently active for a player (main hand holds
     * the real gloves, off-hand holds the cosmetic paired glove). Used to block the vanilla
     * F-key hand swap, which would otherwise silently move the real, PDC-tagged item into the
     * off-hand and leave the untagged cosmetic in the main hand - melee hits with the cosmetic
     * item don't carry the {@code WARDEN_GLOVES} tag, so they'd deal plain vanilla damage with
     * none of this class's punch/Rising Fury logic running, and no debug output either.
     */
    public boolean isBothHandsActive(UUID uuid) {
        return wardenBothHandsActive.contains(uuid);
    }

    private ItemStack createPairedGloveCosmetic() {
        ItemStack item = new ItemStack(MythicItem.WARDEN_GLOVES.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.parse("<light_purple><bold>Warden Gloves</bold></light_purple>"));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(Keys.WARDEN_GLOVES_COSMETIC, PersistentDataType.BYTE, (byte) 1);
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
        applyRisingFuryAttributes(player, 0);

        // Infinite rather than a fixed duration: Rising Fury ends on a no-hit timeout or a weapon
        // swap, not on a clock we could pre-compute here, so endRisingFury owns the removal.
        CashClashPlayer.applyEffect(player, PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, true);

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
        BukkitTask task = SchedulerUtils.runTaskLater(() -> endRisingFury(player), timeoutTicks);
        risingFuryTimeoutTasks.put(uuid, task);
        manager.trackTask(uuid, task);

        TimerDisplayUtils.startCountdownTimer(player, timeoutTicks * 50L, PRIORITY_RISING_FURY_TIMER,
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
        int hitsPerStack = cfg.getWardenRisingFuryHitsPerStack();
        int maxHits = maxStacks * hitsPerStack;
        int previousHits = risingFuryHitCount.getOrDefault(uuid, 0);
        int hitCount = Math.min(maxHits, previousHits + 1);
        risingFuryHitCount.put(uuid, hitCount);
        int stacks = hitCount / hitsPerStack;

        applyRisingFuryAttributes(player, stacks);

        // Announce only on the hit that actually crosses a stack boundary, not on every third
        // hit once the counter has already been clamped at max.
        if (stacks > previousHits / hitsPerStack) {
            announceReachGain(player, stacks, maxStacks);
        }

        if (stacks >= maxStacks) {
            tryBreakShield(victim);
        }
    }

    /**
     * Feedback for a reach stack landing - a distinct message and a higher cue at the cap, so the
     * player can tell "another stack" from "fully stacked" without counting hits.
     */
    private void announceReachGain(Player player, int stacks, int maxStacks) {
        String reach = String.format("%.2f", cfg.getWardenRisingFuryReachPerStack() * stacks);

        if (stacks >= maxStacks) {
            Messages.send(player, "mythic.warden-rising-fury-reach-maxed", "{reach}", reach);
            SoundUtils.play(player, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.4f);
        } else {
            Messages.send(player, "mythic.warden-rising-fury-reach-gained",
                    "{stacks}", String.valueOf(stacks),
                    "{max_stacks}", String.valueOf(maxStacks),
                    "{reach}", reach);
            SoundUtils.play(player, Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.0f + (0.2f * stacks));
        }
    }

    private void tryBreakShield(Player victim) {
        if (victim == null || !victim.isOnline() || !victim.isBlocking()) return;

        victim.setCooldown(Material.SHIELD, cfg.getWardenShieldDisableTicks());
        Messages.send(victim, "mythic.warden-shield-broken");
        SoundUtils.play(victim, Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f);
    }

    /**
     * Ends Rising Fury, clearing the stacked reach bonus. Notifies the player either way -
     * whether it timed out naturally or was cancelled by swapping away from the gloves -
     * so deactivation is never silent.
     */
    private void endRisingFury(Player player) {
        UUID uuid = player.getUniqueId();
        if (!risingFuryActive.remove(uuid)) return;

        risingFuryHitCount.remove(uuid);
        BukkitTask task = risingFuryTimeoutTasks.remove(uuid);
        if (task != null && !task.isCancelled()) task.cancel();
        TimerDisplayUtils.stopCountdownTimer(player);
        CashClashPlayer.removeEffect(player, PotionEffectType.SPEED);

        // No-ops safely if the player already swapped away from the gloves.
        applyRisingFuryAttributes(player, 0);

        if (player.isOnline()) {
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
     * Applies (or clears, at {@code stacks == 0}) Rising Fury's reach-stack modifiers by
     * re-issuing the held ItemStack's meta - attribute modifiers live on the item itself, not a
     * runtime AttributeInstance. Damage/speed are untouched here; they're the item's permanent
     * diamond-sword-equivalent baseline set once at creation. No-ops if the player is no longer
     * actually holding Warden Gloves in their main hand.
     */
    private void applyRisingFuryAttributes(Player player, int stacks) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (PDCDetection.getMythic(item) != MythicItem.WARDEN_GLOVES) return;

        ItemMeta meta = item.getItemMeta();
        meta.removeAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE);

        double reachPerStack = cfg.getWardenRisingFuryReachPerStack();
        for (int i = 0; i < stacks && i < RISING_FURY_REACH_STACK_KEYS.length; i++) {
            meta.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE, new AttributeModifier(
                    RISING_FURY_REACH_STACK_KEYS[i], reachPerStack, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
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
        shockwaveDamageActive.clear();
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
        TimerDisplayUtils.stopCountdownTimer(player);

        // Restore whatever was stashed rather than just dropping the tracking - otherwise the
        // stashed item is silently lost and the cosmetic is left stuck in the off-hand.
        if (wardenBothHandsActive.remove(uuid) && player.isOnline()) {
            ItemStack stashed = wardenStashedOffhand.remove(uuid);
            if (isCosmeticGlove(player.getInventory().getItemInOffHand())) {
                player.getInventory().setItemInOffHand(stashed != null ? stashed : new ItemStack(Material.AIR));
            }
        } else {
            wardenStashedOffhand.remove(uuid);
        }
    }
}
