package me.psikuvit.cashClash.scoreboard.placeholder;

import me.psikuvit.cashClash.gamemode.impl.ProtectThePresidentGamemode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Provides Protect the President specific placeholders
 */
public class PTPPlaceholderProvider implements PlaceholderProvider {

    private static final Set<String> SUPPORTED_PLACEHOLDERS = new HashSet<>();

    static {
        SUPPORTED_PLACEHOLDERS.add("red_president");
        SUPPORTED_PLACEHOLDERS.add("blue_president");
        SUPPORTED_PLACEHOLDERS.add("red_president_status");
        SUPPORTED_PLACEHOLDERS.add("blue_president_status");
        SUPPORTED_PLACEHOLDERS.add("red_assassinations");
        SUPPORTED_PLACEHOLDERS.add("blue_assassinations");
        SUPPORTED_PLACEHOLDERS.add("red_buff");
        SUPPORTED_PLACEHOLDERS.add("blue_buff");
        SUPPORTED_PLACEHOLDERS.add("red_assassination_circles");
        SUPPORTED_PLACEHOLDERS.add("blue_assassination_circles");
    }

    private final ProtectThePresidentGamemode gamemode;

    public PTPPlaceholderProvider(ProtectThePresidentGamemode gamemode) {
        this.gamemode = gamemode;
    }

    @Override
    public String getValue(String placeholder, Player player) {
        if (gamemode == null) {
            return getDefaultValue(placeholder);
        }

        return switch (placeholder) {
            case "red_president" -> getPresidentName(gamemode.getPresident(1));
            case "blue_president" -> getPresidentName(gamemode.getPresident(2));
            case "red_president_status" -> getPresidentStatus(gamemode.getPresident(1));
            case "blue_president_status" -> getPresidentStatus(gamemode.getPresident(2));
            case "red_assassinations" -> String.valueOf(gamemode.getAssassinationCount(1));
            case "blue_assassinations" -> String.valueOf(gamemode.getAssassinationCount(2));
            case "red_buff" -> gamemode.getPresidentBuff(1);
            case "blue_buff" -> gamemode.getPresidentBuff(2);
            case "red_assassination_circles" -> getAssassinationCircles(gamemode.getAssassinationCount(1));
            case "blue_assassination_circles" -> getAssassinationCircles(gamemode.getAssassinationCount(2));
            default -> null;
        };
    }

    @Override
    public boolean handles(String placeholder) {
        return SUPPORTED_PLACEHOLDERS.contains(placeholder);
    }

    private String getPresidentName(UUID presidentUuid) {
        if (presidentUuid == null) {
            return "None";
        }
        Player president = Bukkit.getPlayer(presidentUuid);
        return president != null ? president.getName() : "Unknown";
    }

    private String getPresidentStatus(UUID presidentUuid) {
        if (presidentUuid == null) {
            return "<red>No President</red>";
        }
        Player president = Bukkit.getPlayer(presidentUuid);
        if (president == null) {
            return "<red>Offline</red>";
        }
        return "<green>Active (" + president.getName() + ")</green>";
    }

    private String getAssassinationCircles(int assassinations) {
        StringBuilder sb = new StringBuilder();
        int maxAssassinations = 2; // Adjust based on your game logic
        for (int i = 0; i < maxAssassinations; i++) {
            if (i < assassinations) {
                sb.append("<red>✕</red>");
            } else {
                sb.append("<gray>○</gray>");
            }
            if (i < maxAssassinations - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private String getDefaultValue(String placeholder) {
        return switch (placeholder) {
            case "red_president", "blue_president" -> "None";
            case "red_president_status", "blue_president_status" -> "<red>No President</red>";
            case "red_assassinations", "blue_assassinations" -> "0";
            case "red_buff", "blue_buff" -> "None";
            case "red_assassination_circles", "blue_assassination_circles" -> getAssassinationCircles(0);
            default -> null;
        };
    }
}

