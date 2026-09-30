package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.ChanceBag;
import org.bukkit.entity.Player;

/**
 * Bullseye Pants - a fixed-luck share of the wearer's shots become storming arrows.
 */
public class BullseyePantsHandler extends ArmorSetHandler {

    private final ChanceBag stormArrowChance;

    public BullseyePantsHandler(CustomArmorManager manager) {
        super(manager);
        this.stormArrowChance = new ChanceBag();
    }

    public boolean hasBullseyePants(Player player) {
        for (CustomArmorItem ca : getEquippedCustomArmor(player)) {
            if (ca == CustomArmorItem.BULLSEYE_PANTS) return true;
        }
        return false;
    }

    /**
     * Rolls whether the wearer's shot is a storming arrow - exactly the configured share of their
     * shots, e.g. 1 in every 4 at 25%. Always false without Bullseye Pants on.
     */
    public boolean rollStormArrow(Player player) {
        if (!hasBullseyePants(player)) return false;
        return stormArrowChance.roll(player.getUniqueId(), cfg.getBullseyeStormChancePercent());
    }

    @Override
    public void cleanup() {
        stormArrowChance.clear();
    }

    @Override
    public void resetRoundTracking() {
        stormArrowChance.clear();
    }
}
