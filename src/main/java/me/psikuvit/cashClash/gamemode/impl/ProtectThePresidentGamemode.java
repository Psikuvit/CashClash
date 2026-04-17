package me.psikuvit.cashClash.gamemode.impl;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.psikuvit.cashClash.config.MessagesConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

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

    private final Map<Integer, President> presidents;
    private final Map<UUID, Long> extraHeartExpiry;
    private final Map<Integer, Integer> teamKillCount;
    private final Map<UUID, ItemStack[]> savedInventories;

    private final boolean inSuddenDeath;
    private boolean selectionPhaseActive;
    private boolean buffSelectionFinalized;
    private final BukkitTask extraHeartTask;
    private BukkitTask selectionTask;
    private int selectionTimeRemaining;

    public ProtectThePresidentGamemode(GameSession session) {
        super(session, GamemodeType.PROTECT_THE_PRESIDENT);

        // Initialize all data structures
        this.presidents = new HashMap<>(2);
        this.extraHeartExpiry = new HashMap<>();
        this.teamKillCount = new HashMap<>(2);
        this.savedInventories = new HashMap<>();
        this.inSuddenDeath = false;
        this.selectionPhaseActive = false;
        this.buffSelectionFinalized = false;
        this.extraHeartTask = null;
        this.selectionTask = null;
        this.selectionTimeRemaining = SELECTION_TIME;

        // Pre-populate team kill count
        teamKillCount.put(1, 0);
        teamKillCount.put(2, 0);
    }

    @Override
    public void onGameStart() {
        // Select a random president for each team
        selectPresidents();

        // Announce presidents
        Messages.broadcast(session.getPlayers(), "gamemode-ptp.game-started");
        Messages.broadcast(session.getPlayers(), MessagesConfig.getInstance().getMessage("gamemode-ptp.red-president",
                "president_name", getPresidentName(1)));
        Messages.broadcast(session.getPlayers(), MessagesConfig.getInstance().getMessage("gamemode-ptp.blue-president",
                "president_name", getPresidentName(2)));
    }

    @Override
    public void onCombatPhaseStart() {
        Messages.debug("[PTP] Combat phase started");


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
                // Explicitly remove all potion effects using removePotionEffect()
                presPlayer.removePotionEffect(PotionEffectType.GLOWING);
                presPlayer.removePotionEffect(PotionEffectType.STRENGTH);
                presPlayer.removePotionEffect(PotionEffectType.RESISTANCE);
                presPlayer.removePotionEffect(PotionEffectType.SPEED);

                // Reset max health if HP buff was active
                var maxHealthAttr = presPlayer.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealthAttr != null) {
                    double maxHealth = maxHealthAttr.getValue();
                    if (maxHealth > 20.0) {
                        maxHealthAttr.setBaseValue(20.0);
                    }
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

            if (deaths == 1) {
                String presTeamName = presidentTeam == 1 ? "Red" : "Blue";
                String colorTag = presTeamName.toLowerCase();
                Messages.broadcast(session.getPlayers(), MessagesConfig.getInstance().getMessage("gamemode-ptp.president-died",
                        "color", colorTag,
                        "team_name", presTeamName,
                        "win_condition", String.valueOf(WIN_CONDITION)));
            }

            addTeamKill(killerTeam);
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
            // Always reapply glowing to presidents
            President pres = presidents.get(presidentTeam);
            applyGlow(pres);
            Messages.debug("[PTP] Applied glowing to president on spawn: " + player.getName());
        }

        // Apply extra heart effect if in sudden death
        if (inSuddenDeath && extraHeartExpiry.containsKey(playerUuid)) {
            applyExtraHeart(player);
        }
    }

    @Override
    public boolean checkGameWinner() {
        // Check if a president has died 2 times in this round - if so, the round ends
        int deaths1 = getPresidentDeaths(1);
        int deaths2 = getPresidentDeaths(2);

        return deaths1 >= 2 || deaths2 >= 2; // Round ends
    }

    @Override
    public int getWinningTeam() {
        int deaths1 = getPresidentDeaths(1);
        int deaths2 = getPresidentDeaths(2);

        // Return the team whose president didn't die 2 times (round winner)
        if (deaths1 >= 2 && deaths2 < 2) {
            return 2; // Team 2 wins the round
        } else if (deaths2 >= 2 && deaths1 < 2) {
            return 1; // Team 1 wins the round
        }
        return 0; // No winner yet
    }

    @Override
    public void cleanup() {
        cancelTask(selectionTask);
        cancelTask(extraHeartTask);
        presidents.clear();
        extraHeartExpiry.clear();
        teamKillCount.clear();
        savedInventories.clear();

        // Remove extra heart effects from all players
        for (UUID uuid : extraHeartExpiry.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                clearPresidentialBuffs();
                // Reset health if over max
                if (p.getHealth() > 20.0) {
                    p.setHealth(20.0);
                }
                var maxHealthAttr = p.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealthAttr != null) {
                    maxHealthAttr.setBaseValue(20.0);
                }
            }
        }
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public String getRoundStartMessage() {
        if (inSuddenDeath) {
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
        if (inSuddenDeath) {
            return "<yellow>The game has entered sudden death. Money bonuses have been replaced with an extra heart " +
                    "that lasts for 45 seconds. Eliminate the other team's president 4 times to win!</yellow>";
        }

        return "<yellow>Best of 7 series - First to 4 assassinations wins! " +
                "Every 2 kills the president's team gets a split 15k bonus!</yellow>";
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

            Messages.send(president, "<gold>Right-click an item to select your buff! Right-click again to deselect.</gold>");
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
            item.unsetData(DataComponentTypes.CONSUMABLE); // Remove default consumable behavior
        } else {
            Messages.debug("[PTP] WARNING: Could not get ItemMeta for POTION");
        }
        
        return item;
    }


    private void updateSelectionCountdown() {
        if (selectionTimeRemaining > 0) {
            if (selectionTimeRemaining == 15 || selectionTimeRemaining == 10 ||
                selectionTimeRemaining == 5 || selectionTimeRemaining <= 3) {
                Messages.broadcast(session.getPlayers(), MessagesConfig.getInstance().getMessage("round.buff-selection-countdown-ptp",
                        "time_remaining", String.valueOf(selectionTimeRemaining)));
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
            if (pres != null && !pres.hasSelectedBuff()) {
                PresidentialBuff randomBuff = buffs[(int) (Math.random() * buffs.length)];
                President updatedPres = pres.withBuff(randomBuff);
                presidents.put(team, updatedPres);

                Player presPlayer = getPresidentPlayerByTeam(team);
                if (presPlayer != null) {
                    Messages.debug("[PTP] Team " + team + " - Applied random buff: " + randomBuff.getName());
                    Messages.send(presPlayer, "<gold>No buff selected! Random buff applied: " + randomBuff.getName() + "</gold>");
                }
            } else if (pres != null) {
                Messages.debug("[PTP] Team " + team + " - President selected buff: " + pres.selectedBuff().getName());
                applyGlow(presidents.get(team));
            }
        }
        
        // Restore inventories for all presidents SYNCHRONOUSLY before combat
        for (UUID uuid : savedInventories.keySet().stream().toList()) {
            Player presPlayer = Bukkit.getPlayer(uuid);
            if (presPlayer != null) {
                restoreInventory(presPlayer);
            }
        }

        // Unlock the shop for all players
        Messages.broadcast(session.getPlayers(), "gamemode-ptp.buff-selected");
        Messages.debug("[PTP] Shop unlocked - All presidents have buffs selected");
    }

    private void applyGlow(President pres) {
        if (pres != null) {
            Player presPlayer = Bukkit.getPlayer(pres.uuid());
            if (presPlayer != null) {
                presPlayer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false));
                Messages.debug("[PTP] Applied glowing to president: " + presPlayer.getName());
                applyPresidentialBuff(presPlayer);
            }
        }
    }

    /**
     * Add a kill for the team and check for bonuses
     */
    private void addTeamKill(int team) {
        int killCount = teamKillCount.merge(team, 1, Integer::sum);
        Messages.debug("[PTP] Team " + team + " kill count: " + killCount);

        // Every 2 kills grant bonus split among team
        if (killCount % KILL_BONUS_THRESHOLD == 0) {
            long bonusPerPlayer = KILL_BONUS_AMOUNT / 4;
            Team teamObj = team == 1 ? session.getTeamRed() : session.getTeamBlue();

            Messages.debug("[PTP] Team " + team + " reached " + killCount + " kills - Awarding bonus: " + bonusPerPlayer + " per player");

            for (UUID uuid : teamObj.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    Messages.send(p, "<green>+3,750 coins! (Kill bonus)</green>");
                    session.getCashClashPlayer(uuid).addCoins(bonusPerPlayer);
                }
            }
        }
    }

    /**
     * Apply an extra heart to a player (45s duration)
     */
    private void applyExtraHeart(Player player) {
        UUID uuid = player.getUniqueId();
        long expiryTime = System.currentTimeMillis() + HEART_DURATION_MS;
        extraHeartExpiry.put(uuid, expiryTime);

        Messages.debug("[PTP] Applied extra heart to: " + player.getName());

        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double maxHealth = maxHealthAttr.getBaseValue();
            player.setHealth(Math.min(player.getHealth() + 4, maxHealth));
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
        if (pres == null || !pres.hasSelectedBuff()) {
            Messages.debug("[PTP] No buff found for president: " + presPlayer.getName());
            return;
        }

        PresidentialBuff buff = pres.selectedBuff();

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
                var maxHealthAttr = presPlayer.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealthAttr != null) {
                    double currentMax = maxHealthAttr.getBaseValue();
                    maxHealthAttr.setBaseValue(currentMax + 6.0); // 3 hearts = 6 health
                    presPlayer.setHealth(Math.min(presPlayer.getHealth() + 6.0, currentMax + 6.0));
                    Messages.debug("[PTP] Applied +3 hearts buff to: " + presPlayer.getName());
                }
            }
        }

        Messages.send(presPlayer, "<gold>Buff Activated: " + buff.getName() + "!</gold>");
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
     * Get the buff for a president's team
     */
    public String getPresidentBuff(int teamNumber) {
        President pres = presidents.get(teamNumber);
        if (pres == null || !pres.hasSelectedBuff()) {
            return "None";
        }
        return pres.selectedBuff().getName();
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

        // Check if already selected - if so, deselect
        if (pres.hasSelectedBuff() && pres.selectedBuff() == buff) {
            President updatedPres = pres.withResetBuff();
            presidents.put(presTeam, updatedPres);
            Messages.debug("[PTP] " + player.getName() + " deselected buff: " + buff.getName());
            Messages.send(player, "<red>Buff deselected!</red>");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            return true;
        }
        
        // Select new buff
        President updatedPres = pres.withBuff(buff);
        presidents.put(presTeam, updatedPres);
        Messages.debug("[PTP] " + player.getName() + " selected buff: " + buff.getName());
        Messages.send(player, "<green>Buff selected: " + buff.getName() + "!</green>");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        return true;
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







