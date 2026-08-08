package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import me.psikuvit.cashClash.util.items.PDCSetter;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Governs the toggleable enchant-rune items bought from the Enchants shop category. All static -
 * every piece of state (link target, active flag, level, durability, cooldown timers) is persisted
 * as PDC tags on the rune/target items themselves rather than held in memory here.
 *
 * <p>Two application models depending on enchant type:
 * <ul>
 *   <li><b>Weapon/bow runes</b> (Sharpness, Knockback, Power, etc.) link to exactly one specific
 *   item, tracked by a persistent {@link Keys#ITEM_UUID} tag on the target so it can be found
 *   again wherever it's moved to. {@link #toggleRune} applies/removes the enchant on that one
 *   linked item only.</li>
 *   <li><b>Armor runes</b> (Protection, Projectile Protection) apply to the player's entire
 *   currently worn armor set instead of a single linked piece - see
 *   {@link #applyArmorRuneToAllWorn}/{@link #removeArmorRuneFromAllWorn} and
 *   {@link #syncArmorRuneOnEquipChange}, which keeps this live as pieces are swapped.</li>
 * </ul>
 */
public class RuneManager {

    private static final int MAX_ACTIVE_RUNES = 2;
    private static final int TOGGLE_COOLDOWN_SECONDS = 5;
    private static final long BROKEN_DURATION_MS = 10_000;
    private static final long RECHARGE_DELAY_MS = 3_000;
    private static final double RECHARGE_PER_SECOND = 1.5;

    // How high above the player the activation book spawns and hovers
    private static final double BOOK_HOVER_HEIGHT = 2.75;

    private RuneManager() {
        throw new AssertionError("Nope.");
    }

    // ==================== ITEM IDENTITY / LINKING ====================

    /**
     * Tags an item with a stable random UUID (if it doesn't already have one) so it can be found
     * again later wherever it's moved to - see {@link #findSlotByItemUUID}.
     */
    private static void ensureItemUUID(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        if (PDCDetection.hasKey(item, Keys.ITEM_UUID)) return;

        PDCSetter.of(item).set(Keys.ITEM_UUID, PersistentDataType.STRING, UUID.randomUUID().toString()).apply();
    }

    private static String getItemUUID(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        return PDCDetection.readTag(item, Keys.ITEM_UUID);
    }

    /**
     * Links a rune to a target item by tagging the rune with the target's {@link Keys#ITEM_UUID}
     * (assigning one first if the target doesn't have one yet).
     */
    public static void setRuneLink(ItemStack rune, ItemStack target) {
        if (rune == null || target == null) return;
        if (rune.getType().isAir() || target.getType().isAir()) return;
        if (!rune.hasItemMeta()) return;

        ensureItemUUID(target);
        String targetUUID = getItemUUID(target);
        if (targetUUID == null) return;

        PDCSetter.of(rune).set(Keys.RUNE_LINK, PersistentDataType.STRING, targetUUID).apply();
    }

    public static void clearRuneLink(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return;

        PDCSetter.of(rune).remove(Keys.RUNE_LINK).apply();
    }

    /**
     * Resolves a rune's linked item, wherever it currently sits - a main inventory slot, an
     * equipped armor slot, or the off-hand (armor runes link to equipped armor rather than
     * storage).
     */
    public static ItemStack getLinkedItem(Player player, ItemStack rune) {
        if (player == null || rune == null || !rune.hasItemMeta()) return null;

        String linkedUUID = PDCDetection.readTag(rune, Keys.RUNE_LINK);
        if (linkedUUID == null) return null;

        for (ItemStack item : player.getInventory().getContents()) {
            if (linkedUUID.equals(getItemUUID(item))) return item;
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (linkedUUID.equals(getItemUUID(item))) return item;
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (linkedUUID.equals(getItemUUID(offhand))) return offhand;

        return null;
    }

    /**
     * Inventory slot index of the item carrying the given {@link Keys#ITEM_UUID} tag, or -1 if
     * it isn't in a main inventory slot (it may instead be equipped armor, off-hand, or on the
     * cursor - see {@link #persistLinkedItem}).
     */
    private static int findSlotByItemUUID(Player player, String uuid) {
        if (player == null || uuid == null) return -1;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (uuid.equals(getItemUUID(contents[i]))) return i;
        }
        return -1;
    }

    /**
     * Writes a modified item back to wherever it currently sits - a main inventory slot, an
     * equipped armor slot, the off-hand, or the cursor - by matching its {@link Keys#ITEM_UUID}
     * tag. Needed because a rune's linked item can be any of those (armor runes link to equipped
     * armor, and unequipping one via an armor-slot click puts the old piece on the cursor), and
     * getArmorContents()/off-hand/cursor mutations don't persist without an explicit write-back.
     */
    private static void persistLinkedItem(Player player, ItemStack item) {
        if (player == null || item == null) return;
        String uuid = getItemUUID(item);
        if (uuid == null) return;

        int slot = findSlotByItemUUID(player, uuid);
        if (slot != -1) {
            player.getInventory().setItem(slot, item);
            return;
        }

        PlayerInventory inv = player.getInventory();
        if (uuid.equals(getItemUUID(inv.getHelmet()))) {
            inv.setHelmet(item);
        } else if (uuid.equals(getItemUUID(inv.getChestplate()))) {
            inv.setChestplate(item);
        } else if (uuid.equals(getItemUUID(inv.getLeggings()))) {
            inv.setLeggings(item);
        } else if (uuid.equals(getItemUUID(inv.getBoots()))) {
            inv.setBoots(item);
        } else if (uuid.equals(getItemUUID(inv.getItemInOffHand()))) {
            inv.setItemInOffHand(item);
        } else if (uuid.equals(getItemUUID(player.getItemOnCursor()))) {
            player.setItemOnCursor(item);
        }
    }

    /**
     * Writes a modified rune back into the player's main inventory by matching its
     * {@link Keys#ITEM_UUID}.
     */
    private static void persistRune(Player player, ItemStack rune) {
        if (player == null || rune == null) return;

        String uuid = getItemUUID(rune);
        if (uuid == null) return;

        int slot = findSlotByItemUUID(player, uuid);
        if (slot != -1) {
            player.getInventory().setItem(slot, rune);
        }
    }

    // ==================== RUNE STATE ====================

    public static boolean isRune(ItemStack item) {
        return item != null && !item.getType().isAir() && PDCDetection.getRune(item) != null;
    }

    public static boolean isRuneActive(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return false;

        Byte active = PDCDetection.readByteTag(rune, Keys.RUNE_ACTIVE);
        return active != null && active == 1;
    }

    /**
     * Flips the rune's active flag and its own visible enchant glow. Callers are responsible for
     * applying/removing the enchant on the linked target(s) separately - see {@link #toggleRune}.
     */
    private static void setRuneActive(ItemStack rune, boolean active) {
        if (rune == null || !rune.hasItemMeta()) return;

        PDCSetter tags = PDCSetter.of(rune);
        tags.set(Keys.RUNE_ACTIVE, PersistentDataType.BYTE, (byte) (active ? 1 : 0));

        EnchantEntry enchant = PDCDetection.getRune(rune);
        if (enchant != null) {
            if (active) {
                tags.meta().addEnchant(enchant.getEnchantment(), 1, true);
            } else {
                tags.meta().removeEnchant(enchant.getEnchantment());
            }
        }

        tags.apply();
    }

    private static Integer getRuneLevel(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return null;
        return PDCDetection.readIntTag(rune, Keys.RUNE_LEVEL);
    }

    private static int getActiveRuneCount(Player player) {
        if (player == null) return 0;

        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isRune(item) && isRuneActive(item)) count++;
        }
        return count;
    }

    /**
     * The player's currently active rune of a given enchant type, if any.
     */
    public static ItemStack getActiveRune(Player player, EnchantEntry enchant) {
        if (player == null || enchant == null) return null;

        for (ItemStack item : player.getInventory().getContents()) {
            if (isRune(item) && isRuneActive(item) && PDCDetection.getRune(item) == enchant) {
                return item;
            }
        }
        return null;
    }

    // ==================== TOGGLE / APPLY / REMOVE ====================

    /**
     * Toggles a rune on or off. Activating links it to a target if not already linked (or, for
     * armor runes, applies to the whole worn set) and applies the enchant; deactivating removes
     * it. The rune and target copies are written back to the inventory.
     *
     * @return true if the rune's state actually changed
     */
    public static boolean toggleRune(Player player, ItemStack rune) {
        if (player == null || rune == null) return false;

        if (isRuneBroken(rune)) {
            Messages.send(player, "rune.broken-cannot-use");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        EnchantEntry enchantEntry = PDCDetection.getRune(rune);
        if (enchantEntry == null) return false;

        ensureItemUUID(rune);
        String runeUUID = getItemUUID(rune);
        if (runeUUID == null) return false;

        UUID playerUUID = player.getUniqueId();
        String cooldownKey = toggleCooldownKey(runeUUID);
        CooldownManager cooldowns = CooldownManager.getInstance();

        if (cooldowns.isOnCooldown(playerUUID, cooldownKey)) {
            long remaining = cooldowns.getRemainingCooldownSeconds(playerUUID, cooldownKey);
            Messages.send(player, "rune.toggle-cooldown", "seconds", String.valueOf(remaining));
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        boolean toggledOn = isRuneActive(rune)
                ? deactivateRune(player, rune, enchantEntry)
                : activateRune(player, rune, enchantEntry);

        if (toggledOn) {
            cooldowns.setCooldownSeconds(playerUUID, cooldownKey, TOGGLE_COOLDOWN_SECONDS);
        }
        return toggledOn;
    }

    private static String toggleCooldownKey(String runeUUID) {
        return CooldownManager.Keys.RUNE_TOGGLE + "_" + runeUUID;
    }

    /**
     * Links the rune to a target if not already linked (armor runes apply to the whole worn set
     * instead), applies the enchant, and plays the activation visuals.
     *
     * @return false if there's nothing eligible to enchant, or the max active runes are already in use
     */
    private static boolean activateRune(Player player, ItemStack rune, EnchantEntry enchantEntry) {
        if (getActiveRuneCount(player) >= MAX_ACTIVE_RUNES) {
            Messages.send(player, "rune.max-active");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        if (isArmorEnchant(enchantEntry)) {
            // Protection/Projectile Protection enchant every currently equipped armor piece
            // they can apply to, not a single linked piece like every other rune.
            if (applyArmorRuneToAllWorn(player, rune) == 0) {
                Messages.send(player, "rune.no-valid-item");
                SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return false;
            }
        } else {
            ItemStack target = getLinkedItem(player, rune);
            if (target == null) {
                // Not linked yet - auto-link to the first applicable item instead of failing
                target = findFirstApplicableItem(player, rune);
                if (target == null) {
                    Messages.send(player, "rune.no-valid-item");
                    SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return false;
                }
                setRuneLink(rune, target);
            }
            applyRune(target, rune);
            persistLinkedItem(player, target);
        }

        setRuneActive(rune, true);
        player.getInventory().setItemInMainHand(rune);

        try {
            playRuneActivation(player, enchantEntry);
            SoundUtils.play(player, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        } catch (Exception ex) {
            Messages.debug("RUNES", "Activation visual failed: " + ex.getMessage());
        }
        return true;
    }

    /**
     * Strips the enchant (from the whole worn set for armor runes, or the single linked item
     * otherwise) and plays the deactivation visuals. Always succeeds.
     */
    private static boolean deactivateRune(Player player, ItemStack rune, EnchantEntry enchantEntry) {
        setRuneActive(rune, false);
        setRuneOffTime(rune);

        if (isArmorEnchant(enchantEntry)) {
            removeArmorRuneFromAllWorn(player, rune);
        } else {
            ItemStack linkedItem = getLinkedItem(player, rune);
            if (linkedItem != null) {
                removeRune(linkedItem, rune);
                persistLinkedItem(player, linkedItem);
            }
        }
        player.getInventory().setItemInMainHand(rune);

        try {
            playRuneDeactivation(player, enchantEntry);
            SoundUtils.play(player, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
        } catch (Exception ex) {
            Messages.debug("RUNES", "Deactivation visual failed: " + ex.getMessage());
        }
        return true;
    }

    /**
     * Finds the first inventory item this rune's enchant can apply to, for auto-linking when the
     * player toggles a rune that hasn't been manually linked yet.
     */
    private static ItemStack findFirstApplicableItem(Player player, ItemStack rune) {
        if (player == null || rune == null) return null;

        EnchantEntry enchantEntry = PDCDetection.getRune(rune);
        if (enchantEntry == null) return null;

        // Armor runes (PROTECTION/PROJECTILE_PROTECTION) must only ever target armor the
        // player is currently wearing - never a spare piece sitting in the main inventory
        // (e.g. gear replaced by a shop upgrade but still carried). Weapon/bow runes keep
        // scanning the general inventory since they have no "equipped" slot of their own.
        if (isArmorEnchant(enchantEntry)) {
            for (ItemStack item : player.getInventory().getArmorContents()) {
                if (item != null && enchantEntry.canApplyTo(item)) return item;
            }
            return null;
        }

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || isRune(item)) continue;
            if (enchantEntry.canApplyTo(item)) return item;
        }
        return null;
    }

    /**
     * True for enchants whose applicable materials are all armor pieces (PROTECTION,
     * PROJECTILE_PROTECTION) - these must resolve against equipped armor only, see
     * {@link #findFirstApplicableItem}.
     */
    private static boolean isArmorEnchant(EnchantEntry enchantEntry) {
        return enchantEntry == EnchantEntry.PROTECTION || enchantEntry == EnchantEntry.PROJECTILE_PROTECTION;
    }

    /**
     * Applies the rune's enchant to its linked target - a specific, single item. Used for every
     * rune type except PROTECTION/PROJECTILE_PROTECTION, which enchant the player's whole worn
     * armor set instead (see {@link #applyArmorRuneToAllWorn}). Caller is responsible for
     * persisting the mutated target back via {@link #persistLinkedItem}.
     */
    private static void applyRune(ItemStack target, ItemStack rune) {
        if (target == null || rune == null) return;

        EnchantEntry enchantEntry = PDCDetection.getRune(rune);
        Integer level = getRuneLevel(rune);
        if (enchantEntry == null || level == null) return;

        PDCSetter setter = PDCSetter.of(target);
        setter.meta().addEnchant(enchantEntry.getEnchantment(), level, true);
        setter.apply();
    }

    /**
     * Removes the rune's enchant from its linked target only - see {@link #applyRune}.
     */
    private static void removeRune(ItemStack target, ItemStack rune) {
        if (target == null || rune == null) return;

        EnchantEntry enchantEntry = PDCDetection.getRune(rune);
        if (enchantEntry == null) return;

        PDCSetter setter = PDCSetter.of(target);
        setter.meta().removeEnchant(enchantEntry.getEnchantment());
        setter.apply();
    }

    /**
     * PROTECTION/PROJECTILE_PROTECTION apply to the player's whole worn armor set rather than a
     * single linked piece (manual linking of these two types is refused entirely - see
     * GameListener's rune-link handling). Applies the rune's enchant to every currently equipped
     * armor piece it's eligible for and writes the armor slots back.
     *
     * @return how many pieces were enchanted, so the caller can fail the toggle if the player
     *         isn't wearing any eligible armor at all
     */
    private static int applyArmorRuneToAllWorn(Player player, ItemStack rune) {
        if (player == null || rune == null) return 0;

        EnchantEntry enchantEntry = PDCDetection.getRune(rune);
        Integer level = getRuneLevel(rune);
        if (enchantEntry == null || level == null) return 0;

        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        int applied = 0;

        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null || piece.getType().isAir() || !enchantEntry.canApplyTo(piece)) continue;

            // Tag it now, while we know it's the piece being enchanted - lets a later unequip
            // find it again (by UUID, wherever it lands) to strip the enchant back off, see
            // syncArmorRuneOnEquipChange.
            ensureItemUUID(piece);

            PDCSetter setter = PDCSetter.of(piece);
            setter.meta().addEnchant(enchantEntry.getEnchantment(), level, true);
            setter.apply();
            applied++;
        }

        if (applied > 0) inv.setArmorContents(armor);
        return applied;
    }

    /**
     * Counterpart to {@link #applyArmorRuneToAllWorn} - strips the rune's enchant from every
     * currently equipped armor piece that has it.
     */
    private static void removeArmorRuneFromAllWorn(Player player, ItemStack rune) {
        if (player == null || rune == null) return;

        EnchantEntry enchantEntry = PDCDetection.getRune(rune);
        if (enchantEntry == null) return;

        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        boolean modified = false;

        for (ItemStack piece : armor) {
            if (piece == null || piece.getType().isAir()) continue;
            if (piece.getItemMeta() == null || !piece.getItemMeta().hasEnchant(enchantEntry.getEnchantment())) continue;

            PDCSetter setter = PDCSetter.of(piece);
            setter.meta().removeEnchant(enchantEntry.getEnchantment());
            setter.apply();
            modified = true;
        }

        if (modified) inv.setArmorContents(armor);
    }

    /**
     * Keeps active PROTECTION/PROJECTILE_PROTECTION runes strictly tied to whatever is currently
     * worn, live - called on every armor slot change regardless of cause (manual swap,
     * shift-click, hotbar equip, dispenser, plugin call). The piece that just left a slot loses
     * any active armor rune's enchant, wherever it landed; the piece that's now worn gains it if
     * an armor rune is active and it's an eligible material. No-ops entirely if neither armor
     * rune is active.
     */
    public static void syncArmorRuneOnEquipChange(Player player, ItemStack oldItem, ItemStack newItem) {
        if (player == null) return;

        ItemStack protectionRune = getActiveRune(player, EnchantEntry.PROTECTION);
        ItemStack projectileRune = getActiveRune(player, EnchantEntry.PROJECTILE_PROTECTION);
        if (protectionRune == null && projectileRune == null) return;

        if (oldItem != null && !oldItem.getType().isAir() && oldItem.getItemMeta() != null) {
            ItemMeta meta = oldItem.getItemMeta();
            boolean changed = false;
            if (protectionRune != null && meta.hasEnchant(EnchantEntry.PROTECTION.getEnchantment())) {
                meta.removeEnchant(EnchantEntry.PROTECTION.getEnchantment());
                changed = true;
            }
            if (projectileRune != null && meta.hasEnchant(EnchantEntry.PROJECTILE_PROTECTION.getEnchantment())) {
                meta.removeEnchant(EnchantEntry.PROJECTILE_PROTECTION.getEnchantment());
                changed = true;
            }
            if (changed) {
                oldItem.setItemMeta(meta);
                persistLinkedItem(player, oldItem);
            }
        }

        // Re-running the "apply to everything currently worn" pass covers the newly-equipped
        // piece too, since it's now part of that set - no separate single-item apply needed.
        if (newItem != null && !newItem.getType().isAir()) {
            if (protectionRune != null) applyArmorRuneToAllWorn(player, protectionRune);
            if (projectileRune != null) applyArmorRuneToAllWorn(player, projectileRune);
        }
    }

    // ==================== RUNE DURABILITY ====================

    private static double getRuneDurability(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return 0;

        Double durability = PDCDetection.readDoubleTag(rune, Keys.RUNE_DURABILITY);
        return durability != null ? durability : 0;
    }

    private static void setRuneDurability(ItemStack rune, double amount) {
        if (rune == null || !rune.hasItemMeta()) return;

        PDCSetter.of(rune).set(Keys.RUNE_DURABILITY, PersistentDataType.DOUBLE, amount).apply();
    }

    private static double getMaxRuneDurability(EnchantEntry enchant) {
        return switch (enchant) {
            case SHARPNESS, PROTECTION, QUICK_CHARGE -> 30;
            case FIRE_ASPECT, KNOCKBACK, FLAME -> 15;
            case POWER, PROJECTILE_PROTECTION -> 18;
            case PUNCH -> 10;
            case PIERCING -> 24;
        };
    }

    /**
     * Redraws the rune's vanilla durability bar to reflect its current rune-durability fraction.
     */
    private static void updateRuneDurabilityBar(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return;

        EnchantEntry enchant = PDCDetection.getRune(rune);
        if (enchant == null) return;

        ItemMeta meta = rune.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;

        double fraction = getRuneDurability(rune) / getMaxRuneDurability(enchant);
        int maxDamage = rune.getType().getMaxDurability();
        damageable.setDamage((int) Math.round(maxDamage - (fraction * maxDamage)));

        rune.setItemMeta(meta);
    }

    /**
     * Sets a freshly-purchased rune to full durability. No-op if it's already been initialized
     * (durability tag already present).
     */
    public static void initializeRuneDurability(ItemStack rune, EnchantEntry enchant) {
        if (rune == null) return;
        if (rune.hasItemMeta() && PDCDetection.hasKey(rune, Keys.RUNE_DURABILITY)) return;

        setRuneDurability(rune, getMaxRuneDurability(enchant));
        updateRuneDurabilityBar(rune);
    }

    /**
     * Drains one rune-level's worth of durability on hit. Breaks (deactivating and stripping the
     * enchant) and starts the broken-cooldown when it hits zero.
     *
     * @return true if this hit broke the rune
     */
    public static boolean consumeRuneDurability(Player player, ItemStack rune) {
        if (player == null || rune == null || !isRune(rune)) return false;

        EnchantEntry enchant = PDCDetection.getRune(rune);
        Integer level = getRuneLevel(rune);
        if (enchant == null || level == null) return false;

        double remaining = getRuneDurability(rune) - level;

        if (remaining > 0) {
            setRuneDurability(rune, remaining);
            updateRuneDurabilityBar(rune);
            persistRune(player, rune);
            return false;
        }

        setRuneDurability(rune, 0);
        updateRuneDurabilityBar(rune);
        setRuneActive(rune, false);

        ItemStack linkedItem = getLinkedItem(player, rune);
        if (linkedItem != null) {
            removeRune(linkedItem, rune);
            persistLinkedItem(player, linkedItem);
        }

        setRuneBroken(rune);
        setRuneOffTime(rune);
        persistRune(player, rune);

        Messages.send(player, "rune.broken", "rune", enchant.getDisplayName());
        SoundUtils.play(player, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        return true;
    }

    private static void setRuneBroken(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return;

        PDCSetter.of(rune).set(Keys.RUNE_BROKEN_TIME, PersistentDataType.LONG, System.currentTimeMillis()).apply();
    }

    private static void clearRuneBroken(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return;

        PDCSetter.of(rune).remove(Keys.RUNE_BROKEN_TIME).apply();
    }

    public static boolean isRuneBroken(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return false;

        Long brokenTime = PDCDetection.readLongTag(rune, Keys.RUNE_BROKEN_TIME);
        if (brokenTime == null) return false;

        if (System.currentTimeMillis() - brokenTime >= BROKEN_DURATION_MS) {
            clearRuneBroken(rune);
            return false;
        }
        return true;
    }

    private static void setRuneOffTime(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return;

        PDCSetter.of(rune).set(Keys.RUNE_OFF_TIME, PersistentDataType.LONG, System.currentTimeMillis()).apply();
    }

    private static boolean canRuneRecharge(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta() || isRuneBroken(rune)) return false;

        Long offTime = PDCDetection.readLongTag(rune, Keys.RUNE_OFF_TIME);
        return offTime != null && System.currentTimeMillis() - offTime >= RECHARGE_DELAY_MS;
    }

    private static boolean hasFullChargeWarning(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return false;

        Byte warning = PDCDetection.readByteTag(rune, Keys.RUNE_FULL_CHARGE_WARNING);
        return warning != null && warning == 1;
    }

    private static void setFullChargeWarning(ItemStack rune, boolean warned) {
        if (rune == null || !rune.hasItemMeta()) return;

        PDCSetter.of(rune).set(Keys.RUNE_FULL_CHARGE_WARNING, PersistentDataType.BYTE, (byte) (warned ? 1 : 0)).apply();
    }

    /**
     * Ticks every online player's inactive runes back up toward full durability once a second,
     * starting {@code RECHARGE_DELAY_MS} after they were last toggled off. Notifies the player
     * once, the first time a rune reaches full charge after recharging.
     */
    public static void startRuneRechargeTask() {
        SchedulerUtils.runTaskTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ItemStack[] contents = player.getInventory().getContents();
                boolean changed = false;

                for (int i = 0; i < contents.length; i++) {
                    if (rechargeOneRune(player, contents[i])) changed = true;
                }

                if (changed) player.getInventory().setContents(contents);
            }
        }, 20L, 20L);
    }

    /**
     * @return true if the item's contents were mutated and need writing back to the inventory
     */
    private static boolean rechargeOneRune(Player player, ItemStack item) {
        if (!isRune(item) || isRuneActive(item) || !canRuneRecharge(item)) return false;

        EnchantEntry enchant = PDCDetection.getRune(item);
        double max = getMaxRuneDurability(enchant);
        double current = getRuneDurability(item);
        if (current >= max) return false;

        if (hasFullChargeWarning(item)) {
            setFullChargeWarning(item, false);
        }

        double newAmount = Math.min(current + RECHARGE_PER_SECOND, max);
        setRuneDurability(item, newAmount);
        updateRuneDurabilityBar(item);

        if (newAmount >= max && !hasFullChargeWarning(item)) {
            Messages.send(player, "rune.recharged", "rune", enchant.getDisplayName());
            SoundUtils.play(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
            setFullChargeWarning(item, true);
        }

        return true;
    }

    // ==================== RUNE VISUALS ====================

    private static void playRuneActivation(Player player, EnchantEntry enchant) {
        // Anchor the book to the player's position so it always travels the same
        // distance upward, even when the player is midair.
        double animationY = player.getLocation().getY();
        spawnRuneParticles(player, enchant);
        ItemDisplay book = spawnRuneBook(player, animationY, BOOK_HOVER_HEIGHT);

        final int duration = 60;

        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || book.isDead()) {
                    book.remove();
                    cancel();
                    return;
                }

                tick++;

                Location playerLoc = player.getLocation().clone();
                playerLoc.setY(animationY);

                double progress = tick / (double) duration;
                double eased = 1 - Math.pow(1 - progress, 3);
                double radius = 2.5 * (1 - eased);
                double angle = tick * 0.25;

                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;

                Location newLoc = playerLoc.clone().add(x, BOOK_HOVER_HEIGHT, z);
                book.teleport(newLoc);

                double spinSpeed = 15;
                if (tick >= 50 && tick <= 53) {
                    spinSpeed *= (53 - tick) / 3.0;
                } else if (tick > 53) {
                    spinSpeed = 0;
                }
                spinBook(book, (float) spinSpeed);

                if (tick >= duration) {
                    book.remove();
                    cancel();
                }
            }
        }, 0L, 1L);
    }

    private static void playRuneDeactivation(Player player, EnchantEntry enchant) {
        // Anchor to the player's position the same way activation does, so the book hovers in
        // the same area for both. Spawns 1 block lower than activation since this animation
        // rises over its duration - starting at the same height would end up too high.
        double animationY = player.getLocation().getY();
        double spawnHeight = BOOK_HOVER_HEIGHT - 1.0;
        ItemDisplay book = spawnRuneBook(player, animationY, spawnHeight);
        final int duration = 32; // 1.6 seconds

        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || book.isDead()) {
                    book.remove();
                    cancel();
                    return;
                }

                tick++;

                Location playerLoc = player.getLocation().clone();
                playerLoc.setY(animationY);

                double progress = tick / (double) duration;
                double eased = 1 - Math.pow(1 - progress, 3);
                double height = spawnHeight + (eased * 0.7);

                book.teleport(playerLoc.clone().add(0, height, 0));

                // Slow spin at first, begin slowing down earlier, then stop
                double spinSpeed = 18;
                if (tick >= 20 && tick <= 26) {
                    spinSpeed *= (26 - tick) / 6.0;
                } else if (tick > 20) {
                    spinSpeed = 0;
                }
                spinBook(book, (float) spinSpeed);

                if (tick >= duration) {
                    book.remove();

                    // Small, condensed burst, at the height the book actually rose to
                    Location center = player.getLocation().clone().add(0, spawnHeight + 0.7, 0);
                    ParticleUtils.spawnDust(center, getRuneColor(enchant), 1.1f, 12, 0.25, 0.25, 0.25);
                    cancel();
                }
            }
        }, 0L, 1L);
    }

    private static ItemDisplay spawnRuneBook(Player player, double animationY, double heightOffset) {
        ItemDisplay display = player.getWorld().spawn(
                getRuneStartLocation(player, animationY, heightOffset),
                ItemDisplay.class
        );

        display.setItemStack(new ItemStack(Material.ENCHANTED_BOOK));
        display.setBillboard(Display.Billboard.FIXED);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
        display.setGravity(false);
        display.setInvulnerable(true);
        return display;
    }

    private static Location getRuneStartLocation(Player player, double animationY, double heightOffset) {
        Location location = player.getLocation().clone();
        location.setY(animationY);

        Vector forward = location.getDirection().setY(0).normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());

        return location
                .add(forward.multiply(-2))
                .add(right.multiply(2))
                .add(0, heightOffset, 0);
    }

    private static void spinBook(ItemDisplay book, float speedDegrees) {
        Transformation transformation = book.getTransformation();
        transformation.getLeftRotation().rotateY((float) Math.toRadians(speedDegrees));
        book.setTransformation(transformation);
    }

    private static void spawnRuneParticles(Player player, EnchantEntry enchant) {
        Location center = player.getLocation().clone().add(0, 1.2, 0);

        ParticleUtils.spawn(Particle.ENCHANT, center, 40, 0.6, 0.6, 0.6, 0.15);
        ParticleUtils.spawnDust(center, getRuneColor(enchant), 1.2f, 25, 0.6, 0.6, 0.6);
    }

    private static Color getRuneColor(EnchantEntry enchant) {
        return switch (enchant) {
            case SHARPNESS -> Color.RED;
            case FIRE_ASPECT -> Color.fromRGB(255, 140, 0);
            case KNOCKBACK -> Color.GREEN;
            case PROTECTION -> Color.BLUE;
            case PROJECTILE_PROTECTION -> Color.AQUA;
            case POWER -> Color.fromRGB(75, 0, 130);
            case FLAME -> Color.YELLOW;
            case PUNCH -> Color.fromRGB(255, 105, 180);
            case PIERCING -> Color.fromRGB(138, 43, 226);
            case QUICK_CHARGE -> Color.WHITE;
        };
    }
}
