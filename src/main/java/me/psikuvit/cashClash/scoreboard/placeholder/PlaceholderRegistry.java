package me.psikuvit.cashClash.scoreboard.placeholder;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
import me.psikuvit.cashClash.gamemode.impl.ProtectThePresidentGamemode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central registry for managing all placeholder providers
 * Handles placeholder parsing and replacement
 */
public class PlaceholderRegistry {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)}");
    private final List<PlaceholderProvider> providers = new ArrayList<>();

    /**
     * Create and initialize a registry for a game session
     */
    public static PlaceholderRegistry forGameSession(GameSession session, Player player) {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.registerProvider(new CommonGamePlaceholderProvider(session));

        if (session.getGamemode() instanceof CaptureTheFlagGamemode ctf) {
            registry.registerProvider(new CTFPlaceholderProvider(ctf));
        } else if (session.getGamemode() instanceof ProtectThePresidentGamemode ptp) {
            registry.registerProvider(new PTPPlaceholderProvider(ptp));
        }

        return registry;
    }

    /**
     * Create and initialize a registry for the lobby
     */
    public static PlaceholderRegistry forLobby() {
        PlaceholderRegistry registry = new PlaceholderRegistry();
        registry.registerProvider(new LobbyPlaceholderProvider());
        return registry;
    }

    /**
     * Register a placeholder provider
     */
    public void registerProvider(PlaceholderProvider provider) {
        if (provider != null) {
            providers.add(provider);
        }
    }

    /**
     * Fill all placeholders in a line
     * Uses all registered providers to find and replace placeholders
     * If a placeholder is not found, it gets a default value to prevent literal display
     */
    public String fillPlaceholders(String line, Player player) {
        if (line == null || line.isEmpty()) {
            return line;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(line);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String placeholderName = matcher.group(1);
            String replacement = resolvePlaceholder(placeholderName, player);

            // Escape special regex characters in the replacement
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolve a single placeholder value
     */
    private String resolvePlaceholder(String placeholder, Player player) {
        // Try each provider in order
        for (PlaceholderProvider provider : providers) {
            if (provider.handles(placeholder)) {
                String value = provider.getValue(placeholder, player);
                if (value != null) {
                    return value;
                }
            }
        }

        // If no provider handled it, return the placeholder surrounded by brackets
        // This makes it easier to spot unhandled placeholders
        return "{" + placeholder + "}";
    }

    /**
     * Get a list of all supported placeholders
     */
    public List<String> getSupportedPlaceholders() {
        List<String> supported = new ArrayList<>();
        for (PlaceholderProvider provider : providers) {
            if (provider instanceof CommonGamePlaceholderProvider) {
                addCommonPlaceholders(supported);
            } else if (provider instanceof CTFPlaceholderProvider) {
                addCTFPlaceholders(supported);
            } else if (provider instanceof PTPPlaceholderProvider) {
                addPTPPlaceholders(supported);
            } else if (provider instanceof LobbyPlaceholderProvider) {
                addLobbyPlaceholders(supported);
            }
        }
        return supported;
    }

    private void addCommonPlaceholders(List<String> list) {
        list.addAll(List.of(
            "{phase}", "{state}", "{phase_number}", "{time}", "{time_seconds}",
            "{teamRed_coins}", "{teamBlue_coins}",
            "{your_team}", "{your_team_coins}", "{your_team_ready}",
            "{enemy_team}", "{enemy_team_coins}", "{enemy_team_ready}",
            "{player_coins}", "{player_kills}", "{player_lives}", "{player_deaths}", "{kill_streak}",
            "{round_kills}", "{teamRed_alive}", "{teamBlue_alive}",
            "{your_team_alive}", "{enemy_team_alive}",
            "{round}", "{players}"
        ));
    }

    private void addCTFPlaceholders(List<String> list) {
        list.addAll(List.of(
            "{teamRed_captures}", "{teamBlue_captures}",
            "{teamRed_capture_circles}", "{teamBlue_capture_circles}",
            "{red_flag_holder}", "{blue_flag_holder}",
            "{red_flag_status}", "{blue_flag_status}"
        ));
    }

    private void addPTPPlaceholders(List<String> list) {
        list.addAll(List.of(
            "{red_president}", "{blue_president}",
            "{red_president_status}", "{blue_president_status}",
            "{red_assassinations}", "{blue_assassinations}",
            "{red_buff}", "{blue_buff}",
            "{red_assassination_circles}", "{blue_assassination_circles}"
        ));
    }

    private void addLobbyPlaceholders(List<String> list) {
        list.addAll(List.of(
            "{player}", "{online}", "{max_online}",
            "{wins}", "{losses}", "{kills}", "{deaths}",
            "{total_coins_earned}", "{kd_ratio}", "{win_rate}"
        ));
    }
}

