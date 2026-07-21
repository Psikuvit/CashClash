package me.psikuvit.cashClash.scoreboard.placeholder;

import me.psikuvit.cashClash.gamemode.impl.KillConfirmGamemode;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides Kill Confirm specific placeholders
 */
public class KCPlaceholderProvider implements PlaceholderProvider {

    private static final int WIN_CONDITION = 16;
    private static final Set<String> SUPPORTED_PLACEHOLDERS = new HashSet<>();

    static {
        SUPPORTED_PLACEHOLDERS.add("teamRed_kc_score");
        SUPPORTED_PLACEHOLDERS.add("teamBlue_kc_score");
        SUPPORTED_PLACEHOLDERS.add("teamRed_kc_progress");
        SUPPORTED_PLACEHOLDERS.add("teamBlue_kc_progress");
    }

    private final KillConfirmGamemode gamemode;

    public KCPlaceholderProvider(KillConfirmGamemode gamemode) {
        this.gamemode = gamemode;
    }

    @Override
    public String getValue(String placeholder, Player player) {
        if (gamemode == null) {
            return getDefaultValue(placeholder);
        }

        return switch (placeholder) {
            case "teamRed_kc_score" -> String.valueOf(gamemode.getTeamScore(1));
            case "teamBlue_kc_score" -> String.valueOf(gamemode.getTeamScore(2));
            case "teamRed_kc_progress" -> gamemode.getTeamScore(1) + "/" + WIN_CONDITION;
            case "teamBlue_kc_progress" -> gamemode.getTeamScore(2) + "/" + WIN_CONDITION;
            default -> null;
        };
    }

    @Override
    public boolean handles(String placeholder) {
        return SUPPORTED_PLACEHOLDERS.contains(placeholder);
    }

    private String getDefaultValue(String placeholder) {
        return switch (placeholder) {
            case "teamRed_kc_score", "teamBlue_kc_score" -> "0";
            case "teamRed_kc_progress", "teamBlue_kc_progress" -> "0/" + WIN_CONDITION;
            default -> null;
        };
    }
}
