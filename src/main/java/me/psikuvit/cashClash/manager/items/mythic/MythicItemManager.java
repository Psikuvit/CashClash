package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.items.CustomModelDataMapper;
import me.psikuvit.cashClash.util.items.ItemFactory;
import me.psikuvit.cashClash.util.items.PDCSetter;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Registry of every mythic (legendary) item's behaviour, plus the game-wide
 * ownership and shop lifecycle. Each mythic item is handled by its own
 * {@link MythicItemHandler}; the manager looks handlers up by item or handler
 * type and fans out cleanup to every handler.
 *
 * <p>Purchase/ownership rules: each player can only purchase ONE mythic per
 * game, and each mythic can only be purchased ONCE per game (globally).</p>
 */
public class MythicItemManager {

    private final ItemsConfig cfg;
    private final CooldownManager cooldownManager;

    private final Map<UUID, Map<UUID, MythicItem>> playerMythics;
    private final Map<UUID, Set<MythicItem>> sessionPurchasedMythics;
    private final Map<UUID, List<MythicItem>> sessionAvailableMythics;

    // Active tasks across all handlers, cancelled on per-player and global cleanup
    private final Map<UUID, List<BukkitTask>> activeTasks;

    // Handler registry - one handler per mythic item
    private final Map<MythicItem, MythicItemHandler> handlers;
    private final List<MythicItemHandler> allHandlers;

    private final ItemFactory itemFactory;

    public MythicItemManager(ItemsConfig itemsConfig, CooldownManager cooldownManager, ItemFactory itemFactory) {
        this.cfg = itemsConfig;
        this.cooldownManager = cooldownManager;
        this.itemFactory = itemFactory;

        playerMythics = new ConcurrentHashMap<>();
        sessionPurchasedMythics = new ConcurrentHashMap<>();
        sessionAvailableMythics = new ConcurrentHashMap<>();
        activeTasks = new ConcurrentHashMap<>();

        handlers = new EnumMap<>(MythicItem.class);
        allHandlers = new ArrayList<>();

        register(CarlsBattleaxeHandler::new, MythicItem.CARLS_BATTLEAXE);
        register(WindBowHandler::new, MythicItem.WIND_BOW);
        register(ElectricEelHandler::new, MythicItem.ELECTRIC_EEL_SWORD);
        register(GoblinSpearHandler::new, MythicItem.GOBLIN_SPEAR);
        register(BloodwrenchHandler::new, MythicItem.BLOODWRENCH_CROSSBOW);
        register(WardenGlovesHandler::new, MythicItem.WARDEN_GLOVES);
        register(BlazebiteHandler::new, MythicItem.BLAZEBITE_CROSSBOWS);
        register(AlchemistWandHandler::new, MythicItem.ALCHEMIST_WAND);
    }

    private void register(java.util.function.Function<MythicItemManager, ? extends MythicItemHandler> factory, MythicItem item) {
        MythicItemHandler handler = factory.apply(this);
        allHandlers.add(handler);
        handlers.put(item, handler);
    }

    /**
     * Looks up the handler for a mythic item.
     *
     * @throws IllegalArgumentException when no handler is registered for the item
     */
    public MythicItemHandler getHandler(MythicItem item) {
        MythicItemHandler handler = handlers.get(item);
        if (handler == null) {
            throw new IllegalArgumentException("No mythic handler registered for " + item);
        }
        return handler;
    }

    /**
     * Looks up a handler by its concrete type, e.g. {@code getHandler(WindBowHandler.class)}.
     *
     * @throws IllegalArgumentException when no handler of that type is registered
     */
    public <T extends MythicItemHandler> T getHandler(Class<T> type) {
        for (MythicItemHandler handler : allHandlers) {
            if (type.isInstance(handler)) {
                return type.cast(handler);
            }
        }
        throw new IllegalArgumentException("No mythic handler registered for " + type.getSimpleName());
    }

    /**
     * Every registered handler, for cross-cutting fan-out (cleanup).
     */
    public Collection<MythicItemHandler> getHandlers() {
        return Collections.unmodifiableList(allHandlers);
    }

    // ---- Shared dependencies handed to every handler ----

    ItemsConfig getCfg() {
        return cfg;
    }

    CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    /**
     * Registers a scheduled task to be cancelled when the owning player quits
     * or dies, or when the plugin shuts down.
     */
    void trackTask(UUID uuid, BukkitTask task) {
        activeTasks.computeIfAbsent(uuid, k -> new ArrayList<>()).add(task);
    }

    // ==================== PURCHASE & OWNERSHIP ====================

    /**
     * Check if a player has already purchased a mythic (only one per player per game).
     * @return true if player already owns a mythic
     */
    public boolean hasPlayerPurchasedMythic(GameSession session, UUID playerUuid) {
        if (session == null || playerUuid == null) return false;
        UUID sessionId = session.getSessionId();
        Map<UUID, MythicItem> sessionPlayerMythics = playerMythics.get(sessionId);
        if (sessionPlayerMythics == null) return false;
        return sessionPlayerMythics.containsKey(playerUuid);
    }

    /**
     * Check if a specific mythic has been purchased by anyone.
     * @return true if mythic is already purchased
     */
    public boolean isMythicPurchased(GameSession session, MythicItem mythic) {
        if (session == null || mythic == null) return false;
        UUID sessionId = session.getSessionId();
        Set<MythicItem> purchased = sessionPurchasedMythics.get(sessionId);
        if (purchased == null) return false;
        return purchased.contains(mythic);
    }

    /**
     * Get the player who owns a specific mythic, if any.
     */
    public UUID getMythicOwner(GameSession session, MythicItem mythic) {
        if (session == null || mythic == null) return null;
        UUID sessionId = session.getSessionId();

        Map<UUID, MythicItem> sessionPlayerMythics = playerMythics.get(sessionId);
        if (sessionPlayerMythics == null) return null;

        for (Map.Entry<UUID, MythicItem> entry : sessionPlayerMythics.entrySet()) {
            if (entry.getValue() == mythic) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Register that a player has purchased a mythic.
     */
    public void registerMythicPurchase(GameSession session, UUID playerUuid, MythicItem mythic) {
        if (session == null || playerUuid == null || mythic == null) return;
        UUID sessionId = session.getSessionId();
        playerMythics.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                   .put(playerUuid, mythic);
        sessionPurchasedMythics.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                               .add(mythic);
    }

    /**
     * Get the mythic owned by a player, if any.
     */
    public MythicItem getPlayerMythic(GameSession session, UUID playerUuid) {
        if (session == null || playerUuid == null) return null;
        UUID sessionId = session.getSessionId();

        Map<UUID, MythicItem> sessionPlayerMythics = playerMythics.get(sessionId);
        if (sessionPlayerMythics == null) return null;
        return sessionPlayerMythics.getOrDefault(playerUuid, null);
    }

    // ==================== RANDOM LEGENDARY SELECTION ====================

    /**
     * Select 5 random legendaries for a game session.
     * Should be called when the game starts.
     */
    public void selectLegendariesForSession(GameSession session) {
        if (session == null) return;
        UUID sessionId = session.getSessionId();

        // Get all mythic items and shuffle them
        List<MythicItem> allMythics = new ArrayList<>(Arrays.asList(MythicItem.values()));
        Collections.shuffle(allMythics, ThreadLocalRandom.current());

        // Select first 5 (or all if less than 5 exist)
        int count = Math.min(cfg.getLegendsPerGame(), allMythics.size());
        List<MythicItem> selectedMythics = new ArrayList<>(allMythics.subList(0, count));

        sessionAvailableMythics.put(sessionId, selectedMythics);

        Messages.debug("MYTHIC", "Selected " + count + " legendaries for session " + sessionId + ": " +
            selectedMythics.stream().map(MythicItem::getDisplayName).reduce((a, b) -> a + ", " + b).orElse("none")
        );
    }

    /**
     * Get the available legendaries for a game session.
     * Returns an empty list if none have been selected.
     */
    public List<MythicItem> getAvailableMythics(GameSession session) {
        if (session == null) return Collections.emptyList();
        UUID sessionId = session.getSessionId();
        return sessionAvailableMythics.getOrDefault(sessionId, Collections.emptyList());
    }

    /**
     * Create the mythic item with proper tags and appearance.
     */
    public ItemStack createMythicItem(MythicItem mythic, Player owner) {
        ItemStack item = new ItemStack(mythic.getMaterial());

        PDCSetter tags = PDCSetter.of(item);

        // Display name with mythic color
        tags.meta().displayName(Messages.parse("<light_purple><bold>" + mythic.getDisplayName() + "</bold></light_purple>"));

        // Lore
        List<Component> lore = itemFactory.getGameplayFactory().getConfiguredLore(mythic);
        if (!lore.isEmpty()) {
            tags.meta().lore(lore);
        }

        // PDC tags
        tags.set(Keys.ITEM_ID, PersistentDataType.STRING, mythic.name());
        tags.set(Keys.ITEM_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());

        // Apply special attributes based on mythic type
        applyMythicAttributes(mythic, tags.meta());

        tags.meta().setUnbreakable(true);
        tags.meta().addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        tags.apply();

        // Apply custom model data using string key for resource pack
        CustomModelDataMapper.applyCustomModel(item, mythic);

        return item;
    }

    /**
     * Player attribute bases that item modifiers stack on top of. Needed because the modifiers
     * written here are absolute, not deltas - see {@link #addAttackModifiers}.
     */
    private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;
    private static final double PLAYER_BASE_ATTACK_SPEED = 4.0;

    /**
     * Each melee mythic (Carl's Battleaxe, Electric Eel Sword, Goblin Spear) keeps its own
     * vanilla base-material stats - axe, sword, and trident respectively - rather than being
     * normalized to a common baseline; only Warden Gloves is explicitly pulled down to
     * diamond-sword-equivalent (7.0 damage / 1.6 speed), since its base material (Netherite
     * Sword) would otherwise hit harder than an unarmed-style weapon should.
     */
    private static final double DIAMOND_SWORD_DAMAGE = 7.0;
    private static final double DIAMOND_SWORD_SPEED = 1.6;

    private void applyMythicAttributes(MythicItem mythic, ItemMeta meta) {
        switch (mythic) {
            case CARLS_BATTLEAXE ->
                // Netherite Axe stats.
                    addAttackModifiers(meta, Keys.MYTHIC_CARLS_BATTLEAXE_DAMAGE, Keys.MYTHIC_CARLS_BATTLEAXE_SPEED, 10.0, 1.0);
            case ELECTRIC_EEL_SWORD ->
                // Diamond Sword stats.
                    addAttackModifiers(meta, Keys.MYTHIC_ELECTRIC_EEL_DAMAGE, Keys.MYTHIC_ELECTRIC_EEL_SPEED, DIAMOND_SWORD_DAMAGE, DIAMOND_SWORD_SPEED);
            case GOBLIN_SPEAR -> {
                // Trident stats.
                addAttackModifiers(meta, Keys.MYTHIC_GOBLIN_SPEAR_DAMAGE, Keys.MYTHIC_GOBLIN_SPEAR_SPEED, 9.0, 1.1);

                // Loyalty makes the thrown spear return to the wielder - kept.
                meta.addEnchant(Enchantment.LOYALTY, 3, true);
            }
            case WARDEN_GLOVES ->
                // Netherite Sword base pulled down to diamond-sword-equivalent; Rising Fury only
                // adds reach/shield-break on top.
                    addAttackModifiers(meta, Keys.MYTHIC_WARDEN_GLOVES_DAMAGE, Keys.MYTHIC_WARDEN_GLOVES_SPEED, DIAMOND_SWORD_DAMAGE, DIAMOND_SWORD_SPEED);
            case BLOODWRENCH_CROSSBOW -> {
                // No enchantments - mode system handles functionality
            }
            case WIND_BOW -> // Legendary bow gets Power 3
                    meta.addEnchant(Enchantment.POWER, 3, true);
            case BLAZEBITE_CROSSBOWS -> {
                // No enchantments - Glacier/Magma Storm effects are handled entirely in the hit
                // handler, based on what the arrow hits.
            }
            default -> {
            }
        }
    }

    /**
     * Adds mainhand ATTACK_DAMAGE/ATTACK_SPEED modifiers under stable, item-specific keys.
     * <p>
     * {@code totalDamage}/{@code totalSpeed} are the weapon's full effective stats, not deltas:
     * writing any attribute modifier onto an ItemMeta replaces the base material's own default
     * modifiers outright, so a netherite sword carrying one modifier no longer contributes its
     * native +7 damage. Each value is therefore emitted relative to the bare player attribute.
     */
    private void addAttackModifiers(ItemMeta meta, NamespacedKey damageKey, NamespacedKey speedKey, double totalDamage, double totalSpeed) {
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                damageKey, totalDamage - PLAYER_BASE_ATTACK_DAMAGE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                speedKey, totalSpeed - PLAYER_BASE_ATTACK_SPEED, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
    }

    /**
     * Check if a specific mythic is available in this session.
     */
    public boolean isUnavailable(GameSession session, MythicItem mythic) {
        if (session == null || mythic == null) return true;
        List<MythicItem> available = getAvailableMythics(session);
        return !available.contains(mythic);
    }

    // ==================== CLEANUP ====================

    /**
     * Clean up all state for a specific player (on death or quit).
     */
    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();

        // Release each handler's per-player state
        for (MythicItemHandler handler : allHandlers) {
            handler.cleanupPlayer(player);
        }

        // Cancel player tasks
        List<BukkitTask> tasks = activeTasks.remove(uuid);
        if (tasks != null) {
            tasks.forEach(task -> {
                if (task != null && !task.isCancelled()) task.cancel();
            });
        }
    }

    /**
     * Clean up all state on plugin shutdown.
     */
    public void cleanup() {
        CashClashPlugin.getInstance().getLogger().info("[MythicItemManager] Cleaning up all mythic data...");

        // Clear session data
        playerMythics.clear();
        sessionPurchasedMythics.clear();
        sessionAvailableMythics.clear();

        // Clear each handler's state
        for (MythicItemHandler handler : allHandlers) {
            handler.cleanup();
        }

        // Cancel all active tasks
        activeTasks.values().forEach(tasks -> tasks.forEach(task -> {
            if (task != null && !task.isCancelled()) task.cancel();
        }));
        activeTasks.clear();

        CashClashPlugin.getInstance().getLogger().info("[MythicItemManager] Cleanup complete");
    }
}
