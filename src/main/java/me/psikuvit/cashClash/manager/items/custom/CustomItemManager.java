package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.manager.items.armor.CustomArmorManager;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.util.CooldownManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry of every custom item's behaviour. Each item is handled by its own
 * {@link CustomItemHandler}; handlers register here by item so callers can look
 * them up either by the {@link CustomItem} they handle or by handler type.
 *
 * <p>Adding a new custom item means writing one handler class and one
 * {@link #register(Function, CustomItem...)} line — the registry handles lookup,
 * cleanup fan-out and nothing else. Anything shared across items lives here
 * (the config/cooldown/armor dependencies handed to every handler, and the
 * healing-reduction hook that Soul Katana applies and Radiating Lotus / Blooming
 * Rose consume).</p>
 */
public class CustomItemManager {

    private final CooldownManager cooldownManager;
    private final ItemsConfig cfg;
    private final CustomArmorManager armorManager;

    // Handler registry - one or more items per handler
    private final Map<CustomItem, CustomItemHandler> handlers;
    private final List<CustomItemHandler> allHandlers;

    // Shared: healing-reduction hook (e.g. Soul Katana's debuff), consumed by any item's heals

    public CustomItemManager(CooldownManager cooldownManager, ItemsConfig itemsConfig, CustomArmorManager armorManager) {
        this.cooldownManager = cooldownManager;
        this.cfg = itemsConfig;
        this.armorManager = armorManager;
        this.handlers = new EnumMap<>(CustomItem.class);
        this.allHandlers = new ArrayList<>();

        register(GrenadeHandler::new, CustomItem.GRENADE, CustomItem.SMOKE_CLOUD_GRENADE);
        register(BouncePadHandler::new, CustomItem.BOUNCE_PAD);
        register(MedicPouchHandler::new, CustomItem.MEDIC_POUCH);
        register(TabletOfHackingHandler::new, CustomItem.TABLET_OF_HACKING);
        register(BagOfPotatoesHandler::new, CustomItem.BAG_OF_POTATOES);
        register(BoomboxHandler::new, CustomItem.BOOMBOX);
        register(InvisCloakHandler::new, CustomItem.INVIS_CLOAK);
        register(RespawnAnchorHandler::new, CustomItem.RESPAWN_ANCHOR);
        register(TotemOfHauntingHandler::new, CustomItem.TOTEM_OF_HAUNTING);
        register(RadiatingLotusHandler::new, CustomItem.RADIATING_LOTUS);
        register(IceFanHandler::new, CustomItem.ICE_FAN);
        register(OverdriveHandler::new, CustomItem.OVERDRIVE_POTION);
        register(HuntersMarkHandler::new, CustomItem.HUNTERS_MARK);
        register(BloomingRoseHandler::new, CustomItem.BLOOMING_ROSE);
        register(OrbOfGravitationHandler::new, CustomItem.ORB_OF_GRAVITATION);
    }

    /**
     * Registers a handler for the given item(s). Each item maps to exactly one
     * handler; a single handler may serve several items (e.g. both grenade variants).
     */
    private void register(Function<CustomItemManager, ? extends CustomItemHandler> factory, CustomItem... items) {
        CustomItemHandler handler = factory.apply(this);
        allHandlers.add(handler);
        for (CustomItem item : items) {
            handlers.put(item, handler);
        }
    }

    /**
     * Looks up the handler for a detected item, e.g. after resolving it from PDC tags.
     *
     * @throws IllegalArgumentException when no handler is registered for the item
     */
    public CustomItemHandler getHandler(CustomItem item) {
        CustomItemHandler handler = handlers.get(item);
        if (handler == null) {
            throw new IllegalArgumentException("No custom item handler registered for " + item);
        }
        return handler;
    }

    /**
     * Looks up a handler by its concrete type, e.g. {@code getHandler(GrenadeHandler.class)}.
     *
     * @throws IllegalArgumentException when no handler of that type is registered
     */
    public <T extends CustomItemHandler> T getHandler(Class<T> type) {
        for (CustomItemHandler handler : allHandlers) {
            if (type.isInstance(handler)) {
                return type.cast(handler);
            }
        }
        throw new IllegalArgumentException("No custom item handler registered for " + type.getSimpleName());
    }

    /**
     * Every registered handler, for cross-cutting fan-out (cleanup, disable-all, ...).
     */
    public Collection<CustomItemHandler> getHandlers() {
        return Collections.unmodifiableList(allHandlers);
    }

    // ---- Shared dependencies handed to every handler ----

    CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    ItemsConfig getCfg() {
        return cfg;
    }

    CustomArmorManager getArmorManager() {
        return armorManager;
    }

    // ==================== CLEANUP ====================

    /**
     * Fans out to every handler's cleanup.
     */
    public void cleanup() {
        for (CustomItemHandler handler : allHandlers) {
            handler.cleanup();
        }
    }
}
