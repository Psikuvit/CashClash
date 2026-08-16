package me.psikuvit.cashClash.sequence;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.config.SequencesConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.gamemode.impl.ProtectThePresidentGamemode;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;

/**
 * Concrete {@link Sequence} factories for each scripted game moment. Callers drive
 * locking/blindness via {@link SequenceManager#play}; the sequences here only handle
 * timing and title content.
 */
public final class Sequences {

    private static final SequencesConfig MSG = CashClashPlugin.getInstance().getSequencesConfig();

    private Sequences() {
        throw new AssertionError("Nope.");
    }

    private static int revealBlindnessTicks() {
        return CashClashPlugin.getInstance().getConfigManager().getRevealBlindnessTicks();
    }

    private static Title.Times suddenDeathTimes() {
        var cfg = CashClashPlugin.getInstance().getConfigManager();
        return Title.Times.times(
                Duration.ofMillis(cfg.getSuddenDeathTitleFadeInMs()),
                Duration.ofMillis(cfg.getSuddenDeathTitleStayMs()),
                Duration.ofMillis(cfg.getSuddenDeathTitleFadeOutMs()));
    }

    private static Title.Times victoryTimes() {
        var cfg = CashClashPlugin.getInstance().getConfigManager();
        return Title.Times.times(
                Duration.ofMillis(cfg.getVictoryTitleFadeInMs()),
                Duration.ofMillis(cfg.getVictoryTitleStayMs()),
                Duration.ofMillis(cfg.getVictoryTitleFadeOutMs()));
    }

    /**
     * Round 1 start: blind + freeze all players, "Selecting Gamemode...", a 5-second
     * countdown, then reveal the chosen gamemode and its objective.
     */
    public static Sequence roundStart(Gamemode gamemode) {
        String gamemodeName = gamemode.getType().getDisplayName();
        String objective = gamemode.getObjectiveShort();
        String subtitleKey = "round-start." + subtitleKeySuffixFor(gamemode.getType());
        // "Selecting Gamemode..." holds for as long as the reveal title does afterward, rather
        // than being visible for only ~1s before the countdown overwrites it.
        double revealHoldSeconds = 4;

        return Sequence.create()
                .run(s -> SequenceEffects.applyBlindness(s.getPlayers(), revealBlindnessTicks()))
                .pause(40)
                .run(s -> SequenceEffects.showTitle(s.getPlayers(),
                        component(MSG.getRaw("round-start.selecting")), Component.empty()))
                .waitSeconds(revealHoldSeconds)
                .countdown(5, count -> s -> SequenceEffects.showTitle(s.getPlayers(),
                        Component.text(count), Component.empty()))
                .then(20L, s -> {
                    SequenceEffects.showTitle(s.getPlayers(),
                            component(MSG.getMessage("round-start.selected-title", "gamemode", gamemodeName)),
                            component(MSG.getMessage(subtitleKey, "objective", objective)));
                    SoundUtils.playTo(s.getPlayers(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                })
                .waitSeconds(revealHoldSeconds)
                .run(Sequences::clearLock);
    }

    /**
     * Protect the President buy-phase reveal: blind + freeze all players, "Selecting
     * President...", a 5-second countdown, then reveal each team's own president to
     * only that team.
     */
    public static Sequence presidentReveal(ProtectThePresidentGamemode ptp) {
        // "Selecting President..." holds for as long as the reveal title does afterward, rather
        // than being visible for only ~1s before the countdown overwrites it.
        double revealHoldSeconds = 3.5;

        return Sequence.create()
                .run(s -> SequenceEffects.applyBlindness(s.getPlayers(), revealBlindnessTicks()))
                .pause(40)
                .run(s -> SequenceEffects.showTitle(s.getPlayers(),
                        component(MSG.getRaw("president.selecting")), Component.empty()))
                .waitSeconds(revealHoldSeconds)
                .countdown(5, count -> s -> SequenceEffects.showTitle(s.getPlayers(),
                        Component.text(count), Component.empty()))
                .then(20L, s -> {
                    revealPresidentToTeam(s.getTeamRed(), ptp.getPresident(1));
                    revealPresidentToTeam(s.getTeamBlue(), ptp.getPresident(2));
                })
                .waitSeconds(revealHoldSeconds)
                .run(Sequences::clearLock);
    }

    private static void revealPresidentToTeam(Team team, UUID presidentUuid) {
        Component title = component(MSG.getMessage("president.selected-title",
                "player_name", presidentName(presidentUuid)));
        Component subtitle = component(MSG.getRaw("president.selected-subtitle"));
        SequenceEffects.showTitle(team.getPlayers(), title, subtitle);
        SoundUtils.playTo(team.getPlayers(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    private static String presidentName(UUID uuid) {
        if (uuid == null) return "Unknown";
        Player p = Bukkit.getPlayer(uuid);
        return p != null ? p.getName() : "Unknown";
    }

    /**
     * "Determining Shield...", a 5-second countdown, then the actual outcome - "Everyone was
     * given a shield!" or "No shields were given out!" depending on this session's coin flip.
     * Played once at game start immediately after the round-start gamemode reveal (during buy
     * phase 1). The shield itself is granted per the caller's completion callback, not by this
     * sequence, so it always arrives after the reveal rather than silently beforehand.
     */
    public static Sequence shieldReveal() {
        double revealHoldSeconds = 2;

        return Sequence.create()
                .run(s -> SequenceEffects.applyBlindness(s.getPlayers(), revealBlindnessTicks()))
                .pause(20)
                .run(s -> SequenceEffects.showTitle(s.getPlayers(),
                        component(MSG.getRaw("shield-reveal.determining")), Component.empty()))
                .waitSeconds(revealHoldSeconds)
                .countdown(5, count -> s -> SequenceEffects.showTitle(s.getPlayers(),
                        Component.text(count), Component.empty()))
                .then(20L, s -> {
                    String key = s.hasShields() ? "shield-reveal.shields-given" : "shield-reveal.no-shields";
                    SequenceEffects.showTitle(s.getPlayers(), component(MSG.getRaw(key)), Component.empty());
                    SoundUtils.playTo(s.getPlayers(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                })
                .waitSeconds(3)
                .run(Sequences::clearLock);
    }

    /**
     * Round end: freeze input (no blindness), hold the win/loss result on screen for the
     * remainder of the 5-second window, then clear.
     */
    public static Sequence roundEnd(int winningTeam) {
        return Sequence.create()
                .run(s -> {
                    if (winningTeam != 1 && winningTeam != 2) {
                        // No clear winner (e.g. combat timer expired with nobody eliminated)
                        SequenceEffects.showTitle(s.getPlayers(),
                                component(MSG.getRaw("round-end.no-winner-title")), Component.empty());
                        SoundUtils.playTo(s.getPlayers(), Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.0f);
                        return;
                    }

                    Team winner = winningTeam == 1 ? s.getTeamRed() : s.getTeamBlue();
                    Team loser = s.getOpposingTeam(winner);
                    SequenceEffects.showTitle(winner.getPlayers(),
                            component(MSG.getRaw("round-end.win-title")), Component.empty());
                    SequenceEffects.showTitle(loser.getPlayers(),
                            component(MSG.getRaw("round-end.lose-title")), Component.empty());
                    SoundUtils.playTo(s.getPlayers(), Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.0f);
                })
                .waitSeconds(5)
                .run(s -> SequenceEffects.clearTitle(s.getPlayers()));
    }

    /**
     * Sudden death: no lock/blindness, just a short delay then a red announcement title.
     */
    public static Sequence suddenDeath(Gamemode gamemode) {
        String objective = gamemode.getObjectiveShort();

        return Sequence.create()
                .waitSeconds(1)
                .run(s -> {
                    SequenceEffects.showTitle(s.getPlayers(),
                            component(MSG.getRaw("sudden-death.title")),
                            component(MSG.getMessage("sudden-death.subtitle", "objective", objective)),
                            suddenDeathTimes());
                    SoundUtils.playTo(s.getPlayers(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                });
    }

    /**
     * Game victory: freeze input (no blindness), hold the win/loss result for 10s, clear
     * it, then hold another 10s before the caller performs the deferred teleport/cleanup.
     */
    public static Sequence gameVictory(Team winner) {
        return Sequence.create()
                .run(s -> {
                    Team loser = s.getOpposingTeam(winner);
                    Title.Times victoryTimes = victoryTimes();
                    SequenceEffects.showTitle(winner.getPlayers(),
                            component(MSG.getRaw("victory.win-title")), Component.empty(), victoryTimes);
                    SequenceEffects.showTitle(loser.getPlayers(),
                            component(MSG.getRaw("victory.lose-title")), Component.empty(), victoryTimes);
                    SoundUtils.playTo(s.getPlayers(), Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.0f);
                })
                .waitSeconds(10)
                .run(s -> SequenceEffects.clearTitle(s.getPlayers()))
                .waitSeconds(10);
    }

    private static String subtitleKeySuffixFor(GamemodeType type) {
        return switch (type) {
            case CAPTURE_THE_FLAG -> "selected-subtitle-ctf";
            case PROTECT_THE_PRESIDENT -> "selected-subtitle-ptp";
            case KILL_CONFIRM -> "selected-subtitle-kc";
        };
    }

    private static void clearLock(GameSession session) {
        SequenceEffects.clearBlindness(session.getPlayers());
        SequenceEffects.clearTitle(session.getPlayers());
    }

    private static Component component(String miniMessage) {
        return Messages.parse(miniMessage);
    }
}
