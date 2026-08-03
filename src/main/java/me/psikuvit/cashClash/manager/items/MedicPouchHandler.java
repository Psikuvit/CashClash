package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * The Medic Pouch: a self-heal on right-click and a limited team-heal when used
 * on a same-team player. Health gain that would overshoot max health is converted
 * into absorption hearts. Stateless - every outcome derives from the config and
 * the shared cooldown manager.
 */
public class MedicPouchHandler extends CustomItemHandler {

    public MedicPouchHandler(CustomItemManager manager) {
        super(manager);
    }

    public void useMedicPouchSelf(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.MEDIC_POUCH)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.MEDIC_POUCH);
            Messages.send(player, "customitem.medic-pouch-cooldown", "remaining", String.valueOf(remaining));
            return;
        }

        double currentHealth = player.getHealth();
        double maxHealth = getMaxHealth(player);
        double healAmount = cfg.getMedicPouchSelfHeal();

        if (currentHealth >= maxHealth) {
            CashClashPlayer.applyEffect(player, PotionEffectType.ABSORPTION, 20 * 30, 0, false, true);
            Messages.send(player, "customitem.healing-converted");
        } else {
            double healed = CashClashPlayer.heal(player, healAmount);
            double excess = healAmount - healed;

            if (excess > 0) {
                CashClashPlayer.applyEffect(player, PotionEffectType.ABSORPTION, 20 * 30, 0, false, true);
                Messages.send(player, "customitem.healed-full");
            } else {
                Messages.send(player, "customitem.healed-three-hearts");
            }
        }

        consumeItem(player, item);
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.MEDIC_POUCH, cfg.getMedicPouchCooldown());
        SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    public void useMedicPouchAlly(Player player, Player target, ItemStack item, GameSession session) {
        UUID uuid = player.getUniqueId();

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.MEDIC_POUCH)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.MEDIC_POUCH);
            Messages.send(player, "customitem.medic-pouch-cooldown", "remaining", String.valueOf(remaining));
            return;
        }

        if (session == null) return;

        Team playerTeam = session.getPlayerTeam(player);
        Team targetTeam = session.getPlayerTeam(target);

        if (playerTeam == null || targetTeam == null || playerTeam.getTeamNumber() != targetTeam.getTeamNumber()) {
            Messages.send(player, "customitem.heal-teammates-only");
            return;
        }

        double currentHealth = target.getHealth();
        double maxHealth = getMaxHealth(target);
        double healAmount = cfg.getMedicPouchAllyHeal();

        if (currentHealth >= maxHealth) {
            CashClashPlayer.applyEffect(target, PotionEffectType.ABSORPTION, 20 * 30, 1, false, true);
            Messages.send(target, "customitem.ally-gave-absorption", "player_name", player.getName());
        } else {
            double healed = CashClashPlayer.heal(target, healAmount);

            if (healAmount - healed > 0) {
                CashClashPlayer.applyEffect(target, PotionEffectType.ABSORPTION, 20 * 30, 1, false, true);
            }
            Messages.send(target, "customitem.ally-healed-you", "player_name", player.getName());
        }

        consumeItem(player, item);
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.MEDIC_POUCH, cfg.getMedicPouchCooldown());
        Messages.send(player, "customitem.healed-ally", "player_name", target.getName());
        SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        SoundUtils.play(target, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    @Override
    public void cleanup() {
    }
}
