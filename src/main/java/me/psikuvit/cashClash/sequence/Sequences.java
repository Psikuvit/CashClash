package me.psikuvit.cashClash.sequence;

import me.psikuvit.cashClash.config.SequencesConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.gamemode.impl.ProtectThePresidentGamemode;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;

/**
 * Concrete {@link Sequence} factories for each scripted game moment. Callers drive
 * locking/blindness via {@link SequenceManager#play}; the sequences here only handle
 * timing and title content.
 */
public final class Sequences {

    private static final SequencesConfig MSG = SequencesConfig.getInstance();

    // Generous blindness duration for "reveal"-style sequences; always cleared explicitly
    // at the end of the sequence, so it only needs to outlast the sequence itself.
    private static final int REVEAL_BLINDNESS_TICKS = 300;

    private static final Title.Times SUDDEN_DEATH_TIMES = Title.Times.times(
            Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMillis(500));

    private static final Title.Times VICTORY_TIMES = Title.Times.times(
            Duration.ofMillis(500), Duration.ofSeconds(9), Duration.ofMillis(500));

    private Sequences() {
        throw new AssertionError("Nope.");
    }

    /**
     * Round 1 start: blind + freeze all players, "Selecting Gamemode...", a 5-second
     * countdown, then reveal the chosen gamemode and its objective.
     */
    public static Sequence roundStart(Gamemode gamemode) {
        String gamemodeName = gamemode.getType().getDisplayName();
        String objective = gamemode.getObjectiveShort();

        return Sequence.create()
                .run(s -> SequenceEffects.applyBlindness(s.getPlayers(), REVEAL_BLINDNESS_TICKS))
                .pause(40)
                .run(s -> SequenceEffects.showTitle(s.getPlayers(),
                        component(MSG.getRaw("round-start.selecting")), Component.empty()))
                .countdown(5, count -> s -> SequenceEffects.showTitle(s.getPlayers(),
                        Component.text(count), Component.empty()))
                .then(20L, s -> SequenceEffects.showTitle(s.getPlayers(),
                        component(MSG.getMessage("round-start.selected-title", "gamemode", gamemodeName)),
                        component(MSG.getMessage("round-start.selected-subtitle", "objective", objective))))
                .waitSeconds(4)
                .run(Sequences::clearLock);
    }

    /**
     * Protect the President buy-phase reveal: blind + freeze all players, "Selecting
     * President...", a 5-second countdown, then reveal each team's own president to
     * only that team.
     */
    public static Sequence presidentReveal(ProtectThePresidentGamemode ptp) {
        return Sequence.create()
                .run(s -> SequenceEffects.applyBlindness(s.getPlayers(), REVEAL_BLINDNESS_TICKS))
                .pause(40)
                .run(s -> SequenceEffects.showTitle(s.getPlayers(),
                        component(MSG.getRaw("president.selecting")), Component.empty()))
                .countdown(5, count -> s -> SequenceEffects.showTitle(s.getPlayers(),
                        Component.text(count), Component.empty()))
                .then(20L, s -> {
                    revealPresidentToTeam(s.getTeamRed(), ptp.getPresident(1));
                    revealPresidentToTeam(s.getTeamBlue(), ptp.getPresident(2));
                })
                .waitSeconds(3.5)
                .run(Sequences::clearLock);
    }

    private static void revealPresidentToTeam(Team team, UUID presidentUuid) {
        Component title = component(MSG.getMessage("president.selected-title",
                "player_name", presidentName(presidentUuid)));
        Component subtitle = component(MSG.getRaw("president.selected-subtitle"));
        SequenceEffects.showTitle(team.getPlayers(), title, subtitle);
    }

    private static String presidentName(UUID uuid) {
        if (uuid == null) return "Unknown";
        Player p = Bukkit.getPlayer(uuid);
        return p != null ? p.getName() : "Unknown";
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
                        return;
                    }

                    Team winner = winningTeam == 1 ? s.getTeamRed() : s.getTeamBlue();
                    Team loser = s.getOpposingTeam(winner);
                    SequenceEffects.showTitle(winner.getPlayers(),
                            component(MSG.getRaw("round-end.win-title")), Component.empty());
                    SequenceEffects.showTitle(loser.getPlayers(),
                            component(MSG.getRaw("round-end.lose-title")), Component.empty());
                })
                .waitSeconds(5)
                .run(s -> SequenceEffects.clearTitle(s.getPlayers()));
    }

    /**
     * Round 4 buy phase: blind + freeze, announce the shield/shieldless half transition.
     */
    public static Sequence round4ShieldTransition() {
        return Sequence.create()
                .run(s -> SequenceEffects.applyBlindness(s.getPlayers(), REVEAL_BLINDNESS_TICKS))
                .pause(40)
                .run(s -> {
                    SequenceEffects.showTitle(s.getPlayers(),
                            component(MSG.getRaw("round4.title")),
                            component(MSG.getRaw("round4.subtitle")));
                    boolean round4HasShield = !s.hasShieldsInRounds1to3();
                    Messages.broadcast(s.getPlayers(),
                            round4HasShield ? "round.round4-shield-chat" : "round.round4-shieldless-chat");
                })
                .waitSeconds(5)
                .run(Sequences::clearLock);
    }

    /**
     * Sudden death: no lock/blindness, just a short delay then a red announcement title.
     */
    public static Sequence suddenDeath(Gamemode gamemode) {
        String objective = gamemode.getObjectiveShort();

        return Sequence.create()
                .waitSeconds(1)
                .run(s -> SequenceEffects.showTitle(s.getPlayers(),
                        component(MSG.getRaw("sudden-death.title")),
                        component(MSG.getMessage("sudden-death.subtitle", "objective", objective)),
                        SUDDEN_DEATH_TIMES));
    }

    /**
     * Game victory: freeze input (no blindness), hold the win/loss result for 10s, clear
     * it, then hold another 10s before the caller performs the deferred teleport/cleanup.
     */
    public static Sequence gameVictory(Team winner) {
        return Sequence.create()
                .run(s -> {
                    Team loser = s.getOpposingTeam(winner);
                    SequenceEffects.showTitle(winner.getPlayers(),
                            component(MSG.getRaw("victory.win-title")), Component.empty(), VICTORY_TIMES);
                    SequenceEffects.showTitle(loser.getPlayers(),
                            component(MSG.getRaw("victory.lose-title")), Component.empty(), VICTORY_TIMES);
                })
                .waitSeconds(10)
                .run(s -> SequenceEffects.clearTitle(s.getPlayers()))
                .waitSeconds(10);
    }

    private static void clearLock(GameSession session) {
        SequenceEffects.clearBlindness(session.getPlayers());
        SequenceEffects.clearTitle(session.getPlayers());
    }

    private static Component component(String miniMessage) {
        return Messages.parse(miniMessage);
    }
}
