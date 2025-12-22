package me.psikuvit.cashClash.config;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Manages shop.yml configuration for all shop item prices.
 */
public class ShopConfig {

    private static ShopConfig instance;
    private FileConfiguration config;

    private ShopConfig() {
        loadConfig();
    }

    public static ShopConfig getInstance() {
        if (instance == null) {
            instance = new ShopConfig();
        }
        return instance;
    }

    private void loadConfig() {
        File configFile = new File(CashClashPlugin.getInstance().getDataFolder(), "shop.yml");

        if (!configFile.exists()) {
            CashClashPlugin.getInstance().saveResource("shop.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reload() {
        loadConfig();
    }

    public long getArmorPrice(String armorKey) {
        return config.getLong("armor." + armorKey, 0);
    }

    public long getWeaponPrice(String weaponKey) {
        return config.getLong("weapons." + weaponKey, 0);
    }

    public long getFoodPrice(String foodKey) {
        return config.getLong("food." + foodKey, 0);
    }

    public long getUtilityPrice(String utilityKey) {
        return config.getLong("utility." + utilityKey, 0);
    }

    public long getCustomItemPrice(String itemKey) {
        return config.getLong("custom-items." + itemKey, 0);
    }

    public long getCustomArmorPrice(String armorKey) {
        return config.getLong("custom-armor." + armorKey, 0);
    }

    public long getMythicItemPrice(String mythicKey) {
        return config.getLong("mythic-items." + mythicKey, 0);
    }

    public long getEnchantPrice(String enchantKey, int tier) {
        return config.getLong("enchants." + enchantKey + ".tier-" + tier, 0);
    }

    public long getInvestmentCost(String investmentKey) {
        return config.getLong("investments." + investmentKey + ".cost", 0);
    }

    public long getInvestmentBonusReturn(String investmentKey) {
        return config.getLong("investments." + investmentKey + ".bonus-return", 0);
    }

    public long getInvestmentNegativeReturn(String investmentKey) {
        return config.getLong("investments." + investmentKey + ".negative-return", 0);
    }
}

