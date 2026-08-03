package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Bag of Potatoes: every melee hit heals the attacker 1 heart and wears down the
 * item's durability, breaking it once the damage counter is exhausted. Stateless.
 */
public class BagOfPotatoesHandler extends CustomItemHandler {

    public BagOfPotatoesHandler(CustomItemManager manager) {
        super(manager);
    }

    public void handleBagOfPotatoesHit(Player attacker, ItemStack item, GameSession session) {
        CashClashPlayer.heal(attacker, 2.0);

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int damage = damageable.getDamage();
            int maxDur = item.getType().getMaxDurability();

            if (damage + 1 >= maxDur || damage >= 2) {
                attacker.getInventory().setItemInMainHand(null);
                SoundUtils.play(attacker, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            } else {
                damageable.setDamage(damage + 1);
                item.setItemMeta(meta);
            }
        }

        ParticleUtils.hearts(attacker, 3);
    }

    @Override
    public void cleanup() {
    }
}
