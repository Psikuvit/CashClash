package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.gui.builder.AbstractGui;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.storage.PlayerData;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.items.ItemFactory;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for displaying player statistics.
 * Fetches data from the database via PlayerDataManager.
 * Extends AbstractGui for consistent GUI implementation.
 */
public class StatsGUI extends AbstractGui {

    private static final String GUI_ID = "player_stats";
    private final Player target;
    private final PlayerData data;

    public StatsGUI(Player viewer, Player target) {
        super(GUI_ID, viewer);
        this.target = target;
        this.data = PlayerDataManager.getInstance().getData(target.getUniqueId());
        setTitle("<gold><bold>Player Statistics</bold></gold>");
        setRows(5);
        setFillMaterial(Material.GRAY_STAINED_GLASS_PANE);
    }

    /**
     * Open the stats GUI for a player, showing their own statistics.
     */
    public static void openStatsGUI(Player player) {
        openStatsGUI(player, player);
    }

    /**
     * Open the stats GUI showing another player's statistics.
     */
    public static void openStatsGUI(Player viewer, Player target) {
        new StatsGUI(viewer, target).open();
    }

    @Override
    protected void build() {
        // Player head with overview
        setButton(4, createOverviewButton());

        // Combat stats section
        setButton(19, createKillsButton());
        setButton(20, createDeathsButton());
        setButton(21, createKDRButton());

        // Game stats section
        setButton(23, createWinsButton());
        setButton(24, createLossesButton());
        setButton(25, createWinRateButton());

        // Economy stats section
        setButton(30, createCoinsEarnedButton());
        setButton(31, createCoinsInvestedButton());
        setButton(32, createProfitButton());

        // Close button
        setCloseButton(40);
    }

    private GuiButton createOverviewButton() {
        ItemStack skull = ItemFactory.getInstance().getGuiFactory().createPlayerHead(target,
                "<gold><bold>" + target.getName() + "'s Stats</bold></gold>",
                List.of(
                        "<gray>Total Games: <white>" + (data.getWins() + data.getLosses()) + "</white></gray>",
                        "<gray>Win Rate: <white>" + calculateWinRate() + "%</white></gray>",
                        "<gray>K/D Ratio: <white>" + calculateKDR() + "</white></gray>",
                        "",
                        "<yellow>View detailed stats below!</yellow>"));
        return GuiButton.of(skull);
    }

    private GuiButton createKillsButton() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Total enemies eliminated</gray>"));
        lore.add(Messages.parse("<gray>in all games played.</gray>"));

        return GuiButton.of(Material.IRON_SWORD, Messages.parse("<red><bold>Kills:</bold> <white>" + data.getKills() + "</white></red>"), lore);
    }

    private GuiButton createDeathsButton() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Total times eliminated</gray>"));
        lore.add(Messages.parse("<gray>in all games played.</gray>"));

        return GuiButton.of(Material.SKELETON_SKULL, Messages.parse("<dark_red><bold>Deaths:</bold> <white>" + data.getDeaths() + "</white></dark_red>"), lore);
    }

    private GuiButton createKDRButton() {
        String kdr = calculateKDR();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Kill/Death ratio</gray>"));
        lore.add(Messages.parse("<gray>Formula: Kills ÷ Deaths</gray>"));

        return GuiButton.of(Material.TARGET, Messages.parse("<gold><bold>K/D Ratio:</bold> <white>" + kdr + "</white></gold>"), lore);
    }

    private GuiButton createWinsButton() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Total games won</gray>"));
        lore.add(Messages.parse("<gray>with your team.</gray>"));

        return GuiButton.of(Material.EMERALD, Messages.parse("<green><bold>Wins:</bold> <white>" + data.getWins() + "</white></green>"), lore);
    }

    private GuiButton createLossesButton() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Total games lost</gray>"));
        lore.add(Messages.parse("<gray>against opponents.</gray>"));

        return GuiButton.of(Material.REDSTONE, Messages.parse("<red><bold>Losses:</bold> <white>" + data.getLosses() + "</white></red>"), lore);
    }

    private GuiButton createWinRateButton() {
        String winRate = calculateWinRate();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Percentage of games won</gray>"));
        lore.add(Messages.parse("<gray>Formula: (Wins ÷ Total) × 100</gray>"));

        return GuiButton.of(Material.GOLDEN_APPLE, Messages.parse("<yellow><bold>Win Rate:</bold> <white>" + winRate + "%</white></yellow>"), lore);
    }

    private GuiButton createCoinsEarnedButton() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Total coins earned from</gray>"));
        lore.add(Messages.parse("<gray>kills and objectives.</gray>"));

        return GuiButton.of(Material.GOLD_INGOT,
                Messages.parse("<gold><bold>Coins Earned:</bold> <white>$" + formatNumber(data.getTotalCoinsEarned()) + "</white></gold>"),
                lore);
    }

    private GuiButton createCoinsInvestedButton() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Total coins spent on</gray>"));
        lore.add(Messages.parse("<gray>items and upgrades.</gray>"));

        return GuiButton.of(Material.GOLD_NUGGET,
                Messages.parse("<yellow><bold>Coins Invested:</bold> <white>$" + formatNumber(data.getTotalCoinsInvested()) + "</white></yellow>"),
                lore);
    }

    private GuiButton createProfitButton() {
        long profit = data.getTotalCoinsEarned() - data.getTotalCoinsInvested();
        String color = profit >= 0 ? "<green>" : "<red>";
        String sign = profit >= 0 ? "+" : "";

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Net profit/loss from</gray>"));
        lore.add(Messages.parse("<gray>all your investments.</gray>"));

        return GuiButton.of(Material.DIAMOND,
                Messages.parse(color + "<bold>Profit:</bold> <white>" + sign + "$" + formatNumber(profit) + "</white>" + color.replace("<", "</")),
                lore);
    }

    // ==================== UTILITY METHODS ====================

    private String calculateKDR() {
        if (data.getDeaths() == 0) {
            return data.getKills() > 0 ? data.getKills() + ".00" : "0.00";
        }
        double kdr = (double) data.getKills() / data.getDeaths();
        return String.format("%.2f", kdr);
    }

    private String calculateWinRate() {
        int totalGames = data.getWins() + data.getLosses();
        if (totalGames == 0) {
            return "0.0";
        }
        double winRate = ((double) data.getWins() / totalGames) * 100;
        return String.format("%.1f", winRate);
    }

    private String formatNumber(long number) {
        return String.format("%,d", number);
    }
}

