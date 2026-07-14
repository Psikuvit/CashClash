package me.psikuvit.cashClash.gamemode.impl;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.player.ScoreboardManager;
import me.psikuvit.cashClash.gamemode.FinalStandManager;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.gamemode.SuddenDeathManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.game.ptp.PTPFinalStandUtils;
import me.psikuvit.cashClash.util.game.ptp.PTPInventoryUtils;
import me.psikuvit.cashClash.util.game.ptp.PresidentialEffectsUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Protect the President Gamemode
 * Goal: Best of 7 series - first to 2 assassinations wins each round
 */
public class ProtectThePresidentGamemode extends Gamemode {

    private static final int SELECTION_TIME = 15;
    private static final int KILL_BONUS_THRESHOLD = 2;
    private static final long KILL_BONUS_AMOUNT = 15000;
    private static final long HEART_DURATION_MS = 45 * 1000;
    private static final int WIN_CONDITION = 2;

    private final Map<Integer, President> presidents;
    private final Map<Integer, Integer> teamKillCount;
    private final Map<Integer, Integer> suddenDeathPresidentKills;
    private final Map<UUID, List<PresidentialBuff>> selectedBuffs;
    private final Map<UUID, ItemStack[]> savedInventories;
    private final Map<Integer, BukkitTask> glowingTasks;

    private final SuddenDeathManager suddenDeathManager;
    private final FinalStandManager finalStandManager;
    private boolean selectionPhaseActive;
    private boolean buffSelectionFinalized;
    private BukkitTask selectionTask;
    private int selectionTimeRemaining;
    private int suddenDeathWinningTeam;
    private PTPFinalStandUtils.BorderSnapshot preFinalStandBorderSnapshot;
    // Track the most recent team that received an extra-heart bonus and when it was awarded
    private int recentHeartBonusTeam;
    private long recentHeartBonusAwardMs;

    public ProtectThePresidentGamemode(GameSession session) {
        super(session, GamemodeType.PROTECT_THE_PRESIDENT);

        // Initialize all data structures
        this.presidents = new HashMap<>(2);
        this.teamKillCount = new HashMap<>(2);
        this.suddenDeathPresidentKills = new HashMap<>(2);
        this.selectedBuffs = new HashMap<>();
        this.savedInventories = new HashMap<>();
        this.glowingTasks = new HashMap<>(2);
        this.suddenDeathManager = new SuddenDeathManager(session, this);
        this.finalStandManager = new FinalStandManager(session, this);
        this.selectionPhaseActive = false;
        this.buffSelectionFinalized = false;
        this.selectionTask = null;
        this.selectionTimeRemaining = SELECTION_TIME;
        this.suddenDeathWinningTeam = 0;
        this.preFinalStandBorderSnapshot = null;
        this.recentHeartBonusTeam = 0;
        this.recentHeartBonusAwardMs = 0L;

        // Pre-populate team kill count
        teamKillCount.put(1, 0);
        teamKillCount.put(2, 0);
        suddenDeathPresidentKills.put(1, 0);
        suddenDeathPresidentKills.put(2, 0);
    }

    @Override
    public void onGameStart() {
        // Select a random president for each team - the reveal itself happens via the
        // Protect the President sequence at the start of every buy phase, round 1 included.
        selectPresidents();

        Messages.broadcast(session.getPlayers(), "gamemode-ptp.game-started");
    }

    @Override
    public void onCombatPhaseStart() {
        Messages.debug("[PTP] Combat phase started");
        if (suddenDeathManager.isInSuddenDeath()) {
            // Reset sudden death kill counters for this cycle
            suddenDeathPresidentKills.put(1, 0);
            suddenDeathPresidentKills.put(2, 0);
            Messages.debug("[PTP] Sudden death cycle started - kill counters reset");
        }

        // Apply glow and buffs to presidents now that combat has started
        for (int team = 1; team <= 2; team++) {
            President pres = presidents.get(team);
            if (pres != null && pres.hasSelectedBuff()) {
                refreshPresidentEffects(pres, true);
            }
        }

        // Reset kill count for round
        teamKillCount.put(1, 0);
        teamKillCount.put(2, 0);

        // Schedule president glowing after 15s
        for (int team = 1; team <= 2; team++) {
            Player presPlayer = getPresidentPlayerByTeam(team);
            if (presPlayer != null) {
                schedulePresidentGlow(team, presPlayer);
            }
        }
    }

    @Override
    public void onRoundEnd() {
        // Clear all buffs and reset for next round
        clearPresidentialBuffs();

        // Cancel all glowing tasks
        glowingTasks.values().forEach(this::cancelTask);
        glowingTasks.clear();

        // Reset deaths for next round
        presidents.replaceAll((team, pres) -> pres.withResetDeaths());
        suddenDeathWinningTeam = 0;
        finalStandManager.cancel();
        resetFinalStandBorder();
        // Reset recent heart bonus tracking
        recentHeartBonusTeam = 0;
        recentHeartBonusAwardMs = 0L;
    }

    /**
     * Called at the start of each round's buy phase, before the president-reveal
     * sequence plays. Selects new presidents (except round 1, already selected in
     * {@link #onGameStart()}) and resets their deaths - selection only, no UI/phase
     * start, so the sequence can reveal a name that's already settled.
     */
    public void selectPresidentsForRound() {
        Messages.debug("[PTP] Selecting presidents for round: " + session.getCurrentRound());

        selectedBuffs.clear();

        // Select new presidents for this round (except round 1, presidents already selected)
        if (session.getCurrentRound() > 1) {
            selectPresidents();
        }

        // Reset deaths for this round
        presidents.replaceAll((team, pres) -> pres.withResetDeaths());
    }

    /**
     * Starts the buff selection UI/phase, once the president-reveal sequence has
     * finished showing each team their president.
     */
    public void beginBuffSelectionPhase() {
        startPresidentSelectionPhase();
    }

    /**
     * Clear all presidential buffs from current presidents
     */
    private void clearPresidentialBuffs() {
        for (int team = 1; team <= 2; team++) {
            Player presPlayer = getPresidentPlayerByTeam(team);
            if (presPlayer != null && presPlayer.isOnline()) {
                clearPresidentEffects(presPlayer);

                // Reset health modifier through CashClashPlayer
                UUID presUuid = presPlayer.getUniqueId();
                var cashPlayer = session.getCashClashPlayer(presUuid);
                if (cashPlayer != null) {
                    cashPlayer.resetHealthModifier();
                }

                Messages.debug("[PTP] Cleared all buffs for president: " + presPlayer.getName());
            }

            // Reset buff in president record
            President pres = presidents.get(team);
            if (pres != null) {
                presidents.put(team, pres.withResetBuff());
            }
        }
    }

    @Override
    public void onPlayerDeath(Player victim, Player killer) {
        UUID victimUuid = victim.getUniqueId();

        // Check if victim is a president
        int presidentTeam = findPresidentTeam(victimUuid);

        if (presidentTeam != 0) {
            // President died - update death count and notify
            President deadPresident = presidents.get(presidentTeam);
            President updatedPresident = deadPresident.withDeath();
            presidents.put(presidentTeam, updatedPresident);
            int deaths = updatedPresident.deaths();
            int killerTeam = (presidentTeam == 1) ? 2 : 1;

            Messages.debug("[PTP] President died! Team " + presidentTeam + " - Deaths: " + deaths + " | Killer team: " + killerTeam);

            // Clear health modifier when president dies so they don't keep extra hearts on respawn
            var cashPlayer = session.getCashClashPlayer(victimUuid);
            if (cashPlayer != null) {
                cashPlayer.resetHealthModifier();
                Messages.debug("[PTP] Cleared health modifier for deceased president: " + victim.getName());
            }

            if (deaths == 1) {
                String presTeamName = presidentTeam == 1 ? "Red" : "Blue";
                String colorTag = presTeamName.toLowerCase();
                Messages.broadcast(session.getPlayers(), "gamemode-ptp.president-died",
                        "color", colorTag,
                        "team_name", presTeamName,
                        "win_condition", String.valueOf(WIN_CONDITION));
            }

            addTeamKill(killerTeam);
            if (suddenDeathManager.isInSuddenDeath()) {
                suddenDeathPresidentKills.merge(killerTeam, 1, Integer::sum);
            }
            if (finalStandManager.isActive()) {
                suddenDeathWinningTeam = killerTeam;
                Messages.debug("[PTP] Final Stand winner determined after president death: Team " + suddenDeathWinningTeam);
            }
        } else if (killer != null) {
            // Regular player died, award killer's team if killer is a president
            int killerPresidentTeam = findPresidentTeam(killer.getUniqueId());
            if (killerPresidentTeam != 0) {
                addTeamKill(killerPresidentTeam);
            }

            // Permanent deaths during Final Stand
            if (finalStandManager.isActive()) {
                var cashVictim = session.getCashClashPlayer(victimUuid);
                if (cashVictim != null) {
                    cashVictim.setLives(0);
                    Messages.debug("[PTP] Permanent death during Final Stand for non-president: " + victim.getName());
                }
            }
        }
    }

    @Override
    public void onPlayerSpawn(Player player) {
        UUID playerUuid = player.getUniqueId();

        // Check if this player is a president
        int presidentTeam = findPresidentTeam(playerUuid);
        if (presidentTeam != 0) {
            President pres = presidents.get(presidentTeam);
            refreshPresidentEffects(pres, true);
            schedulePresidentGlow(presidentTeam, player);
            Messages.debug("[PTP] Refreshed president effects on spawn: " + player.getName());
        }

        // Apply extra heart effect if in sudden death
        suddenDeathManager.onPlayerSpawn(player);
    }

    @Override
    public boolean checkGameWinner() {
        int deaths1 = getPresidentDeaths(1);
        int deaths2 = getPresidentDeaths(2);

        if (suddenDeathWinningTeam > 0) {
            return true;
        }

        return !suddenDeathManager.isInSuddenDeath() &&
                (deaths1 >= WIN_CONDITION || deaths2 >= WIN_CONDITION);
    }

    @Override
    public int getWinningTeam() {
        if (suddenDeathWinningTeam > 0) {
            return suddenDeathWinningTeam;
        }

        int deaths1 = getPresidentDeaths(1);
        int deaths2 = getPresidentDeaths(2);

        // Return the team whose president didn't reach the assassination target first.
        if (!suddenDeathManager.isInSuddenDeath() && deaths1 >= WIN_CONDITION && deaths2 < WIN_CONDITION) {
            return 2; // Team 2 wins the round
        } else if (!suddenDeathManager.isInSuddenDeath() && deaths2 >= WIN_CONDITION && deaths1 < WIN_CONDITION) {
            return 1; // Team 1 wins the round
        }
        return 0; // No winner yet
    }

    @Override
    public void cleanup() {
        cancelTask(selectionTask);
        glowingTasks.values().forEach(this::cancelTask);
        glowingTasks.clear();
        finalStandManager.cancel();
        resetFinalStandBorder();
        suddenDeathManager.cleanup();
        presidents.clear();
        teamKillCount.clear();
        savedInventories.clear();
        // Reset recent heart bonus tracking on cleanup
        recentHeartBonusTeam = 0;
        recentHeartBonusAwardMs = 0L;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void schedulePresidentGlow(int team, Player player) {
        // Cancel existing task for this team if any
        cancelTask(glowingTasks.remove(team));

        // Schedule new task for 15s (300 ticks)
        BukkitTask task = SchedulerUtils.runTaskLater(() -> {
            if (player.isOnline()) {
                PresidentialEffectsUtils.applyGlowEffect(player);
                Messages.debug("[PTP] President glowing activated after 15s delay for: " + player.getName());
            }
            glowingTasks.remove(team);
        }, 300L);

        glowingTasks.put(team, task);
        Messages.debug("[PTP] Scheduled glowing for " + player.getName() + " in 15s");
    }

    @Override
    public String getRoundStartMessage() {
        if (suddenDeathManager.isInSuddenDeath()) {
            String pres1Name = getPresidentName(1);
            String pres2Name = getPresidentName(2);
            return "<gold>" + pres1Name + " and " + pres2Name + " are elected as your presidents! " +
                    "You can select up to 2 bonus effects for this final fight!</gold>";
        }
        return "<gold>" + getPresidentName(1) + " is your team's president and can pick 1 bonus effect.</gold>";
    }

    private String getPresidentName(int team) {
        President pres = presidents.get(team);
        if (pres == null) return "Unknown";
        Player presPlayer = Bukkit.getPlayer(pres.uuid());
        return presPlayer != null ? presPlayer.getName() : "Unknown";
    }

    @Override
    public String getBuyPhaseMessage() {
        if (suddenDeathManager.isInSuddenDeath()) {
            return "<yellow>The game has entered sudden death. Money bonuses have been replaced with an extra heart " +
                        "that lasts for 45 seconds. Eliminate the other team's president more times in 3 minutes to win the match!</yellow>";
        }

        return "<yellow>Best of 7 series - First to 2 assassinations wins each round! " +
                "Every 2 kills the president's team gets a split 15k bonus!</yellow>";
    }

    @Override
    public String getObjectiveShort() {
        return "Kill the President Twice";
    }

    @Override
    public void onFinalStandActivated() {
        Messages.broadcast(session.getPlayers(), "gamemode-ptp.final-stand-activated");
        Messages.broadcast(session.getPlayers(), "gamemode-ptp.border-closing");
        activateFinalStandElimination();
        startFinalStandBorder();
        Messages.debug("[PTP] Final Stand activated - non-Presidents will be eliminated");
    }

    @Override
    public void onSuddenDeathCycleEnded() {
        int team1Kills = suddenDeathPresidentKills.getOrDefault(1, 0);
        int team2Kills = suddenDeathPresidentKills.getOrDefault(2, 0);

        if (team1Kills != team2Kills) {
            suddenDeathWinningTeam = team1Kills > team2Kills ? 1 : 2;
            Messages.debug("[PTP] Sudden death cycle winner determined: Team " + suddenDeathWinningTeam + " with " + Math.max(team1Kills, team2Kills) + " president kills");
        } else {
            Messages.debug("[PTP] Sudden death cycle tied " + team1Kills + "-" + team2Kills + "; restarting");
        }
    }

    @Override
    public void onSuddenDeathCycleRestart() {
        suddenDeathPresidentKills.put(1, 0);
        suddenDeathPresidentKills.put(2, 0);
        
        // Reset scoreboard indicators and show timer start
        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                ScoreboardManager.getInstance().updatePlayerScoreboard(p);
                Messages.send(p, "gamemode-ptp.sudden-death-timer-start");
            }
        }
        
        Messages.broadcast(session.getPlayers(), "gamemode-ptp.sudden-death-tied-restart");
        Messages.debug("[PTP] Sudden death president kill counters reset for next cycle");
    }

    /**
     * Select random presidents for each team
     */
    private void selectPresidents() {
        UUID pres1Uuid = session.getTeamRed().getPlayers().stream().findAny().orElse(null);
        President pres1 = President.create(pres1Uuid, 1);
        presidents.put(1, pres1);

        UUID pres2Uuid = session.getTeamBlue().getPlayers().stream().findAny().orElse(null);
        President pres2 = President.create(pres2Uuid, 2);
        presidents.put(2, pres2);
    }

    /**
     * Start the 15-second president buff selection phase
     */
    private void startPresidentSelectionPhase() {
        selectionPhaseActive = true;
        buffSelectionFinalized = false;
        selectionTimeRemaining = SELECTION_TIME;

        Messages.debug("[PTP] Starting president buff selection phase (15 seconds)");

        Player presPlayer1 = getPresidentPlayerByTeam(1);
        Player presPlayer2 = getPresidentPlayerByTeam(2);

        if (presPlayer1 != null && presPlayer2 != null) {
            Messages.debug("[PTP] Both presidents found, giving buff selection items");
            PTPInventoryUtils.giveBuffSelectionItems(presPlayer1, savedInventories);
            PTPInventoryUtils.giveBuffSelectionItems(presPlayer2, savedInventories);
            Messages.debug("[PTP] Buff selection items given to both presidents");
        } else {
            Messages.debug("[PTP] ERROR: One or both presidents are null!");
        }

        selectionTask = SchedulerUtils.runTaskTimer(this::updateSelectionCountdown, 0, 20);
    }
    
    private void updateSelectionCountdown() {
        if (selectionTimeRemaining > 0) {
            if (selectionTimeRemaining == 15 || selectionTimeRemaining == 10 ||
                selectionTimeRemaining == 5 || selectionTimeRemaining <= 3) {
                Messages.broadcast(session.getPlayers(), "round.buff-selection-countdown-ptp",
                        "time_remaining", String.valueOf(selectionTimeRemaining));
            }
            selectionTimeRemaining--;
        } else if (!buffSelectionFinalized) {
            // Only execute once when timer expires
            buffSelectionFinalized = true;
            applyBuffsToPresidents();
            if (selectionTask != null) selectionTask.cancel();
        }
    }

    /**
     * Apply default random buffs to presidents who didn't select
     */
    private void applyBuffsToPresidents() {
        selectionPhaseActive = false;
        Messages.debug("[PTP] Selection phase ended - Applying buffs");

        PresidentialBuff[] buffs = PresidentialBuff.values();

        for (int team = 1; team <= 2; team++) {
            President pres = presidents.get(team);
            if (pres != null && getSelectedBuffs(pres.uuid()).isEmpty()) {
                List<PresidentialBuff> randomBuffs = new ArrayList<>(List.of(buffs));
                Collections.shuffle(randomBuffs);

                int randomBuffCount = suddenDeathManager.isInSuddenDeath() ? 2 : 1;
                randomBuffs = new ArrayList<>(randomBuffs.subList(0, randomBuffCount));

                President updatedPres = pres.withBuff(randomBuffs.getFirst());
                presidents.put(team, updatedPres);
                selectedBuffs.put(pres.uuid(), randomBuffs);

                Player presPlayer = getPresidentPlayerByTeam(team);
                if (presPlayer != null) {
                    String buffNames = randomBuffs.stream()
                            .map(PresidentialBuff::getName)
                            .reduce((first, second) -> first + ", " + second)
                            .orElse("None");
                    Messages.debug("[PTP] Team " + team + " - Applied random buffs: " + buffNames);
                    Messages.send(presPlayer, "gamemode-ptp.no-buff-selected", "buff_name", buffNames);
                }
            } else if (pres != null) {
                Messages.debug("[PTP] Team " + team + " - President selected buffs: " + getPresidentBuff(team));
                // Don't apply glow here - will be applied at combat start
            }
        }
        
        // Restore inventories for all presidents before combat
        for (UUID uuid : new ArrayList<>(savedInventories.keySet())) {
            Player presPlayer = Bukkit.getPlayer(uuid);
            if (presPlayer != null) {
                PTPInventoryUtils.restoreInventory(presPlayer, savedInventories);
            }
        }

        // Unlock the shop for all players
        Messages.broadcast(session.getPlayers(), "gamemode-ptp.buff-selected");
        Messages.debug("[PTP] Shop unlocked - All presidents have buffs selected");
    }

    private void refreshPresidentEffects(President pres, boolean applyBuff) {
        if (pres != null) {
            Player presPlayer = Bukkit.getPlayer(pres.uuid());
            if (presPlayer != null && presPlayer.isOnline()) {
                clearPresidentEffects(presPlayer);
                PresidentialEffectsUtils.applyGlowEffect(presPlayer);
                if (applyBuff) {
                    applyPresidentialBuff(presPlayer);
                }
            }
        }
    }

    private void clearPresidentEffects(Player player) {
        PresidentialEffectsUtils.clearPresidentialEffects(player);
    }

    /**
     * Add a kill for the team and check for bonuses
     */
    private void addTeamKill(int team) {
        int killCount = teamKillCount.merge(team, 1, Integer::sum);
        Messages.debug("[PTP] Team " + team + " kill count: " + killCount);

        // Every 2 kills grant bonus split among team
        if (killCount % KILL_BONUS_THRESHOLD == 0) {
            Team teamObj = team == 1 ? session.getTeamRed() : session.getTeamBlue();

            Messages.debug("[PTP] Team " + team + " reached " + killCount + " kills - Awarding bonus");

            if (suddenDeathManager.isInSuddenDeath()) {
                // In sudden death: award extra hearts instead of coins
                for (UUID uuid : teamObj.getPlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        suddenDeathManager.applyExtraHeart(p, HEART_DURATION_MS);
                        Messages.send(p, "gamemode-ptp.kill-bonus-heart");
                        Messages.debug("[PTP] Applied extra heart bonus to: " + p.getName());
                    }
                }
                // Record which team received the recent heart bonus and timestamp (for scoreboard placeholders)
                recentHeartBonusTeam = team;
                recentHeartBonusAwardMs = System.currentTimeMillis();
            } else {
                // Normal mode: award coins split among team
                long bonusPerPlayer = KILL_BONUS_AMOUNT / 4;
                for (UUID uuid : teamObj.getPlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        Messages.send(p, "gamemode-ptp.kill-bonus");
                        session.getCashClashPlayer(uuid).addCoins(bonusPerPlayer);
                    }
                }
            }
        }
    }

    /**
     * Apply the selected presidential buff to a president
     */
    private void applyPresidentialBuff(Player presPlayer) {
        if (isNotPresident(presPlayer.getUniqueId())) {
            Messages.debug("[PTP] Non-president tried to get buff applied");
            return;
        }

        President pres = getPresidentByUUID(presPlayer.getUniqueId());
        List<PresidentialBuff> buffs = getSelectedBuffs(presPlayer.getUniqueId());
        if (pres == null || buffs.isEmpty()) {
            Messages.debug("[PTP] No buff found for president: " + presPlayer.getName());
            return;
        }

        for (PresidentialBuff buff : buffs) {
            switch (buff) {
                case OFFENSE -> PresidentialEffectsUtils.applyPotionEffect(presPlayer, PotionEffectType.STRENGTH);
                case TANK -> PresidentialEffectsUtils.applyPotionEffect(presPlayer, PotionEffectType.RESISTANCE);
                case SPEED -> PresidentialEffectsUtils.applyPotionEffect(presPlayer, PotionEffectType.SPEED);
                case HP -> {
                    // Add 1 extra heart (permanent for this round, will be reset on round end)
                    UUID presUuid = presPlayer.getUniqueId();
                    var cashPlayer = session.getCashClashPlayer(presUuid);
                    if (cashPlayer != null) {
                        cashPlayer.addHealthModifier(4.0); // 2 health = 1 heart
                    }
                }
            }

            Messages.send(presPlayer, "gamemode-ptp.buff-activated", "buff_name", buff.getName());
        }
    }

    public boolean isNotPresident(UUID uuid) {
        return findPresidentTeam(uuid) == 0;
    }

    /**
     * Find which team a player is president of
     * @return team number (1 or 2) or null if not a president
     */
    private int findPresidentTeam(UUID uuid) {
        for (int team = 1; team <= 2; team++) {
            President pres = presidents.get(team);
            if (pres != null && pres.uuid().equals(uuid)) {
                return team;
            }
        }
        return 0;
    }

    /**
     * Get the President object by player UUID
     */
    private President getPresidentByUUID(UUID uuid) {
        int team = findPresidentTeam(uuid);
        return team == 0 ? null : presidents.get(team);
    }

    /**
     * Get the online Player for a president team
     */
    private Player getPresidentPlayerByTeam(int team) {
        President pres = presidents.get(team);
        return pres != null ? Bukkit.getPlayer(pres.uuid()) : null;
    }

    /**
     * Get death count for a president's team
     */
    private int getPresidentDeaths(int team) {
        President pres = presidents.get(team);
        return pres != null ? pres.deaths() : 0;
    }

    /**
     * Get president UUID for a specific team
     */
    public UUID getPresident(int teamNumber) {
        President pres = presidents.get(teamNumber);
        return pres != null ? pres.uuid() : null;
    }

    /**
     * Get assassination count for a specific team
     */
    public int getAssassinationCount(int teamNumber) {
        return getPresidentDeaths(teamNumber);
    }

    /**
     * Enter sudden death mode
     */
    public void enterSuddenDeath() {
        suddenDeathManager.enterSuddenDeath();
        Messages.broadcast(session.getPlayers(), "gamemode-ptp.sudden-death");
        Messages.broadcast(session.getPlayers(), "gamemode-ptp.sudden-death-timer-start");
        Messages.debug("[PTP] Entered sudden death - 3-minute sudden-death initial period started");
    }

    @Override
    public boolean forceSuddenDeathForTesting() {
        if (suddenDeathManager.isInSuddenDeath()) {
            return false;
        }

        enterSuddenDeath();
        return true;
    }

    @Override
    public void prepareSuddenDeathRound() {
        if (suddenDeathManager.isInSuddenDeath()) {
            return;
        }

        suddenDeathManager.enterSuddenDeath();

        Messages.broadcast(session.getPlayers(), "gamemode-ptp.sudden-death");
        Messages.broadcast(session.getPlayers(), "gamemode-ptp.sudden-death-timer-start");
    }

    /**
     * Activate Final Stand: eliminate all non-Presidents for 1v1 duel
     */
    private void activateFinalStandElimination() {
        PTPFinalStandUtils.eliminateNonPresidents(session, this::findPresidentTeam, this::getPresidentName);
    }

    private void startFinalStandBorder() {
        preFinalStandBorderSnapshot = PTPFinalStandUtils.startFinalStandBorder(session);
    }

    private void resetFinalStandBorder() {
        PTPFinalStandUtils.resetFinalStandBorder(session, preFinalStandBorderSnapshot);
        preFinalStandBorderSnapshot = null;
    }

    /**
     * Get the buff for a president's team
     */
    public String getPresidentBuff(int teamNumber) {
        President pres = presidents.get(teamNumber);
        if (pres == null || getSelectedBuffs(pres.uuid()).isEmpty()) {
            return "None";
        }
        return getSelectedBuffs(pres.uuid()).stream()
                .map(PresidentialBuff::getName)
                .reduce((first, second) -> first + ", " + second)
                .orElse("None");
    }

    /**
     * Check if buff selection phase is active (shop should be locked)
     */
    public boolean isBuffSelectionActive() {
        return selectionPhaseActive;
    }

    /**
     * Handle buff selection from a president
     * @return true if the buff selection was successful
     */
    public boolean handlePresidentBuffSelection(Player player, int slot) {
        if (!selectionPhaseActive) {
            Messages.debug("[PTP] Buff selection attempted but phase is not active");
            return false;
        }

        UUID playerUuid = player.getUniqueId();
        if (isNotPresident(playerUuid)) {
            Messages.debug("[PTP] Non-president " + player.getName() + " tried to select buff");
            return false;
        }

        PresidentialBuff buff;
        
        switch (slot) {
            case 1 -> buff = PresidentialBuff.OFFENSE;       // Strength
            case 3 -> buff = PresidentialBuff.SPEED;         // Speed
            case 5 -> buff = PresidentialBuff.TANK;          // Resistance
            case 7 -> buff = PresidentialBuff.HP;            // Health
            default -> {
                Messages.debug("[PTP] Invalid slot clicked: " + slot);
                return false;
            }
        }
        
        // Find the president record for this team
        int presTeam = findPresidentTeam(playerUuid);
        if (presTeam == 0) {
            return false;
        }

        President pres = presidents.get(presTeam);
        if (pres == null) {
            return false;
        }
        List<PresidentialBuff> buffs = selectedBuffs.computeIfAbsent(playerUuid, uuid -> new ArrayList<>());

        // In sudden death presidents can select up to 2 buffs; otherwise only 1
        int maxBuffs = suddenDeathManager.isInSuddenDeath() ? 2 : 1;

        // Check if already selected - if so, deselect
        if (buffs.contains(buff)) {
            buffs.remove(buff);
            President updatedPres = buffs.isEmpty() ? pres.withResetBuff() : pres.withBuff(buffs.getFirst());
            presidents.put(presTeam, updatedPres);
            Messages.debug("[PTP] " + player.getName() + " deselected buff: " + buff.getName());
            Messages.send(player, "gamemode-ptp.buff-deselected");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            return true;
        }
        
        // Check if we can select another buff
        if (buffs.size() >= maxBuffs) {
            Messages.send(player, "gamemode-ptp.buff-selection-limit", "max_buffs", String.valueOf(maxBuffs));
            return false;
        }

        // Select new buff
        buffs.add(buff);
        President updatedPres = pres.withBuff(buff);
        presidents.put(presTeam, updatedPres);
        Messages.debug("[PTP] " + player.getName() + " selected buff: " + buff.getName());
        Messages.send(player, "gamemode-ptp.buff-selected-player", "buff_name", buff.getName());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        return true;
    }

    private List<PresidentialBuff> getSelectedBuffs(UUID playerUuid) {
        return selectedBuffs.getOrDefault(playerUuid, List.of());
    }

    @Override
    public boolean isFinalStandActive() {
        return finalStandManager.isActive();
    }


    /**
     * Return the team number that most recently received an extra-heart bonus, or 0 if none or expired.
     * The recorded value expires after 3 minutes.
     */
    public int getRecentHeartBonusTeam() {
        if (recentHeartBonusTeam == 0) return 0;
        long elapsed = System.currentTimeMillis() - recentHeartBonusAwardMs;
        if (elapsed > 3 * 60 * 1000L) { // 3 minutes
            return 0;
        }
        return recentHeartBonusTeam;
    }

    /**
     * Get remaining milliseconds for the recent heart bonus indicator (for scoreboard timers), or 0 if expired.
     */
    public long getRecentHeartBonusRemainingMs() {
        if (recentHeartBonusTeam == 0) return 0L;
        long elapsed = System.currentTimeMillis() - recentHeartBonusAwardMs;
        long remaining = 3 * 60 * 1000L - elapsed;
        return Math.max(0L, remaining);
    }

    @Override
    public SuddenDeathManager getSuddenDeathManager() {
        return suddenDeathManager;
    }

    @Override
    public FinalStandManager getFinalStandManager() {
        return finalStandManager;
    }


    /**
     * Enum for presidential buff options
     */
    public enum PresidentialBuff {
        OFFENSE("Strength", PotionEffectType.STRENGTH),
        TANK("Resistance", PotionEffectType.RESISTANCE),
        SPEED("Speed", PotionEffectType.SPEED),
        HP("Extra Hearts", null); // Special case

        private final String name;
        private final PotionEffectType effect;

        PresidentialBuff(String name, PotionEffectType effect) {
            this.name = name;
            this.effect = effect;
        }

        public String getName() {
            return name;
        }

        public PotionEffectType getEffect() {
            return effect;
        }
    }
}
