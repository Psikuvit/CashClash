package me.psikuvit.cashClash.party;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all parties and party invitations.
 */
public class PartyManager {

    private static PartyManager instance;

    private final Set<Party> parties;
    private final Map<UUID, Party> playerPartyMap;
    private final Map<UUID, List<PartyInvite>> pendingInvites;
    private BukkitTask cleanupTask;

    private PartyManager() {
        this.parties = ConcurrentHashMap.newKeySet();
        this.playerPartyMap = new ConcurrentHashMap<>();
        this.pendingInvites = new ConcurrentHashMap<>();
        startCleanupTask();
    }

    public static PartyManager getInstance() {
        if (instance == null) {
            instance = new PartyManager();
        }
        return instance;
    }

    /**
     * Start a task to clean up expired invites.
     */
    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimer(CashClashPlugin.getInstance(), () -> {
            for (List<PartyInvite> invites : pendingInvites.values()) {
                invites.removeIf(PartyInvite::isExpired);
            }
            pendingInvites.values().removeIf(List::isEmpty);
        }, 20L * 30, 20L * 30); // Every 30 seconds
    }

    /**
     * Shutdown the manager.
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        parties.clear();
        playerPartyMap.clear();
        pendingInvites.clear();
    }

    // ==================== PARTY GETTERS/SETTERS ====================

    public Set<Party> getParties() {
        return Collections.unmodifiableSet(parties);
    }

    public Party getParty(UUID partyId) {
        return parties.stream()
                .filter(p -> p.getPartyId().equals(partyId))
                .findFirst()
                .orElse(null);
    }

    public Party getPlayerParty(UUID playerId) {
        return playerPartyMap.get(playerId);
    }

    public Party getPlayerParty(Player player) {
        return getPlayerParty(player.getUniqueId());
    }

    public boolean isInParty(UUID playerId) {
        return playerPartyMap.containsKey(playerId);
    }

    public boolean isInParty(Player player) {
        return isInParty(player.getUniqueId());
    }

    // ==================== PARTY OPERATIONS ====================

    /**
     * Create a new party with the given player as owner.
     */
    public Party createParty(Player owner) {
        if (isInParty(owner)) {
            Messages.send(owner, "<red>You are already in a party! Leave your current party first.</red>");
            return null;
        }

        Party party = new Party(owner.getUniqueId());
        parties.add(party);
        playerPartyMap.put(owner.getUniqueId(), party);

        Messages.send(owner, "<green>Party created! Use <yellow>/party invite <player></yellow> to invite players.</green>");
        return party;
    }

    /**
     * Disband a party.
     */
    public void disbandParty(Party party, Player disbander) {
        if (!party.isOwner(disbander.getUniqueId())) {
            Messages.send(disbander, "<red>Only the party owner can disband the party!</red>");
            return;
        }

        // Notify all members
        for (Player member : party.getOnlineMembers()) {
            if (!member.equals(disbander)) {
                Messages.send(member, "<red>The party has been disbanded by the owner.</red>");
            }
            playerPartyMap.remove(member.getUniqueId());
        }

        parties.remove(party);
        party.disband();

        Messages.send(disbander, "<yellow>Party disbanded.</yellow>");
    }

    /**
     * Leave a party.
     */
    public void leaveParty(Player player) {
        Party party = getPlayerParty(player);
        if (party == null) {
            Messages.send(player, "<red>You are not in a party!</red>");
            return;
        }

        UUID playerId = player.getUniqueId();

        if (party.isOwner(playerId)) {
            // Owner leaving - transfer or disband
            if (party.getSize() == 1) {
                disbandParty(party, player);
            } else {
                // Transfer to another member
                UUID newOwner = party.getMembers().stream()
                        .filter(id -> !id.equals(playerId))
                        .findFirst()
                        .orElse(null);

                if (newOwner != null) {
                    party.transferOwnership(newOwner);
                    party.removeMember(playerId);
                    playerPartyMap.remove(playerId);

                    Player newOwnerPlayer = Bukkit.getPlayer(newOwner);
                    if (newOwnerPlayer != null) {
                        Messages.send(newOwnerPlayer, "<gold>You are now the party owner!</gold>");
                    }

                    broadcastToParty(party, "<yellow>" + player.getName() + " has left the party. " +
                            (newOwnerPlayer != null ? newOwnerPlayer.getName() : "Someone") + " is now the owner.</yellow>");
                    Messages.send(player, "<yellow>You left the party.</yellow>");
                }
            }
        } else {
            party.removeMember(playerId);
            playerPartyMap.remove(playerId);

            broadcastToParty(party, "<yellow>" + player.getName() + " has left the party.</yellow>");
            Messages.send(player, "<yellow>You left the party.</yellow>");
        }
    }

    /**
     * Kick a player from a party.
     */
    public void kickPlayer(Player kicker, Player target) {
        Party party = getPlayerParty(kicker);
        if (party == null) {
            Messages.send(kicker, "<red>You are not in a party!</red>");
            return;
        }

        if (!party.isOwner(kicker.getUniqueId())) {
            Messages.send(kicker, "<red>Only the party owner can kick players!</red>");
            return;
        }

        if (!party.isMember(target.getUniqueId())) {
            Messages.send(kicker, "<red>That player is not in your party!</red>");
            return;
        }

        if (target.equals(kicker)) {
            Messages.send(kicker, "<red>You cannot kick yourself! Use /party leave or /party disband.</red>");
            return;
        }

        party.removeMember(target.getUniqueId());
        playerPartyMap.remove(target.getUniqueId());

        Messages.send(target, "<red>You have been kicked from the party!</red>");
        broadcastToParty(party, "<yellow>" + target.getName() + " has been kicked from the party.</yellow>");
    }

    /**
     * Transfer party ownership.
     */
    public void transferOwnership(Player owner, Player newOwner) {
        Party party = getPlayerParty(owner);
        if (party == null) {
            Messages.send(owner, "<red>You are not in a party!</red>");
            return;
        }

        if (!party.isOwner(owner.getUniqueId())) {
            Messages.send(owner, "<red>Only the party owner can transfer ownership!</red>");
            return;
        }

        if (!party.isMember(newOwner.getUniqueId())) {
            Messages.send(owner, "<red>That player is not in your party!</red>");
            return;
        }

        party.transferOwnership(newOwner.getUniqueId());
        broadcastToParty(party, "<gold>" + newOwner.getName() + " is now the party owner!</gold>");
    }

    // ==================== INVITE OPERATIONS ====================

    /**
     * Send a party invite.
     */
    public void invitePlayer(Player inviter, Player invitee) {
        Party party = getPlayerParty(inviter);

        // Auto-create party if not in one
        if (party == null) {
            party = createParty(inviter);
            if (party == null) return;
        }

        if (!party.isOwner(inviter.getUniqueId())) {
            Messages.send(inviter, "<red>Only the party owner can invite players!</red>");
            return;
        }

        if (invitee.equals(inviter)) {
            Messages.send(inviter, "<red>You cannot invite yourself!</red>");
            return;
        }

        if (isInParty(invitee)) {
            Messages.send(inviter, "<red>" + invitee.getName() + " is already in a party!</red>");
            return;
        }

        // Check for existing invite
        List<PartyInvite> invites = pendingInvites.computeIfAbsent(invitee.getUniqueId(), k -> new ArrayList<>());
        final Party finalParty = party;
        boolean hasExistingInvite = invites.stream()
                .anyMatch(i -> i.getPartyId().equals(finalParty.getPartyId()) && !i.isExpired());

        if (hasExistingInvite) {
            Messages.send(inviter, "<red>You already have a pending invite to this player!</red>");
            return;
        }

        // Create and store invite
        PartyInvite invite = new PartyInvite(inviter.getUniqueId(), invitee.getUniqueId(), party.getPartyId());
        invites.add(invite);

        Messages.send(inviter, "<green>Invite sent to " + invitee.getName() + "!</green>");

        // Send clickable invite message
        Component acceptButton = Messages.parse("<green><bold>[ACCEPT]</bold></green>")
                .clickEvent(ClickEvent.runCommand("/party accept " + inviter.getName()))
                .hoverEvent(HoverEvent.showText(Messages.parse("<green>Click to accept the invite</green>")));

        Component denyButton = Messages.parse("<red><bold>[DENY]</bold></red>")
                .clickEvent(ClickEvent.runCommand("/party deny " + inviter.getName()))
                .hoverEvent(HoverEvent.showText(Messages.parse("<red>Click to deny the invite</red>")));

        Component inviteMessage = Messages.parse("<gold>" + inviter.getName() + " has invited you to their party! </gold>")
                .append(acceptButton)
                .append(Messages.parse(" "))
                .append(denyButton);

        invitee.sendMessage(inviteMessage);
        Messages.send(invitee, "<gray>This invite expires in 60 seconds.</gray>");
    }

    /**
     * Accept a party invite.
     */
    public void acceptInvite(Player player, String inviterName) {
        List<PartyInvite> invites = pendingInvites.get(player.getUniqueId());
        if (invites == null || invites.isEmpty()) {
            Messages.send(player, "<red>You have no pending party invites!</red>");
            return;
        }

        // Find the invite from this inviter
        PartyInvite invite;
        Player inviter = Bukkit.getPlayer(inviterName);

        if (inviter != null) {
            invite = invites.stream()
                    .filter(i -> i.getInviter().equals(inviter.getUniqueId()) && !i.isExpired())
                    .findFirst()
                    .orElse(null);
        } else {
            // Try to find by name from offline cache or just take first valid invite
            invite = invites.stream()
                    .filter(i -> !i.isExpired())
                    .findFirst()
                    .orElse(null);
        }

        if (invite == null) {
            Messages.send(player, "<red>That invite has expired or doesn't exist!</red>");
            return;
        }

        if (isInParty(player)) {
            Messages.send(player, "<red>You are already in a party! Leave it first.</red>");
            return;
        }

        Party party = getParty(invite.getPartyId());
        if (party == null) {
            Messages.send(player, "<red>That party no longer exists!</red>");
            invites.remove(invite);
            return;
        }

        // Join the party
        party.addMember(player.getUniqueId());
        playerPartyMap.put(player.getUniqueId(), party);
        invites.remove(invite);

        broadcastToParty(party, "<green>" + player.getName() + " has joined the party!</green>");
    }

    /**
     * Deny a party invite.
     */
    public void denyInvite(Player player, String inviterName) {
        List<PartyInvite> invites = pendingInvites.get(player.getUniqueId());
        if (invites == null || invites.isEmpty()) {
            Messages.send(player, "<red>You have no pending party invites!</red>");
            return;
        }

        Player inviter = Bukkit.getPlayer(inviterName);
        PartyInvite invite = null;

        if (inviter != null) {
            invite = invites.stream()
                    .filter(i -> i.getInviter().equals(inviter.getUniqueId()))
                    .findFirst()
                    .orElse(null);
        }

        if (invite == null) {
            // Just remove first invite if no specific one found
            invite = invites.isEmpty() ? null : invites.getFirst();
        }

        if (invite != null) {
            invites.remove(invite);
            Messages.send(player, "<yellow>Invite denied.</yellow>");

            Player inviterPlayer = Bukkit.getPlayer(invite.getInviter());
            if (inviterPlayer != null) {
                Messages.send(inviterPlayer, "<red>" + player.getName() + " denied your party invite.</red>");
            }
        } else {
            Messages.send(player, "<red>No invite found to deny!</red>");
        }
    }

    /**
     * Get pending invites for a player.
     */
    public List<PartyInvite> getPendingInvites(UUID playerId) {
        List<PartyInvite> invites = pendingInvites.get(playerId);
        if (invites == null) return Collections.emptyList();
        invites.removeIf(PartyInvite::isExpired);
        return new ArrayList<>(invites);
    }

    // ==================== PARTY CHAT ====================

    /**
     * Send a message to all party members.
     */
    public void sendPartyMessage(Player sender, String message) {
        Party party = getPlayerParty(sender);
        if (party == null) {
            Messages.send(sender, "<red>You are not in a party!</red>");
            return;
        }

        Component chatMessage = Messages.parse("<dark_aqua>[Party] </dark_aqua>")
                .append(Messages.parse("<aqua>" + sender.getName() + "</aqua>"))
                .append(Messages.parse("<gray>: </gray>"))
                .append(Messages.parse("<white>" + message + "</white>"));

        for (Player member : party.getOnlineMembers()) {
            member.sendMessage(chatMessage);
        }
    }

    /**
     * Broadcast a system message to all party members.
     */
    public void broadcastToParty(Party party, String miniMessage) {
        Component message = Messages.parse(miniMessage);
        for (Player member : party.getOnlineMembers()) {
            member.sendMessage(message);
        }
    }

    // ==================== PARTY INFO ====================

    /**
     * Show party info to a player.
     */
    public void showPartyInfo(Player player) {
        Party party = getPlayerParty(player);
        if (party == null) {
            Messages.send(player, "<red>You are not in a party!</red>");
            Messages.send(player, "<gray>Create one with /party create or join one.</gray>");
            return;
        }

        Messages.send(player, "<gold>═══════ Party Info ═══════</gold>");

        Player ownerPlayer = party.getOwnerPlayer();
        String ownerName = ownerPlayer != null ? ownerPlayer.getName() : "Unknown";
        Messages.send(player, "<yellow>Owner:</yellow> <white>" + ownerName + "</white>");
        Messages.send(player, "<yellow>Members (" + party.getSize() + "):</yellow>");

        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            String name = member != null ? member.getName() : "Offline";
            String status = member != null && member.isOnline() ? "<green>●</green>" : "<red>●</red>";
            String role = party.isOwner(memberId) ? " <gold>★</gold>" : "";
            Messages.send(player, "  " + status + " <white>" + name + "</white>" + role);
        }

        Messages.send(player, "<gold>══════════════════════════</gold>");
    }
}

