package me.psikuvit.cashClash.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.psikuvit.cashClash.chat.ChatManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listener for handling chat messages and routing them to appropriate channels.
 */
public class ChatListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Let ChatManager process the message
        boolean handled = ChatManager.getInstance().processMessage(player, message);

        if (handled) {
            // Message was sent to a specific channel, cancel the default broadcast
            event.setCancelled(true);
        }
    }
}

