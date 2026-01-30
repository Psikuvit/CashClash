package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gui.builder.AbstractGui;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.listener.TransferInputListener;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.items.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * GUI for transferring money to teammates.
 * Player selects a teammate, then enters amount via sign input.
 * Extends AbstractGui for consistent GUI implementation.
 */
public class TransferGUI extends AbstractGui {

    private static final String GUI_ID = "transfer_main";
    private final GameSession session;
    private final Team playerTeam;

    public TransferGUI(Player viewer, GameSession session, Team playerTeam) {
        super(GUI_ID, viewer);
        this.session = session;
        this.playerTeam = playerTeam;
        setTitle("<gold><bold>Transfer Money</bold></gold>");
        setRows(3);
        setFillMaterial(Material.GRAY_STAINED_GLASS_PANE);
    }

    /**
     * Open the transfer GUI for a player.
     */
    public static void open(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(player, "<red>You must be in a game to transfer money!</red>");
            return;
        }

        Team playerTeam = session.getPlayerTeam(player);
        if (playerTeam == null) {
            Messages.send(player, "<red>You must be on a team to transfer money!</red>");
            return;
        }

        new TransferGUI(player, session, playerTeam).open();
    }

    @Override
    protected void build() {
        // Back button to shop
        setBackButton(18, ShopGUI::openMain);

        // Info item showing transfer fee
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.displayName(Messages.parse("<yellow>Transfer Info</yellow>"));
        infoMeta.lore(List.of(
                Messages.parse("<gray>Transfer fee: <red>10%</red></gray>"),
                Messages.parse("<gray>Select a teammate to transfer coins</gray>")
        ));
        infoItem.setItemMeta(infoMeta);
        setItem(4, infoItem);

        // Balance display
        CashClashPlayer ccp = session.getCashClashPlayer(viewer.getUniqueId());
        long coins = ccp != null ? ccp.getCoins() : 0;
        setItem(22, ItemFactory.getInstance().getGuiFactory().createCoinDisplay(coins));

        // Teammate slots: 11, 12, 13, 14 (4 players max per team)
        int[] slots = {11, 12, 13, 14};
        int slotIndex = 0;

        for (UUID teammateUuid : playerTeam.getPlayers()) {
            if (teammateUuid.equals(viewer.getUniqueId())) {
                continue; // Skip self
            }

            Player teammate = Bukkit.getPlayer(teammateUuid);
            if (teammate == null || !teammate.isOnline()) {
                continue;
            }

            if (slotIndex < slots.length) {
                setButton(slots[slotIndex], createTeammateButton(teammate));
                slotIndex++;
            }
        }
    }

    private GuiButton createTeammateButton(Player teammate) {
        CashClashPlayer teammateCcp = session.getCashClashPlayer(teammate.getUniqueId());
        long teammateCoins = teammateCcp != null ? teammateCcp.getCoins() : 0;

        ItemStack skull = ItemFactory.getInstance().getGuiFactory().createPlayerHead(teammate,
                "<yellow>" + teammate.getName() + "</yellow>",
                List.of(
                        "<gray>Balance: <gold>$" + String.format("%,d", teammateCoins) + "</gold></gray>",
                        "",
                        "<green>Click to transfer coins</green>"
                ));

        return GuiButton.of(skull).onClick(clicker -> {
            clicker.closeInventory();
            // Start sign input for transfer amount
            TransferInputListener.getInstance().startTransferInput(clicker, teammate, session);
        });
    }
}

