package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carl's Battleaxe - spinning melee attack, a shift+right-click throw that catches and drags
 * enemies, and a visual Item Display axe used for both.
 */
public class CarlsBattleaxeHandler extends MythicItemHandler {

    private final Set<UUID> spinningPlayers;

    // Throw ability state, keyed by thrower - tracked at handler level (not just inside the
    // runnable closure) so cleanup()/cleanupPlayer() can tear down entities and restore the
    // stashed axe even if the player disconnects or the game ends mid-flight.
    private final Set<UUID> carlsThrowing;
    private final Map<UUID, ItemStack> carlsStashedAxe;
    private final Map<UUID, Entity> carlsThrowCarriers;
    private final Map<UUID, ItemDisplay> carlsThrowDisplays;

    public CarlsBattleaxeHandler(MythicItemManager manager) {
        super(manager);
        this.spinningPlayers = ConcurrentHashMap.newKeySet();
        this.carlsThrowing = ConcurrentHashMap.newKeySet();
        this.carlsStashedAxe = new ConcurrentHashMap<>();
        this.carlsThrowCarriers = new ConcurrentHashMap<>();
        this.carlsThrowDisplays = new ConcurrentHashMap<>();
    }

    /**
     * Activate Carl's Battleaxe spinning attack.
     * Player spins the axe around their body, slowed down, dealing high damage to nearby enemies.
     * Includes a spinning Item Display visual effect.
     */
    public void activateCarlsSpinAttack(Player attacker) {
        UUID uuid = attacker.getUniqueId();

        Messages.debug(attacker, "CARLS_BATTLEAXE: Spin attack activated");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_SLASH)) {
            Messages.debug(attacker, "CARLS_BATTLEAXE: Spin attack on cooldown - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_SLASH) + "s");
            Messages.send(attacker, "mythic.carls-battleaxe-cooldown", "cooldown_seconds", String.valueOf(cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_SLASH)));
            return;
        }

        if (spinningPlayers.contains(uuid)) {
            Messages.send(attacker, "mythic.carls-battleaxe-already-spinning");
            return;
        }

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_SLASH, cfg.getCarlsSpinCooldown());
        spinningPlayers.add(uuid);

        // Apply slowness during spin
        CashClashPlayer.applyEffect(attacker, PotionEffectType.SLOWNESS, cfg.getCarlsSpinDuration(), 1);

        Messages.send(attacker, "mythic.carls-battleaxe-activated");
        SoundUtils.play(attacker, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

        // Spawn the spinning axe display
        ItemDisplay axeDisplay = spawnSpinningAxeDisplay(attacker);

        // Start the spin attack runnable
        final int duration = cfg.getCarlsSpinDuration();
        final double damage = cfg.getCarlsSpinDamage();
        final double radius = cfg.getCarlsSpinRadius();
        final int hitInterval = cfg.getCarlsSpinHitInterval();
        final Set<UUID> recentlyHit = new HashSet<>();

        BukkitRunnable spinRunnable = new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;
            double heightOffset = 0;
            boolean goingUp = true;

            @Override
            public void run() {
                if (ticks >= duration || !attacker.isOnline() || attacker.isDead()) {
                    cleanup();
                    return;
                }

                // Update axe display position - spin around player and bob up/down
                if (axeDisplay.isValid()) {
                    angle += Math.PI / 8; // Spin speed

                    // Bob up and down
                    if (goingUp) {
                        heightOffset += 0.05;
                        if (heightOffset >= 0.5) goingUp = false;
                    } else {
                        heightOffset -= 0.05;
                        if (heightOffset <= -0.3) goingUp = true;
                    }

                    double x = Math.cos(angle) * 1.2;
                    double z = Math.sin(angle) * 1.2;
                    Location newLoc = attacker.getLocation().add(x, 1.0 + heightOffset, z);

                    // Make the axe face the player (handle towards player)
                    float yaw = (float) Math.toDegrees(Math.atan2(-x, -z));
                    newLoc.setYaw(yaw);
                    newLoc.setPitch(0);

                    axeDisplay.teleport(newLoc);
                    // Rotate the axe itself for spinning visual
                    axeDisplay.setRotation(yaw + (ticks * 15), 90); // Vertical orientation with spin
                }

                // Deal damage every hitInterval ticks
                if (ticks % hitInterval == 0) {
                    recentlyHit.clear();

                    for (Entity entity : attacker.getNearbyEntities(radius, radius, radius)) {
                        if (!(entity instanceof Player victim)) continue;
                        if (victim.equals(attacker)) continue;
                        if (recentlyHit.contains(victim.getUniqueId())) continue;

                        // Check if in same game and different team
                        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
                        if (session == null) continue;

                        Team attackerTeam = session.getPlayerTeam(attacker);
                        Team victimTeam = session.getPlayerTeam(victim);
                        if (attackerTeam == null || victimTeam == null) continue;
                        if (attackerTeam.equals(victimTeam)) continue;

                        // Deal damage
                        victim.damage(damage, attacker);
                        recentlyHit.add(victim.getUniqueId());

                        // Visual feedback
                        SoundUtils.playAt(victim.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
                        ParticleUtils.hitFeedback(victim.getLocation(), 15, 0.3);
                        Messages.debug(attacker, "CARLS_BATTLEAXE: Spin hit " + victim.getName() + " for " + damage + " damage");
                    }
                }

                // Spinning particles around player
                if (ticks % 2 == 0) {
                    ParticleUtils.spinSweep(attacker.getLocation(), angle, radius);
                }

                // Sound every 10 ticks
                if (ticks % 10 == 0) {
                    SoundUtils.playAt(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
                }

                ticks++;
            }

            private void cleanup() {
                spinningPlayers.remove(uuid);
                if (axeDisplay.isValid()) {
                    axeDisplay.remove();
                }
                CashClashPlayer.removeEffect(attacker, PotionEffectType.SLOWNESS);
                Messages.send(attacker, "mythic.carls-battleaxe-ended");
                cancel();
            }
        };
        SchedulerUtils.runTaskTimer(spinRunnable, 0L, 1L);

        Messages.debug(attacker, "CARLS_BATTLEAXE: Spin attack started! Duration: " + (duration / 20) + "s, Damage: " + damage + ", Radius: " + radius);
    }

    /**
     * Spawn an ItemDisplay entity showing Carl's Battleaxe spinning around the player.
     */
    private ItemDisplay spawnSpinningAxeDisplay(Player player) {
        Location spawnLoc = player.getLocation().add(1.2, 1.0, 0);

        return player.getWorld().spawn(spawnLoc, ItemDisplay.class, display -> {
            // Create the axe item
            ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
            display.setItemStack(axe);

            // Set the transformation for vertical orientation (handle facing player)
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);

            // Make it larger and rotate to vertical (handle down)
            Transformation transform = display.getTransformation();
            Quaternionf leftRotation = new Quaternionf();
            leftRotation.rotateX((float) Math.toRadians(90)); // Rotate to vertical

            display.setTransformation(new Transformation(
                    transform.getTranslation(),
                    leftRotation,
                    new Vector3f(1.5f, 1.5f, 1.5f), // Scale up
                    transform.getRightRotation()
            ));

            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
        });
    }

    /**
     * Check if a player is currently in a spin attack.
     */
    public boolean isSpinning(UUID playerId) {
        return spinningPlayers.contains(playerId);
    }

    /**
     * Carl's Battleaxe Throw (shift+right-click). The axe leaves the player's hand and flies
     * out up to {@code distance} blocks (or until it hits a wall), catching enemies along the
     * way; it then reverses and drags any caught players back, releasing + damaging them once
     * within {@code release-distance} of the thrower, and the axe reappears in their hand.
     */
    public void useCarlsThrow(Player player) {
        UUID uuid = player.getUniqueId();

        if (carlsThrowing.contains(uuid)) return;

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_THROW)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_THROW);
            Messages.send(player, "mythic.carls-battleaxe-throw-cooldown", "cooldown_seconds", String.valueOf(remaining));
            return;
        }

        ItemStack axe = player.getInventory().getItemInMainHand();
        if (PDCDetection.getMythic(axe) != MythicItem.CARLS_BATTLEAXE) return;

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_THROW, cfg.getCarlsThrowCooldown());
        carlsThrowing.add(uuid);
        carlsStashedAxe.put(uuid, axe.clone());
        player.getInventory().setItemInMainHand(null);

        Messages.send(player, "mythic.carls-battleaxe-throw-activated");
        SoundUtils.play(player, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.6f);

        Location startLoc = player.getEyeLocation();
        Snowball carrier = player.getWorld().spawn(startLoc, Snowball.class, s -> {
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setPersistent(false);
            s.setSilent(true);
            s.setVisibleByDefault(false);
            s.setShooter(player);
        });
        ItemDisplay display = spawnThrownAxeDisplay(startLoc);
        carlsThrowCarriers.put(uuid, carrier);
        carlsThrowDisplays.put(uuid, display);

        double maxDistance = cfg.getCarlsThrowDistance();
        double stepSize = cfg.getCarlsThrowSpeedPerTick();
        double catchRadius = cfg.getCarlsThrowCatchRadius();
        double releaseDistance = cfg.getCarlsThrowReleaseDistance();
        double damage = cfg.getCarlsThrowDamage();

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        Team playerTeam = session != null ? session.getPlayerTeam(player) : null;

        BukkitRunnable throwRunnable = new BukkitRunnable() {
            Vector direction = startLoc.getDirection().normalize();
            double traveled = 0;
            boolean returning = false;
            final List<Player> caught = new ArrayList<>();

            @Override
            public void run() {
                if (!player.isOnline() || !carrier.isValid()) {
                    finish(false);
                    return;
                }

                Location carrierLoc = carrier.getLocation();

                if (!returning) {
                    Location nextLoc = carrierLoc.clone().add(direction.clone().multiply(stepSize));
                    traveled += stepSize;
                    boolean hitWall = nextLoc.getBlock().getType().isSolid();
                    moveTo(nextLoc, direction);

                    for (Entity entity : carrier.getNearbyEntities(catchRadius, catchRadius, catchRadius)) {
                        if (!(entity instanceof Player target)) continue;
                        if (target.equals(player) || caught.contains(target)) continue;
                        if (session != null && playerTeam != null) {
                            Team targetTeam = session.getPlayerTeam(target);
                            if (targetTeam != null && targetTeam.getTeamNumber() == playerTeam.getTeamNumber()) continue;
                        }
                        caught.add(target);
                        SoundUtils.play(target, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
                        Messages.debug(player, "CARLS_BATTLEAXE: Throw caught " + target.getName());
                    }

                    if (hitWall || traveled >= maxDistance) {
                        returning = true;
                    }
                } else {
                    Vector toPlayer = player.getEyeLocation().toVector().subtract(carrierLoc.toVector());
                    double distanceToPlayer = toPlayer.length();
                    if (distanceToPlayer <= releaseDistance) {
                        finish(true);
                        return;
                    }
                    Vector returnDir = toPlayer.normalize();
                    Location nextLoc = carrierLoc.clone().add(returnDir.clone().multiply(stepSize));
                    moveTo(nextLoc, returnDir);
                }

                // Drag caught players along every tick once caught
                Location dragLoc = carrier.getLocation().add(0, 0.5, 0);
                for (Player c : caught) {
                    if (c.isOnline()) c.teleport(dragLoc);
                }

                ParticleUtils.spawnDust(carrier.getLocation(), Color.fromRGB(150, 150, 160), 1.0f, 4, 0.15);
            }

            private void moveTo(Location nextLoc, Vector facing) {
                carrier.teleport(nextLoc);
                if (display.isValid()) {
                    float yaw = (float) Math.toDegrees(Math.atan2(-facing.getX(), -facing.getZ()));
                    nextLoc.setYaw(yaw);
                    display.teleport(nextLoc);
                    display.setRotation(yaw, 90);
                }
            }

            private void finish(boolean natural) {
                cancel();
                carlsThrowing.remove(uuid);
                carlsThrowCarriers.remove(uuid);
                carlsThrowDisplays.remove(uuid);
                if (carrier.isValid()) carrier.remove();
                if (display.isValid()) display.remove();

                if (natural && !caught.isEmpty()) {
                    for (Player c : caught) {
                        if (!c.isOnline()) continue;
                        c.setNoDamageTicks(0);
                        c.setMaximumNoDamageTicks(0);
                        c.damage(damage, player);
                        SchedulerUtils.runTaskLater(() -> {
                            if (c.isOnline()) c.setMaximumNoDamageTicks(20);
                        }, 1L);
                        ParticleUtils.damageIndicator(c.getLocation().add(0, 1, 0), 20, 0.5);
                        SoundUtils.play(c, Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
                    }
                    Messages.send(player, "mythic.carls-battleaxe-throw-hit", "{enemy_count}", String.valueOf(caught.size()));
                }

                ItemStack stashed = carlsStashedAxe.remove(uuid);
                if (player.isOnline() && stashed != null) {
                    player.getInventory().setItemInMainHand(stashed);
                }
                Messages.debug(player, "CARLS_BATTLEAXE: Throw ended, caught " + caught.size() + " players");
            }
        };

        BukkitTask task = SchedulerUtils.runTaskTimer(throwRunnable, 0L, 1L);
        manager.trackTask(uuid, task);
    }

    /**
     * Spawn a (non-spinning) ItemDisplay axe for the Throw ability, oriented per-tick towards
     * the direction of travel by the caller.
     */
    private ItemDisplay spawnThrownAxeDisplay(Location loc) {
        return loc.getWorld().spawn(loc, ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.NETHERITE_AXE));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);

            Transformation transform = display.getTransformation();
            Quaternionf leftRotation = new Quaternionf();
            leftRotation.rotateX((float) Math.toRadians(90));
            display.setTransformation(new Transformation(
                    transform.getTranslation(),
                    leftRotation,
                    new Vector3f(1.3f, 1.3f, 1.3f),
                    transform.getRightRotation()
            ));
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
        });
    }

    /**
     * Handle Carl's Battleaxe critical hit launch.
     * Critical hits (while falling) launch enemies into the air.
     * 10 second cooldown.
     */
    public void handleCarlsCriticalHit(Player attacker, Player victim) {
        // Launch ability removed - now just deals normal critical damage
        Messages.debug(attacker, "CARLS_BATTLEAXE: Critical hit detected (launch disabled)");

        SoundUtils.play(attacker, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
        ParticleUtils.crit(victim.getLocation().add(0, 1, 0), 20, 0.5);
    }

    @Override
    public void cleanup() {
        spinningPlayers.clear();

        carlsThrowCarriers.values().forEach(carrier -> {
            if (carrier != null && carrier.isValid()) carrier.remove();
        });
        carlsThrowCarriers.clear();
        carlsThrowDisplays.values().forEach(display -> {
            if (display != null && display.isValid()) display.remove();
        });
        carlsThrowDisplays.clear();
        carlsThrowing.clear();
        carlsStashedAxe.clear();
    }

    @Override
    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        spinningPlayers.remove(uuid);

        if (carlsThrowing.remove(uuid)) {
            Entity carrier = carlsThrowCarriers.remove(uuid);
            if (carrier != null && carrier.isValid()) carrier.remove();

            ItemDisplay display = carlsThrowDisplays.remove(uuid);
            if (display != null && display.isValid()) display.remove();

            ItemStack stashed = carlsStashedAxe.remove(uuid);
            if (player.isOnline() && stashed != null) {
                player.getInventory().setItemInMainHand(stashed);
            }
        }
    }
}
