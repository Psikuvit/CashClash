package me.psikuvit.cashClash.util.game;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Who an item ability hits and who it helps. In a game that's decided by team. Outside one - the
 * hub and the practice range - there are no teams: an ability only hits the range's enemy mobs
 * (skeletons, zombie dummies, the boss) and only helps its user and the range's piglin
 * teammates, never another player, so nobody can grief the hub with them.
 */
public final class AbilityTargets {

    private AbilityTargets() {
        throw new AssertionError("Nope.");
    }

    public static boolean inGame(Player player) {
        return player != null && CashClashPlugin.getInstance().getGameManager().getPlayerSession(player) != null;
    }

    /**
     * Whether an ability used by {@code user} may hit {@code target}: in a game a player on the
     * other team, outside one a practice-range enemy mob.
     */
    public static boolean isEnemy(Player user, Entity target) {
        if (!(target instanceof LivingEntity living) || living.equals(user) || living.isDead()) return false;

        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(user);
        if (session == null) return PDCDetection.isPracticeMob(target);
        if (!(target instanceof Player other)) return false;

        Team userTeam = session.getPlayerTeam(user);
        Team otherTeam = session.getPlayerTeam(other);
        return userTeam != null && otherTeam != null && userTeam.getTeamNumber() != otherTeam.getTeamNumber();
    }

    /**
     * Whether {@code target} is on {@code user}'s side, the user themselves not counted: in a
     * game a teammate, outside one a practice-range piglin.
     */
    public static boolean isAlly(Player user, Entity target) {
        if (!(target instanceof LivingEntity living) || living.equals(user) || living.isDead()) return false;

        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(user);
        if (session == null) return PDCDetection.isPracticeTeammate(target);
        if (!(target instanceof Player other)) return false;

        Team userTeam = session.getPlayerTeam(user);
        Team otherTeam = session.getPlayerTeam(other);
        return userTeam != null && otherTeam != null && userTeam.getTeamNumber() == otherTeam.getTeamNumber();
    }

    /**
     * Every enemy of {@code user} in the box of the given half-sizes around {@code center}.
     */
    public static List<LivingEntity> enemiesNear(Player user, Location center, double x, double y, double z) {
        List<LivingEntity> enemies = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) return enemies;
        for (Entity entity : world.getNearbyEntities(center, x, y, z)) {
            if (isEnemy(user, entity)) enemies.add((LivingEntity) entity);
        }
        return enemies;
    }

    /**
     * Every ally of {@code user} (not the user) in the box of the given half-sizes around
     * {@code center}.
     */
    public static List<LivingEntity> alliesNear(Player user, Location center, double x, double y, double z) {
        List<LivingEntity> allies = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) return allies;
        for (Entity entity : world.getNearbyEntities(center, x, y, z)) {
            if (isAlly(user, entity)) allies.add((LivingEntity) entity);
        }
        return allies;
    }

    /**
     * Whether an entity an ability is holding on to is still around: online for a player,
     * alive and loaded for a mob.
     */
    public static boolean isPresent(LivingEntity entity) {
        return entity instanceof Player player ? player.isOnline() : entity.isValid();
    }

    /**
     * A potion effect on an ability's target: through {@link CashClashPlayer} for a player, so
     * it's tracked, and directly for a mob.
     */
    public static void applyEffect(LivingEntity target, PotionEffectType type, int durationTicks, int amplifier,
                                   boolean ambient, boolean particles) {
        if (target instanceof Player player) {
            CashClashPlayer.applyEffect(player, type, durationTicks, amplifier, ambient, particles);
        } else {
            target.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, ambient, particles));
        }
    }

    public static void applyEffect(LivingEntity target, PotionEffectType type, int durationTicks, int amplifier) {
        applyEffect(target, type, durationTicks, amplifier, false, false);
    }

    public static void removeEffect(LivingEntity target, PotionEffectType type) {
        if (target instanceof Player player) {
            CashClashPlayer.removeEffect(player, type);
        } else {
            target.removePotionEffect(type);
        }
    }

    /**
     * A sound for an ability's target: to the player themselves, or played where a mob stands.
     */
    public static void playSound(LivingEntity target, Sound sound, float volume, float pitch) {
        if (target instanceof Player player) {
            SoundUtils.play(player, sound, volume, pitch);
        } else {
            SoundUtils.playAt(target.getLocation(), sound, volume, pitch);
        }
    }

    /**
     * An ability target's max health: through {@link CashClashPlayer} for a player, the
     * attribute for a mob.
     */
    public static double getMaxHealth(LivingEntity target) {
        if (target instanceof Player player) return CashClashPlayer.getMaxHealth(player);
        AttributeInstance attribute = target.getAttribute(Attribute.MAX_HEALTH);
        return attribute != null ? attribute.getValue() : 20.0;
    }
}
