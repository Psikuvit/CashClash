package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.CustomArmorManager;
import me.psikuvit.cashClash.manager.EconomyManager;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.player.BonusType;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.Location;

/**
 * Handles player deaths and respawns
 */
public class DeathListener implements Listener {

    private final CustomArmorManager armorManager = CustomArmorManager.getInstance();

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);

        if (session == null) return;

        CashClashPlayer victim = session.getCashClashPlayer(player.getUniqueId());
        if (victim == null) return;

        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);

        victim.handleDeath();
        session.getCurrentRoundData().removeLife(player.getUniqueId());

        // Handle killer rewards
        Player killer = player.getKiller();
        if (killer != null) {
            CashClashPlayer killerCCP = session.getCashClashPlayer(killer.getUniqueId());
            if (killerCCP != null) {
                handleKillRewards(session, killerCCP, victim);

                armorManager.onPlayerKill(killer);
            }
        }

        // Determine the template spectator location (converted to session world)
        Location spectatorLoc = null;
        Arena arena = ArenaManager.getInstance().getArena(session.getArenaNumber());
        if (arena != null ) {
            TemplateWorld tpl = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
            spectatorLoc = tpl.getSpectatorSpawn();
        }
        if (spectatorLoc == null ) spectatorLoc = session.getGameWorld().getSpawnLocation();

        // If out of lives -> permanently spectator for this round
        if (victim.getLives() <= 0) {
            Messages.send(player, "<red>You are out of lives for this round!</red>");

            Location finalSpectatorLoc = spectatorLoc;
            Bukkit.getScheduler().runTask(CashClashPlugin.getInstance(), () -> {
                player.spigot().respawn();
                player.teleport(finalSpectatorLoc);
                player.setGameMode(GameMode.SPECTATOR);
            });

            return;
        }

        // Otherwise, make the player a spectator immediately (skip death screen) and schedule respawn
        int respawnDelaySec = ConfigManager.getInstance().getRespawnDelay();
        int respawnProtectionSec = ConfigManager.getInstance().getRespawnProtection();
        Messages.send(player, "<yellow>You will respawn in " + respawnDelaySec + " seconds...</yellow>");

        Location finalSpectatorLoc1 = spectatorLoc;
        Bukkit.getScheduler().runTask(CashClashPlugin.getInstance(), () -> {
            player.spigot().respawn();
            player.teleport(finalSpectatorLoc1);
            player.setGameMode(GameMode.SPECTATOR);
        });

        // Schedule the actual respawn into the round after the delay
        Bukkit.getScheduler().runTaskLater(CashClashPlugin.getInstance(), () -> {
            GameSession session2 = GameManager.getInstance().getPlayerSession(player);
            if (session2 == null) return;

            var spawn = session2.getSpawnForPlayer(player.getUniqueId());
            if (spawn != null) player.teleport(spawn);

            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.max(1.0, maxHealth));
            player.setFoodLevel(20);

            player.setGameMode(GameMode.SURVIVAL);

            // Mark respawn protection on the player's CashClashPlayer record
            var ccp = session2.getCashClashPlayer(player.getUniqueId());
            if (ccp != null) {
                ccp.setRespawnProtection(respawnProtectionSec * 1000L); // ms
            }

            Messages.send(player, "<green>You have respawned.</green>");
        }, respawnDelaySec * 20L); // seconds -> ticks
    }

    private void handleKillRewards(GameSession session, CashClashPlayer killer, CashClashPlayer victim) {
        killer.handleKill();
        session.getCurrentRoundData().addKill(killer.getUuid());

        // First blood bonus
        if (session.getCurrentRoundData().getFirstBloodPlayer() == null) {
            session.getCurrentRoundData().setFirstBloodPlayer(killer.getUuid());
            killer.earnBonus(BonusType.FIRST_BLOOD);
            Messages.send(killer.getPlayer(), "<gold><bold>FIRST BLOOD!</bold> <yellow>+" + BonusType.FIRST_BLOOD.getReward() + "</yellow></gold>");
        }

        // Kill reward and stolen amount
        long killReward = EconomyManager.getKillReward(session, killer);
        long stolenAmount = EconomyManager.calculateStealAmount(session, victim);

        // Apply investor multiplier to rewards if any
        double mult = armorManager.getInvestorMultiplier(killer.getPlayer());

        if (killReward > 0) {
            long adjusted = Math.round(killReward * mult);
            killer.addCoins(adjusted);
            if (adjusted != killReward) Messages.send(killer.getPlayer(), "<green>Investor bonus applied: x" + String.format("%.2f", mult) + "</green>");
        }

        if (stolenAmount > 0) {
            long adj = Math.round(stolenAmount * mult);
            victim.deductCoins(adj);
            killer.addCoins(adj);
            Messages.send(killer.getPlayer(), "<yellow>+$" + adj + "</yellow> stolen from <gray>" + victim.getPlayer().getName() + "</gray>");
        }

        // Rampage bonus (3 kill streak)
        if (killer.getKillStreak() >= 3 && killer.getKillStreak() % 3 == 0) {
            killer.earnBonus(BonusType.RAMPAGE);
            Messages.send(killer.getPlayer(), "<gold><bold>RAMPAGE!</bold> <yellow>+" + BonusType.RAMPAGE.getReward() + "</yellow></gold>");
        }
    }
}
