package me.psikuvit.cashClash.scoreboard.context;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Scoreboard context for Capture the Flag gamemode
 * Displays flag captures, holders, and progress circles
 */
public class CTFScoreboardContext extends GameScoreboardContext {

    @Override
    public Component getTitle(Player player, GameSession session) {
        String titleRaw = ConfigManager.getInstance().getCTFScoreboardTitle();
        return Messages.parse(fillPlaceholders(titleRaw, player, session));
    }

    @Override
    public List<String> getLines(Player player, GameSession session) {
        return ConfigManager.getInstance().getCTFScoreboardLines();
    }

    @Override
    protected String fillGamemodeSpecificPlaceholders(String line, Player player, GameSession session) {
        if (!(session.getGamemode() instanceof CaptureTheFlagGamemode ctf)) {
            return line;
        }

        int teamRedCaptures = ctf.getFlagCaptures(1);
        int teamBlueCaptures = ctf.getFlagCaptures(2);

        line = line.replace("{teamRed_captures}", String.valueOf(teamRedCaptures));
        line = line.replace("{teamBlue_captures}", String.valueOf(teamBlueCaptures));

        String teamRedCircle = getFlagCaptureCircles(teamRedCaptures);
        String teamBlueCircle = getFlagCaptureCircles(teamBlueCaptures);

        // Replace circles - check length first
        String withRedCircles = line.replace("{teamRed_capture_circles}", teamRedCircle);
        String withBlueCircles = line.replace("{teamBlue_capture_circles}", teamBlueCircle);

        // Only use circles if they don't exceed 40 chars
        if (withRedCircles.length() <= 40) {
            line = withRedCircles;
        } else {
            line = line.replace("{teamRed_capture_circles}", "");
        }

        if (withBlueCircles.length() <= 40) {
            line = withBlueCircles;
        } else {
            line = line.replace("{teamBlue_capture_circles}", "");
        }

        UUID redHolder = ctf.getFlagHolder(1);
        UUID blueHolder = ctf.getFlagHolder(2);

        // Safely get players from UUIDs
        Player redHolderPlayer = redHolder != null ? Bukkit.getPlayer(redHolder) : null;
        Player blueHolderPlayer = blueHolder != null ? Bukkit.getPlayer(blueHolder) : null;

        String redHolderName = redHolderPlayer != null ? redHolderPlayer.getName() : "None";
        String blueHolderName = blueHolderPlayer != null ? blueHolderPlayer.getName() : "None";

        line = line.replace("{red_flag_holder}", redHolderName);
        line = line.replace("{blue_flag_holder}", blueHolderName);

        // Debug logging for Red Team data
        if (line.contains("Red")) {
            Messages.debug("[CTF] Red team data line: " + line + " (length: " + line.length() + ")");
        }

        return line;
    }

    private String getFlagCaptureCircles(int captures) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            if (i < captures) {
                sb.append("<green>●</green>");
            } else {
                sb.append("<gray>○</gray>");
            }
            if (i < 2 - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    @Override
    public ContextType getContextType() {
        return ContextType.CTF;
    }
}


