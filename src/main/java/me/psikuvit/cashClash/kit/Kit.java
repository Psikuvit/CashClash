package me.psikuvit.cashClash.kit;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.shop.items.UtilityItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.items.ItemFactory;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
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

    private static final NamespacedKey KIT_ITEM_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "kit_item");
    private static final byte KIT_ITEM_FLAG = (byte) 1;

    Kit(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Apply kit to a player. This is the main method to give kit items.
     * Round 1: Base items + kit-specific items
     * Round 2+: Remove kit items, keep base items
     * Always: Apply shield logic
     *
     * @param player The player to give the kit to
     * @param round The current round number
     * @param rounds1to3HaveShields Whether rounds 1-3 have shields
     */
    public void apply(Player player, int round, boolean rounds1to3HaveShields) {
        if (round == 1) {
            // Round 1: Clear inventory and give everything
            player.getInventory().clear();
            giveBaseItems(player, round, rounds1to3HaveShields);
            giveKitSpecificItems(player);
        } else {
            // Round 2+: Remove kit items only (keep base items)
            removeKitItems(player);
            removeKitSpecificEnhancements(player);
        }

        // Always apply shield logic each round
        toggleShield(player, round, rounds1to3HaveShields);
    }

    /**
     * Backward compatibility method - defaults to round 1 with shields
     */
    public void apply(Player player) {
        apply(player, 1, true);
    }

    /**
     * Remove kit from a player - clears inventory and removes potion effects
     */
    public void remove(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);

        // Remove kit-specific potion effects
        switch (this) {
            case GHOST -> player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
            case PYROMANIAC -> player.removePotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE);
            default -> {}
        }
    }

    /**
     * Toggle shield in the offhand based on round number.
     * This is called at the start of each shopping phase to update the shield.
     *
     * @param player The player to update
     * @param round The current round number
     * @param rounds1to3HaveShields Whether rounds 1-3 have shields
     */
    public void toggleShield(Player player, int round, boolean rounds1to3HaveShields) {
        boolean shouldGiveShield = shouldGiveShield(round, rounds1to3HaveShields);

        if (shouldGiveShield) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        } else {
            // Remove shield from offhand
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (offHand.getType() == Material.SHIELD) {
                player.getInventory().setItemInOffHand(null);
            }
        }
    }

    /**
     * Give kit-specific items (only called in round 1)
     */
    private void giveKitSpecificItems(Player player) {
        ItemFactory factory = ItemFactory.getInstance();

        switch (this) {
            case ARCHER -> {
                ItemStack bow = factory.createGameplayItem(UtilityItem.BOW);
                ItemStack arrows = factory.createGameplayItem(UtilityItem.ARROWS);
                arrows.setAmount(10);
                markKitItem(bow);
                markKitItem(arrows);
                player.getInventory().addItem(bow, arrows);
            }
            case HEALER -> {
                ItemStack splash = new ItemStack(Material.SPLASH_POTION);
                PotionMeta meta = (PotionMeta) splash.getItemMeta();
                if (meta != null) {
                    meta.setBasePotionType(PotionType.HEALING);
                    meta.displayName(Messages.parse("<blue>Potion of Instant Health"));
                    splash.setItemMeta(meta);
                }
                markKitItem(splash);
                player.getInventory().addItem(splash);
            }
            case TANK -> {
                // Protection 1 only on round 1
                ItemStack[] armor = player.getInventory().getArmorContents();
                for (ItemStack piece : armor) {
                    if (piece != null && !piece.getType().isAir()) {
                        ItemMeta m = piece.getItemMeta();
                        if (m != null) {
                            m.addEnchant(Enchantment.PROTECTION, 1, true);
                            piece.setItemMeta(m);
                        }
                    }
                }
                player.getInventory().setArmorContents(armor);
            }
            case SCOUT -> {
                ItemStack crossbow = new ItemStack(Material.CROSSBOW);
                ItemStack arrows = new ItemStack(Material.ARROW, 3);
                markKitItem(crossbow);
                markKitItem(arrows);
                player.getInventory().addItem(crossbow, arrows);
            }
            case LUMBERJACK -> {
                // Add Sharpness 1 to the stone axe
                int axeSlot = getAxeSlot(player);
                if (axeSlot >= 0) {
                    ItemStack axe = player.getInventory().getItem(axeSlot);
                    if (axe != null) {
                        ItemMeta meta = axe.getItemMeta();
                        if (meta != null) {
                            meta.addEnchant(Enchantment.SHARPNESS, 1, true);
                            axe.setItemMeta(meta);
                        }
                    }
                }
            }
            case PYROMANIAC -> {
                ItemStack lava = new ItemStack(Material.LAVA_BUCKET);
                ItemStack charges = new ItemStack(Material.FIRE_CHARGE, 2);
                markKitItem(lava);
                markKitItem(charges);
                player.getInventory().addItem(lava, charges);
            }
            case GHOST -> {} // Speed effect applied at start of combat phase
            case FIGHTER -> {
                // Add Sharpness 1 to the stone sword
                int swordSlot = getSwordSlot(player);
                if (swordSlot >= 0) {
                    ItemStack sword = player.getInventory().getItem(swordSlot);
                    if (sword != null) {
                        ItemMeta meta = sword.getItemMeta();
                        if (meta != null) {
                            meta.addEnchant(Enchantment.SHARPNESS, 1, true);
                            sword.setItemMeta(meta);
                        }
                    }
                }
            }
            case SPIDER -> {
                ItemStack cobwebs = factory.createGameplayItem(UtilityItem.COBWEB);
                cobwebs.setAmount(2);
                markKitItem(cobwebs);
                player.getInventory().addItem(cobwebs);
            }
            case BOMBER -> {
                for (int i = 0; i < 2; i++) {
                    ItemStack grenade = factory.createCustomItem(CustomItem.GRENADE, player);
                    markKitItem(grenade);
                    player.getInventory().addItem(grenade);
                }
            }
        }
    }

    /**
     * Give base items that all kits receive every round.
     * This includes armor, tools, food, and shield (based on round).
     */
    private void giveBaseItems(Player player, int round, boolean rounds1to3HaveShields) {
        // === ARMOR ===
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

        // === TOOLS ===
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

        // === FOOD ===
        ItemFactory factory = ItemFactory.getInstance();
        ItemStack steak = factory.createGameplayItem(FoodItem.STEAK);
        steak.setAmount(8);
        player.getInventory().addItem(steak);

        ItemStack bread = factory.createGameplayItem(FoodItem.BREAD);
        bread.setAmount(16);
        player.getInventory().addItem(bread);

        // === UTILITY ===
        player.getInventory().addItem(new ItemStack(Material.WATER_BUCKET));

        // === SHIELD ===
        // Shield logic based on round number
        boolean shouldGiveShield = shouldGiveShield(round, rounds1to3HaveShields);
        if (shouldGiveShield) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        }
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
     * Apply kit with a custom layout and round number.
     * Items are placed according to the slot -> item identifier mapping.
     * Round 1: Clear inventory, place base items + kit items with layout, apply shield
     * Round 2+: Remove kit items only, apply shield logic (same as apply())
     *
     * @param player The player to give the kit to
     * @param layout Map of slot -> item identifier
     * @param round The current round number
     * @param rounds1to3HaveShields Whether rounds 1-3 have shields (rounds 4-6 will be opposite)
     */
    public void applyWithLayout(Player player, Map<Integer, String> layout, int round, boolean rounds1to3HaveShields) {
        if (round == 1) {
            // Round 1: Clear inventory and give everything with layout
            player.getInventory().clear();
            giveBaseItemsWithLayout(player, layout);
            giveKitSpecificItemsWithLayout(player, layout);
        } else {
            // Round 2+: Remove kit items only (keep base items), same as apply()
            removeKitItems(player);
            removeKitSpecificEnhancements(player);
        }

        // Always apply shield logic each round
        toggleShield(player, round, rounds1to3HaveShields);
    }

    /**
     * Give base items with custom layout (only called in round 1).
     * This is similar to giveBaseItems but respects the layout.
     */
    private void giveBaseItemsWithLayout(Player player, Map<Integer, String> layout) {
        // === ARMOR ===
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

        // Build a map of item identifier -> ItemStack for base items
        Map<String, ItemStack> itemMap = new HashMap<>();

        // === TOOLS ===
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

        // === FOOD ===
        ItemStack steak = ItemFactory.getInstance().createGameplayItem(FoodItem.STEAK);
        steak.setAmount(8);
        itemMap.put("CUSTOM:STEAK", steak);
        itemMap.put("MATERIAL:COOKED_BEEF", steak); // Fallback

        ItemStack bread = ItemFactory.getInstance().createGameplayItem(FoodItem.BREAD);
        bread.setAmount(16);
        itemMap.put("CUSTOM:BREAD", bread);
        itemMap.put("MATERIAL:BREAD", bread); // Fallback

        // === UTILITY ===
        itemMap.put("MATERIAL:WATER_BUCKET", new ItemStack(Material.WATER_BUCKET));

        // Place items according to layout
        placeItemsWithLayout(player, itemMap, layout);
    }

    /**
     * Give kit-specific items with custom layout (only called in round 1).
     */
    private void giveKitSpecificItemsWithLayout(Player player, Map<Integer, String> layout) {
        Map<String, ItemStack> itemMap = new HashMap<>();
        ItemFactory factory = ItemFactory.getInstance();

        switch (this) {
            case ARCHER -> {
                ItemStack bow = factory.createGameplayItem(UtilityItem.BOW);
                ItemStack arrows = factory.createGameplayItem(UtilityItem.ARROWS);
                arrows.setAmount(10);
                markKitItem(bow);
                markKitItem(arrows);
                itemMap.put("MATERIAL:BOW", bow);
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
                markKitItem(splash);
                itemMap.put("MATERIAL:SPLASH_POTION", splash);
            }
            case TANK ->
                // Protection 1 only on round 1 - applied after placement
                applyTankEnchantments(player);
            case SCOUT -> {
                ItemStack crossbow = new ItemStack(Material.CROSSBOW);
                ItemStack arrows = new ItemStack(Material.ARROW, 3);
                markKitItem(crossbow);
                markKitItem(arrows);
                itemMap.put("MATERIAL:CROSSBOW", crossbow);
                itemMap.put("MATERIAL:ARROW", arrows);
            }
            case LUMBERJACK -> {
                // Sharpness on axe - applied after placement
                int axeSlot = getAxeSlot(player);
                if (axeSlot >= 0) {
                    ItemStack axe = player.getInventory().getItem(axeSlot);
                    if (axe != null) {
                        ItemMeta meta = axe.getItemMeta();
                        if (meta != null) {
                            meta.addEnchant(Enchantment.SHARPNESS, 1, true);
                            axe.setItemMeta(meta);
                        }
                    }
                }
            }
            case PYROMANIAC -> {
                ItemStack lava = new ItemStack(Material.LAVA_BUCKET);
                ItemStack charges = new ItemStack(Material.FIRE_CHARGE, 2);
                markKitItem(lava);
                markKitItem(charges);
                itemMap.put("MATERIAL:LAVA_BUCKET", lava);
                itemMap.put("MATERIAL:FIRE_CHARGE", charges);
            }
            case GHOST -> {} // Speed effect applied at start of combat phase
            case FIGHTER -> {
                // Sharpness on sword - applied after placement
                int swordSlot = getSwordSlot(player);
                if (swordSlot >= 0) {
                    ItemStack sword = player.getInventory().getItem(swordSlot);
                    if (sword != null) {
                        ItemMeta meta = sword.getItemMeta();
                        if (meta != null) {
                            meta.addEnchant(Enchantment.SHARPNESS, 1, true);
                            sword.setItemMeta(meta);
                        }
                    }
                }
            }
            case SPIDER -> {
                ItemStack cobwebs = factory.createGameplayItem(UtilityItem.COBWEB);
                cobwebs.setAmount(2);
                markKitItem(cobwebs);
                itemMap.put("MATERIAL:COBWEB", cobwebs);
            }
            case BOMBER -> {
                for (int i = 0; i < 2; i++) {
                    ItemStack grenade = factory.createCustomItem(CustomItem.GRENADE, player);
                    markKitItem(grenade);
                    itemMap.put("CUSTOM:GRENADE_" + i, grenade);
                    itemMap.put("CUSTOM:GRENADE", grenade); // Also map without number
                }
            }
        }

        // Place items according to layout
        placeItemsWithLayout(player, itemMap, layout);
    }

    /**
     * Place items in the player's inventory according to the layout.
     * Items not in the layout are added to the first available slot.
     */
    private void placeItemsWithLayout(Player player, Map<String, ItemStack> itemMap, Map<Integer, String> layout) {
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
                // Skip duplicate mappings (fallbacks and numbered grenades)
                if (entry.getKey().startsWith("CUSTOM:GRENADE_") ||
                    entry.getKey().equals("MATERIAL:COOKED_BEEF") ||
                    entry.getKey().equals("MATERIAL:BREAD")) {
                    continue;
                }
                player.getInventory().addItem(entry.getValue().clone());
            }
        }
    }

    /**
     * Apply Tank kit's Protection enchantment to all armor pieces.
     */
    private void applyTankEnchantments(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chest = player.getInventory().getChestplate();
        ItemStack legs = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();

        if (helmet != null && !helmet.getType().isAir()) {
            ItemMeta m = helmet.getItemMeta();
            if (m != null) {
                m.addEnchant(Enchantment.PROTECTION, 1, true);
                helmet.setItemMeta(m);
                player.getInventory().setHelmet(helmet);
            }
        }
        if (chest != null && !chest.getType().isAir()) {
            ItemMeta m = chest.getItemMeta();
            if (m != null) {
                m.addEnchant(Enchantment.PROTECTION, 1, true);
                chest.setItemMeta(m);
                player.getInventory().setChestplate(chest);
            }
        }
        if (legs != null && !legs.getType().isAir()) {
            ItemMeta m = legs.getItemMeta();
            if (m != null) {
                m.addEnchant(Enchantment.PROTECTION, 1, true);
                legs.setItemMeta(m);
                player.getInventory().setLeggings(legs);
            }
        }
        if (boots != null && !boots.getType().isAir()) {
            ItemMeta m = boots.getItemMeta();
            if (m != null) {
                m.addEnchant(Enchantment.PROTECTION, 1, true);
                boots.setItemMeta(m);
                player.getInventory().setBoots(boots);
            }
        }
    }

    private void markKitItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(KIT_ITEM_KEY, PersistentDataType.BYTE, KIT_ITEM_FLAG);
        item.setItemMeta(meta);
    }

    private boolean isKitItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KIT_ITEM_KEY, PersistentDataType.BYTE);
    }

    private void removeKitItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (isKitItem(item)) {
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isKitItem(offhand)) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    private void removeKitSpecificEnhancements(Player player) {
        switch (this) {
            case TANK -> {
                ItemStack helmet = player.getInventory().getHelmet();
                ItemStack chest = player.getInventory().getChestplate();
                ItemStack legs = player.getInventory().getLeggings();
                ItemStack boots = player.getInventory().getBoots();

                removeProtectionIfBaseItem(helmet, Material.LEATHER_HELMET);
                removeProtectionIfBaseItem(chest, Material.GOLDEN_CHESTPLATE);
                removeProtectionIfBaseItem(legs, Material.GOLDEN_LEGGINGS);
                removeProtectionIfBaseItem(boots, Material.GOLDEN_BOOTS);
            }
            case LUMBERJACK -> removeSharpnessFromBaseTool(player, Material.STONE_AXE);
            case FIGHTER -> removeSharpnessFromBaseTool(player, Material.STONE_SWORD);
            default -> {}
        }
    }

    private void removeProtectionIfBaseItem(ItemStack item, Material baseType) {
        if (item == null || item.getType() != baseType) return;
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.removeEnchant(Enchantment.PROTECTION);
        item.setItemMeta(meta);
    }

    private void removeSharpnessFromBaseTool(Player player, Material baseType) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != baseType) continue;
            if (!item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            meta.removeEnchant(Enchantment.SHARPNESS);
            item.setItemMeta(meta);
        }
    }

}
