package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.BonusManager;
import me.psikuvit.cashClash.manager.CustomArmorManager;
import me.psikuvit.cashClash.manager.EconomyManager;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.player.BonusType;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.manager.PlayerDataManager;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Handles player deaths and respawns with improved code quality
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

        // Prevent item drops and experience loss
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);

        // Increment persistent death count
        PlayerDataManager.getInstance().incDeaths(player.getUniqueId());
        victim.handleDeath();
        session.getCurrentRoundData().removeLife(player.getUniqueId());

        Player killer = player.getKiller();
        if (killer != null) handleKillerRewards(session, killer, victim);

        Location spectatorLocation = getSpectatorLocation(session);

        if (victim.getLives() <= 0) handlePermanentSpectator(player, spectatorLocation);
        else handleTemporarySpectatorAndRespawn(player, spectatorLocation);

    }

    /**
     * Get the spectator spawn location adjusted to the game session's copied world
     */
    private Location getSpectatorLocation(GameSession session) {
        Arena arena = ArenaManager.getInstance().getArena(session.getArenaNumber());
        if (arena != null) {
            TemplateWorld template = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
            if (template != null && template.getSpectatorSpawn() != null) {
                // Adjust template location to copied world
                return LocationUtils.adjustLocationToWorld(template.getSpectatorSpawn(), session.getGameWorld());
            }
        }

        // Fallback to game world spawn
        return session.getGameWorld() != null
                ? session.getGameWorld().getSpawnLocation()
                : Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    /**
     * Handle killer rewards and statistics
     */
    private void handleKillerRewards(GameSession session, Player killer, CashClashPlayer victim) {
        CashClashPlayer killerCCP = session.getCashClashPlayer(killer.getUniqueId());
        if (killerCCP == null) {
            return;
        }

        PlayerDataManager.getInstance().incKills(killer.getUniqueId());
        handleKillRewards(session, killerCCP, victim);
        armorManager.onPlayerKill(killer);
    }

    /**
     * Handle player who is permanently out of lives (spectator for remainder of round)
     */
    private void handlePermanentSpectator(Player player, Location spectatorLocation) {
        Messages.send(player, "<red>You are out of lives for this round!</red>");

        SchedulerUtils.runTask(() -> {
            player.spigot().respawn();
            player.teleport(spectatorLocation);
            player.setGameMode(GameMode.SPECTATOR);
        });
    }

    /**
     * Handle temporary spectator state and schedule respawn
     */
    private void handleTemporarySpectatorAndRespawn(Player player, Location spectatorLocation) {
        int respawnDelaySec = ConfigManager.getInstance().getRespawnDelay();
        int respawnProtectionSec = ConfigManager.getInstance().getRespawnProtection();

        Messages.send(player, "<yellow>You will respawn in " + respawnDelaySec + " seconds...</yellow>");

        // Make player spectator immediately (skip death screen)
        SchedulerUtils.runTask(() -> {
            player.spigot().respawn();
            player.teleport(spectatorLocation);
            player.setGameMode(GameMode.SPECTATOR);
        });

        // Schedule respawn after delay
        SchedulerUtils.runTaskLater(() -> respawnPlayer(player, respawnProtectionSec), respawnDelaySec * 20L);
    }

    /**
     * Respawn player back into the game
     */
    private void respawnPlayer(Player player, int respawnProtectionSec) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            return;
        }

        // Get spawn location in copied world
        Location spawnLocation = session.getSpawnForPlayer(player.getUniqueId());
        if (spawnLocation != null) {
            player.teleport(spawnLocation);
        }

        // Reset player health and hunger
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double maxHealth = maxHealthAttr.getValue();
            player.setHealth(Math.max(1.0, maxHealth));
        } else {
            player.setHealth(20.0); // Default health
        }

        player.setFoodLevel(20);
        player.setGameMode(GameMode.SURVIVAL);

        // Apply respawn protection
        CashClashPlayer cashClashPlayer = session.getCashClashPlayer(player.getUniqueId());
        if (cashClashPlayer != null) {
            cashClashPlayer.setRespawnProtection(respawnProtectionSec * 1000L); // Convert to milliseconds
        }

        BonusManager bonusManager = session.getBonusManager();
        if (bonusManager != null) {
            bonusManager.onRespawn(player.getUniqueId());
        }

        Messages.send(player, "<green>You have respawned.</green>");
    }

    /**
     * Calculate and apply kill rewards including bonuses
     */
    private void handleKillRewards(GameSession session, CashClashPlayer killer, CashClashPlayer victim) {
        killer.handleKill();
        session.getCurrentRoundData().addKill(killer.getUuid());

        // Check and award first blood bonus
        if (session.getCurrentRoundData().getFirstBloodPlayer() == null) {
            session.getCurrentRoundData().setFirstBloodPlayer(killer.getUuid());
            killer.earnBonus(BonusType.FIRST_BLOOD);

            long firstBloodReward = BonusType.FIRST_BLOOD.getReward();
            Messages.send(killer.getPlayer(),
                    "<gold><bold>FIRST BLOOD!</bold> <yellow>+$" + firstBloodReward + "</yellow></gold>");
        }

        // Calculate base rewards
        long killReward = EconomyManager.getKillReward(session, killer);
        long stolenAmount = EconomyManager.calculateStealAmount(session, victim);

        // Apply investor armor multiplier to rewards
        double investorMultiplier = armorManager.getInvestorMultiplier(killer.getPlayer());

        // Apply kill reward
        if (killReward > 0) {
            long adjustedReward = Math.round(killReward * investorMultiplier);
            killer.addCoins(adjustedReward);

            if (adjustedReward != killReward) {
                Messages.send(killer.getPlayer(),
                        "<green>Investor bonus applied: x" + String.format("%.2f", investorMultiplier) + "</green>");
            }
        }

        // Apply stolen amount from victim
        if (stolenAmount > 0) {
            long adjustedStolen = Math.round(stolenAmount * investorMultiplier);
            victim.deductCoins(adjustedStolen);
            killer.addCoins(adjustedStolen);

            Messages.send(killer.getPlayer(),
                    "<yellow>+$" + adjustedStolen + "</yellow> stolen from <gray>" + victim.getPlayer().getName() + "</gray>");
        }

        // Check and award rampage bonus (every 3 kills)
        int killStreak = killer.getKillStreak();
        if (killStreak >= 3 && killStreak % 3 == 0) {
            killer.earnBonus(BonusType.RAMPAGE);

            long rampageReward = BonusType.RAMPAGE.getReward();
            Messages.send(killer.getPlayer(),
                    "<gold><bold>RAMPAGE!</bold> <yellow>+$" + rampageReward + "</yellow></gold>");
        }
    }
}
