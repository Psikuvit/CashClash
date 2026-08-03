package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Electric Eel Sword - chain lightning on charged hits and a blink-dash teleport.
 */
public class ElectricEelHandler extends MythicItemHandler {

    public ElectricEelHandler(MythicItemManager manager) {
        super(manager);
    }

    /**
     * Electric Eel Sword chain damage.
     * Fully charged hits damage nearby enemies in 5 block radius for 0.5 hearts.
     * 1 second cooldown.
     */
    public void handleElectricEelChain(Player attacker, LivingEntity victim) {
        UUID uuid = attacker.getUniqueId();

        Messages.debug(attacker, "ELECTRIC_EEL: Chain damage check");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.ELECTRIC_EEL_CHAIN)) {
            Messages.debug(attacker, "ELECTRIC_EEL: Chain on cooldown");
            return;
        }
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.ELECTRIC_EEL_CHAIN, cfg.getEelChainCooldown());

        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
        if (session == null) {
            Messages.debug(attacker, "ELECTRIC_EEL: No session");
            return;
        }

        Team attackerTeam = session.getPlayerTeam(attacker);
        Location victimLoc = victim.getLocation();
        int radius = cfg.getEelChainRadius();
        int chainCount = 0;

        // Chain damage to nearby enemies
        for (Entity entity : victim.getWorld().getNearbyEntities(victimLoc, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(attacker) || target.equals(victim)) continue;

            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam != null && attackerTeam != null &&
                targetTeam.getTeamNumber() == attackerTeam.getTeamNumber()) continue;

            target.damage(cfg.getEelChainDamage(), attacker);
            chainCount++;

            // Lightning spark effect
            ParticleUtils.electricSpark(target.getLocation().add(0, 1, 0), 15, 0.3);
        }

        Messages.debug(attacker, "ELECTRIC_EEL: Chained to " + chainCount + " enemies, radius: " + radius + ", damage: " + cfg.getEelChainDamage());
        SoundUtils.playAt(victimLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.5f);
    }

    /**
     * Electric Eel Sword teleport.
     * Zaps player 4 blocks forward but not through walls.
     * 15 second cooldown.
     */
    public void useElectricEelTeleport(Player player) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "ELECTRIC_EEL: Teleport ability triggered");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.ELECTRIC_EEL_LIGHTNING)) {
            Messages.debug(player, "ELECTRIC_EEL: Teleport on cooldown - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.ELECTRIC_EEL_LIGHTNING) + "s");
            Messages.send(player, "mythic.electric-eel-teleport-cooldown", "{cooldown_seconds}", String.valueOf(cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.ELECTRIC_EEL_LIGHTNING)));
            return;
        }

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.ELECTRIC_EEL_LIGHTNING, cfg.getEelTeleportCooldown());

        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();
        World world = player.getWorld();

        double distance = cfg.getEelTeleportDistance(); // treat as push strength

        RayTraceResult result = world.rayTraceBlocks(start, direction, distance, FluidCollisionMode.NEVER, true);

        double pushStrength = distance; // you can tune this

        if (result != null && result.getHitBlock() != null) {
            // Stop before the wall
            double hitDistance = result.getHitPosition().distance(start.toVector());
            pushStrength = Math.max(0.1, hitDistance - 0.5);
            Messages.debug(player, "ELECTRIC_EEL: Hit wall, reduced push strength");
        } else {
            Messages.debug(player, "ELECTRIC_EEL: Full push strength");
        }

        Vector velocity = direction.multiply(pushStrength);
        player.setVelocity(velocity);

        // Effects at destination
        ParticleUtils.electricSpark(player.getLocation().add(0, 1, 0), 30, 0.5);

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.ELECTRIC_EEL_LIGHTNING, cfg.getEelTeleportCooldown());
        Messages.debug(player, "ELECTRIC_EEL: Teleported! Distance: " + distance + ", Cooldown: " + cfg.getEelTeleportCooldown() + "s");
        Messages.send(player, "mythic.electric-eel-zap");
    }

    @Override
    public void cleanup() {
        // No per-handler state
    }
}
