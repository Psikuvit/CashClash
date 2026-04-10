package me.psikuvit.cashClash.scoreboard.context;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gamemode.impl.ProtectThePresidentGamemode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Scoreboard context for Protect the President gamemode
 * Displays president information and assassination counts
 */
public class PTPScoreboardContext extends GameScoreboardContext {

    @Override
    public List<String> getLines(Player player, GameSession session) {
        return Arrays.asList(
                "<gold><bold>Protect the President</bold></gold>",
                "",
                "<red>Team Red</red>",
                "President: {red_president}",
                "Assassinations: {red_assassinations}/4",
                "Coins: {teamRed_coins}",
                "",
                "<blue>Team Blue</blue>",
                "President: {blue_president}",
                "Assassinations: {blue_assassinations}/4",
                "Coins: {teamBlue_coins}",
                "",
                "<yellow>Phase</yellow>: {phase}",
                "Time: {time}",
                ""
        );
    }

    @Override
    protected String fillGamemodeSpecificPlaceholders(String line, Player player, GameSession session) {
        if (!(session.getGamemode() instanceof ProtectThePresidentGamemode ptp)) {
            return line;
        }

        UUID redPres = ptp.getPresident(1);
        UUID bluePres = ptp.getPresident(2);

        Player redPresPlayer = Bukkit.getPlayer(redPres);
        Player bluePresPlayer = Bukkit.getPlayer(bluePres);

        String redPresName = redPresPlayer != null ? redPresPlayer.getName() : "None";
        String bluePresName = bluePresPlayer != null ? bluePresPlayer.getName() : "None";

        line = line.replace("{red_president}", redPresName);
        line = line.replace("{blue_president}", bluePresName);

        int redAssassinations = ptp.getAssassinationCount(1);
        int blueAssassinations = ptp.getAssassinationCount(2);

        line = line.replace("{red_assassinations}", String.valueOf(redAssassinations));
        line = line.replace("{blue_assassinations}", String.valueOf(blueAssassinations));

        return line;
    }

    @Override
    public ContextType getContextType() {
        return ContextType.PTP;
    }
}

