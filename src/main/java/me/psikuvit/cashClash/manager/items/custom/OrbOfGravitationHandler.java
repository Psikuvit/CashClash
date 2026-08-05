package me.psikuvit.cashClash.manager.items.custom;

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
import me.psikuvit.cashClash.util.items.PDCSetter;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

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
    // the orb's dust-trail task, and the magma-slime ItemDisplay riding it, all cancelled/removed
    // together when the orb resolves)
    private final Map<UUID, Integer> orbHitsRemaining;
    private final Map<UUID, UUID> orbOwners;
    private final Map<UUID, BukkitTask> orbTrailTasks;
    private final Map<UUID, ItemDisplay> orbDisplays;

    public OrbOfGravitationHandler(CustomItemManager manager) {
        super(manager);
        this.orbHitsRemaining = new HashMap<>();
        this.orbOwners = new HashMap<>();
        this.orbTrailTasks = new HashMap<>();
        this.orbDisplays = new HashMap<>();
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
        Vector direction = player.getLocation().getDirection();
        orb.setVelocity(direction.multiply(cfg.getOrbThrowSpeed()));
        // No gravity - the orb should slowly cruise straight along where the player aimed
        // instead of arcing/dropping like a thrown snowball.
        orb.setGravity(false);
        // Hidden entirely - it stays the real projectile/collision entity, but the magma-cream
        // ItemDisplay below is the only thing that should actually be visible flying through
        // the air.
        orb.setVisibleByDefault(false);

        PDCSetter.of(orb)
                .set(Keys.ITEM_ID, PersistentDataType.STRING, CustomItem.ORB_OF_GRAVITATION.name())
                .set(Keys.ITEM_OWNER, PersistentDataType.STRING, player.getUniqueId().toString())
                .apply();

        UUID orbUuid = orb.getUniqueId();
        orbHitsRemaining.put(orbUuid, cfg.getOrbHitsToDestroy());
        orbOwners.put(orbUuid, player.getUniqueId());

        // Cosmetic "magma slime" riding the orb - a magma-cream ItemDisplay closely follows the
        // invisible Snowball, which stays the real projectile/collision entity underneath so all
        // existing hit-detection (ProjectileHitEvent, charged-arrow hits) keeps working unchanged.
        ItemDisplay orbDisplay = orb.getWorld().spawn(orb.getLocation(), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.MAGMA_CREAM));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            Transformation t = d.getTransformation();
            d.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(), new Vector3f(1.4f, 1.4f, 1.4f), t.getRightRotation()));
        });
        orbDisplays.put(orbUuid, orbDisplay);

        BukkitTask trailTask = SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            private float spin;

            @Override
            public void run() {
                if (orb.isDead() || !orbHitsRemaining.containsKey(orbUuid)) {
                    cancel();
                    return;
                }
                // Re-affirm velocity every tick so gravity/drag can't slowly bleed off the
                // straight-line cruise - keeps it "slowly flying where the player aimed".
                orb.setVelocity(direction.clone().multiply(cfg.getOrbThrowSpeed()));

                spin += 8f;
                orbDisplay.teleport(orb.getLocation());
                orbDisplay.setRotation(spin, 0f);
                ParticleUtils.spawnDust(orb.getLocation(), Color.fromRGB(180, 140, 40), 0.8f, 2, 0.1);
            }
        }, 0L, 1L);
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

        Color pullYellow = Color.fromRGB(255, 220, 60);
        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                tick++;

                for (Player target : new ArrayList<>(pulled)) {
                    if (!target.isOnline() || target.isDead()) continue;
                    Vector toCenter = center.toVector().subtract(target.getLocation().toVector());

                    if (toCenter.lengthSquared() < 0.25) {
                        // Arrived - kill residual momentum instead of letting it overshoot and get
                        // yanked back and forth, and just barely hover/sway in place. No beam once
                        // fully pulled in, matching the line fading out as they arrive.
                        Vector hover = target.getVelocity().multiply(0.2);
                        double phase = (tick + target.getEntityId()) * 0.3;
                        hover.setX(hover.getX() + Math.sin(phase) * 0.03);
                        hover.setZ(hover.getZ() + Math.cos(phase) * 0.03);
                        hover.setY(Math.min(hover.getY(), 0));
                        target.setVelocity(hover);
                        continue;
                    }

                    // Slower pull than before, and the beam stays yellow throughout - only its
                    // length (naturally shrinking as the target closes in) signals progress.
                    target.setVelocity(toCenter.normalize().multiply(0.3));
                    ParticleUtils.beam(center.clone().add(0, 1, 0), target.getLocation().add(0, 1, 0), pullYellow, 0.15f, 2);
                }
                ParticleUtils.spawnDust(center.clone().add(0, 1, 0), pullYellow, 1.0f, 3, 0.3);

                if (tick >= durationTicks) {
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
        ItemDisplay display = orbDisplays.remove(orbUuid);
        if (display != null && !display.isDead()) display.remove();
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

    @Override
    public void cleanup() {
        orbTrailTasks.values().forEach(BukkitTask::cancel);
        orbTrailTasks.clear();
        orbDisplays.values().forEach(d -> {
            if (!d.isDead()) d.remove();
        });
        orbDisplays.clear();
        orbHitsRemaining.keySet().forEach(uuid -> {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) entity.remove();
        });
        orbHitsRemaining.clear();
        orbOwners.clear();
    }
}
