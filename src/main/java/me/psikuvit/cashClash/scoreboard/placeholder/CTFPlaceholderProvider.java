package me.psikuvit.cashClash.scoreboard.placeholder;

import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Provides Capture the Flag specific placeholders
 */
public class CTFPlaceholderProvider implements PlaceholderProvider {

    private static final Set<String> SUPPORTED_PLACEHOLDERS = new HashSet<>();

    static {
        SUPPORTED_PLACEHOLDERS.add("teamRed_captures");
        SUPPORTED_PLACEHOLDERS.add("teamBlue_captures");
        SUPPORTED_PLACEHOLDERS.add("teamRed_capture_circles");
        SUPPORTED_PLACEHOLDERS.add("teamBlue_capture_circles");
        SUPPORTED_PLACEHOLDERS.add("red_flag_holder");
        SUPPORTED_PLACEHOLDERS.add("blue_flag_holder");
        SUPPORTED_PLACEHOLDERS.add("red_flag_status");
        SUPPORTED_PLACEHOLDERS.add("blue_flag_status");
    }

    private final CaptureTheFlagGamemode gamemode;

    public CTFPlaceholderProvider(CaptureTheFlagGamemode gamemode) {
        this.gamemode = gamemode;
    }

    @Override
    public String getValue(String placeholder, Player player) {
        if (gamemode == null) {
            return getDefaultValue(placeholder);
        }

        return switch (placeholder) {
            case "teamRed_captures" -> String.valueOf(gamemode.getFlagCaptures(1));
            case "teamBlue_captures" -> String.valueOf(gamemode.getFlagCaptures(2));
            case "teamRed_capture_circles" -> getCaptureCircles(gamemode.getFlagCaptures(1));
            case "teamBlue_capture_circles" -> getCaptureCircles(gamemode.getFlagCaptures(2));
            case "red_flag_holder" -> getFlagHolderName(gamemode.getFlagHolder(1));
            case "blue_flag_holder" -> getFlagHolderName(gamemode.getFlagHolder(2));
            case "red_flag_status" -> getFlagStatus(gamemode.getFlagHolder(1));
            case "blue_flag_status" -> getFlagStatus(gamemode.getFlagHolder(2));
            default -> null;
        };
    }

    @Override
    public boolean handles(String placeholder) {
        return SUPPORTED_PLACEHOLDERS.contains(placeholder);
    }

    private String getCaptureCircles(int captures) {
        StringBuilder sb = new StringBuilder();
        int maxCaptures = 2;
        for (int i = 0; i < maxCaptures; i++) {
            if (i < captures) {
                sb.append("<green>●</green>");
            } else {
                sb.append("<gray>○</gray>");
            }
            if (i < maxCaptures - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private String getFlagHolderName(UUID holderUuid) {
        if (holderUuid == null) {
            return "At Base";
        }
        Player holder = Bukkit.getPlayer(holderUuid);
        return holder != null ? holder.getName() : "Unknown";
    }

    private String getFlagStatus(UUID holderUuid) {
        if (holderUuid == null) {
            return "<green>Safe</green>";
        }
        Player holder = Bukkit.getPlayer(holderUuid);
        String holderName = holder != null ? holder.getName() : "Unknown";
        return "<red>Taken by " + holderName + "</red>";
    }

    private String getDefaultValue(String placeholder) {
        return switch (placeholder) {
            case "teamRed_captures", "teamBlue_captures" -> "0";
            case "teamRed_capture_circles", "teamBlue_capture_circles" -> getCaptureCircles(0);
            case "red_flag_holder", "blue_flag_holder" -> "At Base";
            case "red_flag_status", "blue_flag_status" -> "<green>Safe</green>";
            default -> null;
        };
    }
}

