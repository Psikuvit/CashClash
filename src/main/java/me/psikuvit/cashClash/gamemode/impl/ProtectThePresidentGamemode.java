package me.psikuvit.cashClash.gamemode.impl;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.gamemode.SuddenDeathManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Protect the President Gamemode
 * Goal: Best of 7 series - first to 4 assassinations wins the match
 */
public class ProtectThePresidentGamemode extends Gamemode {

    private static final int SELECTION_TIME = 15;
    private static final int KILL_BONUS_THRESHOLD = 2;
    private static final long KILL_BONUS_AMOUNT = 15000;
    private static final long HEART_DURATION_MS = 45 * 1000;
    private static final int WIN_CONDITION = 4;
    private static final int SUDDEN_DEATH_TRIGGER_SCORE = 3;
    private static final int SUDDEN_DEATH_CYCLE_SECONDS = 180;

    private final Map<Integer, President> presidents;
    private final Map<Integer, Integer> teamKillCount;
    private final Map<Integer, Integer> suddenDeathPresidentKills;
    private final Map<UUID, Integer> selectedBuffCount;
    private final Map<UUID, List<PresidentialBuff>> selectedBuffs;
    private final Map<UUID, ItemStack[]> savedInventories;

    private final SuddenDeathManager suddenDeathManager;
    private boolean selectionPhaseActive;
    private boolean buffSelectionFinalized;
    private BukkitTask selectionTask;
    private BukkitTask suddenDeathCycleTask;
    private int selectionTimeRemaining;
    private boolean finalStandTriggered; // Track if we've already executed final stand
    private long suddenDeathCycleStartMs;
    private int suddenDeathCycle;
    private int suddenDeathWinningTeam;

    public ProtectThePresidentGamemode(GameSession session) {
        super(session, GamemodeType.PROTECT_THE_PRESIDENT);

        // Initialize all data structures
        this.presidents = new HashMap<>(2);
        this.teamKillCount = new HashMap<>(2);
        this.suddenDeathPresidentKills = new HashMap<>(2);
        this.selectedBuffCount = new HashMap<>();
        this.selectedBuffs = new HashMap<>();
        this.savedInventories = new HashMap<>();
        this.suddenDeathManager = new SuddenDeathManager(session, this);
        this.selectionPhaseActive = false;
        this.buffSelectionFinalized = false;
        this.selectionTask = null;
        this.suddenDeathCycleTask = null;
        this.selectionTimeRemaining = SELECTION_TIME;
        this.finalStandTriggered = false;
        this.suddenDeathCycleStartMs = 0L;
        this.suddenDeathCycle = 0;
        this.suddenDeathWinningTeam = 0;

        // Pre-populate team kill count
        teamKillCount.put(1, 0);
        teamKillCount.put(2, 0);
        suddenDeathPresidentKills.put(1, 0);
        suddenDeathPresidentKills.put(2, 0);
    }

    @Override
    public void onGameStart() {
        // Select a random president for each team
        selectPresidents();

        // Announce presidents
        Messages.broadcast(session.getPlayers(), "gamemode-ptp.game-started");
        Messages.broadcast(session.getPlayers(), "gamemode-ptp.red-president",
                "president_name", getPresidentName(1));
        Messages.broadcast(session.getPlayers(), "gamemode-ptp.blue-president",
                "president_name", getPresidentName(2));
    }

    @Override
    public void onCombatPhaseStart() {
        Messages.debug("[PTP] Combat phase started");

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
    }

    @Override
    public void onRoundEnd() {
        // Clear all buffs and reset for next round
        clearPresidentialBuffs();

        // Reset deaths for next round
        presidents.replaceAll((team, pres) -> pres.withResetDeaths());
    }

    /**
     * Called when entering buff selection phase at the start of each round.
     * This selects new presidents (except round 1) and starts the selection.
     */
    public void startRoundBuffSelection() {
        Messages.debug("[PTP] Starting buff selection for round: " + session.getCurrentRound());

        // Reset buff selection count for all players so they can pick new buffs this round
        selectedBuffCount.clear();
        selectedBuffs.clear();

        // Select new presidents for this round (except round 1, presidents already selected)
        if (session.getCurrentRound() > 1) {
            selectPresidents();
        }

        // Reset deaths for this round
        presidents.replaceAll((team, pres) -> pres.withResetDeaths());

        // Start the buff selection UI
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
        } else if (killer != null) {
            // Regular player died, award killer's team if killer is a president
            int killerPresidentTeam = findPresidentTeam(killer.getUniqueId());
            if (killerPresidentTeam != 0) {
                addTeamKill(killerPresidentTeam);
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

        // Check if final stand has activated
        if (suddenDeathManager.isFinalStandActive() && !finalStandTriggered) {
            activateFinalStandElimination();
            finalStandTriggered = true;
        }

        if (!suddenDeathManager.isInSuddenDeath() &&
                deaths1 == SUDDEN_DEATH_TRIGGER_SCORE &&
                deaths2 == SUDDEN_DEATH_TRIGGER_SCORE) {
            enterSuddenDeath();
            return false;
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
        cancelTask(suddenDeathCycleTask);
        suddenDeathManager.cleanup();
        presidents.clear();
        teamKillCount.clear();
        savedInventories.clear();
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public String getRoundStartMessage() {
        if (suddenDeathManager.isInSuddenDeath()) {
            String pres1Name = getPresidentName(1);
            String pres2Name = getPresidentName(2);
            return "<gold>" + pres1Name + " and " + pres2Name + " are elected as your presidents! " +
                    "You can select 2 bonus effects for this final fight!</gold>";
        }
        return "<gold>" + getPresidentName(1) + " is your team's president and can pick a bonus effect.</gold>";
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
                    "that lasts for 45 seconds. The penalty timer has doubled. Eliminate the other team's president " +
                    "more times in 3 minutes to win the match!</yellow>";
        }

        return "<yellow>Best of 7 series - First to 4 assassinations wins! " +
                "Every 2 kills the president's team gets a split 15k bonus!</yellow>";
    }

    @Override
    public void onFinalStandActivated() {
        Messages.broadcast(session.getPlayers(), "gamemode-ptp.final-stand-activated");
        Messages.broadcast(session.getPlayers(), "gamemode-ptp.border-closing");
        activateFinalStandElimination();
        startFinalStandBorder();
        finalStandTriggered = true;
        Messages.debug("[PTP] Final Stand activated - non-Presidents will be eliminated");
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
            giveBuffSelectionItems(presPlayer1);
            giveBuffSelectionItems(presPlayer2);
            Messages.debug("[PTP] Buff selection items given to both presidents");
        } else {
            Messages.debug("[PTP] ERROR: One or both presidents are null!");
        }

        selectionTask = SchedulerUtils.runTaskTimer(this::updateSelectionCountdown, 0, 20);
    }
    
    /**
     * Give buff selection items to a president
     * Slots: 2 (Strength), 4 (Speed), 6 (Resistance), 8 (Instant Health)
     */
    private void giveBuffSelectionItems(Player president) {
        UUID presUuid = president.getUniqueId();

        savedInventories.put(presUuid, president.getInventory().getContents().clone());

        SchedulerUtils.runTask(() -> {
            Messages.debug("[PTP] Saving inventory for president: " + president.getName());

            president.getInventory().clear();
            Messages.debug("[PTP] Cleared inventory for: " + president.getName());

            // Slot 2 - Strength Potion
            ItemStack strengthPotion = createBuffSelectionItem("Strength Potion",
                    "<gold>Strength I - Deal more damage</gold>");
            president.getInventory().setItem(1, strengthPotion);

            // Slot 4 - Speed Potion
            ItemStack speedPotion = createBuffSelectionItem("Speed Potion",
                    "<gold>Speed I - Move faster</gold>");
            president.getInventory().setItem(3, speedPotion);

            // Slot 6 - Resistance Potion
            ItemStack resistancePotion = createBuffSelectionItem("Resistance Potion",
                    "<gold>Resistance I - Take less damage</gold>");
            president.getInventory().setItem(5, resistancePotion);

            // Slot 8 - Instant Health Potion
            ItemStack healthPotion = createBuffSelectionItem("Health Potion",
                    "<gold>Extra Hearts - Gain +3 max hearts</gold>");
            president.getInventory().setItem(7, healthPotion);

            // Update inventory on client
            president.updateInventory();

            Messages.send(president, "gamemode-ptp.buff-selection-prompt");
        });
    }
    
    /**
     * Create a buff selection item with appropriate display and effects
     */
    private ItemStack createBuffSelectionItem(String name, String benefit) {
        ItemStack item = new ItemStack(Material.POTION);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.displayName(Messages.parse("<yellow>" + name + "</yellow>"));
            meta.lore(List.of(
                    Messages.parse(benefit),
                    Component.empty(),
                    Messages.parse("<gray>Right-click to select</gray>"),
                    Messages.parse("<gray>or deselect</gray>")
            ));
            // Mark as buff selection potion (undrinkable)
            meta.getPersistentDataContainer().set(Keys.BUFF_SELECTION_POTION, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
            try {
                item.unsetData(DataComponentTypes.CONSUMABLE); // Remove default consumable behavior
            } catch (Exception e) {
                Messages.debug("[PTP] Warning: Could not unset consumable data: " + e.getMessage());
            }
        } else {
            Messages.debug("[PTP] WARNING: Could not get ItemMeta for POTION");
        }
        
        return item;
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
                PresidentialBuff randomBuff = buffs[(int) (Math.random() * buffs.length)];
                President updatedPres = pres.withBuff(randomBuff);
                presidents.put(team, updatedPres);
                selectedBuffs.put(pres.uuid(), new ArrayList<>(List.of(randomBuff)));

                Player presPlayer = getPresidentPlayerByTeam(team);
                if (presPlayer != null) {
                    Messages.debug("[PTP] Team " + team + " - Applied random buff: " + randomBuff.getName());
                    Messages.send(presPlayer, "gamemode-ptp.no-buff-selected", "buff_name", randomBuff.getName());
                }
            } else if (pres != null) {
                Messages.debug("[PTP] Team " + team + " - President selected buffs: " + getPresidentBuff(team));
                // Don't apply glow here - will be applied at combat start
            }
        }
        
        // Restore inventories for all presidents SYNCHRONOUSLY before combat
        for (UUID uuid : new ArrayList<>(savedInventories.keySet())) {
            Player presPlayer = Bukkit.getPlayer(uuid);
            if (presPlayer != null) {
                restoreInventory(presPlayer);
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
                presPlayer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false));
                Messages.debug("[PTP] Applied glowing to president: " + presPlayer.getName());
                if (applyBuff) {
                    applyPresidentialBuff(presPlayer);
                }
            }
        }
    }

    private void clearPresidentEffects(Player player) {
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.SPEED);
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
            // Use INFINITE_DURATION - buffs will be explicitly removed on round end via clearPresidentialBuffs()
            switch (buff) {
                case OFFENSE -> {
                    presPlayer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0, false, false));
                    Messages.debug("[PTP] Applied Strength buff to: " + presPlayer.getName());
                }
                case TANK -> {
                    presPlayer.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
                    Messages.debug("[PTP] Applied Resistance buff to: " + presPlayer.getName());
                }
                case SPEED -> {
                    presPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false));
                    Messages.debug("[PTP] Applied Speed buff to: " + presPlayer.getName());
                }
                case HP -> {
                    // Add 3 extra hearts (permanent for this round, will be reset on round end)
                    UUID presUuid = presPlayer.getUniqueId();
                    var cashPlayer = session.getCashClashPlayer(presUuid);
                    if (cashPlayer != null) {
                        cashPlayer.setHealthModifier(6.0); // 6 health = 3 hearts
                        Messages.debug("[PTP] Applied +3 hearts buff to: " + presPlayer.getName());
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
        startSuddenDeathCycle();
    }

    /**
     * Activate Final Stand: eliminate all non-Presidents for 1v1 duel
     */
    private void activateFinalStandElimination() {
        Messages.debug("[PTP] Final Stand activated - eliminating all non-Presidents");

        for (UUID playerUuid : new ArrayList<>(session.getPlayers())) {
            Player p = Bukkit.getPlayer(playerUuid);
            if (p != null && p.isOnline()) {
                // Check if this player is a president
                int presTeam = findPresidentTeam(playerUuid);
                if (presTeam == 0) {
                    // Non-president - eliminate them for the 1v1 duel
                    p.setHealth(0);
                    Messages.debug("[PTP] Eliminated non-president: " + p.getName());
                } else {
                    // President - apply glow effect and announce
                    Messages.send(p, "gamemode-ptp.final-stand-president-survived",
                            "opponent", getPresidentName(presTeam == 1 ? 2 : 1));
                }
            }
        }
    }

    private void startFinalStandBorder() {
        if (session.getGameWorld() == null) {
            return;
        }

        Location center = getArenaCenter();
        WorldBorder border = session.getGameWorld().getWorldBorder();
        border.setCenter(center);
        border.setSize(120.0);
        border.setDamageAmount(1.0);
        border.setDamageBuffer(0.0);
        border.setSize(20.0, 30L);
    }

    private Location getArenaCenter() {
        Player pres1 = getPresidentPlayerByTeam(1);
        Player pres2 = getPresidentPlayerByTeam(2);
        if (pres1 != null && pres2 != null) {
            Location loc1 = pres1.getLocation();
            Location loc2 = pres2.getLocation();
            return new Location(session.getGameWorld(),
                    (loc1.getX() + loc2.getX()) / 2.0,
                    (loc1.getY() + loc2.getY()) / 2.0,
                    (loc1.getZ() + loc2.getZ()) / 2.0);
        }
        return session.getGameWorld().getSpawnLocation();
    }

    private void startSuddenDeathCycle() {
        suddenDeathPresidentKills.put(1, 0);
        suddenDeathPresidentKills.put(2, 0);
        suddenDeathCycle = Math.max(suddenDeathCycle, 0) + 1;
        suddenDeathCycleStartMs = System.currentTimeMillis();
        cancelTask(suddenDeathCycleTask);
        suddenDeathCycleTask = SchedulerUtils.runTaskTimer(this::checkSuddenDeathCycle, 20L, 20L);
    }

    private void checkSuddenDeathCycle() {
        if (!suddenDeathManager.isInSuddenDeath() || suddenDeathWinningTeam > 0) {
            return;
        }

        if (getSuddenDeathTimerRemainingSeconds() > 0) {
            return;
        }

        int team1Kills = suddenDeathPresidentKills.getOrDefault(1, 0);
        int team2Kills = suddenDeathPresidentKills.getOrDefault(2, 0);

        if (team1Kills == team2Kills) {
            Messages.broadcast(session.getPlayers(), "gamemode-ptp.sudden-death-tied-restart");
            startSuddenDeathCycle();
            return;
        }

        suddenDeathWinningTeam = team1Kills > team2Kills ? 1 : 2;
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

        // In sudden death, allow 2 buffs; otherwise allow 1
        int maxBuffs = suddenDeathManager.isInSuddenDeath() ? 2 : 1;

        // Check if already selected - if so, deselect
        if (buffs.contains(buff)) {
            buffs.remove(buff);
            President updatedPres = buffs.isEmpty() ? pres.withResetBuff() : pres.withBuff(buffs.getFirst());
            presidents.put(presTeam, updatedPres);
            selectedBuffCount.put(playerUuid, buffs.size());
            Messages.debug("[PTP] " + player.getName() + " deselected buff: " + buff.getName());
            Messages.send(player, "gamemode-ptp.buff-deselected");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            return true;
        }
        
        // In non-sudden death, allow replacing the current buff
        if (!suddenDeathManager.isInSuddenDeath() && !buffs.isEmpty()) {
            buffs.clear();
            buffs.add(buff);
            President updatedPres = pres.withBuff(buff);
            presidents.put(presTeam, updatedPres);
            selectedBuffCount.put(playerUuid, 1);
            Messages.debug("[PTP] " + player.getName() + " replaced buff with: " + buff.getName());
            Messages.send(player, "gamemode-ptp.buff-selected-player", "buff_name", buff.getName());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            return true;
        }
        
        // Check if we can select another buff (for sudden death with multiple buffs)
        if (buffs.size() >= maxBuffs) {
            Messages.send(player, "gamemode-ptp.buff-selection-limit", "max_buffs", String.valueOf(maxBuffs));
            return false;
        }

        // Select new buff
        buffs.add(buff);
        President updatedPres = pres.withBuff(buff);
        presidents.put(presTeam, updatedPres);
        selectedBuffCount.put(playerUuid, buffs.size());
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
        return suddenDeathManager.isFinalStandActive();
    }

    @Override
    public int getSuddenDeathTimerRemainingSeconds() {
        if (!suddenDeathManager.isInSuddenDeath() || suddenDeathManager.isFinalStandActive() || suddenDeathCycleStartMs <= 0) {
            return -1;
        }

        long elapsedSeconds = (System.currentTimeMillis() - suddenDeathCycleStartMs) / 1000;
        return (int) Math.max(0, SUDDEN_DEATH_CYCLE_SECONDS - elapsedSeconds);
    }

    @Override
    public int getSuddenDeathCycle() {
        return suddenDeathCycle;
    }

    @Override
    public long getExtraHeartRemainingMs(UUID playerUuid) {
        return suddenDeathManager.getExtraHeartRemainingMs(playerUuid);
    }

    /**
     * Restore a player's inventory from the saved state
     */
    private void restoreInventory(Player player) {
        UUID uuid = player.getUniqueId();
        if (savedInventories.containsKey(uuid)) {
            // Restore synchronously to ensure it's done before combat starts
            ItemStack[] savedContents = savedInventories.get(uuid);
            player.getInventory().clear();
            SchedulerUtils.runTaskAsync(() -> {
                player.getInventory().setContents(savedContents);
                Messages.debug("[PTP] Restored inventory for: " + player.getName());
                savedInventories.remove(uuid);
                player.updateInventory();
            });
        }
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
