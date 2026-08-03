package me.psikuvit.cashClash.manager.items;

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
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
        Set<Block> blocks = new HashSet<>();
        buildRoseStructure(origin, blocks);

        Location center = origin.getLocation();
        long expiresAt = System.currentTimeMillis() + cfg.getBloomingRoseZoneDurationSeconds() * 1000L;
        BukkitTask upkeepTask = startRoseZoneTask(center, blocks, expiresAt, session, team.getTeamNumber());
        bloomingRoseZones.put(center, new BloomingRoseZone(session, team.getTeamNumber(), center, expiresAt, blocks, upkeepTask));

        Messages.send(player, "customitem.blooming-rose-placed");
        SoundUtils.playAt(center, Sound.BLOCK_CHERRY_WOOD_PLACE, 1.0f, 1.0f);

        spawnRoseFormationVisual(center);
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
        return SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() >= expiresAt) {
                    destroyBloomingRose(center);
                    cancel();
                    return;
                }
                for (Block block : blocks) {
                    if (block.getType() == Material.CHERRY_LEAVES) {
                        ParticleUtils.spawnDust(block.getLocation().add(0.5, 0.5, 0.5),
                                Color.fromRGB(255, 150, 190), 0.6f, 1, 0.15);
                    }
                }
                healRoseMembersToFloor(center, session, teamNumber);
            }
        }, 20L, 20L);
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

            double heal = (floor - target.getHealth()) * manager.getHealingMultiplier(target.getUniqueId());
            CashClashPlayer.heal(target, heal);
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
            if (zone.blocks().isEmpty()) continue;
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
     * cursors converge from opposite ends around the trunk.
     */
    private void spawnRoseFormationVisual(Location center) {
        double radius = cfg.getBloomingRoseZoneRadius();
        Color pink = Color.fromRGB(255, 150, 190);

        final int[] formed = {0};
        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            @Override
            public void run() {
                formed[0] += 6;
                if (formed[0] >= 90) {
                    ParticleUtils.formingRing(center.clone().add(0, 0.5, 0), radius, 90, 90, pink, 0.12f);
                    cancel();
                    return;
                }
                ParticleUtils.formingRing(center.clone().add(0, 0.5, 0), radius, 90, formed[0], pink, 0.12f);
            }
        }, 0L, 1L);
        final int[] fig = {0};
        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            @Override
            public void run() {
                fig[0] += 4;
                if (fig[0] >= 60) {
                    ParticleUtils.figureEight(center.clone().add(0, 0.5, 0), radius * 0.4, pink, 60, 60, false);
                    ParticleUtils.figureEight(center.clone().add(0, 0.5, 0), radius * 0.4, pink, 60, 60, true);
                    cancel();
                    return;
                }
                ParticleUtils.figureEight(center.clone().add(0, 0.5, 0), radius * 0.4, pink, 60, fig[0], false);
                ParticleUtils.figureEight(center.clone().add(0, 0.5, 0), radius * 0.4, pink, 60, fig[0], true);
            }
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

    @Override
    public void cleanup() {
        bloomingRoseZones.values().forEach(zone -> {
            if (zone.task() != null) zone.task().cancel();
            for (Block block : zone.blocks()) {
                if (block.getType() == Material.CHERRY_LOG || block.getType() == Material.CHERRY_LEAVES) {
                    block.setType(Material.AIR, false);
                }
            }
        });
        bloomingRoseZones.clear();
    }
}
