package me.psikuvit.cashClash.manager.game;

import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gamemode.impl.ProtectThePresidentGamemode;
import me.psikuvit.cashClash.manager.items.CustomArmorManager;
import me.psikuvit.cashClash.manager.player.BonusManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
        Messages.debug("GAME", "Starting shopping phase for round " + roundNumber + " in session " + session.getSessionId());

        // For Protect the President, run buff selection phase first (round 1 only during onGameStart, but also after each round)
        if (session.getGamemode() instanceof ProtectThePresidentGamemode) {
            startBuffSelectionPhase(roundNumber);
            return;
        }

        ConfigManager config = ConfigManager.getInstance();
        timeRemaining = config.getShoppingPhaseDuration();

        // Apply loss streak bonuses at start of shopping phase (round 2+)
        if (roundNumber > 1) {
            applyLossStreakBonuses();
        }

        Messages.broadcastWithPrefix(session.getPlayers(), "<yellow><bold>Round " + roundNumber + " - Shopping Phase!</bold></yellow>");
        Messages.broadcastWithPrefix(session.getPlayers(), "<gray>You have <yellow>" + timeRemaining + " seconds </yellow><gray>to shop!</gray>");

        Team team1 = session.getTeam1();
        Team team2 = session.getTeam2();

        Arena arena = ArenaManager.getInstance().getArena(session.getArenaNumber());
        if (arena != null && session.getGameWorld() != null) {
            TemplateWorld tpl = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
            World copiedWorld = session.getGameWorld();

            Location team1ShopTpl = tpl.getTeam1ShopSpawn();
            Location team2ShopTpl = tpl.getTeam2ShopSpawn();

            for (UUID uuid : session.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;

                // Full heal health and hunger at start of shopping phase
                AttributeInstance maxHealthAttr = p.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealthAttr != null) {
                    p.setHealth(maxHealthAttr.getValue());
                }
                p.setFoodLevel(20);
                p.setSaturation(20.0f);

                int teamNum = teamRed.hasPlayer(uuid) ? 1 : (teamBlue.hasPlayer(uuid) ? 2 : 0);
                Location destTemplate = null;

                if (teamNum == 1 && teamRedShopTpl != null) {
                    destTemplate = teamRedShopTpl;
                } else if (teamNum == 2 && teamBlueShopTpl != null) {
                    destTemplate = teamBlueShopTpl;
                }

                // fallback to spectator spawn
                if (destTemplate == null) {
                    Messages.send(p, "<red>Your team's shopping area is not set. Teleporting to spectator area.</red>");
                    destTemplate = tpl.getSpectatorSpawn();
                }

                // Adjust the location to the copied world
                if (destTemplate != null) {
                    Location dest = LocationUtils.adjustLocationToWorld(destTemplate, copiedWorld);
                    p.setGameMode(GameMode.SURVIVAL);
                    p.teleport(dest);
                    // Close inventory/shop when teleporting to shopping phase
                    p.closeInventory();
                    Messages.send(p, "<yellow>Teleported to your team's shopping area.</yellow>");
                }
            }
        }

        // Start countdown
        phaseTask = SchedulerUtils.runTaskTimer(() -> {
            if (team1.isTeamReady() && team2.isTeamReady()) {
                Messages.broadcastWithPrefix(session.getPlayers(), "<green>Both teams are ready! Ending shopping phase early.</green>");
                endShoppingPhase();
                return;
            }
            timeRemaining--;

            if (timeRemaining <= 3 && timeRemaining > 0) {
                SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            }

            if (timeRemaining <= 0) {
                endShoppingPhase();
            } else if (timeRemaining <= 10 || timeRemaining % 30 == 0) {
                Messages.broadcastWithPrefix(session.getPlayers(), "<yellow>" + timeRemaining + " seconds remaining!</yellow>");
            }
        }, 0, 20L); // Run every second
    }

    /**
     * 15-second buff selection phase for Protect the President gamemode.
     * During this phase, presidents select their buffs but players cannot buy items.
     */
    private void startBuffSelectionPhase(int roundNumber) {
        Messages.debug("GAME", "Starting buff selection phase for round " + roundNumber);

        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }

        timeRemaining = 15; // 15 seconds for buff selection

        Messages.broadcastWithPrefix(session.getPlayers(), "<yellow><bold>Round " + roundNumber + " - Buff Selection Phase!</bold></yellow>");
        Messages.broadcastWithPrefix(session.getPlayers(), "<gold>Presidents: Right-click an item to select your buff!</gold>");
        Messages.broadcastWithPrefix(session.getPlayers(), "<gray>You have <yellow>15 seconds </yellow><gray>to select!</gray>");

        Team team1 = session.getTeam1();
        Team team2 = session.getTeam2();

        Arena arena = ArenaManager.getInstance().getArena(session.getArenaNumber());
        if (arena != null && session.getGameWorld() != null) {
            TemplateWorld tpl = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
            World copiedWorld = session.getGameWorld();

            Location team1ShopTpl = tpl.getTeam1ShopSpawn();
            Location team2ShopTpl = tpl.getTeam2ShopSpawn();

            for (UUID uuid : session.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;

                int teamNum = team1.hasPlayer(uuid) ? 1 : (team2.hasPlayer(uuid) ? 2 : 0);
                Location destTemplate = null;

                if (teamNum == 1 && team1ShopTpl != null) {
                    destTemplate = team1ShopTpl;
                } else if (teamNum == 2 && team2ShopTpl != null) {
                    destTemplate = team2ShopTpl;
                }

                if (destTemplate == null) {
                    destTemplate = tpl.getSpectatorSpawn();
                }

                if (destTemplate != null) {
                    Location dest = LocationUtils.adjustLocationToWorld(destTemplate, copiedWorld);
                    p.setGameMode(GameMode.SURVIVAL);
                    p.teleport(dest);
                    p.closeInventory();
                }
            }
        }

        // Start countdown for buff selection
        phaseTask = SchedulerUtils.runTaskTimer(() -> {
            timeRemaining--;

            if (timeRemaining <= 3 && timeRemaining > 0) {
                SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            }

            if (timeRemaining <= 0) {
                endBuffSelectionPhase(roundNumber);
            } else if (timeRemaining <= 10 || timeRemaining % 5 == 0) {
                Messages.broadcastWithPrefix(session.getPlayers(), "<gold>" + timeRemaining + " seconds to select buff!</gold>");
            }
        }, 0, 20L);
    }

    private void endBuffSelectionPhase(int roundNumber) {
        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }

        Messages.broadcastWithPrefix(session.getPlayers(), "<gold>Buff selection complete! Starting shopping phase...</gold>");

        // Now start the actual shopping phase
        startShoppingPhaseActual(roundNumber);
    }

    private void startShoppingPhaseActual(int roundNumber) {
        Messages.debug("GAME", "Starting actual shopping phase for round " + roundNumber + " in session " + session.getSessionId());
        // ensure previous task is cancelled to avoid double timers
        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }
    }

    public void endShoppingPhase() {
        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }

        // Unready all players when combat phase starts
        Team team1 = session.getTeam1();
        Team team2 = session.getTeam2();
        team1.resetReadyStatus();
        team2.resetReadyStatus();

        // Close all player inventories/shops when combat starts
        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.closeInventory();
            }
        }

        Messages.broadcastWithPrefix(session.getPlayers(), "<red><bold>Combat Phase Starting!</bold></red>");
        session.startCombatPhase();
        startCombatPhase();
    }

    public void startCombatPhase() {
        Messages.debug("GAME", "Starting combat phase for round " + session.getCurrentRound() + " in session " + session.getSessionId());
        // ensure previous task is cancelled to avoid double timers
        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }

        ConfigManager config = ConfigManager.getInstance();
        timeRemaining = config.getCombatPhaseDuration();

        // Start bonus tracking for the round
        BonusManager bonusManager = session.getBonusManager();
        if (bonusManager != null) {
            bonusManager.startRound();
        }

        // Start countdown
        phaseTask = SchedulerUtils.runTaskTimer(() -> {
            timeRemaining--;

            if (timeRemaining <= 0) {
                endCombatPhase();
            } else if (timeRemaining <= 10 || timeRemaining % 60 == 0) {
                Messages.broadcastWithPrefix(session.getPlayers(), "<yellow>" + timeRemaining + " seconds remaining!</yellow>");
            }

            // Check armor effects for all players (Flamebringer fire, Tax Evasion tick)
            for (UUID uuid : session.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    CustomArmorManager.getInstance().onFlamebringerFireTick(p);
                    CustomArmorManager.getInstance().onTaxEvasionTick(p, session);
                }
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

        SoundUtils.playTo(session.getPlayers(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.2f, 1.4f);

        Messages.broadcastWithPrefix(session.getPlayers(), "<yellow>Round " + session.getCurrentRound() + " ended!</yellow>");

        BonusManager bonusManager = session.getBonusManager();
        if (bonusManager != null) {
            bonusManager.awardEndRoundBonuses();
        }

        session.resolveRoundInvestments();

        // Move to next round or end game
        if (session.getCurrentRound() >= ConfigManager.getInstance().getTotalRounds()) {
            session.end();
        } else {
            SchedulerUtils.runTaskLater(session::nextRound, 20L);
        }
    }

    private void checkWinCondition() {
        // First check gamemode-specific win conditions
        if (session.getGamemode() != null && session.getGamemode().checkGameWinner()) {
            int winnerTeam = session.getGamemode().getWinningTeam();
            if (winnerTeam > 0) {
                String winnerName = winnerTeam == 1 ? session.getTeamRed().getName() : session.getTeamBlue().getName();
                SoundUtils.playTo(session.getPlayers(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
                Messages.broadcastWithPrefix(session.getPlayers(), 
                    "<gold><bold>" + winnerName + " Team Wins the Round!</bold></gold>");
                // Update loss streaks for this round
                if (winnerTeam == 1) {
                    session.getTeamRed().resetLossStreak();
                    session.getTeamBlue().incrementLossStreak();
                } else {
                    session.getTeamBlue().resetLossStreak();
                    session.getTeamRed().incrementLossStreak();
                }
                endCombatPhase();
                return;
            }
        }

        // Check if one team is eliminated
        int teamRedAlive = (int) session.getTeamRed().getPlayers().stream()
            .filter(uuid -> session.getCurrentRoundData().isAlive(uuid))
            .count();

        int teamBlueAlive = (int) session.getTeamBlue().getPlayers().stream()
            .filter(uuid -> session.getCurrentRoundData().isAlive(uuid))
            .count();

        if (teamRedAlive == 0) {
            SoundUtils.playTo(session.getPlayers(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.2f, 1.4f);
            Messages.broadcastWithPrefix(session.getPlayers(), "<red><bold>Blue Team wins the round!</bold></red>");
            session.getTeamBlue().resetLossStreak();
            session.getTeamRed().incrementLossStreak();
            endCombatPhase();
        } else if (teamBlueAlive == 0) {
            SoundUtils.playTo(session.getPlayers(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.2f, 1.4f);
            Messages.broadcastWithPrefix(session.getPlayers(), "<red><bold>Red Team wins the round!</bold></red>");
            session.getTeamRed().resetLossStreak();
            session.getTeamBlue().incrementLossStreak();
            endCombatPhase();
        }
    }

    /**
     * Apply loss streak bonuses to team that lost.
     * Winners get no bonus.
     * Only applied at round 2+.
     */
    private void applyLossStreakBonuses() {
        ConfigManager config = ConfigManager.getInstance();
        Team losingTeam;
        
        // Determine which team lost based on their loss streak
        int team1Streak = session.getTeam1().getLossStreak();
        int team2Streak = session.getTeam2().getLossStreak();
        int teamRedStreak = session.getTeamRed().getLossStreak();
        int teamBlueStreak = session.getTeamBlue().getLossStreak();
        
        if (teamRedStreak > 0 && teamBlueStreak == 0) {
            losingTeam = session.getTeamRed();
        } else if (teamBlueStreak > 0 && teamRedStreak == 0) {
            losingTeam = session.getTeamBlue();
        } else {
            // Both teams have the same streak (shouldn't happen, but handle gracefully)
            return;
        }
        
        long bonus = 0;
        int streak = Math.max(teamRedStreak, teamBlueStreak);
        
        switch (streak) {
            case 1 -> bonus = config.getLossStreak1Bonus();
            case 2 -> bonus = config.getLossStreak2Bonus();
            case 3, 4, 5, 6, 7 -> bonus = config.getLossStreak3Bonus();
        }
        
        if (bonus > 0) {
            for (UUID uuid : losingTeam.getPlayers()) {
                CashClashPlayer player = session.getCashClashPlayer(uuid);
                if (player != null) {
                    player.addCoins(bonus);
                    Player bukkit = Bukkit.getPlayer(uuid);
                    if (bukkit != null && bukkit.isOnline()) {
                        Messages.send(bukkit, "<gold>Loss Streak Bonus: +$" + String.format("%,d", bonus) + "</gold>");
                    }
                }
            }
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

    /**
     * Refill all water buckets in a player's inventory during shopping phase.
     */
    private void refillWaterBuckets(Player player) {
        int refilled = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.WATER_BUCKET && item.getAmount() == 0) {
                item.setAmount(1);
                refilled++;
            }
        }

        // Also check armor contents
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && item.getType() == Material.WATER_BUCKET && item.getAmount() == 0) {
                item.setAmount(1);
                refilled++;
            }
        }

        if (refilled > 0) {
            Messages.send(player, "<aqua>Water buckets refilled for shopping phase!</aqua>");
        }
    }
}
