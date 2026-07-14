package me.psikuvit.cashClash.manager.items;


import me.psikuvit.cashClash.manager.items.CustomWeaponManager;
import org.bukkit.entity.Player;


public class CustomHealingManager {

    public static double modifyHealing(Player player, double amount) {
        
        Long expiry = CustomWeaponManager.getInstance()
                .getSoulKatanaHealingReduction()
                .get(player.getUniqueId());

        if (expiry == null) return amount;

        if (System.currentTimeMillis() > expiry) {
            CustomWeaponManager.getInstance()
                    .getSoulKatanaHealingReduction()
                    .remove(player.getUniqueId());

            return amount;
        }
        return amount * 0.7;
    }
}
