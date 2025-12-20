package me.psikuvit.cashClash.listener.items;

import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.shop.items.ShopItems;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Sound;

public class ConsumableListener implements Listener {

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player p = event.getPlayer();
        ItemStack consumed = event.getItem();

        if (consumed.getType().isAir()) return;

        ItemMeta meta = consumed.getItemMeta();
        if (meta == null) return;

        String tag = meta.getPersistentDataContainer().get(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING);
        if (tag == null) return;

        FoodItem fi = ShopItems.getFood(tag);
        if (fi == null) return;

        switch (fi) {
            case SPEED_CARROT -> applyConsumable(p, new PotionEffect(PotionEffectType.SPEED, 20 * 20, 1), "<green>Speed II activated!</green>");
            case GOLDEN_CHICKEN -> applyConsumable(p, new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0), "<gold>Strength I activated!</gold>");
            case COOKIE_OF_LIFE -> applyConsumable(p, new PotionEffect(PotionEffectType.REGENERATION, 14 * 20, 0), "<dark_green>Regeneration I activated!</dark_green>");
            case SUNSCREEN -> applyConsumable(p, new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30 * 20, 0), "<aqua>Fire Resistance activated!</aqua>");
            case CAN_OF_SPINACH -> applyConsumable(p, new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0), "<gold>Spinach Strength activated!</gold>");
            default -> {
            }
        }
    }

    private void applyConsumable(Player p, PotionEffect effect, String msg) {
        p.addPotionEffect(effect);
        Messages.send(p, msg);
        SoundUtils.play(p, Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
    }
}
