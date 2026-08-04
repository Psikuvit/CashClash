package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
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
 * Investor's Set - rewards the wearer's team coins on kills and CTF flag captures.
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
     * Calculate the price for an investor piece based on how many are already owned.
     */
    public long getInvestorPrice(CustomArmorItem armor, int piecesOwned) {
        if (!armor.isInvestorsSet()) return armor.getBasePrice();
        // Each piece increases price by 25%
        double multiplier = Math.pow(1.25, piecesOwned);
        return Math.round(armor.getBasePrice() * multiplier);
    }

    /**
     * Investor's Set: reward the killer's team coins on every kill.
     */
    public void onInvestorKill(Player killer, GameSession session) {
        if (killer == null || session == null) return;
        int pieces = countInvestorsPieces(killer);
        if (pieces <= 0) return;

        Team team = session.getPlayerTeam(killer);
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
     * Investor's Set: reward the capturing player's team coins on a CTF flag capture.
     */
    public void onInvestorObjectivectf(Player player) {
        if (player == null) return;
        int pieces = countInvestorsPieces(player);
        if (pieces <= 0) return;

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        Team team = session.getPlayerTeam(player);
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
        SoundUtils.play(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.7f, 1.5f);
        Messages.send(player, "armor.investor-reward", "reward", String.valueOf(reward));

        Color lightGreen = Color.fromRGB(120, 255, 120);
        Location center = player.getLocation().clone().add(0, 0.6, 0);
        for (int i = 0; i < 18; i++) {
            int delay = i;
            SchedulerUtils.runTaskLater(() -> {
                double angle = (Math.PI * 2 / 18) * delay;
                double x = Math.cos(angle) * 0.55;
                double z = Math.sin(angle) * 0.55;
                ParticleUtils.spawnDust(center.clone().add(x, 0, z), lightGreen, 1.3f, 2);
            }, (int) (delay * 0.8));
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
