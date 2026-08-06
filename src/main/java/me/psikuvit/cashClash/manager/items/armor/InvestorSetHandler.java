package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Investor's Set - rewards the wearer's team coins on kills and objective completions.
 */
public class InvestorSetHandler extends ArmorSetHandler {

    public InvestorSetHandler(CustomArmorManager manager) {
        super(manager);
    }

    public int countInvestorsPieces(Player p) {
        int cnt = 0;
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca.isInvestorsSet()) cnt++;
        }
        return cnt;
    }

    /**
     * Balance tradeoff for the set's passive team income: melee damage dealt is reduced 5%
     * per Investor's Set piece worn (e.g. full 4-piece set = -20%).
     * @return melee damage multiplier, 1.0 if no pieces are worn
     */
    public double getMeleeDamageMultiplier(Player player) {
        int pieces = countInvestorsPieces(player);
        if (pieces <= 0) return 1.0;
        return 1.0 - (0.05 * pieces);
    }

    /**
     * Calculate the price for an investor piece based on how many are already owned.
     */
    public long getInvestorPrice(CustomArmorItem armor, int piecesOwned) {
        if (!armor.isInvestorsSet()) return armor.getBasePrice();
        // Each piece increases price by 25%
        double multiplier = Math.pow(1.25, piecesOwned);
        return Math.round(armor.getBasePrice() * multiplier);
    }

    /**
     * Investor's Set: reward the wearer's team coins on a kill or objective completion
     * (CTF flag capture, Kill Confirm tag confirm - a Protect The President assassination
     * is just a kill). Routed through {@link me.psikuvit.cashClash.manager.player.RewardManager#grantKillOrObjective}
     * rather than called directly by gamemode code.
     */
    public void onInvestorReward(Player player, GameSession session) {
        if (player == null || session == null) return;
        int pieces = countInvestorsPieces(player);
        Messages.debug(player, "ECONOMY", "InvestorSet: onInvestorReward called, pieces=" + pieces);
        if (pieces <= 0) return;

        Team team = session.getPlayerTeam(player);
        Messages.debug(player, "ECONOMY", "InvestorSet: team=" + (team == null ? "null" : team.getTeamNumber()));
        if (team == null) return;

        int reward = 200 * pieces;
        for (UUID uuid : team.getPlayers()) {
            CashClashPlayer ccp = session.getCashClashPlayer(uuid);
            if (ccp != null) {
                ccp.addCoins(reward);
            }
            Player teammate = Bukkit.getPlayer(uuid);
            if (teammate != null && teammate.isOnline()) {
                playInvestorRewardEffect(teammate, reward);
            }
        }
    }

    /**
     * Play the coin reward particle/sound effect for an investor reward recipient.
     */
    private void playInvestorRewardEffect(Player player, int reward) {
        SoundUtils.play(player, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        Messages.send(player, "armor.investor-reward", "reward", String.valueOf(reward));

        Location center = player.getLocation().clone().add(0, 0.6, 0);
        for (int i = 0; i < 18; i++) {
            int delay = i;
            SchedulerUtils.runTaskLater(() -> {
                double angle = (Math.PI * 2 / 18) * delay;
                double x = Math.cos(angle) * 0.55;
                double z = Math.sin(angle) * 0.55;
                ParticleUtils.spawnDust(center.clone().add(x, 0, z), Color.fromRGB(0, 120, 40), 1.3f, 2, 0.05);
            }, (long) (delay * 0.8));
        }
    }

    @Override
    public void cleanup() {
        // No persistent state
    }

    @Override
    public void resetRoundTracking() {
        // No persistent state
    }
}
