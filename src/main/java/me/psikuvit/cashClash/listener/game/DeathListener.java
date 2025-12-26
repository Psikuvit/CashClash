package me.psikuvit.cashClash.listener.game;

import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.CashQuakeManager;
import me.psikuvit.cashClash.manager.game.EconomyManager;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.CustomArmorManager;
import me.psikuvit.cashClash.manager.player.BonusManager;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
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
        Messages.debug(player, "GAME", "Player died in session " + session.getSessionId());

        CashClashPlayer victim = session.getCashClashPlayer(player.getUniqueId());
        if (victim == null) return;

        // Prevent item drops and experience loss
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);

        PlayerDataManager.getInstance().incDeaths(player.getUniqueId());

        // Handle victim death logic
        victim.handleDeath();
        session.getCurrentRoundData().removeLife(player.getUniqueId());

        BonusManager bonusManager = session.getBonusManager();
        if (bonusManager != null) {
            bonusManager.onDeath(player.getUniqueId());
        }

        Player killer = player.getKiller();
        if (killer != null) {
            Messages.debug(player, "GAME", "Killed by " + killer.getName());
            handleKillerRewards(session, killer, victim);
        }

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

        CashQuakeManager cashQuakeManager = session.getCashQuakeManager();
        if (cashQuakeManager != null && cashQuakeManager.isLifeStealActive()) {
            cashQuakeManager.onLifeStealKill(killer);
        }
    }

    /**
     * Handle player who is permanently out of lives (spectator for remainder of round)
     */
    private void handlePermanentSpectator(Player player, Location spectatorLocation) {
        Messages.send(player, "<red>You are out of lives for this round!</red>");
        Messages.debug(player, "GAME", "Lives exhausted. Setting to permanent spectator for round.");

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
        Messages.debug(player, "GAME", "Temporary spectator. Respawn in " + respawnDelaySec + "s");

        SchedulerUtils.runTask(() -> {
            player.spigot().respawn();
            player.teleport(spectatorLocation);
            player.setGameMode(GameMode.SPECTATOR);
        });

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

        // Delegate bonus logic (FIRST_BLOOD, RAMPAGE, COMEBACK_KID) to BonusManager
        BonusManager bonusManager = session.getBonusManager();
        if (bonusManager != null) {
            bonusManager.onKill(killer.getUuid());
        }

        long killReward = EconomyManager.getKillReward(session, killer);
        long stolenAmount = EconomyManager.calculateStealAmount(session, victim);

        double investorMultiplier = armorManager.getInvestorMultiplier(killer.getPlayer());

        if (killReward > 0) {
            long adjustedReward = Math.round(killReward * investorMultiplier);
            killer.addCoins(adjustedReward);

            if (adjustedReward != killReward) {
                Messages.send(killer.getPlayer(),
                        "<green>Investor bonus applied: x" + String.format("%.2f", investorMultiplier) + "</green>"
                );
            } else {
                Messages.send(killer.getPlayer(),
                        "<yellow>+$" + adjustedReward + "</yellow> for the kill!"
                );
            }
        }

        if (stolenAmount > 0) {
            long adjustedStolen = Math.round(stolenAmount * investorMultiplier);
            victim.deductCoins(adjustedStolen);
            killer.addCoins(adjustedStolen);

            Messages.send(killer.getPlayer(),
                    "<yellow>+$" + adjustedStolen + "</yellow> stolen from <gray>" + victim.getPlayer().getName() + "</gray>");
        }
    }
}
