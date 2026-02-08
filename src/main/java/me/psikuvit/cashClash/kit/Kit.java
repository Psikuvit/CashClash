package me.psikuvit.cashClash.kit;

import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.shop.items.UtilityItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.items.ItemFactory;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Starter kits for Round 1
 */
public enum Kit {
    ARCHER("Archer"),
    HEALER("Healer"),
    TANK("Tank"),
    SCOUT("Scout"),
    LUMBERJACK("Lumberjack"),
    PYROMANIAC("Pyromaniac"),
    GHOST("Ghost"),
    FIGHTER("Fighter"),
    SPIDER("Spider"),
    BOMBER("Bomber");

    private final String displayName;

    Kit(String displayName) {
        this.displayName = displayName;
    }

    public void apply(Player player) {
        apply(player, 1, true); // Default to round 1, shields enabled for backwards compatibility
    }
    
    public void apply(Player player, int round, boolean rounds1to3HaveShields) {
        player.getInventory().clear();

        giveBaseItems(player, round, rounds1to3HaveShields);

        // Kit-specific items
        ItemFactory factory = ItemFactory.getInstance();
        switch (this) {
            case ARCHER -> {
                ItemStack arrows = factory.createGameplayItem(UtilityItem.ARROWS);
                arrows.setAmount(10);
                ItemStack bow = factory.createGameplayItem(UtilityItem.BOW);
                player.getInventory().addItem(bow);
                player.getInventory().addItem(arrows);
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
                // Protection 1 only on round 1, removed after round 1
                if (round == 1) {
                    ItemStack[] armor = player.getInventory().getArmorContents();

                    for (ItemStack piece : armor) {
                        if (piece != null) {
                            ItemMeta m = piece.getItemMeta();
                            if (m != null) m.addEnchant(Enchantment.PROTECTION, 1, true);

                            piece.setItemMeta(m);
                        }
                    }
                    // Set the armor contents back to apply the enchantments
                    player.getInventory().setArmorContents(armor);
                }
            }
            case SCOUT -> {
                ItemStack crossbow = new ItemStack(Material.CROSSBOW);

                player.getInventory().addItem(crossbow);
                player.getInventory().addItem(ItemStack.of(Material.ARROW, 3));
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
            case GHOST -> {
                // Speed effect applied at start of each round via reapplyKitPotionEffects()
            }
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
            case SPIDER -> {
                ItemStack cobwebs = factory.createGameplayItem(UtilityItem.COBWEB);
                cobwebs.setAmount(2);
                player.getInventory().addItem(cobwebs);
            }
            case BOMBER -> {
                // Give 2 actual grenades with proper custom item tags
                for (int i = 0; i < 2; i++) {
                    ItemStack grenade = factory.createCustomItem(CustomItem.GRENADE, player);
                    player.getInventory().addItem(grenade);
                }
            }
        }
    }

    private void giveBaseItems(Player player) {
        giveBaseItems(player, 1, true); // Default to round 1, shields enabled for backwards compatibility
    }
    
    private void giveBaseItems(Player player, int round, boolean rounds1to3HaveShields) {
        // Leather helmet (unbreakable)
        ItemStack leatherHelmet = new ItemStack(Material.LEATHER_HELMET);
        ItemMeta helmetMeta = leatherHelmet.getItemMeta();
        if (helmetMeta != null) {
            helmetMeta.setUnbreakable(true);
            leatherHelmet.setItemMeta(helmetMeta);
        }
        player.getInventory().setHelmet(leatherHelmet);

        player.getInventory().setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.GOLDEN_BOOTS));

        // Stone tools
        player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        player.getInventory().addItem(new ItemStack(Material.STONE_AXE));

        // Diamond pickaxe with Efficiency 2
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta pickMeta = pickaxe.getItemMeta();
        if (pickMeta != null) {
            pickMeta.addEnchant(Enchantment.EFFICIENCY, 2, true);
            pickaxe.setItemMeta(pickMeta);
        }
        player.getInventory().addItem(pickaxe);

        // Shears
        player.getInventory().addItem(new ItemStack(Material.SHEARS));

        // Food (with ITEM_ID for refund tracking)
        ItemFactory factory = ItemFactory.getInstance();
        ItemStack steak = factory.createGameplayItem(FoodItem.STEAK);
        steak.setAmount(8);
        player.getInventory().addItem(steak);

        ItemStack bread = factory.createGameplayItem(FoodItem.BREAD);
        bread.setAmount(16);
        player.getInventory().addItem(bread);

        // Shield logic: rounds 1-3 are either shield or shieldless, rounds 4-6 is the other one
        // In overtime (round 7+), there's a 50/50 for shield vs shieldless
        boolean shouldGiveShield = shouldGiveShield(round, rounds1to3HaveShields);
        if (shouldGiveShield) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        }
        
        player.getInventory().addItem(new ItemStack(Material.WATER_BUCKET));
    }
    
    /**
     * Determine if player should get a shield based on round number.
     * Rounds 1-3: shield or shieldless (determined by rounds1to3HaveShields)
     * Rounds 4-6: opposite of rounds 1-3
     * Round 7+: 50/50 chance
     */
    private boolean shouldGiveShield(int round, boolean rounds1to3HaveShields) {
        if (round <= 3) {
            return rounds1to3HaveShields;
        } else if (round <= 6) {
            return !rounds1to3HaveShields;
        } else {
            // Round 7+ (overtime): 50/50 chance
            return Math.random() < 0.5;
        }
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

    /**
     * Apply kit items for layout editing (no armor, just inventory items).
     * Used by LayoutManager to let players arrange items.
     */
    public void applyForLayout(Player player) {
        player.getInventory().clear();

        // Stone tools
        player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        player.getInventory().addItem(new ItemStack(Material.STONE_AXE));

        // Diamond pickaxe with Efficiency 2
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta pickMeta = pickaxe.getItemMeta();
        if (pickMeta != null) {
            pickMeta.addEnchant(Enchantment.EFFICIENCY, 2, true);
            pickaxe.setItemMeta(pickMeta);
        }
        player.getInventory().addItem(pickaxe);

        // Shears
        player.getInventory().addItem(new ItemStack(Material.SHEARS));

        // Food (with ITEM_ID for refund tracking)
        ItemFactory factory = ItemFactory.getInstance();
        ItemStack steak = factory.createGameplayItem(FoodItem.STEAK);
        steak.setAmount(8);
        player.getInventory().addItem(steak);

        ItemStack bread = factory.createGameplayItem(FoodItem.BREAD);
        bread.setAmount(16);
        player.getInventory().addItem(bread);

        // Water bucket
        player.getInventory().addItem(new ItemStack(Material.WATER_BUCKET));

        // Kit-specific items (no potion effects for layout)
        switch (this) {
            case ARCHER -> {
                player.getInventory().addItem(new ItemStack(Material.BOW));
                player.getInventory().addItem(new ItemStack(Material.ARROW, 5));
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
            case SCOUT -> {
                ItemStack crossbow = new ItemStack(Material.CROSSBOW);
                ItemMeta meta = crossbow.getItemMeta();
                if (meta != null) meta.addEnchant(Enchantment.QUICK_CHARGE, 1, true);
                crossbow.setItemMeta(meta);
                player.getInventory().addItem(crossbow);
                player.getInventory().addItem(new ItemStack(Material.ARROW, 10));
            }
            case PYROMANIAC -> {
                player.getInventory().addItem(new ItemStack(Material.LAVA_BUCKET));
                player.getInventory().addItem(new ItemStack(Material.FIRE_CHARGE, 2));
            }
            case SPIDER -> player.getInventory().addItem(new ItemStack(Material.COBWEB, 2));
            case BOMBER -> {
                for (int i = 0; i < 2; i++) {
                    ItemStack grenade = factory.createCustomItem(CustomItem.GRENADE, player);
                    player.getInventory().addItem(grenade);
                }
            }
            // TANK, LUMBERJACK, FIGHTER modify existing items - handled in apply()
            // GHOST, FIRE_FIGHTER only add potion effects - nothing to add here
            default -> {}
        }
    }

    /**
     * Apply kit with a custom layout.
     * Items are placed according to the slot -> item identifier mapping.
     *
     * @param player The player to give the kit to
     * @param layout Map of slot -> item identifier
     */
    public void applyWithLayout(Player player, Map<Integer, String> layout) {
        applyWithLayout(player, layout, 1, true); // Default to round 1, shields enabled for backwards compatibility
    }
    
    /**
     * Apply kit with a custom layout and round number.
     * Items are placed according to the slot -> item identifier mapping.
     *
     * @param player The player to give the kit to
     * @param layout Map of slot -> item identifier
     * @param round The current round number
     * @param rounds1to3HaveShields Whether rounds 1-3 have shields (rounds 4-6 will be opposite)
     */
    public void applyWithLayout(Player player, Map<Integer, String> layout, int round, boolean rounds1to3HaveShields) {
        player.getInventory().clear();

        // Apply armor
        ItemStack leatherHelmet = new ItemStack(Material.LEATHER_HELMET);
        ItemMeta helmetMeta = leatherHelmet.getItemMeta();
        if (helmetMeta != null) {
            helmetMeta.setUnbreakable(true);
            leatherHelmet.setItemMeta(helmetMeta);
        }
        player.getInventory().setHelmet(leatherHelmet);
        player.getInventory().setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.GOLDEN_BOOTS));
        
        // Shield logic: rounds 1-3 are either shield or shieldless, rounds 4-6 is the other one
        // In overtime (round 7+), there's a 50/50 for shield vs shieldless
        boolean shouldGiveShield = shouldGiveShield(round, rounds1to3HaveShields);
        if (shouldGiveShield) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        }

        // Build a map of item identifier -> ItemStack for all kit items
        Map<String, ItemStack> itemMap = new HashMap<>();

        // Base items
        itemMap.put("MATERIAL:STONE_SWORD", new ItemStack(Material.STONE_SWORD));
        itemMap.put("MATERIAL:STONE_AXE", new ItemStack(Material.STONE_AXE));

        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta pickMeta = pickaxe.getItemMeta();
        if (pickMeta != null) {
            pickMeta.addEnchant(Enchantment.EFFICIENCY, 2, true);
            pickaxe.setItemMeta(pickMeta);
        }
        itemMap.put("MATERIAL:DIAMOND_PICKAXE", pickaxe);
        itemMap.put("MATERIAL:SHEARS", new ItemStack(Material.SHEARS));

        ItemStack steak = ItemFactory.getInstance().createGameplayItem(FoodItem.STEAK);
        steak.setAmount(8);
        itemMap.put("CUSTOM:STEAK", steak);
        itemMap.put("MATERIAL:COOKED_BEEF", steak); // Fallback

        ItemStack bread = ItemFactory.getInstance().createGameplayItem(FoodItem.BREAD);
        bread.setAmount(16);
        itemMap.put("CUSTOM:BREAD", bread);
        itemMap.put("MATERIAL:BREAD", bread); // Fallback

        itemMap.put("MATERIAL:WATER_BUCKET", new ItemStack(Material.WATER_BUCKET));

        // Kit-specific items
        switch (this) {
            case ARCHER -> {
                itemMap.put("MATERIAL:BOW", new ItemStack(Material.BOW));
                ItemStack arrows = new ItemStack(Material.ARROW, 10);
                itemMap.put("MATERIAL:ARROW", arrows);
            }
            case HEALER -> {
                ItemStack splash = new ItemStack(Material.SPLASH_POTION);
                PotionMeta meta = (PotionMeta) splash.getItemMeta();
                if (meta != null) {
                    meta.setBasePotionType(PotionType.HEALING);
                    meta.displayName(Messages.parse("<blue>Potion of Instant Health"));
                    splash.setItemMeta(meta);
                }
                itemMap.put("MATERIAL:SPLASH_POTION", splash);
            }
            case TANK -> {
                // Tank gets Protection on armor - applied after placement
            }
            case SCOUT -> {
                ItemStack crossbow = new ItemStack(Material.CROSSBOW);
                ItemMeta meta = crossbow.getItemMeta();
                if (meta != null) meta.addEnchant(Enchantment.QUICK_CHARGE, 1, true);
                crossbow.setItemMeta(meta);
                itemMap.put("MATERIAL:CROSSBOW", crossbow);
                itemMap.put("MATERIAL:ARROW", new ItemStack(Material.ARROW, 10));
            }
            case LUMBERJACK -> {
                // Sharpness on axe - applied after placement
            }
            case PYROMANIAC -> {
                itemMap.put("MATERIAL:LAVA_BUCKET", new ItemStack(Material.LAVA_BUCKET));
                itemMap.put("MATERIAL:FIRE_CHARGE", new ItemStack(Material.FIRE_CHARGE, 2));
            }
            case GHOST -> {
                // Speed effect applied at start of each round via reapplyKitPotionEffects()
            }
            case FIGHTER -> {
                // Sharpness on sword - applied after placement
            }
            case SPIDER -> itemMap.put("MATERIAL:COBWEB", new ItemStack(Material.COBWEB, 2));
            case BOMBER -> {
                // Grenades need to be created with player UUID
                for (int i = 0; i < 2; i++) {
                    ItemStack grenade = ItemFactory.getInstance().createCustomItem(CustomItem.GRENADE, player);
                    // Add grenades as GRENADE_1 and GRENADE_2
                    itemMap.put("CUSTOM:GRENADE_" + i, grenade);
                    itemMap.put("CUSTOM:GRENADE", grenade); // Also map without number
                }
            }
            default -> {}
        }

        // Track which items have been placed to avoid duplicates
        Set<String> placedItems = new HashSet<>();

        // Place items according to layout
        for (Map.Entry<Integer, String> entry : layout.entrySet()) {
            int slot = entry.getKey();
            String itemId = entry.getValue();

            if (slot < 0 || slot >= 36) continue;

            ItemStack item = itemMap.get(itemId);
            if (item != null && !placedItems.contains(itemId)) {
                player.getInventory().setItem(slot, item.clone());
                placedItems.add(itemId);
            }
        }

        // Add any remaining items that weren't in the layout
        for (Map.Entry<String, ItemStack> entry : itemMap.entrySet()) {
            if (!placedItems.contains(entry.getKey())) {
                // Skip duplicate mappings (fallbacks)
                if (entry.getKey().startsWith("CUSTOM:GRENADE_")) continue;
                player.getInventory().addItem(entry.getValue().clone());
            }
        }

        // Apply kit-specific enchants to existing items
        switch (this) {
            case TANK -> {
                // Protection 1 only on round 1, removed after round 1
                if (round == 1) {
                    ItemStack helmet = player.getInventory().getHelmet();
                    ItemStack chest = player.getInventory().getChestplate();
                    ItemStack legs = player.getInventory().getLeggings();
                    ItemStack boots = player.getInventory().getBoots();
                    if (helmet != null) {
                        ItemMeta m = helmet.getItemMeta();
                        if (m != null) m.addEnchant(Enchantment.PROTECTION, 1, true);
                        helmet.setItemMeta(m);
                        player.getInventory().setHelmet(helmet);
                    }
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
                    if (boots != null) {
                        ItemMeta m = boots.getItemMeta();
                        if (m != null) m.addEnchant(Enchantment.PROTECTION, 1, true);
                        boots.setItemMeta(m);
                        player.getInventory().setBoots(boots);
                    }
                }
            }
            case LUMBERJACK -> {
                int axeSlot = getAxeSlot(player);
                if (axeSlot >= 0) {
                    ItemStack axe = player.getInventory().getItem(axeSlot);
                    if (axe != null) {
                        ItemMeta meta = axe.getItemMeta();
                        if (meta != null) meta.addEnchant(Enchantment.SHARPNESS, 1, true);
                        axe.setItemMeta(meta);
                    }
                }
            }
            case FIGHTER -> {
                int swordSlot = getSwordSlot(player);
                if (swordSlot >= 0) {
                    ItemStack sword = player.getInventory().getItem(swordSlot);
                    if (sword != null) {
                        ItemMeta meta = sword.getItemMeta();
                        if (meta != null) meta.addEnchant(Enchantment.SHARPNESS, 1, true);
                        sword.setItemMeta(meta);
                    }
                }
            }
            default -> {}
        }
    }

}
