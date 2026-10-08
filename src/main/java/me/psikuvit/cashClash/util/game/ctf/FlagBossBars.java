package me.psikuvit.cashClash.util.game.ctf;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.enums.TeamColor;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * One CTF game's boss bars, both draining towards empty in the colour of the flag they're about:
 * a dropped flag's return countdown (everyone in the game) and a player's own pickup or scoring
 * progress (only them). Showing and hiding a boss bar is idempotent, so callers just re-apply the
 * state they want on every update.
 */
public class FlagBossBars {

    private final Map<TeamColor, BossBar> returnBars;
    private final Map<UUID, BossBar> progressBars;

    public FlagBossBars() {
        this.returnBars = new EnumMap<>(TeamColor.class);
        this.progressBars = new HashMap<>();
    }

    /**
     * Shows a dropped flag's return countdown to every online viewer, except the ones
     * {@code hiddenFrom} matches (players picking that flag up, who see their pickup bar instead).
     */
    public void showReturn(TeamColor flag, long remainingMs, long totalMs, Collection<UUID> viewers, Predicate<UUID> hiddenFrom) {
        BossBar bar = returnBars.computeIfAbsent(flag, FlagBossBars::createBar);
        update(bar, "gamemode-ctf.flag-return-bar", flag, remainingMs, totalMs);

        for (UUID uuid : viewers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            if (hiddenFrom.test(uuid)) {
                player.hideBossBar(bar);
            } else {
                player.showBossBar(bar);
            }
        }
    }

    public void hideReturn(TeamColor flag) {
        BossBar bar = returnBars.remove(flag);
        if (bar != null) hideFromEveryone(bar);
    }

    /**
     * Shows a player their own progress bar - picking a flag up, or scoring one - titled from
     * {@code nameKey}.
     */
    public void showProgress(Player player, TeamColor flag, String nameKey, long remainingMs, long totalMs) {
        BossBar bar = progressBars.get(player.getUniqueId());
        if (bar != null && bar.color() != barColor(flag)) {
            player.hideBossBar(bar);
            bar = null;
        }
        if (bar == null) {
            bar = createBar(flag);
            progressBars.put(player.getUniqueId(), bar);
        }
        update(bar, nameKey, flag, remainingMs, totalMs);
        player.showBossBar(bar);
    }

    public void hideProgress(Player player) {
        BossBar bar = progressBars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    /**
     * Takes every bar off one player - for a player leaving the game.
     */
    public void hideFor(Player player) {
        hideProgress(player);
        returnBars.values().forEach(player::hideBossBar);
    }

    public void hideAll() {
        List<BossBar> bars = new ArrayList<>(returnBars.values());
        bars.addAll(progressBars.values());
        returnBars.clear();
        progressBars.clear();
        bars.forEach(FlagBossBars::hideFromEveryone);
    }

    private static void update(BossBar bar, String nameKey, TeamColor flag, long remainingMs, long totalMs) {
        long clamped = Math.max(0L, remainingMs);
        long seconds = clamped / 1000L + (clamped % 1000L > 0 ? 1 : 0);
        String name = CashClashPlugin.getInstance().getMessagesConfig().getMessage(nameKey,
                "color", flag.getDisplayName().toLowerCase(),
                "team_name", flag.getDisplayName(),
                "seconds", String.valueOf(seconds));
        bar.name(Messages.parse(name));
        bar.progress(totalMs <= 0 ? 0f : Math.min(1f, (float) clamped / totalMs));
    }

    private static BossBar createBar(TeamColor flag) {
        return BossBar.bossBar(Component.empty(), 1f, barColor(flag), BossBar.Overlay.PROGRESS);
    }

    private static BossBar.Color barColor(TeamColor flag) {
        return flag == TeamColor.RED ? BossBar.Color.RED : BossBar.Color.BLUE;
    }

    private static void hideFromEveryone(BossBar bar) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bar);
        }
    }
}
