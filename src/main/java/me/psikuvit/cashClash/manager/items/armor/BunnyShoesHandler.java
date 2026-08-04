package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.manager.items.RuneManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bunny Shoes - sneak toggle burst of Speed/Jump Boost.
 */
public class BunnyShoesHandler extends ArmorSetHandler {

    private final Map<UUID, Boolean> bunnyToggleReady;

    public BunnyShoesHandler(CustomArmorManager manager) {
        super(manager);
        this.bunnyToggleReady = new ConcurrentHashMap<>();
    }

    public boolean hasBunnyShoes(Player player) {
        for (CustomArmorItem ca : getEquippedCustomArmor(player)) {
            if (ca == CustomArmorItem.BUNNY_SHOES) return true;
        }
        return false;
    }

    public void onPlayerToggleSneak(Player p, boolean sneaking) {
        if (!hasBunnyShoes(p)) return;
        if (!p.isOnline()) return;
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.isDead()) return;
        if (p.getHealth() <= 0) return;
        UUID id = p.getUniqueId();

        if (sneaking) {
            bunnyToggleReady.put(id, true);
        } else {
            Boolean ready = bunnyToggleReady.get(id);
            if (ready != null && ready) {
                bunnyToggleReady.put(id, false);
                tryActivateBunnyShoes(p);
            }
        }
    }

    private void tryActivateBunnyShoes(Player p) {
        if (!p.isOnline()) return;
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.isDead()) return;
        if (p.getHealth() <= 0) return;
        UUID id = p.getUniqueId();

        // Blocked while a mythic shift ability is active
        if (manager.isMythicShiftLocked(id)) return;

        // Check if player is silenced (carrying enemy flag in CTF)
        if (isSilenced(p)) {
            Messages.send(p, "listener.cannot-use-items-while-silenced");
            return;
        }

        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.BUNNY_SHOES)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(id, CooldownManager.Keys.BUNNY_SHOES);
            Messages.send(p, "armor.bunny-shoes-cooldown", "remaining", String.valueOf(remaining));
            return;
        }

        int duration = cfg.getBunnyShoesDuration();
        CashClashPlayer.applyEffect(p, PotionEffectType.SPEED, duration * 20, 1);
        CashClashPlayer.applyEffect(p, PotionEffectType.JUMP_BOOST, duration * 20, 0);
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.BUNNY_SHOES, cfg.getBunnyShoesCooldown());

        Messages.send(p, "armor.bunny-shoes-activated", "duration", String.valueOf(duration));
        ParticleUtils.bunnyDiamond(p.getLocation().clone().add(0, 0.08, 0));
        SoundUtils.play(p, Sound.ENTITY_BREEZE_IDLE_AIR, 1.0f, 1.0f);
    }

    /**
     * Expose the bunny shoes toggle-ready state so it can be cleared on death.
     */
    public Map<UUID, Boolean> getBunnyToggleReady() {
        return bunnyToggleReady;
    }

    @Override
    public void cleanup() {
        bunnyToggleReady.clear();
    }

    @Override
    public void resetRoundTracking() {
        // Bunny toggle state is cleared on death, not at round start
    }
}
