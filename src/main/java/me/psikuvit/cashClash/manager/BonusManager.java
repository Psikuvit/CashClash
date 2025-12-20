package me.psikuvit.cashClash.manager;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.util.enums.BonusType;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BonusManager {

    // Timing constants
    private static final long SURVIVOR_TIME_MS = 3 * 60 * 1000; // 3 minutes
    private static final long CLOSE_CALLS_SURVIVE_TIME_MS = 10 * 1000; // 10 seconds
    private static final int UNDERDOG_KILLS_REQUIRED = 4;
    private static final int RAMPAGE_KILL_STREAK = 3;

    private final GameSession session;

    // Tracking data for midgame bonuses
    private final Map<UUID, Long> playerSpawnTime;
    private final Map<UUID, Long> closeCallHealTime;
    private final Set<UUID> closeCallsAwarded;
    private final Set<UUID> survivorAwarded;
    private final Set<UUID> rampageAwarded;
    private UUID firstDeathPlayer;
    private final Set<UUID> comebackKidAwarded;

    // Tasks
    private BukkitTask survivorCheckTask;
    private BukkitTask closeCallCheckTask;

    public BonusManager(GameSession session) {
        this.session = session;
        this.playerSpawnTime = new HashMap<>();
        this.closeCallHealTime = new HashMap<>();
        this.closeCallsAwarded = new HashSet<>();
        this.survivorAwarded = new HashSet<>();
        this.rampageAwarded = new HashSet<>();
        this.comebackKidAwarded = new HashSet<>();
        this.firstDeathPlayer = null;
    }

    /**
     * Start tracking bonuses for a new combat phase.
     */
    public void startRound() {
        reset();

        // Initialize spawn times for all players
        long now = System.currentTimeMillis();
        for (UUID uuid : session.getPlayers()) {
            playerSpawnTime.put(uuid, now);
        }

        startSurvivorCheck();
        startCloseCallCheck();
    }

    /**
     * Reset all tracking data for a new round.
     */
    public void reset() {
        playerSpawnTime.clear();
        closeCallHealTime.clear();
        closeCallsAwarded.clear();
        survivorAwarded.clear();
        rampageAwarded.clear();
        comebackKidAwarded.clear();
        firstDeathPlayer = null;

        stopTasks();
    }

    /**
     * Cleanup resources when game ends.
     */
    public void cleanup() {
        reset();
    }

    /**
     * Called when a player gets a kill. Handles FIRST_BLOOD and RAMPAGE bonuses.
     */
    public void onKill(UUID killer) {
        RoundData roundData = session.getCurrentRoundData();
        if (roundData == null) {
            return;
        }

        CashClashPlayer killerCcp = session.getCashClashPlayer(killer);
        if (killerCcp == null) {
            return;
        }

        // FIRST_BLOOD: First kill in the round
        if (roundData.getFirstBloodPlayer() == null) {
            roundData.setFirstBloodPlayer(killer);
            awardBonus(killer, BonusType.FIRST_BLOOD);
        }

        // RAMPAGE: Kill streak of 3
        if (killerCcp.getKillStreak() >= RAMPAGE_KILL_STREAK && !rampageAwarded.contains(killer)) {
            rampageAwarded.add(killer);
            awardBonus(killer, BonusType.RAMPAGE);
        }

        // COMEBACK_KID: First death player gets 2 kills before next death
        checkComebackKid(killer);
    }

    /**
     * Called when a player dies. Tracks first death for COMEBACK_KID.
     */
    public void onDeath(UUID player) {
        // Track first death for Comeback Kid
        if (firstDeathPlayer == null) {
            firstDeathPlayer = player;
        }

        // Reset survivor timer on death/respawn
        playerSpawnTime.put(player, System.currentTimeMillis());

        // Reset close call tracking on death
        closeCallHealTime.remove(player);
    }

    /**
     * Called when a player respawns.
     */
    public void onRespawn(UUID player) {
        // Reset survivor timer
        playerSpawnTime.put(player, System.currentTimeMillis());

        // Reset close call tracking
        closeCallHealTime.remove(player);
    }

    /**
     * Called when a player's health drops to 1 heart or below.
     */
    public void onReachLowHealth(UUID player) {
        if (closeCallsAwarded.contains(player)) {
            return;
        }

        // Mark player as in close call state (not yet healed)
        // We use -1 to indicate they're in low health state but haven't healed yet
        if (!closeCallHealTime.containsKey(player)) {
            closeCallHealTime.put(player, -1L);
        }
    }

    /**
     * Called when a player heals above 1 heart after being at or below 1 heart.
     */
    public void onHealFromLowHealth(UUID player) {
        if (closeCallsAwarded.contains(player)) {
            return;
        }

        Long state = closeCallHealTime.get(player);
        if (state != null && state == -1L) {
            // Player just healed from low health, start the 10-second timer
            closeCallHealTime.put(player, System.currentTimeMillis());
        }
    }

    /**
     * Called when a player drops back to low health after healing.
     * Resets the 10-second timer.
     */
    public void onDropBackToLowHealth(UUID player) {
        if (closeCallsAwarded.contains(player)) {
            return;
        }

        // Reset timer - they dropped back to low health
        closeCallHealTime.put(player, -1L);
    }

    /**
     * Called at end of round to award end-game bonuses.
     */
    public void awardEndRoundBonuses() {
        stopTasks();

        RoundData roundData = session.getCurrentRoundData();
        if (roundData == null) {
            return;
        }

        // MOST_KILLS
        UUID mostKillsPlayer = roundData.getMostKillsPlayer();
        if (mostKillsPlayer != null && roundData.getKills(mostKillsPlayer) > 0) {
            awardBonus(mostKillsPlayer, BonusType.MOST_KILLS);
        }

        // MOST_DAMAGE
        UUID mostDamagePlayer = roundData.getMostDamagePlayer();
        if (mostDamagePlayer != null && roundData.getDamage(mostDamagePlayer) > 0) {
            awardBonus(mostDamagePlayer, BonusType.MOST_DAMAGE);
        }

        // UNDERDOG: Cheapest loadout with 4+ kills
        UUID underdogPlayer = findUnderdogPlayer();
        if (underdogPlayer != null) {
            awardBonus(underdogPlayer, BonusType.UNDERDOG);
        }
    }


    private void startSurvivorCheck() {
        survivorCheckTask = SchedulerUtils.runTaskTimer(() -> {
            long now = System.currentTimeMillis();
            RoundData roundData = session.getCurrentRoundData();
            if (roundData == null) {
                return;
            }

            for (UUID uuid : session.getPlayers()) {
                if (survivorAwarded.contains(uuid)) {
                    continue;
                }

                if (!roundData.isAlive(uuid)) {
                    continue;
                }

                Long spawnTime = playerSpawnTime.get(uuid);
                if (spawnTime != null && (now - spawnTime) >= SURVIVOR_TIME_MS) {
                    survivorAwarded.add(uuid);
                    roundData.addSurvivor(uuid);
                    awardBonus(uuid, BonusType.SURVIVOR);
                }
            }
        }, 20L * 10, 20L * 10); // Check every 10 seconds
    }

    private void startCloseCallCheck() {
        closeCallCheckTask = SchedulerUtils.runTaskTimer(() -> {
            long now = System.currentTimeMillis();
            RoundData roundData = session.getCurrentRoundData();
            if (roundData == null) {
                return;
            }

            for (Map.Entry<UUID, Long> entry : new HashMap<>(closeCallHealTime).entrySet()) {
                UUID uuid = entry.getKey();
                Long healTime = entry.getValue();

                if (closeCallsAwarded.contains(uuid)) {
                    closeCallHealTime.remove(uuid);
                    continue;
                }

                if (!roundData.isAlive(uuid)) {
                    closeCallHealTime.remove(uuid);
                    continue;
                }

                // Only check if timer has started (healTime > 0, not -1 which means still at low health)
                if (healTime > 0 && (now - healTime) >= CLOSE_CALLS_SURVIVE_TIME_MS) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline() && p.getHealth() > 2.0) {
                        closeCallsAwarded.add(uuid);
                        closeCallHealTime.remove(uuid);
                        awardBonus(uuid, BonusType.CLOSE_CALLS);

                        // Reset player's close call tracking in CashClashPlayer
                        CashClashPlayer ccp = session.getCashClashPlayer(uuid);
                        if (ccp != null) {
                            ccp.resetLife();
                        }
                    }
                }
            }
        }, 20L, 20L); // Check every second
    }

    private void checkComebackKid(UUID killer) {
        if (firstDeathPlayer == null || !firstDeathPlayer.equals(killer)) {
            return;
        }

        if (comebackKidAwarded.contains(killer)) {
            return;
        }

        RoundData roundData = session.getCurrentRoundData();
        if (roundData == null) {
            return;
        }

        // Check if player has 2+ kills this round
        int killsThisRound = roundData.getKills(killer);
        if (killsThisRound >= 2) {
            comebackKidAwarded.add(killer);
            awardBonus(killer, BonusType.COMEBACK_KID);
        }
    }

    private UUID findUnderdogPlayer() {
        RoundData roundData = session.getCurrentRoundData();
        if (roundData == null) {
            return null;
        }

        UUID cheapestPlayer = null;
        long lowestLoadoutValue = Long.MAX_VALUE;

        for (UUID uuid : session.getPlayers()) {
            int kills = roundData.getKills(uuid);
            if (kills < UNDERDOG_KILLS_REQUIRED) {
                continue;
            }

            CashClashPlayer ccp = session.getCashClashPlayer(uuid);
            if (ccp == null) {
                continue;
            }

            // Calculate loadout value (total spent)
            long loadoutValue = ccp.getPurchaseHistory().stream()
                    .mapToLong(PurchaseRecord::price)
                    .sum();

            if (loadoutValue < lowestLoadoutValue) {
                lowestLoadoutValue = loadoutValue;
                cheapestPlayer = uuid;
            }
        }

        return cheapestPlayer;
    }

    private void awardBonus(UUID playerUuid, BonusType bonusType) {
        CashClashPlayer ccp = session.getCashClashPlayer(playerUuid);
        if (ccp == null) {
            return;
        }

        // Use the existing earnBonus method
        ccp.earnBonus(bonusType);

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            String bonusName = formatBonusName(bonusType);
            Messages.send(player, "");
            Messages.send(player, "<dark_purple><bold>⭐ BONUS!</bold></dark_purple> <light_purple>" + bonusName + "</light_purple>");
            Messages.send(player, "<gray>+$" + formatCoins(bonusType.getReward()) + " coins</gray>");
            Messages.send(player, "");
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }

        Messages.broadcast(session.getPlayers(), "<dark_purple>" + getPlayerName(playerUuid) + "</dark_purple> <dark_gray>earned</dark_gray> <light_purple>" + formatBonusName(bonusType) + "</light_purple>");
    }

    private void stopTasks() {
        if (survivorCheckTask != null) {
            survivorCheckTask.cancel();
            survivorCheckTask = null;
        }
        if (closeCallCheckTask != null) {
            closeCallCheckTask.cancel();
            closeCallCheckTask = null;
        }
    }

    private String formatBonusName(BonusType type) {
        return type.name().replace("_", " ");
    }

    private String getPlayerName(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        return p != null ? p.getName() : "Unknown";
    }

    private String formatCoins(long coins) {
        return String.format("%,d", coins);
    }
}
