package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consolidated listener for all BlockBreakEvent and BlockPlaceEvent handling.
 * Handles: lobby protection, shop phase protection, map protection, custom items,
 * water/lava flow restrictions, web limits, leaf blocks.
 */
public class BlockListener implements Listener {

    private final GameManager gameManager;

    public BlockListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    /**
     * A floating countdown over a placed utility block. {@code colorTag} is a MiniMessage
     * colour name rather than a {@link org.bukkit.Color} because the label is text; each
     * utility gets the colour of the block it placed. {@code material} is what the block was
     * when the timer went up, so the ticker can drop the label the moment it stops being that.
     */
    private record DespawnTimer(TextDisplay display, Material material, long expiresAt, String colorTag) {}

    private static final Map<UUID, Set<Location>> placedBlocks = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<Integer, Integer>> waterLavaSourceCount = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<UUID, Integer>> playerLeafBlockCount = new ConcurrentHashMap<>();
    private static final Map<Location, BukkitTask> webDespawnTasks = new ConcurrentHashMap<>();
    private static final Map<Location, BukkitTask> leafDecayTasks = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<UUID, Integer>> playerWaterBucketRefillCount = new ConcurrentHashMap<>();
    private static final Map<Location, Location> waterLavaOrigins = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<Location>> quickFluidVisited = new ConcurrentHashMap<>();
    private static final Map<Location, DespawnTimer> despawnTimers = new ConcurrentHashMap<>();
    private static BukkitTask despawnTimerTask;

    // ==================== BLOCK PLACE ====================

    public static void cleanupRound(UUID sessionId) {
        Set<Location> blocks = placedBlocks.remove(sessionId);
        if (blocks != null) {
            for (Location loc : blocks) {
                cancelWebDespawnTask(loc);
                cancelLeafDecayTask(loc);
                removeDespawnTimer(loc);
            }
        }
        if (blocks == null) {
            Messages.debug("No blocks to clean up for session " + sessionId);
            return;
        }
        for (Location loc : blocks) {
            if (loc == null) continue;
            loc.getBlock().setType(Material.AIR);
        }
    }

    /**
     * Clean up tracked blocks when a session ends.
     */
    public static void cleanupSession(UUID sessionId) {
        placedBlocks.remove(sessionId);
        waterLavaSourceCount.remove(sessionId);
        playerLeafBlockCount.remove(sessionId);
        playerWaterBucketRefillCount.remove(sessionId);
        quickFluidVisited.remove(sessionId);
    }

    /**
     * Queue a water bucket refill for the player to receive during the next shopping phase.
     */
    private static void queueWaterBucketRefill(Player player) {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        if (session == null) {
            return;
        }

        UUID sessionId = session.getSessionId();
        UUID playerId = player.getUniqueId();
        playerWaterBucketRefillCount
            .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
            .merge(playerId, 1, Integer::sum);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        Material bucket = event.getBucket();
        if (bucket != Material.WATER_BUCKET && bucket != Material.LAVA_BUCKET) return;

        Player player = event.getPlayer();
        GameSession session = gameManager.getPlayerSession(player);
        if (session == null) return;

        Block target = event.getBlock();

        if (bucket == Material.WATER_BUCKET && target.getBlockData() instanceof Waterlogged) {
            event.setCancelled(true);
            Messages.send(player, "listener.water-bucket-waterlog-blocked");
            return;
        }

        Location origin = target.getLocation().toBlockLocation();
        waterLavaOrigins.put(origin, origin);

        boolean water = bucket == Material.WATER_BUCKET;
        Material fluid = water ? Material.WATER : Material.LAVA;

        if (water) {
            scheduleWaterLavaCleanup(target);
            queueWaterBucketRefill(player);
        } else {
            scheduleLavaCleanup(target);
        }

        trackPlacedBlock(session.getSessionId(), target);
        if (canQuickSpread(target, event.getBlockClicked())) {
            createQuickFluid(target, session.getSessionId(), origin, fluid);
        }

        SchedulerUtils.runTask(() -> {
            if (target.getType() != fluid) return;
            showDespawnTimer(target, fluid, 200, water ? "aqua" : "gold");
        });
    }

    /**
     * Refill all queued water buckets for a player during the shopping phase.
     * Replaces empty buckets in inventory with full ones.
     */
    public static void refillWaterBuckets(Player player) {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        if (session == null) {
            return;
        }

        Map<UUID, Integer> counts = playerWaterBucketRefillCount.get(session.getSessionId());
        if (counts == null) {
            return;
        }

        int refillCount = counts.getOrDefault(player.getUniqueId(), 0);
        if (refillCount <= 0) {
            return;
        }

        counts.remove(player.getUniqueId());

        ItemStack[] contents = player.getInventory().getContents();
        int refilled = 0;
        for (int i = 0; i < contents.length && refilled < refillCount; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.BUCKET) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                    player.getInventory().addItem(new ItemStack(Material.WATER_BUCKET));
                } else {
                    contents[i] = new ItemStack(Material.WATER_BUCKET);
                }
                refilled++;
            }
        }

        while (refilled < refillCount) {
            player.getInventory().addItem(new ItemStack(Material.WATER_BUCKET));
            refilled++;
        }

        player.getInventory().setContents(contents);
        Messages.send(player, "listener.water-bucket-refilled");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlaceGame(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        GameSession session = gameManager.getPlayerSession(player);

        if (!validateBlockPlaceContext(event, player, session)) {
            return;
        }

        Block block = event.getBlock();
        Material blockType = block.getType();
        UUID sessionId = session.getSessionId();
        UUID playerId = player.getUniqueId();

        if (handleWebPlacement(event, blockType, sessionId, playerId, block)) return;
        if (handleLeafPlacement(event, blockType, sessionId, playerId, block)) return;
        if (handleWaterLavaPlacement(event, blockType, sessionId, playerId, player, session)) return;

        trackPlacedBlock(sessionId, block);
    }

    /**
     * Validate block placement context (session, world, state)
     */
    private boolean validateBlockPlaceContext(BlockPlaceEvent event, Player player, GameSession session) {
        if (session == null) return false;

        if (!player.getWorld().equals(session.getGameWorld())) {
            Messages.debug("World mismatch for player " + player.getName() + " in BlockPlaceEvent");
            event.setCancelled(true);
            return false;
        }

        if (session.isActionsRestricted()) {
            Messages.debug("Block placement blocked during restricted sequence for player " + player.getName());
            event.setCancelled(true);
            return false;
        }

        GameState state = session.getState();
        Material blockType = event.getBlockPlaced().getType();

        if (state == GameState.SHOPPING) {
            // Only allow water/lava buckets during shopping (for refill)
            if (blockType != Material.WATER && blockType != Material.LAVA) {
                Messages.debug("Only water/lava placement allowed during SHOPPING for player " + player.getName());
                event.setCancelled(true);
                return false;
            }
        } else if (state != GameState.COMBAT) {
            Messages.debug("Session state is not COMBAT/SHOPPING for player " + player.getName() + " in BlockPlaceEvent");
            event.setCancelled(true);
            return false;
        }

        return true;
    }

    /**
     * Handle web block placement (max 8 per player)
     */
    private boolean handleWebPlacement(BlockPlaceEvent event, Material blockType, UUID sessionId, UUID playerId, Block block) {
        if (blockType != Material.COBWEB) return false;

        // Track placed block and schedule despawn (limit handled by inventory control)
        trackPlacedBlock(sessionId, block);
        scheduleWebDespawn(block);
        showDespawnTimer(block, Material.COBWEB, 160, "white");
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.setCancelled(true);
    }

    /**
     * Handle leaf block placement (max 64 per player, 3-block vertical stack limit).
     */
    private boolean handleLeafPlacement(BlockPlaceEvent event, Material blockType, UUID sessionId, UUID playerId, Block block) {
        if (!isLeafBlock(blockType)) return false;

        GameSession session = gameManager.getPlayerSession(Bukkit.getPlayer(playerId));
        if (session == null) return false;

        Map<UUID, Integer> counts = playerLeafBlockCount.computeIfAbsent(sessionId, k -> new HashMap<>());
        int currentLeafs = counts.getOrDefault(playerId, 0);

        if (currentLeafs >= 64) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (player != null) {
                Messages.send(player, "listener.max-leaf-blocks-reached");
            }
            return true;
        }

        if (checkVerticalLeafStack(block)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (player != null) {
                Messages.send(player, "listener.leaf-stack-limit");
            }
            return true;
        }

        counts.put(playerId, currentLeafs + 1);
        trackPlacedBlock(sessionId, block);
        scheduleLeafDecay(block);
        showDespawnTimer(block, blockType, 160, "green");
        return true;
    }

    /**
     * Check if placing a leaf block here would exceed 3-block vertical stack limit.
     */
    private boolean checkVerticalLeafStack(Block block) {
        int stackCount = 1;
        for (int i = -2; i <= 2; i++) {
            if (i == 0) continue;
            Block adjacent = block.getRelative(0, i, 0);
            if (isLeafBlock(adjacent.getType())) {
                stackCount++;
                if (stackCount > 3) return true;
            }
        }
        return false;
    }

    /**
     * Handle water/lava placement (max 4 per team)
     */
    private boolean handleWaterLavaPlacement(BlockPlaceEvent event, Material blockType, UUID sessionId, UUID playerId, Player player, GameSession session) {
        if (blockType != Material.WATER && blockType != Material.LAVA) return false;

        Team team = session.getTeamRed().hasPlayer(playerId) ? session.getTeamRed() : session.getTeamBlue();
        int teamNum = team.getTeamNumber();

        int currentCount = waterLavaSourceCount.computeIfAbsent(sessionId, k -> new HashMap<>())
            .getOrDefault(teamNum, 0);

        if (currentCount >= 4) {
            event.setCancelled(true);
            Messages.send(player, "listener.max-water-lava-sources");
            return true;
        }

        waterLavaSourceCount.get(sessionId).put(teamNum, currentCount + 1);
        trackPlacedBlock(sessionId, event.getBlock());

        // Track origin and schedule despawn so placed fluids flow then clear
        Location origin = event.getBlock().getLocation().toBlockLocation();
        waterLavaOrigins.put(origin, origin);
        if (blockType == Material.WATER) {
            scheduleWaterLavaCleanup(event.getBlock());
            showDespawnTimer(event.getBlock(), Material.WATER, 200, "aqua");
        } else if (blockType == Material.LAVA) {
            scheduleLavaCleanup(event.getBlock());
            showDespawnTimer(event.getBlock(), Material.LAVA, 200, "gold");
        }
        if (canQuickSpread(event.getBlock(), event.getBlockAgainst())) {
            createQuickFluid(event.getBlock(), sessionId, origin, blockType);
        }

        // Schedule water bucket refill (only for water, not lava)
        if (blockType == Material.WATER) {
            queueWaterBucketRefill(player);
        }
        return true;
    }

    // ==================== PLACED-UTILITY DESPAWN TIMERS ====================

    /**
     * Put a colour-matched countdown above a placed utility block so both teams can read how
     * much longer it will be there. Only the block the player actually placed gets one - the
     * water/lava blocks {@link #createQuickFluid} spreads out from it would otherwise stack a
     * dozen overlapping labels on one puddle.
     *
     * @param material what the block is expected to be for as long as the timer runs - passed
     *                 explicitly rather than read off {@code block}, since the bucket path
     *                 places its fluid a tick after the event that starts the timer
     */
    private static void showDespawnTimer(Block block, Material material, long durationTicks, String colorTag) {
        Location loc = block.getLocation().toBlockLocation();
        if (loc.getWorld() == null) return;
        removeDespawnTimer(loc);

        TextDisplay display = loc.getWorld().spawn(loc.clone().add(0.5, 1.1, 0.5), TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(true);
            d.setShadowed(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
        });

        despawnTimers.put(loc, new DespawnTimer(display, material,
                System.currentTimeMillis() + durationTicks * 50L, colorTag));
        startDespawnTimerTask();
    }

    /**
     * One shared ticker drives every countdown label instead of a task per block - a team can
     * easily have dozens of leaves and webs out at once. Stops itself once nothing is left to
     * count down.
     */
    private static void startDespawnTimerTask() {
        if (despawnTimerTask != null) return;

        despawnTimerTask = SchedulerUtils.runTaskTimer(() -> {
            long now = System.currentTimeMillis();
            despawnTimers.entrySet().removeIf(entry -> {
                Location loc = entry.getKey();
                DespawnTimer timer = entry.getValue();
                long remainingMs = timer.expiresAt() - now;

                // isWorldLoaded() rather than a null check: a session's world copy is deleted
                // when the game ends, and touching a block in it after that would blow up.
                boolean finished = remainingMs <= 0
                        || timer.display().isDead()
                        || !loc.isWorldLoaded()
                        || loc.getBlock().getType() != timer.material();
                if (finished) {
                    if (!timer.display().isDead()) timer.display().remove();
                    return true;
                }

                long seconds = (long) Math.ceil(remainingMs / 1000.0);
                timer.display().text(Messages.parse("<" + timer.colorTag() + ">" + seconds + "s</" + timer.colorTag() + ">"));
                return false;
            });

            if (despawnTimers.isEmpty() && despawnTimerTask != null) {
                despawnTimerTask.cancel();
                despawnTimerTask = null;
            }
        }, 0L, 10L);
    }

    private static void removeDespawnTimer(Location loc) {
        DespawnTimer timer = despawnTimers.remove(loc.toBlockLocation());
        if (timer != null && !timer.display().isDead()) {
            timer.display().remove();
        }
    }

    /**
     * Track a placed block location
     */
    private void trackPlacedBlock(UUID sessionId, Block block) {
        Location loc = block.getLocation().toBlockLocation();
        placedBlocks.computeIfAbsent(sessionId, k -> new HashSet<>()).add(loc);
    }

    /**
     * Limit water/lava vanilla spread to 3 blocks from the original source.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onWaterLavaSpread(BlockFromToEvent event) {
        if (event.isCancelled()) return;

        Block source = event.getBlock();
        Material type = source.getType();
        if (type != Material.WATER && type != Material.LAVA) return;

        Location sourceLocation = source.getLocation().toBlockLocation();

        Location origin = waterLavaOrigins.get(sourceLocation);

        // Try to find the origin from nearby tracked water blocks
        if (origin == null) {
            for (Map.Entry<Location, Location> entry : waterLavaOrigins.entrySet()) {
                if (entry.getKey().distanceSquared(sourceLocation) <= 4) {
                    origin = entry.getValue();
                    waterLavaOrigins.put(sourceLocation, origin);
                    break;
                }
            }
        }

        // Makes new water source origin
        if (origin == null) {
            origin = sourceLocation;
            waterLavaOrigins.put(sourceLocation, origin);
        }

        Location destination = event.getToBlock().getLocation().toBlockLocation();

        // Stop vanilla spread beyond 3 blocks
        if (destination.distance(origin) > 3.0) {
            event.setCancelled(true);
            return;
        }

        // Track newly spread water/lava so future spreads know the origin
        waterLavaOrigins.put(destination, origin);
    }

    /**
     * Force placed water/lava to flow outwards immediately rather than creeping at vanilla
     * speed, capped at 3 blocks from the origin by the caller's distance check.
     */
    /**
     * Whether the instant 4-way spread is safe here, or the placement should be left to vanilla
     * flow instead. The spread only walks sideways, so a source clicked onto a plant, button or
     * lever - or any spot with nothing solid beneath it - would otherwise pave a floating slab
     * of source blocks out into the air.
     */
    private boolean canQuickSpread(Block placed, Block clickedAgainst) {
        return clickedAgainst.getType().isSolid()
                && placed.getRelative(BlockFace.DOWN).getType().isSolid();
    }

    private void createQuickFluid(Block source, UUID sessionId, Location origin, Material fluid) {
        // Spreads the fluid the bucket actually held, never source.getType(). PlayerBucketEmptyEvent
        // fires before the liquid replaces the block, so reading the type off the source stamped
        // copies of whatever was clicked onto every neighbouring air block.
        if (fluid != Material.WATER && fluid != Material.LAVA) {
            return;
        }

        Set<Location> visited = quickFluidVisited.computeIfAbsent(
                sessionId,
                k -> ConcurrentHashMap.newKeySet()
        );

        Location loc = source.getLocation().toBlockLocation();

        if (loc.distance(origin) >= 3) {
            return;
        }

        if (!visited.add(loc)) {
            return;
        }

        for (BlockFace face : new BlockFace[]{
                BlockFace.NORTH,
                BlockFace.SOUTH,
                BlockFace.EAST,
                BlockFace.WEST
        }) {
            Block next = source.getRelative(face);
            if (next.getBlockData() instanceof Waterlogged) {
                continue;
            }
            if (next.getType() == Material.AIR) {
                next.setType(fluid);
                waterLavaOrigins.put(next.getLocation().toBlockLocation(), origin);
                trackPlacedBlock(sessionId, next);
                if (fluid == Material.WATER) {
                    scheduleWaterLavaCleanup(next);
                } else {
                    scheduleLavaCleanup(next);
                }
                SchedulerUtils.runTaskLater(
                        () -> createQuickFluid(next, sessionId, origin, fluid),
                        1L
                );
            }
        }
    }

    /**
     * Schedule a water/lava block to despawn after 10 seconds with visual effects.
     */
    private void scheduleWaterLavaCleanup(Block block) {
        Location loc = block.getLocation().toBlockLocation();

        SchedulerUtils.runTaskLater(() -> {
            if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                Location center = loc.clone().add(0.5, 0.5, 0.5);
                ParticleUtils.spawn(Particle.SPLASH, center, 12, 0.3, 0.2, 0.3, 0.05);
                ParticleUtils.spawn(Particle.BUBBLE, center, 8, 0.2, 0.2, 0.2, 0.02);
                SoundUtils.playAt(center, Sound.ENTITY_GENERIC_SPLASH, 0.7f, 1.0f);
                block.setType(Material.AIR);
            }
            waterLavaOrigins.remove(loc);
        }, 200);
    }

    /**
     * Schedule a lava block to despawn after 10 seconds with visual effects.
     */
    private void scheduleLavaCleanup(Block block) {
        Location loc = block.getLocation().toBlockLocation();

        SchedulerUtils.runTaskLater(() -> {
            if (block.getType() == Material.LAVA) {
                Location center = loc.clone().add(0.5, 0.5, 0.5);
                ParticleUtils.spawn(Particle.LAVA, center, 8, 0.25, 0.2, 0.25, 0);
                ParticleUtils.spawn(Particle.SMALL_FLAME, center, 12, 0.25, 0.2, 0.25, 0.01);
                SoundUtils.playAt(center, Sound.BLOCK_LAVA_EXTINGUISH, 0.7f, 1.0f);
                block.setType(Material.AIR);
            }
            waterLavaOrigins.remove(loc);
        }, 200);
    }

    /**
     * Schedule an arrow to despawn after 5 seconds.
     */
    private void scheduleArrowDespawn(AbstractArrow arrow) {
        SchedulerUtils.runTaskLater(() -> {
            if (!arrow.isDead()) {
                ParticleUtils.spawn(Particle.CRIT, arrow.getLocation(), 8, 0.2);
                SoundUtils.playAt(arrow.getLocation(), Sound.ENTITY_ARROW_HIT, 0.5f, 1.2f);
                arrow.remove();
            }
        }, 100);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArrowShoot(EntityShootBowEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        scheduleArrowDespawn(arrow);
    }

    // ==================== STATIC UTILITIES ====================

    /**
     * Handle leaf block breaks - don't drop items.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLeafBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Block block = event.getBlock();
        Material type = block.getType();

        if (isLeafBlock(type)) {
            event.setDropItems(false);
            cancelLeafDecayTask(block.getLocation());
            removeDespawnTimer(block.getLocation());

            // Decrement player's leaf count
            decrementPlayerLeaf(event, playerLeafBlockCount);
        }
    }

    private static void cancelLeafDecayTask(Location loc) {
        BukkitTask task = leafDecayTasks.remove(loc.toBlockLocation());
        if (task != null) {
            task.cancel();
        }
    }

    private void decrementPlayerLeaf(BlockBreakEvent event, Map<UUID, Map<UUID, Integer>> playerLeafBlockCount) {
        Player player = event.getPlayer();
        GameSession session = gameManager.getPlayerSession(player);
        if (session != null) {
            Map<UUID, Integer> counts = playerLeafBlockCount.get(session.getSessionId());
            if (counts != null) {
                int current = counts.getOrDefault(player.getUniqueId(), 0);
                if (current > 0) {
                    counts.put(player.getUniqueId(), current - 1);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreakShopPhase(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        GameSession session = gameManager.getPlayerSession(player);

        if (session != null && (session.getState() == GameState.SHOPPING || session.isActionsRestricted())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreakMapProtection(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        GameSession session = gameManager.getPlayerSession(player);

        if (session == null) return;

        UUID sessionId = session.getSessionId();
        Block block = event.getBlock();
        Location loc = block.getLocation().toBlockLocation();

        Set<Location> sessionPlacedBlocks = placedBlocks.get(sessionId);

        // If the block was NOT placed by a player, cancel the break
        if (!isBlockPlayerPlaced(sessionPlacedBlocks, loc)) {
            event.setCancelled(true);
            return;
        }

        // Block was player-placed, allow breaking and remove from tracking
        sessionPlacedBlocks.remove(loc);
        removeDespawnTimer(loc);

        // Update water/lava source count if applicable
        updateBlockTypeCountOnBreak(session, player, block);
    }

    /**
     * Check if a block was placed by a player
     */
    private boolean isBlockPlayerPlaced(Set<Location> sessionPlacedBlocks, Location loc) {
        return sessionPlacedBlocks != null && sessionPlacedBlocks.contains(loc);
    }

    /**
     * Update block type counts when a block is broken
     */
    private void updateBlockTypeCountOnBreak(GameSession session, Player player, Block block) {
        Material blockType = block.getType();
        if (blockType != Material.WATER && blockType != Material.LAVA) return;

        Team playerTeam = session.getTeamRed().hasPlayer(player.getUniqueId()) ? session.getTeamRed() : session.getTeamBlue();
        if (playerTeam == null) return;

        int teamNum = playerTeam.getTeamNumber();
        Map<Integer, Integer> counts = waterLavaSourceCount.get(session.getSessionId());
        if (counts != null) {
            int current = counts.getOrDefault(teamNum, 0);
            if (current > 0) {
                counts.put(teamNum, current - 1);
            }
        }
    }

    /**
     * Schedule web block to despawn after 8 seconds with visual effects.
     */
    private void scheduleWebDespawn(Block block) {
        Location loc = block.getLocation().toBlockLocation();
        BukkitTask task = SchedulerUtils.runTaskLater(() -> {
            if (block.getType() == Material.COBWEB) {
                Location center = loc.clone().add(0.5, 0.5, 0.5);
                ParticleUtils.spawn(Particle.CLOUD, center, 10, 0.25, 0.25, 0.25, 0.01);
                SoundUtils.playAt(center, Sound.BLOCK_WOOL_BREAK, 0.6f, 1.0f);
                block.setType(Material.AIR);
            }
            webDespawnTasks.remove(loc);
        }, 160);
        
        webDespawnTasks.put(loc, task);
    }

    /**
     * Cancel web despawn task if player touches it.
     */
    private static void cancelWebDespawnTask(Location loc) {
        BukkitTask task = webDespawnTasks.remove(loc);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Handle web block breaks - don't drop items.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onWebBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Block block = event.getBlock();
        if (block.getType() == Material.COBWEB) {
            event.setDropItems(false);
            cancelWebDespawnTask(block.getLocation());
            removeDespawnTimer(block.getLocation());
        }
    }

    /**
     * Schedule leaf block to decay after 8 seconds with visual effects.
     */
    private void scheduleLeafDecay(Block block) {
        Location loc = block.getLocation().toBlockLocation();
        BukkitTask task = SchedulerUtils.runTaskLater(() -> {
            if (isLeafBlock(block.getType())) {
                Location center = loc.clone().add(0.5, 0.5, 0.5);
                ParticleUtils.spawn(Particle.FALLING_DUST, center, 10, 0.3, block.getBlockData());
                SoundUtils.playAt(center, Sound.BLOCK_GRASS_BREAK, 1.0f, 1.0f);

                block.setType(Material.AIR);
            }
            leafDecayTasks.remove(loc);
        }, 160);
        leafDecayTasks.put(loc, task);
    }

    /**
     * Check if a material is a leaf block.
     */
    private boolean isLeafBlock(Material material) {
        return material == Material.OAK_LEAVES || material == Material.SPRUCE_LEAVES ||
               material == Material.BIRCH_LEAVES || material == Material.JUNGLE_LEAVES ||
               material == Material.ACACIA_LEAVES || material == Material.DARK_OAK_LEAVES ||
               material == Material.MANGROVE_LEAVES || material == Material.CHERRY_LEAVES ||
               material == Material.PALE_OAK_LEAVES;
    }
}

