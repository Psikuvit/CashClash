package me.psikuvit.cashClash.manager;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Manages round progression and timing
 */
public class RoundManager {

    private final GameSession session;
    private BukkitTask phaseTask;
    private int timeRemaining;

    public RoundManager(GameSession session) {
        this.session = session;
    }

    public void startShoppingPhase(int roundNumber) {
        timeRemaining = ConfigManager.getInstance().getShoppingPhaseDuration();

        Messages.broadcastWithPrefix(session.getPlayers(), "<yellow><bold>Round " + roundNumber + " - Shopping Phase!</bold></yellow>");
        Messages.broadcastWithPrefix(session.getPlayers(), "<gray>You have <yellow>" + timeRemaining + " seconds </yellow><gray>to shop!</gray>");

        Arena arena = ArenaManager.getInstance().getArena(session.getArenaNumber());
        if (arena != null && session.getGameWorld() != null) {
            TemplateWorld tpl = ArenaManager.getInstance().getTemplate(arena.getTemplateId());

            Location team1ShopTpl = tpl.getTeam1ShopSpawn();
            Location team2ShopTpl = tpl.getTeam2ShopSpawn();

            for (UUID uuid : session.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;

                int teamNum = session.getTeam1().hasPlayer(uuid) ? 1 : (session.getTeam2().hasPlayer(uuid) ? 2 : 0);
                Location dest = null;

                if (teamNum == 1 && team1ShopTpl != null) dest = team1ShopTpl;
                else if (teamNum == 2 && team2ShopTpl != null) dest = team2ShopTpl;

                // fallback to spectator spawn in copied world
                if (dest == null) {
                    Messages.parse("<red>Your team's shopping area is not set. Teleporting to spectator area.</red>");
                    dest = tpl.getSpectatorSpawn();
                }

                p.teleport(dest);
                Messages.send(p, "<yellow>Teleported to your team's shopping area.</yellow>");
            }
        }

        // Start countdown
        phaseTask = Bukkit.getScheduler().runTaskTimer(CashClashPlugin.getInstance(), () -> {
            timeRemaining--;

            if (timeRemaining <= 0) {
                endShoppingPhase();
            } else if (timeRemaining <= 10 || timeRemaining % 30 == 0) {
                Messages.broadcastWithPrefix(session.getPlayers(), "<yellow>" + timeRemaining + " seconds remaining!</yellow>");
            }
        }, 0, 20L); // Run every second
    }

    public void endShoppingPhase() {
        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }

        Messages.broadcastWithPrefix(session.getPlayers(), "<red><bold>Combat Phase Starting!</bold></red>");
        session.startCombatPhase();
        startCombatPhase();
    }

    public void startCombatPhase() {
        ConfigManager config = ConfigManager.getInstance();
        timeRemaining = config.getCombatPhaseDuration();

        // Start countdown
        phaseTask = Bukkit.getScheduler().runTaskTimer(CashClashPlugin.getInstance(), () -> {
            timeRemaining--;

            if (timeRemaining <= 0) {
                endCombatPhase();
            } else if (timeRemaining <= 10 || timeRemaining % 60 == 0) {
                Messages.broadcastWithPrefix(session.getPlayers(), "<yellow>" + timeRemaining + " seconds remaining!</yellow>");
            }

            // Check win conditions
            checkWinCondition();
        }, 0, 20L);
    }

    public void endCombatPhase() {
        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }

        Messages.broadcastWithPrefix(session.getPlayers(), "<yellow>Round " + session.getCurrentRound() + " ended!</yellow>");

        // Award bonuses
        // TODO: Implement bonus calculation and awarding

        // Move to next round or end game
        if (session.getCurrentRound() >= 5) {
            session.end();
        } else {
            Bukkit.getScheduler().runTaskLater(CashClashPlugin.getInstance(), () -> {
                session.nextRound();
                startShoppingPhase(session.getCurrentRound());
            }, 100L); // 5 second delay
        }
    }

    private void checkWinCondition() {
        // Check if one team is eliminated
        int team1Alive = (int) session.getTeam1().getPlayers().stream()
            .filter(uuid -> session.getCurrentRoundData().isAlive(uuid))
            .count();

        int team2Alive = (int) session.getTeam2().getPlayers().stream()
            .filter(uuid -> session.getCurrentRoundData().isAlive(uuid))
            .count();

        if (team1Alive == 0) {
            Messages.broadcastWithPrefix(session.getPlayers(), "<red><bold>Team 2 wins the round!</bold></red>");
            endCombatPhase();
        } else if (team2Alive == 0) {
            Messages.broadcastWithPrefix(session.getPlayers(), "<red><bold>Team 1 wins the round!</bold></red>");
            endCombatPhase();
        }
    }


    public void cleanup() {
        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }
    }

    public int getTimeRemaining() {
        return timeRemaining;
    }
}
