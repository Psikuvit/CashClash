package me.psikuvit.cashClash.chat;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.party.Party;
import me.psikuvit.cashClash.party.PartyManager;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player chat channels and message routing.
 */
public class ChatManager {

    private static ChatManager instance;

    private final Map<UUID, ChatChannel> playerChannels;

    private ChatManager() {
        this.playerChannels = new ConcurrentHashMap<>();
    }

    public static ChatManager getInstance() {
        if (instance == null) {
            instance = new ChatManager();
        }
        return instance;
    }

    /**
     * Get a player's current chat channel.
     */
    public ChatChannel getPlayerChannel(Player player) {
        return playerChannels.getOrDefault(player.getUniqueId(), ChatChannel.GLOBAL);
    }

    /**
     * Set a player's chat channel.
     */
    public void setPlayerChannel(Player player, ChatChannel channel) {
        if (channel == ChatChannel.GLOBAL) {
            playerChannels.remove(player.getUniqueId());
        } else {
            playerChannels.put(player.getUniqueId(), channel);
        }
    }

    /**
     * Toggle between global and the specified channel.
     */
    public void toggleChannel(Player player, ChatChannel channel) {
        ChatChannel current = getPlayerChannel(player);
        if (current == channel) {
            setPlayerChannel(player, ChatChannel.GLOBAL);
            Messages.send(player, "chat.switched-to-global");
        } else {
            setPlayerChannel(player, channel);
            Messages.send(player, "chat.switched-to-channel",
                "channel_color", channel.getNameColor(),
                "channel_name", channel.getDisplayName());
        }
    }

    /**
     * Process and route a chat message.
     * @return true if the message was handled, false if it should go to default chat
     */
    public boolean processMessage(Player sender, String message) {
        ChatChannel channel = getPlayerChannel(sender);

        // Check for quick channel prefixes
        if (message.startsWith("@p ") || message.startsWith("@party ")) {
            String actualMessage = message.startsWith("@p ") ? message.substring(3) : message.substring(7);
            return sendPartyMessage(sender, actualMessage);
        }

        if (message.startsWith("@t ") || message.startsWith("@team ")) {
            String actualMessage = message.startsWith("@t ") ? message.substring(3) : message.substring(6);
            return sendTeamMessage(sender, actualMessage);
        }

        if (message.startsWith("@g ") || message.startsWith("@game ")) {
            String actualMessage = message.startsWith("@g ") ? message.substring(3) : message.substring(6);
            return sendGameMessage(sender, actualMessage);
        }

        if (message.startsWith("@a ") || message.startsWith("@all ")) {
            // Force global chat - let default handler process it
            return false;
        }

        // Route based on current channel
        return switch (channel) {
            case PARTY -> sendPartyMessage(sender, message);
            case TEAM -> sendTeamMessage(sender, message);
            case GAME -> sendGameMessage(sender, message);
            case GLOBAL -> false; // Let default chat handler process it
        };
    }

    /**
     * Send a message to party members.
     */
    public boolean sendPartyMessage(Player sender, String message) {
        Party party = PartyManager.getInstance().getPlayerParty(sender);
        if (party == null) {
            Messages.send(sender, "chat.not-in-party-message");
            return true;
        }

        Component chatMessage = Messages.parse(ChatChannel.PARTY.getPrefix())
                .append(Messages.parse(ChatChannel.PARTY.getNameColor() + sender.getName()))
                .append(Messages.parse("<gray>: </gray>"))
                .append(Messages.parse("<white>" + message + "</white>"));

        for (Player member : party.getOnlineMembers()) {
            member.sendMessage(chatMessage);
        }
        return true;
    }

    /**
     * Send a message to team members in a game.
     */
    public boolean sendTeamMessage(Player sender, String message) {
        GameSession session = GameManager.getInstance().getPlayerSession(sender);
        if (session == null) {
            Messages.send(sender, "chat.not-in-game-message");
            return true;
        }

        Team team = session.getPlayerTeam(sender);
        if (team == null) {
            Messages.send(sender, "chat.not-on-team-message");
            return true;
        }
        return false;
    }

    /**
     * Send a message to all players in the same game.
     */
    public boolean sendGameMessage(Player sender, String message) {
        GameSession session = GameManager.getInstance().getPlayerSession(sender);
        if (session == null) {
            Messages.send(sender, "chat.not-in-game-message");
            return true;
        }
        return false;
    }

    /**
     * Send a global message (returns false to let default handler process).
     */
    public boolean sendGlobalMessage(Player sender, String message) {
        // Return false to let default Bukkit chat handler process it
        return false;
    }

    /**
     * Clear a player's channel preference (on disconnect).
     */
    public void clearPlayer(UUID playerId) {
        playerChannels.remove(playerId);
    }

    /**
     * Shutdown the chat manager.
     */
    public void shutdown() {
        playerChannels.clear();
    }
}

