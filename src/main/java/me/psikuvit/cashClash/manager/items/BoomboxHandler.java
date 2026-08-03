package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
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

import java.util.HashSet;
import java.util.Set;

/**
 * Boombox: places a 12-second JUKEBOX that pulses 4 times (every 3s), drawing an
 * orange forming ring and granting same-team players within radius a temporary
 * speed boost each pulse (flag carriers excluded in CTF).
 */
public class BoomboxHandler extends CustomItemHandler {

    private final Set<Location> activeBoomboxes;

    public BoomboxHandler(CustomItemManager manager) {
        super(manager);
        this.activeBoomboxes = new HashSet<>();
    }

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

        int durationTicks = cfg.getBoomboxSpeedBoostDuration() * 20;
        int amplifier = speedPercentToAmplifier(cfg.getBoomboxSpeedBoostPercent());

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam == null || targetTeam.getTeamNumber() != placerTeam.getTeamNumber()) continue;
            if (session.getGamemode() instanceof CaptureTheFlagGamemode ctf && ctf.isSilenced(target.getUniqueId())) continue;

            CashClashPlayer.applyEffect(target, PotionEffectType.SPEED, durationTicks, amplifier, false, true);
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

    @Override
    public void cleanup() {
        activeBoomboxes.forEach(loc -> {
            Block block = loc.getBlock();
            if (block.getType() == Material.JUKEBOX) {
                block.setType(Material.AIR);
            }
        });
        activeBoomboxes.clear();
    }
}
