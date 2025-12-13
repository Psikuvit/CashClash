package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.shop.ShopItem;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SoundUtils;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Sound;

public class ConsumableListener implements Listener {

    @EventHandler
    public void onPlayerUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();

        if (held.getType().isAir()) return;

        ItemMeta meta = held.getItemMeta();
        if (meta == null) return;

        String tag = meta.getPersistentDataContainer().get(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING);
        if (tag == null) return;

        try {
            ShopItem si = ShopItem.valueOf(tag);
            switch (si) {
                case SPEED_CARROT -> applyConsumable(p, held, new PotionEffect(PotionEffectType.SPEED, 20 * 20, 1), "<green>Speed!</green>");
                case GOLDEN_CHICKEN -> applyConsumable(p, held, new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0), "<gold>Strength!</gold>");
                case COOKIE_OF_LIFE -> applyConsumable(p, held, new PotionEffect(PotionEffectType.REGENERATION, 14 * 20, 0), "<dark_green>Regeneration!</dark_green>");
                case SUNSCREEN -> applyConsumable(p, held, new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30 * 20, 0), "<aqua>Fire Resistance!</aqua>");
                case CAN_OF_SPINACH -> applyConsumable(p, held, new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0), "<gold>Spinach Strength!</gold>");
                default -> {
                    return;
                }
            }
        } catch (IllegalArgumentException ex) {
            return;
        }

        event.setCancelled(true);
    }

    private void applyConsumable(Player p, ItemStack held, PotionEffect effect, String msg) {
        p.addPotionEffect(effect);
        Messages.send(p, msg);
        SoundUtils.play(p, Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);

        int amt = held.getAmount();
        if (amt > 1) {
            held.setAmount(amt - 1);
            p.getInventory().setItemInMainHand(held);
        } else {
            p.getInventory().setItemInMainHand(null);
        }
    }
}
