package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Blooming Rose: places a cherry-blossom structure that creates a healing zone.
 * Same-team players inside are healed back to a 2-heart floor each second and take
 * reduced damage; on expiry (or manual destruction of any structure block) the tree
 * is torn down and nearby teammates get a burst of Regen I. Also runs a once-per-
 * plugin-life actionbar loop showing teammates' HP to rose holders.
 */
public class BloomingRoseHandler extends CustomItemHandler {

    // Blooming Rose - placed sakura zones keyed by trunk location
    private final Map<Location, BloomingRoseZone> bloomingRoseZones;
    private BukkitTask hpRevealTask;

    /**
     * @param session       the game session the rose was placed in (used for team lookups on expiry)
     * @param teamNumber    the team the placer belongs to - only same-team players get protection/regen
     * @param center        the trunk location (zone centre)
     * @param expiresAt     epoch millis the zone naturally expires
     * @param originalBlocks every block the structure occupies (log + leaves), mapped to what was
     *                      there before placement so it can be restored, not just cleared to air
     * @param task          the zone upkeep task (drift particles + floor heal + expiry)
     * @param ringTask      the infinite boundary-ring redraw task - has no expiry check of its
     *                      own, so it must be cancelled here rather than left to stop itself
     */
    private record BloomingRoseZone(GameSession session, int teamNumber, Location center, long expiresAt,
                                    Map<Block, BlockData> originalBlocks, BukkitTask task, BukkitTask ringTask) {}

    public BloomingRoseHandler(CustomItemManager manager) {
        super(manager);
        this.bloomingRoseZones = new HashMap<>();
    }

    public void placeBloomingRose(Player player, ItemStack item, Location loc) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        Team team = session != null ? session.getPlayerTeam(player) : null;
        if (session == null || team == null) return;

        consumeItem(player, item);

        Block origin = loc.getBlock();
        Map<Block, BlockData> originalBlocks = new HashMap<>();
        buildRoseStructure(origin, originalBlocks);

        Location center = origin.getLocation();
        long expiresAt = System.currentTimeMillis() + cfg.getBloomingRoseZoneDurationSeconds() * 1000L;
        BukkitTask upkeepTask = startRoseZoneTask(center, originalBlocks, expiresAt, session, team.getTeamNumber());
        BukkitTask ringTask = spawnRoseFormationVisual(center);
        bloomingRoseZones.put(center, new BloomingRoseZone(session, team.getTeamNumber(), center, expiresAt, originalBlocks, upkeepTask, ringTask));

        Messages.send(player, "customitem.blooming-rose-placed");
        SoundUtils.playAt(center, Sound.BLOCK_CHERRY_WOOD_PLACE, 1.0f, 1.0f);

        startBloomingRoseHpRevealLoop();
    }

    /**
     * Builds the 6-high CHERRY_LOG trunk with a small CHERRY_LEAVES canopy at the top and two
     * single-log branch offshoots, recording each block's original state before overwriting it
     * so the structure can be fully torn down (and the map restored, not left as air) on expiry
     * or manual destruction (the intended counterplay).
     */
    private void buildRoseStructure(Block origin, Map<Block, BlockData> originalBlocks) {
        World world = origin.getWorld();
        int baseX = origin.getX();
        int baseY = origin.getY() + 1;
        int baseZ = origin.getZ();

        for (int i = 0; i < 6; i++) {
            Block b = world.getBlockAt(baseX, baseY + i, baseZ);
            originalBlocks.put(b, b.getBlockData());
            b.setType(Material.CHERRY_LOG, false);
        }

        int canopyY = baseY + 6;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue; // rounded canopy
                Block b = world.getBlockAt(baseX + dx, canopyY, baseZ + dz);
                originalBlocks.put(b, b.getBlockData());
                b.setType(Material.CHERRY_LEAVES, false);
            }
        }
        Block crown = world.getBlockAt(baseX, canopyY + 1, baseZ);
        originalBlocks.put(crown, crown.getBlockData());
        crown.setType(Material.CHERRY_LEAVES, false);

        Block branchA = world.getBlockAt(baseX + 1, baseY + 3, baseZ);
        originalBlocks.put(branchA, branchA.getBlockData());
        branchA.setType(Material.CHERRY_LOG, false);
        Block branchB = world.getBlockAt(baseX - 1, baseY + 3, baseZ);
        originalBlocks.put(branchB, branchB.getBlockData());
        branchB.setType(Material.CHERRY_LOG, false);
    }

    /**
     * Zone upkeep: every second it drifts red dust off the leaves, draws a red ring at the
     * zone's healing radius, heals same-team members below the health floor back up to it,
     * and tears the structure down once it expires.
     */
    private BukkitTask startRoseZoneTask(Location center, Map<Block, BlockData> originalBlocks, long expiresAt,
                                         GameSession session, int teamNumber) {
        return SchedulerUtils.runTaskTimer(new BukkitRunnable() {

            private int tick;

            @Override
            public void run() {
                if (System.currentTimeMillis() >= expiresAt) {
                    destroyBloomingRose(center);
                    cancel();
                    return;
                }
                tick++;

                // tick increments once per call, and this task runs every 5 ticks - so "once per
                // second" is every 4 calls (4 * 5 ticks = 20 ticks = 1s), not every 20 calls
                // (which would be every 5 real seconds). With the zone's 6s total lifetime, the
                // old "% 20" only ever fired once, right before the zone expired - the leaf
                // particles and health-floor healing were effectively never visible/active.
                if (tick % 4 == 0) {
                    Color red = Color.fromRGB(220, 20, 20);
                    for (Block block : originalBlocks.keySet()) {
                        if (block.getType() == Material.CHERRY_LEAVES) {
                            ParticleUtils.spawnDust(block.getLocation().add(0.5, 0.5, 0.5),
                                    red, 0.6f, 1, 0.15);
                        }
                    }
                    healRoseMembersToFloor(center, session, teamNumber);
                }
            }
        }, 20L, 5L);
    }

    /**
     * Draws a red ring on the ground at the zone's healing radius so players can see its bounds -
     * a direct point-by-point loop (not routed through the shared ring helpers) redrawn often
     * enough that it never visibly fades between draws, with a generous size/count per point so
     * it's unmistakable even against grass/terrain.
     */
    private void spawnRoseRadiusRing(Location center, Color color) {
        double radius = cfg.getBloomingRoseZoneRadius();
        int points = 48;

        // +0.5 X/Z: center is the trunk block's corner coordinate, not its visual middle.
        ParticleUtils.circle(center.clone().add(0.5, 0, 0.5), radius, points, 1, color, 1.0f);
    }

    /**
     * Heals any same-team player inside the zone whose health has fallen below the 2-heart floor
     * back up to it (scaled through the shared healing-reduction hook).
     */
    private void healRoseMembersToFloor(Location center, GameSession session, int teamNumber) {
        double floor = cfg.getBloomingRoseMinHealthFloor();
        double radius = cfg.getBloomingRoseZoneRadius();

        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (!isSameTeam(session, teamNumber, target)) continue;
            if (target.getHealth() >= floor) continue;

            // CashClashPlayer.heal already applies the shared healing-reduction multiplier.
            CashClashPlayer.heal(target, floor - target.getHealth());
        }
    }

    /**
     * Tears down an active zone (expiry or manual destruction): restores every structure block
     * to whatever was there before the rose was placed (not just air) and grants teammates
     * inside the radius Regen I for the configured duration.
     */
    private void destroyBloomingRose(Location center) {
        BloomingRoseZone zone = bloomingRoseZones.remove(center);
        if (zone == null) return;

        if (zone.task() != null) zone.task().cancel();
        if (zone.ringTask() != null) zone.ringTask().cancel();
        for (Map.Entry<Block, BlockData> entry : zone.originalBlocks().entrySet()) {
            Block block = entry.getKey();
            if (block.getType() == Material.CHERRY_LOG || block.getType() == Material.CHERRY_LEAVES) {
                block.setBlockData(entry.getValue(), false);
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
            if (entry.getValue().originalBlocks().containsKey(block)) {
                destroyBloomingRose(entry.getKey());
                return;
            }
        }
    }

    private void triggerRoseRegen(BloomingRoseZone zone) {
        double radius = cfg.getBloomingRoseZoneRadius();
        int durationTicks = cfg.getBloomingRoseRegenDurationSeconds() * 20;

        for (Entity entity : zone.center().getWorld().getNearbyEntities(zone.center(), radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (!isSameTeam(zone.session(), zone.teamNumber(), target)) continue;
            CashClashPlayer.applyEffect(target, PotionEffectType.REGENERATION, durationTicks, 0);
            Messages.send(target, "customitem.blooming-rose-teammates-regen");
        }
    }

    /**
     * @return the active same-team zone reduction % for a player standing inside one, else 0
     */
    public double getBloomingRoseDamageReduction(Player player) {
        BloomingRoseZone zone = findRoseZone(player);
        if (zone == null) return 0.0;
        return cfg.getBloomingRoseDamageReductionPercent();
    }

    /**
     * @return the active same-team zone health floor for a player standing inside one, else -1
     */
    public double getBloomingRoseMinHealth(Player player) {
        BloomingRoseZone zone = findRoseZone(player);
        if (zone == null) return -1.0;
        return cfg.getBloomingRoseMinHealthFloor();
    }

    private BloomingRoseZone findRoseZone(Player player) {
        double radius = cfg.getBloomingRoseZoneRadius();
        for (BloomingRoseZone zone : bloomingRoseZones.values()) {
            if (zone.originalBlocks().isEmpty()) continue;
            if (zone.center().getWorld().equals(player.getWorld())
                    && zone.center().distance(player.getLocation()) <= radius
                    && isSameTeam(zone.session(), zone.teamNumber(), player)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Sakura formation visual: a red formingRing (zone radius) that draws in while two figure-eight
     * cursors converge from opposite ends around the trunk. Also starts the boundary ring's
     * continuous redraw loop, which - unlike the two animations above - never stops on its own,
     * so its task is returned for the caller to track and cancel when the zone is destroyed.
     */
    private BukkitTask spawnRoseFormationVisual(Location center) {
        double radius = cfg.getBloomingRoseZoneRadius();
        Color pink = Color.fromRGB(255, 150, 190);

        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            private int formed;

            @Override
            public void run() {
                formed += 6;
                if (formed >= 90) {
                    ParticleUtils.formingRing(center.clone().add(0, 1, 0), radius, 90, 90, pink, 0.12f);
                    cancel();
                    return;
                }
                ParticleUtils.formingRing(center.clone().add(0, 1, 0), radius, 90, formed, pink, 0.12f);
            }
        }, 0L, 1L);
        Location figureEightCenter = center.clone().add(0.5, 1, 0.5); // +0.5 X/Z: block-center, not corner
        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            // Reveal progress is time-based (tick / FIGURE_EIGHT_DURATION_TICKS) rather than a
            // fixed points-per-call increment, so the whole draw-in reliably takes 5s regardless
            // of the point count below.
            private static final int FIGURE_EIGHT_DURATION_TICKS = 100; // 5s
            private int tick;

            @Override
            public void run() {
                tick++;
                int formed = (int) Math.ceil(60.0 * tick / FIGURE_EIGHT_DURATION_TICKS);
                if (formed >= 60) {
                    ParticleUtils.figureEight(figureEightCenter, radius * 0.5, pink, 60, 60, false);
                    ParticleUtils.figureEight(figureEightCenter, radius * 0.5, pink, 60, 60, true);
                    cancel();
                    return;
                }
                ParticleUtils.figureEight(figureEightCenter, radius * 0.5, pink, 60, formed, false);
                ParticleUtils.figureEight(figureEightCenter, radius * 0.5, pink, 60, formed, true);
            }
        }, 0L, 1L);
        return SchedulerUtils.runTaskTimer(() -> spawnRoseRadiusRing(center, Color.fromRGB(220, 20, 20)), 0L, 1);
    }

    /**
     * Lazy actionbar loop, started on first rose placement and left running until {@link #cleanup()}
     * cancels it: every 10 ticks, players holding a Blooming Rose see their teammates' current HP.
     */
    private void startBloomingRoseHpRevealLoop() {
        if (hpRevealTask != null) return;

        hpRevealTask = SchedulerUtils.runTaskTimer(() -> {
            for (Player holder : Bukkit.getOnlinePlayers()) {
                if (PDCDetection.getCustomItem(holder.getInventory().getItemInMainHand()) != CustomItem.BLOOMING_ROSE) continue;
                GameSession session = GameManager.getInstance().getPlayerSession(holder);
                if (session == null) continue;
                Team team = session.getPlayerTeam(holder);
                if (team == null) continue;

                StringBuilder sb = new StringBuilder("<white>Rose HP:</white> <aqua>You</aqua> <red>❤ ")
                        .append(String.format("%.1f", holder.getHealth())).append("</red>");
                for (Player teammate : Bukkit.getOnlinePlayers()) {
                    if (teammate.equals(holder)) continue;
                    Team t = session.getPlayerTeam(teammate);
                    if (t == null || t.getTeamNumber() != team.getTeamNumber()) continue;
                    sb.append("  <aqua>").append(teammate.getName()).append("</aqua> <red>❤ ")
                            .append(String.format("%.1f", teammate.getHealth())).append("</red>");
                }
                holder.sendActionBar(Messages.parse(sb.toString()));
            }
        }, 0L, 10L);
    }

    @Override
    public void cleanup() {
        bloomingRoseZones.values().forEach(zone -> {
            if (zone.task() != null) zone.task().cancel();
            if (zone.ringTask() != null) zone.ringTask().cancel();
            for (Map.Entry<Block, BlockData> entry : zone.originalBlocks().entrySet()) {
                Block block = entry.getKey();
                if (block.getType() == Material.CHERRY_LOG || block.getType() == Material.CHERRY_LEAVES) {
                    block.setBlockData(entry.getValue(), false);
                }
            }
        });
        bloomingRoseZones.clear();

        if (hpRevealTask != null) {
            hpRevealTask.cancel();
            hpRevealTask = null;
        }
    }
}
