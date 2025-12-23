package me.psikuvit.cashClash.listener.items;

import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.ShopDetection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Sound;

public class ConsumableListener implements Listener {

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player p = event.getPlayer();
        ItemStack consumed = event.getItem();

        if (consumed.getType().isAir()) return;

        FoodItem fi = ShopDetection.getFood(consumed);
        if (fi == null) return;

        switch (fi) {
            case SPEED_CARROT -> applyConsumable(p, new PotionEffect(PotionEffectType.SPEED, 20 * 20, 0), "<green>Speed I activated!</green>");
            case GOLDEN_CHICKEN -> applyConsumable(p, new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0), "<gold>Strength I activated!</gold>");
            case COOKIE_OF_LIFE -> applyConsumable(p, new PotionEffect(PotionEffectType.REGENERATION, 14 * 20, 0), "<dark_green>Regeneration I activated!</dark_green>");
            case SUNSCREEN -> applyConsumable(p, new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30 * 20, 0), "<aqua>Fire Resistance activated!</aqua>");
            case CAN_OF_SPINACH -> applyConsumable(p, new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0), "<gold>Spinach Strength activated!</gold>");
            default -> {
            }
        }
    }

    /**
     * Allow custom foods to be consumed even at full hunger.
     * When right-clicking with a custom food at full hunger, manually apply the effect.
     */
    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        // Check if it's a custom food item
        FoodItem fi = ShopDetection.getFood(item);
        if (fi == null) return;

        // Only handle custom foods with effects (those with descriptions)
        if (fi.getDescription().isEmpty()) return;

        // If player is at full hunger, manually consume the item
        if (p.getFoodLevel() >= 20) {
            event.setCancelled(true);

            // Apply the effect
            switch (fi) {
                case SPEED_CARROT -> applyConsumable(p, new PotionEffect(PotionEffectType.SPEED, 20 * 20, 0), "<green>Speed I activated!</green>");
                case GOLDEN_CHICKEN -> applyConsumable(p, new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0), "<gold>Strength I activated!</gold>");
                case COOKIE_OF_LIFE -> applyConsumable(p, new PotionEffect(PotionEffectType.REGENERATION, 14 * 20, 0), "<dark_green>Regeneration I activated!</dark_green>");
                case SUNSCREEN -> applyConsumable(p, new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30 * 20, 0), "<aqua>Fire Resistance activated!</aqua>");
                case CAN_OF_SPINACH -> applyConsumable(p, new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0), "<gold>Spinach Strength activated!</gold>");
            }

            // Remove one item from hand
            item.setAmount(item.getAmount() - 1);
        }
    }

    private void applyConsumable(Player p, PotionEffect effect, String msg) {
        p.addPotionEffect(effect);
        Messages.send(p, msg);
        SoundUtils.play(p, Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
    }
}
