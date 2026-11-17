package me.psikuvit.cashClash.manager.game;

import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.SpawnLine;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.Shutdownable;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.enums.TeamColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

/**
 * Draws every map's spawn lines in their team's colour, twice a second: in each running game's
 * world, and in any template world someone is in, so the lines can be checked while they're
 * being set up. {@code spawn-lines.particle-density} scales how many points each line gets.
 */
public class SpawnLineManager implements Shutdownable {

    private static final double POINTS_PER_BLOCK = 2.0;
    private static final float PARTICLE_SIZE = 1.0f;
    // Just above the block's floor, so the line lies on the ground
    private static final double HEIGHT_IN_BLOCK = 0.1;

    private final GameManager gameManager;
    private final ArenaManager arenaManager;
    private final ConfigManager configManager;
    private BukkitTask task;

    public SpawnLineManager(GameManager gameManager, ArenaManager arenaManager, ConfigManager configManager) {
        this.gameManager = gameManager;
        this.arenaManager = arenaManager;
        this.configManager = configManager;
    }

    public void start() {
        task = SchedulerUtils.runTaskTimer(this::drawAll, 20L, 10L);
    }

    private void drawAll() {
        double density = configManager.getSpawnLineParticleDensity();
        if (density <= 0) return;

        for (GameSession session : gameManager.getActiveSessions()) {
            draw(session.getArenaTemplate(), session.getGameWorld(), density);
        }
        for (TemplateWorld template : arenaManager.getAllTemplates().values()) {
            draw(template, template.getWorld(), density);
        }
    }

    private void draw(TemplateWorld template, World world, double density) {
        if (template == null || world == null || world.getPlayers().isEmpty()) return;

        for (TeamColor team : TeamColor.values()) {
            for (int line = 1; line <= 2; line++) {
                SpawnLine spawnLine = template.getSpawnLine(team, line);
                if (!spawnLine.isComplete()) continue;
                drawLine(onFloor(spawnLine.start(), world), onFloor(spawnLine.end(), world), team, density);
            }
        }
    }

    private static Location onFloor(Location block, World world) {
        return new Location(world, block.getBlockX() + 0.5, block.getBlockY() + HEIGHT_IN_BLOCK, block.getBlockZ() + 0.5);
    }

    private static void drawLine(Location from, Location to, TeamColor team, double density) {
        double distance = from.distance(to);
        int points = Math.max(1, (int) Math.ceil(distance * POINTS_PER_BLOCK * density));
        for (int i = 0; i <= points; i++) {
            double f = i / (double) points;
            Location point = from.clone().add(to.clone().subtract(from).toVector().multiply(f));
            ParticleUtils.spawnDust(point, team.getParticleColor(), PARTICLE_SIZE, 1);
        }
    }

    @Override
    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
    }
}
