package me.psikuvit.cashClash.manager.items;


import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomWeapon;
import me.psikuvit.cashClash.util.CooldownManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.entity.Arrow;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;


import java.util.*;


import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.util.Vector;


public class CustomWeaponManager implements Listener {


    private final CooldownManager cooldownManager = CooldownManager.getInstance();
    private static final CustomWeaponManager instance = new CustomWeaponManager();
    private final CustomArmorManager armorManager = CustomArmorManager.getInstance();
    private final Map<UUID, Location> playersInProfitVortex = new HashMap<>();
    private final Map<Location, UUID> profitVortexOwners = new HashMap<>();
    private final Map<UUID, Location> playersKilledInProfitVortex = new HashMap<>();
    private final Map<Location, Boolean> spectralProfitVortices = new HashMap<>();
    private final Map<Location, Set<UUID>> spectralVortexMarkedPlayers = new HashMap<>();


    private CustomWeaponManager() {
        Bukkit.getPluginManager().registerEvents(
                this,
                CashClashPlugin.getInstance()
        );
    }


    public static CustomWeaponManager getInstance() {
        return instance;
    }


    private final Map<UUID, Boolean> cashBlasterSupercharged = new HashMap<>();
    private final NamespacedKey profitVortexArrow =
            new NamespacedKey(CashClashPlugin.getInstance(), "profit_vortex_arrow");


        public void onCashBlasterToggle(PlayerInteractEvent event) {

        Player player = event.getPlayer();
        armorManager.lockMythicShift(player);

        if (!player.isSneaking()) return;

        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!CustomWeapon.hasCashBlaster(player)) return;

        UUID uuid = player.getUniqueId();


        if (cooldownManager.isOnCooldown(
                uuid,
                CooldownManager.Keys.CASH_BLASTER_TOGGLE
        )) {
            return;
        }


        boolean enabled = !cashBlasterSupercharged.getOrDefault(uuid, false);
        cashBlasterSupercharged.put(uuid, enabled);
        cooldownManager.setCooldownSeconds(
                uuid,
                CooldownManager.Keys.CASH_BLASTER_TOGGLE,
                1
        );
        if (enabled) {


            player.sendMessage("§aCash Blaster supercharged!");


            player.playSound(
                    player.getLocation(),
                    Sound.BLOCK_BEACON_ACTIVATE,
                    1.0f,
                    1.0f
            );
        } else {


            player.sendMessage("§cCash Blaster supercharge disabled!");


            player.playSound(
                    player.getLocation(),
                    Sound.BLOCK_BEACON_DEACTIVATE,
                    1.0f,
                    1.0f
            );
        }
    }


    public void onCashBlasterShoot(EntityShootBowEvent event) {


        if (!(event.getEntity() instanceof Player player)) return;


        if (!CustomWeapon.hasCashBlaster(player)) return;


        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;


        // Normal mode: +10% arrow damage
        if (!cashBlasterSupercharged.getOrDefault(
                player.getUniqueId(),
                false
        )) {
            arrow.setDamage(arrow.getDamage() * 1.10);
            return;
        }


        event.setCancelled(true);
        if (event.getForce() < 0.99f) {


            event.setCancelled(true);
            player.sendMessage("§cYou must fully charge the bow!");
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_VILLAGER_NO,
                    1.0f,
                    1.0f
            );
            return;
        }


        if (cooldownManager.isOnCooldown(
                player.getUniqueId(),
                CooldownManager.Keys.CASH_BLASTER_VORTEX
        )) {


            long remaining = cooldownManager.getRemainingCooldownSeconds(
                    player.getUniqueId(),
                    CooldownManager.Keys.CASH_BLASTER_VORTEX
            );
            player.sendMessage(
                    "§cProfit Vortex is on cooldown for " + remaining + "s!"
            );
            return;
        }


        if (getProfitVortexArrowCount(player) < 4) {


            player.sendMessage("§cYou need 5 arrows to use Profit Vortex!");
            return;


        }


        Bukkit.getScheduler().runTaskLater(
                CashClashPlugin.getInstance(),
                () -> removeProfitVortexArrows(player, 4),
                1L
        );
        boolean spectralVortex = isSpectralProfitVortex(player);
        Arrow vortexArrow = player.launchProjectile(Arrow.class);
        vortexArrow.setDamage(vortexArrow.getDamage() * 1.10);
        vortexArrow.getPersistentDataContainer().set(
                profitVortexArrow,
                PersistentDataType.BYTE,
                (byte) 1
        );


        vortexArrow.getPersistentDataContainer().set(
                new NamespacedKey(CashClashPlugin.getInstance(), "spectral_vortex"),
                PersistentDataType.BYTE,
                spectralVortex ? (byte) 1 : (byte) 0
        );


        cooldownManager.setCooldownSeconds(
                player.getUniqueId(),
                CooldownManager.Keys.CASH_BLASTER_VORTEX,
                10
        );


    }
    public void onProfitVortexArrowHit(ProjectileHitEvent event) {


        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!arrow.getPersistentDataContainer().has(
                profitVortexArrow,
                PersistentDataType.BYTE
        )) return;
        Location vortexLocation = arrow.getLocation();


        vortexLocation.getWorld().playSound(
                vortexLocation,
                Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,
                1.5f,
                1.5f
        );


        if (!(arrow.getShooter() instanceof Player shooter)) return;


        profitVortexOwners.put(
                vortexLocation,
                shooter.getUniqueId()
        );


        boolean isSpectral = arrow.getPersistentDataContainer().getOrDefault(
                new NamespacedKey(CashClashPlugin.getInstance(), "spectral_vortex"),
                PersistentDataType.BYTE,
                (byte) 0
        ) == (byte) 1;


        spectralProfitVortices.put(
                vortexLocation,
                isSpectral
        );


        spectralVortexMarkedPlayers.put(
                vortexLocation,
                new HashSet<>()
        );


        arrow.remove();
        BukkitTask vortexTask = Bukkit.getScheduler().runTaskTimer(
                CashClashPlugin.getInstance(),
                () -> {


                    for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 24) {


                        double x = Math.cos(angle) * 3.0;
                        double z = Math.sin(angle) * 3.0;


                        Location particleLocation = vortexLocation.clone()
                                .add(x, 0.2, z);


                        if (isSpectral && ((int)(angle * 10) % 6 == 0)) {


                            vortexLocation.getWorld().spawnParticle(
                                    Particle.DUST,
                                    particleLocation,
                                    5,
                                    0.05,
                                    0.05,
                                    0.05,
                                    new Particle.DustOptions(
                                            Color.fromRGB(255, 255, 255),
                                            1.5f
                                    )
                            );
                            vortexLocation.getWorld().spawnParticle(
                                    Particle.DUST,
                                    particleLocation,
                                    4,
                                    0.05,
                                    0.05,
                                    0.05,
                                    new Particle.DustOptions(
                                            Color.fromRGB(255, 215, 0),
                                            1.5f
                                    )
                            );
                        } else {
                            vortexLocation.getWorld().spawnParticle(
                                    Particle.DUST,
                                    particleLocation,
                                    3,
                                    0.05,
                                    0.05,
                                    0.05,
                                    new Particle.DustOptions(
                                            Color.fromRGB(120, 255, 120),
                                            1.2f
                                    )
                            );


                        }
                        vortexLocation.getWorld().spawnParticle(
                                Particle.DUST,
                                particleLocation,
                                2,
                                0.05,
                                0.05,
                                0.05,
                                new Particle.DustOptions(
                                        Color.fromRGB(0, 120, 0),
                                        1.2f
                                )
                        );
                    }
                    for (Player target : vortexLocation.getWorld().getPlayers()) {
                        if (target.getLocation().distanceSquared(vortexLocation) <= 12.25) {
                            if (!target.getUniqueId().equals(
                                    profitVortexOwners.get(vortexLocation)
                            )) {
                                if (spectralProfitVortices.getOrDefault(
                                        vortexLocation,
                                        false
                                ) && !spectralVortexMarkedPlayers
                                        .get(vortexLocation)
                                        .contains(target.getUniqueId())) {
                                    target.addPotionEffect(
                                            new PotionEffect(
                                                    PotionEffectType.GLOWING,
                                                    200,
                                                    0,
                                                    false,
                                                    true
                                            )
                                    );
                                    spectralVortexMarkedPlayers
                                            .get(vortexLocation)
                                            .add(target.getUniqueId());
                                }
                                target.addPotionEffect(
                                        new PotionEffect(
                                                PotionEffectType.SLOWNESS,
                                                20,
                                                0,
                                                false,
                                                false
                                        )
                                );
                                playersKilledInProfitVortex.put(
                                        target.getUniqueId(),
                                        vortexLocation
                                );
                            }
                        }
                    }
                },
                0L,
                5L
        );
        Bukkit.getScheduler().runTaskLater(
                CashClashPlugin.getInstance(),
                () -> {


                    vortexTask.cancel();
                    profitVortexOwners.remove(vortexLocation);
                    spectralVortexMarkedPlayers.remove(vortexLocation);
                    playersKilledInProfitVortex.entrySet().removeIf(
                            entry -> entry.getValue().equals(vortexLocation)
                    );
                    vortexLocation.getWorld().playSound(
                            vortexLocation,
                            Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,
                            1.5f,
                            1.2f
                    );
                },
                80L
        );
    }
    public void onProfitVortexDeath(PlayerDeathEvent event) {


        Player victim = event.getEntity();


        if (!playersKilledInProfitVortex.containsKey(victim.getUniqueId())) {
            return;
        }


        Player killer = victim.getKiller();


        if (killer == null) {
            return;
        }


        Location vortexLocation = playersKilledInProfitVortex.get(
                victim.getUniqueId()
        );


        if (vortexLocation.distance(victim.getLocation()) > 5) {
            return;
        }
        GameSession session = GameManager.getInstance().getPlayerSession(killer);


        if (session == null) return;


        Team killerTeam = session.getPlayerTeam(killer);


        if (killerTeam == null) return;


        for (UUID uuid : killerTeam.getPlayers()) {


            CashClashPlayer ccp = session.getCashClashPlayer(uuid);


            if (ccp != null) {
                ccp.addCoins(400);
            }
        }
        playersKilledInProfitVortex.remove(victim.getUniqueId());
    }


    private int getProfitVortexArrowCount(Player player) {


        int count = 0;


        for (ItemStack item : player.getInventory().getContents()) {


            if (item == null) continue;


            if (item.getType() == Material.ARROW ||
                    item.getType() == Material.SPECTRAL_ARROW) {


                count += item.getAmount();
            }
        }


        return count;
    }


    private void removeProfitVortexArrows(Player player, int amount) {


        int needed = amount;


        // Remove normal arrows first
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


        // Then remove spectral arrows
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


    private boolean isSpectralProfitVortex(Player player) {


        int normalArrows = 0;
        int spectralArrows = 0;


        for (ItemStack item : player.getInventory().getContents()) {


            if (item == null) continue;


            if (item.getType() == Material.ARROW) {
                normalArrows += item.getAmount();
            }


            if (item.getType() == Material.SPECTRAL_ARROW) {
                spectralArrows += item.getAmount();
            }
        }
        return normalArrows == 0 && spectralArrows >= 4;
    }

    private final Map<UUID, Boolean> soulKatanaDashing = new HashMap<>();
    private final Map<UUID, Boolean> soulKatanaLeftGround = new HashMap<>();
    private final Map<UUID, Long> soulKatanaHealingReduction = new HashMap<>();


    public Map<UUID, Long> getSoulKatanaHealingReduction() {
        return soulKatanaHealingReduction;
    }


    public void onSoulKatanaUse(PlayerInteractEvent event) {


        Player player = event.getPlayer();


        if (!player.isSneaking()) return;


        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }


        if (!CustomWeapon.hasSoulKatana(player)) return;

        if (CooldownManager.getInstance().isOnCooldown(
                player.getUniqueId(),
                CooldownManager.Keys.SOUL_KATANA
        )) {
            return;
        }


        Vector leap = player.getLocation().getDirection().normalize().multiply(0.6);
        leap.setY(0.4);
        player.setVelocity(leap);


        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_ENDER_DRAGON_FLAP,
                0.8f,
                1.8f
        );

        soulKatanaDashing.put(player.getUniqueId(), true);
        soulKatanaLeftGround.put(player.getUniqueId(), false);


        CooldownManager.getInstance().setCooldownSeconds(
                player.getUniqueId(),
                CooldownManager.Keys.SOUL_KATANA,
                18
        );


    }
    public void onSoulKatanaLand(PlayerMoveEvent event) {


        Player player = event.getPlayer();


        if (!soulKatanaDashing.containsKey(player.getUniqueId())) return;


        if (!player.isOnGround()) {
            soulKatanaLeftGround.put(player.getUniqueId(), true);
            return;
        }


        if (!soulKatanaLeftGround.getOrDefault(player.getUniqueId(), false)) return;


        for (Player target : player.getWorld().getPlayers()) {


            if (target.equals(player)) continue;




            Vector directionToTarget = target.getLocation()
                    .toVector()
                    .subtract(player.getLocation().toVector())
                    .normalize();


            Vector playerDirection = player.getLocation()
                    .getDirection()
                    .normalize();


            double dot = playerDirection.dot(directionToTarget);
            double distance = player.getLocation().distance(target.getLocation());


            if (distance <= 4.0 && dot > 0.3) {
                dealPhantomSliceDamage(target, player);
                target.getWorld().playSound(
                        target.getLocation(),
                        Sound.ENTITY_PLAYER_ATTACK_CRIT,
                        1.0f,
                        1.0f
                );


                soulKatanaHealingReduction.put(
                        target.getUniqueId(),
                        System.currentTimeMillis() + 3000
                );
            }
        }


        Location center = player.getLocation();
        Vector facing = center.getDirection().normalize();


        Particle.DustOptions lightBlue = new Particle.DustOptions(
                Color.fromRGB(120, 220, 255),
                1.5f
        );


        Particle.DustOptions darkBlue = new Particle.DustOptions(
                Color.fromRGB(30, 120, 255),
                1.3f
        );


        for (double angle = -90; angle <= 90; angle += 9) {


            Vector particleDirection = facing.clone();
            particleDirection.rotateAroundY(Math.toRadians(angle));


            Location particleLocation = center.clone()
                    .add(0, 1.8, 0)
                    .add(particleDirection.multiply(2.9));


            player.getWorld().spawnParticle(
                    Particle.DUST,
                    particleLocation,
                    5,
                    0.12,
                    0.12,
                    0.12,
                    0,
                    lightBlue
            );


            player.getWorld().spawnParticle(
                    Particle.DUST,
                    particleLocation,
                    3,
                    0.08,
                    0.08,
                    0.08,
                    0,
                    darkBlue
            );
        }
        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                1.0f,
                2.5f
        );
        soulKatanaDashing.remove(player.getUniqueId());
        soulKatanaLeftGround.remove(player.getUniqueId());
    }
    private void dealPhantomSliceDamage(Player target, Player attacker) {
        double damage = 6.0;
        target.setHealth(Math.max(0, target.getHealth() - damage));
    }
}