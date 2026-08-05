package me.psikuvit.cashClash.manager.player;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.enums.BonusType;
import me.psikuvit.cashClash.util.enums.RewardType;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Tracks and awards the four bonuses in the game, each a flat 2000 coins to that player only,
 * announced only to their own team: First Blood (first kill of the round), Killstreak (every
 * 4th kill in an uninterrupted streak), and Most Kills / Most Damage (that round's leader,
 * awarded at round end).
 */
public class BonusManager {

    private static final int KILLSTREAK_INTERVAL = 4;

    private final GameSession session;

    public BonusManager(GameSession session) {
        this.session = session;
    }

    /**
     * Called when a player gets a kill. Handles First Blood and Killstreak.
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

        // FIRST_BLOOD: first kill of the round
        if (roundData.getFirstBloodPlayer() == null) {
            roundData.setFirstBloodPlayer(killer);
            awardBonus(killer, BonusType.FIRST_BLOOD);
        }

        // KILLSTREAK: every 4th kill in an uninterrupted streak (streak resets to 0 on death)
        if (killerCcp.getKillStreak() % KILLSTREAK_INTERVAL == 0) {
            awardBonus(killer, BonusType.KILLSTREAK);
        }
    }

    /**
     * Called at end of round to award Most Kills / Most Damage to that round's leader.
     */
    public void awardEndRoundBonuses() {
        RoundData roundData = session.getCurrentRoundData();
        if (roundData == null) {
            return;
        }

        UUID mostKillsPlayer = roundData.getMostKillsPlayer();
        if (mostKillsPlayer != null && roundData.getKills(mostKillsPlayer) > 0) {
            awardBonus(mostKillsPlayer, BonusType.MOST_KILLS);
        }

        UUID mostDamagePlayer = roundData.getMostDamagePlayer();
        if (mostDamagePlayer != null && roundData.getDamage(mostDamagePlayer) > 0) {
            awardBonus(mostDamagePlayer, BonusType.MOST_DAMAGE);
        }
    }

    /**
     * Cleanup resources when game ends.
     */
    public void cleanup() {
        // No persistent state
    }

    private void awardBonus(UUID playerUuid, BonusType bonusType) {
        CashClashPlayer ccp = session.getCashClashPlayer(playerUuid);
        if (ccp == null) {
            return;
        }

        long reward = bonusType.getReward();

        Messages.debug("GAME", "Awarding bonus " + bonusType.name() + " to " + playerUuid + " (+$" + reward + ")");

        session.getRewardManager().grant(playerUuid, RewardType.BONUS, reward);
        ccp.getBonusesEarned().put(bonusType, ccp.getBonusesEarned().getOrDefault(bonusType, 0) + 1);

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            String bonusName = formatBonusName(bonusType);
            Messages.send(player, "bonus.announce-spacer");
            Messages.send(player, "bonus.bonus-earned",
                "bonus_name", bonusName);
            Messages.send(player, "bonus.bonus-coins",
                "amount", formatCoins(reward));
            Messages.send(player, "bonus.announce-spacer");
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }

        // Only the earner's own team sees the broadcast
        Team team = session.getPlayerTeam(playerUuid);
        if (team != null) {
            Messages.broadcastToTeam(team, "bonus.bonus-broadcast",
                "player_name", getPlayerName(playerUuid),
                "bonus_name", formatBonusName(bonusType));
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
