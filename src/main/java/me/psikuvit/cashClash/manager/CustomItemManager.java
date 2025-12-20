package me.psikuvit.cashClash.manager;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.shop.items.CustomItemType;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.ShopItems;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages custom item state, cooldowns, and core functionality.
 */
public class CustomItemManager {

    private static CustomItemManager instance;

    // Cooldown tracking
    private final Map<UUID, Long> medicPouchCooldown = new HashMap<>();
    private final Map<UUID, Long> invisCloakCooldown = new HashMap<>();
    private final Map<UUID, Integer> invisCloakUsesRemaining = new HashMap<>();
    private final Set<UUID> invisCloakActive = new HashSet<>();
    private final Map<UUID, BukkitTask> invisCloakTasks = new HashMap<>();

    // Grenade tracking
    private final Set<Item> activeGrenades = new HashSet<>();

    // Bounce pad tracking - stores location -> owner team
    private final Map<Location, Integer> bouncePadTeams = new HashMap<>();

    // Boombox tracking
    private final Set<Location> activeBoomboxes = new HashSet<>();

    private CustomItemManager() {}

    public static CustomItemManager getInstance() {
        if (instance == null) {
            instance = new CustomItemManager();
        }
        return instance;
    }

    // ==================== ITEM TYPE DETECTION ====================

    public CustomItemType getCustomItemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String typeStr = pdc.get(Keys.CUSTOM_ITEM_KEY, PersistentDataType.STRING);

        if (typeStr == null) return null;

        return ShopItems.getCustomItem(typeStr);
    }

    // ==================== GRENADE IMPLEMENTATION ====================

    public void throwGrenade(Player player, ItemStack item, boolean isSmoke) {
        consumeItem(player, item);

        Item thrownItem = player.getWorld().dropItem(
                player.getEyeLocation(),
                new ItemStack(isSmoke ? Material.GRAY_DYE : Material.FIRE_CHARGE)
        );
        thrownItem.setVelocity(player.getLocation().getDirection().multiply(1.2));
        thrownItem.setPickupDelay(Integer.MAX_VALUE);
        activeGrenades.add(thrownItem);

        SoundUtils.play(player, Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.8f);

        SchedulerUtils.runTaskLater(() -> {
            if (!thrownItem.isValid()) return;
            activeGrenades.remove(thrownItem);

            Location loc = thrownItem.getLocation();
            thrownItem.remove();

            if (isSmoke) {
                explodeSmokeGrenade(loc);
            } else {
                explodeGrenade(loc);
            }
        }, 5 * 20L);
    }

    private void explodeGrenade(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.EXPLOSION, loc, 1);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        for (Entity entity : world.getNearbyEntities(loc, 6, 6, 6)) {
            if (!(entity instanceof Player target)) continue;

            double distance = target.getLocation().distance(loc);
            double damage;

            if (distance <= 4) {
                damage = 8.0; // 4 hearts
            } else if (distance <= 6) {
                damage = 2.0; // 1 heart
            } else {
                continue;
            }

            target.damage(damage);
            SoundUtils.play(target, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
        }
    }

    private void explodeSmokeGrenade(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        world.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.5f);

        BukkitTask cloudTask = SchedulerUtils.runTaskTimer(() -> {
            world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, loc, 20, 2.5, 1, 2.5, 0.01);

            for (Entity entity : world.getNearbyEntities(loc, 5, 5, 5)) {
                if (!(entity instanceof Player target)) continue;

                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true));
            }
        }, 0L, 20L);

        SchedulerUtils.runTaskLater(() -> {
            if (cloudTask != null) {
                cloudTask.cancel();
            }
        }, 8 * 20L);
    }

    // ==================== MEDIC POUCH IMPLEMENTATION ====================

    public boolean useMedicPouchSelf(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long cd = medicPouchCooldown.get(uuid);
        if (cd != null && now < cd) {
            long remaining = (cd - now) / 1000;
            Messages.send(player, "<red>Medic Pouch on cooldown! (" + remaining + "s)</red>");
            return false;
        }

        double currentHealth = player.getHealth();
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = attr != null ? attr.getValue() : 20.0;
        double healAmount = 6.0; // 3 hearts

        if (currentHealth >= maxHealth) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 30, 0, false, true));
            Messages.send(player, "<green>Healing converted to absorption!</green>");
        } else {
            double newHealth = Math.min(maxHealth, currentHealth + healAmount);
            double excess = (currentHealth + healAmount) - maxHealth;

            player.setHealth(newHealth);

            if (excess > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 30, 0, false, true));
                Messages.send(player, "<green>Healed to full! Excess converted to absorption.</green>");
            } else {
                Messages.send(player, "<green>Healed 3 hearts!</green>");
            }
        }

        consumeItem(player, item);
        medicPouchCooldown.put(uuid, now + 10000L);
        SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        return true;
    }

    public boolean useMedicPouchAlly(Player player, Player target, ItemStack item) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long cd = medicPouchCooldown.get(uuid);
        if (cd != null && now < cd) {
            long remaining = (cd - now) / 1000;
            Messages.send(player, "<red>Medic Pouch on cooldown! (" + remaining + "s)</red>");
            return false;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;

        Team playerTeam = session.getPlayerTeam(player);
        Team targetTeam = session.getPlayerTeam(target);

        if (playerTeam == null || targetTeam == null || playerTeam.getTeamNumber() != targetTeam.getTeamNumber()) {
            Messages.send(player, "<red>You can only heal teammates!</red>");
            return false;
        }

        double currentHealth = target.getHealth();
        var attr = target.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = attr != null ? attr.getValue() : 20.0;
        double healAmount = 10.0; // 5 hearts

        if (currentHealth >= maxHealth) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 30, 1, false, true));
            Messages.send(target, "<green>" + player.getName() + " gave you absorption!</green>");
        } else {
            double newHealth = Math.min(maxHealth, currentHealth + healAmount);
            double excess = (currentHealth + healAmount) - maxHealth;

            target.setHealth(newHealth);

            if (excess > 0) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 30, 1, false, true));
            }
            Messages.send(target, "<green>" + player.getName() + " healed you!</green>");
        }

        consumeItem(player, item);
        medicPouchCooldown.put(uuid, now + 10000L);
        Messages.send(player, "<green>Healed " + target.getName() + "!</green>");
        SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        SoundUtils.play(target, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        return true;
    }

    // ==================== TABLET OF HACKING IMPLEMENTATION ====================

    public void useTabletOfHacking(Player player, ItemStack item) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(player, "<red>You must be in a game!</red>");
            return;
        }

        Team playerTeam = session.getPlayerTeam(player);
        if (playerTeam == null) return;

        Team enemyTeam = playerTeam.getTeamNumber() == 1 ? session.getTeam2() : session.getTeam1();

        Messages.send(player, "<gold><bold>===== ENEMY TEAM COINS =====</bold></gold>");

        for (UUID enemyUuid : enemyTeam.getPlayers()) {
            CashClashPlayer enemyCcp = session.getCashClashPlayer(enemyUuid);
            Player enemyPlayer = Bukkit.getPlayer(enemyUuid);

            if (enemyCcp != null && enemyPlayer != null) {
                Messages.send(player, "<yellow>" + enemyPlayer.getName() + ": <gold>$" +
                        String.format("%,d", enemyCcp.getCoins()) + "</gold></yellow>");
            }
        }

        Messages.send(player, "<gold><bold>============================</bold></gold>");

        consumeItem(player, item);
        SoundUtils.play(player, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
    }

    // ==================== INVIS CLOAK IMPLEMENTATION ====================

    public void toggleInvisCloak(Player player, boolean turnOn) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (turnOn && !invisCloakActive.contains(uuid)) {
            Long cd = invisCloakCooldown.get(uuid);
            if (cd != null && now < cd) {
                long remaining = (cd - now) / 1000;
                Messages.send(player, "<red>Invisibility Cloak on cooldown! (" + remaining + "s)</red>");
                return;
            }

            int uses = invisCloakUsesRemaining.getOrDefault(uuid, 5);
            if (uses <= 0) {
                Messages.send(player, "<red>No uses remaining this round!</red>");
                return;
            }

            invisCloakActive.add(uuid);
            invisCloakUsesRemaining.put(uuid, uses - 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));

            Messages.send(player, "<green>Invisibility activated! Left-click to deactivate.</green>");
            Messages.send(player, "<yellow>Losing 100 coins per second...</yellow>");
            SoundUtils.play(player, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.0f);

            GameSession session = GameManager.getInstance().getPlayerSession(player);
            CashClashPlayer ccp = session != null ? session.getCashClashPlayer(uuid) : null;

            BukkitTask drainTask = SchedulerUtils.runTaskTimer(() -> {
                if (!invisCloakActive.contains(uuid)) return;

                if (ccp != null && ccp.getCoins() >= 100) {
                    ccp.deductCoins(100);
                } else {
                    toggleInvisCloak(player, false);
                    Messages.send(player, "<red>Out of coins! Invisibility ended.</red>");
                }
            }, 20L, 20L);

            invisCloakTasks.put(uuid, drainTask);

        } else if (!turnOn && invisCloakActive.contains(uuid)) {
            invisCloakActive.remove(uuid);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);

            BukkitTask task = invisCloakTasks.remove(uuid);
            if (task != null) task.cancel();

            invisCloakCooldown.put(uuid, now + 15000L);

            Messages.send(player, "<yellow>Invisibility deactivated.</yellow>");
            SoundUtils.play(player, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.8f);
        }
    }

    public boolean isInvisActive(UUID uuid) {
        return invisCloakActive.contains(uuid);
    }

    // ==================== BAG OF POTATOES IMPLEMENTATION ====================

    public void handleBagOfPotatoesHit(Player attacker, ItemStack item) {
        double currentHealth = attacker.getHealth();
        var attr = attacker.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = attr != null ? attr.getValue() : 20.0;

        attacker.setHealth(Math.min(maxHealth, currentHealth + 2.0));

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int damage = damageable.getDamage();
            int maxDur = item.getType().getMaxDurability();

            if (damage + 1 >= maxDur || damage >= 2) {
                attacker.getInventory().setItemInMainHand(null);
                SoundUtils.play(attacker, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            } else {
                damageable.setDamage(damage + 1);
                item.setItemMeta(meta);
            }
        }

        attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 2, 0), 3);
    }

    // ==================== CASH BLASTER IMPLEMENTATION ====================

    public void handleCashBlasterHit(Player attacker) {
        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
        if (session == null) return;

        CashClashPlayer ccp = session.getCashClashPlayer(attacker.getUniqueId());
        if (ccp != null) {
            ccp.addCoins(500);
            Messages.send(attacker, "<green>+$500 from Cash Blaster hit!</green>");
            SoundUtils.play(attacker, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        }
    }

    // ==================== BOUNCE PAD IMPLEMENTATION ====================

    public boolean placeBouncePad(Player player, ItemStack item, Block clickedBlock) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;

        Team team = session.getPlayerTeam(player);
        if (team == null) return false;

        Location placeLoc = clickedBlock.getRelative(BlockFace.UP).getLocation();
        Block placeBlock = placeLoc.getBlock();

        if (placeBlock.getType() != Material.AIR) {
            Messages.send(player, "<red>Cannot place bounce pad here!</red>");
            return false;
        }

        placeBlock.setType(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
        bouncePadTeams.put(placeBlock.getLocation(), team.getTeamNumber());

        consumeItem(player, item);
        Messages.send(player, "<green>Bounce pad placed!</green>");
        SoundUtils.play(player, Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);
        return true;
    }

    public boolean handleBouncePad(Player player, Location padLoc) {
        Integer padTeam = bouncePadTeams.get(padLoc);
        if (padTeam == null) return false;

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;

        Team playerTeam = session.getPlayerTeam(player);
        if (playerTeam == null) return false;

        if (playerTeam.getTeamNumber() != padTeam) {
            return false;
        }

        Vector direction = player.getLocation().getDirection();
        direction.setY(0).normalize();

        Vector velocity = direction.multiply(1.4).setY(0.8);
        player.setVelocity(velocity);

        SoundUtils.play(player, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);
        return true;
    }

    public boolean isBouncePad(Location loc) {
        return bouncePadTeams.containsKey(loc);
    }

    // ==================== BOOMBOX IMPLEMENTATION ====================

    public void placeBoombox(Player player, ItemStack item, Block clickedBlock) {
        Location placeLoc = clickedBlock.getRelative(BlockFace.UP).getLocation();
        Block placeBlock = placeLoc.getBlock();

        if (placeBlock.getType() != Material.AIR) {
            Messages.send(player, "<red>Cannot place boombox here!</red>");
            return;
        }

        placeBlock.setType(Material.JUKEBOX);
        activeBoomboxes.add(placeBlock.getLocation());

        consumeItem(player, item);
        Messages.send(player, "<green>Boombox placed! Pulsing knockback for 12 seconds.</green>");
        SoundUtils.play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

        final Location boomLoc = placeBlock.getLocation().clone().add(0.5, 0.5, 0.5);

        for (int i = 0; i < 4; i++) {
            int delay = i * 60;
            SchedulerUtils.runTaskLater(() -> {
                if (!activeBoomboxes.contains(placeBlock.getLocation())) return;

                World world = boomLoc.getWorld();
                if (world == null) return;

                world.playSound(boomLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 2.0f, 0.5f);
                world.spawnParticle(Particle.SONIC_BOOM, boomLoc, 1);

                for (Entity entity : world.getNearbyEntities(boomLoc, 5, 5, 5)) {
                    if (!(entity instanceof Player)) continue;
                    if (entity.equals(player)) continue;

                    Vector knockback = entity.getLocation().toVector()
                            .subtract(boomLoc.toVector())
                            .normalize()
                            .multiply(1.5)
                            .setY(0.5);
                    entity.setVelocity(knockback);
                }
            }, delay);
        }

        SchedulerUtils.runTaskLater(() -> {
            activeBoomboxes.remove(placeBlock.getLocation());
            if (placeBlock.getType() == Material.JUKEBOX) {
                placeBlock.setType(Material.AIR);
            }
        }, 12 * 20L);

    }

    // ==================== UTILITY METHODS ====================

    public void consumeItem(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    // ==================== CLEANUP ====================

    public void resetForRound(UUID playerId) {
        medicPouchCooldown.remove(playerId);
        invisCloakCooldown.remove(playerId);
        invisCloakUsesRemaining.remove(playerId);
        invisCloakActive.remove(playerId);

        BukkitTask task = invisCloakTasks.remove(playerId);
        if (task != null) task.cancel();
    }

    public void cleanup() {
        activeGrenades.forEach(Item::remove);
        activeGrenades.clear();

        bouncePadTeams.keySet().forEach(loc -> {
            Block block = loc.getBlock();
            if (block.getType() == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
                block.setType(Material.AIR);
            }
        });
        bouncePadTeams.clear();

        activeBoomboxes.forEach(loc -> {
            Block block = loc.getBlock();
            if (block.getType() == Material.JUKEBOX) {
                block.setType(Material.AIR);
            }
        });
        activeBoomboxes.clear();

        invisCloakTasks.values().forEach(BukkitTask::cancel);
        invisCloakTasks.clear();
        invisCloakActive.clear();
    }
}

