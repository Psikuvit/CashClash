package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.listener.BlockListener;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Admin command to toggle water/lava flow behavior.
 * Usage: /cc flow
 */
public class FlowCommand extends AbstractArgCommand {

    public FlowCommand() {
        super("flow", Collections.emptyList(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        BlockListener.toggleFlow();
        boolean enabled = BlockListener.isFlowEnabled();
        
        if (enabled) {
            Messages.send(sender, "<green>Water/Lava flow is now <bold>ENABLED</bold>. Water/Lava can spread to break webs.</green>");
        } else {
            Messages.send(sender, "<red>Water/Lava flow is now <bold>DISABLED</bold>. Water/Lava cannot spread.</red>");
        }
        
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

