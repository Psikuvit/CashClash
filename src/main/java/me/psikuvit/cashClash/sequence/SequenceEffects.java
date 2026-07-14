package me.psikuvit.cashClash.sequence;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Reusable presentation helpers for {@link Sequence} steps: title display and temporary
 * blindness. Movement lock itself is enforced via {@code GameSession#isSequenceLocked()}
 * checked in {@code MoveListener}, not a potion effect.
 */
public final class SequenceEffects {

    private static final Title.Times DEFAULT_TIMES = Title.Times.times(
            Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(250));

    private SequenceEffects() {
        throw new AssertionError("Nope.");
    }

    public static void showTitle(Collection<UUID> players, Component title, Component subtitle) {
        showTitle(players, title, subtitle, DEFAULT_TIMES);
    }

    public static void showTitle(Collection<UUID> players, Component title, Component subtitle, Title.Times times) {
        Title displayTitle = Title.title(title, subtitle == null ? Component.empty() : subtitle, times);
        forEachOnline(players, p -> p.showTitle(displayTitle));
    }

    public static void clearTitle(Collection<UUID> players) {
        forEachOnline(players, Player::clearTitle);
    }

    public static void applyBlindness(Collection<UUID> players, int ticks) {
        forEachOnline(players, p -> p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, false, false)));
    }

    public static void clearBlindness(Collection<UUID> players) {
        forEachOnline(players, p -> p.removePotionEffect(PotionEffectType.BLINDNESS));
    }

    private static void forEachOnline(Collection<UUID> players, Consumer<Player> action) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                action.accept(p);
            }
        }
    }
}
