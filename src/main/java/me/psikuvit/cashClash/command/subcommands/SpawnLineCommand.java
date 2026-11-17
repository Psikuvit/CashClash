package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * /cc spawnline [density] - sets how many particles every map's spawn lines are drawn with
 * (1.0 standard, 0 none, 2 double), saved to config.yml. With no argument it shows the current
 * value.
 */
public class SpawnLineCommand extends AbstractArgCommand {

    public SpawnLineCommand() {
        super("spawnline", Collections.emptyList(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        ConfigManager cfg = CashClashPlugin.getInstance().getConfigManager();
        if (args.length == 0) {
            Messages.send(sender, "admin.spawnline-current", "density", String.valueOf(cfg.getSpawnLineParticleDensity()));
            return true;
        }

        double density;
        try {
            density = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            Messages.send(sender, "admin.spawnline-usage");
            return true;
        }
        if (density < 0 || !Double.isFinite(density)) {
            Messages.send(sender, "admin.spawnline-usage");
            return true;
        }

        cfg.setSpawnLineParticleDensity(density);
        Messages.send(sender, "admin.spawnline-set", "density", String.valueOf(density));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 1) return Collections.emptyList();
        return List.of("0", "0.5", "1.0", "2.0").stream().filter(s -> s.startsWith(args[0])).toList();
    }
}
