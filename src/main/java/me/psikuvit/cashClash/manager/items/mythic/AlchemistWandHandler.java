package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alchemist Wand - teammate Blink Swap with short damage protection, a "Tidy Up" window that
 * reveals every player's potion effects and cleanses/strips them on your next melee hit, and a
 * Taunt that chains nearby teammates and redirects their damage to the wielder.
 */
public class AlchemistWandHandler extends MythicItemHandler {

    // Debuffs Tidy Up cleanses off the wielder's own team on a teammate hit.
    private static final Set<PotionEffectType> TIDY_UP_NEGATIVE_EFFECTS = Set.of(
            PotionEffectType.POISON, PotionEffectType.WEAKNESS, PotionEffectType.SLOWNESS,
            PotionEffectType.MINING_FATIGUE, PotionEffectType.NAUSEA, PotionEffectType.BLINDNESS,
            PotionEffectType.HUNGER, PotionEffectType.WITHER, PotionEffectType.LEVITATION,
            PotionEffectType.UNLUCK, PotionEffectType.DARKNESS, PotionEffectType.BAD_OMEN,
            PotionEffectType.INFESTED
    );

    // Buffs Tidy Up strips off the enemy team on an enemy hit.
    private static final Set<PotionEffectType> TIDY_UP_POSITIVE_EFFECTS = Set.of(
            PotionEffectType.STRENGTH, PotionEffectType.REGENERATION, PotionEffectType.SPEED,
            PotionEffectType.RESISTANCE, PotionEffectType.FIRE_RESISTANCE, PotionEffectType.ABSORPTION
    );

    // Alchemist Blink Swap invincibility (UUID -> hits remaining)
    private final Map<UUID, Integer> alchemistBlinkProtection;
    private final Map<UUID, Long> alchemistBlinkProtectionExpiry;

    // Alchemist Wand Tidy Up tracking
    private final Map<UUID, Long> alchemistTidyUpExpiry;
    private final Map<UUID, BukkitTask> alchemistTidyUpTimeoutTasks;
    private final Map<UUID, BukkitTask> alchemistTidyUpDisplayTasks;
    // wielder -> target -> effect -> bottle display (only visible to the wielder)
    private final Map<UUID, Map<UUID, Map<PotionEffectType, ItemDisplay>>> alchemistTidyUpBottles;

    // Alchemist Wand Taunt tracking
    private final Map<UUID, TauntSession> alchemistTaunts;   // wielder -> their active session
    private final Map<UUID, UUID> alchemistTauntRedirect;    // chained player -> wielder
    private final Set<UUID> alchemistTauntRedirecting;       // transient re-entrancy guard

    public AlchemistWandHandler(MythicItemManager manager) {
        super(manager);
        this.alchemistBlinkProtection = new ConcurrentHashMap<>();
        this.alchemistBlinkProtectionExpiry = new ConcurrentHashMap<>();
        this.alchemistTidyUpExpiry = new ConcurrentHashMap<>();
        this.alchemistTidyUpTimeoutTasks = new ConcurrentHashMap<>();
        this.alchemistTidyUpDisplayTasks = new ConcurrentHashMap<>();
        this.alchemistTidyUpBottles = new ConcurrentHashMap<>();
        this.alchemistTaunts = new ConcurrentHashMap<>();
        this.alchemistTauntRedirect = new ConcurrentHashMap<>();
        this.alchemistTauntRedirecting = ConcurrentHashMap.newKeySet();
    }

    // ==================== BLINK SWAP (unchanged) ====================

    /**
     * Alchemist Wand Blink Swap.
     * Swaps positions with the player directly under the crosshair.
     * Target must be within 10 blocks and have line of sight.
     */
    public void useAlchemistBlinkSwap(Player player) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "ALCHEMIST_WAND: Blink Swap triggered");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.ALCHEMIST_BLINK_SWAP)) {
            Messages.debug(player, "ALCHEMIST_WAND: Blink Swap on cooldown - "
                    + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.ALCHEMIST_BLINK_SWAP) + "s");
            return;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.debug(player, "ALCHEMIST_WAND: No session");
            return;
        }

        Team playerTeam = session.getPlayerTeam(player);
        if (playerTeam == null) {
            Messages.debug(player, "ALCHEMIST_WAND: Player has no team");
            return;
        }

        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();

        RayTraceResult result = player.getWorld().rayTraceEntities(
                start,
                direction,
                10.0,
                0.1,
                entity -> entity instanceof Player target
                        && !target.equals(player)
                        && isSameTeam(session, playerTeam.getTeamNumber(), target)
        );

        if (result == null || !(result.getHitEntity() instanceof Player target)) {
            Messages.debug(player, "ALCHEMIST_WAND: No valid teammate target");
            return;
        }

        if (!player.hasLineOfSight(target)) {
            Messages.debug(player, "ALCHEMIST_WAND: Target is not in line of sight");
            return;
        }

        Location playerLocation = player.getLocation().clone();
        Location targetLocation = target.getLocation().clone();

        playAlchemistBlinkVisual(playerLocation);
        playAlchemistBlinkVisual(targetLocation);

        long protectionExpiry = System.currentTimeMillis() + 5000;

        alchemistBlinkProtection.put(uuid, 2);
        alchemistBlinkProtection.put(target.getUniqueId(), 2);

        alchemistBlinkProtectionExpiry.put(uuid, protectionExpiry);
        alchemistBlinkProtectionExpiry.put(target.getUniqueId(), protectionExpiry);

        player.teleport(targetLocation);
        target.teleport(playerLocation);

        SoundUtils.play(player, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.0f);

        cooldownManager.setCooldownSeconds(
                uuid,
                CooldownManager.Keys.ALCHEMIST_BLINK_SWAP,
                11
        );

        Messages.debug(player, "ALCHEMIST_WAND: Blink Swap successful with " + target.getName());
    }

    /**
     * Creates a yellow spiral effect for Alchemist Wand Blink Swap.
     */
    private void playAlchemistBlinkVisual(Location location) {
        if (location == null || location.getWorld() == null) return;

        Location center = location.clone();

        BukkitRunnable blinkRunnable = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 13 || center.getWorld() == null) {
                    cancel();
                    return;
                }

                double progress = ticks / 13.0;
                double radius = 0.25 + (progress * 0.75);
                double height = progress * 1.8;
                double angle = ticks * 0.8;

                for (int i = 0; i < 3; i++) {
                    double offsetAngle = angle + (i * (Math.PI * 2 / 3));

                    double x = Math.cos(offsetAngle) * radius;
                    double z = Math.sin(offsetAngle) * radius;

                    Location particleLocation = center.clone().add(x, height, z);

                    ParticleUtils.spawnDust(
                            particleLocation,
                            Color.fromRGB(255, 220, 0),
                            1.5f,
                            2,
                            0.05
                    );
                }

                ticks++;
            }
        };
        SchedulerUtils.runTaskTimer(blinkRunnable, 0L, 1L);
    }

    /**
     * Checks and consumes Alchemist Blink Swap damage protection.
     *
     * @return true if the damage should be cancelled
     */
    public boolean handleAlchemistBlinkProtection(Player player) {
        UUID uuid = player.getUniqueId();

        Integer hitsRemaining = alchemistBlinkProtection.get(uuid);
        if (hitsRemaining == null || hitsRemaining <= 0) {
            return false;
        }

        Long expiry = alchemistBlinkProtectionExpiry.get(uuid);
        if (expiry == null || System.currentTimeMillis() >= expiry) {
            alchemistBlinkProtection.remove(uuid);
            alchemistBlinkProtectionExpiry.remove(uuid);
            return false;
        }

        if (hitsRemaining <= 1) {
            alchemistBlinkProtection.remove(uuid);
            alchemistBlinkProtectionExpiry.remove(uuid);
        } else {
            alchemistBlinkProtection.put(uuid, hitsRemaining - 1);
        }

        Messages.debug(player, "ALCHEMIST_WAND: Blink protection blocked damage. Hits remaining: "
                + Math.max(0, hitsRemaining - 1));

        return true;
    }

    // ==================== TIDY UP ====================

    /**
     * Left-click activation: opens a window (config duration, default 10s) during which the
     * wielder sees every session player's active potion effects as bottle icons above their
     * heads, and their next melee hit resolves the ability - see {@link #onAlchemistMeleeHit}.
     */
    public void useAlchemistTidyUp(Player wielder) {
        UUID uuid = wielder.getUniqueId();
        Messages.debug(wielder, "ALCHEMIST_WAND: Tidy Up triggered");

        if (alchemistTidyUpExpiry.containsKey(uuid)) {
            return; // already active, left-clicking again does nothing extra
        }

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.ALCHEMIST_TIDY_UP)) {
            Messages.debug(wielder, "ALCHEMIST_WAND: Tidy Up on cooldown - "
                    + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.ALCHEMIST_TIDY_UP) + "s");
            return;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(wielder);
        if (session == null) {
            Messages.debug(wielder, "ALCHEMIST_WAND: No session");
            return;
        }

        int durationTicks = cfg.getAlchemistTidyUpDuration() * 20;
        alchemistTidyUpExpiry.put(uuid, System.currentTimeMillis() + durationTicks * 50L);
        alchemistTidyUpBottles.put(uuid, new ConcurrentHashMap<>());

        BukkitTask displayTask = startAlchemistTidyUpDisplayTask(wielder, session, uuid);
        alchemistTidyUpDisplayTasks.put(uuid, displayTask);

        BukkitTask timeoutTask = SchedulerUtils.runTaskLater(() -> endAlchemistTidyUp(wielder, true), durationTicks);
        alchemistTidyUpTimeoutTasks.put(uuid, timeoutTask);

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.ALCHEMIST_TIDY_UP, cfg.getAlchemistTidyUpCooldown());
        Messages.send(wielder, "mythic.alchemist-tidy-up-activated");
        SoundUtils.play(wielder, Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.2f);
    }

    /**
     * @return true if the wielder currently has an active, unexpired Tidy Up window.
     */
    public boolean isTidyUpActive(UUID uuid) {
        Long expiry = alchemistTidyUpExpiry.get(uuid);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /**
     * Refreshes every session player's bottle-icon row every 4 ticks while a Tidy Up window is
     * active - throttled rather than every tick, matching the codebase's other redraw-throttle
     * conventions (e.g. Tectonic Cap's fall-warning ring).
     */
    private BukkitTask startAlchemistTidyUpDisplayTask(Player wielder, GameSession session, UUID wielderId) {
        return SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            @Override
            public void run() {
                if (!wielder.isOnline() || !alchemistTidyUpExpiry.containsKey(wielderId)) {
                    cancel();
                    return;
                }
                refreshAlchemistTidyUpBottles(wielder, session, wielderId);
            }
        }, 0L, 4L);
    }

    private void refreshAlchemistTidyUpBottles(Player wielder, GameSession session, UUID wielderId) {
        Map<UUID, Map<PotionEffectType, ItemDisplay>> perTarget =
                alchemistTidyUpBottles.computeIfAbsent(wielderId, k -> new ConcurrentHashMap<>());

        for (UUID targetUuid : session.getPlayers()) {
            Player target = Bukkit.getPlayer(targetUuid);
            if (target == null || !target.isOnline()) {
                removeAlchemistTidyUpBottlesFor(perTarget, targetUuid);
                continue;
            }

            Map<PotionEffectType, ItemDisplay> bottles = perTarget.computeIfAbsent(targetUuid, k -> new ConcurrentHashMap<>());

            Set<PotionEffectType> active = new HashSet<>();
            for (PotionEffect effect : target.getActivePotionEffects()) {
                active.add(effect.getType());
            }

            bottles.keySet().removeIf(type -> {
                if (active.contains(type)) return false;
                ItemDisplay display = bottles.get(type);
                if (display != null && !display.isDead()) display.remove();
                return true;
            });

            for (PotionEffectType type : active) {
                bottles.computeIfAbsent(type, t -> spawnAlchemistTidyUpBottle(wielder, target, t));
            }

            layoutAlchemistTidyUpBottles(target, bottles);
        }
    }

    private void removeAlchemistTidyUpBottlesFor(Map<UUID, Map<PotionEffectType, ItemDisplay>> perTarget, UUID targetUuid) {
        Map<PotionEffectType, ItemDisplay> bottles = perTarget.remove(targetUuid);
        if (bottles == null) return;
        for (ItemDisplay display : bottles.values()) {
            if (display != null && !display.isDead()) display.remove();
        }
    }

    /**
     * Spawns one potion-bottle icon for a single active effect above a target's head, tinted to
     * match the effect and visible only to the Tidy Up wielder (nothing else in this codebase
     * does per-viewer visibility yet - setVisibleByDefault(false) + Player#showEntity is new).
     */
    private ItemDisplay spawnAlchemistTidyUpBottle(Player wielder, Player target, PotionEffectType type) {
        ItemStack potion = new ItemStack(Material.POTION);
        ItemMeta meta = potion.getItemMeta();
        if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setColor(colorForAlchemistEffect(type));
            potion.setItemMeta(potionMeta);
        }

        ItemDisplay display = target.getWorld().spawn(target.getEyeLocation(), ItemDisplay.class, d -> {
            d.setItemStack(potion);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setInvulnerable(true);
            d.setVisibleByDefault(false);
        });
        wielder.showEntity(CashClashPlugin.getInstance(), display);
        return display;
    }

    /**
     * Lays out a target's current bottle icons in a horizontal row above their head - Billboard
     * CENTER always faces the viewer, so a flat world-space row (no facing-relative rotation
     * needed) reads correctly regardless of where the wielder is standing.
     */
    private void layoutAlchemistTidyUpBottles(Player target, Map<PotionEffectType, ItemDisplay> bottles) {
        if (bottles.isEmpty()) return;

        Location base = target.getEyeLocation().add(0, 0.4, 0);
        List<ItemDisplay> ordered = new ArrayList<>(bottles.values());
        int count = ordered.size();

        for (int i = 0; i < count; i++) {
            ItemDisplay display = ordered.get(i);
            if (display == null || display.isDead()) continue;
            double xOffset = (i - (count - 1) / 2.0) * 0.35;
            display.teleport(base.clone().add(xOffset, 0, 0));
        }
    }

    private Color colorForAlchemistEffect(PotionEffectType type) {
        if (type == PotionEffectType.SPEED) return Color.fromRGB(120, 200, 255);
        if (type == PotionEffectType.STRENGTH) return Color.fromRGB(150, 20, 20);
        if (type == PotionEffectType.POISON) return Color.fromRGB(80, 160, 40);
        if (type == PotionEffectType.WEAKNESS) return Color.fromRGB(60, 60, 60);
        if (type == PotionEffectType.SLOWNESS) return Color.fromRGB(90, 60, 140);
        if (type == PotionEffectType.REGENERATION) return Color.fromRGB(255, 130, 170);
        if (type == PotionEffectType.RESISTANCE) return Color.fromRGB(120, 90, 60);
        if (type == PotionEffectType.FIRE_RESISTANCE) return Color.fromRGB(255, 150, 40);
        if (type == PotionEffectType.ABSORPTION) return Color.fromRGB(255, 210, 0);
        return Color.WHITE;
    }

    /**
     * Resolves an active Tidy Up window on the wielder's next melee hit (called from
     * DamageListener.processMythicDamageEffects, which only fires while the wand is still the
     * item in the attacker's hand at hit time, matching every other mythic on-hit effect).
     * Hitting a teammate cleanses the whole team's debuffs; hitting an enemy strips the whole
     * enemy team's buffs. Ends the window regardless of whether any effects were present.
     */
    public void onAlchemistMeleeHit(Player attacker, Player victim) {
        UUID attackerId = attacker.getUniqueId();
        Long expiry = alchemistTidyUpExpiry.get(attackerId);
        if (expiry == null || System.currentTimeMillis() >= expiry) return;

        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
        if (session == null) return;

        Team attackerTeam = session.getPlayerTeam(attacker);
        Team victimTeam = session.getPlayerTeam(victim);
        if (attackerTeam == null || victimTeam == null) return;

        boolean sameTeam = attackerTeam.getTeamNumber() == victimTeam.getTeamNumber();
        Team affectedTeam = sameTeam ? attackerTeam : victimTeam;
        Set<PotionEffectType> effectsToRemove = sameTeam ? TIDY_UP_NEGATIVE_EFFECTS : TIDY_UP_POSITIVE_EFFECTS;
        Color beamColor = sameTeam ? Color.WHITE : Color.fromRGB(15, 15, 15);
        Sound sound = sameTeam ? Sound.BLOCK_BEACON_ACTIVATE : Sound.ENTITY_WITHER_HURT;
        String messageKey = sameTeam ? "mythic.alchemist-tidy-up-cleansed" : "mythic.alchemist-tidy-up-stripped";

        for (UUID memberUuid : affectedTeam.getPlayers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member == null || !member.isOnline()) continue;

            boolean removedAny = false;
            for (PotionEffectType type : effectsToRemove) {
                if (CashClashPlayer.hasEffect(member, type)) {
                    CashClashPlayer.removeEffect(member, type);
                    removedAny = true;
                }
            }

            if (removedAny) {
                ParticleUtils.verticalBeam(member.getLocation(), beamColor, 5.0, 6, 0.5f, 6);
                SoundUtils.playAt(member.getLocation(), sound, 1.0f, 1.2f);
                Messages.send(member, messageKey);
            }
        }

        Messages.debug(attacker, "ALCHEMIST_WAND: Tidy Up resolved on " + victim.getName()
                + " (" + (sameTeam ? "cleanse" : "strip") + ")");

        endAlchemistTidyUp(attacker, false);
    }

    /**
     * Tears down an active (or expired) Tidy Up window: cancels its tasks and removes every
     * bottle icon. Safe to call for a player who never had one active. When it ends because the
     * 10s ran out without a qualifying hit ({@code expired}), the wielder gets a "you lost Tidy
     * Up" sound + message - a hit-consumed end already has its own cleanse/strip feedback.
     */
    private void endAlchemistTidyUp(Player wielder, boolean expired) {
        UUID uuid = wielder.getUniqueId();
        boolean wasActive = alchemistTidyUpExpiry.containsKey(uuid);

        BukkitTask timeoutTask = alchemistTidyUpTimeoutTasks.remove(uuid);
        if (timeoutTask != null) timeoutTask.cancel();

        BukkitTask displayTask = alchemistTidyUpDisplayTasks.remove(uuid);
        if (displayTask != null) displayTask.cancel();

        Map<UUID, Map<PotionEffectType, ItemDisplay>> perTarget = alchemistTidyUpBottles.remove(uuid);
        if (perTarget != null) {
            for (Map<PotionEffectType, ItemDisplay> bottles : perTarget.values()) {
                for (ItemDisplay display : bottles.values()) {
                    if (display != null && !display.isDead()) display.remove();
                }
            }
        }

        alchemistTidyUpExpiry.remove(uuid);

        if (expired && wasActive && wielder.isOnline()) {
            Messages.send(wielder, "mythic.alchemist-tidy-up-lost");
            SoundUtils.play(wielder, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
        }
    }

    // ==================== TAUNT ====================

    /**
     * One pairwise chain link, formed at activation between two candidates within
     * {@code chain-radius} of each other. {@code anchor} is the invisible leashable mob
     * (tracks {@code a}, leashed to {@code b}) rendering the lead visual for this edge.
     */
    private record TauntEdge(UUID a, UUID b, Bat anchor) {}

    /**
     * One wielder's active Taunt. {@code chainedPlayers} and {@code edges} are mutable
     * (contents change every tick as edges break) even though the record itself is immutable.
     */
    private record TauntSession(UUID wielder, Set<UUID> chainedPlayers, List<TauntEdge> edges,
                                 long expiresAt, BukkitTask tickTask) {}

    /**
     * Shift+right-click activation: chains the wielder to nearby teammates (or, while
     * {@code test-mode-all-players} is on, any nearby player) within {@code chain-radius},
     * transitively through other already-chained players, and starts the live edge-breaking
     * tick task that keeps the chain graph honest for the duration.
     */
    public void useAlchemistTaunt(Player wielder) {
        UUID uuid = wielder.getUniqueId();

        Messages.debug(wielder, "ALCHEMIST_WAND: Taunt triggered");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.ALCHEMIST_TAUNT)) {
            Messages.debug(wielder, "ALCHEMIST_WAND: Taunt on cooldown - "
                    + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.ALCHEMIST_TAUNT) + "s");
            return;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(wielder);
        if (session == null) {
            Messages.debug(wielder, "ALCHEMIST_WAND: No session");
            return;
        }

        List<Player> candidates = alchemistTauntCandidates(wielder, session);
        List<TauntEdge> edges = buildAlchemistTauntEdges(candidates);

        Set<UUID> reachable = alchemistTauntReachable(uuid, edges);
        edges.removeIf(edge -> !reachable.contains(edge.a()) || !reachable.contains(edge.b()));

        Set<UUID> chained = new HashSet<>(reachable);
        chained.remove(uuid);

        List<TauntEdge> liveEdges = new ArrayList<>();
        for (TauntEdge edge : edges) {
            Player a = Bukkit.getPlayer(edge.a());
            Player b = Bukkit.getPlayer(edge.b());
            if (a == null || b == null) continue;
            liveEdges.add(new TauntEdge(edge.a(), edge.b(), spawnAlchemistTauntAnchor(a, b)));
        }

        for (UUID chainedUuid : chained) {
            alchemistTauntRedirect.put(chainedUuid, uuid);
        }

        int durationTicks = cfg.getAlchemistTauntDuration() * 20;
        long expiresAt = System.currentTimeMillis() + durationTicks * 50L;

        BukkitTask tickTask = startAlchemistTauntTick(wielder, uuid);
        alchemistTaunts.put(uuid, new TauntSession(uuid, chained, liveEdges, expiresAt, tickTask));

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.ALCHEMIST_TAUNT, cfg.getAlchemistTauntCooldown());
        Messages.debug(wielder, "ALCHEMIST_WAND: Taunt activated with " + chained.size() + " chained players");
        Messages.send(wielder, "mythic.alchemist-taunt-activated");
        SoundUtils.play(wielder, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.8f);
    }

    private List<Player> alchemistTauntCandidates(Player wielder, GameSession session) {
        List<Player> candidates = new ArrayList<>();

        if (cfg.isAlchemistTauntTestModeAllPlayers()) {
            for (UUID uuid : session.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) candidates.add(p);
            }
            return candidates;
        }

        Team wielderTeam = session.getPlayerTeam(wielder);
        if (wielderTeam == null) return candidates;

        for (UUID uuid : wielderTeam.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) candidates.add(p);
        }
        return candidates;
    }

    private List<TauntEdge> buildAlchemistTauntEdges(List<Player> candidates) {
        List<TauntEdge> edges = new ArrayList<>();
        double radius = cfg.getAlchemistTauntChainRadius();

        for (int i = 0; i < candidates.size(); i++) {
            Player first = candidates.get(i);
            for (int j = i + 1; j < candidates.size(); j++) {
                Player second = candidates.get(j);
                if (!first.getWorld().equals(second.getWorld())) continue;
                if (first.getLocation().distance(second.getLocation()) <= radius) {
                    edges.add(new TauntEdge(first.getUniqueId(), second.getUniqueId(), null));
                }
            }
        }
        return edges;
    }

    /**
     * BFS over the edge graph from {@code root} - the set of UUIDs reachable through any chain
     * of live edges (including {@code root} itself).
     */
    private Set<UUID> alchemistTauntReachable(UUID root, List<TauntEdge> edges) {
        Map<UUID, Set<UUID>> adjacency = new HashMap<>();
        for (TauntEdge edge : edges) {
            adjacency.computeIfAbsent(edge.a(), k -> new HashSet<>()).add(edge.b());
            adjacency.computeIfAbsent(edge.b(), k -> new HashSet<>()).add(edge.a());
        }

        Set<UUID> reachable = new HashSet<>();
        Deque<UUID> toVisit = new ArrayDeque<>();
        toVisit.add(root);

        while (!toVisit.isEmpty()) {
            UUID current = toVisit.poll();
            if (!reachable.add(current)) continue;
            for (UUID neighbor : adjacency.getOrDefault(current, Set.of())) {
                if (!reachable.contains(neighbor)) toVisit.add(neighbor);
            }
        }
        return reachable;
    }

    /**
     * Real players can't be leashed in vanilla, so the lead visual is faked with one invisible,
     * AI-less, no-gravity anchor Bat per edge: teleported every tick to track {@code a}'s
     * position, leashed to {@code b} (Mob#setLeashHolder accepts any LivingEntity, including a
     * Player) so the vanilla rope renders between the two.
     */
    private Bat spawnAlchemistTauntAnchor(Player a, Player b) {
        Bat anchor = a.getWorld().spawn(a.getLocation(), Bat.class, bat -> {
            bat.setInvisible(true);
            bat.setSilent(true);
            bat.setAI(false);
            bat.setGravity(false);
            bat.setInvulnerable(true);
            bat.setCollidable(false);
            bat.setPersistent(false);
        });
        anchor.setLeashHolder(b);
        return anchor;
    }

    private BukkitTask startAlchemistTauntTick(Player wielder, UUID wielderId) {
        return SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            @Override
            public void run() {
                TauntSession taunt = alchemistTaunts.get(wielderId);
                if (taunt == null) {
                    cancel();
                    return;
                }
                if (!wielder.isOnline() || System.currentTimeMillis() >= taunt.expiresAt()) {
                    endAlchemistTaunt(wielder, true);
                    cancel();
                    return;
                }
                tickAlchemistTauntEdges(taunt);
            }
        }, 0L, 1L);
    }

    /**
     * Every tick: reposition each edge's anchor to follow its tracked endpoint, drop any edge
     * whose two endpoints are no longer both online/alive/within radius (unleashing + removing
     * its anchor), then recompute wielder-reachability over the surviving edges - a player only
     * loses their chain once they have zero remaining links back to the wielder.
     */
    private void tickAlchemistTauntEdges(TauntSession taunt) {
        double radius = cfg.getAlchemistTauntChainRadius();
        Iterator<TauntEdge> iterator = taunt.edges().iterator();

        while (iterator.hasNext()) {
            TauntEdge edge = iterator.next();
            Player a = Bukkit.getPlayer(edge.a());
            Player b = Bukkit.getPlayer(edge.b());

            boolean valid = a != null && a.isOnline() && !a.isDead()
                    && b != null && b.isOnline() && !b.isDead()
                    && a.getWorld().equals(b.getWorld())
                    && a.getLocation().distance(b.getLocation()) <= radius;

            if (!valid) {
                removeAlchemistTauntAnchor(edge);
                iterator.remove();
                continue;
            }

            if (edge.anchor() != null && !edge.anchor().isDead()) {
                edge.anchor().teleport(a.getLocation());
            }
        }

        Set<UUID> reachable = alchemistTauntReachable(taunt.wielder(), taunt.edges());
        Iterator<UUID> chainedIterator = taunt.chainedPlayers().iterator();
        while (chainedIterator.hasNext()) {
            UUID chainedUuid = chainedIterator.next();
            if (!reachable.contains(chainedUuid)) {
                chainedIterator.remove();
                alchemistTauntRedirect.remove(chainedUuid);
            }
        }
    }

    private void removeAlchemistTauntAnchor(TauntEdge edge) {
        Bat anchor = edge.anchor();
        if (anchor == null || anchor.isDead()) return;
        anchor.setLeashHolder(null);
        anchor.remove();
    }

    /**
     * Tears down an active (or expired) Taunt: cancels its tick task, unleashes and removes
     * every edge's anchor, and drops every redirect entry pointing at this wielder. Safe to
     * call for a player who never had one active. {@code natural} distinguishes the 7s timer
     * genuinely running out (plays a deactivation sound + message) from a forced end via
     * cleanupPlayer/cleanup (death, disconnect, plugin shutdown - no feedback needed).
     */
    private void endAlchemistTaunt(Player wielder, boolean natural) {
        UUID uuid = wielder.getUniqueId();
        TauntSession taunt = alchemistTaunts.remove(uuid);
        if (taunt == null) return;

        if (taunt.tickTask() != null) taunt.tickTask().cancel();
        for (TauntEdge edge : taunt.edges()) {
            removeAlchemistTauntAnchor(edge);
        }

        alchemistTauntRedirect.values().removeIf(wielderUuid -> wielderUuid.equals(uuid));

        if (natural && wielder.isOnline()) {
            Messages.send(wielder, "mythic.alchemist-taunt-ended");
            SoundUtils.play(wielder, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.8f);
        }
    }

    /**
     * @return true if the wielder currently has an active, unexpired Taunt.
     */
    public boolean isTaunting(UUID uuid) {
        TauntSession taunt = alchemistTaunts.get(uuid);
        return taunt != null && taunt.expiresAt() > System.currentTimeMillis();
    }

    /**
     * @return the wielder a chained player's damage should redirect to, or null if they're not
     * currently chained to anyone.
     */
    public UUID getTauntRedirectTarget(UUID chainedUuid) {
        return alchemistTauntRedirect.get(chainedUuid);
    }

    public boolean isRedirecting(UUID wielderUuid) {
        return alchemistTauntRedirecting.contains(wielderUuid);
    }

    /**
     * Deals redirected damage to the wielder under a transient re-entrancy guard, so the
     * EntityDamageEvent this triggers for the wielder isn't itself mistaken for another
     * redirect target while it's being processed.
     */
    public void redirectTauntDamage(Player wielder, double amount, Entity damager) {
        UUID uuid = wielder.getUniqueId();
        try {
            alchemistTauntRedirecting.add(uuid);
            wielder.damage(amount, damager);
        } finally {
            alchemistTauntRedirecting.remove(uuid);
        }
    }

    // ==================== LIFECYCLE ====================

    @Override
    public void cleanup() {
        alchemistBlinkProtection.clear();
        alchemistBlinkProtectionExpiry.clear();

        alchemistTidyUpTimeoutTasks.values().forEach(task -> { if (task != null) task.cancel(); });
        alchemistTidyUpTimeoutTasks.clear();
        alchemistTidyUpDisplayTasks.values().forEach(task -> { if (task != null) task.cancel(); });
        alchemistTidyUpDisplayTasks.clear();
        for (Map<UUID, Map<PotionEffectType, ItemDisplay>> perTarget : alchemistTidyUpBottles.values()) {
            for (Map<PotionEffectType, ItemDisplay> bottles : perTarget.values()) {
                for (ItemDisplay display : bottles.values()) {
                    if (display != null && !display.isDead()) display.remove();
                }
            }
        }
        alchemistTidyUpBottles.clear();
        alchemistTidyUpExpiry.clear();

        for (TauntSession taunt : alchemistTaunts.values()) {
            if (taunt.tickTask() != null) taunt.tickTask().cancel();
            for (TauntEdge edge : taunt.edges()) {
                removeAlchemistTauntAnchor(edge);
            }
        }
        alchemistTaunts.clear();
        alchemistTauntRedirect.clear();
        alchemistTauntRedirecting.clear();
    }

    /**
     * Handles a dying/disconnecting player in every role they might hold - Tidy Up wielder,
     * Tidy Up target (bottles tracked under every OTHER active wielder), Taunt wielder, or a
     * chained/edge player elsewhere (the next tick of that Taunt's edge-breaking task will
     * naturally drop the now-invalid edge, but the redirect entry is scrubbed immediately so a
     * damage event arriving before that tick doesn't redirect onto a dead/offline chain).
     */
    @Override
    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        alchemistBlinkProtection.remove(uuid);
        alchemistBlinkProtectionExpiry.remove(uuid);

        endAlchemistTidyUp(player, false);
        for (Map<UUID, Map<PotionEffectType, ItemDisplay>> perTarget : alchemistTidyUpBottles.values()) {
            removeAlchemistTidyUpBottlesFor(perTarget, uuid);
        }

        endAlchemistTaunt(player, false);
        alchemistTauntRedirect.remove(uuid);
    }
}
