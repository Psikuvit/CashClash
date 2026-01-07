package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.gui.builder.GuiBuilder;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.storage.PlayerData;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.items.GuiItemUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for displaying player statistics.
 * Fetches data from the database via PlayerDataManager.
 */
public class StatsGUI {

    private static final String GUI_ID = "player_stats";

    /**
     * Open the stats GUI for a player, showing their own statistics.
     *
     * @param player The player to show stats to
     */
    public static void openStatsGUI(Player player) {
        openStatsGUI(player, player);
    }

    /**
     * Open the stats GUI showing another player's statistics.
     *
     * @param viewer The player viewing the GUI
     * @param target The player whose stats to show
     */
    public static void openStatsGUI(Player viewer, Player target) {
        PlayerData data = PlayerDataManager.getInstance().getData(target.getUniqueId());

        GuiBuilder builder = GuiBuilder.create(GUI_ID)
                .title("<gold><bold>Player Statistics</bold></gold>")
                .rows(5)
                .fill(Material.GRAY_STAINED_GLASS_PANE)
                .closeButton(40);

        // Player head with overview
        builder.button(4, createOverviewButton(target, data));

        // Combat stats section
        builder.button(19, createKillsButton(data));
        builder.button(20, createDeathsButton(data));
        builder.button(21, createKDRButton(data));

        // Game stats section
        builder.button(23, createWinsButton(data));
        builder.button(24, createLossesButton(data));
        builder.button(25, createWinRateButton(data));

        // Economy stats section
        builder.button(30, createCoinsEarnedButton(data));
        builder.button(31, createCoinsInvestedButton(data));
        builder.button(32, createProfitButton(data));

        builder.open(viewer);
    }

    private static GuiButton createOverviewButton(Player target, PlayerData data) {
        ItemStack skull = GuiItemUtils.createPlayerHead(target,
                "<gold><bold>" + target.getName() + "'s Stats</bold></gold>",
                List.of(
                        "<gray>Total Games: <white>" + (data.getWins() + data.getLosses()) + "</white></gray>",
                        "<gray>Win Rate: <white>" + calculateWinRate(data) + "%</white></gray>",
                        "<gray>K/D Ratio: <white>" + calculateKDR(data) + "</white></gray>",
                        "",
                        "<yellow>View detailed stats below!</yellow>"));
        return GuiButton.of(skull);
    }

    private static GuiButton createKillsButton(PlayerData data) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Total enemies eliminated</gray>"));
        lore.add(Messages.parse("<gray>in all games played.</gray>"));

        return GuiButton.of(Material.IRON_SWORD, Messages.parse("<red><bold>Kills:</bold> <white>" + data.getKills() + "</white></red>"), lore);
    }

    private static GuiButton createDeathsButton(PlayerData data) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Total times eliminated</gray>"));
        lore.add(Messages.parse("<gray>in all games played.</gray>"));

        return GuiButton.of(Material.SKELETON_SKULL, Messages.parse("<dark_red><bold>Deaths:</bold> <white>" + data.getDeaths() + "</white></dark_red>"), lore);
    }

    private static GuiButton createKDRButton(PlayerData data) {
        String kdr = calculateKDR(data);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Kill/Death ratio</gray>"));
        lore.add(Messages.parse("<gray>Formula: Kills ÷ Deaths</gray>"));

        return GuiButton.of(Material.TARGET, Messages.parse("<gold><bold>K/D Ratio:</bold> <white>" + kdr + "</white></gold>"), lore);
    }

    private static GuiButton createWinsButton(PlayerData data) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Total games won</gray>"));
        lore.add(Messages.parse("<gray>with your team.</gray>"));

        return GuiButton.of(Material.EMERALD, Messages.parse("<green><bold>Wins:</bold> <white>" + data.getWins() + "</white></green>"), lore);
    }

    private static GuiButton createLossesButton(PlayerData data) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Total games lost</gray>"));
        lore.add(Messages.parse("<gray>against opponents.</gray>"));

        return GuiButton.of(Material.REDSTONE, Messages.parse("<red><bold>Losses:</bold> <white>" + data.getLosses() + "</white></red>"), lore);
    }

    private static GuiButton createWinRateButton(PlayerData data) {
        String winRate = calculateWinRate(data);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Percentage of games won</gray>"));
        lore.add(Messages.parse("<gray>Formula: (Wins ÷ Total) × 100</gray>"));

        return GuiButton.of(Material.GOLDEN_APPLE, Messages.parse("<yellow><bold>Win Rate:</bold> <white>" + winRate + "%</white></yellow>"), lore);
    }

    private static GuiButton createCoinsEarnedButton(PlayerData data) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Total coins earned from</gray>"));
        lore.add(Messages.parse("<gray>kills and objectives.</gray>"));

        return GuiButton.of(Material.GOLD_INGOT,
                Messages.parse("<gold><bold>Coins Earned:</bold> <white>$" + formatNumber(data.getTotalCoinsEarned()) + "</white></gold>"),
                lore);
    }

    private static GuiButton createCoinsInvestedButton(PlayerData data) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Total coins spent on</gray>"));
        lore.add(Messages.parse("<gray>items and upgrades.</gray>"));

        return GuiButton.of(Material.GOLD_NUGGET,
                Messages.parse("<yellow><bold>Coins Invested:</bold> <white>$" + formatNumber(data.getTotalCoinsInvested()) + "</white></yellow>"),
                lore);
    }

    private static GuiButton createProfitButton(PlayerData data) {
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

    private static String calculateKDR(PlayerData data) {
        if (data.getDeaths() == 0) {
            return data.getKills() > 0 ? data.getKills() + ".00" : "0.00";
        }
        double kdr = (double) data.getKills() / data.getDeaths();
        return String.format("%.2f", kdr);
    }

    private static String calculateWinRate(PlayerData data) {
        int totalGames = data.getWins() + data.getLosses();
        if (totalGames == 0) {
            return "0.0";
        }
        double winRate = ((double) data.getWins() / totalGames) * 100;
        return String.format("%.1f", winRate);
    }

    private static String formatNumber(long number) {
        return String.format("%,d", number);
    }
}

