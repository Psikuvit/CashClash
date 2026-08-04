package me.psikuvit.cashClash.manager.items.weapon;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.WeaponItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cash Blaster: a sneak-right-click supercharge toggle, and - while supercharged -
 * a fully-charged shot that consumes 4 arrows and launches a Profit Vortex arrow.
 * The vortex slows enemies inside it and credits the shooter's team when one of
 * them dies inside it; a spectral-vortex variant glows enemies first.
 */
public class CashBlasterHandler extends WeaponItemHandler {

    private final Map<UUID, Boolean> cashBlasterSupercharged;

    private final Map<UUID, Location> playersInProfitVortex;
    private final Map<Location, UUID> profitVortexOwners;
    private final Map<UUID, Location> playersKilledInProfitVortex;
    private final Map<Location, Boolean> spectralProfitVortices;
    private final Map<Location, Set<UUID>> spectralVortexMarkedPlayers;

    public CashBlasterHandler(WeaponItemManager manager) {
        super(manager);
        this.cashBlasterSupercharged = new HashMap<>();
        this.playersInProfitVortex = new HashMap<>();
        this.profitVortexOwners = new HashMap<>();
        this.playersKilledInProfitVortex = new HashMap<>();
        this.spectralProfitVortices = new HashMap<>();
        this.spectralVortexMarkedPlayers = new HashMap<>();
    }

    /**
     * Cash Blaster supercharge toggle: sneak + right-click while holding the Cash Blaster.
     * Toggles the supercharged state with a short cooldown and feedback sound.
     */
    public void onCashBlasterToggle(Player player) {
        if (!hasCashBlasterInHand(player)) return;

        UUID uuid = player.getUniqueId();
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.CASH_BLASTER_TOGGLE)) {
            return;
        }

        boolean enabled = !cashBlasterSupercharged.getOrDefault(uuid, false);
        cashBlasterSupercharged.put(uuid, enabled);
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.CASH_BLASTER_TOGGLE, 1);

        if (enabled) {
            Messages.send(player, "customitem.cash-blaster-supercharged");
            SoundUtils.play(player, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        } else {
            Messages.send(player, "customitem.cash-blaster-supercharge-disabled");
            SoundUtils.play(player, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
        }
    }

    public boolean hasCashBlasterInHand(Player player) {
        return PDCDetection.getWeapon(player.getInventory().getItemInMainHand()) == WeaponItem.CASH_BLASTER;
    }

    /**
     * Handle a Cash Blaster shot. Normal mode: +10% arrow damage.
     * Supercharged mode: requires a fully charged shot, consumes 4 arrows and launches a
     * Profit Vortex arrow that spawns a damaging/levitating vortex on impact.
     */
    public void onCashBlasterShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!hasCashBlasterInHand(player)) return;
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;

        // Normal mode: +10% arrow damage with no vanilla critical, keeping the bonus flat
        if (!cashBlasterSupercharged.getOrDefault(player.getUniqueId(), false)) {
            arrow.setDamage(arrow.getDamage() * 1.10);
            arrow.setCritical(false);
            startCashBlasterTrail(arrow, false, false);
            return;
        }

        event.setCancelled(true);
        if (event.getForce() < 0.99f) {
            Messages.send(player, "customitem.cash-blaster-vortex-full-charge");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (cooldownManager.isOnCooldown(player.getUniqueId(), CooldownManager.Keys.CASH_BLASTER_VORTEX)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(player.getUniqueId(), CooldownManager.Keys.CASH_BLASTER_VORTEX);
            Messages.send(player, "customitem.cash-blaster-vortex-cooldown", "remaining", String.valueOf(remaining));
            return;
        }

        if (getProfitVortexArrowCount(player) < 4) {
            Messages.send(player, "customitem.cash-blaster-vortex-need-arrows");
            return;
        }

        SchedulerUtils.runTaskLater(() -> removeProfitVortexArrows(player, 4), 1L);
        boolean firedArrowWasSpectral = arrow instanceof SpectralArrow;
        int spectralArrowsConsumed = countSpectralArrowsConsumed(player, 4) + (firedArrowWasSpectral ? 1 : 0);
        Arrow vortexArrow = player.launchProjectile(Arrow.class);
        vortexArrow.setDamage(vortexArrow.getDamage() * 1.10);
        vortexArrow.setCritical(false);
        vortexArrow.getPersistentDataContainer().set(Keys.PROFIT_VORTEX_ARROW, PersistentDataType.BYTE, (byte) 1);
        vortexArrow.getPersistentDataContainer().set(Keys.PROFIT_VORTEX_GLOW_SECONDS, PersistentDataType.INTEGER, spectralArrowsConsumed * 2);

        startCashBlasterTrail(vortexArrow, true, spectralArrowsConsumed > 0);
        cooldownManager.setCooldownSeconds(player.getUniqueId(), CooldownManager.Keys.CASH_BLASTER_VORTEX, cfg.getCashBlasterVortexCooldown());
    }

    /**
     * A tagged Profit Vortex arrow hit the ground/wall: spawn the vortex at the impact point.
     * Runs a particle spiral for the vortex duration, slowing and crediting enemies inside it.
     */
    public void onProfitVortexArrowHit(Arrow arrow) {
        if (!arrow.getPersistentDataContainer().has(Keys.PROFIT_VORTEX_ARROW, PersistentDataType.BYTE)) {
            return;
        }
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        Location vortexLocation = arrow.getLocation();
        SoundUtils.playAt(vortexLocation, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 1.5f);

        profitVortexOwners.put(vortexLocation, shooter.getUniqueId());

        int glowSeconds = arrow.getPersistentDataContainer().getOrDefault(
                Keys.PROFIT_VORTEX_GLOW_SECONDS, PersistentDataType.INTEGER, 0);
        boolean isSpectral = glowSeconds > 0;
        spectralProfitVortices.put(vortexLocation, isSpectral);
        spectralVortexMarkedPlayers.put(vortexLocation, new HashSet<>());

        arrow.remove();

        double radius = cfg.getCashBlasterVortexRadius();
        int durationTicks = cfg.getCashBlasterVortexDurationTicks();

        BukkitTask vortexTask = SchedulerUtils.runTaskTimer(() -> {
            for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 24) {
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;

                Location particleLocation = vortexLocation.clone().add(x, 0.2, z);

                if (isSpectral && ((int) (angle * 10) % 6 == 0)) {
                    ParticleUtils.spawnDust(particleLocation, Color.fromRGB(255, 255, 255), 1.5f, 5, 0.05);
                    ParticleUtils.spawnDust(particleLocation, Color.fromRGB(255, 215, 0), 1.5f, 4, 0.05);
                } else {
                    ParticleUtils.spawnDust(particleLocation, Color.fromRGB(120, 255, 120), 1.2f, 3, 0.05);
                }
                ParticleUtils.spawnDust(particleLocation, Color.fromRGB(0, 120, 0), 1.2f, 2, 0.05);
            }

            for (Player target : vortexLocation.getWorld().getPlayers()) {
                if (target.getLocation().distanceSquared(vortexLocation) > radius * radius) continue;
                if (target.getUniqueId().equals(profitVortexOwners.get(vortexLocation))) continue;

                GameSession session = GameManager.getInstance().getPlayerSession(shooter);
                if (session != null) {
                    Team ownerTeam = session.getPlayerTeam(shooter);
                    Team targetTeam = session.getPlayerTeam(target);
                    if (ownerTeam != null && ownerTeam.equals(targetTeam)) continue;
                }

                if (isSpectral && !spectralVortexMarkedPlayers.get(vortexLocation).contains(target.getUniqueId())) {
                    CashClashPlayer.applyEffect(target, PotionEffectType.GLOWING, (glowSeconds + 1) * 20, 0, false, true);
                    spectralVortexMarkedPlayers.get(vortexLocation).add(target.getUniqueId());
                }
                CashClashPlayer.applyEffect(target, PotionEffectType.SLOWNESS, 20, 0);
                playersKilledInProfitVortex.put(target.getUniqueId(), vortexLocation);
            }
        }, 0L, 5L);

        SchedulerUtils.runTaskLater(() -> {
            vortexTask.cancel();
            profitVortexOwners.remove(vortexLocation);
            spectralVortexMarkedPlayers.remove(vortexLocation);
            playersKilledInProfitVortex.entrySet().removeIf(entry -> entry.getValue().equals(vortexLocation));
            SoundUtils.playAt(vortexLocation, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.5f, 1.2f);
        }, durationTicks);
    }

    /**
     * Player died while inside a Profit Vortex: award 400 coins to the vortex owner's team.
     */
    public void onProfitVortexDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!playersKilledInProfitVortex.containsKey(victim.getUniqueId())) return;

        Player killer = victim.getKiller();
        if (killer == null) return;

        Location vortexLocation = playersKilledInProfitVortex.get(victim.getUniqueId());
        if (vortexLocation.distance(victim.getLocation()) > 5) return;

        GameSession session = GameManager.getInstance().getPlayerSession(killer);
        if (session == null) return;

        Team killerTeam = session.getPlayerTeam(killer);
        if (killerTeam == null) return;

        int coins = cfg.getCashBlasterVortexKillReward();
        for (UUID uuid : killerTeam.getPlayers()) {
            CashClashPlayer ccp = session.getCashClashPlayer(uuid);
            if (ccp == null) continue;
            ccp.addCoins(coins);

            Player teammate = Bukkit.getPlayer(uuid);
            if (teammate == null || !teammate.isOnline()) continue;

            SoundUtils.play(teammate, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.5f);
            Location center = teammate.getLocation().clone().add(0, 0.6, 0);
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

        playersKilledInProfitVortex.remove(victim.getUniqueId());
    }

    /**
     * Counts the total arrows (normal + spectral) in the player's inventory.
     */
    private int getProfitVortexArrowCount(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() == Material.ARROW || item.getType() == Material.SPECTRAL_ARROW) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Removes arrows from the player's inventory (normal arrows first, then spectral).
     */
    private void removeProfitVortexArrows(Player player, int amount) {
        int needed = amount;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() != Material.ARROW) continue;

            int itemAmount = item.getAmount();
            if (itemAmount <= needed) {
                needed -= itemAmount;
                item.setAmount(0);
            } else {
                item.setAmount(itemAmount - needed);
                needed = 0;
            }
            if (needed <= 0) return;
        }

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() != Material.SPECTRAL_ARROW) continue;

            int itemAmount = item.getAmount();
            if (itemAmount <= needed) {
                needed -= itemAmount;
                item.setAmount(0);
            } else {
                item.setAmount(itemAmount - needed);
                needed = 0;
            }
            if (needed <= 0) return;
        }
    }

    /**
     * Counts how many of the arrows consumed for a Profit Vortex are spectral. Each spectral
     * arrow (including the one actually fired) extends the glow applied to caught enemies.
     */
    private int countSpectralArrowsConsumed(Player player, int arrowsNeeded) {
        int remaining = arrowsNeeded;
        int spectralUsed = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() != Material.ARROW && item.getType() != Material.SPECTRAL_ARROW) continue;
            int taken = Math.min(item.getAmount(), remaining);
            if (item.getType() == Material.SPECTRAL_ARROW) {
                spectralUsed += taken;
            }
            remaining -= taken;
            if (remaining <= 0) break;
        }
        return spectralUsed;
    }

    /**
     * Trails a Cash Blaster arrow: light-green dust in normal mode, a rotating dark-green pair
     * (plus white/yellow sparkle when spectral) in Profit Vortex mode. Self-cancelling.
     */
    private void startCashBlasterTrail(AbstractArrow arrow, boolean vortex, boolean spectral) {
        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            double rotation = 0;

            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isOnGround() || arrow.isInBlock()) {
                    cancel();
                    return;
                }
                Location center = arrow.getLocation();
                if (!vortex) {
                    ParticleUtils.spawnDust(center, Color.fromRGB(120, 255, 120), 1.1f, 2, 0);
                    return;
                }
                double radius = 0.18;
                double x1 = Math.cos(rotation) * radius;
                double y1 = Math.sin(rotation) * radius;
                double x2 = Math.cos(rotation + Math.PI) * radius;
                double y2 = Math.sin(rotation + Math.PI) * radius;
                ParticleUtils.spawnDust(center.clone().add(x1, y1, 0), Color.fromRGB(30, 170, 30), 1.2f, 1, 0);
                ParticleUtils.spawnDust(center.clone().add(x2, y2, 0), Color.fromRGB(15, 110, 15), 1.2f, 1, 0);
                if (spectral) {
                    ParticleUtils.spawnDust(center, Color.WHITE, 1.0f, 1, 0.05);
                    ParticleUtils.spawnDust(center, Color.fromRGB(255, 230, 70), 1.0f, 1, 0.08);
                }
                rotation += 0.55;
            }
        }, 0L, 1L);
    }

    @Override
    public void cleanup() {
        cashBlasterSupercharged.clear();
        playersInProfitVortex.clear();
        profitVortexOwners.clear();
        playersKilledInProfitVortex.clear();
        spectralProfitVortices.clear();
        spectralVortexMarkedPlayers.clear();
    }
}
