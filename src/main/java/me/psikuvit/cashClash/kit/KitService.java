package me.psikuvit.cashClash.kit;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.shop.items.UtilityItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.items.ItemFactory;
import me.psikuvit.cashClash.util.items.ItemUtils;
import me.psikuvit.cashClash.util.items.PDCSetter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mutation logic for {@link Kit} - giving/removing kit items, potion effects, and shield state.
 * Kit itself stays a plain enum (display name only); every behavior that used to live on the
 * enum's instance methods lives here instead, taking the Kit as an explicit parameter.
 */
public class KitService {

    private static final NamespacedKey KIT_ITEM_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "kit_item");
    private static final byte KIT_ITEM_FLAG = (byte) 1;

    private KitService() {
        throw new AssertionError("Nope.");
    }

    /**
     * Apply kit to a player. This is the main method to give kit items.
     * Round 1: Clear inventory and give base items (kit-specific items disabled for now)
     * Round 2+: Don't reissue base items - the player already has (or has spent/upgraded)
     *   whatever they were given in round 1, so only the shield toggle re-runs
     * Always: Apply shield logic
     *
     * @param kit The kit to apply
     * @param player The player to give the kit to
     * @param round The current round number
     * @param shieldsEnabled Whether this game session has shields (fixed for every round)
     */
    public static void apply(Kit kit, Player player, int round, boolean shieldsEnabled) {
        if (round == 1) {
            // Round 1 kits removed temporarily - all rounds get base items
            player.getInventory().clear();

            // Remove kit items if they exist (clean up from previous versions)
            removeKitItems(player);
            removeKitSpecificEnhancements(kit, player);

            giveBaseItems(player, shieldsEnabled);
        }

        // Always apply shield logic each round
        toggleShield(player, shieldsEnabled);
    }

    /**
     * Backward compatibility method - defaults to round 1 with shields
     */
    public static void apply(Kit kit, Player player) {
        apply(kit, player, 1, true);
    }

    /**
     * Remove kit from a player - clears inventory and removes potion effects
     */
    public static void remove(Kit kit, Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);

        // Remove kit-specific potion effects
        switch (kit) {
            case GHOST -> CashClashPlayer.removeEffect(player, PotionEffectType.SPEED);
            case PYROMANIAC -> CashClashPlayer.removeEffect(player, PotionEffectType.FIRE_RESISTANCE);
            default -> {}
        }
    }

    /**
     * Toggle shield in the offhand based on this session's fixed shield setting.
     * This is called at the start of each shopping phase to reapply the shield.
     *
     * @param player The player to update
     * @param shieldsEnabled Whether this game session has shields (fixed for every round)
     */
    public static void toggleShield(Player player, boolean shieldsEnabled) {
        setShield(player, shieldsEnabled);
    }

    /**
     * Give or remove a shield from a player's offhand.
     * Used by the round-based shield toggle and the admin shield override command.
     *
     * @param player The player to update
     * @param give True to equip a shield, false to remove one
     */
    public static void setShield(Player player, boolean give) {
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (give) {
            if (offHand != null && offHand.getType() != Material.AIR && offHand.getType() != Material.SHIELD) {
                ItemUtils.returnItemToInventoryOrDrop(player, offHand);
            }
            player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        } else {
            if (offHand.getType() == Material.SHIELD) {
                player.getInventory().setItemInOffHand(null);
            }
        }
    }

    /**
     * Give kit-specific items (only called in round 1)
     */
    private static void giveKitSpecificItems(Kit kit, Player player) {
        ItemFactory factory = CashClashPlugin.getInstance().getItemFactory();

        switch (kit) {
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
     * This includes armor, tools, food, and shield (fixed for the whole game session).
     */
    private static void giveBaseItems(Player player, boolean shieldsEnabled) {
        // === ARMOR ===
        // Leather helmet (unbreakable)
        ItemStack leatherHelmet = new ItemStack(Material.LEATHER_HELMET);
        ItemMeta helmetMeta = leatherHelmet.getItemMeta();
        if (helmetMeta != null) {
            helmetMeta.setUnbreakable(true);
            leatherHelmet.setItemMeta(helmetMeta);
        }
        player.getInventory().setHelmet(leatherHelmet);

        // === GOLD ARMOR (UNBREAKABLE) ===
        ItemStack goldChestplate = new ItemStack(Material.GOLDEN_CHESTPLATE);
        ItemMeta chestMeta = goldChestplate.getItemMeta();
        if (chestMeta != null) {
            chestMeta.setUnbreakable(true);
            goldChestplate.setItemMeta(chestMeta);
        }
        player.getInventory().setChestplate(goldChestplate);

        ItemStack goldLeggings = new ItemStack(Material.GOLDEN_LEGGINGS);
        ItemMeta legsMeta = goldLeggings.getItemMeta();
        if (legsMeta != null) {
            legsMeta.setUnbreakable(true);
            goldLeggings.setItemMeta(legsMeta);
        }
        player.getInventory().setLeggings(goldLeggings);

        ItemStack goldBoots = new ItemStack(Material.GOLDEN_BOOTS);
        ItemMeta bootsMeta = goldBoots.getItemMeta();
        if (bootsMeta != null) {
            bootsMeta.setUnbreakable(true);
            goldBoots.setItemMeta(bootsMeta);
        }
        player.getInventory().setBoots(goldBoots);

        // === TOOLS (UNBREAKABLE) ===
        ItemStack stoneSword = new ItemStack(Material.STONE_SWORD);
        ItemMeta swordMeta = stoneSword.getItemMeta();
        if (swordMeta != null) {
            swordMeta.setUnbreakable(true);
            stoneSword.setItemMeta(swordMeta);
        }
        markKitItem(stoneSword);
        player.getInventory().addItem(stoneSword);

        ItemStack stoneAxe = new ItemStack(Material.STONE_AXE);
        ItemMeta axeMeta = stoneAxe.getItemMeta();
        if (axeMeta != null) {
            axeMeta.setUnbreakable(true);
            stoneAxe.setItemMeta(axeMeta);
        }
        markKitItem(stoneAxe);
        player.getInventory().addItem(stoneAxe);

        ItemStack shears = new ItemStack(Material.SHEARS);
        ItemMeta shearsMeta = shears.getItemMeta();
        if (shearsMeta != null) {
            shearsMeta.setUnbreakable(true);
            shears.setItemMeta(shearsMeta);
        }
        markKitItem(shears);
        player.getInventory().addItem(shears);

        // === FOOD ===
        ItemFactory factory = CashClashPlugin.getInstance().getItemFactory();
        ItemStack steak = factory.createGameplayItem(FoodItem.STEAK);
        steak.setAmount(8);
        markKitItem(steak);
        player.getInventory().addItem(steak);

        ItemStack bread = factory.createGameplayItem(FoodItem.BREAD);
        bread.setAmount(16);
        markKitItem(bread);
        player.getInventory().addItem(bread);

        // === UTILITY ===
        ItemStack waterBucket = new ItemStack(Material.WATER_BUCKET);
        markKitItem(waterBucket);
        player.getInventory().addItem(waterBucket);

        // === SHIELD ===
        // Fixed for the whole game session - no per-round swap
        setShield(player, shieldsEnabled);
    }

    private static int getSwordSlot(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType().name().contains("SWORD")) {
                return i;
            }
        }
        return -1;
    }

    private static int getAxeSlot(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType().name().contains("AXE")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Apply kit items for layout editing (no armor, just inventory items).
     * Used by LayoutManager to let players arrange items.
     */
    public static void applyForLayout(Kit kit, Player player) {
        player.getInventory().clear();

        // Stone tools
        player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        player.getInventory().addItem(new ItemStack(Material.STONE_AXE));

        player.getInventory().addItem(new ItemStack(Material.SHEARS));

        // Food (with ITEM_ID for refund tracking)
        ItemFactory factory = CashClashPlugin.getInstance().getItemFactory();
        ItemStack steak = factory.createGameplayItem(FoodItem.STEAK);
        steak.setAmount(8);
        player.getInventory().addItem(steak);

        ItemStack bread = factory.createGameplayItem(FoodItem.BREAD);
        bread.setAmount(16);
        player.getInventory().addItem(bread);

        // Water bucket
        player.getInventory().addItem(new ItemStack(Material.WATER_BUCKET));

        // Kit-specific items (no potion effects for layout)
        switch (kit) {
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
            case SPIDER -> player.getInventory().addItem(new ItemStack(Material.COBWEB, 8));
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
     * Round 1: Clear inventory, place base items with layout, apply shield
     * Round 2+: Don't reissue base items (see {@link #apply}), just re-run the shield toggle
     *
     * @param kit The kit to apply
     * @param player The player to give the kit to
     * @param layout Map of slot -> item identifier
     * @param round The current round number
     * @param shieldsEnabled Whether this game session has shields (fixed for every round)
     */
    public static void applyWithLayout(Kit kit, Player player, Map<Integer, String> layout, int round, boolean shieldsEnabled) {
        if (round == 1) {
            // Round 1 kits removed temporarily - all rounds get base items
            player.getInventory().clear();

            // Remove kit items if they exist (clean up from previous versions)
            removeKitItems(player);
            removeKitSpecificEnhancements(kit, player);

            giveBaseItemsWithLayout(player, layout);
        }

        // Always apply shield logic each round
        toggleShield(player, shieldsEnabled);
    }

    /**
     * Give base items with custom layout (only called in round 1).
     * This is similar to giveBaseItems but respects the layout.
     */
    private static void giveBaseItemsWithLayout(Player player, Map<Integer, String> layout) {
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

        itemMap.put("MATERIAL:SHEARS", new ItemStack(Material.SHEARS));

        // === FOOD ===
        ItemStack steak = CashClashPlugin.getInstance().getItemFactory().createGameplayItem(FoodItem.STEAK);
        steak.setAmount(8);
        itemMap.put("CUSTOM:STEAK", steak);
        itemMap.put("MATERIAL:COOKED_BEEF", steak); // Fallback

        ItemStack bread = CashClashPlugin.getInstance().getItemFactory().createGameplayItem(FoodItem.BREAD);
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
    private static void giveKitSpecificItemsWithLayout(Kit kit, Player player, Map<Integer, String> layout) {
        Map<String, ItemStack> itemMap = new HashMap<>();
        ItemFactory factory = CashClashPlugin.getInstance().getItemFactory();

        switch (kit) {
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
    private static void placeItemsWithLayout(Player player, Map<String, ItemStack> itemMap, Map<Integer, String> layout) {
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
    private static void applyTankEnchantments(Player player) {
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

    private static void markKitItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        PDCSetter.of(item).set(KIT_ITEM_KEY, PersistentDataType.BYTE, KIT_ITEM_FLAG).apply();
    }

    private static boolean isKitItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KIT_ITEM_KEY, PersistentDataType.BYTE);
    }

    private static void removeKitItems(Player player) {
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

    private static void removeKitSpecificEnhancements(Kit kit, Player player) {
        switch (kit) {
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

    private static void removeProtectionIfBaseItem(ItemStack item, Material baseType) {
        if (item == null || item.getType() != baseType) return;
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.removeEnchant(Enchantment.PROTECTION);
        item.setItemMeta(meta);
    }

    private static void removeSharpnessFromBaseTool(Player player, Material baseType) {
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
