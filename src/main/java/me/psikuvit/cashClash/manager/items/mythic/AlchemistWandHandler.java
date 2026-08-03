package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alchemist Wand - teammate Blink Swap with short damage protection and a
 * chain taunt linking nearby teammates with visible chains.
 */
public class AlchemistWandHandler extends MythicItemHandler {

    // Alchemist Blink Swap invincibility (UUID -> hits remaining)
    private final Map<UUID, Integer> alchemistBlinkProtection;
    private final Map<UUID, Long> alchemistBlinkProtectionExpiry;

    // Alchemist Wand Taunt tracking
    private final Map<UUID, Set<UUID>> alchemistTauntChains;
    private final Map<UUID, Long> alchemistTauntExpiry;
    private final Map<UUID, List<ItemDisplay>> alchemistTauntDisplays;

    public AlchemistWandHandler(MythicItemManager manager) {
        super(manager);
        this.alchemistBlinkProtection = new ConcurrentHashMap<>();
        this.alchemistBlinkProtectionExpiry = new ConcurrentHashMap<>();
        this.alchemistTauntChains = new ConcurrentHashMap<>();
        this.alchemistTauntExpiry = new ConcurrentHashMap<>();
        this.alchemistTauntDisplays = new ConcurrentHashMap<>();
    }

    /**
     * Alchemist Wand Blink Swap.
     * Swaps positions with the player directly under the crosshair.
     * Target must be within 10 blocks and have line of sight.
     */
    public void useAlchemistBlinkSwap(Player player) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "ALCHEMIST_WAND: Blink Swap triggered");

        // Check cooldown
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.ALCHEMIST_BLINK_SWAP)) {
            Messages.debug(player, "ALCHEMIST_WAND: Blink Swap on cooldown - "
                    + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.ALCHEMIST_BLINK_SWAP) + "s");
            return;
        }

        // Get game session and player's team
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.debug(player, "ALCHEMIST_WAND: No session");
            return;
        }

        Team playerTeam = session.getPlayerTeam(player);
        if (playerTeam == null) {
            Messages.debug(player, "ALCHEMIST_WAND: Player has no team");
            return;
        }

        // Ray trace from the player's crosshair
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();

        RayTraceResult result = player.getWorld().rayTraceEntities(
                start,
                direction,
                10.0,
                0.1,
                entity -> entity instanceof Player target
                        && !target.equals(player)
                        && isSameTeam(session, playerTeam.getTeamNumber(), target)
        );

        // No valid teammate under crosshair
        if (result == null || !(result.getHitEntity() instanceof Player target)) {
            Messages.debug(player, "ALCHEMIST_WAND: No valid teammate target");
            return;
        }

        // Require clear line of sight
        if (!player.hasLineOfSight(target)) {
            Messages.debug(player, "ALCHEMIST_WAND: Target is not in line of sight");
            return;
        }

        // Store both positions, including their original orientations
        Location playerLocation = player.getLocation().clone();
        Location targetLocation = target.getLocation().clone();

        // Play Blink Swap visual at both original positions
        playAlchemistBlinkVisual(playerLocation);
        playAlchemistBlinkVisual(targetLocation);

        // Give both players protection against their next 2 damage hits
        long protectionExpiry = System.currentTimeMillis() + 5000;

        alchemistBlinkProtection.put(uuid, 2);
        alchemistBlinkProtection.put(target.getUniqueId(), 2);

        alchemistBlinkProtectionExpiry.put(uuid, protectionExpiry);
        alchemistBlinkProtectionExpiry.put(target.getUniqueId(), protectionExpiry);

        // Swap positions
        player.teleport(targetLocation);
        target.teleport(playerLocation);

        // Play Blink Swap activation sound
        SoundUtils.play(player, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.0f);

        // Start cooldown only after a successful swap
        cooldownManager.setCooldownSeconds(
                uuid,
                CooldownManager.Keys.ALCHEMIST_BLINK_SWAP,
                11
        );

        Messages.debug(player, "ALCHEMIST_WAND: Blink Swap successful with " + target.getName());
    }

    /**
     * Creates a yellow spiral effect for Alchemist Wand Blink Swap.
     */
    private void playAlchemistBlinkVisual(Location location) {
        if (location == null || location.getWorld() == null) return;

        Location center = location.clone();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 13 || center.getWorld() == null) {
                    cancel();
                    return;
                }

                double progress = ticks / 13.0;
                double radius = 0.25 + (progress * 0.75);
                double height = progress * 1.8;
                double angle = ticks * 0.8;

                for (int i = 0; i < 3; i++) {
                    double offsetAngle = angle + (i * (Math.PI * 2 / 3));

                    double x = Math.cos(offsetAngle) * radius;
                    double z = Math.sin(offsetAngle) * radius;

                    Location particleLocation = center.clone().add(x, height, z);

                    ParticleUtils.spawnDust(
                            particleLocation,
                            Color.fromRGB(255, 220, 0),
                            1.5f,
                            2,
                            0.05
                    );
                }

                ticks++;
            }
        }.runTaskTimer(CashClashPlugin.getInstance(), 0L, 1L);
    }

    /**
     * Checks and consumes Alchemist Blink Swap damage protection.
     *
     * @return true if the damage should be cancelled
     */
    public boolean handleAlchemistBlinkProtection(Player player) {
        UUID uuid = player.getUniqueId();

        Integer hitsRemaining = alchemistBlinkProtection.get(uuid);
        if (hitsRemaining == null || hitsRemaining <= 0) {
            return false;
        }

        // Protection expires after 5 seconds
        Long expiry = alchemistBlinkProtectionExpiry.get(uuid);
        if (expiry == null || System.currentTimeMillis() >= expiry) {
            alchemistBlinkProtection.remove(uuid);
            alchemistBlinkProtectionExpiry.remove(uuid);
            return false;
        }

        // Consume one protected hit
        if (hitsRemaining <= 1) {
            alchemistBlinkProtection.remove(uuid);
            alchemistBlinkProtectionExpiry.remove(uuid);
        } else {
            alchemistBlinkProtection.put(uuid, hitsRemaining - 1);
        }

        Messages.debug(player, "ALCHEMIST_WAND: Blink protection blocked damage. Hits remaining: "
                + Math.max(0, hitsRemaining - 1));

        return true;
    }

    private Map<UUID, Set<UUID>> buildAlchemistTauntLinks(Player wielder, GameSession session) {
        Map<UUID, Set<UUID>> links = new HashMap<>();

        Team wielderTeam = session.getPlayerTeam(wielder);
        if (wielderTeam == null) {
            return links;
        }

        List<Player> teamPlayers = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Team team = session.getPlayerTeam(player);

            if (team != null &&
                    team.getTeamNumber() == wielderTeam.getTeamNumber()) {
                teamPlayers.add(player);
                links.put(player.getUniqueId(), new HashSet<>());
            }
        }

        // Build direct 6.5-block connections
        for (Player first : teamPlayers) {
            for (Player second : teamPlayers) {
                if (first.equals(second)) continue;

                if (!first.getWorld().equals(second.getWorld())) continue;

                if (first.getLocation().distance(second.getLocation()) <= 6.5) {
                    links.get(first.getUniqueId()).add(second.getUniqueId());
                }
            }
        }

        // Only keep players that are actually connected to the wielder
        Set<UUID> connectedToWielder = new HashSet<>();
        Set<UUID> toVisit = new HashSet<>();
        toVisit.add(wielder.getUniqueId());

        while (!toVisit.isEmpty()) {
            UUID current = toVisit.iterator().next();
            toVisit.remove(current);

            if (!connectedToWielder.add(current)) {
                continue;
            }

            Set<UUID> neighbors = links.get(current);
            if (neighbors != null) {
                for (UUID neighbor : neighbors) {
                    if (!connectedToWielder.contains(neighbor)) {
                        toVisit.add(neighbor);
                    }
                }
            }
        }

        // Remove players who aren't connected to the wielder
        links.keySet().removeIf(uuid -> !connectedToWielder.contains(uuid));

        return links;
    }

    public void useAlchemistTaunt(Player wielder) {
        UUID uuid = wielder.getUniqueId();

        Messages.debug(wielder, "ALCHEMIST_WAND: Taunt triggered");

        // Check cooldown
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.ALCHEMIST_TAUNT)) {
            Messages.debug(wielder, "ALCHEMIST_WAND: Taunt on cooldown - "
                    + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.ALCHEMIST_TAUNT) + "s");
            return;
        }

        // Get game session
        GameSession session = GameManager.getInstance().getPlayerSession(wielder);
        if (session == null) {
            Messages.debug(wielder, "ALCHEMIST_WAND: No session");
            return;
        }

        // Find everyone connected through the 6.5 block chain network
        Map<UUID, Set<UUID>> tauntLinks = buildAlchemistTauntLinks(wielder, session);

        // Create chain visuals
        List<ItemDisplay> displays = new ArrayList<>();

        for (Map.Entry<UUID, Set<UUID>> entry : tauntLinks.entrySet()) {
            Player first = Bukkit.getPlayer(entry.getKey());
            if (first == null) continue;

            for (UUID secondUuid : entry.getValue()) {
                Player second = Bukkit.getPlayer(secondUuid);
                if (second == null) continue;

                // Only create each chain once
                if (first.getUniqueId().compareTo(second.getUniqueId()) >= 0) continue;

                ItemDisplay display = createAlchemistChainDisplay(first, second);
                if (display != null) {
                    displays.add(display);
                }
            }
        }

        alchemistTauntDisplays.put(wielder.getUniqueId(), displays);

        // Store the chain links
        for (Map.Entry<UUID, Set<UUID>> entry : tauntLinks.entrySet()) {
            alchemistTauntChains.put(entry.getKey(), entry.getValue());
        }

        // Taunt lasts 7 seconds
        long expiry = System.currentTimeMillis() + 7000;
        alchemistTauntExpiry.put(uuid, expiry);

        // Start cooldown
        cooldownManager.setCooldownSeconds(
                uuid,
                CooldownManager.Keys.ALCHEMIST_TAUNT,
                28
        );

        Set<UUID> chainedPlayers = findAlchemistTauntChain(wielder, session);
        alchemistTauntChains.put(wielder.getUniqueId(), chainedPlayers);
        alchemistTauntExpiry.put(wielder.getUniqueId(), System.currentTimeMillis() + 7000);
        Messages.debug(wielder, "ALCHEMIST_WAND: Taunt activated with "
                + tauntLinks.size() + " chained players");

        Messages.send(wielder, "mythic.alchemist-taunt-activated");
    }

    private ItemDisplay createAlchemistChainDisplay(Player first, Player second) {
        Location firstLocation = first.getLocation().clone().add(0, 1.0, 0);
        Location secondLocation = second.getLocation().clone().add(0, 1.0, 0);

        Vector difference = secondLocation.toVector().subtract(firstLocation.toVector());
        double distance = difference.length();

        if (distance <= 0.05) {
            return null;
        }

        Vector direction = difference.clone().normalize();

        // Position the display halfway between both players
        Location center = firstLocation.clone().add(
                direction.clone().multiply(distance / 2.0)
        );

        return first.getWorld().spawn(center, ItemDisplay.class, display -> {
            ItemStack chain = new ItemStack(Material.matchMaterial("CHAIN"));
            display.setItemStack(chain);

            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));

            // Rotate the chain so it points between the two players
            float yaw = (float) Math.toDegrees(
                    Math.atan2(-direction.getX(), direction.getZ())
            );

            float pitch = (float) Math.toDegrees(
                    Math.asin(-direction.getY())
            );

            display.setRotation(yaw, pitch);

            // Stretch the chain to approximately span the distance
            Transformation transform = display.getTransformation();

            display.setTransformation(new Transformation(
                    transform.getTranslation(),
                    transform.getLeftRotation(),
                    new Vector3f(
                            1.0f,
                            (float) distance,
                            1.0f
                    ),
                    transform.getRightRotation()
            ));
        });
    }

    private Set<UUID> findAlchemistTauntChain(Player wielder, GameSession session) {
        Set<UUID> chainedPlayers = new HashSet<>();

        // Start with the wielder
        Set<UUID> connected = new HashSet<>();
        connected.add(wielder.getUniqueId());

        boolean changed;

        do {
            changed = false;

            for (UUID playerId : session.getPlayers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) continue;
                if (connected.contains(player.getUniqueId())) {
                    continue;
                }

                // Check if this player is within 6.5 blocks of ANY
                // player already connected to the chain.
                for (UUID connectedId : connected) {
                    Player connectedPlayer = Bukkit.getPlayer(connectedId);
                    if (connectedPlayer == null || !connectedPlayer.isOnline()) {
                        continue;
                    }

                    if (!connectedPlayer.getWorld().equals(player.getWorld())) {
                        continue;
                    }

                    if (connectedPlayer.getLocation().distance(player.getLocation()) <= 6.5) {
                        connected.add(player.getUniqueId());
                        chainedPlayers.add(player.getUniqueId());
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);

        return chainedPlayers;
    }

    private void removeAlchemistTauntDisplays(UUID playerId) {
        List<ItemDisplay> displays = alchemistTauntDisplays.remove(playerId);
        if (displays == null) return;
        for (ItemDisplay display : displays) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
    }

    @Override
    public void cleanup() {
        alchemistBlinkProtection.clear();
        alchemistBlinkProtectionExpiry.clear();
        alchemistTauntChains.clear();
        alchemistTauntExpiry.clear();
        for (UUID playerId : alchemistTauntDisplays.keySet()) {
            removeAlchemistTauntDisplays(playerId);
        }
        alchemistTauntDisplays.clear();
    }

    @Override
    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        alchemistBlinkProtection.remove(uuid);
        alchemistBlinkProtectionExpiry.remove(uuid);
        alchemistTauntChains.remove(uuid);
        alchemistTauntExpiry.remove(uuid);
        removeAlchemistTauntDisplays(uuid);
    }
}
