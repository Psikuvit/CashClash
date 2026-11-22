package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.List;
import java.util.Locale;

/**
 * Commands only operators may run, from config.yml's {@code op-only-commands} - typically other
 * plugins' commands (/banner, /armorcolor) this plugin can't set a permission on. Anyone else is
 * refused, and the commands are left out of their command list so auto-fill never offers them.
 */
public class OpOnlyCommandListener implements Listener {

    private final ConfigManager configManager;

    public OpOnlyCommandListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        String label = event.getMessage().substring(1).split(" ", 2)[0];
        if (isOpOnly(label)) {
            event.setCancelled(true);
            Messages.send(player, "command.no-permission");
        }
    }

    @EventHandler
    public void onCommandList(PlayerCommandSendEvent event) {
        if (!event.getPlayer().isOp()) {
            event.getCommands().removeIf(this::isOpOnly);
        }
    }

    /**
     * Whether a typed command label is one of them, with or without its plugin prefix
     * ({@code banner} or {@code someplugin:banner}).
     */
    private boolean isOpOnly(String label) {
        String name = label.toLowerCase(Locale.ROOT);
        name = name.substring(name.indexOf(':') + 1);
        List<String> opOnly = configManager.getOpOnlyCommands();
        return opOnly.stream().anyMatch(name::equalsIgnoreCase);
    }
}
