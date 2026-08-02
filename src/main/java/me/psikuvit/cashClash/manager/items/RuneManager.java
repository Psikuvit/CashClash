package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RuneManager {

    private static final Set<UUID> runeShiftLock = new HashSet<>();

    public static void ensureItemUUID(ItemStack item) {
        if (item == null || item.getType().isAir()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!pdc.has(Keys.ITEM_UUID, PersistentDataType.STRING)) {
            pdc.set(
                    Keys.ITEM_UUID,
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
            );
            item.setItemMeta(meta);
        }
    }

    public static String getItemUUID(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        if (!item.hasItemMeta()) return null;

        return item.getItemMeta()
                .getPersistentDataContainer()
                .get(Keys.ITEM_UUID, PersistentDataType.STRING);
    }

    public static void setRuneLink(ItemStack rune, ItemStack target) {
        if (rune == null || target == null) return;
        if (rune.getType().isAir() || target.getType().isAir()) return;

        ensureItemUUID(target);
        String uuid = getItemUUID(target);
        if (uuid == null) return;
        if (!rune.hasItemMeta()) return;
        ItemMeta meta = rune.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(
                Keys.RUNE_LINK,
                PersistentDataType.STRING,
                uuid
        );
        rune.setItemMeta(meta);
    }

    public static ItemStack getLinkedItem(Player player, ItemStack rune) {
        if (player == null || rune == null) return null;

        if (!rune.hasItemMeta()) return null;

        String linkedUUID = rune.getItemMeta()
                .getPersistentDataContainer()
                .get(Keys.RUNE_LINK, PersistentDataType.STRING);

        if (linkedUUID == null) return null;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            String itemUUID = getItemUUID(item);
            if (linkedUUID.equals(itemUUID)) {
                return item;
            }
        }

        return null;
    }

    public static boolean isRune(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return PDCDetection.getRune(item) != null;
    }

    public static boolean isRuneActive(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return false;

        Byte active = rune.getItemMeta()
                .getPersistentDataContainer()
                .get(Keys.RUNE_ACTIVE, PersistentDataType.BYTE);

        return active != null && active == 1;
    }

    public static void setRuneActive(ItemStack rune, boolean active) {
        if (rune == null || !rune.hasItemMeta()) return;

        ItemMeta meta = rune.getItemMeta();

        meta.getPersistentDataContainer().set(
                Keys.RUNE_ACTIVE,
                PersistentDataType.BYTE,
                (byte) (active ? 1 : 0)
        );

        EnchantEntry enchant = PDCDetection.getRune(rune);

        if (enchant != null) {
            if (active) {
                meta.addEnchant(enchant.getEnchantment(), 1, true);
            } else {
                meta.removeEnchant(enchant.getEnchantment());
            }
        }

        rune.setItemMeta(meta);
    }

    public static boolean toggleRune(Player player, ItemStack rune) {
        if (player == null || rune == null) return false;
        if (isRuneBroken(rune)) {
            Messages.send(player, "rune.broken-cannot-use");
            SoundUtils.play(
                    player,
                    Sound.ENTITY_VILLAGER_NO,
                    1.0f,
                    1.0f
            );
            return false;
        }

        EnchantEntry enchantEntry = PDCDetection.getRune(rune);
        if (enchantEntry == null) return false;

        String runeUUID = getItemUUID(rune);

        if (runeUUID == null) {
            ensureItemUUID(rune);
            runeUUID = getItemUUID(rune);
        }

        if (runeUUID == null) return false;

        if (CooldownManager.getInstance().isOnCooldown(
                player.getUniqueId(),
                CooldownManager.Keys.RUNE_TOGGLE + "_" + runeUUID
        )) {
            long remaining = CooldownManager.getInstance()
                    .getRemainingCooldownSeconds(
                            player.getUniqueId(),
                            CooldownManager.Keys.RUNE_TOGGLE + "_" + runeUUID);

            Messages.send(player, "rune.toggle-cooldown",
                    "seconds", String.valueOf(remaining));

            SoundUtils.play(
                    player,
                    Sound.ENTITY_VILLAGER_NO,
                    1.0f,
                    1.0f
            );

            return false;
        }

        if (isRuneActive(rune)) {
            ItemStack linkedItem = getLinkedItem(player, rune);
            setRuneActive(rune, false);
            playRuneDeactivation(player, enchantEntry);
            setRuneOffTime(rune);
            if (linkedItem != null) {
                removeRune(player, linkedItem, rune);

                if (enchantEntry == EnchantEntry.PROTECTION ||
                        enchantEntry == EnchantEntry.PROJECTILE_PROTECTION) {

                    updateArmorRunes(player);
                }
            }
            CooldownManager.getInstance().setCooldownSeconds(
                    player.getUniqueId(),
                    CooldownManager.Keys.RUNE_TOGGLE + "_" + runeUUID,
                    5
            );
            lockRuneShift(player);
            return true;
        }

        if (getActiveRuneCount(player) >= 2) {
            Messages.send(player, "rune.max-active");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }

        ItemStack target = getLinkedItem(player, rune);

        if (target == null) {

            if (isRuneActive(rune)) {
                Messages.send(player, "rune.active-cannot-switch");
                SoundUtils.play(
                        player,
                        Sound.ENTITY_VILLAGER_NO,
                        1.0f,
                        1.0f
                );
                return false;
            }

            target = findFirstApplicableItem(player, rune);

            if (target == null) {
                Messages.send(player, "rune.no-valid-item");
                return false;
            }

            setRuneLink(rune, target);
        }
        // Link rune if it wasn't already linked
        applyRune(player, target, rune);
        setRuneActive(rune, true);
        playRuneActivation(player, enchantEntry);

        if (enchantEntry == EnchantEntry.PROTECTION ||
                enchantEntry == EnchantEntry.PROJECTILE_PROTECTION) {

            updateArmorRunes(player);
        }

        CooldownManager.getInstance().setCooldownSeconds(
                player.getUniqueId(),
                CooldownManager.Keys.RUNE_TOGGLE + "_" + runeUUID,
                5
        );

        lockRuneShift(player);
        return true;
    }

    public static ItemStack findFirstApplicableItem(Player player, ItemStack rune) {
        if (player == null || rune == null) return null;

        EnchantEntry enchantEntry = PDCDetection.getRune(rune);
        if (enchantEntry == null) return null;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;

            if (enchantEntry.canApplyTo(item)) {
                return item;
            }
        }

        return null;
    }

    public static void applyRune(Player player, ItemStack target, ItemStack rune) {
        if (player == null || rune == null) return;

        EnchantEntry enchantEntry = PDCDetection.getRune(rune);
        if (enchantEntry == null) return;

        Integer level = rune.getItemMeta()
                .getPersistentDataContainer()
                .get(Keys.RUNE_LEVEL, PersistentDataType.INTEGER);

        if (level == null) return;

        int damage = level;

        // Armor runes update based on currently equipped armor
        if (enchantEntry == EnchantEntry.PROTECTION ||
                enchantEntry == EnchantEntry.PROJECTILE_PROTECTION) {

            updateArmorRunes(player);
            return;
        }

        // Normal item enchant
        if (target == null) return;

        ItemMeta meta = target.getItemMeta();
        if (meta == null) return;

        meta.addEnchant(
                enchantEntry.getEnchantment(),
                level,
                true
        );

        target.setItemMeta(meta);
    }

    public static void removeRune(Player player, ItemStack target, ItemStack rune) {
        if (player == null || rune == null) return;

        EnchantEntry enchantEntry = PDCDetection.getRune(rune);
        if (enchantEntry == null) return;


        // Armor runes remove from all equipped armor
        if (enchantEntry == EnchantEntry.PROTECTION ||
                enchantEntry == EnchantEntry.PROJECTILE_PROTECTION) {
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (armor == null || armor.getType().isAir()) continue;
                ItemMeta meta = armor.getItemMeta();
                if (meta == null) continue;
                meta.removeEnchant(enchantEntry.getEnchantment());
                armor.setItemMeta(meta);
            }
            return;
        }

        // Normal item removal
        if (target == null) return;
        ItemMeta meta = target.getItemMeta();
        if (meta == null) return;
        meta.removeEnchant(enchantEntry.getEnchantment());
        target.setItemMeta(meta);
    }

    public static void lockRuneShift(Player player) {
        UUID id = player.getUniqueId();

        runeShiftLock.add(id);

        SchedulerUtils.runTaskLater(
                () -> runeShiftLock.remove(id),
                10L
        );
    }

    public static boolean isRuneShiftLocked(Player player) {
        return runeShiftLock.contains(player.getUniqueId());
    }

    public static int getActiveRuneCount(Player player) {
        if (player == null) return 0;

        int count = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;

            if (isRune(item) && isRuneActive(item)) {
                count++;
            }
        }

        return count;
    }

    public static void clearRuneLink(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return;

        ItemMeta meta = rune.getItemMeta();

        meta.getPersistentDataContainer().remove(Keys.RUNE_LINK);

        rune.setItemMeta(meta);
    }

    public static void updateArmorRunes(Player player) {

        // Remove rune enchants from every armor piece in inventory
        for (ItemStack item : player.getInventory().getContents()) {

            if (!isArmor(item)) continue;

            removeArmorRuneEnchants(item);
        }

        // Remove rune enchants from equipped armor
        for (ItemStack armor : player.getInventory().getArmorContents()) {

            if (armor == null || armor.getType().isAir()) continue;

            removeArmorRuneEnchants(armor);
        }


        // Now reapply active armor runes to equipped armor only
        for (ItemStack armor : player.getInventory().getArmorContents()) {

            if (armor == null || armor.getType().isAir()) continue;

            ItemMeta meta = armor.getItemMeta();
            if (meta == null) continue;


            for (ItemStack runeItem : player.getInventory().getContents()) {

                if (!isRune(runeItem)) continue;
                if (!isRuneActive(runeItem)) continue;

                EnchantEntry rune = PDCDetection.getRune(runeItem);

                if (rune != EnchantEntry.PROTECTION &&
                        rune != EnchantEntry.PROJECTILE_PROTECTION) {
                    continue;
                }

                Integer level = runeItem.getItemMeta()
                        .getPersistentDataContainer()
                        .get(Keys.RUNE_LEVEL, PersistentDataType.INTEGER);

                if (level == null) continue;


                meta.addEnchant(
                        rune.getEnchantment(),
                        level,
                        true
                );
            }

            armor.setItemMeta(meta);
        }
    }

    private static void removeArmorRuneEnchants(ItemStack item) {

        ItemMeta meta = item.getItemMeta();

        if (meta == null) return;

        meta.removeEnchant(
                EnchantEntry.PROTECTION.getEnchantment()
        );

        meta.removeEnchant(
                EnchantEntry.PROJECTILE_PROTECTION.getEnchantment()
        );

        item.setItemMeta(meta);
    }

    private static boolean isArmor(ItemStack item) {

        if (item == null) return false;

        String name = item.getType().name();

        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    // ================= RUNE DURABILITY =====================

    public static double getRuneDurability(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return 0;

        Double durability = rune.getItemMeta()
                .getPersistentDataContainer()
                .get(Keys.RUNE_DURABILITY, PersistentDataType.DOUBLE);

        return durability != null ? durability : 0;
    }


    public static void setRuneDurability(ItemStack rune, double amount) {
        if (rune == null || !rune.hasItemMeta()) return;

        ItemMeta meta = rune.getItemMeta();

        meta.getPersistentDataContainer().set(
                Keys.RUNE_DURABILITY,
                PersistentDataType.DOUBLE,
                amount
        );

        rune.setItemMeta(meta);
    }

    public static void updateRuneDurabilityBar(ItemStack rune) {

        if (rune == null || !rune.hasItemMeta()) return;

        EnchantEntry enchant = PDCDetection.getRune(rune);
        if (enchant == null) return;

        ItemMeta meta = rune.getItemMeta();

        if (!(meta instanceof Damageable damageable)) return;


        double current = getRuneDurability(rune);
        double max = getMaxRuneDurability(enchant);


        int maxDamage = rune.getType().getMaxDurability();

        int damage = (int) Math.round(
                maxDamage - ((current / max) * maxDamage)
        );


        damageable.setDamage(damage);

        rune.setItemMeta(meta);
    }

    public static void initializeRuneDurability(ItemStack rune, EnchantEntry enchant) {
        if (rune == null) return;

        if (rune.hasItemMeta() &&
                rune.getItemMeta()
                        .getPersistentDataContainer()
                        .has(Keys.RUNE_DURABILITY, PersistentDataType.DOUBLE)) {
            return;
        }

        setRuneDurability(rune, getMaxRuneDurability(enchant));
        updateRuneDurabilityBar(rune);
    }

    public static boolean consumeRuneDurability(Player player, ItemStack rune) {

        if (rune == null || !isRune(rune)) return false;

        EnchantEntry enchant = PDCDetection.getRune(rune);
        if (enchant == null) return false;

        double current = getRuneDurability(rune);

        Integer level = rune.getItemMeta()
                .getPersistentDataContainer()
                .get(Keys.RUNE_LEVEL, PersistentDataType.INTEGER);

        if (level == null) return false;

        double newAmount = current - level;

        if (newAmount <= 0) {

            setRuneDurability(rune, 0);
            updateRuneDurabilityBar(rune);

            setRuneActive(rune, false);

            ItemStack linkedItem = getLinkedItem(player, rune);

            if (linkedItem != null) {
                removeRune(player, linkedItem, rune);

                if (enchant == EnchantEntry.PROTECTION ||
                        enchant == EnchantEntry.PROJECTILE_PROTECTION) {

                    updateArmorRunes(player);
                }
            }

            setRuneBroken(rune);
            setRuneOffTime(rune);

            Messages.send(player, "rune.broken",
                    "rune", formatRuneName(enchant));

            SoundUtils.play(
                    player,
                    Sound.ENTITY_ITEM_BREAK,
                    1.0f,
                    1.0f
            );

            return true;
        }


        setRuneDurability(rune, newAmount);
        updateRuneDurabilityBar(rune);

        return false;
    }

    public static double getMaxRuneDurability(EnchantEntry enchant) {
        return switch (enchant) {
            case SHARPNESS, PROTECTION, QUICK_CHARGE -> 30;
            case FIRE_ASPECT, KNOCKBACK, FLAME -> 15;
            case POWER, PROJECTILE_PROTECTION -> 18;
            case PUNCH -> 10;
            case PIERCING -> 24;
        };
    }

    public static void setRuneBroken(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return;

        ItemMeta meta = rune.getItemMeta();

        meta.getPersistentDataContainer().set(
                Keys.RUNE_BROKEN_TIME,
                PersistentDataType.LONG,
                System.currentTimeMillis()
        );

        rune.setItemMeta(meta);
    }


    public static boolean isRuneBroken(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return false;

        Long brokenTime = rune.getItemMeta()
                .getPersistentDataContainer()
                .get(Keys.RUNE_BROKEN_TIME, PersistentDataType.LONG);

        if (brokenTime == null) return false;
        if (System.currentTimeMillis() - brokenTime >= 10000) {
            clearRuneBroken(rune);
            return false;
        }
        return true;
    }


    public static void clearRuneBroken(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return;

        ItemMeta meta = rune.getItemMeta();

        meta.getPersistentDataContainer()
                .remove(Keys.RUNE_BROKEN_TIME);

        rune.setItemMeta(meta);
    }

    public static void setRuneOffTime(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return;

        ItemMeta meta = rune.getItemMeta();

        meta.getPersistentDataContainer().set(
                Keys.RUNE_OFF_TIME,
                PersistentDataType.LONG,
                System.currentTimeMillis()
        );

        rune.setItemMeta(meta);
    }

    public static boolean canRuneRecharge(ItemStack rune) {
        if (rune == null || !rune.hasItemMeta()) return false;

        if (isRuneBroken(rune)) {
            return false;
        }

        Long offTime = rune.getItemMeta()
                .getPersistentDataContainer()
                .get(Keys.RUNE_OFF_TIME, PersistentDataType.LONG);

        if (offTime == null) return false;

        return System.currentTimeMillis() - offTime >= 3000;
    }

    public static void startRuneRechargeTask() {

        SchedulerUtils.runTaskTimer(
                () -> {

                    for (Player player : Bukkit.getOnlinePlayers()) {

                        for (ItemStack item : player.getInventory().getContents()) {

                            if (!isRune(item)) continue;
                            if (isRuneActive(item)) continue;
                            if (!canRuneRecharge(item)) continue;

                            double current = getRuneDurability(item);
                            double max = getMaxRuneDurability(
                                    PDCDetection.getRune(item)
                            );

                            boolean wasFull = current >= max;

                            if (current >= max) {
                                continue;
                            }
                            else {
                                if (hasFullChargeWarning(item)) {
                                    setFullChargeWarning(item, false);
                                }
                            }

                            double newAmount = Math.min(
                                    current + 1.5,
                                    max
                            );

                            setRuneDurability(item, newAmount);
                            updateRuneDurabilityBar(item);

                            if (newAmount >= max) {

                                if (!hasFullChargeWarning(item) && !wasFull) {

                                    Messages.send(player, "rune.recharged",
                                            "rune", formatRuneName(PDCDetection.getRune(item)));

                                    SoundUtils.play(
                                            player,
                                            Sound.BLOCK_NOTE_BLOCK_CHIME,
                                            1.0f,
                                            1.5f
                                    );

                                    setFullChargeWarning(item, true);
                                }
                            }
                        }
                    }

                },
                20L,
                20L
        );
    }

    public static ItemStack getActiveRune(Player player, EnchantEntry enchant) {

        if (player == null || enchant == null) return null;

        for (ItemStack item : player.getInventory().getContents()) {

            if (!isRune(item)) continue;
            if (!isRuneActive(item)) continue;

            EnchantEntry runeEnchant = PDCDetection.getRune(item);

            if (runeEnchant == enchant) {
                return item;
            }
        }

        return null;
    }

    public static boolean hasFullChargeWarning(ItemStack rune) {

        if (rune == null || !rune.hasItemMeta()) return false;

        Byte warning = rune.getItemMeta()
                .getPersistentDataContainer()
                .get(Keys.RUNE_FULL_CHARGE_WARNING, PersistentDataType.BYTE);

        return warning != null && warning == 1;
    }


    public static void setFullChargeWarning(ItemStack rune, boolean warned) {

        if (rune == null || !rune.hasItemMeta()) return;

        ItemMeta meta = rune.getItemMeta();

        meta.getPersistentDataContainer().set(
                Keys.RUNE_FULL_CHARGE_WARNING,
                PersistentDataType.BYTE,
                (byte) (warned ? 1 : 0)
        );

        rune.setItemMeta(meta);
    }

    public static String formatRuneName(EnchantEntry enchant) {

        if (enchant == null) return "Unknown";

        String name = enchant.name().toLowerCase();

        String[] words = name.split("_");

        StringBuilder formatted = new StringBuilder();

        for (String word : words) {

            if (formatted.length() > 0) {
                formatted.append(" ");
            }

            formatted.append(
                    word.substring(0, 1).toUpperCase()
            ).append(
                    word.substring(1)
            );
        }

        return formatted.toString();
    }

    // ================= RUNE VISUALS =====================

    public static void playRuneActivation(Player player, EnchantEntry enchant) {

        Location ground = player.getLocation().clone();

        while (ground.getBlock().isPassable()) {
            ground.subtract(0, 1, 0);
        }

        double animationY = ground.getY() + 1;
        spawnRuneParticles(player, enchant);
        ItemDisplay book = spawnRuneBook(player, animationY);

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
                double height = 1 + (eased * 1.7);
                double angle = tick * 0.25;

                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;

                Location newLoc = playerLoc.clone()
                        .add(x, height, z);

                book.teleport(newLoc);

                double spinSpeed = 15;

                if (tick >= 50 && tick <= 53) {

                    double slowdown = (53 - tick) / 3.0;
                    spinSpeed *= slowdown;

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

    private static ItemDisplay spawnRuneBook(Player player, double animationY) {

        ItemDisplay display = player.getWorld().spawn(
                getRuneStartLocation(player, animationY),
                ItemDisplay.class
        );

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);

        display.setItemStack(book);

        display.setBillboard(Display.Billboard.FIXED);

        display.setItemDisplayTransform(
                ItemDisplay.ItemDisplayTransform.GROUND
        );

        display.setGravity(false);

        display.setInvulnerable(true);

        return display;
    }

    private static Location getRuneStartLocation(Player player, double animationY) {

        Location location = player.getLocation().clone();
        location.setY(animationY);

        Vector forward = location.getDirection()
                .setY(0)
                .normalize();

        Vector right = new Vector(
                -forward.getZ(),
                0,
                forward.getX()
        );

        return location
                .add(forward.multiply(-2))
                .add(right.multiply(2))
                .add(0, 1, 0);
    }

    private static void spinBook(ItemDisplay book, float speed) {

        Transformation transformation = book.getTransformation();

        transformation.getLeftRotation().rotateY(
                (float) Math.toRadians(speed)
        );

        book.setTransformation(transformation);
    }

    private static void spawnRuneParticles(Player player, EnchantEntry enchant) {

        Location center = player.getLocation().clone()
                .add(0, 1.2, 0);

        Color runeColor = getRuneColor(enchant);

        player.getWorld().spawnParticle(
                Particle.ENCHANT,
                center,
                40,
                0.6,
                0.6,
                0.6,
                0.15
        );

        player.getWorld().spawnParticle(
                Particle.DUST,
                center,
                25,
                0.6,
                0.6,
                0.6,
                new Particle.DustOptions(
                        runeColor,
                        1.2f
                )
        );
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

    public static void playRuneDeactivation(Player player, EnchantEntry enchant) {

        Location ground = player.getLocation().clone();

        while (ground.getBlock().isPassable()) {
            ground.subtract(0, 1, 0);
        }

        double animationY = ground.getY() + 1;

        ItemDisplay book = spawnRuneBook(player, animationY);

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

                double height = 1 + (eased * 1.7);

                Location newLoc = playerLoc.clone()
                        .add(0, height, 0);

                book.teleport(newLoc);

                // Slow spin at first
                double spinSpeed = 18;

                // Begin slowing down earlier
                if (tick >= 20 && tick <= 26) {

                    double slowdown = (26 - tick) / 6.0;
                    spinSpeed *= slowdown;

                } else if (tick > 20) {

                    spinSpeed = 0;
                }

                spinBook(book, (float) spinSpeed);

                if (tick >= duration) {

                    book.remove();

                    // Small, condensed burst
                    Location center = player.getLocation().clone()
                            .add(0, 2.7, 0);

                    Color runeColor = getRuneColor(enchant);

                    player.getWorld().spawnParticle(
                            Particle.DUST,
                            center,
                            12,
                            0.25,
                            0.25,
                            0.25,
                            new Particle.DustOptions(
                                    runeColor,
                                    1.1f
                            )
                    );

                    cancel();
                }
            }
        }, 0L, 1L);
    }
}
