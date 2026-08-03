package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bullseye Pants - every 4th melee hit triggers a bonus effect.
 */
public class BullseyePantsHandler extends ArmorSetHandler {

    private final Map<UUID, Integer> bullseyeHitCount; // Attacker -> current hit count

    public BullseyePantsHandler(CustomArmorManager manager) {
        super(manager);
        this.bullseyeHitCount = new ConcurrentHashMap<>();
    }

    public boolean hasBullseyePants(Player player) {
        for (CustomArmorItem ca : getEquippedCustomArmor(player)) {
            if (ca == CustomArmorItem.BULLSEYE_PANTS) return true;
        }
        return false;
    }

    /**
     * Increments the hit count for Bullseye Pants.
     * @return true if it was the 4th hit (triggering the effect)
     */
    public boolean incrementBullseyeHit(Player p) {
        UUID id = p.getUniqueId();
        int hits = bullseyeHitCount.getOrDefault(id, 0) + 1;
        if (hits >= 4) {
            bullseyeHitCount.put(id, 0);
            return true;
        }
        bullseyeHitCount.put(id, hits);
        return false;
    }

    @Override
    public void cleanup() {
        bullseyeHitCount.clear();
    }

    @Override
    public void resetRoundTracking() {
        bullseyeHitCount.clear();
    }
}
