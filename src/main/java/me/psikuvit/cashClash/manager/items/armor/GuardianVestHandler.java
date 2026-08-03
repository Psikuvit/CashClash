package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guardian's Vest - grants Resistance when dropping below 4 hearts, up to 3 times per round.
 */
public class GuardianVestHandler extends ArmorSetHandler {

    private final Map<UUID, Integer> guardianUsesThisRound;

    public GuardianVestHandler(CustomArmorManager manager) {
        super(manager);
        this.guardianUsesThisRound = new ConcurrentHashMap<>();
    }

    public boolean hasGuardianVest(Player player) {
        for (CustomArmorItem ca : getEquippedCustomArmor(player)) {
            if (ca == CustomArmorItem.GUARDIANS_VEST) return true;
        }
        return false;
    }

    public void onPlayerDamaged(Player p, double healthAfter) {
        if (!hasGuardianVest(p)) return;
        UUID id = p.getUniqueId();

        if (healthAfter > 8.0) return; // 4 hearts = 8 HP

        int used = guardianUsesThisRound.getOrDefault(id, 0);
        if (used >= 3) return;

        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.GUARDIAN_VEST)) return;

        CashClashPlayer.applyEffect(p, PotionEffectType.RESISTANCE, 15 * 20, 1);
        guardianUsesThisRound.put(id, used + 1);
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.GUARDIAN_VEST, 20);

        Messages.send(p, "armor.guardian-vest-activated", "uses", String.valueOf(used + 1));
        ParticleUtils.guardianRings(p.getLocation());
        SoundUtils.play(p, Sound.ITEM_TOTEM_USE, 0.5f, 1.5f);
    }

    @Override
    public void cleanup() {
        guardianUsesThisRound.clear();
    }

    @Override
    public void resetRoundTracking() {
        guardianUsesThisRound.clear();
    }
}
