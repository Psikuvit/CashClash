package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carl's Battleaxe - spinning melee attack with a visual Item Display axe.
 */
public class CarlsBattleaxeHandler extends MythicItemHandler {

    private final Set<UUID> spinningPlayers;

    public CarlsBattleaxeHandler(MythicItemManager manager) {
        super(manager);
        this.spinningPlayers = ConcurrentHashMap.newKeySet();
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

        new BukkitRunnable() {
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
        }.runTaskTimer(CashClashPlugin.getInstance(), 0L, 1L);

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
    }

    @Override
    public void cleanupPlayer(Player player) {
        spinningPlayers.remove(player.getUniqueId());
    }
}
