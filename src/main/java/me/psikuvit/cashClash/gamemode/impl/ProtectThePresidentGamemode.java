package me.psikuvit.cashClash.gamemode.impl;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Protect the President Gamemode
 * Goal: Assassinate the enemy President 2 times to win
 */
public class ProtectThePresidentGamemode extends Gamemode {

    private static final int SELECTION_TIME = 15;
    private static final int KILL_BONUS_THRESHOLD = 2;
    private static final long KILL_BONUS_AMOUNT = 15000;
    private static final long HEART_DURATION_MS = 45 * 1000;
    private static final int WIN_CONDITION = 2;
    private static final int SUDDEN_DEATH_THRESHOLD = 3;

    private final Map<Integer, UUID> presidents;
    private final Map<Integer, Integer> presidentialDeaths;
    private final Map<UUID, PresidentialBuff> selectedBuffs;
    private final Map<UUID, Long> extraHeartExpiry;
    private final Map<Integer, Integer> teamKillCount;

    private boolean inSuddenDeath;
    private BukkitTask extraHeartTask;
    private BukkitTask selectionTask;
    private int selectionTimeRemaining;

    public ProtectThePresidentGamemode(GameSession session) {
        super(session, GamemodeType.PROTECT_THE_PRESIDENT);
        
        // Initialize all data structures
        this.presidents = new HashMap<>(2);
        this.presidentialDeaths = new HashMap<>(2);
        this.selectedBuffs = new HashMap<>();
        this.extraHeartExpiry = new HashMap<>();
        this.teamKillCount = new HashMap<>(2);
        this.inSuddenDeath = false;
        this.extraHeartTask = null;
        this.selectionTask = null;
        this.selectionTimeRemaining = SELECTION_TIME;
        
        // Pre-populate team maps
        presidentialDeaths.put(1, 0);
        presidentialDeaths.put(2, 0);
        teamKillCount.put(1, 0);
        teamKillCount.put(2, 0);
    }

    @Override
    public void onGameStart() {
        // Select a random president for each team
        selectPresidents();
        
        Team team1 = session.getTeam1();
        Team team2 = session.getTeam2();

        // Get president names
        Player pres1 = Bukkit.getPlayer(presidents.get(1));
        Player pres2 = Bukkit.getPlayer(presidents.get(2));

        String pres1Name = pres1 != null ? pres1.getName() : "Unknown";
        String pres2Name = pres2 != null ? pres2.getName() : "Unknown";

        // Announce presidents
        Messages.broadcastWithPrefix(session.getPlayers(),
                "<gold>Protect the President has been selected as the gamemode!</gold>");
        Messages.broadcastWithPrefix(session.getPlayers(),
                "<red>" + pres1Name + " is Team Red's president and can pick a bonus effect.</red>");
        Messages.broadcastWithPrefix(session.getPlayers(),
                "<blue>" + pres2Name + " is Team Blue's president and can pick a bonus effect.</blue>");

        // Start president buff selection (15 seconds)
        startPresidentSelectionPhase();
    }

    @Override
    public void onCombatPhaseStart() {
        // Apply glowing effect to presidents
        Player pres1 = Bukkit.getPlayer(presidents.get(1));
        Player pres2 = Bukkit.getPlayer(presidents.get(2));

        if (pres1 != null) {
            pres1.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
        }
        if (pres2 != null) {
            pres2.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
        }

        // Reset kill count for round
        teamKillCount.put(1, 0);
        teamKillCount.put(2, 0);
    }

    @Override
    public void onRoundEnd() {
        // Reset kill tracking
        teamKillCount.put(1, 0);
        teamKillCount.put(2, 0);
    }

    @Override
    public void onPlayerDeath(Player victim, Player killer) {
        UUID victimUuid = victim.getUniqueId();
        
        // Check if victim is a president
        Integer presidentTeam = null;
        if (victimUuid.equals(presidents.get(1))) {
            presidentTeam = 1;
        } else if (victimUuid.equals(presidents.get(2))) {
            presidentTeam = 2;
        }
        
        if (presidentTeam != null) {
            // President died - update death count and notify
            int deaths = presidentialDeaths.merge(presidentTeam, 1, Integer::sum);
            int killerTeam = (presidentTeam == 1) ? 2 : 1;
            
            if (deaths == 1) {
                String presTeamName = presidentTeam == 1 ? "Red" : "Blue";
                Messages.broadcastWithPrefix(session.getPlayers(),
                        "<" + presTeamName.toLowerCase() + "><bold>Team " + presTeamName + 
                        "'s president has been assassinated!</bold></> <yellow>1/" + WIN_CONDITION + "</yellow>");
            }
            
            addTeamKill(killerTeam);
        } else if (killer != null) {
            // Regular player died, award killer's team if killer is a president
            UUID killerUuid = killer.getUniqueId();
            if (killerUuid.equals(presidents.get(1))) {
                addTeamKill(1);
            } else if (killerUuid.equals(presidents.get(2))) {
                addTeamKill(2);
            }
        }
    }

    @Override
    public void onPlayerSpawn(Player player) {
        // Reapply glowing if they're a president
        if (player.getUniqueId().equals(presidents.get(1)) ||
            player.getUniqueId().equals(presidents.get(2))) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
        }

        // Apply extra heart effect if in sudden death
        if (inSuddenDeath && extraHeartExpiry.containsKey(player.getUniqueId())) {
            applyExtraHeart(player);
        }
    }

    @Override
    public boolean checkGameWinner() {
        int deaths1 = presidentialDeaths.get(1);
        int deaths2 = presidentialDeaths.get(2);

        // Check if a team has won
        if (deaths1 >= WIN_CONDITION || deaths2 >= WIN_CONDITION) {
            return true;
        }

        // Check for sudden death condition
        if (deaths1 == SUDDEN_DEATH_THRESHOLD && deaths2 == SUDDEN_DEATH_THRESHOLD && !inSuddenDeath) {
            enterSuddenDeath();
        }

        return false;
    }

    @Override
    public int getWinningTeam() {
        int deaths1 = presidentialDeaths.get(1);
        if (deaths1 >= WIN_CONDITION) {
            return 2;
        } else if (presidentialDeaths.get(2) >= WIN_CONDITION) {
            return 1;
        }
        return 0;
    }

    @Override
    public void cleanup() {
        cancelTask(selectionTask);
        cancelTask(extraHeartTask);
        presidents.clear();
        selectedBuffs.clear();
        extraHeartExpiry.clear();
        teamKillCount.clear();
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
        UUID presUuid = presidents.get(team);
        if (presUuid == null) return "Unknown";
        Player pres = Bukkit.getPlayer(presUuid);
        return pres != null ? pres.getName() : "Unknown";
    }

    @Override
    public String getBuyPhaseMessage() {
        if (inSuddenDeath) {
            return "<yellow>The game has entered sudden death. Money bonuses have been replaced with an extra heart " +
                   "that lasts for 45 seconds. Eliminate the other team's president 3 times to win!</yellow>";
        }

        return "<yellow>The first team to assassinate the other team's president 2 times wins! " +
               "Every 2 kills the president's team gets a split 15k bonus!</yellow>";
    }

    /**
     * Select random presidents for each team
     */
    private void selectPresidents() {
        presidents.put(1, session.getTeam1().getPlayers().stream().findAny().orElse(null));
        presidents.put(2, session.getTeam2().getPlayers().stream().findAny().orElse(null));
    }

    /**
     * Start the 15-second president buff selection phase
     */
    private void startPresidentSelectionPhase() {
        selectionTimeRemaining = SELECTION_TIME;
        selectionTask = SchedulerUtils.runTaskTimer(this::updateSelectionCountdown, 0, 20);
    }

    private void updateSelectionCountdown() {
        if (selectionTimeRemaining > 0) {
            if (selectionTimeRemaining == 15 || selectionTimeRemaining == 10 ||
                selectionTimeRemaining == 5 || selectionTimeRemaining <= 3) {
                Messages.broadcastWithPrefix(session.getPlayers(),
                        "<gold>Presidents must select their buff in " + selectionTimeRemaining + "s!</gold>");
            }
            selectionTimeRemaining--;
        } else {
            applyDefaultBuffsToPresidents();
            if (selectionTask != null) selectionTask.cancel();
        }
    }

    /**
     * Apply default random buffs to presidents who didn't select
     */
    private void applyDefaultBuffsToPresidents() {
        PresidentialBuff[] buffs = PresidentialBuff.values();

        for (int team = 1; team <= 2; team++) {
            UUID presUuid = presidents.get(team);
            if (presUuid != null && !selectedBuffs.containsKey(presUuid)) {
                PresidentialBuff randomBuff = buffs[(int) (Math.random() * buffs.length)];
                selectedBuffs.put(presUuid, randomBuff);

                Player pres = Bukkit.getPlayer(presUuid);
                if (pres != null) {
                    Messages.send(pres, "<gold>No buff selected! Random buff applied: " + randomBuff.getName() + "</gold>");
                }
            }
        }

        // Unlock the shop for all players
        Messages.broadcastWithPrefix(session.getPlayers(), "<green>Presidents have selected their buffs! Shop is now unlocked!</green>");
    }

    /**
     * Add a kill for the team and check for bonuses
     */
    private void addTeamKill(int team) {
        int killCount = teamKillCount.merge(team, 1, Integer::sum);

        // Every 2 kills grant bonus split among team
        if (killCount % KILL_BONUS_THRESHOLD == 0) {
            long bonusPerPlayer = KILL_BONUS_AMOUNT / 4;
            Team teamObj = team == 1 ? session.getTeam1() : session.getTeam2();

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
     * Enter sudden death mode
     */
    private void enterSuddenDeath() {
        inSuddenDeath = true;
        Messages.broadcastWithPrefix(session.getPlayers(), "<red><bold>SUDDEN DEATH!</bold></red>");
        Messages.broadcastWithPrefix(session.getPlayers(),
                "<yellow>Money bonuses have been replaced with extra hearts (45s duration).</yellow>");
        
        extraHeartTask = SchedulerUtils.runTaskTimer(this::checkExtraHeartExpiry, 0, 20);
    }

    private void checkExtraHeartExpiry() {
        long now = System.currentTimeMillis();
        extraHeartExpiry.entrySet().removeIf(entry -> {
            if (entry.getValue() <= now) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    p.removePotionEffect(PotionEffectType.GLOWING);
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Apply an extra heart to a player (45s duration)
     */
    private void applyExtraHeart(Player player) {
        UUID uuid = player.getUniqueId();
        long expiryTime = System.currentTimeMillis() + HEART_DURATION_MS;
        extraHeartExpiry.put(uuid, expiryTime);

        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 45 * 20, 0, false, false));

        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(player.getHealth() + 4, maxHealth));
    }

    // Getters for buff application
    public PresidentialBuff getPresidentBuff(UUID presidentUuid) {
        return selectedBuffs.get(presidentUuid);
    }

    public void setPresidentBuff(UUID presidentUuid, PresidentialBuff buff) {
        selectedBuffs.put(presidentUuid, buff);
    }

    public boolean isPresident(UUID uuid) {
        return presidents.containsValue(uuid);
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










