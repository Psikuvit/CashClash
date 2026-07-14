package me.psikuvit.cashClash.manager.items;


import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import me.psikuvit.cashClash.manager.items.CustomHealingManager;
import org.bukkit.event.entity.EntityDamageEvent;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Handles runtime behavior for custom armor pieces (effects, cooldowns, detection helpers).
 */
public class CustomArmorManager {


    private static CustomArmorManager instance;


    private final CooldownManager cooldownManager;


    //Tectonic Cap cooldown uses
    private final Map<UUID, Long> tectonicCharge1Cooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tectonicCharge2Cooldown = new ConcurrentHashMap<>();


    // Bunny Shoes tracking
    private final Map<UUID, Boolean> bunnyToggleReady;
    private final Set<UUID> mythicShiftLock = new HashSet<>();


    // Guardian's Vest tracking
    private final Map<UUID, Integer> guardianUsesThisRound;


    // Deathmauler tracking
    private final Map<UUID, Integer> deathmaulerExtraHearts;


    // Dragon Set tracking
    private final Map<UUID, Integer> dragonScales = new HashMap<>();
    private final Map<UUID, Integer> dragonHitCount = new HashMap<>();


    // Bullseye Pants tracking
    private final Map<UUID, Integer> bullseyeHitCount; // Attacker -> current hit count


    // Flamebringer Set tracking
    private final Map<UUID, Integer> flamebringerKills; // Player -> kill count this round
    private final Map<UUID, BukkitTask> flamebringerFireTask; // Player -> fire effect task
    private final Map<UUID, Integer> flamebringerLavaUses; // Player -> lava speed procs this game
    private final Map<UUID, Long> flamebringerSpeedEndTime; // Player -> time when speed effect should end
    private final Map<UUID, BukkitTask> flamebringerTrailTasks = new HashMap<>(); //Stores particles for fire tral
    private final Map<UUID, Long> flamebringerTrailEndTime = new HashMap<>();
    private final Map<UUID, List<Location>> flamebringerTrailLocations = new HashMap<>();


    private final Random random;


    private CustomArmorManager() {
        this.cooldownManager = CooldownManager.getInstance();


        this.bunnyToggleReady = new ConcurrentHashMap<>();


        this.guardianUsesThisRound = new ConcurrentHashMap<>();


        this.deathmaulerExtraHearts = new ConcurrentHashMap<>();


        this.bullseyeHitCount = new ConcurrentHashMap<>();


        this.flamebringerKills = new ConcurrentHashMap<>();
        this.flamebringerFireTask = new ConcurrentHashMap<>();
        this.flamebringerLavaUses = new ConcurrentHashMap<>();
        this.flamebringerSpeedEndTime = new ConcurrentHashMap<>();


        this.random = new Random();
    }


    public static CustomArmorManager getInstance() {
        if (instance == null) {
            instance = new CustomArmorManager();
        }
        return instance;
    }


    private List<CustomArmorItem> getEquippedCustomArmor(Player p) {
        List<CustomArmorItem> found = new ArrayList<>();
        for (ItemStack is : p.getInventory().getArmorContents()) {
            if (is == null) continue;


            CustomArmorItem P = PDCDetection.getCustomArmor(is);
            if (P == null) continue;
            found.add(P);
        }
        return found;
    }


    public int countInvestorsPieces(Player p) {
        int cnt = 0;


        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.INVESTORS_BOOTS
                    || ca == CustomArmorItem.INVESTORS_LEGGINGS
                    || ca == CustomArmorItem.INVESTORS_CHESTPLATE
                    || ca == CustomArmorItem.INVESTORS_HELMET) {
                cnt++;
            }
        }
        return cnt;
    }


    public boolean hasBullseyePants(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.BULLSEYE_PANTS) return true;
        }
        return false;
    }


    public boolean hasTectonicCap(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.TECTONIC_CAP) return true;
        }
        return false;
    }


    public boolean hasDeathmaulerSet(Player p) {
        boolean hasChest = false, hasLegs = false;
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.DEATHMAULER_CHESTPLATE) hasChest = true;
            if (ca == CustomArmorItem.DEATHMAULER_LEGGINGS) hasLegs = true;
        }
        return hasChest && hasLegs;
    }


    public boolean hasDragonSet(Player p) {
        boolean hasChest = false, hasBoots = false, hasHelmet = false;
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.DRAGON_CHESTPLATE) hasChest = true;
            if (ca == CustomArmorItem.DRAGON_BOOTS) hasBoots = true;
            if (ca == CustomArmorItem.DRAGON_HELMET) hasHelmet = true;
        }
        return hasChest && hasBoots && hasHelmet;
    }


    public boolean hasBunnyShoes(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.BUNNY_SHOES) return true;
        }
        return false;
    }


    public boolean hasGuardianVest(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.GUARDIANS_VEST) return true;
        }
        return false;
    }


    public boolean hasFlamebringerSet(Player p) {
        boolean hasBoots = false, hasLegs = false;
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.FLAMEBRINGER_BOOTS) hasBoots = true;
            if (ca == CustomArmorItem.FLAMEBRINGER_LEGGINGS) hasLegs = true;
        }
        return hasBoots && hasLegs;
    }


    // ==================== TECTONIC CAP ====================


    public void onTectonicCapFall(EntityDamageEvent event, Player player) {


        if (!hasTectonicCap(player)) return;


        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();


        boolean charge1Ready =
                !tectonicCharge1Cooldown.containsKey(id)
                        || tectonicCharge1Cooldown.get(id) <= now;


        boolean charge2Ready =
                !tectonicCharge2Cooldown.containsKey(id)
                        || tectonicCharge2Cooldown.get(id) <= now;


        if (!charge1Ready && !charge2Ready) return;


        boolean useCharge1 = charge1Ready;


        Location impact = player.getLocation().clone().add(0, 0.1, 0);
        Material feet = player.getLocation().getBlock().getType();


        if (feet == Material.WATER || feet == Material.LAVA || feet == Material.COBWEB) {
            return;
        }


        double fallDamage = event.getFinalDamage(); // calculate FIRST
        event.setCancelled(true);
        double radius = 4.0 + (fallDamage * 0.3);
        World world = player.getWorld();


        Particle.DustOptions lightBrown = new Particle.DustOptions(
                Color.fromRGB(180, 120, 60),
                2.5f
        );


        Particle.DustOptions darkBrown = new Particle.DustOptions(
                Color.fromRGB(80, 45, 20),
                2.5f
        );


        world.spawnParticle(
                Particle.DUST,
                impact,
                75,
                1.1,
                0.1,
                1.1,
                0,
                lightBrown
        );


        world.spawnParticle(
                Particle.DUST,
                impact,
                60,
                1.1,
                0.1,
                1.1,
                0,
                darkBrown
        );


        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);


        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {


            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;


            target.damage(4.0 + fallDamage, player);


            Vector knockback = target.getLocation()
                    .toVector()
                    .subtract(impact.toVector())
                    .normalize()
                    .setY(0.25)
                    .multiply(0.7);


            target.setVelocity(knockback);
            if (target.getLocation().distance(impact) <= 2.0) {
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS,
                        20 * 4, // 4 seconds
                        1,      // Slowness II
                        false,
                        true,
                        true
                ));
            }
        }
        long cooldownEnd = System.currentTimeMillis() + 22_000L;


        if (useCharge1) {
            tectonicCharge1Cooldown.put(id, cooldownEnd);
        } else {
            tectonicCharge2Cooldown.put(id, cooldownEnd);
        }
    }


    // ==================== DRAGON SET ====================


    private int getDragonScales(Player player) {
        return dragonScales.getOrDefault(player.getUniqueId(), 0);
    }
    private void setDragonScales(Player player, int amount) {
        dragonScales.put(player.getUniqueId(), Math.min(3, amount));
    }


    private boolean consumeDragonScale(Player player) {
        int scales = getDragonScales(player);


        if (scales <= 0) {
            return false;
        }
        setDragonScales(player, scales - 1);
        return true;
    }


    public boolean useDragonRushScale(Player player) {
        return consumeDragonScale(player);
    }


    public void handleDragonHit(Player player) {


        if (!hasDragonSet(player)) {
            return;
        }


        if (!isFullyChargedMelee(player)) return;


        if (getDragonScales(player) >= 3) {
            return;
        }


        int hits = dragonHitCount.getOrDefault(
                player.getUniqueId(),
                0
        );


        hits++;


        if (hits >= 5) {


            setDragonScales(
                    player,
                    getDragonScales(player) + 1
            );


            dragonHitCount.put(
                    player.getUniqueId(),
                    0
            );


            player.sendMessage(
                    "§5Dragon Scale charged! §d"
                            + getDragonScales(player)
                            + "/3"
            );


        } else {


            dragonHitCount.put(
                    player.getUniqueId(),
                    hits
            );
        }
    }


    // ==================== BUNNY SHOES ====================


    public void onPlayerToggleSneak(Player p, boolean sneaking) {
        if (!hasBunnyShoes(p)) return;
        if (p.isDead()) return; // Don't apply effects to dead players
        UUID id = p.getUniqueId();


        if (sneaking) {
            bunnyToggleReady.put(id, true);
        } else {
            Boolean ready = bunnyToggleReady.get(id);
            if (ready != null && ready) {
                bunnyToggleReady.put(id, false);
                tryActivateBunnyShoes(p);
            }
        }
    }


    private void tryActivateBunnyShoes(Player p) {
        UUID id = p.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();


        //Check if player is using a mythic ability
        if (mythicShiftLock.contains(id)) return;
        Messages.debug(p, "SHIFT LOCK: " + mythicShiftLock.contains(p.getUniqueId()));


        // Check if player is silenced (carrying enemy flag in CTF)
        if (isSilenced(p)) {
            Messages.send(p, "listener.cannot-use-items-while-silenced");
            return;
        }


        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.BUNNY_SHOES)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(id, CooldownManager.Keys.BUNNY_SHOES);
            Messages.send(p, "armor.bunny-shoes-cooldown", "remaining", String.valueOf(remaining));
            return;
        }


        int duration = cfg.getBunnyShoesDuration();
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration * 20, 1));
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.BUNNY_SHOES, cfg.getBunnyShoesCooldown());


        Messages.send(p, "armor.bunny-shoes-activated", "duration", String.valueOf(duration));
        SoundUtils.play(p, Sound.ENTITY_RABBIT_JUMP, 1.0f, 1.5f);
    }


    // ==================== GUARDIAN'S VEST ====================


    public void onPlayerDamaged(Player p, double healthAfter) {
        if (!hasGuardianVest(p)) return;
        UUID id = p.getUniqueId();


        if (healthAfter > 8.0) return; // 4 hearts = 8 HP


        int used = guardianUsesThisRound.getOrDefault(id, 0);
        if (used >= 3) return;


        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.GUARDIAN_VEST)) return;


        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 15 * 20, 1));
        guardianUsesThisRound.put(id, used + 1);
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.GUARDIAN_VEST, 20);


        Messages.send(p, "armor.guardian-vest-activated", "uses", String.valueOf(used + 1));
        SoundUtils.play(p, Sound.ITEM_TOTEM_USE, 0.5f, 1.5f);
    }


    // ==================== DEATHMAULER'S OUTFIT ====================


    public void onPlayerKill(Player killer, GameSession session) {
        if (!hasDeathmaulerSet(killer)) return;
        UUID id = killer.getUniqueId();


        // Heal 3 hearts (6 HP) using centralized health system for max health
        CashClashPlayer killerCCP = session != null ? session.getCashClashPlayer(id) : null;
        double maxHealth = killerCCP != null ? killerCCP.getMaxHealth() : 20.0;
        double newHealth = Math.min(maxHealth, killer.getHealth() + 6.0);
        killer.setHealth(newHealth);


        Messages.send(killer, "armor.deathmauler-heal");


        // Show small healing particle effect on normal kills
        ParticleUtils.deathmaulerHeal(killer.getLocation());
        SoundUtils.play(killer, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 1.5f);
    }


    public void onDeathmaulerDamageTaken(Player p) {
        if (!hasDeathmaulerSet(p)) return;
        UUID id = p.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();
        cooldownManager.setTimestamp(id, CooldownManager.Keys.DEATHMAULER_DAMAGE);


        int delaySeconds = cfg.getDeathmaulerAbsorptionDelay();
        // Schedule absorption check after configured delay without damage
        SchedulerUtils.runTaskLater(() -> {
            if (!cooldownManager.hasTimePassedSeconds(id, CooldownManager.Keys.DEATHMAULER_DAMAGE, delaySeconds)) {
                return;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60 * 20, 0));
            Messages.send(p, "armor.deathmauler-absorption");
        }, delaySeconds * 20L);
    }


    // ==================== DEATHMAULER SOUL BURST ====================


    public void tryDeathmaulerSoulBurst(Player attacker, Player victim, GameSession session) {
        if (!hasDeathmaulerSet(attacker) || victim == null) return;
        UUID id = attacker.getUniqueId();


        // Use centralized health system for correct max health
        var attackerCCP = session != null ? session.getCashClashPlayer(id) : null;
        double max = attackerCCP != null ? attackerCCP.getMaxHealth() : 20.0;
        if (attacker.getHealth() > max * 0.5) return;


        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.DEATHMAULER_SOUL_BURST)) return;


        if (!isFullyChargedMelee(attacker)) return;


        playDeathmaulerSoulBurst(attacker, session);


        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.DEATHMAULER_SOUL_BURST, 35);
        Messages.send(attacker, "armor.soul-burst");
        SoundUtils.play(attacker, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.8f);
    }


    private void playDeathmaulerSoulBurst(Player attacker, GameSession session) {


        Location center = attacker.getLocation();
        final double[] radius = {0.5};
        Set<UUID> hitPlayers = new HashSet<>();


        BukkitTask[] task = new BukkitTask[1];
        task[0] = SchedulerUtils.runTaskTimer(() -> {


            if (!attacker.isOnline() || attacker.isDead()) {
                task[0].cancel();
                return;
            }


            radius[0] += 0.5;


            if (radius[0] >= 6.0) {
                task[0].cancel();
                return;
            }


            for (double angle = 0; angle < Math.PI * 2; angle += 0.15) {


                double x = Math.cos(angle) * radius[0];
                double z = Math.sin(angle) * radius[0];


                Location particleLoc = center.clone().add(
                        x,
                        1.0 + (Math.random() * 0.4 - 0.2),
                        z
                );


                Color particleColor = (angle % 0.3 < 0.15)
                        ? Color.BLACK
                        : Color.RED;


                center.getWorld().spawnParticle(
                        Particle.DUST,
                        particleLoc,
                        1,
                        new Particle.DustOptions(particleColor, 1.2f)
                );}
            for (Entity entity : center.getWorld().getNearbyEntities(center, radius[0], radius[0], radius[0])) {


                if (!(entity instanceof Player target)) continue;
                if (target.equals(attacker)) continue;


                if (hitPlayers.contains(target.getUniqueId())) continue;


                if (session != null) {
                    Team aTeam = session.getPlayerTeam(attacker);
                    Team tTeam = session.getPlayerTeam(target);


                    if (tTeam != null && aTeam == tTeam) continue;
                }


                // Player has been hit by the Soul Burst wave
                hitPlayers.add(target.getUniqueId());


                double damage = 3.0; // 1.5 hearts


                double newHealth = Math.max(0.0, target.getHealth() - damage);
                target.setHealth(newHealth);


                // Heal Deathmauler for damage dealt
                var attackerCCP = session != null ? session.getCashClashPlayer(attacker.getUniqueId()) : null;
                double max = attackerCCP != null ? attackerCCP.getMaxHealth() : 20.0;


                double healAmount = CustomHealingManager.modifyHealing(attacker, damage);
                double healed = Math.min(attacker.getHealth() + healAmount, max);
                attacker.setHealth(healed);


                ParticleUtils.hitFeedback(target.getLocation(), 10, 0.2);
            }
        }, 0L, 1L);
    }


    private boolean isFullyChargedMelee(Player attacker) {
        try {
            return attacker.getAttackCooldown() >= 0.85f;
        } catch (NoSuchMethodError ignored) {
            return true;
        }
    }


    // ==================== FLAMEBRINGER SET ====================


    /**
     * Flamebringer Furnace Blood: If player is on fire, take no fire tick KB and gain Speed I for 12s.
     */
    public void onFlamebringerFireTick(Player p) {
        if (!hasFlamebringerSet(p)) return;
        if (p.isDead()) return; // Don't apply effects to dead players


        UUID id = p.getUniqueId();
        long currentTime = System.currentTimeMillis();


        if (p.getFireTicks() > 0) {
            // Check if speed was already applied and is still active
            Long endTime = flamebringerSpeedEndTime.get(id);
            if (endTime != null && currentTime < endTime) {
                // Speed effect is still active, don't reapply
                return;
            }


            // Apply speed for 12 seconds
            ItemsConfig cfg = ItemsConfig.getInstance();
            p.removePotionEffect(PotionEffectType.SPEED);
            p.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED,
                    cfg.getFlamebringerSpeedDuration() * 20,
                    cfg.getFlamebringerSpeedLevel(),
                    false,
                    false,
                    true
            ));


            startFlamebringerTrail(p);


            flamebringerTrailEndTime.put(
                    id,
                    System.currentTimeMillis() + (cfg.getFlamebringerSpeedDuration() * 1000L)
            );


            // Track when this speed effect should end
            flamebringerSpeedEndTime.put(id, currentTime + (cfg.getFlamebringerSpeedDuration() * 1000L));
        } else {
            // Player is no longer on fire, check if speed should be cleared
            Long endTime = flamebringerSpeedEndTime.get(id);
            if (endTime != null && currentTime >= endTime) {
                p.removePotionEffect(PotionEffectType.SPEED);
                flamebringerSpeedEndTime.remove(id);
            }
        }
    }


    private void startFlamebringerTrail(Player p) {
        UUID id = p.getUniqueId();


        // Prevent duplicate trails
        if (flamebringerTrailTasks.containsKey(id)) return;


        BukkitTask task = SchedulerUtils.runTaskTimer(() -> {


            if (!p.isOnline() || p.isDead()) {
                stopFlamebringerTrail(p);
                return;
            }


            Long endTime = flamebringerTrailEndTime.get(id);


            if (endTime == null || System.currentTimeMillis() >= endTime || !hasFlamebringerSet(p)) {
                stopFlamebringerTrail(p);
                return;
            }


            Location loc = p.getLocation().clone();


            List<Location> trail = flamebringerTrailLocations.computeIfAbsent(
                    id,
                    k -> new ArrayList<>()
            );


            // Save current position
            trail.add(loc);


            // Remove locations older than 1s
            if (trail.size() > 6) {
                trail.remove(0);
            }


            // Spawn particles behind player
            for (Location trailLoc : trail) {
                p.getWorld().spawnParticle(
                        Particle.DUST,
                        trailLoc.clone().add(0, 0.2, 0),
                        3,
                        0.15,
                        0.05,
                        0.15,
                        0,
                        new Particle.DustOptions(Color.RED, 1.0f)
                );
            }


            // Ignite enemies touching trail
            for (Entity entity : p.getNearbyEntities(1.2, 1.0, 1.2)) {


                if (!(entity instanceof Player target)) continue;
                if (target.equals(p)) continue;


                target.setFireTicks(60); // 3s of fire
            }


        }, 0L, 2L);


        flamebringerTrailTasks.put(id, task);
    }


    private void stopFlamebringerTrail(Player p) {
        UUID id = p.getUniqueId();


        BukkitTask task = flamebringerTrailTasks.remove(id);


        if (task != null) {
            task.cancel();
        }


        flamebringerTrailLocations.remove(id);
    }


    /**
     * Triggered when lava damages the player: grant Speed for 12s, max 3 per game, 2s cooldown between procs.
     */
    public void onFlamebringerLavaDamage(Player p) {
        if (!hasFlamebringerSet(p)) return;
        UUID id = p.getUniqueId();


        int used = flamebringerLavaUses.getOrDefault(id, 0);
        if (used >= 3) return;


        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.FLAMEBRINGER_LAVA_COOLDOWN)) {
            return;
        }


        ItemsConfig cfg = ItemsConfig.getInstance();
        p.removePotionEffect(PotionEffectType.SPEED);
        p.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                cfg.getFlamebringerSpeedDuration() * 20,
                cfg.getFlamebringerSpeedLevel(),
                false,
                false,
                true
        ));


        startFlamebringerTrail(p);
        flamebringerTrailEndTime.put(
                id,
                System.currentTimeMillis() + (cfg.getFlamebringerSpeedDuration() * 1000L)
        );


        flamebringerLavaUses.put(id, used + 1);
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.FLAMEBRINGER_LAVA_COOLDOWN, 2);
        Messages.send(p, "armor.flamebringer-speed", "remaining", String.valueOf(3 - (used + 1)));
    }


    /**
     * Check if player should have fire tick knockback negation.
     */
    public boolean hasFlamebringerNoFireKb(Player p) {
        if (!hasFlamebringerSet(p)) return false;
        return p.getFireTicks() > 0 && ItemsConfig.getInstance().getFlamebringerNoFireKb();
    }


    /**
     * Handle Flamebringer kill tracking and gravitational pull on 2nd kill.
     */
    public void onFlamebringerKill(Player killer) {
        if (!hasFlamebringerSet(killer)) return;


        UUID id = killer.getUniqueId();
        int kills = flamebringerKills.getOrDefault(id, 0) + 1;
        flamebringerKills.put(id, kills);


        ItemsConfig cfg = ItemsConfig.getInstance();


        if (kills >= cfg.getFlamebringerKillsForPull()) {
            flamebringerKills.put(id, 0);


            Messages.send(killer, "armor.flamebringer-pull-activated");
            SoundUtils.play(killer, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);


            double radius = cfg.getFlamebringerPullRadius();
            double duration = cfg.getFlamebringerPullDuration();
            double pullStrength = cfg.getFlamebringerPullStrength();


            GameManager gameManager = GameManager.getInstance();
            GameSession session = gameManager.getPlayerSession(killer);
            Team killerTeam = session != null ? session.getPlayerTeam(killer) : null;


            Location killerLoc = killer.getLocation();


            int durationTicks = (int) (duration * 20);
            BukkitTask pullTask = SchedulerUtils.runTaskTimer(() -> {
                if (!killer.isOnline()) return;


                ParticleUtils.flamebringerPull(killerLoc, radius);


                for (Entity entity : killer.getWorld().getNearbyEntities(killerLoc, radius, radius, radius)) {
                    if (!(entity instanceof Player target)) continue;
                    if (target.equals(killer)) continue;


                    if (session != null && killerTeam != null) {
                        Team targetTeam = session.getPlayerTeam(target);
                        if (targetTeam == killerTeam) continue;
                    }


                    Vector direction = killerLoc.toVector().subtract(target.getLocation().toVector()).normalize();
                    target.setVelocity(direction.multiply(pullStrength));
                }
            }, 0L, 2L);


            SchedulerUtils.runTaskLater(() -> {
                if (pullTask != null) {
                    pullTask.cancel();
                }
                if (killer.isOnline()) {
                    Messages.send(killer, "armor.flamebringer-pull-ended");
                }
            }, durationTicks);
        }
    }


    // ==================== BULLSEYE PANTS ====================


    /**
     * Increments the hit count for Bullseye Pants.
     * @return true if it was the 4th hit (triggering the effect)
     */
    public boolean incrementBullseyeHit(Player p) {
        UUID id = p.getUniqueId();
        int hits = bullseyeHitCount.getOrDefault(id, 0) + 1;
        if (hits >= 4) {
            bullseyeHitCount.put(id, 0);
            return true;
        }
        bullseyeHitCount.put(id, hits);
        return false;
    }


    // ==================== INVESTOR'S SET ====================


    public double getInvestorMultiplier(Player p) {
        int pieces = countInvestorsPieces(p);
        if (pieces <= 0) return 1.0;
        return 1.0 + (0.125 * pieces); // +12.5% per piece
    }


    public double getInvestorMeleeDamageMultiplier(Player p, int currentRound) {
        int pieces = countInvestorsPieces(p);
        if (pieces <= 0) return 1.0;
        return 1.0 - (0.05 * pieces); //5% less damage per piece
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


    // ==================== RESET ====================


    public void cleanup() {


        bunnyToggleReady.clear();


        guardianUsesThisRound.clear();


        deathmaulerExtraHearts.clear();


        bullseyeHitCount.clear();


        // Cancel all flamebringer tasks
        flamebringerFireTask.values().forEach(BukkitTask::cancel);
        flamebringerFireTask.clear();
        flamebringerKills.clear();
        flamebringerLavaUses.clear();
        flamebringerSpeedEndTime.clear();


        // Note: cooldowns are managed by CooldownManager and will be cleared when players are cleared
    }

    /**
     * Reset per-round tracking for all players (called at round start).
     */
    public void resetRoundTracking() {
        guardianUsesThisRound.clear();
        deathmaulerExtraHearts.clear();
        bullseyeHitCount.clear();


        // Reset flamebringer kill counters
        flamebringerKills.clear();
        flamebringerSpeedEndTime.clear();
    }


    /**
     * Check if player is silenced (carrying enemy flag in CTF or dead)
     */
    private boolean isSilenced(Player player) {
        // Dead players are silenced for all items
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null) {
            RoundData roundData = session.getCurrentRoundData();
            if (roundData != null && !roundData.isAlive(player.getUniqueId())) {
                return true;
            }
        }


        if (session == null || session.getGamemode() == null) return false;
        if (!(session.getGamemode() instanceof CaptureTheFlagGamemode gamemode)) return false;
        return gamemode.isSilenced(player.getUniqueId());
    }
    public void lockMythicShift(Player player) {
        UUID id = player.getUniqueId();


        mythicShiftLock.add(id);


        Bukkit.getScheduler().runTaskLater(
                JavaPlugin.getProvidingPlugin(getClass()),
                () -> mythicShiftLock.remove(id),
                10L
        );
    }
}