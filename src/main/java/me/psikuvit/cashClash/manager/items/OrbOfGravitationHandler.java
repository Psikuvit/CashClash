package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orb of Gravitation: thrown as a Snowball tagged with its owner and a hits budget.
 * Enemy players in flight can shoot it with fully-charged arrows to destroy it;
 * it also detonates on natural impact or on a manual right-click while live.
 * Detonation pulls all enemies within range toward its centre, applying Slowness I
 * and drawing shrinking colour-lerped beams. The item is only consumed once the orb
 * fully resolves.
 */
public class OrbOfGravitationHandler extends CustomItemHandler {

    // Orb of Gravitation - live orb tracking (Snowball entity UUID -> hits remaining, owner UUID,
    // and the orb's dust-trail task, cancelled when the orb resolves)
    private final Map<UUID, Integer> orbHitsRemaining;
    private final Map<UUID, UUID> orbOwners;
    private final Map<UUID, BukkitTask> orbTrailTasks;

    public OrbOfGravitationHandler(CustomItemManager manager) {
        super(manager);
        this.orbHitsRemaining = new HashMap<>();
        this.orbOwners = new HashMap<>();
        this.orbTrailTasks = new HashMap<>();
    }

    public boolean isOrbEntity(Entity entity) {
        return entity instanceof Snowball && orbHitsRemaining.containsKey(entity.getUniqueId());
    }

    /**
     * @return true if the player has at least one live orb still in flight (right-click again
     * then detonates it manually instead of throwing another)
     */
    public boolean hasLiveOrb(Player player) {
        UUID owner = player.getUniqueId();
        for (UUID stored : orbOwners.values()) {
            if (owner.equals(stored)) return true;
        }
        return false;
    }

    /**
     * Detonates the player's live orb (manual right-click while one is in flight).
     */
    public void activateOrbByOwner(Player player) {
        UUID owner = player.getUniqueId();
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(orbOwners.entrySet())) {
            if (owner.equals(entry.getValue())) {
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity instanceof Snowball orb && !orb.isDead()) {
                    activateOrb(orb);
                }
                return;
            }
        }
    }

    /**
     * Launches the orb as a Snowball tagged with its owner. The item is NOT consumed yet - it is
     * only consumed once the orb fully resolves (destroyed, pulled-and-expired, or detonated).
     */
    public void throwOrbOfGravitation(Player player) {

        Snowball orb = player.launchProjectile(Snowball.class);
        orb.setVelocity(player.getLocation().getDirection().multiply(cfg.getOrbThrowSpeed()));

        PersistentDataContainer pdc = orb.getPersistentDataContainer();
        pdc.set(Keys.ITEM_ID, PersistentDataType.STRING, CustomItem.ORB_OF_GRAVITATION.name());
        pdc.set(Keys.ITEM_OWNER, PersistentDataType.STRING, player.getUniqueId().toString());

        UUID orbUuid = orb.getUniqueId();
        orbHitsRemaining.put(orbUuid, cfg.getOrbHitsToDestroy());
        orbOwners.put(orbUuid, player.getUniqueId());

        BukkitTask trailTask = SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            @Override
            public void run() {
                if (orb.isDead() || !orbHitsRemaining.containsKey(orbUuid)) {
                    cancel();
                    return;
                }
                ParticleUtils.spawnDust(orb.getLocation(), Color.fromRGB(180, 140, 40), 0.8f, 2, 0.1);
            }
        }, 0L, 2L);
        orbTrailTasks.put(orbUuid, trailTask);

        Messages.send(player, "customitem.orb-thrown");
        SoundUtils.play(player, Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.8f);
    }

    /**
     * Detonates a live orb (manual right-click, natural impact, or 4th charged-arrow hit):
     * removes it and pulls all enemies within range toward its centre for the pull duration,
     * applying Slowness I and shrinking light-yellow->red beams as each target closes in.
     */
    public void activateOrb(Snowball orb) {
        UUID orbUuid = orb.getUniqueId();
        if (!orbHitsRemaining.containsKey(orbUuid) || orb.isDead()) return;

        UUID ownerUuid = orbOwners.get(orbUuid);
        cleanupOrbTracking(orbUuid);

        Location center = orb.getLocation().clone();
        orb.remove();

        Player owner = ownerUuid != null ? Bukkit.getPlayer(ownerUuid) : null;
        if (owner != null) {
            consumeOrbItem(owner);
            Messages.send(owner, "customitem.orb-activated");
            SoundUtils.play(owner, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
        }
        SoundUtils.playAt(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.6f);

        double radius = cfg.getOrbPullRadius();
        int durationTicks = cfg.getOrbPullDurationTicks();
        int slownessTicks = cfg.getOrbSlownessDurationSeconds() * 20;

        GameSession session = owner != null ? GameManager.getInstance().getPlayerSession(owner) : null;
        Team team = session != null ? session.getPlayerTeam(owner) : null;

        // Slowness I once + a colour progress marker for the beam lerp
        List<Player> pulled = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player target) || target.equals(owner)) continue;
            if (!target.hasLineOfSight(center)) continue;
            if (team != null) {
                Team targetTeam = session.getPlayerTeam(target);
                if (targetTeam != null && targetTeam.getTeamNumber() == team.getTeamNumber()) continue;
            }
            CashClashPlayer.applyEffect(target, PotionEffectType.SLOWNESS, slownessTicks, 0);
            pulled.add(target);
        }

        final int[] tick = {0};
        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            @Override
            public void run() {
                tick[0]++;
                float progress = Math.min(1.0f, tick[0] / (float) durationTicks);
                Color beamColor = lerpColor(Color.fromRGB(255, 230, 150), Color.fromRGB(200, 40, 40), progress);

                for (Player target : new ArrayList<>(pulled)) {
                    if (!target.isOnline() || target.isDead()) continue;
                    Vector toCenter = center.toVector().subtract(target.getLocation().toVector());
                    if (toCenter.lengthSquared() < 0.25) continue; // arrived
                    target.setVelocity(toCenter.normalize().multiply(0.55));
                    ParticleUtils.beam(center.clone().add(0, 1, 0), target.getLocation().add(0, 1, 0), beamColor, 0.15f, 2);
                }
                ParticleUtils.spawnDust(center.clone().add(0, 1, 0), beamColor, 1.0f, 3, 0.3);

                if (tick[0] >= durationTicks) {
                    cancel();
                }
            }
        }, 0L, 1L);
    }

    /**
     * A fully-charged bow shot hitting a live orb decrements its hits-remaining counter; on the
     * configured final hit the orb shatters (destroyed = fully resolved, so the item is consumed).
     */
    public void handleOrbHitByChargedArrow(Arrow arrow, Snowball orb) {
        UUID orbUuid = orb.getUniqueId();
        if (!orbHitsRemaining.containsKey(orbUuid)) return;
        if (arrow.getPersistentDataContainer().get(Keys.FULLY_CHARGED_ARROW, PersistentDataType.BYTE) == null) return;

        int left = orbHitsRemaining.get(orbUuid) - 1;
        if (left <= 0) {
            Location loc = orb.getLocation();
            UUID ownerUuid = orbOwners.get(orbUuid);
            cleanupOrbTracking(orbUuid);
            orb.remove();
            arrow.remove();
            Player owner = ownerUuid != null ? Bukkit.getPlayer(ownerUuid) : null;
            if (owner != null) {
                consumeOrbItem(owner);
                Messages.send(owner, "customitem.orb-destroyed");
            }
            SoundUtils.playAt(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.2f);
        } else {
            orbHitsRemaining.put(orbUuid, left);
        }
    }

    private void cleanupOrbTracking(UUID orbUuid) {
        orbHitsRemaining.remove(orbUuid);
        orbOwners.remove(orbUuid);
        BukkitTask trail = orbTrailTasks.remove(orbUuid);
        if (trail != null) trail.cancel();
    }

    /**
     * Consumes a single orb item wherever it sits in the owner's inventory (they may have
     * switched items since throwing, and the item is not consumed until the orb resolves).
     */
    private void consumeOrbItem(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (PDCDetection.getCustomItem(main) == CustomItem.ORB_OF_GRAVITATION) {
            consumeItem(player, main);
            return;
        }
        for (ItemStack slot : player.getInventory().getContents()) {
            if (slot != null && PDCDetection.getCustomItem(slot) == CustomItem.ORB_OF_GRAVITATION) {
                slot.setAmount(slot.getAmount() - 1);
                return;
            }
        }
    }

    private Color lerpColor(Color from, Color to, float t) {
        return Color.fromRGB(
                (int) (from.getRed() + (to.getRed() - from.getRed()) * t),
                (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * t));
    }

    @Override
    public void cleanup() {
        orbTrailTasks.values().forEach(BukkitTask::cancel);
        orbTrailTasks.clear();
        orbHitsRemaining.keySet().forEach(uuid -> {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) entity.remove();
        });
        orbHitsRemaining.clear();
        orbOwners.clear();
    }
}
