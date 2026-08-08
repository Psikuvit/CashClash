package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Registry for per-set armor handlers. Holds shared cross-cutting state
 * (mythic shift lock) and delegates per-set behavior to registered handlers.
 */
public class CustomArmorManager {

    private final CooldownManager cooldownManager;

    private final ItemsConfig cfg;

    private final List<ArmorSetHandler> allHandlers;

    // Mythic shift lock: players blocked from activating bunny shoes while a mythic shift ability is active
    private final Set<UUID> mythicShiftLock;

    public CustomArmorManager(CooldownManager cooldownManager, ItemsConfig cfg) {
        this.cooldownManager = cooldownManager;
        this.cfg = cfg;

        this.allHandlers = new ArrayList<>();
        register(BunnyShoesHandler::new);
        register(GuardianVestHandler::new);
        register(DragonSetHandler::new);
        register(TectonicCapHandler::new);
        register(FlamebringerSetHandler::new);
        register(BullseyePantsHandler::new);
        register(DeathmaulerSetHandler::new);
        register(InvestorSetHandler::new);

        this.mythicShiftLock = ConcurrentHashMap.newKeySet();
    }

    private void register(Function<CustomArmorManager, ? extends ArmorSetHandler> factory) {
        allHandlers.add(factory.apply(this));
    }

    @SuppressWarnings("unchecked")
    public <T extends ArmorSetHandler> T getHandler(Class<T> handlerClass) {
        for (ArmorSetHandler handler : allHandlers) {
            if (handlerClass.isInstance(handler)) {
                return (T) handler;
            }
        }
        throw new IllegalStateException("No armor handler registered for " + handlerClass.getName());
    }

    public List<ArmorSetHandler> getHandlers() {
        return allHandlers;
    }

    ItemsConfig getCfg() {
        return cfg;
    }

    CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    boolean isMythicShiftLocked(UUID uuid) {
        return mythicShiftLock.contains(uuid);
    }

    /**
     * Temporarily block mythic shift activations for a player (used while a shift ability runs).
     */
    public void lockMythicShift(Player player) {
        UUID id = player.getUniqueId();
        mythicShiftLock.add(id);
        SchedulerUtils.runTaskLater(() -> mythicShiftLock.remove(id), 10L);
    }

    public void cleanup() {
        for (ArmorSetHandler handler : allHandlers) {
            handler.cleanup();
        }

        mythicShiftLock.clear();

        // Note: cooldowns are managed by CooldownManager and will be cleared when players are cleared
    }

    /**
     * Reset per-round tracking for all players (called at round start).
     */
    public void resetRoundTracking() {
        for (ArmorSetHandler handler : allHandlers) {
            handler.resetRoundTracking();
        }
    }
}
