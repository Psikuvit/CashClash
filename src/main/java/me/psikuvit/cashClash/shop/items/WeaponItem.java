package me.psikuvit.cashClash.shop.items;


import me.psikuvit.cashClash.config.ShopConfig;
import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;


/**
 * Weapon items available in the shop.
 */
public enum WeaponItem implements Purchasable {
    IRON_SWORD(Material.IRON_SWORD, "iron-sword", "Iron Sword"),
    IRON_AXE(Material.IRON_AXE, "iron-axe", "Iron Axe"),
    DIAMOND_SWORD(Material.DIAMOND_SWORD, "diamond-sword", "Diamond Sword"),
    DIAMOND_AXE(Material.DIAMOND_AXE, "diamond-axe", "Diamond Axe"),
    NETHERITE_SWORD(Material.NETHERITE_SWORD, "netherite-sword", "Netherite Sword"),
    NETHERITE_AXE(Material.NETHERITE_AXE, "netherite-axe", "Netherite Axe"),
    BOW(Material.BOW, "bow", "Bow"),
    CROSSBOW(Material.CROSSBOW, "crossbow", "Crossbow"),
    SOUL_KATANA(Material.IRON_SWORD, "soul-katana", "Soul Katana"),
    CASH_BLASTER(Material.BOW, "cash-blaster", "Cash Blaster");


    private final Material material;
    private final String configKey;
    private final String displayName;


    WeaponItem(Material material, String configKey, String displayName) {
        this.material = material;
        this.configKey = configKey;
        this.displayName = displayName;
    }


    @Override
    public Material getMaterial() {
        return material;
    }


    @Override
    public ShopCategory getCategory() {
        return ShopCategory.WEAPONS;
    }


    @Override
    public long getPrice() {
        return ShopConfig.getInstance().getWeaponPrice(configKey);
    }


    @Override
    public String getConfigKey() {
        return configKey;
    }


    @Override
    public int getInitialAmount() {
        return 1;
    }


    @Override
    public String getDisplayName() {
        return displayName;
    }
}