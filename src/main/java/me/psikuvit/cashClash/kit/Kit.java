package me.psikuvit.cashClash.kit;

import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.kyori.adventure.text.Component;
import org.bukkit.potion.PotionType;

/**
 * Starter kits for Round 1
 */
public enum Kit {
    ARCHER("Archer"),
    BUILDER("Builder"),
    HEALER("Healer"),
    TANK("Tank"),
    SCOUT("Scout"),
    LUMBERJACK("Lumberjack"),
    PYROMANIAC("Pyromaniac"),
    GHOST("Ghost"),
    FIGHTER("Fighter"),
    FIRE_FIGHTER("Fire Fighter"),
    SPIDER("Spider"),
    BOMBER("Bomber");

    private final String displayName;

    Kit(String displayName) {
        this.displayName = displayName;
    }

    public void apply(Player player) {
        player.getInventory().clear();

        giveBaseItems(player);

        // Kit-specific items
        switch (this) {
            case ARCHER -> {
                player.getInventory().addItem(new ItemStack(Material.BOW));
                player.getInventory().addItem(new ItemStack(Material.ARROW, 10));
            }
            case BUILDER -> {
                player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 64));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 2400, 0, false, false));
            }
            case HEALER -> {
                ItemStack splash = new ItemStack(Material.SPLASH_POTION);
                PotionMeta meta = (PotionMeta) splash.getItemMeta();

                if (meta != null) {
                    meta.setBasePotionType(PotionType.HEALING);
                    meta.displayName(Messages.parse("<blue>Potion of Instant Health"));
                    splash.setItemMeta(meta);
                }
                player.getInventory().addItem(splash);
            }
            case TANK -> {
                ItemStack chest = player.getInventory().getChestplate();
                ItemStack legs = player.getInventory().getLeggings();

                if (chest != null) {
                    ItemMeta m = chest.getItemMeta();
                    if (m != null) m.addEnchant(Enchantment.PROTECTION, 1, true);

                    chest.setItemMeta(m);
                    player.getInventory().setChestplate(chest);
                }
                if (legs != null) {
                    ItemMeta m = legs.getItemMeta();
                    if (m != null) m.addEnchant(Enchantment.PROTECTION, 1, true);

                    legs.setItemMeta(m);
                    player.getInventory().setLeggings(legs);
                }
            }
            case SCOUT -> {
                ItemStack crossbow = new ItemStack(Material.CROSSBOW);
                ItemMeta meta = crossbow.getItemMeta();

                if (meta != null) meta.addEnchant(Enchantment.QUICK_CHARGE, 1, true);

                crossbow.setItemMeta(meta);
                player.getInventory().addItem(crossbow);
                player.getInventory().addItem(new ItemStack(Material.ARROW, 10));
            }
            case LUMBERJACK -> {
                int slot = getAxeSlot(player);
                ItemStack axe = null;
                if (slot >= 0) axe = player.getInventory().getItem(slot);

                if (axe == null) {
                    axe = new ItemStack(Material.STONE_AXE);
                    ItemMeta meta = axe.getItemMeta();

                    if (meta != null) meta.addEnchant(Enchantment.SHARPNESS, 1, true);
                    axe.setItemMeta(meta);

                    player.getInventory().addItem(axe);
                } else {
                    ItemMeta meta = axe.getItemMeta();
                    if (meta != null) meta.addEnchant(Enchantment.SHARPNESS, 1, true);
                    axe.setItemMeta(meta);
                }
            }
            case PYROMANIAC -> {
                player.getInventory().addItem(new ItemStack(Material.LAVA_BUCKET));
                player.getInventory().addItem(new ItemStack(Material.FIRE_CHARGE, 2));
            }
            case GHOST -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 2400, 0, false, false));
            case FIGHTER -> {
                int swordSlot = getSwordSlot(player);
                ItemStack sword = null;

                if (swordSlot >= 0) sword = player.getInventory().getItem(swordSlot);
                if (sword == null) {
                    sword = new ItemStack(Material.STONE_SWORD);
                    ItemMeta meta = sword.getItemMeta();

                    if (meta != null) meta.addEnchant(Enchantment.SHARPNESS, 1, true);

                    sword.setItemMeta(meta);
                    player.getInventory().addItem(sword);
                } else {
                    ItemMeta meta = sword.getItemMeta();
                    if (meta != null) meta.addEnchant(Enchantment.SHARPNESS, 1, true);
                    sword.setItemMeta(meta);
                }
            }
            case FIRE_FIGHTER -> player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 2400, 0, false, false));
            case SPIDER -> player.getInventory().addItem(new ItemStack(Material.COBWEB, 2));
            case BOMBER -> {
                // Give 2 TNT items as grenades (placeable but treated here as placeholder)
                ItemStack t = new ItemStack(Material.TNT, 2);
                ItemMeta tm = t.getItemMeta();

                if (tm != null) tm.displayName(Component.text("§4Grenade"));
                t.setItemMeta(tm);
                player.getInventory().addItem(t);
            }
        }
    }

    private void giveBaseItems(Player player) {
        // Gold armor equivalent (using leather armor with gold stats via attributes)
        player.getInventory().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.GOLDEN_BOOTS));

        // Stone tools
        player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        player.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE));
        player.getInventory().addItem(new ItemStack(Material.STONE_AXE));
        player.getInventory().addItem(new ItemStack(Material.STONE_SHOVEL));

        // Food
        player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 10));
        player.getInventory().addItem(new ItemStack(Material.BREAD, 30));

        // Shield
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        player.getInventory().addItem(new ItemStack(Material.WATER_BUCKET));
    }

    private int getSwordSlot(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType().name().contains("SWORD")) {
                return i;
            }
        }
        return -1;
    }

    private int getAxeSlot(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType().name().contains("AXE")) {
                return i;
            }
        }
        return -1;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Kit getRandom() {
        Kit[] kits = values();
        return kits[(int) (Math.random() * kits.length)];
    }
}
