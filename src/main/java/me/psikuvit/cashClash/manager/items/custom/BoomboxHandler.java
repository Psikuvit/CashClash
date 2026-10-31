package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.effects.TeamColorUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Boombox: places a 12-second JUKEBOX that pulses 4 times (every 3s), drawing an
 * orange forming ring and granting same-team players within radius a temporary
 * speed boost each pulse (flag carriers excluded in CTF).
 */
public class BoomboxHandler extends CustomItemHandler {

    // The ring floating over a placed boombox, in the placer's team colour
    private static final double FLOATING_RING_HEIGHT = 1.5;
    private static final double FLOATING_RING_RADIUS = 0.6;
    private static final int FLOATING_RING_POINTS = 20;
    private static final Color OUTSIDE_GAME_RING_COLOR = Color.fromRGB(255, 140, 0);

    private final Set<Location> activeBoomboxes;
    private final Map<Location, BukkitTask> floatingRingTasks;

    public BoomboxHandler(CustomItemManager manager) {
        super(manager);
        this.activeBoomboxes = new HashSet<>();
        this.floatingRingTasks = new HashMap<>();
    }

    public void placeBoombox(Player player, ItemStack item, Block clickedBlock) {
        Location placeLoc = clickedBlock.getRelative(BlockFace.UP).getLocation();
        Block placeBlock = placeLoc.getBlock();

        GameSession boomboxSession = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        if (boomboxSession != null && (boomboxSession.getState() == GameState.SHOPPING || boomboxSession.isActionsRestricted())) {
            Messages.sendPhaseRestriction(player, boomboxSession, "customitem.cannot-place-during-shopping");
            return;
        }

        if (placeBlock.getType() != Material.AIR) {
            Messages.send(player, "customitem.cannot-place-boombox");
            return;
        }

        placeBlock.setType(Material.JUKEBOX);
        activeBoomboxes.add(placeBlock.getLocation());
        startFloatingRing(placeBlock.getLocation(), TeamColorUtils.particleColor(player, OUTSIDE_GAME_RING_COLOR));

        consumeItem(player, item);
        Messages.send(player, "mythic.boombox-placed");
        SoundUtils.play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

        final Location boomLoc = placeBlock.getLocation().clone().add(0.5, 0.5, 0.5);
        double radius = cfg.getBoomboxRadius();

        // Pulse every 3 seconds (0, 3, 6, 9 seconds = 4 pulses total)
        for (int i = 0; i < 4; i++) {
            int delay = i * 3 * 20; // 3 seconds between pulses
            SchedulerUtils.runTaskLater(() -> {
                if (!activeBoomboxes.contains(placeBlock.getLocation())) return;

                World world = boomLoc.getWorld();
                if (world == null) return;

                SoundUtils.playAt(boomLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 2.0f, 0.5f);
                spawnBoomboxRing(boomLoc, radius);
                applyBoomboxSpeedBoost(player, boomLoc, world, radius);
            }, delay);
        }

        SchedulerUtils.runTaskLater(() -> {
            activeBoomboxes.remove(placeBlock.getLocation());
            stopFloatingRing(placeBlock.getLocation());
            if (placeBlock.getType() == Material.JUKEBOX) {
                placeBlock.setType(Material.AIR);
            }
        }, 12 * 20L);

    }

    /**
     * Keeps a ring floating over the boombox for as long as it stands, so both teams can tell
     * whose it is.
     */
    private void startFloatingRing(Location blockLoc, Color color) {
        Location ringCenter = blockLoc.clone().add(0.5, FLOATING_RING_HEIGHT, 0.5);
        floatingRingTasks.put(blockLoc, SchedulerUtils.runTaskTimer(() ->
                ParticleUtils.circle(ringCenter, FLOATING_RING_RADIUS, FLOATING_RING_POINTS, 0, color, 1.0f), 0L, 2L));
    }

    private void stopFloatingRing(Location blockLoc) {
        BukkitTask task = floatingRingTasks.remove(blockLoc);
        if (task != null) task.cancel();
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
            int delay = Math.round(i * (float) formTicks / ringPoints);
            SchedulerUtils.runTaskLater(() ->
                    ParticleUtils.formingRing(center, radius, ringPoints, formed, Color.fromRGB(255, 140, 0), 1.4f), delay);
        }
    }

    /**
     * Grants allies within radius (including the placer, excluding the flag carrier) a
     * temporary speed boost. Outside a game the placer is the only ally.
     */
    private void applyBoomboxSpeedBoost(Player placer, Location center, World world, double radius) {
        int durationTicks = cfg.getBoomboxSpeedBoostDuration() * 20;
        int amplifier = cfg.getBoomboxSpeedAmplifier();

        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(placer);
        if (session == null) {
            if (placer.isOnline() && placer.getWorld().equals(world) && placer.getLocation().distance(center) <= radius) {
                CashClashPlayer.applyEffect(placer, PotionEffectType.SPEED, durationTicks, amplifier, false, true);
            }
            return;
        }

        Team placerTeam = session.getPlayerTeam(placer);
        if (placerTeam == null) return;

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam == null || targetTeam.getTeamNumber() != placerTeam.getTeamNumber()) continue;
            if (session.getGamemode() instanceof CaptureTheFlagGamemode ctf && ctf.isSilenced(target.getUniqueId())) continue;

            CashClashPlayer.applyEffect(target, PotionEffectType.SPEED, durationTicks, amplifier, false, true);
        }
    }

    public boolean isBoombox(Block block) {
        if (block.getType() != Material.JUKEBOX) return false;
        return activeBoomboxes.contains(block.getLocation());
    }

    @Override
    public void cleanup() {
        floatingRingTasks.values().forEach(BukkitTask::cancel);
        floatingRingTasks.clear();
        activeBoomboxes.forEach(loc -> {
            Block block = loc.getBlock();
            if (block.getType() == Material.JUKEBOX) {
                block.setType(Material.AIR);
            }
        });
        activeBoomboxes.clear();
    }
}
