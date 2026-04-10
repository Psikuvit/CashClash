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

        line = line.replace("{teamRed_capture_circles}", teamRedCircle);
        line = line.replace("{teamBlue_capture_circles}", teamBlueCircle);

        UUID redHolder = ctf.getFlagHolder(1);
        UUID blueHolder = ctf.getFlagHolder(2);

        Player redHolderPlayer = Bukkit.getPlayer(redHolder);
        Player blueHolderPlayer = Bukkit.getPlayer(blueHolder);

        String redHolderName = redHolderPlayer != null ? redHolderPlayer.getName() : "None";
        String blueHolderName = blueHolderPlayer != null ? blueHolderPlayer.getName() : "None";

        line = line.replace("{red_flag_holder}", redHolderName);
        line = line.replace("{blue_flag_holder}", blueHolderName);

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

