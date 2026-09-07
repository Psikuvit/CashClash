package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
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
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    private final ItemsConfig itemsConfig;

    public BlockListener(GameManager gameManager, ItemsConfig itemsConfig) {
        this.gameManager = gameManager;
        this.itemsConfig = itemsConfig;
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
    private static final Map<Location, BukkitTask> webDespawnTasks = new ConcurrentHashMap<>();
    private static final Map<Location, BukkitTask> leafDecayTasks = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<UUID, Integer>> playerWaterBucketRefillCount = new ConcurrentHashMap<>();
    // What a placed fluid displaced, so the map can be put back exactly as it was once the
    // fluid despawns rather than left as a hole where a plant or lever used to be.
    private static final Map<Location, BlockData> fluidReplacedBlocks = new ConcurrentHashMap<>();
    private static final Map<Location, DespawnTimer> despawnTimers = new ConcurrentHashMap<>();
    private static BukkitTask despawnTimerTask;

    /**
     * One "quick fluid" placement: gravity-first, breadth-first-outward extent computed once as
     * a plain synchronous pass (see {@link #planQuickFluid}) rather than a live recursion racing
     * against a possible concurrent drain. {@code rings} is reveal order - each inner list is
     * every cell at the same hop distance from the source, revealed or removed together, one
     * ring per flow tick. The source block itself isn't in {@code rings}; it's tracked and
     * drained separately, always last.
     */
    private static final class PlacedFluid {
        final Location origin;
        final Material fluid;
        final UUID sessionId;
        final List<List<Location>> rings;
        volatile boolean active = true;

        PlacedFluid(Location origin, Material fluid, UUID sessionId, List<List<Location>> rings) {
            this.origin = origin;
            this.fluid = fluid;
            this.sessionId = sessionId;
            this.rings = rings;
        }
    }
    
    private static final Map<Location, PlacedFluid> fluidCells = new ConcurrentHashMap<>();

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
            restoreAfterFluid(loc.getBlock());
        }
    }

    /**
     * Clean up tracked blocks when a session ends.
     */
    public static void cleanupSession(UUID sessionId) {
        placedBlocks.remove(sessionId);
        waterLavaSourceCount.remove(sessionId);
        playerWaterBucketRefillCount.remove(sessionId);
        fluidReplacedBlocks.clear();
        fluidCells.clear();
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
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Material bucket = event.getBucket();
        if (bucket != Material.WATER_BUCKET && bucket != Material.LAVA_BUCKET) return;

        Player player = event.getPlayer();
        GameSession session = gameManager.getPlayerSession(player);
        if (session == null) {
            Messages.debug(player, "FLUID", "bucket ignored: player not in a session");
            return;
        }

        Block target = event.getBlock();

        if (bucket == Material.WATER_BUCKET && target.getBlockData() instanceof Waterlogged) {
            event.setCancelled(true);
            Messages.debug(player, "FLUID", "bucket refused: " + describe(target) + " is waterloggable");
            Messages.send(player, "listener.water-bucket-waterlog-blocked");
            return;
        }

        Location origin = target.getLocation().toBlockLocation();
        boolean water = bucket == Material.WATER_BUCKET;
        Material fluid = water ? Material.WATER : Material.LAVA;

        rememberReplacedBlock(target);
        trackPlacedBlock(session.getSessionId(), target);
        placeQuickFluid(target, origin, fluid, session.getSessionId());

        if (water) {
            queueWaterBucketRefill(player);
        }

        Messages.debug(player, "FLUID", "bucket " + fluid + " at " + at(origin)
                + " | target was " + describe(target)
                + " | flow-distance=" + itemsConfig.getFluidFlowDistance()
                + " despawn=" + itemsConfig.getFluidDespawnSeconds() + "s");

        // Bucket-empty fires before the block actually updates, so wait a tick to confirm the
        // source really landed before putting a countdown label over it.
        SchedulerUtils.runTask(() -> {
            if (target.getType() != fluid) {
                Messages.debug(player, "FLUID", "source +1t " + at(origin) + " = " + describe(target)
                        + "  <-- SOURCE NEVER PLACED / ALREADY GONE");
                return;
            }
            showDespawnTimer(target, fluid, itemsConfig.getFluidDespawnSeconds() * 20L, water ? "aqua" : "gold");
        });
    }

    /**
     * Draining the block a placement's source lives at should behave like vanilla: the whole
     * connected body starts draining with it, right away - not linger frozen in place until its
     * despawn timer (started back at placement time) eventually gets around to it. See
     * {@link #drainPlacement} for how that drain is staggered rather than instant. Draining a
     * non-source spread block instead just removes that one block; the source is still feeding
     * the rest of the pool, same as vanilla leaves the rest of a body alone when you scoop from
     * its edge.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block target = event.getBlockClicked();
        Location loc = target.getLocation().toBlockLocation();

        PlacedFluid placement = fluidCells.get(loc);
        if (placement == null) {
            Messages.debug(event.getPlayer(), "FLUID", "drained untracked " + describe(target) + " at " + at(loc)
                    + " - left entirely to vanilla");
            return;
        }

        if (loc.equals(placement.origin)) {
            drainPlacement(placement, "source scooped by " + event.getPlayer().getName());
            return;
        }

        fluidCells.remove(loc);
        fluidReplacedBlocks.remove(loc);

        Messages.debug(event.getPlayer(), "FLUID", "scooped tracked " + describe(target) + " at " + at(loc)
                + " (origin=" + at(placement.origin) + ", was a spread block) - untracked, no neighbors touched");
    }

    /**
     * Drains an entire placement - source and every revealed/still-pending ring. Marking it
     * inactive and untracking the source immediately is what stops {@link #revealRing} from
     * placing any ring that hadn't gone out yet, so a placement drained before it even finished
     * spreading (or before it started at all) yields no further flow, matching how vanilla
     * behaves when a source is scooped the instant it's placed. Already-revealed rings stay
     * tracked (still protected from vanilla reprocessing) right up until the tick they're
     * actually restored: removal is staggered source-first, then outward ring by ring, at the
     * same pace the fluid originally spread out - matching how vanilla recession actually
     * cascades (the ring touching the now-missing source is the first to lose its supply and
     * recede, which is what leaves the next ring out unsupplied a tick later, and so on outward)
     * rather than the whole body vanishing in one instant frame.
     */
    private void drainPlacement(PlacedFluid placement, String reason) {
        if (!placement.active) return;
        placement.active = false;
        fluidCells.remove(placement.origin);

        Messages.debug("FLUID", "draining placement at " + at(placement.origin) + " (" + reason + ")");

        long tickInterval = flowTicksFor(placement.fluid);
        long delay = 0;
        SchedulerUtils.runTaskLater(() -> removeFluidRing(List.of(placement.origin), true), delay);
        delay += tickInterval;

        for (List<Location> ring : placement.rings) {
            SchedulerUtils.runTaskLater(() -> removeFluidRing(ring, false), delay);
            delay += tickInterval;
        }
    }

    /** Restores (or untracks) every block in one ring. Only the source ring gets a sound cue. */
    private void removeFluidRing(List<Location> ring, boolean playSound) {
        for (Location loc : ring) {
            Block block = loc.getBlock();
            Material type = block.getType();
            if (type == Material.WATER || type == Material.LAVA) {
                Location center = loc.clone().add(0.5, 0.5, 0.5);
                if (type == Material.WATER) {
                    ParticleUtils.spawn(Particle.SPLASH, center, 6, 0.3, 0.2, 0.3, 0.05);
                    if (playSound) SoundUtils.playAt(center, Sound.ENTITY_GENERIC_SPLASH, 0.5f, 1.0f);
                } else {
                    ParticleUtils.spawn(Particle.LAVA, center, 4, 0.25, 0.2, 0.25, 0);
                    if (playSound) SoundUtils.playAt(center, Sound.BLOCK_LAVA_EXTINGUISH, 0.5f, 1.0f);
                }
                restoreAfterFluid(block);
            }
            fluidCells.remove(loc);
        }
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
     * Handle leaf block placement - no placement limit, just tracking for despawn/cleanup.
     */
    private boolean handleLeafPlacement(BlockPlaceEvent event, Material blockType, UUID sessionId, UUID playerId, Block block) {
        if (!isLeafBlock(blockType)) return false;

        trackPlacedBlock(sessionId, block);
        scheduleLeafDecay(block);
        showDespawnTimer(block, blockType, 160, "green");
        return true;
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

        Location origin = event.getBlock().getLocation().toBlockLocation();
        placeQuickFluid(event.getBlock(), origin, blockType, sessionId);
        showDespawnTimer(event.getBlock(), blockType, itemsConfig.getFluidDespawnSeconds() * 20L,
                blockType == Material.WATER ? "aqua" : "gold");

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
     * water/lava blocks {@link #placeQuickFluid} spreads out from it would otherwise stack a
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
     * Suppresses vanilla flow for any fluid this plugin placed. planQuickFluid already worked out
     * its full reach in every direction; letting vanilla flow on top of that is what produced the
     * terrain-dependent, sometimes-it-does-sometimes-it-doesn't spreading that the fixed reach
     * exists to replace.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onWaterLavaSpread(BlockFromToEvent event) {
        if (event.isCancelled()) return;

        Material type = event.getBlock().getType();
        if (type != Material.WATER && type != Material.LAVA) return;

        Location from = event.getBlock().getLocation().toBlockLocation();
        Location to = event.getToBlock().getLocation().toBlockLocation();
        if (fluidCells.containsKey(from)) {
            event.setCancelled(true);
            Messages.debug("FLUID", "cancelled vanilla flow " + at(from) + " -> " + at(to));
        } else {
            Messages.debug("FLUID", "untracked vanilla flow allowed " + at(from) + " -> " + at(to)
                    + " (not a tracked block - left to vanilla)");
        }
    }

    /**
     * A tracked fluid block is meant to be entirely frozen - never reprocessed by vanilla's own
     * fluid logic, only ever changed by this class's own spread/despawn/restore code. Removing a
     * neighboring block (e.g. draining a source with {@link #onBucketFill}) still fires a normal
     * physics update to its neighbors regardless of how those neighbors were themselves placed,
     * which is a separate notification path from {@link #onWaterLavaSpread}'s BlockFromToEvent
     * cancellation - cancelling it here too closes that gap instead of letting vanilla's fluid
     * tick reprocess (and reshape) the frozen ring right after a drain.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFluidPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type != Material.WATER && type != Material.LAVA) return;

        Location loc = block.getLocation().toBlockLocation();
        if (!fluidCells.containsKey(loc)) return;

        event.setCancelled(true);
        Messages.debug("FLUID", "cancelled physics update on frozen " + describe(block) + " at " + at(loc));
    }

    /** Compact "x,y,z" for the FLUID debug lines. */
    private static String at(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private static String describe(Block block) {
        String type = block.getType().toString();
        if (block.getBlockData() instanceof Levelled levelled) {
            return type + "(level=" + levelled.getLevel() + ")";
        }
        return type;
    }

    /** Why a direction was refused, for the FLUID trace. */
    private static String flowBlockedReason(Block block) {
        Material type = block.getType();
        if (type == Material.WATER || type == Material.LAVA) return "already " + type;
        if (block.getBlockData() instanceof Waterlogged) return "waterloggable";
        if (type.isSolid()) return "solid " + type;
        return "allowed";
    }

    /**
     * Records a block the fluid is about to overwrite, unless it was only air.
     */
    private static void rememberReplacedBlock(Block block) {
        if (block.getType() == Material.AIR) return;
        fluidReplacedBlocks.putIfAbsent(block.getLocation().toBlockLocation(), block.getBlockData());
    }

    /**
     * Puts back whatever the fluid displaced here, or clears to air if it displaced nothing.
     */
    private static void restoreAfterFluid(Block block) {
        Messages.debug("FLUID", "despawn " + at(block.getLocation().toBlockLocation()) + " was " + describe(block));
        BlockData original = fluidReplacedBlocks.remove(block.getLocation().toBlockLocation());
        if (original != null) {
            block.setBlockData(original, false);
        } else {
            block.setType(Material.AIR);
        }
    }

    /**
     * Whether the spread may step into a block. Anything without a collision box gives way -
     * plants, buttons, levers, torches - so a direction is never skipped just because something
     * is standing in it, which is the vanilla behaviour being replaced. Only real map geometry
     * stops the flow, and waterlogging stays refused.
     */
    private boolean canFlowInto(Block block) {
        Material type = block.getType();
        if (type == Material.WATER || type == Material.LAVA) return false;
        if (block.getBlockData() instanceof Waterlogged) return false;
        return !type.isSolid();
    }

    /**
     * Places one spread block as <em>flowing</em> fluid, at a depth matching how many steps out
     * it is. Physics stays off: vanilla would immediately recompute these into its own flow
     * pattern, which is the behaviour this whole spread exists to replace. Only the block the
     * player placed is left as a source.
     */
    private void placeFlowingFluid(Block block, Material fluid, int step) {
        rememberReplacedBlock(block);
        block.setType(fluid, false);

        if (!(block.getBlockData() instanceof Levelled levelled)) return;

        levelled.setLevel(Math.clamp(step, levelled.getMinimumLevel() + 1, levelled.getMaximumLevel()));
        block.setBlockData(levelled, false);
    }

    /** Vanilla overworld flow rates by default, tunable per fluid in items.yml. */
    private long flowTicksFor(Material fluid) {
        return fluid == Material.LAVA
                ? itemsConfig.getLavaFlowTicks()
                : itemsConfig.getWaterFlowTicks();
    }

    /**
     * Computes and starts revealing a "quick fluid" placement: the whole reachable extent is
     * planned once, synchronously, up front (see {@link #planQuickFluid}) - there's no live
     * recursion for a concurrent drain to race against, since nothing is placed until the plan is
     * already final. The source sits alone for one full flow tick before the first ring goes
     * out, same as vanilla - starting the reveal inline made the whole spread land a step ahead
     * of where real water would be. Also starts the placement's despawn timer immediately, same
     * as the source's own countdown label.
     */
    private void placeQuickFluid(Block source, Location origin, Material fluid, UUID sessionId) {
        PlacedFluid placement = new PlacedFluid(origin, fluid, sessionId, planQuickFluid(source));
        fluidCells.put(origin, placement);

        SchedulerUtils.runTaskLater(() -> revealRing(placement, 0), flowTicksFor(fluid));

        SchedulerUtils.runTaskLater(() -> drainPlacement(placement, "despawn timer"),
                itemsConfig.getFluidDespawnSeconds() * 20L);
    }

    /**
     * Works out the full extent of a placement in one synchronous pass: straight down first -
     * gravity takes priority, matching vanilla (a source flows downward before it flows to its
     * sides) - up to {@code fluid-fall-max-depth}, then breadth-first outward from wherever that
     * landed, up to {@code fluid-flow-distance} hops. This is deliberately unconditional about
     * direction, same as before: vanilla decides per-direction whether fluid advances based on
     * the surrounding terrain, and a fixed reach replaces that so a placed bucket always produces
     * the same shape whatever it's placed against. Each returned list is one ring - every cell at
     * the same hop distance from the source - in reveal order.
     */
    private List<List<Location>> planQuickFluid(Block source) {
        List<List<Location>> rings = new ArrayList<>();
        Set<Location> seen = new HashSet<>();
        seen.add(source.getLocation().toBlockLocation());

        List<Location> frontier = List.of(fallTo(source.getLocation().toBlockLocation(), seen, rings));
        for (int hop = 0; hop < itemsConfig.getFluidFlowDistance() && !frontier.isEmpty(); hop++) {
            List<Location> ring = new ArrayList<>();
            for (Location from : frontier) {
                for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                    Location next = from.clone().add(face.getModX(), face.getModY(), face.getModZ()).toBlockLocation();
                    if (!seen.add(next)) continue;
                    if (!canFlowInto(next.getBlock())) continue;
                    ring.add(next);
                }
            }
            if (ring.isEmpty()) break;
            rings.add(ring);

            List<Location> nextFrontier = new ArrayList<>(ring.size());
            for (Location cell : ring) {
                nextFrontier.add(fallTo(cell, seen, rings));
            }
            frontier = nextFrontier;
        }

        return rings;
    }

    /**
     * Falls straight down from {@code start} until solid ground or {@code fluid-fall-max-depth},
     * adding each fallen-through cell as its own single-cell ring (a falling animation, same as
     * the source's own initial fall) and to {@code seen}. Returns {@code start} unchanged if
     * there's nothing to fall through.
     */
    private Location fallTo(Location start, Set<Location> seen, List<List<Location>> rings) {
        Location current = start;
        int remaining = itemsConfig.getFluidFallMaxDepth();
        while (remaining > 0) {
            Location below = current.clone().add(0, -1, 0);
            if (seen.contains(below) || !canFlowInto(below.getBlock())) break;

            seen.add(below);
            rings.add(List.of(below));
            current = below;
            remaining--;
        }
        return current;
    }

    /**
     * Reveals one ring, then schedules the next - one ring per flow tick, matching how fluid used
     * to spread one hop at a time. Bails out, placing nothing further, the moment the placement
     * is drained: {@code active} is checked fresh on every call, so this stops immediately
     * whether the drain happened before this ring's turn even came up (including before the very
     * first ring - a placement drained the instant it's placed produces no flow at all, matching
     * vanilla) or partway through revealing.
     */
    private void revealRing(PlacedFluid placement, int index) {
        if (!placement.active || index >= placement.rings.size()) return;

        int level = index + 1;
        List<Location> ring = placement.rings.get(index);
        for (int i = 0; i < ring.size(); i++) {
            Location loc = ring.get(i);
            Block block = loc.getBlock();
            if (!canFlowInto(block)) {
                Messages.debug("FLUID", "  ring " + level + " skip " + at(loc) + ": " + flowBlockedReason(block));
                continue;
            }

            placeFlowingFluid(block, placement.fluid, level);
            fluidCells.put(loc, placement);
            trackPlacedBlock(placement.sessionId, block);
            Messages.debug("FLUID", "  ring " + level + " placed " + describe(block) + " at " + at(loc));

            cascadeDown(block, placement, level, ring);
        }

        SchedulerUtils.runTaskLater(() -> revealRing(placement, index + 1), flowTicksFor(placement.fluid));
    }

    /**
     * Any spread cell with air below it falls straight down to the next solid ground, same as
     * the source's own initial fall - so a bucket placed on a ledge doesn't leave water hanging
     * in mid-air past the edge. Appends the fallen cells to the same ring they cascaded from, so
     * they drain/despawn together with it.
     */
    private void cascadeDown(Block from, PlacedFluid placement, int level, List<Location> ring) {
        Block current = from;
        for (int step = 0; step < itemsConfig.getFluidFallMaxDepth(); step++) {
            Block below = current.getRelative(BlockFace.DOWN);
            if (!canFlowInto(below)) break;

            placeFlowingFluid(below, placement.fluid, level);
            Location belowLoc = below.getLocation().toBlockLocation();
            fluidCells.put(belowLoc, placement);
            trackPlacedBlock(placement.sessionId, below);
            ring.add(belowLoc);

            current = below;
        }
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
        }
    }

    private static void cancelLeafDecayTask(Location loc) {
        BukkitTask task = leafDecayTasks.remove(loc.toBlockLocation());
        if (task != null) {
            task.cancel();
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

