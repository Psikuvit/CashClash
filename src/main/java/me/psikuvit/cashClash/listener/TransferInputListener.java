package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.gui.TransferGUI;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for handling transfer money input via sign GUI.
 */
public class TransferInputListener implements Listener {

    private static final double TRANSFER_FEE = 0.10; // 10% fee
    private static TransferInputListener instance;

    private final Map<UUID, PendingTransfer> pendingTransfers;
    private final Map<UUID, Block> signBlocks;

    public TransferInputListener() {
        // Register this listener
        this.pendingTransfers = new ConcurrentHashMap<>();
        this.signBlocks = new ConcurrentHashMap<>();
    }

    public static TransferInputListener getInstance() {
        if (instance == null) {
            instance = new TransferInputListener();
        }
        return instance;
    }

    /**
     * Start a transfer input session for a player using sign input.
     */
    public void startTransferInput(Player sender, Player receiver, GameSession session) {
        pendingTransfers.put(sender.getUniqueId(), new PendingTransfer(receiver.getUniqueId(), session.getSessionId()));

        // Create and show sign input
        Block signBlock = createSignBlock(sender);

        signBlocks.put(sender.getUniqueId(), signBlock);

        // Set up the sign with prompt text
        Sign sign = (Sign) signBlock.getState();
        sign.getSide(Side.FRONT).line(0, Component.empty());
        sign.getSide(Side.FRONT).line(1, Messages.parse("<dark_gray>^^^^^^^^</dark_gray>"));
        sign.getSide(Side.FRONT).line(2, Messages.parse("<gray>Enter amount</gray>"));
        sign.getSide(Side.FRONT).line(3, Messages.parse("<gray>Fee: 10%</gray>"));
        sign.update();

        // Open sign editor
        sender.openSign(sign, Side.FRONT);
    }

    /**
     * Creates a temporary sign block for input.
     */
    private Block createSignBlock(Player player) {
        var loc = player.getLocation().clone();
        // Place sign below the player where they can't see it
        loc.setY(Math.max(player.getWorld().getMinHeight(), loc.getBlockY() - 3));

        Block block = loc.getBlock();
        block.setType(Material.OAK_SIGN);

        return block;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!pendingTransfers.containsKey(playerId)) return;

        // Cancel the event so the sign doesn't actually get changed in world
        event.setCancelled(true);

        // Get the input from the first line
        Component line0 = event.line(0);
        String amountStr = line0 != null ? PlainTextComponentSerializer.plainText().serialize(line0).trim() : "";

        // Clean up the sign block
        Block signBlock = signBlocks.remove(playerId);
        if (signBlock != null) {
            Bukkit.getScheduler().runTask(CashClashPlugin.getInstance(),
                    () -> signBlock.setType(Material.AIR));
        }

        // Process the input
        PendingTransfer pending = pendingTransfers.remove(playerId);
        if (pending == null) return;

        if (amountStr.isEmpty() || amountStr.equalsIgnoreCase("cancel")) {
            Messages.send(player, "<red>Transfer cancelled.</red>");
            Bukkit.getScheduler().runTaskLater(CashClashPlugin.getInstance(),
                    () -> TransferGUI.open(player), 5L);
            return;
        }

        try {
            long amount = Long.parseLong(amountStr.replaceAll("[^0-9]", ""));

            if (amount <= 0) {
                Messages.send(player, "<red>Please enter a valid amount greater than 0.</red>");
                Bukkit.getScheduler().runTaskLater(CashClashPlugin.getInstance(),
                        () -> TransferGUI.open(player), 20L);
                return;
            }

            processTransfer(player, pending, amount);

        } catch (NumberFormatException e) {
            Messages.send(player, "<red>Invalid amount. Please enter a number.</red>");
            Bukkit.getScheduler().runTaskLater(CashClashPlugin.getInstance(),
                    () -> TransferGUI.open(player), 20L);
        }
    }

    private void processTransfer(Player sender, PendingTransfer pending, long amount) {
        GameSession session = GameManager.getInstance().getActiveSessions().stream()
                .filter(s -> s.getSessionId().equals(pending.sessionId()))
                .findFirst()
                .orElse(null);

        if (session == null) {
            Messages.send(sender, "<red>Transfer failed: Game session not found.</red>");
            return;
        }

        Player receiver = Bukkit.getPlayer(pending.receiverId());
        if (receiver == null || !receiver.isOnline()) {
            Messages.send(sender, "<red>Transfer failed: Player is no longer online.</red>");
            Bukkit.getScheduler().runTaskLater(CashClashPlugin.getInstance(),
                    () -> TransferGUI.open(sender), 20L);
            return;
        }

        CashClashPlayer senderCcp = session.getCashClashPlayer(sender.getUniqueId());
        CashClashPlayer receiverCcp = session.getCashClashPlayer(receiver.getUniqueId());

        if (senderCcp == null || receiverCcp == null) {
            Messages.send(sender, "<red>Transfer failed: Player not found in game.</red>");
            Bukkit.getScheduler().runTaskLater(CashClashPlugin.getInstance(),
                    () -> TransferGUI.open(sender), 20L);
            return;
        }

        if (!senderCcp.canAfford(amount)) {
            Messages.send(sender, "<red>You don't have enough coins!</red>");
            Messages.send(sender, "<gray>Your balance: <gold>$" + String.format("%,d", senderCcp.getCoins()) + "</gold></gray>");
            Bukkit.getScheduler().runTaskLater(CashClashPlugin.getInstance(),
                    () -> TransferGUI.open(sender), 20L);
            return;
        }

        long fee = (long) (amount * TRANSFER_FEE);
        long netAmount = amount - fee;

        // Deduct from sender and add to receiver
        senderCcp.deductCoins(amount);
        receiverCcp.addCoins(netAmount);

        Messages.send(sender, "<green>Transferred <gold>$" + String.format("%,d", amount) +
                "</gold> to " + receiver.getName() + "!</green>");
        Messages.send(sender, "<gray>Fee: <red>-$" + String.format("%,d", fee) + "</red></gray>");

        Messages.send(receiver, "<green>You received <gold>$" + String.format("%,d", netAmount) +
                "</gold> from " + sender.getName() + "!</green>");

        // Reopen shop after a short delay
        Bukkit.getScheduler().runTaskLater(CashClashPlugin.getInstance(),
                () -> ShopGUI.openMain(sender), 20L);
    }

    /**
     * Check if a player has a pending transfer.
     */
    public boolean hasPendingTransfer(UUID playerId) {
        return pendingTransfers.containsKey(playerId);
    }

    /**
     * Cancel a pending transfer.
     */
    public void cancelTransfer(UUID playerId) {
        pendingTransfers.remove(playerId);
    }

    /**
     * Get pending transfer for a player.
     */
    public PendingTransfer getPendingTransfer(UUID playerId) {
        return pendingTransfers.get(playerId);
    }

    /**
     * Remove pending transfer.
     */
    public void removePendingTransfer(UUID playerId) {
        pendingTransfers.remove(playerId);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingTransfers.remove(playerId);
        Block signBlock = signBlocks.remove(playerId);
        if (signBlock != null) {
            signBlock.setType(Material.AIR);
        }
    }

    /**
     * Shutdown and cleanup.
     */
    public void shutdown() {
        // Clean up any remaining sign blocks
        for (Block block : signBlocks.values()) {
            if (block != null) {
                block.setType(Material.AIR);
            }
        }
        signBlocks.clear();
        pendingTransfers.clear();
        HandlerList.unregisterAll(this);
        instance = null;
    }

    /**
     * Record for pending transfer data.
     */
    public record PendingTransfer(UUID receiverId, UUID sessionId) {}
}

