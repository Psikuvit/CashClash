package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gui.PlayerSelectorGUI;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Manages custom item state, cooldowns, and core functionality.
 */
public class CustomItemManager {

    private static CustomItemManager instance;

    private final CooldownManager cooldownManager;

    // Invis Cloak state tracking
    private final Map<UUID, Integer> invisCloakUsesRemaining;
    private final Set<UUID> invisCloakActive;
    private final Map<UUID, BukkitTask> invisCloakTasks;
    private final Map<UUID, List<ItemStack>> invisCloakStoredArmor;

    // Grenade tracking
    private final Set<Item> activeGrenades;

    // Bounce pad tracking - stores location -> owner team
    private final Map<Location, Integer> bouncePadTeams;

    // Boombox tracking
    private final Set<Location> activeBoomboxes;

    // Respawn anchor tracking - stores reviver UUID -> target UUID and task
    private final Map<UUID, UUID> respawnAnchorTargets;
    private final Map<UUID, BukkitTask> respawnAnchorTasks;
    private final Map<UUID, Integer> respawnAnchorsUsedThisRound;
    private final Set<UUID> playersRevivedThisRound;

    private CustomItemManager() {
        this.cooldownManager = CooldownManager.getInstance();
        this.invisCloakUsesRemaining = new HashMap<>();
        this.invisCloakActive = new HashSet<>();
        this.invisCloakTasks = new HashMap<>();
        this.invisCloakStoredArmor = new HashMap<>();
        this.activeGrenades = new HashSet<>();
        this.bouncePadTeams = new HashMap<>();
        this.activeBoomboxes = new HashSet<>();
        this.respawnAnchorTargets = new HashMap<>();
        this.respawnAnchorTasks = new HashMap<>();
        this.respawnAnchorsUsedThisRound = new HashMap<>();
        this.playersRevivedThisRound = new HashSet<>();
    }

    public static CustomItemManager getInstance() {
        if (instance == null) {
            instance = new CustomItemManager();
        }
        return instance;
    }

    // ==================== GRENADE IMPLEMENTATION ====================

    public void throwGrenade(Player player, ItemStack item, boolean isSmoke) {
        consumeItem(player, item);
        ItemsConfig cfg = ItemsConfig.getInstance();

        Item thrownItem = player.getWorld().dropItem(
                player.getEyeLocation(),
                new ItemStack(Material.FIRE_CHARGE) // Both grenades use FIRE_CHARGE for resource pack compatibility
        );
        thrownItem.setVelocity(player.getLocation().getDirection().multiply(1.2));
        thrownItem.setPickupDelay(Integer.MAX_VALUE);
        activeGrenades.add(thrownItem);

        SoundUtils.play(player, Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.8f);

        int fuseSeconds = cfg.getGrenadeFuseSeconds();
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
        }, fuseSeconds * 20L);
    }

    private void explodeGrenade(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        ParticleUtils.explosion(loc);
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
            ParticleUtils.campfireSmoke(loc, 20, 2.5, 1, 2.5);

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

    public void useMedicPouchSelf(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.MEDIC_POUCH)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.MEDIC_POUCH);
            Messages.send(player, "<red>Medic Pouch on cooldown! (" + remaining + "s)</red>");
            return;
        }

        double currentHealth = player.getHealth();
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = attr != null ? attr.getValue() : 20.0;
        double healAmount = cfg.getMedicPouchSelfHeal();

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
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.MEDIC_POUCH, cfg.getMedicPouchCooldown());
        SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    public void useMedicPouchAlly(Player player, Player target, ItemStack item) {
        UUID uuid = player.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.MEDIC_POUCH)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.MEDIC_POUCH);
            Messages.send(player, "<red>Medic Pouch on cooldown! (" + remaining + "s)</red>");
            return;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        Team playerTeam = session.getPlayerTeam(player);
        Team targetTeam = session.getPlayerTeam(target);

        if (playerTeam == null || targetTeam == null || playerTeam.getTeamNumber() != targetTeam.getTeamNumber()) {
            Messages.send(player, "<red>You can only heal teammates!</red>");
            return;
        }

        double currentHealth = target.getHealth();
        var attr = target.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = attr != null ? attr.getValue() : 20.0;
        double healAmount = cfg.getMedicPouchAllyHeal();

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
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.MEDIC_POUCH, cfg.getMedicPouchCooldown());
        Messages.send(player, "<green>Healed " + target.getName() + "!</green>");
        SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        SoundUtils.play(target, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    // ==================== TABLET OF HACKING IMPLEMENTATION ====================

    public void useTabletOfHacking(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.debug("TABLET", "Player " + player.getName() + " tried to use Tablet of Hacking outside of a game.");
            return;
        }
        Team playerTeam = session.getPlayerTeam(player);
        if (playerTeam == null) return;

        Team enemyTeam = session.getOpposingTeam(playerTeam);
        if (enemyTeam == null) return;

        long cost = 2000L;
        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null || ccp.getCoins() < cost) {
            Messages.send(player, "<red>Not enough coins to use Tablet of Hacking! Cost: $2,000");
            return;
        }

        PlayerSelectorGUI.openTabletOfHacking(player, enemyTeam.getPlayers());
    }

    // Called when a player selects an enemy in the PlayerSelector for Tablet of Hacking
    public void handleTabletOfHackingSelection(Player viewer, Player target) {
        GameSession session = GameManager.getInstance().getPlayerSession(viewer);
        if (session == null) {
            Messages.debug("TABLET", "Player " + viewer.getName() + " tried to use Tablet of Hacking outside of a game.");
            return;
        }
        long cost = 2000L;
        CashClashPlayer ccp = session.getCashClashPlayer(viewer.getUniqueId());
        if (ccp == null || ccp.getCoins() < cost) {
            Messages.send(viewer, "<red>Not enough coins to use Tablet of Hacking! Cost: $2,000");
            return;
        }
        ccp.deductCoins(cost);
        viewer.openInventory(target.getInventory());
        Messages.send(viewer, "<gray>Viewing " + target.getName() + "'s inventory.</gray>");
        SoundUtils.play(viewer, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
    }

    // ==================== INVIS CLOAK IMPLEMENTATION ====================

    public void toggleInvisCloak(Player player, boolean turnOn) {
        UUID uuid = player.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (turnOn && !invisCloakActive.contains(uuid)) {
            if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.INVIS_CLOAK)) {
                long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.INVIS_CLOAK);
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

            // Store and hide armor
            ItemStack[] currentArmor = player.getInventory().getArmorContents();
            List<ItemStack> armorCopy = new ArrayList<>();
            for (ItemStack stack : currentArmor) {
                armorCopy.add(stack != null ? stack.clone() : null);
            }
            if (player.getInventory().getItemInOffHand().getType() == Material.SHIELD) {
                armorCopy.add(player.getInventory().getItemInOffHand().clone());
            }
            invisCloakStoredArmor.put(uuid, armorCopy);
            player.getInventory().setArmorContents(new ItemStack[4]); // Clear visible armor
            player.getInventory().setItemInOffHand(null); // Clear shield if any

            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));

            Messages.send(player, "<green>Invisibility activated! Right-click to deactivate.</green>");
            int costPerSecond = cfg.getInvisCloakCostPerSecond();
            Messages.send(player, "<yellow>Losing " + costPerSecond + " coins per second...</yellow>");
            SoundUtils.play(player, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.0f);

            GameSession session = GameManager.getInstance().getPlayerSession(player);
            CashClashPlayer ccp = session != null ? session.getCashClashPlayer(uuid) : null;

            BukkitTask drainTask = SchedulerUtils.runTaskTimer(() -> {
                if (!invisCloakActive.contains(uuid)) return;

                if (ccp != null && ccp.getCoins() >= costPerSecond) {
                    ccp.deductCoins(costPerSecond);
                } else {
                    toggleInvisCloak(player, false);
                    Messages.send(player, "<red>Out of coins! Invisibility ended.</red>");
                }
            }, 20L, 20L);

            invisCloakTasks.put(uuid, drainTask);

        } else if (!turnOn && invisCloakActive.contains(uuid)) {
            invisCloakActive.remove(uuid);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);

            // Restore armor
            List<ItemStack> storedArmor = invisCloakStoredArmor.remove(uuid);
            if (storedArmor != null) {
                storedArmor.forEach(item -> {
                    if (item != null && item.getType() == Material.SHIELD) {
                        player.getInventory().setItemInOffHand(item);
                    } else {
                        ItemStack[] armorContents = player.getInventory().getArmorContents();
                        for (int i = 0; i < 4; i++) {
                            if (armorContents[i] == null) {
                                armorContents[i] = item;
                                break;
                            }
                        }
                        player.getInventory().setArmorContents(armorContents);
                    }
                });

            }

            BukkitTask task = invisCloakTasks.remove(uuid);
            if (task != null) task.cancel();

            cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.INVIS_CLOAK, cfg.getInvisCloakCooldown());

            Messages.send(player, "<yellow>Invisibility deactivated.</yellow>");
            SoundUtils.play(player, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.8f);
        }
    }

    /**
     * Handles right-click with invis cloak - toggles invisibility.
     */
    public void handleInvisCloakRightClick(Player player) {
        UUID uuid = player.getUniqueId();

        // If already active, turn off
        if (invisCloakActive.contains(uuid)) {
            toggleInvisCloak(player, false);
            return;
        }

        // Otherwise, turn on
        toggleInvisCloak(player, true);
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

        ParticleUtils.hearts(attacker, 3);
    }

    // ==================== CASH BLASTER IMPLEMENTATION ====================

    public void handleCashBlasterHit(Player attacker) {
        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
        if (session == null) return;
        ItemsConfig cfg = ItemsConfig.getInstance();

        CashClashPlayer ccp = session.getCashClashPlayer(attacker.getUniqueId());
        if (ccp != null) {
            int coinsPerHit = cfg.getCashBlasterCoinsPerHit();
            ccp.addCoins(coinsPerHit);
            Messages.send(attacker, "<green>+$" + coinsPerHit + " from Cash Blaster hit!</green>");
            SoundUtils.play(attacker, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        }
    }

    // ==================== BOUNCE PAD IMPLEMENTATION ====================

    public void placeBouncePad(Player player, ItemStack item, Block clickedBlock) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        Team team = session.getPlayerTeam(player);
        if (team == null) return;

        if (session.getState().isShopping()) {
            Messages.send(player, "<red>You can't place this item during shopping!</red>");
            return;
        }

        Block placeBlock = clickedBlock.getRelative(BlockFace.UP);

        if (!placeBlock.getType().isAir()) {
            Messages.send(player, "<red>Cannot place bounce pad here!</red>");
            return;
        }

        // Use a full block so players can reliably trigger it
        placeBlock.setType(Material.SLIME_BLOCK);
        Location blockLoc = placeBlock.getLocation();
        bouncePadTeams.put(blockLoc, team.getTeamNumber());

        consumeItem(player, item);
        Messages.send(player, "<green>Bounce pad placed!</green>");
        SoundUtils.play(player, Sound.BLOCK_SLIME_BLOCK_PLACE, 1.0f, 1.0f);

        SchedulerUtils.runTaskLater(() -> {
            bouncePadTeams.remove(blockLoc);
            if (placeBlock.getType() == Material.SLIME_BLOCK) {
                placeBlock.setType(Material.AIR);
            }
        }, 5 * 20L);
    }

    public void handleBouncePad(Player player, Block block) {
        Location blockLoc = block.getLocation();
        Integer padTeam = bouncePadTeams.get(blockLoc);
        if (padTeam == null) return;

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        Team playerTeam = session.getPlayerTeam(player);
        if (playerTeam == null) return;

        if (playerTeam.getTeamNumber() != padTeam) {
            Messages.send(player, "<red>This bounce pad belongs to the enemy team!</red>");
            return;
        }

        ItemsConfig cfg = ItemsConfig.getInstance();
        Vector direction = player.getLocation().getDirection();
        direction.setY(0).normalize();
        Vector velocity = direction.multiply(cfg.getBouncePadForwardVelocity()).setY(cfg.getBouncePadUpwardVelocity());
        player.setVelocity(velocity);

        SoundUtils.play(player, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);
    }

    public boolean isBouncePad(Block block) {
        if (block.getType() != Material.SLIME_BLOCK) return false;
        return bouncePadTeams.containsKey(block.getLocation());
    }

    // ==================== BOOMBOX IMPLEMENTATION ====================

    public void placeBoombox(Player player, ItemStack item, Block clickedBlock) {
        Location placeLoc = clickedBlock.getRelative(BlockFace.UP).getLocation();
        Block placeBlock = placeLoc.getBlock();

        if (GameManager.getInstance().getPlayerSession(player).getState().isShopping()) {
            Messages.send(player, "<red>You can't place this item during shopping!</red>");
            return;
        }

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

        // Pulse every 3 seconds (0, 3, 6, 9 seconds = 4 pulses total)
        for (int i = 0; i < 4; i++) {
            int delay = i * 3 * 20; // 3 seconds between pulses
            SchedulerUtils.runTaskLater(() -> {
                if (!activeBoomboxes.contains(placeBlock.getLocation())) return;

                World world = boomLoc.getWorld();
                if (world == null) return;

                world.playSound(boomLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 2.0f, 0.5f);
                ParticleUtils.sonicBoom(boomLoc);

                for (Entity entity : world.getNearbyEntities(boomLoc, 5, 5, 5)) {
                    if (!(entity instanceof Player target)) continue;
                    if (target.equals(player)) continue;

                    Vector knockback = target.getLocation().toVector()
                            .subtract(boomLoc.toVector())
                            .normalize()
                            .multiply(1.5)
                            .setY(0.5);
                    target.setVelocity(knockback);
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

    public boolean isBoombox(Block block) {
        if (block.getType() != Material.JUKEBOX) return false;
        return activeBoomboxes.contains(block.getLocation());
    }

    // ==================== RESPAWN ANCHOR IMPLEMENTATION ====================

    /**
     * Start reviving a dead teammate with respawn anchor.
     */
    public void useRespawnAnchor(Player reviver, Player target, ItemStack item) {
        UUID reviverUuid = reviver.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        GameSession session = GameManager.getInstance().getPlayerSession(reviver);
        if (session == null) {
            Messages.send(reviver, "<red>You must be in a game!</red>");
            return;
        }

        // Check if same team
        Team reviverTeam = session.getPlayerTeam(reviver);
        Team targetTeam = session.getPlayerTeam(target);
        if (reviverTeam == null || targetTeam == null || reviverTeam.getTeamNumber() != targetTeam.getTeamNumber()) {
            Messages.send(reviver, "<red>You can only revive teammates!</red>");
            return;
        }

        // Check if target actually needs reviving (has 0 lives)
        CashClashPlayer targetCcp = session.getCashClashPlayer(targetUuid);
        if (targetCcp == null || targetCcp.getLives() > 0) {
            Messages.send(reviver, "<red>" + target.getName() + " still has lives remaining!</red>");
            return;
        }

        // Check max 2 uses per round
        int usesThisRound = respawnAnchorsUsedThisRound.getOrDefault(reviverUuid, 0);
        if (usesThisRound >= 2) {
            Messages.send(reviver, "<red>You've used the maximum respawn anchors this round!</red>");
            return;
        }

        // Check if target was already revived this round
        if (playersRevivedThisRound.contains(targetUuid)) {
            Messages.send(reviver, "<red>" + target.getName() + " was already revived this round!</red>");
            return;
        }

        // Check if already reviving someone
        if (respawnAnchorTargets.containsKey(reviverUuid)) {
            Messages.send(reviver, "<red>You're already reviving someone!</red>");
            return;
        }

        // Start the revive process
        respawnAnchorTargets.put(reviverUuid, targetUuid);
        consumeItem(reviver, item);
        respawnAnchorsUsedThisRound.merge(reviverUuid, 1, Integer::sum);

        Messages.send(reviver, "<yellow>Reviving " + target.getName() + "... Stay close for 10 seconds!</yellow>");
        Messages.send(target, "<yellow>" + reviver.getName() + " is reviving you! Stay close for 10 seconds!</yellow>");
        SoundUtils.play(reviver, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.0f);

        final Location startLoc = reviver.getLocation();

        // Progress task - check distance every second
        BukkitTask progressTask = SchedulerUtils.runTaskTimer(() -> {
            // Check if reviver moved too far
            if (reviver.getLocation().distance(startLoc) > 3) {
                cancelRevive(reviverUuid, "Revive cancelled - you moved too far!");
                return;
            }
            // Check if target moved too far from reviver
            if (reviver.getLocation().distance(target.getLocation()) > 5) {
                cancelRevive(reviverUuid, "Revive cancelled - target moved too far!");
                return;
            }
            // Particle effect
            ParticleUtils.portal(target.getLocation().add(0, 1, 0), 10, 0.5);
        }, 20L, 20L);

        respawnAnchorTasks.put(reviverUuid, progressTask);

        // Complete revive after 10 seconds
        SchedulerUtils.runTaskLater(() -> {
            if (!respawnAnchorTargets.containsKey(reviverUuid)) return; // Was cancelled

            BukkitTask task = respawnAnchorTasks.remove(reviverUuid);
            if (task != null) task.cancel();
            respawnAnchorTargets.remove(reviverUuid);

            // Final distance check
            if (reviver.getLocation().distance(target.getLocation()) > 5) {
                Messages.send(reviver, "<red>Revive failed - target too far!</red>");
                return;
            }

            // Complete the revive
            completeRevive(session, reviver, target);
        }, 10 * 20L);

    }

    private void cancelRevive(UUID reviverUuid, String message) {
        respawnAnchorTargets.remove(reviverUuid);
        BukkitTask task = respawnAnchorTasks.remove(reviverUuid);
        if (task != null) task.cancel();

        Player reviver = Bukkit.getPlayer(reviverUuid);
        if (reviver != null) {
            Messages.send(reviver, "<red>" + message + "</red>");
            SoundUtils.play(reviver, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.5f);
        }
    }

    private void completeRevive(GameSession session, Player reviver, Player target) {
        UUID targetUuid = target.getUniqueId();
        CashClashPlayer targetCcp = session.getCashClashPlayer(targetUuid);

        if (targetCcp == null) return;

        // Grant 1 life
        targetCcp.setLives(targetCcp.getLives() + 1);
        playersRevivedThisRound.add(targetUuid);

        // Grant +2 bonus hearts (4 max health increase)
        var attr = target.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(attr.getValue() + 4.0);
        }

        // Get spawn location for the revived player
        Location spawnLocation = session.getSpawnForPlayer(targetUuid);
        if (spawnLocation == null) {
            spawnLocation = reviver.getLocation(); // Fallback to reviver's location
        }

        // Teleport and change game mode to SURVIVAL
        target.teleport(spawnLocation);
        target.setGameMode(GameMode.SURVIVAL);

        // Set health to full after teleport
        target.setHealth(Objects.requireNonNull(target.getAttribute(Attribute.MAX_HEALTH)).getValue());
        target.setFoodLevel(20);

        // 3 seconds of invincibility
        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 3 * 20, 4, false, true)); // Resistance V = invincible
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 3 * 20, 0, false, true));

        Messages.send(reviver, "<green>Successfully revived " + target.getName() + "!</green>");
        Messages.send(target, "<green>You have been revived by " + reviver.getName() + "! +2 bonus hearts!</green>");

        SoundUtils.play(reviver, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 1.0f);
        SoundUtils.play(target, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);

        // Visual effect at spawn location
        ParticleUtils.totem(target.getLocation().add(0, 1, 0), 50, 0.5);
    }

    /**
     * Check if a player can be targeted for revive (has 0 lives, same team, not already revived)
     */
    public boolean canBeRevived(Player reviver, Player target) {
        GameSession session = GameManager.getInstance().getPlayerSession(reviver);
        if (session == null) return false;

        Team reviverTeam = session.getPlayerTeam(reviver);
        Team targetTeam = session.getPlayerTeam(target);
        if (reviverTeam == null || targetTeam == null) return false;
        if (reviverTeam.getTeamNumber() != targetTeam.getTeamNumber()) return false;

        CashClashPlayer targetCcp = session.getCashClashPlayer(target.getUniqueId());
        if (targetCcp == null || targetCcp.getLives() > 0) return false;

        return !playersRevivedThisRound.contains(target.getUniqueId());
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

    public void cleanup() {
        activeGrenades.forEach(Item::remove);
        activeGrenades.clear();

        bouncePadTeams.keySet().forEach(loc -> {
            Block block = loc.getBlock();
            if (block.getType() == Material.SLIME_BLOCK) {
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
        invisCloakStoredArmor.clear();

        respawnAnchorTasks.values().forEach(BukkitTask::cancel);
        respawnAnchorTasks.clear();
        respawnAnchorTargets.clear();
        respawnAnchorsUsedThisRound.clear();
        playersRevivedThisRound.clear();
    }
}

