package me.psikuvit.cashClash.player;
 
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.kit.Kit;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.enums.BonusType;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

/**
 * Wrapper for player data within a Cash Clash game
 */
public class CashClashPlayer {

    private final UUID uuid;
    private final Player player;

    // Economy
    private long coins;
    private long investedCoins;
    private Investment currentInvestment;
    private int deathsThisRound;

    // Combat stats
    private Kit currentKit;
    private int killStreak;
    private int totalKills;
    private boolean hasFirstBlood;
    private final Map<BonusType, Integer> bonusesEarned;

    // Round tracking
    private int lives;
    private UUID firstDeathRound;

    // Special items
    private int revivalStarsUsed;
    private final Deque<PurchaseRecord> purchaseHistory;
    private long respawnProtectionUntil;

    private final Map<EnchantEntry, Integer> ownedEnchants;

    // Health management
    private double healthModifier = 0.0; // Tracks additional health from buffs (e.g. +2 for +1 heart)

    // Potion effect tracking
    private final Map<PotionEffectType, PotionEffect> trackedEffects = new HashMap<>();

    // Invisibility support - visible equipment (armor + both hands) stashed here while an
    // ability like Invis Cloak is active, so nothing gives the player's position away;
    // restored on deactivation/death/disconnect.
    private ItemStack[] hiddenArmorContents;
    private ItemStack hiddenMainHand;
    private ItemStack hiddenOffHand;
    private boolean inventoryHidden;

    public CashClashPlayer(Player player) {
        this.uuid = player.getUniqueId();
        this.player = player;
        this.coins = 0;
        this.investedCoins = 0;
        this.lives = 3;
        this.bonusesEarned = new HashMap<>();
        this.purchaseHistory = new ArrayDeque<>();
        this.respawnProtectionUntil = 0L;
        this.ownedEnchants = new HashMap<>();
    }

    public void initializeRound1() {
        this.coins = 10000;
        this.lives = 99999; // Infinite lives
        this.deathsThisRound = 0;
    }

    public void initializeRound(int roundNumber) {
        switch (roundNumber) {
            case 2, 3, 4 -> this.coins += 30000;
            case 5, 6, 7 -> {
                if (this.coins < 20000) {
                    this.coins += 10000;
                }
            }
        }

        // Reset round-specific stats
        this.lives = 99999; // Infinite lives
        this.deathsThisRound = 0;
        this.killStreak = 0;
        this.hasFirstBlood = false;

        // Clear enchants to prevent carryover
        this.ownedEnchants.clear();

        // Reset health modifier
        resetHealthModifier();
    }

    public boolean canAfford(long amount) {
        return coins >= amount;
    }

    public void deductCoins(long amount) {
        this.coins -= amount;
        Messages.debug(player, "ECONOMY", "Deducted $" + amount + " (Total: $" + this.coins + ")");
    }

    public void addCoins(long amount) {
        coins += amount;
        // Track lifetime coins earned for the leaderboard (excludes refunds/admin grants)
        PlayerDataManager manager = PlayerDataManager.getInstance();
        if (manager != null) manager.addEarnedCoins(uuid, amount);
        Messages.debug(player, "ECONOMY", "Added $" + amount + " (Total: $" + this.coins + ")");
    }

    /**
     * Adds coins without counting them as "earned" for the leaderboard.
     * Used for refunds and admin coin grants.
     */
    public void addCoinsSilently(long amount) {
        coins += amount;
        Messages.debug(player, "ECONOMY", "Added $" + amount + " silently (Total: $" + this.coins + ")");
    }

    public void handleDeath() {
        lives--;
        deathsThisRound++;
        killStreak = 0;

        // Handle investment penalties
        if (currentInvestment != null) {
            currentInvestment.recordDeath();
        }
    }

    public void handleKill() {
        totalKills++;
        killStreak++;
    }

    public void setOwnedEnchantLevel(EnchantEntry enchant, int level) {
        ownedEnchants.put(enchant, level);
    }

    public int getOwnedEnchantLevel(EnchantEntry enchant) {
        return ownedEnchants.getOrDefault(enchant, 0);
    }

    public Map<EnchantEntry, Integer> getOwnedEnchants() { return Map.copyOf(ownedEnchants); }

    public void addPurchase(PurchaseRecord record) {
        if (record == null) return;
        purchaseHistory.addLast(record);
    }

    public void popLastPurchase() {
        purchaseHistory.pollLast();
    }

    public PurchaseRecord peekLastPurchase() {
        PurchaseRecord record = purchaseHistory.peekLast();
        if (record != null && record.item().getCategory() == ShopCategory.ENCHANTS) return null;
        return record;
    }

    public Queue<PurchaseRecord> getPurchaseHistory() {
        return purchaseHistory;
    }

    public void earnBonus(BonusType type) {
        bonusesEarned.put(type, bonusesEarned.getOrDefault(type, 0) + 1);
        addCoins(type.getReward());
    }

    public void setRespawnProtection(long millisFromNow) {
        this.respawnProtectionUntil = System.currentTimeMillis() + millisFromNow;
    }

    public boolean isRespawnProtected() {
        return System.currentTimeMillis() < this.respawnProtectionUntil;
    }

    public long getRespawnProtectionUntil() {
        return respawnProtectionUntil;
    }

    // Health Management System
    /**
     * Get the maximum health for this player (base 20 + modifiers)
     */
    public double getMaxHealth() {
        return 20.0 + healthModifier;
    }

    /**
     * Add to the current health modifier
     * @param amount health to add
     */
    public void addHealthModifier(double amount) {
        this.healthModifier += amount;
        applyHealth();
    }

    /**
     * Remove from the current health modifier
     * @param amount health to remove
     */
    public void removeHealthModifier(double amount) {
        this.healthModifier = Math.max(0.0, healthModifier - amount);
        applyHealth();
    }

    /**
     * Apply the current health configuration to the player
     * Sets max health and heals player to max
     */
    public void applyHealth() {
        if (player == null || !player.isOnline()) return;

        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double maxHealth = getMaxHealth();
            maxHealthAttr.setBaseValue(maxHealth);
            // This prevents infinite recursion from death events
            if (player.getHealth() > 0) {
                player.setHealth(Math.min(player.getHealth(), maxHealth));
            }
            Messages.debug(player, "HEALTH", "Set max health to " + maxHealth);
        }
    }

    /**
     * Reset health modifier to 0 and apply (resets to base 20)
     */
    public void resetHealthModifier() {
        this.healthModifier = 0.0;
        applyHealth();
    }

    /**
     * Reset a player's max health back to the vanilla default (20) and heal them to full.
     * Used outside a game session (e.g. the lobby), where no CashClashPlayer exists, so all
     * max-health resets still go through this class instead of touching the attribute directly.
     */
    public static void resetToDefaultHealth(Player player) {
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(20.0);
        }
        player.setHealth(20.0);
    }

    /**
     * Get current health modifier amount
     */
    public double getHealthModifier() {
        return healthModifier;
    }

    /**
     * Set a specific health modifier (overwrites current)
     * Base health is always 20, this adds to it
     * @param amount health to add (e.g., 2.0 for +1 heart)
     */
    public void setHealthModifier(double amount) {
        this.healthModifier = Math.max(0.0, amount); // Don't allow negative modifiers
        applyHealth();
    }

    /**
     * Heals the player by the given amount, clamped to their max health.
     * @return the amount of health actually restored (less than {@code amount} when the
     *         player was already within {@code amount} of full health)
     */
    public double heal(double amount) {
        if (player == null || !player.isOnline() || amount <= 0) return 0.0;
        double maxHealth = getMaxHealth();
        double healed = Math.min(amount, Math.max(0.0, maxHealth - player.getHealth()));
        if (healed > 0) {
            player.setHealth(player.getHealth() + healed);
        }
        return healed;
    }

    /**
     * Sets the player's health directly, clamped to [0, max health].
     */
    public void setHealth(double health) {
        if (player == null || !player.isOnline()) return;
        player.setHealth(Math.min(Math.max(0.0, health), getMaxHealth()));
    }

    /**
     * Heals the player back up to their full max health.
     */
    public void healToFull() {
        heal(Double.MAX_VALUE);
    }

    // ================= Potion Effect Management =================

    /**
     * Apply a potion effect and track it as plugin-applied so it can be
     * selectively cleared later via {@link #clearPluginEffects()}.
     */
    public void applyEffect(PotionEffect effect) {
        if (effect == null || player == null) return;
        player.addPotionEffect(effect);
        trackedEffects.put(effect.getType(), effect);
    }

    /**
     * Apply a potion effect with default flags (no ambient, shows particles).
     */
    public void applyEffect(PotionEffectType type, int durationTicks, int amplifier) {
        applyEffect(new PotionEffect(type, durationTicks, amplifier, false, false));
    }

    /**
     * Apply a potion effect with explicit ambient/particle flags.
     */
    public void applyEffect(PotionEffectType type, int durationTicks, int amplifier, boolean ambient, boolean particles) {
        applyEffect(new PotionEffect(type, durationTicks, amplifier, ambient, particles));
    }

    /**
     * Apply a potion effect with explicit ambient/particle/icon flags.
     */
    public void applyEffect(PotionEffectType type, int durationTicks, int amplifier, boolean ambient, boolean particles, boolean icon) {
        applyEffect(new PotionEffect(type, durationTicks, amplifier, ambient, particles, icon));
    }

    /**
     * Remove a potion effect (tracked or not) and stop tracking it.
     */
    public void removeEffect(PotionEffectType type) {
        if (type == null || player == null) return;
        player.removePotionEffect(type);
        trackedEffects.remove(type);
    }

    /**
     * Whether the player currently has an active potion effect of this type.
     */
    public boolean hasEffect(PotionEffectType type) {
        return player != null && player.hasPotionEffect(type);
    }

    /**
     * Get the active potion effect of this type, or null.
     */
    public PotionEffect getEffect(PotionEffectType type) {
        return player == null ? null : player.getPotionEffect(type);
    }

    /**
     * Clear only the effects the plugin has applied (tracked), leaving
     * vanilla/other effects untouched. Used on death/round reset.
     */
    public void clearPluginEffects() {
        if (player == null) return;
        for (PotionEffectType type : trackedEffects.keySet()) {
            player.removePotionEffect(type);
        }
        trackedEffects.clear();
    }

    /**
     * Clear every active potion effect on the player (full wipe) and stop tracking.
     */
    public void clearAllEffects() {
        if (player == null) return;
        player.getActivePotionEffects().stream()
                .map(PotionEffect::getType)
                .forEach(player::removePotionEffect);
        trackedEffects.clear();
    }

    /**
     * Hides the player's visible equipment (armor + both hands) for an invisibility-style
     * ability - stashes it here and clears the live slots so nothing gives their position
     * away. No-op if already hidden, so a repeat call can't clobber a saved snapshot with
     * empty slots.
     */
    public void hideInventory() {
        hideInventory(true);
    }

    /**
     * Same as {@link #hideInventory()}, but lets the caller leave the main-hand item alone -
     * needed by an ability whose own toggle-off interaction depends on the player still
     * holding it (e.g. Invis Cloak), where clearing the main hand would make it un-toggleable.
     */
    public void hideInventory(boolean hideMainHand) {
        if (inventoryHidden || player == null) return;

        PlayerInventory inv = player.getInventory();
        hiddenArmorContents = cloneItems(inv.getArmorContents());
        hiddenOffHand = inv.getItemInOffHand().clone();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);

        if (hideMainHand) {
            hiddenMainHand = inv.getItemInMainHand().clone();
            inv.setItemInMainHand(null);
        }
        inventoryHidden = true;
    }

    /**
     * Restores whatever {@link #hideInventory()} stashed. No-op if nothing is hidden.
     */
    public void restoreInventory() {
        if (!inventoryHidden || player == null) return;

        PlayerInventory inv = player.getInventory();
        if (hiddenArmorContents != null) inv.setArmorContents(hiddenArmorContents);
        if (hiddenMainHand != null) inv.setItemInMainHand(hiddenMainHand);
        if (hiddenOffHand != null) inv.setItemInOffHand(hiddenOffHand);

        hiddenArmorContents = null;
        hiddenMainHand = null;
        hiddenOffHand = null;
        inventoryHidden = false;
    }

    /**
     * Whether this player's equipment is currently stashed via {@link #hideInventory()}.
     */
    public boolean isInventoryHidden() {
        return inventoryHidden;
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] != null ? items[i].clone() : null;
        }
        return copy;
    }

    /**
     * Hides a player's visible equipment if they're in a session; a no-op otherwise, since
     * there is no session-scoped instance to hold the snapshot for a later restore.
     */
    public static void hideInventory(Player player) {
        CashClashPlayer ccp = from(player);
        if (ccp != null) ccp.hideInventory();
    }

    /**
     * Same as {@link #hideInventory(Player)}, but lets the caller leave the main-hand item
     * alone - see {@link #hideInventory(boolean)}.
     */
    public static void hideInventory(Player player, boolean hideMainHand) {
        CashClashPlayer ccp = from(player);
        if (ccp != null) ccp.hideInventory(hideMainHand);
    }

    /**
     * Restores a player's equipment previously hidden by {@link #hideInventory(Player)}.
     */
    public static void restoreInventory(Player player) {
        CashClashPlayer ccp = from(player);
        if (ccp != null) ccp.restoreInventory();
    }

    /**
     * Types currently tracked as plugin-applied.
     */
    public Map<PotionEffectType, PotionEffect> getTrackedEffects() {
        return Map.copyOf(trackedEffects);
    }

    // ---------- Static convenience (resolve the session's CashClashPlayer) ----------

    /**
     * Resolve the CashClashPlayer wrapper for a Bukkit player, or null if they are
     * not currently inside a game session.
     */
    public static CashClashPlayer from(Player player) {
        if (player == null) return null;
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        return session == null ? null : session.getCashClashPlayer(player.getUniqueId());
    }

    /**
     * Apply a tracked potion effect to a player if they are in a session;
     * otherwise fall back to a direct, untracked application.
     */
    public static void applyEffect(Player player, PotionEffect effect) {
        CashClashPlayer ccp = from(player);
        if (ccp != null) ccp.applyEffect(effect);
        else if (player != null) player.addPotionEffect(effect);
    }

    /**
     * Apply a tracked potion effect (default flags) to a player if they are in a session.
     */
    public static void applyEffect(Player player, PotionEffectType type, int durationTicks, int amplifier) {
        applyEffect(player, new PotionEffect(type, durationTicks, amplifier, false, false));
    }

    /**
     * Apply a tracked potion effect with explicit ambient/particle flags.
     */
    public static void applyEffect(Player player, PotionEffectType type, int durationTicks, int amplifier, boolean ambient, boolean particles) {
        applyEffect(player, new PotionEffect(type, durationTicks, amplifier, ambient, particles));
    }

    /**
     * Apply a tracked potion effect with explicit ambient/particle/icon flags.
     */
    public static void applyEffect(Player player, PotionEffectType type, int durationTicks, int amplifier, boolean ambient, boolean particles, boolean icon) {
        applyEffect(player, new PotionEffect(type, durationTicks, amplifier, ambient, particles, icon));
    }

    /**
     * Remove a potion effect from a player, tracked if in a session.
     */
    public static void removeEffect(Player player, PotionEffectType type) {
        CashClashPlayer ccp = from(player);
        if (ccp != null) ccp.removeEffect(type);
        else if (player != null) player.removePotionEffect(type);
    }

    /**
     * Whether a player currently has an active potion effect of this type.
     */
    public static boolean hasEffect(Player player, PotionEffectType type) {
        CashClashPlayer ccp = from(player);
        if (ccp != null) return ccp.hasEffect(type);
        return player != null && player.hasPotionEffect(type);
    }

    /**
     * Get a player's active potion effect of this type, or null.
     */
    public static PotionEffect getEffect(Player player, PotionEffectType type) {
        CashClashPlayer ccp = from(player);
        if (ccp != null) return ccp.getEffect(type);
        return player == null ? null : player.getPotionEffect(type);
    }

    /**
     * Clear plugin-applied effects for a player, tracked if in a session.
     */
    public static void clearPluginEffects(Player player) {
        CashClashPlayer ccp = from(player);
        if (ccp != null) ccp.clearPluginEffects();
        else if (player != null) {
            player.getActivePotionEffects().stream()
                    .map(PotionEffect::getType)
                    .forEach(player::removePotionEffect);
        }
    }

    /**
     * Clear all active effects for a player, tracked if in a session.
     */
    public static void clearAllEffects(Player player) {
        CashClashPlayer ccp = from(player);
        if (ccp != null) ccp.clearAllEffects();
        else if (player != null) {
            player.getActivePotionEffects().stream()
                    .map(PotionEffect::getType)
                    .forEach(player::removePotionEffect);
        }
    }

    /**
     * Heal a player through the centralized health system (clamped to max health).
     * Falls back to clamping against the vanilla 20 health when the player is not
     * inside a game session.
     * @return the amount of health actually restored
     */
    public static double heal(Player player, double amount) {
        CashClashPlayer ccp = from(player);
        if (ccp != null) return ccp.heal(amount);
        if (player == null || !player.isOnline() || amount <= 0) return 0.0;
        double healed = Math.min(amount, Math.max(0.0, 20.0 - player.getHealth()));
        if (healed > 0) {
            player.setHealth(player.getHealth() + healed);
        }
        return healed;
    }

    /**
     * Set a player's health through the centralized health system, clamped to [0, max health].
     * Falls back to clamping against the vanilla 20 health outside a game session.
     */
    public static void setHealth(Player player, double health) {
        CashClashPlayer ccp = from(player);
        if (ccp != null) {
            ccp.setHealth(health);
            return;
        }
        if (player == null || !player.isOnline()) return;
        player.setHealth(Math.min(Math.max(0.0, health), 20.0));
    }

    /**
     * Heal a player back up to full max health through the centralized health system.
     * Falls back to the vanilla 20 health outside a game session.
     */
    public static void healToFull(Player player) {
        CashClashPlayer ccp = from(player);
        if (ccp != null) {
            ccp.healToFull();
            return;
        }
        if (player != null && player.isOnline()) {
            player.setHealth(20.0);
        }
    }

    public UUID getUuid() {
        return uuid;
    }

    public Player getPlayer() {
        return player;
    }

    public long getCoins() {
        return coins;
    }

    public void setCoins(long coins) {
        this.coins = coins;
    }

    public long getInvestedCoins() {
        return investedCoins;
    }

    public void setInvestedCoins(long invested) {
        this.investedCoins = invested;
    }

    public Investment getCurrentInvestment() {
        return currentInvestment;
    }

    public void setCurrentInvestment(Investment investment) {
        this.currentInvestment = investment;
    }

    public int getDeathsThisRound() {
        return deathsThisRound;
    }

    public Kit getCurrentKit() {
        return currentKit;
    }

    public void setCurrentKit(Kit kit) {
        this.currentKit = kit;
    }

    public int getKillStreak() {
        return killStreak;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public boolean hasFirstBlood() {
        return hasFirstBlood;
    }

    public void setFirstBlood(boolean firstBlood) {
        this.hasFirstBlood = firstBlood;
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = lives;
    }

    public int getRevivalStarsUsed() {
        return revivalStarsUsed;
    }

    public void incrementRevivalStarsUsed() {
        this.revivalStarsUsed++;
    }

    public UUID getFirstDeathRound() {
        return firstDeathRound;
    }

    public void setFirstDeathRound(UUID round) {
        this.firstDeathRound = round;
    }
    
    public Map<BonusType, Integer> getBonusesEarned() {
        return bonusesEarned;
    }
}
