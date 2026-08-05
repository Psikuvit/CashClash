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

    private static MythicItemManager instance;

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

    private MythicItemManager() {
        cfg = ItemsConfig.getInstance();
        cooldownManager = CooldownManager.getInstance();

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

    public static MythicItemManager getInstance() {
        if (instance == null) {
            instance = new MythicItemManager();
        }
        return instance;
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
        int count = Math.min(ItemsConfig.getInstance().getLegendsPerGame(), allMythics.size());
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
     * For BlazeBite, use createBlazebiteBundle() instead to get both crossbows.
     */
    public ItemStack createMythicItem(MythicItem mythic, Player owner) {
        // For BlazeBite, return just the Glacier crossbow (use createBlazebiteBundle for both)
        if (mythic == MythicItem.BLAZEBITE_CROSSBOWS) {
            return createBlazebiteItem(owner, true); // Default to Glacier
        }

        ItemStack item = new ItemStack(mythic.getMaterial());

        PDCSetter tags = PDCSetter.of(item);

        // Display name with mythic color
        tags.meta().displayName(Messages.parse("<light_purple><bold>" + mythic.getDisplayName() + "</bold></light_purple>"));

        // Lore
        List<Component> lore = ItemFactory.getInstance().getGameplayFactory().getConfiguredLore(mythic);
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
     * Create the BlazeBite crossbow bundle - returns array of [Glacier, Volcano] crossbows.
     */
    public ItemStack[] createBlazebiteBundle(Player owner) {
        return new ItemStack[] {
            createBlazebiteItem(owner, true),  // Glacier
            createBlazebiteItem(owner, false)  // Volcano
        };
    }

    /**
     * Create a single BlazeBite crossbow (Glacier or Volcano).
     */
    private ItemStack createBlazebiteItem(Player owner, boolean isGlacier) {
        ItemStack item = new ItemStack(Material.CROSSBOW);

        PDCSetter tags = PDCSetter.of(item);

        String name = isGlacier ? "Glacier Crossbow" : "Volcano Crossbow";
        String color = isGlacier ? "<aqua>" : "<red>";

        tags.meta().displayName(Messages.parse("<light_purple><bold>" + name + "</bold></light_purple>"));

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Messages.parse("<dark_purple>âœ¦ MYTHIC WEAPON âœ¦</dark_purple>"));
        lore.add(Component.empty());
        if (isGlacier) {
            lore.add(Messages.parse(color + "Glacier Mode: Arrows inflict Slowness I"));
            lore.add(Messages.parse(color + "and Frostbite for 3 seconds."));
        } else {
            lore.add(Messages.parse(color + "Volcano Mode: Explosive fire arrows!"));
            lore.add(Messages.parse(color + "2 hearts direct, 1 heart splash (3 blocks)."));
        }
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>8 shots per magazine, 25s reload</gray>"));
        lore.add(Messages.parse("<gray>Owner: " + owner.getName() + "</gray>"));
        tags.meta().lore(lore);

        // PDC tags - mark as BlazeBite and store mode
        tags.set(Keys.ITEM_ID, PersistentDataType.STRING, MythicItem.BLAZEBITE_CROSSBOWS.name());
        tags.set(Keys.ITEM_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());
        tags.set(Keys.BLAZEBITE_MODE, PersistentDataType.STRING, isGlacier ? "glacier" : "volcano");

        // Apply enchantments - Piercing 3, Quick Charge 1
        tags.meta().addEnchant(Enchantment.PIERCING, 3, true);
        tags.meta().addEnchant(Enchantment.QUICK_CHARGE, 1, true);

        tags.meta().setUnbreakable(true);
        tags.meta().addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        tags.apply();

        return item;
    }

    private void applyMythicAttributes(MythicItem mythic, ItemMeta meta) {
        switch (mythic) {
            case GOBLIN_SPEAR -> {
                NamespacedKey damageKey = new NamespacedKey(CashClashPlugin.getInstance(), "goblin_damage");
                AttributeModifier damageMod = new AttributeModifier(
                        damageKey,
                        8.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                );
                meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageMod);

                NamespacedKey speedKey = new NamespacedKey(CashClashPlugin.getInstance(), "goblin_speed");
                AttributeModifier speedMod = new AttributeModifier(
                        speedKey,
                        -2.4,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                );
                meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedMod);

                // +1 block range removed
                meta.addEnchant(Enchantment.LOYALTY, 3, true);
            }
            case WARDEN_GLOVES -> {
                // Netherite sword stats: 8 attack damage (+4 base = 12 total), 1.6 attack speed
                NamespacedKey damageKey = new NamespacedKey(CashClashPlugin.getInstance(), "warden_damage");
                AttributeModifier damageMod = new AttributeModifier(
                        damageKey,
                        8.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                );
                meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageMod);

                NamespacedKey speedKey = new NamespacedKey(CashClashPlugin.getInstance(), "warden_speed");
                AttributeModifier speedMod = new AttributeModifier(
                        speedKey,
                        -2.4,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                );
                meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedMod);

                // Extra reach removed
                meta.removeAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE);
            }
            case BLOODWRENCH_CROSSBOW -> {
                // No enchantments - mode system handles functionality
            }
            case WIND_BOW -> // Legendary bow gets Power 3
                    meta.addEnchant(Enchantment.POWER, 3, true);
            case BLAZEBITE_CROSSBOWS -> {
                // Legendary crossbow - damage boost handled in hit handler
            }
            default -> {
            }
        }
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
