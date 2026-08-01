package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
import me.psikuvit.cashClash.gui.PlayerSelectorGUI;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    // Bounce pad tracking - stores location -> owner team + the face it's attached to
    private final Map<Location, BouncePadInfo> bouncePadTeams;

    /**
     * @param attachedFace The block face the pad was placed against (UP for a floor pad,
     *                      a horizontal face for a wall-mounted pad); also the direction
     *                      players are launched when they touch it.
     */
    private record BouncePadInfo(int teamNumber, BlockFace attachedFace) {}

    // Boombox tracking
    private final Set<Location> activeBoomboxes;

    // Respawn anchor tracking - stores reviver UUID -> target UUID and task
    private final Map<UUID, UUID> respawnAnchorTargets;
    private final Map<UUID, BukkitTask> respawnAnchorTasks;
    private final Map<UUID, Integer> respawnAnchorsUsedThisRound;
    private final Set<UUID> playersRevivedThisRound;

    // Cash Blaster earnings tracking per round
    private final Map<UUID, Long> cashBlasterEarningsThisRound;

    // Totem of Haunting - active death-save invincibility window
    private final Set<UUID> totemInvincible;

    // Shared: healing-reduction hook (e.g. Soul Katana's debuff), consumed by any item's heals
    private final Map<UUID, Long> healingReducedUntil;
    private final Map<UUID, Double> healingReductionMultiplier;

    // Radiating Lotus - charge-hold state
    private final Map<UUID, Integer> lotusChargeTicks;
    private final Map<UUID, BukkitTask> lotusChargeTasks;
    private static final NamespacedKey LOTUS_SLOW_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "radiating_lotus_slow");

    // Ice Fan - consecutive gust-hit streak per target (for the freeze-after-3s rule) and a
    // transient flag suppressing DamageListener's vanilla-melee cancellation for its own hits
    private final Map<UUID, Integer> iceFanGustStreak;
    private final Map<UUID, BukkitTask> iceFanGustResetTasks;
    private final Set<UUID> iceFanAbilityDamageActive;

    // Overdrive Potion - invincibility window + pulsing aura task (speed modifier has its own
    // duration; cancelling early only drops the invincibility, not the speed boost)
    private final Set<UUID> overdriveInvincible;
    private final Map<UUID, BukkitTask> overdrivePulseTasks;
    private static final NamespacedKey OVERDRIVE_SPEED_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "overdrive_speed");

    // Hunter's Mark - hold-to-charge state (per attacker) and active marks (per target). The
    // target's damage-in multiplier is derived live from their missing hearts.
    private final Map<UUID, Integer> hunterMarkChargeTicks;
    private final Map<UUID, BukkitTask> hunterMarkChargeTasks;
    private final Map<UUID, HunterMarkInfo> hunterMarks;
    private final Map<UUID, Long> markedUntil;

    // Blooming Rose - placed sakura zones keyed by trunk location
    private final Map<Location, BloomingRoseZone> bloomingRoseZones;
    private boolean bloomingRoseHpLoopStarted;

    /**
     * @param session    the game session the rose was placed in (used for team lookups on expiry)
     * @param teamNumber the team the placer belongs to - only same-team players get protection/regen
     * @param center     the trunk location (zone centre)
     * @param expiresAt  epoch millis the zone naturally expires
     * @param blocks     every block the structure occupies (log + leaves), tracked for counterplay
     * @param task       the zone upkeep task (drift particles + floor heal + expiry)
     */
    private record BloomingRoseZone(GameSession session, int teamNumber, Location center, long expiresAt,
                                    Set<Block> blocks, BukkitTask task) {}

    // Orb of Gravitation - live orb tracking (Snowball entity UUID -> hits remaining, owner UUID,
    // and the orb's dust-trail task, cancelled when the orb resolves)
    private final Map<UUID, Integer> orbHitsRemaining;
    private final Map<UUID, UUID> orbOwners;
    private final Map<UUID, BukkitTask> orbTrailTasks;

    // Soul Katana - Phantom Slice: attackers whose damage call is the flat ability strike (set
    // only around the direct damage call so DamageListener zeroes armor/effect modifiers there)
    private final Set<UUID> phantomSliceDamageActive;

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
        this.cashBlasterEarningsThisRound = new HashMap<>();
        this.totemInvincible = new HashSet<>();
        this.healingReducedUntil = new HashMap<>();
        this.healingReductionMultiplier = new HashMap<>();
        this.lotusChargeTicks = new HashMap<>();
        this.lotusChargeTasks = new HashMap<>();
        this.iceFanGustStreak = new HashMap<>();
        this.iceFanGustResetTasks = new HashMap<>();
        this.iceFanAbilityDamageActive = new HashSet<>();
        this.overdriveInvincible = new HashSet<>();
        this.overdrivePulseTasks = new HashMap<>();
        this.hunterMarkChargeTicks = new HashMap<>();
        this.hunterMarkChargeTasks = new HashMap<>();
        this.hunterMarks = new HashMap<>();
        this.markedUntil = new HashMap<>();
        this.bloomingRoseZones = new HashMap<>();
        this.orbHitsRemaining = new HashMap<>();
        this.orbOwners = new HashMap<>();
        this.orbTrailTasks = new HashMap<>();
        this.phantomSliceDamageActive = new HashSet<>();
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
            Messages.send(player, "customitem.medic-pouch-cooldown", "remaining", String.valueOf(remaining));
            return;
        }

        double currentHealth = player.getHealth();
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        CashClashPlayer ccp = session != null ? session.getCashClashPlayer(player.getUniqueId()) : null;
        double maxHealth = ccp != null ? ccp.getMaxHealth() : 20.0;
        double healAmount = cfg.getMedicPouchSelfHeal();

        if (currentHealth >= maxHealth) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 30, 0, false, true));
            Messages.send(player, "customitem.healing-converted");
        } else {
            double newHealth = Math.min(maxHealth, currentHealth + healAmount);
            double excess = (currentHealth + healAmount) - maxHealth;

            player.setHealth(newHealth);

            if (excess > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 30, 0, false, true));
                Messages.send(player, "customitem.healed-full");
            } else {
                Messages.send(player, "customitem.healed-three-hearts");
            }
        }

        consumeItem(player, item);
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.MEDIC_POUCH, cfg.getMedicPouchCooldown());
        SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    public void useMedicPouchAlly(Player player, Player target, ItemStack item, GameSession session) {
        UUID uuid = player.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.MEDIC_POUCH)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.MEDIC_POUCH);
            Messages.send(player, "customitem.medic-pouch-cooldown", "remaining", String.valueOf(remaining));
            return;
        }

        if (session == null) return;

        Team playerTeam = session.getPlayerTeam(player);
        Team targetTeam = session.getPlayerTeam(target);

        if (playerTeam == null || targetTeam == null || playerTeam.getTeamNumber() != targetTeam.getTeamNumber()) {
            Messages.send(player, "customitem.heal-teammates-only");
            return;
        }

        double currentHealth = target.getHealth();
        var targetCCP = session.getCashClashPlayer(target.getUniqueId());
        double maxHealth = targetCCP != null ? targetCCP.getMaxHealth() : 20.0;
        double healAmount = cfg.getMedicPouchAllyHeal();

        if (currentHealth >= maxHealth) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 30, 1, false, true));
            Messages.send(target, "customitem.ally-gave-absorption", "player_name", player.getName());
        } else {
            double newHealth = Math.min(maxHealth, currentHealth + healAmount);
            double excess = (currentHealth + healAmount) - maxHealth;

            target.setHealth(newHealth);

            if (excess > 0) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 30, 1, false, true));
            }
            Messages.send(target, "customitem.ally-healed-you", "player_name", player.getName());
        }

        consumeItem(player, item);
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.MEDIC_POUCH, cfg.getMedicPouchCooldown());
        Messages.send(player, "customitem.healed-ally", "player_name", target.getName());
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
            Messages.send(player, "customitem.tablet-insufficient-coins");
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
            Messages.send(viewer, "customitem.tablet-insufficient-coins");
            return;
        }
        ccp.deductCoins(cost);
        viewer.openInventory(target.getInventory());
        Messages.send(viewer, "customitem.tablet-viewing-inventory", "player_name", target.getName());
        SoundUtils.play(viewer, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
    }

    // ==================== INVIS CLOAK IMPLEMENTATION ====================

    public void toggleInvisCloak(Player player, boolean turnOn) {
        UUID uuid = player.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (turnOn && !invisCloakActive.contains(uuid)) {
            if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.INVIS_CLOAK)) {
                long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.INVIS_CLOAK);
                Messages.send(player, "customitem.invis-cooldown", "remaining", String.valueOf(remaining));
                return;
            }

            int uses = invisCloakUsesRemaining.getOrDefault(uuid, 5);
            if (uses <= 0) {
                Messages.send(player, "customitem.no-uses-remaining");
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

            // Remove all arrows from the player (arrows stuck in them)
            removeArrowsFromPlayer(player);

            Messages.send(player, "customitem.invis-activated");
            int costPerSecond = cfg.getInvisCloakCostPerSecond();
            Messages.send(player, "customitem.invis-cost-per-second", "cost", String.valueOf(costPerSecond));
            SoundUtils.play(player, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.0f);

            GameSession session = GameManager.getInstance().getPlayerSession(player);
            CashClashPlayer ccp = session != null ? session.getCashClashPlayer(uuid) : null;

            BukkitTask drainTask = SchedulerUtils.runTaskTimer(() -> {
                if (!invisCloakActive.contains(uuid)) return;

                if (ccp != null && ccp.getCoins() >= costPerSecond) {
                    ccp.deductCoins(costPerSecond);
                } else {
                    toggleInvisCloak(player, false);
                    Messages.send(player, "customitem.invis-out-of-coins");
                }
            }, 20L, 20L);

            invisCloakTasks.put(uuid, drainTask);

        } else if (!turnOn && invisCloakActive.contains(uuid)) {
            invisCloakActive.remove(uuid);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);

            // Restore armor
            List<ItemStack> storedArmor = invisCloakStoredArmor.remove(uuid);
            if (storedArmor != null && storedArmor.size() >= 4) {
                // Restore armor contents (first 4 items are helmet, chestplate, leggings, boots)
                ItemStack[] armorContents = new ItemStack[4];
                for (int i = 0; i < 4; i++) {
                    armorContents[i] = storedArmor.get(i);
                }
                player.getInventory().setArmorContents(armorContents);

                // Check if there's a 5th item (shield in offhand)
                if (storedArmor.size() > 4 && storedArmor.get(4) != null) {
                    player.getInventory().setItemInOffHand(storedArmor.get(4));
                }
            }

            BukkitTask task = invisCloakTasks.remove(uuid);
            if (task != null) task.cancel();

            cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.INVIS_CLOAK, cfg.getInvisCloakCooldown());

            Messages.send(player, "customitem.invis-deactivated");
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

    /**
     * Clears invisibility cloak state on death and restores armor.
     * The armor was hidden when invis was activated, so we need to restore it.
     */
    public void clearInvisCloakOnDeath(Player player) {
        UUID uuid = player.getUniqueId();

        if (!invisCloakActive.contains(uuid)) return;

        invisCloakActive.remove(uuid);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        // Restore armor that was hidden during invisibility
        List<ItemStack> storedArmor = invisCloakStoredArmor.remove(uuid);
        if (storedArmor != null && storedArmor.size() >= 4) {
            // Restore armor contents (first 4 items are helmet, chestplate, leggings, boots)
            ItemStack[] armorContents = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                armorContents[i] = storedArmor.get(i);
            }
            player.getInventory().setArmorContents(armorContents);

            // Check if there's a 5th item (shield in offhand)
            if (storedArmor.size() > 4 && storedArmor.get(4) != null) {
                player.getInventory().setItemInOffHand(storedArmor.get(4));
            }
        }

        // Cancel the drain task
        BukkitTask task = invisCloakTasks.remove(uuid);
        if (task != null) task.cancel();

        // Reset cooldown
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.INVIS_CLOAK, ItemsConfig.getInstance().getInvisCloakCooldown());
    }

    /**
     * Remove all arrows from a player's body
     */
    private void removeArrowsFromPlayer(Player player) {
        player.setArrowsInBody(0);
    }

    // ==================== BAG OF POTATOES IMPLEMENTATION ====================

    public void handleBagOfPotatoesHit(Player attacker, ItemStack item, GameSession session) {
        double currentHealth = attacker.getHealth();
        var attackerCCP = session != null ? session.getCashClashPlayer(attacker.getUniqueId()) : null;
        double maxHealth = attackerCCP != null ? attackerCCP.getMaxHealth() : 20.0;

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
            UUID attackerId = attacker.getUniqueId();
            long currentEarnings = cashBlasterEarningsThisRound.getOrDefault(attackerId, 0L);
            long MAX_EARNINGS_PER_ROUND = 10000;

            int coinsPerHit = cfg.getCashBlasterCoinsPerHit();

            // Check if adding this hit would exceed the limit
            if (currentEarnings + coinsPerHit > MAX_EARNINGS_PER_ROUND) {
                long remaining = MAX_EARNINGS_PER_ROUND - currentEarnings;
                if (remaining > 0) {
                    ccp.addCoins(remaining);
                    cashBlasterEarningsThisRound.put(attackerId, MAX_EARNINGS_PER_ROUND);
                    Messages.send(attacker, "customitem.cash-blaster-hit-capped", "amount", String.valueOf(remaining), "max", String.valueOf(MAX_EARNINGS_PER_ROUND));
                } else {
                    Messages.send(attacker, "customitem.cash-blaster-limit", "max", String.valueOf(MAX_EARNINGS_PER_ROUND));
                }
                SoundUtils.play(attacker, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                return;
            }

            ccp.addCoins(coinsPerHit);
            cashBlasterEarningsThisRound.put(attackerId, currentEarnings + coinsPerHit);
            Messages.send(attacker, "customitem.cash-blaster-hit", "amount", String.valueOf(coinsPerHit));
            SoundUtils.play(attacker, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        }
    }

    // ==================== BOUNCE PAD IMPLEMENTATION ====================

    public void placeBouncePad(Player player, ItemStack item, Block clickedBlock, BlockFace face) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        Team team = session.getPlayerTeam(player);
        if (team == null) return;

        if (session.getState() == GameState.SHOPPING || session.isActionsRestricted()) {
            Messages.send(player, "customitem.cannot-place-during-shopping");
            return;
        }

        Block placeBlock = clickedBlock.getRelative(face);

        if (!placeBlock.getType().isAir()) {
            Messages.send(player, "customitem.cannot-place-bounce-pad");
            return;
        }

        // Use a full block so players can reliably trigger it
        placeBlock.setType(Material.SLIME_BLOCK);
        Location blockLoc = placeBlock.getLocation();
        bouncePadTeams.put(blockLoc, new BouncePadInfo(team.getTeamNumber(), face));

        consumeItem(player, item);
        Messages.send(player, "customitem.bounce-pad-placed");
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
        BouncePadInfo pad = bouncePadTeams.get(blockLoc);
        if (pad == null) return;

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        Team playerTeam = session.getPlayerTeam(player);
        if (playerTeam == null) return;

        if (playerTeam.getTeamNumber() != pad.teamNumber()) {
            Messages.send(player, "customitem.bounce-pad-enemy-team");
            return;
        }

        ItemsConfig cfg = ItemsConfig.getInstance();
        Vector direction;
        if (pad.attachedFace() != BlockFace.UP && pad.attachedFace() != BlockFace.DOWN) {
            // Wall-mounted pad: launch outward along the wall's face instead of the player's look direction
            direction = pad.attachedFace().getDirection();
        } else {
            direction = player.getLocation().getDirection();
        }
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

        GameSession boomboxSession = GameManager.getInstance().getPlayerSession(player);
        if (boomboxSession.getState() == GameState.SHOPPING || boomboxSession.isActionsRestricted()) {
            Messages.send(player, "customitem.cannot-place-during-shopping");
            return;
        }

        if (placeBlock.getType() != Material.AIR) {
            Messages.send(player, "customitem.cannot-place-boombox");
            return;
        }

        placeBlock.setType(Material.JUKEBOX);
        activeBoomboxes.add(placeBlock.getLocation());

        consumeItem(player, item);
        Messages.send(player, "mythic.boombox-placed");
        SoundUtils.play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

        final Location boomLoc = placeBlock.getLocation().clone().add(0.5, 0.5, 0.5);
        double radius = ItemsConfig.getInstance().getBoomboxRadius();

        // Pulse every 3 seconds (0, 3, 6, 9 seconds = 4 pulses total)
        for (int i = 0; i < 4; i++) {
            int delay = i * 3 * 20; // 3 seconds between pulses
            SchedulerUtils.runTaskLater(() -> {
                if (!activeBoomboxes.contains(placeBlock.getLocation())) return;

                World world = boomLoc.getWorld();
                if (world == null) return;

                world.playSound(boomLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 2.0f, 0.5f);
                spawnBoomboxRing(boomLoc, radius);
                applyBoomboxSpeedBoost(player, boomLoc, world, radius);
            }, delay);
        }

        SchedulerUtils.runTaskLater(() -> {
            activeBoomboxes.remove(placeBlock.getLocation());
            if (placeBlock.getType() == Material.JUKEBOX) {
                placeBlock.setType(Material.AIR);
            }
        }, 12 * 20L);

    }

    /**
     * Draws the orange speed-radius ring, forming point-by-point rather than appearing all at
     * once (see ParticleUtils.formingRing).
     */
    private void spawnBoomboxRing(Location center, double radius) {
        int ringPoints = 24;
        int formTicks = 20; // ~1s to fully form within each 3s pulse window
        for (int i = 0; i < ringPoints; i++) {
            int formed = i + 1;
            int delay = i * Math.max(1, formTicks / ringPoints);
            SchedulerUtils.runTaskLater(() ->
                    ParticleUtils.formingRing(center, radius, ringPoints, formed, Color.fromRGB(255, 140, 0), 1.4f), delay);
        }
    }

    /**
     * Grants allies within radius (including the placer, excluding the flag carrier) a
     * temporary speed boost.
     */
    private void applyBoomboxSpeedBoost(Player placer, Location center, World world, double radius) {
        GameSession session = GameManager.getInstance().getPlayerSession(placer);
        Team placerTeam = session != null ? session.getPlayerTeam(placer) : null;
        if (placerTeam == null) return;

        ItemsConfig cfg = ItemsConfig.getInstance();
        int durationTicks = cfg.getBoomboxSpeedBoostDuration() * 20;
        int amplifier = speedPercentToAmplifier(cfg.getBoomboxSpeedBoostPercent());

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam == null || targetTeam.getTeamNumber() != placerTeam.getTeamNumber()) continue;
            if (session.getGamemode() instanceof CaptureTheFlagGamemode ctf && ctf.isSilenced(target.getUniqueId())) continue;

            target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, amplifier, false, true));
        }
    }

    /**
     * Vanilla Speed levels are +20% per amplifier level (Speed I = amplifier 0 = +20%), so a
     * configured percentage is rounded to the nearest whole level.
     */
    private int speedPercentToAmplifier(int percent) {
        return Math.max(0, Math.round(percent / 20.0f) - 1);
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
            Messages.send(reviver, "gamestate.must-be-in-game");
            return;
        }

        // Check if same team
        Team reviverTeam = session.getPlayerTeam(reviver);
        Team targetTeam = session.getPlayerTeam(target);
        if (reviverTeam == null || targetTeam == null || reviverTeam.getTeamNumber() != targetTeam.getTeamNumber()) {
            Messages.send(reviver, "customitem.revive-teammates-only");
            return;
        }

        // Check if target actually needs reviving (has 0 lives)
        CashClashPlayer targetCcp = session.getCashClashPlayer(targetUuid);
        if (targetCcp == null || targetCcp.getLives() > 0) {
            Messages.send(reviver, "customitem.revive-target-has-lives", "player_name", target.getName());
            return;
        }

        // Check max 2 uses per round
        int usesThisRound = respawnAnchorsUsedThisRound.getOrDefault(reviverUuid, 0);
        if (usesThisRound >= 2) {
            Messages.send(reviver, "customitem.revive-max-anchors");
            return;
        }

        // Check if target was already revived this round
        if (playersRevivedThisRound.contains(targetUuid)) {
            Messages.send(reviver, "customitem.revive-already-revived", "player_name", target.getName());
            return;
        }

        // Check if already reviving someone
        if (respawnAnchorTargets.containsKey(reviverUuid)) {
            Messages.send(reviver, "customitem.revive-already-reviving");
            return;
        }

        // Start the revive process
        respawnAnchorTargets.put(reviverUuid, targetUuid);
        consumeItem(reviver, item);
        respawnAnchorsUsedThisRound.merge(reviverUuid, 1, Integer::sum);

        Messages.send(reviver, "customitem.revive-start-reviver", "player_name", target.getName());
        Messages.send(target, "customitem.revive-start-target", "player_name", reviver.getName());
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
                Messages.send(reviver, "customitem.revive-failed-distance");
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
            Messages.send(reviver, "customitem.revive-failed-generic", "reason", message);
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

        // Grant +2 bonus hearts (4 max health increase) via the centralized health system
        targetCcp.addHealthModifier(4.0);

        // Get spawn location for the revived player
        Location spawnLocation = session.getSpawnForPlayer(targetUuid);
        if (spawnLocation == null) {
            spawnLocation = reviver.getLocation(); // Fallback to reviver's location
        }

        // Teleport and change game mode to SURVIVAL
        target.teleport(spawnLocation);
        target.setGameMode(GameMode.SURVIVAL);

        // Set health to full after teleport
        target.setHealth(targetCcp.getMaxHealth());
        target.setFoodLevel(20);

        // 3 seconds of invincibility
        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 3 * 20, 4, false, true)); // Resistance V = invincible
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 3 * 20, 0, false, true));

        Messages.send(reviver, "customitem.revive-success-reviver", "player_name", target.getName());
        Messages.send(target, "customitem.revive-success-target", "player_name", reviver.getName());

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

    // ==================== TOTEM OF HAUNTING IMPLEMENTATION ====================

    public boolean isTotemInvincible(UUID uuid) {
        return totemInvincible.contains(uuid);
    }

    /**
     * Called from DamageListener when a would-be-lethal hit from another player is detected
     * on a player holding the Totem of Haunting (main or off hand).
     */
    public void triggerTotemOfHaunting(Player player, ItemStack totemItem) {
        UUID uuid = player.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        consumeTotem(player, totemItem);
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        CashClashPlayer ccp = session != null ? session.getCashClashPlayer(player.getUniqueId()) : null;
        double maxHealth = ccp != null ? ccp.getMaxHealth() : 20.0;
        player.setHealth(Math.min(cfg.getTotemRevivalHealth(), maxHealth));

        totemInvincible.add(uuid);
        int invincibilitySeconds = cfg.getTotemInvincibilitySeconds();
        SchedulerUtils.runTaskLater(() -> totemInvincible.remove(uuid), invincibilitySeconds * 20L);

        Messages.send(player, "customitem.totem-haunting-triggered");
        SoundUtils.play(player, Sound.ITEM_TOTEM_USE, 1.0f, 0.6f);
        SoundUtils.play(player, Sound.ENTITY_WITHER_AMBIENT, 0.4f, 0.5f);

        spawnHauntingSpiral(player);
    }

    /**
     * Removes one Totem of Haunting from whichever hand held it (main or off hand).
     */
    private void consumeTotem(Player player, ItemStack totemItem) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main.equals(totemItem)) {
            consumeItem(player, main);
            return;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off.getAmount() > 1) {
            off.setAmount(off.getAmount() - 1);
        } else {
            player.getInventory().setItemInOffHand(null);
        }
    }

    /**
     * Expanding black smoke spiral - applies Slowness I + Weakness I to enemy players the first
     * moment the expanding smoke reaches them, rather than as an instant flat-radius check.
     */
    private void spawnHauntingSpiral(Player player) {
        ItemsConfig cfg = ItemsConfig.getInstance();
        Location origin = player.getLocation();
        double maxRadius = cfg.getTotemDebuffRadius();
        int debuffDurationTicks = cfg.getTotemDebuffDurationSeconds() * 20;
        int totalTicks = 20; // ~1s spiral formation
        int arms = 3;
        Set<UUID> alreadyDebuffed = new HashSet<>();

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        Team playerTeam = session != null ? session.getPlayerTeam(player) : null;

        int[] tick = {0};
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = SchedulerUtils.runTaskTimer(() -> {
            tick[0]++;
            double currentRadius = maxRadius * tick[0] / totalTicks;
            for (int arm = 0; arm < arms; arm++) {
                ParticleUtils.smokeSpiralFrame(origin, currentRadius, arm, arms);
            }

            World world = origin.getWorld();
            if (world != null) {
                for (Entity entity : world.getNearbyEntities(origin, maxRadius, maxRadius, maxRadius)) {
                    if (!(entity instanceof Player target) || target.equals(player)) continue;
                    if (alreadyDebuffed.contains(target.getUniqueId())) continue;
                    if (playerTeam != null && session.getPlayerTeam(target) == playerTeam) continue;
                    if (target.getLocation().distance(origin) > currentRadius + 1.0) continue;

                    alreadyDebuffed.add(target.getUniqueId());
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, debuffDurationTicks, 0, false, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, debuffDurationTicks, 0, false, true));
                    Messages.send(target, "customitem.totem-haunting-witness", "player_name", player.getName());
                }
            }

            if (tick[0] >= totalTicks && taskHolder[0] != null) {
                taskHolder[0].cancel();
            }
        }, 0L, 1L);
    }

    // ==================== SHARED EFFECT HOOKS ====================

    /**
     * Applies a temporary healing-reduction debuff to a target (e.g. Soul Katana's Phantom
     * Slice). Any item's heal application should multiply its heal amount by
     * {@link #getHealingMultiplier(UUID)} before applying it.
     */
    public void applyHealingReduction(UUID target, double multiplier, long durationSeconds) {
        healingReducedUntil.put(target, System.currentTimeMillis() + durationSeconds * 1000L);
        healingReductionMultiplier.put(target, multiplier);
    }

    /**
     * @return 1.0 if the target has no active healing-reduction debuff, else the active multiplier
     */
    public double getHealingMultiplier(UUID target) {
        Long until = healingReducedUntil.get(target);
        if (until == null || System.currentTimeMillis() >= until) {
            healingReducedUntil.remove(target);
            healingReductionMultiplier.remove(target);
            return 1.0;
        }
        return healingReductionMultiplier.getOrDefault(target, 1.0);
    }

    // ==================== RADIATING LOTUS IMPLEMENTATION ====================

    /**
     * Starts the "hold right-click to charge" window. The item is food-eligible so right-click
     * raises the hand (see GameplayItemFactory), letting us poll isHandRaised() every tick to
     * detect when the player releases - there is no generic held-right-click event in Bukkit.
     */
    public void startRadiatingLotusCharge(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        if (lotusChargeTasks.containsKey(uuid)) return; // already charging

        lotusChargeTicks.put(uuid, 0);
        applyLotusSlow(player);

        ItemsConfig cfg = ItemsConfig.getInstance();
        int maxTicks = cfg.getLotusMaxChargeSeconds() * 20;
        int hardCapTicks = maxTicks + cfg.getLotusGraceSeconds() * 20;

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = SchedulerUtils.runTaskTimer(() -> {
            Integer ticks = lotusChargeTicks.get(uuid);
            boolean stillCharging = ticks != null && player.isOnline() && player.isHandRaised()
                    && PDCDetection.getCustomItem(player.getInventory().getItemInMainHand()) == CustomItem.RADIATING_LOTUS;

            if (!stillCharging) {
                finishLotusCharge(player, item, ticks == null ? 0 : Math.min(ticks, maxTicks));
                if (taskHolder[0] != null) taskHolder[0].cancel();
                return;
            }

            int next = ticks + 1;
            lotusChargeTicks.put(uuid, next);
            if (next >= hardCapTicks) {
                finishLotusCharge(player, item, maxTicks); // hard timeout at cap - auto-fires per spec's grace period
                if (taskHolder[0] != null) taskHolder[0].cancel();
            }
        }, 0L, 1L);
        lotusChargeTasks.put(uuid, taskHolder[0]);
    }

    /**
     * Detonates the lotus: knocks the player back, heals self + teammates, and plays the
     * knockback/heal visuals - only at detonation, never during the charge-up.
     */
    private void finishLotusCharge(Player player, ItemStack item, int chargeTicks) {
        UUID uuid = player.getUniqueId();
        lotusChargeTicks.remove(uuid);
        lotusChargeTasks.remove(uuid);
        removeLotusSlow(player);
        consumeItem(player, item);

        ItemsConfig cfg = ItemsConfig.getInstance();
        double chargeSeconds = chargeTicks / 20.0;

        double knockbackDistance = chargeSeconds * cfg.getLotusKnockbackPerSecond();
        Vector back = player.getLocation().getDirection().clone().setY(0).normalize().multiply(-knockbackDistance * 0.35);
        back.setY(0.4);
        player.setVelocity(back);
        cooldownManager.setCooldownSeconds(uuid, "WIND_CHARGE_PROTECTION", 2);

        Messages.send(player, "customitem.lotus-detonated");
        SoundUtils.playAt(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.4f);

        Location loc = player.getLocation();
        double healRadius = Math.max(1.0, chargeSeconds * cfg.getLotusHealRadiusPerSecond());
        double healAmount = cfg.getLotusHealAmount();

        ParticleUtils.spawnDust(loc.clone().add(0, 1, 0), Color.fromRGB(60, 200, 60), 2.0f, 40, 0.5);
        ParticleUtils.groundDiamond(loc, healRadius, Color.fromRGB(255, 105, 180));

        World world = loc.getWorld();
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        Team team = session != null ? session.getPlayerTeam(player) : null;
        if (team == null || world == null) return;

        for (Entity entity : world.getNearbyEntities(loc, healRadius, healRadius, healRadius)) {
            if (!(entity instanceof Player target)) continue;
            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam == null || targetTeam.getTeamNumber() != team.getTeamNumber()) continue;

            CashClashPlayer targetCcp = session.getCashClashPlayer(target.getUniqueId());
            double maxHealth = targetCcp != null ? targetCcp.getMaxHealth() : 20.0;
            double heal = healAmount * getHealingMultiplier(target.getUniqueId());
            target.setHealth(Math.min(maxHealth, target.getHealth() + heal));
        }
    }

    private void applyLotusSlow(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        double reduction = ItemsConfig.getInstance().getLotusSlowPercentWhileCharging() / 100.0;
        speed.addModifier(new AttributeModifier(LOTUS_SLOW_KEY, -reduction, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void removeLotusSlow(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(LOTUS_SLOW_KEY);
    }

    // ==================== ICE FAN IMPLEMENTATION ====================

    /**
     * @return true if the attacker is currently mid-swing with Ice Fan's own gust/burst
     * ability (used by DamageListener to distinguish that from a vanilla melee swing, which
     * Ice Fan should never deal - it's a pure ability-tool).
     */
    public boolean isIceFanAbilityDamage(UUID attackerUuid) {
        return iceFanAbilityDamageActive.contains(attackerUuid);
    }

    /**
     * Left-click: one discrete ~0.5s "gust tick" per swing (Bukkit cannot detect a held-down
     * left mouse button - sustained rapid clicking approximates "continuous").
     */
    public void handleIceFanLeftClick(Player player, ItemStack item) {
        ItemsConfig cfg = ItemsConfig.getInstance();
        int remaining = getIceFanDurability(item);
        if (remaining <= 0) {
            Messages.send(player, "customitem.ice-fan-broken");
            return;
        }

        int drainThisClick = Math.max(1, cfg.getIceFanGustDurabilityPerSecond() / 2);
        int newRemaining = remaining - drainThisClick;
        setIceFanDurability(item, newRemaining);

        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection();
        for (Player target : findIceFanTargets(player, origin, direction, 3)) {
            dealIceFanDamage(player, target, cfg.getIceFanGustDamagePerTick());
            registerIceFanGustHit(target.getUniqueId(), cfg);
        }

        ParticleUtils.iceFanGust(origin.clone().add(direction.clone().multiply(1.5)));
        SoundUtils.play(player, Sound.ENTITY_PHANTOM_FLAP, 0.7f, 1.6f);

        if (newRemaining <= 0) breakIceFan(player, item);
    }

    /**
     * Right-click: a single burst hit, instantly freezing targets. Requires >= 25 durability
     * remaining and costs 25 durability.
     */
    public void handleIceFanRightClick(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.ICE_FAN_BURST)) return;

        ItemsConfig cfg = ItemsConfig.getInstance();
        int remaining = getIceFanDurability(item);
        if (remaining < cfg.getIceFanBurstMinDurability()) {
            Messages.send(player, "customitem.ice-fan-not-enough-durability");
            return;
        }

        int newRemaining = remaining - cfg.getIceFanBurstDurabilityCost();
        setIceFanDurability(item, newRemaining);
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.ICE_FAN_BURST, 1);

        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection();
        for (Player target : findIceFanTargets(player, origin, direction, 4)) {
            dealIceFanDamage(player, target, cfg.getIceFanBurstDamage());
            target.setFreezeTicks(target.getMaxFreezeTicks());
        }

        ParticleUtils.iceFanBurst(origin.clone().add(direction.clone().multiply(2)));
        SoundUtils.play(player, Sound.ENTITY_GLOW_SQUID_SQUIRT, 1.0f, 0.6f);

        if (newRemaining <= 0) breakIceFan(player, item);
    }

    /**
     * Finds enemy players within range and within a narrow forward-facing cone, so the gust/burst
     * only hit what the player is actually aiming at.
     */
    private List<Player> findIceFanTargets(Player player, Location origin, Vector direction, double range) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        Team playerTeam = session != null ? session.getPlayerTeam(player) : null;

        List<Player> targets = new ArrayList<>();
        for (Entity entity : player.getWorld().getNearbyEntities(origin, range, range, range)) {
            if (!(entity instanceof Player target) || target.equals(player)) continue;
            if (playerTeam != null) {
                Team targetTeam = session.getPlayerTeam(target);
                if (targetTeam == null || targetTeam.getTeamNumber() == playerTeam.getTeamNumber()) continue;
            }
            if (!isInCone(origin, direction, target.getEyeLocation(), 30)) continue;
            targets.add(target);
        }
        return targets;
    }

    private boolean isInCone(Location origin, Vector facing, Location targetLoc, double halfAngleDegrees) {
        Vector toTarget = targetLoc.toVector().subtract(origin.toVector());
        if (toTarget.lengthSquared() < 1.0E-4) return true;
        double angle = Math.toDegrees(facing.clone().normalize().angle(toTarget.normalize()));
        return angle <= halfAngleDegrees;
    }

    /**
     * Deals Ice Fan ability damage while flagged so DamageListener.onIceFanMeleeSuppression
     * doesn't also cancel this explicit hit.
     */
    private void dealIceFanDamage(Player attacker, Player target, double damage) {
        iceFanAbilityDamageActive.add(attacker.getUniqueId());
        try {
            target.damage(damage, attacker);
        } finally {
            iceFanAbilityDamageActive.remove(attacker.getUniqueId());
        }
    }

    /**
     * Tracks consecutive gust hits on a target, freezing them once they've been hit enough
     * times to approximate 3 continuous seconds of gust (sustained ~2 clicks/sec); the streak
     * resets if a full second passes without another gust hit landing.
     */
    private void registerIceFanGustHit(UUID targetUuid, ItemsConfig cfg) {
        int hits = iceFanGustStreak.merge(targetUuid, 1, Integer::sum);

        BukkitTask existingReset = iceFanGustResetTasks.remove(targetUuid);
        if (existingReset != null) existingReset.cancel();

        int requiredHits = cfg.getIceFanGustFreezeSecondsRequired() * 2;
        if (hits >= requiredHits) {
            iceFanGustStreak.remove(targetUuid);
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null) target.setFreezeTicks(target.getMaxFreezeTicks());
            return;
        }

        BukkitTask resetTask = SchedulerUtils.runTaskLater(() -> iceFanGustStreak.remove(targetUuid), 20L);
        iceFanGustResetTasks.put(targetUuid, resetTask);
    }

    private int getIceFanDurability(ItemStack item) {
        Integer remaining = PDCDetection.getItemUses(item);
        return remaining != null ? remaining : ItemsConfig.getInstance().getIceFanMaxDurability();
    }

    /**
     * Persists remaining durability as a PDC counter and mirrors it onto the visual durability
     * bar proportionally, since Shears' vanilla max durability doesn't match the 75-point budget.
     */
    private void setIceFanDurability(ItemStack item, int remaining) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        int max = ItemsConfig.getInstance().getIceFanMaxDurability();
        int clamped = Math.max(0, Math.min(max, remaining));
        meta.getPersistentDataContainer().set(Keys.ITEM_USES, PersistentDataType.INTEGER, clamped);

        if (meta instanceof Damageable damageable) {
            int maxDurability = item.getType().getMaxDurability();
            double fractionUsed = 1.0 - ((double) clamped / max);
            damageable.setDamage((int) Math.round(fractionUsed * maxDurability));
        }
        item.setItemMeta(meta);
    }

    private void breakIceFan(Player player, ItemStack item) {
        Messages.send(player, "customitem.ice-fan-broken");
        SoundUtils.play(player, Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f);

        if (item.equals(player.getInventory().getItemInMainHand())) {
            player.getInventory().setItemInMainHand(null);
        } else if (item.equals(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    // ==================== OVERDRIVE POTION IMPLEMENTATION ====================

    public boolean isOverdriveInvincible(UUID uuid) {
        return overdriveInvincible.contains(uuid);
    }

    /**
     * Drinks the Overdrive Potion: grants total invincibility + a speed boost for the
     * configured duration. Speed uses a MOVEMENT_SPEED AttributeModifier so it keeps working
     * even though the player is immune to potion effects while invincible.
     */
    public void useOverdrivePotion(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        consumeItem(player, item);

        overdriveInvincible.add(uuid);
        applyOverdriveSpeed(player);

        int seconds = cfg.getOverdriveInvincibilitySeconds();

        Messages.send(player, "customitem.overdrive-activated");
        SoundUtils.play(player, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.2f);

        // Purple engulf on activation + pulsing aura while active
        ParticleUtils.spawnDust(player.getLocation().add(0, 1, 0), Color.fromRGB(150, 40, 220), 1.6f, 40, 0.6);

        BukkitTask pulseTask = SchedulerUtils.runTaskTimer(() -> {
            if (!player.isOnline() || !overdriveInvincible.contains(uuid)) return;
            ParticleUtils.spawnDust(player.getLocation().add(0, 1, 0), Color.fromRGB(165, 70, 230), 1.2f, 12, 0.4);
        }, 5L, 5L);
        overdrivePulseTasks.put(uuid, pulseTask);

        SchedulerUtils.runTaskLater(() -> endOverdrive(player), seconds * 20L);
    }

    /**
     * Right-clicking again while active cancels the invincibility early; the speed boost keeps
     * running until the original duration elapses (endOverdrive handles its removal).
     */
    public void cancelOverdriveEarly(Player player) {
        UUID uuid = player.getUniqueId();
        if (!overdriveInvincible.remove(uuid)) return;

        BukkitTask task = overdrivePulseTasks.remove(uuid);
        if (task != null) task.cancel();

        ParticleUtils.spawnDust(player.getLocation().add(0, 1, 0), Color.fromRGB(150, 40, 220), 1.2f, 20, 0.4);
        Messages.send(player, "customitem.overdrive-cancelled");
        SoundUtils.play(player, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.8f);
    }

    private void endOverdrive(Player player) {
        UUID uuid = player.getUniqueId();
        overdriveInvincible.remove(uuid);

        BukkitTask task = overdrivePulseTasks.remove(uuid);
        if (task != null) task.cancel();

        removeOverdriveSpeed(player);

        if (player.isOnline()) {
            Messages.send(player, "customitem.overdrive-ended");
        }
    }

    private void applyOverdriveSpeed(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        double boost = ItemsConfig.getInstance().getOverdriveSpeedPercent() / 100.0;
        speed.addModifier(new AttributeModifier(OVERDRIVE_SPEED_KEY, boost, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void removeOverdriveSpeed(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(OVERDRIVE_SPEED_KEY);
    }

    // ==================== HUNTER'S MARK IMPLEMENTATION ====================

    /**
     * Active mark on a target: the tracking/rotation task plus its two display entities
     * (a rotating coal block on the head and a floating vulnerability % above it).
     */
    private record HunterMarkInfo(BukkitTask task, UUID targetUuid, ItemDisplay coalDisplay, TextDisplay textDisplay, long expiresAt) {
    }

    /**
     * Starts the "hold right-click within range of an enemy" charge. The item is food-eligible so
     * right-click raises the hand (see GameplayItemFactory), letting us poll isHandRaised() every
     * tick like Radiating Lotus - release before the timer completes, or an out-of-range/no-target
     * tick, cancels the charge.
     */
    public void startHunterMarkCharge(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        if (hunterMarkChargeTasks.containsKey(uuid)) return; // already charging

        ItemsConfig cfg = ItemsConfig.getInstance();
        hunterMarkChargeTicks.put(uuid, 0);
        int requiredTicks = cfg.getHuntersMarkChargeSeconds() * 20;

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = SchedulerUtils.runTaskTimer(() -> {
            Integer ticks = hunterMarkChargeTicks.get(uuid);
            boolean stillCharging = ticks != null && player.isOnline() && player.isHandRaised()
                    && PDCDetection.getCustomItem(player.getInventory().getItemInMainHand()) == CustomItem.HUNTERS_MARK;

            if (!stillCharging) {
                cancelHunterMarkCharge(uuid);
                if (taskHolder[0] != null) taskHolder[0].cancel();
                return;
            }

            Player target = findNearestMarkTarget(player, cfg.getHuntersMarkRange());
            if (target == null) {
                cancelHunterMarkCharge(uuid);
                if (taskHolder[0] != null) taskHolder[0].cancel();
                Messages.send(player, "customitem.hunters-mark-no-target");
                return;
            }

            int next = ticks + 1;
            hunterMarkChargeTicks.put(uuid, next);
            ParticleUtils.spawnDust(target.getLocation().add(0, 1, 0), Color.fromRGB(230, 40, 40), 0.8f, 3, 0.2);

            if (next >= requiredTicks) {
                applyHunterMark(player, target, item);
                cancelHunterMarkCharge(uuid);
                if (taskHolder[0] != null) taskHolder[0].cancel();
            }
        }, 0L, 1L);
        hunterMarkChargeTasks.put(uuid, taskHolder[0]);
    }

    private void cancelHunterMarkCharge(UUID uuid) {
        hunterMarkChargeTicks.remove(uuid);
        hunterMarkChargeTasks.remove(uuid);
    }

    private void applyHunterMark(Player hunter, Player target, ItemStack item) {
        ItemsConfig cfg = ItemsConfig.getInstance();
        consumeItem(hunter, item);

        clearHunterMark(target.getUniqueId());

        long durationMillis = cfg.getHuntersMarkDurationSeconds() * 1000L;
        markedUntil.put(target.getUniqueId(), System.currentTimeMillis() + durationMillis);

        Messages.send(hunter, "customitem.hunters-mark-applied", "player_name", target.getName());
        Messages.send(target, "customitem.hunters-mark-target");
        SoundUtils.play(target, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.4f);

        spawnHunterMarkDisplay(target, durationMillis);
    }

    /**
     * Tears down an active mark: cancels its task and removes its display entities. Safe to call
     * even when the target has no mark.
     */
    public void clearHunterMark(UUID targetUuid) {
        HunterMarkInfo info = hunterMarks.remove(targetUuid);
        if (info != null) {
            info.task().cancel();
            if (!info.coalDisplay().isDead()) info.coalDisplay().remove();
            if (!info.textDisplay().isDead()) info.textDisplay().remove();
        }
        markedUntil.remove(targetUuid);
    }

    /**
     * @return damage-in multiplier for the target: 1.0 when not marked, otherwise 1 + the live
     * vulnerability % (base + 2% per missing heart), clamped so a dead-health target can't exceed
     * the display's cap.
     */
    public double getVulnerabilityMultiplier(UUID targetUuid) {
        Long until = markedUntil.get(targetUuid);
        if (until == null || System.currentTimeMillis() >= until) {
            markedUntil.remove(targetUuid);
            return 1.0;
        }
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) return 1.0;
        return 1.0 + hunterMarkPercent(target, ItemsConfig.getInstance()) / 100.0;
    }

    private void spawnHunterMarkDisplay(Player target, long durationMillis) {
        ItemsConfig cfg = ItemsConfig.getInstance();
        long expiresAt = System.currentTimeMillis() + durationMillis;
        World world = target.getWorld();

        ItemDisplay coalDisplay = world.spawn(target.getEyeLocation(), ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.COAL_BLOCK));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
            Transformation t = display.getTransformation();
            display.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(), new Vector3f(0.6f, 0.6f, 0.6f), t.getRightRotation()));
        });

        TextDisplay textDisplay = world.spawn(target.getEyeLocation(), TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(false);
        });

        UUID targetUuid = target.getUniqueId();
        BukkitTask task = SchedulerUtils.runTaskTimer(() -> {
            if (!target.isOnline() || target.isDead() || System.currentTimeMillis() >= expiresAt) {
                clearHunterMark(targetUuid);
                return;
            }
            Location eye = target.getEyeLocation();
            coalDisplay.teleport(eye.clone().add(0, 0.25, 0));
            coalDisplay.setRotation(coalDisplay.getYaw() + 12f, 0f);
            textDisplay.teleport(eye.clone().add(0, 0.7, 0));
            textDisplay.text(Messages.parse("<red><bold>+" + hunterMarkPercent(target, cfg) + "%</bold></red>"));
        }, 0L, 1L);

        hunterMarks.put(targetUuid, new HunterMarkInfo(task, targetUuid, coalDisplay, textDisplay, expiresAt));
    }

    private int hunterMarkPercent(Player target, ItemsConfig cfg) {
        GameSession session = GameManager.getInstance().getPlayerSession(target);
        CashClashPlayer ccp = session != null ? session.getCashClashPlayer(target.getUniqueId()) : null;
        double maxHealth = ccp != null ? ccp.getMaxHealth() : 20.0;
        double missingHearts = Math.max(0, (maxHealth - target.getHealth()) / 2.0);
        return cfg.getHuntersMarkBaseVulnerabilityPercent()
                + (int) Math.round(missingHearts * cfg.getHuntersMarkVulnerabilityPerMissingHeart());
    }

    private Player findNearestMarkTarget(Player player, double range) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        Team team = session != null ? session.getPlayerTeam(player) : null;

        Player nearest = null;
        double best = range;
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), range, range, range)) {
            if (!(entity instanceof Player target) || target.equals(player)) continue;
            if (team != null) {
                Team targetTeam = session.getPlayerTeam(target);
                if (targetTeam == null || targetTeam.getTeamNumber() == team.getTeamNumber()) continue;
            }
            double distance = target.getLocation().distance(player.getLocation());
            if (distance <= best) {
                best = distance;
                nearest = target;
            }
        }
        return nearest;
    }

    // ==================== BLOOMING ROSE IMPLEMENTATION ====================

    public void placeBloomingRose(Player player, ItemStack item, Location loc) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        Team team = session != null ? session.getPlayerTeam(player) : null;
        if (session == null || team == null) return;

        ItemsConfig cfg = ItemsConfig.getInstance();
        consumeItem(player, item);

        Block origin = loc.getBlock();
        Set<Block> blocks = new HashSet<>();
        buildRoseStructure(origin, blocks);

        Location center = origin.getLocation();
        long expiresAt = System.currentTimeMillis() + cfg.getBloomingRoseZoneDurationSeconds() * 1000L;
        BukkitTask upkeepTask = startRoseZoneTask(center, blocks, expiresAt, session, team.getTeamNumber());
        bloomingRoseZones.put(center, new BloomingRoseZone(session, team.getTeamNumber(), center, expiresAt, blocks, upkeepTask));

        Messages.send(player, "customitem.blooming-rose-placed");
        SoundUtils.playAt(center, Sound.BLOCK_CHERRY_WOOD_PLACE, 1.0f, 1.0f);

        spawnRoseFormationVisual(center, cfg);
        startBloomingRoseHpRevealLoop();
    }

    /**
     * Builds the 6-high CHERRY_LOG trunk with a small CHERRY_LEAVES canopy at the top and two
     * single-log branch offshoots, tracking every block placed so it can be torn down on expiry
     * or by manual destruction (the intended counterplay).
     */
    private void buildRoseStructure(Block origin, Set<Block> blocks) {
        World world = origin.getWorld();
        int baseX = origin.getX();
        int baseY = origin.getY();
        int baseZ = origin.getZ();

        for (int i = 0; i < 6; i++) {
            Block b = world.getBlockAt(baseX, baseY + i, baseZ);
            b.setType(Material.CHERRY_LOG, false);
            blocks.add(b);
        }

        int canopyY = baseY + 6;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue; // rounded canopy
                Block b = world.getBlockAt(baseX + dx, canopyY, baseZ + dz);
                b.setType(Material.CHERRY_LEAVES, false);
                blocks.add(b);
            }
        }
        Block crown = world.getBlockAt(baseX, canopyY + 1, baseZ);
        crown.setType(Material.CHERRY_LEAVES, false);
        blocks.add(crown);

        Block branchA = world.getBlockAt(baseX + 1, baseY + 3, baseZ);
        branchA.setType(Material.CHERRY_LOG, false);
        blocks.add(branchA);
        Block branchB = world.getBlockAt(baseX - 1, baseY + 3, baseZ);
        branchB.setType(Material.CHERRY_LOG, false);
        blocks.add(branchB);
    }

    /**
     * Zone upkeep: every second it drifts sakura dust off the leaves, heals same-team members
     * below the health floor back up to it, and tears the structure down once it expires.
     */
    private BukkitTask startRoseZoneTask(Location center, Set<Block> blocks, long expiresAt,
                                         GameSession session, int teamNumber) {
        ItemsConfig cfg = ItemsConfig.getInstance();
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = SchedulerUtils.runTaskTimer(() -> {
            if (System.currentTimeMillis() >= expiresAt) {
                destroyBloomingRose(center);
                if (holder[0] != null) holder[0].cancel();
                return;
            }
            for (Block block : blocks) {
                if (block.getType() == Material.CHERRY_LEAVES) {
                    ParticleUtils.spawnDust(block.getLocation().add(0.5, 0.5, 0.5),
                            Color.fromRGB(255, 150, 190), 0.6f, 1, 0.15);
                }
            }
            healRoseMembersToFloor(center, session, teamNumber);
        }, 20L, 20L);
        return holder[0];
    }

    /**
     * Heals any same-team player inside the zone whose health has fallen below the 2-heart floor
     * back up to it (scaled through the shared healing-reduction hook).
     */
    private void healRoseMembersToFloor(Location center, GameSession session, int teamNumber) {
        ItemsConfig cfg = ItemsConfig.getInstance();
        double floor = cfg.getBloomingRoseMinHealthFloor();
        double radius = cfg.getBloomingRoseZoneRadius();

        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (!isSameTeam(session, teamNumber, target)) continue;
            if (target.getHealth() >= floor) continue;

            double maxHealth = getMaxHealth(target, session);
            double heal = (floor - target.getHealth()) * getHealingMultiplier(target.getUniqueId());
            target.setHealth(Math.min(maxHealth, target.getHealth() + heal));
        }
    }

    /**
     * Tears down an active zone (expiry or manual destruction): removes the structure blocks and
     * grants teammates inside the radius Regen I for the configured duration.
     */
    private void destroyBloomingRose(Location center) {
        BloomingRoseZone zone = bloomingRoseZones.remove(center);
        if (zone == null) return;

        if (zone.task() != null) zone.task().cancel();
        for (Block block : zone.blocks()) {
            if (block.getType() == Material.CHERRY_LOG || block.getType() == Material.CHERRY_LEAVES) {
                block.setType(Material.AIR, false);
            }
        }
        triggerRoseRegen(zone);
    }

    /**
     * Detects manual destruction of a tracked structure block (GameListener's BlockBreakEvent) -
     * the intended counterplay - collapsing the whole zone.
     */
    public void onRoseStructureBroken(Block block) {
        for (Map.Entry<Location, BloomingRoseZone> entry : new ArrayList<>(bloomingRoseZones.entrySet())) {
            if (entry.getValue().blocks().contains(block)) {
                destroyBloomingRose(entry.getKey());
                return;
            }
        }
    }

    private void triggerRoseRegen(BloomingRoseZone zone) {
        ItemsConfig cfg = ItemsConfig.getInstance();
        double radius = cfg.getBloomingRoseZoneRadius();
        int durationTicks = cfg.getBloomingRoseRegenDurationSeconds() * 20;

        for (Entity entity : zone.center().getWorld().getNearbyEntities(zone.center(), radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (!isSameTeam(zone.session(), zone.teamNumber(), target)) continue;
            target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, 0, false, false));
            Messages.send(target, "customitem.blooming-rose-teammates-regen");
        }
    }

    /**
     * @return the active same-team zone reduction % for a player standing inside one, else 0
     */
    public double getBloomingRoseDamageReduction(Player player) {
        BloomingRoseZone zone = findRoseZone(player);
        if (zone == null) return 0.0;
        return ItemsConfig.getInstance().getBloomingRoseDamageReductionPercent();
    }

    /**
     * @return the active same-team zone health floor for a player standing inside one, else -1
     */
    public double getBloomingRoseMinHealth(Player player) {
        BloomingRoseZone zone = findRoseZone(player);
        if (zone == null) return -1.0;
        return ItemsConfig.getInstance().getBloomingRoseMinHealthFloor();
    }

    private BloomingRoseZone findRoseZone(Player player) {
        ItemsConfig cfg = ItemsConfig.getInstance();
        double radius = cfg.getBloomingRoseZoneRadius();
        for (BloomingRoseZone zone : bloomingRoseZones.values()) {
            if (zone.blocks().isEmpty()) continue;
            if (zone.center().getWorld().equals(player.getWorld())
                    && zone.center().distance(player.getLocation()) <= radius
                    && isSameTeam(zone.session(), zone.teamNumber(), player)) {
                return zone;
            }
        }
        return null;
    }

    private boolean isSameTeam(GameSession session, int teamNumber, Player target) {
        if (session == null) return false;
        Team targetTeam = session.getPlayerTeam(target);
        return targetTeam != null && targetTeam.getTeamNumber() == teamNumber;
    }

    private double getMaxHealth(Player target, GameSession session) {
        CashClashPlayer ccp = session != null ? session.getCashClashPlayer(target.getUniqueId()) : null;
        return ccp != null ? ccp.getMaxHealth() : 20.0;
    }

    /**
     * Sakura formation visual: a red formingRing (zone radius) that draws in while two figure-eight
     * cursors converge from opposite ends around the trunk.
     */
    private void spawnRoseFormationVisual(Location center, ItemsConfig cfg) {
        double radius = cfg.getBloomingRoseZoneRadius();
        Color pink = Color.fromRGB(255, 150, 190);

        BukkitTask[] ringHolder = new BukkitTask[1];
        final int[] formed = {0};
        ringHolder[0] = SchedulerUtils.runTaskTimer(() -> {
            formed[0] += 6;
            if (formed[0] >= 90) {
                ParticleUtils.formingRing(center.clone().add(0, 0.5, 0), radius, 90, 90, pink, 0.12f);
                if (ringHolder[0] != null) ringHolder[0].cancel();
                return;
            }
            ParticleUtils.formingRing(center.clone().add(0, 0.5, 0), radius, 90, formed[0], pink, 0.12f);
        }, 0L, 1L);
        BukkitTask[] figHolder = new BukkitTask[1];
        final int[] fig = {0};
        figHolder[0] = SchedulerUtils.runTaskTimer(() -> {
            fig[0] += 4;
            if (fig[0] >= 60) {
                ParticleUtils.figureEight(center.clone().add(0, 0.5, 0), radius * 0.4, pink, 60, 60, false);
                ParticleUtils.figureEight(center.clone().add(0, 0.5, 0), radius * 0.4, pink, 60, 60, true);
                if (figHolder[0] != null) figHolder[0].cancel();
                return;
            }
            ParticleUtils.figureEight(center.clone().add(0, 0.5, 0), radius * 0.4, pink, 60, fig[0], false);
            ParticleUtils.figureEight(center.clone().add(0, 0.5, 0), radius * 0.4, pink, 60, fig[0], true);
        }, 0L, 1L);
    }

    /**
     * Lazy once-per-plugin-life actionbar loop (started on first rose placement): every 10 ticks,
     * players holding a Blooming Rose see their teammates' current HP.
     */
    private void startBloomingRoseHpRevealLoop() {
        if (bloomingRoseHpLoopStarted) return;
        bloomingRoseHpLoopStarted = true;

        SchedulerUtils.runTaskTimer(() -> {
            for (Player holder : Bukkit.getOnlinePlayers()) {
                if (PDCDetection.getCustomItem(holder.getInventory().getItemInMainHand()) != CustomItem.BLOOMING_ROSE) continue;
                GameSession session = GameManager.getInstance().getPlayerSession(holder);
                if (session == null) continue;
                Team team = session.getPlayerTeam(holder);
                if (team == null) continue;

                StringBuilder sb = new StringBuilder("<white>Rose HP:</white> <aqua>You <red>❤")
                        .append(String.format("%.1f", holder.getHealth())).append("</red>");
                for (Player teammate : Bukkit.getOnlinePlayers()) {
                    if (teammate.equals(holder)) continue;
                    Team t = session.getPlayerTeam(teammate);
                    if (t == null || t.getTeamNumber() != team.getTeamNumber()) continue;
                    sb.append(" <aqua>").append(teammate.getName()).append(" <red>❤")
                            .append(String.format("%.1f", teammate.getHealth())).append("</red>");
                }
                holder.sendActionBar(Messages.parse(sb.toString()));
            }
        }, 0L, 10L);
    }

    // ==================== ORB OF GRAVITATION IMPLEMENTATION ====================

    public boolean isOrbEntity(Entity entity) {
        return entity instanceof Snowball && orbHitsRemaining.containsKey(entity.getUniqueId());
    }

    /**
     * @return true if the player has at least one live orb still in flight (right-click again
     * then detonates it manually instead of throwing another)
     */
    public boolean hasLiveOrb(Player player) {
        UUID owner = player.getUniqueId();
        for (UUID stored : orbOwners.values()) {
            if (owner.equals(stored)) return true;
        }
        return false;
    }

    /**
     * Detonates the player's live orb (manual right-click while one is in flight).
     */
    public void activateOrbByOwner(Player player) {
        UUID owner = player.getUniqueId();
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(orbOwners.entrySet())) {
            if (owner.equals(entry.getValue())) {
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity instanceof Snowball orb && !orb.isDead()) {
                    activateOrb(orb);
                }
                return;
            }
        }
    }

    /**
     * Launches the orb as a Snowball tagged with its owner. The item is NOT consumed yet - it is
     * only consumed once the orb fully resolves (destroyed, pulled-and-expired, or detonated).
     */
    public void throwOrbOfGravitation(Player player) {
        ItemsConfig cfg = ItemsConfig.getInstance();

        Snowball orb = player.launchProjectile(Snowball.class);
        orb.setVelocity(player.getLocation().getDirection().multiply(cfg.getOrbThrowSpeed()));

        PersistentDataContainer pdc = orb.getPersistentDataContainer();
        pdc.set(Keys.ITEM_ID, PersistentDataType.STRING, CustomItem.ORB_OF_GRAVITATION.name());
        pdc.set(Keys.ITEM_OWNER, PersistentDataType.STRING, player.getUniqueId().toString());

        UUID orbUuid = orb.getUniqueId();
        orbHitsRemaining.put(orbUuid, cfg.getOrbHitsToDestroy());
        orbOwners.put(orbUuid, player.getUniqueId());

        BukkitTask[] trailHolder = new BukkitTask[1];
        trailHolder[0] = SchedulerUtils.runTaskTimer(() -> {
            if (orb.isDead() || !orbHitsRemaining.containsKey(orbUuid)) {
                if (trailHolder[0] != null) trailHolder[0].cancel();
                return;
            }
            ParticleUtils.spawnDust(orb.getLocation(), Color.fromRGB(180, 140, 40), 0.8f, 2, 0.1);
        }, 0L, 2L);
        orbTrailTasks.put(orbUuid, trailHolder[0]);

        Messages.send(player, "customitem.orb-thrown");
        SoundUtils.play(player, Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.8f);
    }

    /**
     * Detonates a live orb (manual right-click, natural impact, or 4th charged-arrow hit):
     * removes it and pulls all enemies within range toward its centre for the pull duration,
     * applying Slowness I and shrinking light-yellow->red beams as each target closes in.
     */
    public void activateOrb(Snowball orb) {
        UUID orbUuid = orb.getUniqueId();
        if (!orbHitsRemaining.containsKey(orbUuid) || orb.isDead()) return;

        UUID ownerUuid = orbOwners.get(orbUuid);
        cleanupOrbTracking(orbUuid);

        Location center = orb.getLocation().clone();
        orb.remove();

        Player owner = ownerUuid != null ? Bukkit.getPlayer(ownerUuid) : null;
        if (owner != null) {
            consumeOrbItem(owner);
            Messages.send(owner, "customitem.orb-activated");
            SoundUtils.play(owner, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
        }
        SoundUtils.playAt(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.6f);

        ItemsConfig cfg = ItemsConfig.getInstance();
        double radius = cfg.getOrbPullRadius();
        int durationTicks = cfg.getOrbPullDurationTicks();
        int slownessTicks = cfg.getOrbSlownessDurationSeconds() * 20;

        GameSession session = owner != null ? GameManager.getInstance().getPlayerSession(owner) : null;
        Team team = session != null ? session.getPlayerTeam(owner) : null;

        // Slowness I once + a colour progress marker for the beam lerp
        List<Player> pulled = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player target) || target.equals(owner)) continue;
            if (!target.hasLineOfSight(center)) continue;
            if (team != null) {
                Team targetTeam = session.getPlayerTeam(target);
                if (targetTeam != null && targetTeam.getTeamNumber() == team.getTeamNumber()) continue;
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessTicks, 0, false, false));
            pulled.add(target);
        }

        BukkitTask[] pullHolder = new BukkitTask[1];
        final int[] tick = {0};
        pullHolder[0] = SchedulerUtils.runTaskTimer(() -> {
            tick[0]++;
            float progress = Math.min(1.0f, tick[0] / (float) durationTicks);
            Color beamColor = lerpColor(Color.fromRGB(255, 230, 150), Color.fromRGB(200, 40, 40), progress);

            for (Player target : new ArrayList<>(pulled)) {
                if (!target.isOnline() || target.isDead()) continue;
                Vector toCenter = center.toVector().subtract(target.getLocation().toVector());
                if (toCenter.lengthSquared() < 0.25) continue; // arrived
                target.setVelocity(toCenter.normalize().multiply(0.55));
                ParticleUtils.beam(center.clone().add(0, 1, 0), target.getLocation().add(0, 1, 0), beamColor, 0.15f, 2);
            }
            ParticleUtils.spawnDust(center.clone().add(0, 1, 0), beamColor, 1.0f, 3, 0.3);

            if (tick[0] >= durationTicks) {
                if (pullHolder[0] != null) pullHolder[0].cancel();
            }
        }, 0L, 1L);
    }

    /**
     * A fully-charged bow shot hitting a live orb decrements its hits-remaining counter; on the
     * configured final hit the orb shatters (destroyed = fully resolved, so the item is consumed).
     */
    public void handleOrbHitByChargedArrow(Arrow arrow, Snowball orb) {
        UUID orbUuid = orb.getUniqueId();
        if (!orbHitsRemaining.containsKey(orbUuid)) return;
        if (arrow.getPersistentDataContainer().get(Keys.FULLY_CHARGED_ARROW, PersistentDataType.BYTE) == null) return;

        int left = orbHitsRemaining.get(orbUuid) - 1;
        if (left <= 0) {
            Location loc = orb.getLocation();
            UUID ownerUuid = orbOwners.get(orbUuid);
            cleanupOrbTracking(orbUuid);
            orb.remove();
            arrow.remove();
            Player owner = ownerUuid != null ? Bukkit.getPlayer(ownerUuid) : null;
            if (owner != null) {
                consumeOrbItem(owner);
                Messages.send(owner, "customitem.orb-destroyed");
            }
            SoundUtils.playAt(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.2f);
        } else {
            orbHitsRemaining.put(orbUuid, left);
        }
    }

    private void cleanupOrbTracking(UUID orbUuid) {
        orbHitsRemaining.remove(orbUuid);
        orbOwners.remove(orbUuid);
        BukkitTask trail = orbTrailTasks.remove(orbUuid);
        if (trail != null) trail.cancel();
    }

    /**
     * Consumes a single orb item wherever it sits in the owner's inventory (they may have
     * switched items since throwing, and the item is not consumed until the orb resolves).
     */
    private void consumeOrbItem(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (PDCDetection.getCustomItem(main) == CustomItem.ORB_OF_GRAVITATION) {
            consumeItem(player, main);
            return;
        }
        for (ItemStack slot : player.getInventory().getContents()) {
            if (slot != null && PDCDetection.getCustomItem(slot) == CustomItem.ORB_OF_GRAVITATION) {
                slot.setAmount(slot.getAmount() - 1);
                return;
            }
        }
    }

    private Color lerpColor(Color from, Color to, float t) {
        return Color.fromRGB(
                (int) (from.getRed() + (to.getRed() - from.getRed()) * t),
                (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * t));
    }

    // ==================== SOUL KATANA IMPLEMENTATION ====================

    /**
     * Shift + right-click Phantom Slice: launches the player forward, then ~4 ticks later (the
     * approximated "end of the leap") strikes every enemy within the strike radius with flat
     * damage, bypassing armor and active effects, and applies the healing-reduction debuff.
     */
    public void usePhantomSlice(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.SOUL_KATANA_PHANTOM_SLICE)) {
            double remaining = cooldownManager.getRemainingCooldownMs(uuid, CooldownManager.Keys.SOUL_KATANA_PHANTOM_SLICE) / 1000.0;
            Messages.send(player, "customitem.soul-katana-cooldown", "remaining", String.valueOf((int) Math.ceil(remaining)));
            return;
        }

        ItemsConfig cfg = ItemsConfig.getInstance();
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.SOUL_KATANA_PHANTOM_SLICE, cfg.getSoulKatanaCooldownSeconds());

        double leap = cfg.getSoulKatanaLeapDistance();
        Vector dir = player.getLocation().getDirection().clone().setY(0).normalize();
        player.setVelocity(dir.multiply(leap * 0.4).setY(0.2));

        Messages.send(player, "customitem.soul-katana-slice");
        SoundUtils.play(player, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.6f);

        SchedulerUtils.runTaskLater(() -> strikePhantomSlice(player, cfg), 4L);
    }

    private void strikePhantomSlice(Player player, ItemsConfig cfg) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        Team team = session != null ? session.getPlayerTeam(player) : null;
        double radius = cfg.getSoulKatanaStrikeRadius();
        double damage = cfg.getSoulKatanaStrikeDamage();
        int debuffDuration = cfg.getSoulKatanaHealingReductionDurationSeconds();
        double healingMultiplier = 1.0 - cfg.getSoulKatanaHealingReductionPercent() / 100.0;

        Location loc = player.getLocation();
        ParticleUtils.spawnDust(loc.clone().add(0, 1, 0), Color.fromRGB(150, 60, 220), 1.6f, 30, 0.5);
        SoundUtils.playAt(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.1f);

        for (Entity entity : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (!(entity instanceof Player target) || target.equals(player)) continue;
            if (team != null) {
                Team targetTeam = session.getPlayerTeam(target);
                if (targetTeam != null && targetTeam.getTeamNumber() == team.getTeamNumber()) continue;
            }
            // Direct damage call still fires a real EntityDamageByEntityEvent for kill attribution;
            // the transient flag lets DamageListener zero this hit's armor/effect modifiers.
            phantomSliceDamageActive.add(player.getUniqueId());
            try {
                target.damage(damage, player);
            } finally {
                phantomSliceDamageActive.remove(player.getUniqueId());
            }
            applyHealingReduction(target.getUniqueId(), healingMultiplier, debuffDuration);
        }
    }

    /**
     * @return true while the player's Phantom Slice strike damage is being applied (transient)
     */
    public boolean isPhantomSliceDamage(UUID attackerUuid) {
        return phantomSliceDamageActive.contains(attackerUuid);
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

        cashBlasterEarningsThisRound.clear();

        totemInvincible.clear();

        healingReducedUntil.clear();
        healingReductionMultiplier.clear();

        lotusChargeTasks.forEach((uuid, task) -> {
            task.cancel();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) removeLotusSlow(player);
        });
        lotusChargeTasks.clear();
        lotusChargeTicks.clear();

        iceFanGustResetTasks.values().forEach(BukkitTask::cancel);
        iceFanGustResetTasks.clear();
        iceFanGustStreak.clear();
        iceFanAbilityDamageActive.clear();

        overdrivePulseTasks.forEach((uuid, task) -> {
            task.cancel();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) removeOverdriveSpeed(player);
        });
        overdrivePulseTasks.clear();
        overdriveInvincible.clear();

        hunterMarkChargeTasks.values().forEach(BukkitTask::cancel);
        hunterMarkChargeTasks.clear();
        hunterMarkChargeTicks.clear();

        new ArrayList<>(hunterMarks.keySet()).forEach(this::clearHunterMark);
        hunterMarks.clear();
        markedUntil.clear();

        bloomingRoseZones.values().forEach(zone -> {
            if (zone.task() != null) zone.task().cancel();
            for (Block block : zone.blocks()) {
                if (block.getType() == Material.CHERRY_LOG || block.getType() == Material.CHERRY_LEAVES) {
                    block.setType(Material.AIR, false);
                }
            }
        });
        bloomingRoseZones.clear();

        orbTrailTasks.values().forEach(BukkitTask::cancel);
        orbTrailTasks.clear();
        orbHitsRemaining.keySet().forEach(uuid -> {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) entity.remove();
        });
        orbHitsRemaining.clear();
        orbOwners.clear();

        phantomSliceDamageActive.clear();
    }

    /**
     * Disable all active invisibility cloaks - used when shopping phase starts
     */
    public void disableAllInvisibilityCloaks() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        for (Player player : online) {
            UUID uuid = player.getUniqueId();
            if (invisCloakActive.contains(uuid)) {
                toggleInvisCloak(player, false);
                Messages.send(player, "customitem.invis-disabled-shopping");
            }
        }
    }
}

