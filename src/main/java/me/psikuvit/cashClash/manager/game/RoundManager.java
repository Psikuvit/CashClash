package me.psikuvit.cashClash.manager.game;

import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
import me.psikuvit.cashClash.gamemode.impl.ProtectThePresidentGamemode;
import me.psikuvit.cashClash.listener.BlockListener;
import me.psikuvit.cashClash.manager.items.CustomArmorManager;
import me.psikuvit.cashClash.manager.items.CustomItemManager;
import me.psikuvit.cashClash.manager.player.BonusManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.effects.TeamColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

import java.util.UUID;

/**
 * Manages round progression and timing
 */
public class RoundManager {

    private static final int SUDDEN_DEATH_PHASE_SECONDS = 180; // 3 minutes

    private final GameSession session;
    private BukkitTask phaseTask;
    private int timeRemaining;

    public RoundManager(GameSession session) {
        this.session = session;
    }

    public void startShoppingPhase(int roundNumber) {
        Messages.debug("GAME", "Starting shopping phase for round " + roundNumber + " in session " + session.getSessionId());
        prepareSuddenDeathRoundIfNeeded();

        // For Protect the President, run buff selection phase first
        if (session.getGamemode() instanceof ProtectThePresidentGamemode ptp) {
            // Trigger buff selection in the gamemode
            ptp.startRoundBuffSelection();
            startPhase(roundNumber, GameState.BUFF_SELECTION);
            return;
        }

        // Run shopping phase directly
        startPhase(roundNumber, GameState.SHOPPING);
    }

    private void prepareSuddenDeathRoundIfNeeded() {
        if (session.getGamemode() == null) {
            return;
        }

        if (session.getRoundWins(1) == 3 && session.getRoundWins(2) == 3) {
            session.getGamemode().prepareSuddenDeathRound();
        }
    }

    /**
     * Common phase startup logic.
     * Handles setup for both buff selection and shopping phases.
     * Teleports players, sets up the environment, and starts the appropriate timer.
     */
    private void startPhase(int roundNumber, GameState phaseType) {
        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }

        // Set duration based on phase type
        if (phaseType == GameState.BUFF_SELECTION) {
            timeRemaining = 15; // 15 seconds for buff selection
        } else {
            ConfigManager config = ConfigManager.getInstance();
            timeRemaining = config.getShoppingPhaseDuration();
        }

        // Broadcast phase messages
        if (phaseType == GameState.BUFF_SELECTION) {
            Messages.broadcast(session.getPlayers(), "round.buff-selection-title",
                "round", String.valueOf(roundNumber));
            Messages.broadcast(session.getPlayers(), "round.buff-selection-prompt");
            Messages.broadcast(session.getPlayers(), "round.buff-selection-time");
        } else {
            // Apply loss streak bonuses at start of shopping phase (round 2+)
            if (roundNumber > 1) {
                applyLossStreakBonuses();
            }
            Messages.broadcast(session.getPlayers(), "round.shopping-phase-title",
                "round", String.valueOf(roundNumber));
            Messages.broadcast(session.getPlayers(), "round.shopping-phase-time",
                "time_remaining", String.valueOf(timeRemaining));
        }

        // Teleport all players to their team's shop area
        Team teamRed = session.getTeamRed();
        Team teamBlue = session.getTeamBlue();

        Arena arena = ArenaManager.getInstance().getArena(session.getArenaNumber());
        if (arena != null && session.getGameWorld() != null) {
            TemplateWorld tpl = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
            World copiedWorld = session.getGameWorld();

            Location teamRedShopTpl = tpl.getTeamRedShopSpawn();
            Location teamBlueShopTpl = tpl.getTeamBlueShopSpawn();

            for (UUID uuid : session.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;

                // For shopping phase: heal and refill
                if (phaseType == GameState.SHOPPING) {
                    AttributeInstance maxHealthAttr = p.getAttribute(Attribute.MAX_HEALTH);
                    if (maxHealthAttr != null) {
                        p.setHealth(maxHealthAttr.getValue());
                    }
                    p.setFoodLevel(20);
                    p.setSaturation(20.0f);
                    refillWaterBuckets(p);
                }

                int teamNum = teamRed.hasPlayer(uuid) ? 1 : (teamBlue.hasPlayer(uuid) ? 2 : 0);
                Location destTemplate = null;

                if (teamNum == 1 && teamRedShopTpl != null) {
                    destTemplate = teamRedShopTpl;
                } else if (teamNum == 2 && teamBlueShopTpl != null) {
                    destTemplate = teamBlueShopTpl;
                }

                if (destTemplate == null) {
                    Messages.send(p, "round.shopping-area-missing");
                    destTemplate = tpl.getSpectatorSpawn();
                }

                if (destTemplate != null) {
                    Location dest = LocationUtils.copyToWorld(destTemplate, copiedWorld);
                    p.setGameMode(GameMode.SURVIVAL);
                    p.teleport(dest);
                    p.closeInventory();
                    if (phaseType == GameState.SHOPPING) {
                        Messages.send(p, "round.shopping-area-teleported");
                    }
                }
            }
        }

        // Start the appropriate phase timer
        if (phaseType == GameState.BUFF_SELECTION) {
            startBuffSelectionTimer(roundNumber);
        } else {
            // Disable all invisibility cloaks when shopping phase starts
            CustomItemManager.getInstance().disableAllInvisibilityCloaks();

            // Notify gamemode when shopping phase starts (for cleanup, banner removal, etc.)
            if (session.getGamemode() instanceof CaptureTheFlagGamemode ctf) {
                ctf.onShoppingPhaseStart();
            }
            startShoppingTimer(teamRed, teamBlue);
        }
    }

    /**
     * Timer logic for buff selection phase
     */
    private void startBuffSelectionTimer(int roundNumber) {
        phaseTask = SchedulerUtils.runTaskTimer(() -> {
            timeRemaining--;

            if (timeRemaining <= 3 && timeRemaining > 0) {
                SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            }

            if (timeRemaining <= 0) {
                endBuffSelectionPhase(roundNumber);
            } else if (timeRemaining <= 10 || timeRemaining % 5 == 0) {
                Messages.broadcast(session.getPlayers(), "round.buff-selection-countdown",
                    "time_remaining", String.valueOf(timeRemaining));
            }
        }, 0, 20L);
    }

    /**
     * Timer logic for shopping phase
     */
    private void startShoppingTimer(Team teamRed, Team teamBlue) {
        phaseTask = SchedulerUtils.runTaskTimer(() -> {
            if (teamRed.isTeamReady() && teamBlue.isTeamReady()) {
                Messages.broadcast(session.getPlayers(), "round.both-teams-ready");
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
                Messages.broadcast(session.getPlayers(), "round.shopping-countdown",
                    "time_remaining", String.valueOf(timeRemaining));
            }
        }, 0, 20L);
    }

    /**
     * Called when buff selection phase ends.
     * Transitions to shopping phase.
     */
    private void endBuffSelectionPhase(int roundNumber) {
        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }

        Messages.broadcast(session.getPlayers(), "round.buff-selection-complete");

        // Start the shopping phase
        startPhase(roundNumber, GameState.SHOPPING);
    }


    public void endShoppingPhase() {
        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }

        // Unready all players when combat phase starts
        Team teamRed = session.getTeamRed();
        Team teamBlue = session.getTeamBlue();
        teamRed.resetReadyStatus();
        teamBlue.resetReadyStatus();

        // Close all player inventories/shops when combat starts
        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.closeInventory();
            }
        }

        Messages.broadcast(session.getPlayers(), "round.combat-phase-starting");
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
        boolean suddenDeathRound = session.getGamemode() != null
                && session.getGamemode().getSuddenDeathManager().isInSuddenDeath();
        timeRemaining = suddenDeathRound ? SUDDEN_DEATH_PHASE_SECONDS : config.getCombatPhaseDuration();

        // Start bonus tracking for the round
        BonusManager bonusManager = session.getBonusManager();
        if (bonusManager != null) {
            bonusManager.startRound();
        }

        // Apply team outlines to all players (Feature #7-8)
        applyTeamOutlinesToAllPlayers();

        // Start countdown
        phaseTask = SchedulerUtils.runTaskTimer(() -> {
            boolean finalStandActive = session.getGamemode() != null && session.getGamemode().isFinalStandActive();
            if (!finalStandActive) {
                timeRemaining--;
            }

            if (!finalStandActive && timeRemaining <= 0) {
                if (suddenDeathRound) {
                    timeRemaining = SUDDEN_DEATH_PHASE_SECONDS;
                    Messages.debug("[RoundManager] Sudden death cycle expired; restarting combat timer for the next cycle");
                } else {
                    if (startFinalStandDueToTimer()) {
                        return;
                    }
                    endCombatPhase();
                }
            } else if (!finalStandActive && (timeRemaining <= 10 || timeRemaining % 60 == 0)) {
                Messages.broadcast(session.getPlayers(), "round.combat-countdown",
                    "time_remaining", String.valueOf(timeRemaining));
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

        Messages.broadcast(session.getPlayers(), "round.round-ended",
            "round", String.valueOf(session.getCurrentRound()));

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
                Messages.broadcast(session.getPlayers(), "round.team-wins-round",
                        "team_name", winnerName);
                // Track round wins for this winner
                session.incrementRoundWins(winnerTeam);
                // Update loss streaks for this round
                if (winnerTeam == 1) {
                    session.getTeamRed().resetLossStreak();
                    session.getTeamBlue().incrementLossStreak();
                } else {
                    session.getTeamBlue().resetLossStreak();
                    session.getTeamRed().incrementLossStreak();
                }

                // Check if game should end early based on dynamic win condition
                if (checkGameEndCondition(winnerTeam)) {
                    return; // Game ends immediately
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
            Messages.broadcast(session.getPlayers(), "round.team-blue-wins");
            session.incrementRoundWins(2);
            session.getTeamBlue().resetLossStreak();
            session.getTeamRed().incrementLossStreak();

            // Check if game should end early based on dynamic win condition
            if (checkGameEndCondition(2)) {
                return; // Game ends immediately
            }

            endCombatPhase();
        } else if (teamBlueAlive == 0) {
            SoundUtils.playTo(session.getPlayers(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.2f, 1.4f);
            Messages.broadcast(session.getPlayers(), "round.team-red-wins");
            session.incrementRoundWins(1);
            session.getTeamRed().resetLossStreak();
            session.getTeamBlue().incrementLossStreak();

            // Check if game should end early based on dynamic win condition
            if (checkGameEndCondition(1)) {
                return; // Game ends immediately
            }

            endCombatPhase();
        }
    }

    /**
     * Check if the game should end early based on dynamic win condition
     * If the winning team needs fewer additional wins than rounds remaining, they've mathematically won
     * Returns true if game ends immediately, false if it continues
     */
    private boolean checkGameEndCondition(int winnerTeam) {
        int team1Wins = session.getRoundWins(1);
        int team2Wins = session.getRoundWins(2);
        int winnerWins = winnerTeam == 1 ? team1Wins : team2Wins;
        int loserWins = winnerTeam == 1 ? team2Wins : team1Wins;

        // Total rounds in the game
        int totalRounds = ConfigManager.getInstance().getTotalRounds();
        int roundsPlayed = team1Wins + team2Wins;
        int roundsRemaining = totalRounds - roundsPlayed;

        // Wins needed for loser to tie
        int winsNeededToTie = winnerWins - loserWins;

        // If loser needs more wins to tie than rounds remaining, winner has won the game
        if (winsNeededToTie > roundsRemaining) {
            String winnerName = winnerTeam == 1 ? session.getTeamRed().getName() : session.getTeamBlue().getName();
            Messages.broadcast(session.getPlayers(), "round.team-wins-match",
                    "team_name", winnerName,
                    "score", winnerWins + "-" + loserWins);
            Messages.debug("GAME", "Game ended early: " + winnerName + " wins " + winnerWins + "-" + loserWins +
                    " (" + winsNeededToTie + " wins needed to tie, only " + roundsRemaining + " rounds remain)");
            session.end();
            return true;
        }

        return false;
    }

    private boolean startFinalStandDueToTimer() {
        if (session.getGamemode() == null) {
            return false;
        }

        var fsm = session.getGamemode().getFinalStandManager();
        // Do not start Final Stand while Sudden Death is active
        if (session.getGamemode().getSuddenDeathManager() != null && session.getGamemode().getSuddenDeathManager().isInSuddenDeath()) {
            Messages.debug("[RoundManager] Final-stand suppressed because session is in sudden death");
            return false;
        }
        if (fsm == null || fsm.isActive() || session.getGamemode().getWinningTeam() != 0) {
            return false;
        }

        Messages.debug("[RoundManager] Starting final-stand for session due to round timer ending with no winner");
        fsm.startUntilWin();
        return true;
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
                        Messages.send(bukkit, "round.loss-streak-bonus", "bonus", String.format("%,d", bonus));
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
           BlockListener.refillWaterBuckets(player);
     }

     /**
      * Apply team outlines (glowing effect) to all players when combat starts (Feature #7-8).
      * Teammates get a GREEN glowing effect visible through walls.
      * Enemies have NO outline.
      */
     private void applyTeamOutlinesToAllPlayers() {
         Team teamRed = session.getTeamRed();
         Team teamBlue = session.getTeamBlue();

         // Apply glowing to Red team members' teammates
         for (UUID uuid : teamRed.getPlayers()) {
             applyGlowingToTeammates(uuid, teamRed);
         }

         // Apply glowing to Blue team members' teammates
         for (UUID uuid : teamBlue.getPlayers()) {
             applyGlowingToTeammates(uuid, teamBlue);
         }
     }

     /**
      * Apply glowing effect to a player's teammates (GREEN outline, visible through walls)
      */
     private void applyGlowingToTeammates(UUID playerUUID, Team playerTeam) {
         Player player = Bukkit.getPlayer(playerUUID);
         if (player == null || !player.isOnline()) return;

         Scoreboard scoreboard = player.getScoreboard();

         // Clear any existing glow state first so enemy outlines do not linger.
         for (UUID sessionPlayerId : session.getPlayers()) {
             Player sessionPlayer = Bukkit.getPlayer(sessionPlayerId);
             if (sessionPlayer == null || !sessionPlayer.isOnline()) continue;

             TeamColorUtils.removeFromGreenGlowTeam(scoreboard, sessionPlayer);
             sessionPlayer.removePotionEffect(PotionEffectType.GLOWING);
         }

         // Apply glowing to all teammates (but not the player themselves)
         for (UUID teamMateUUID : playerTeam.getPlayers()) {
             if (teamMateUUID.equals(playerUUID)) continue;

             Player teamMate = Bukkit.getPlayer(teamMateUUID);
             if (teamMate != null && teamMate.isOnline()) {
                 // Add teammate to green glow team (scoreboard team controls outline color)
                 TeamColorUtils.addToGreenGlowTeam(scoreboard, teamMate);

                 PotionEffect glowing = new PotionEffect(
                     PotionEffectType.GLOWING,
                     PotionEffect.INFINITE_DURATION,
                     0,
                     false,
                     false
                 );
                 teamMate.addPotionEffect(glowing);
             }
         }
     }
}
