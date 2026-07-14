package me.psikuvit.cashClash.shop.items;


import me.psikuvit.cashClash.config.ShopConfig;
import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;


/**
 * Custom crafted items in Cash Clash with unique abilities.
 * These are special items separate from armor/weapons.
 */
public enum CustomItem implements Purchasable {
    DYNAMITE(Material.FIRE_CHARGE, "dynamite", 1, 0, "Dynamite"),
    BOUNCE_PAD(Material.SLIME_BLOCK, "bounce-pad", 1, 8, "Bounce Pad"),
    MEDIC_POUCH(Material.RED_DYE, "medic-pouch", 1, 0, "Medic Pouch"),
    RADIATING_LOTUS(Material.SPORE_BLOSSOM, "radiating-lotus", 1, 0, "Radiating Lotus"),
    ICY_FAN(Material.FEATHER, "icy-fan", 1, 3, "Icy Fan"),
    TOTEM_OF_HAUNTING(Material.TOTEM_OF_UNDYING, "totem_of_haunting", 1, 0, "Totem Of Haunting"),
    BOOMBOX(Material.JUKEBOX, "boombox", 1, 0, "Boombox"),
    INVIS_CLOAK(Material.PHANTOM_MEMBRANE, "invis-cloak", 1, 5, "Invisibility Cloak"),
    OVERDRIVE_POTION(Material.POTION, "overdrive-potion", 1, 1, "Overdrive Potion"),
    GRAVITATION_ORB(Material.ENDER_PEARL, "gravitation-orb", 1, 2, "Gravitation Orb");


    private final Material material;
    private final String configKey;
    private final int initialAmount;
    private final int maxPurchase;
    private final String displayName;


    CustomItem(Material material, String configKey, int initialAmount, int maxPurchase, String displayName ) {
        this.material = material;
        this.configKey = configKey;
        this.initialAmount = initialAmount;
        this.maxPurchase = maxPurchase;
        this.displayName = displayName;
    }


    @Override
    public Material getMaterial() {
        return material;
    }


    @Override
    public ShopCategory getCategory() {
        return ShopCategory.CUSTOM_ITEMS;
    }


    @Override
    public long getPrice() {
        return ShopConfig.getInstance().getCustomItemPrice(configKey);
    }


    @Override
    public String getConfigKey() {
        return configKey;
    }


    @Override
    public int getInitialAmount() {
        return initialAmount;
    }


    @Override
    public String getDisplayName() {
        return displayName;
    }




    /**
     * Gets the maximum number of this item that can be purchased per round.
     * @return Max purchase limit, or 0 if unlimited
     */
    public int getMaxPurchase() {
        return maxPurchase;
    }


    /**
     * Checks if this item has a purchase limit.
     * @return true if there's a max purchase limit
     */
    public boolean hasLimit() {
        return maxPurchase > 0;
    }
}