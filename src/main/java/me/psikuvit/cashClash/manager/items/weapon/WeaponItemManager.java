package me.psikuvit.cashClash.manager.items.weapon;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.manager.items.armor.CustomArmorManager;
import me.psikuvit.cashClash.shop.items.WeaponItem;
import me.psikuvit.cashClash.util.CooldownManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry of every special weapon's behaviour. Each weapon is handled by its own
 * {@link WeaponItemHandler}; handlers register here by item so callers can look
 * them up either by the {@link WeaponItem} they handle or by handler type.
 *
 * <p>Adding a new special weapon means writing one handler class and one
 * {@link #register(Function, WeaponItem...)} line — the registry handles lookup,
 * cleanup fan-out and nothing else. Anything shared across weapons lives here
 * (the config/cooldown/armor dependencies handed to every handler).</p>
 */
public class WeaponItemManager {

    private final CooldownManager cooldownManager;
    private final ItemsConfig cfg;
    private final CustomArmorManager armorManager;

    // Handler registry - one or more items per handler
    private final Map<WeaponItem, WeaponItemHandler> handlers;
    private final List<WeaponItemHandler> allHandlers;

    public WeaponItemManager(CooldownManager cooldownManager, ItemsConfig cfg, CustomArmorManager armorManager) {
        this.cooldownManager = cooldownManager;
        this.cfg = cfg;
        this.armorManager = armorManager;
        this.handlers = new EnumMap<>(WeaponItem.class);
        this.allHandlers = new ArrayList<>();

        register(SoulKatanaHandler::new, WeaponItem.SOUL_KATANA);
        register(CashBlasterHandler::new, WeaponItem.CASH_BLASTER);
    }

    /**
     * Registers a handler for the given item(s). Each item maps to exactly one
     * handler; a single handler may serve several items.
     */
    private void register(Function<WeaponItemManager, ? extends WeaponItemHandler> factory, WeaponItem... items) {
        WeaponItemHandler handler = factory.apply(this);
        allHandlers.add(handler);
        for (WeaponItem item : items) {
            handlers.put(item, handler);
        }
    }

    /**
     * Looks up the handler for a detected weapon, e.g. after resolving it from PDC tags.
     *
     * @throws IllegalArgumentException when no handler is registered for the item
     */
    public WeaponItemHandler getHandler(WeaponItem item) {
        WeaponItemHandler handler = handlers.get(item);
        if (handler == null) {
            throw new IllegalArgumentException("No weapon handler registered for " + item);
        }
        return handler;
    }

    /**
     * Looks up a handler by its concrete type, e.g. {@code getHandler(SoulKatanaHandler.class)}.
     *
     * @throws IllegalArgumentException when no handler of that type is registered
     */
    public <T extends WeaponItemHandler> T getHandler(Class<T> type) {
        for (WeaponItemHandler handler : allHandlers) {
            if (type.isInstance(handler)) {
                return type.cast(handler);
            }
        }
        throw new IllegalArgumentException("No weapon handler registered for " + type.getSimpleName());
    }

    /**
     * Every registered handler, for cross-cutting fan-out (cleanup, disable-all, ...).
     */
    public Collection<WeaponItemHandler> getHandlers() {
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
        for (WeaponItemHandler handler : allHandlers) {
            handler.cleanup();
        }
    }
}
