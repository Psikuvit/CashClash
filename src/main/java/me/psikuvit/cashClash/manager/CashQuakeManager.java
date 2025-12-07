package me.psikuvit.cashClash.manager;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.CashQuakeEvent;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.Random;

/**
 * Manages Cash Quake events during rounds
 */
public class CashQuakeManager {

    private final GameSession session;
    private final Random random;
    private BukkitTask eventTask;
    private int eventsThisGame;
    private int eventsThisRound;

    public CashQuakeManager(GameSession session) {
        this.session = session;
        this.random = new Random();
        this.eventsThisGame = 0;
        this.eventsThisRound = 0;
    }

    public void startEventScheduler() {
        if (eventTask != null) {
            eventTask.cancel();
            eventTask = null;
        }

        ConfigManager config = ConfigManager.getInstance();
        int maxEvents = config.getMaxCashQuakeEvents();
        int maxPerRound = config.getMaxCashQuakePerRound();

        // Schedule random events during combat phase
        eventTask = SchedulerUtils.runTaskTimer(() -> {
            if (session.getState().isCombat() &&
                    eventsThisGame < maxEvents &&
                    eventsThisRound < maxPerRound) {

                if (random.nextDouble() < 0.20) {
                    triggerRandomEvent();
                }
            }
        }, 0, 600L); // Check every 30 seconds
    }

    private void triggerRandomEvent() {
        ConfigManager config = ConfigManager.getInstance();

        CashQuakeEvent event;

        event = getRandomCashQuakeEvent();

        executeEvent(event);
        session.getCurrentRoundData().addEvent(event);
        eventsThisGame++;
        eventsThisRound++;
    }

    private CashQuakeEvent getRandomCashQuakeEvent() {
        CashQuakeEvent[] cashQuakes = {
                CashQuakeEvent.LOTTERY,
                CashQuakeEvent.LIFE_STEAL,
                CashQuakeEvent.CHECK_UP,
                CashQuakeEvent.BONUS_JACKPOT,
                CashQuakeEvent.TAX_TIME,
                CashQuakeEvent.MYSTERY_LOOT,
                CashQuakeEvent.BROKEN_GEAR,
                CashQuakeEvent.WEIGHT_OF_WEALTH
        };
        return cashQuakes[random.nextInt(cashQuakes.length)];
    }

    private void executeEvent(CashQuakeEvent event) {
        ConfigManager cfg = ConfigManager.getInstance();
        String prefix = cfg.getPrefix();
        String message;

        switch (event) {
            case LOTTERY ->
                    message = prefix + " <gold><bold>LOTTERY!</bold> <yellow>Type /lottery to enter (5k coins for 50% chance to double)</yellow>";
            case LIFE_STEAL ->
                    message = prefix + " <red><bold>LIFE STEAL!</bold> <yellow>Kills now grant extra hearts for 2 minutes!</yellow>";
            case CHECK_UP ->
                    message = prefix + " <green><bold>CHECK UP!</bold> <yellow>Everyone receives random bonus hearts!</yellow>";
            case BONUS_JACKPOT ->
                    message = prefix + " <gold><bold>BONUS JACKPOT!</bold> <yellow>Everyone receives bonus money!</yellow>";
            case TAX_TIME ->
                    message = prefix + " <red><bold>TAX TIME!</bold> <yellow>Everyone loses a percentage of their money!</yellow>";
            case MYSTERY_LOOT ->
                    message = prefix + " <purple><bold>MYSTERY LOOT!</bold> <yellow>Everyone receives a random shop item!</yellow>";
            case BROKEN_GEAR ->
                    message = prefix + " <red><bold>BROKEN GEAR!</bold> <yellow>A random piece of equipment disappears for 30 seconds!</yellow>";
            case WEIGHT_OF_WEALTH ->
                    message = prefix + " <gold><bold>WEIGHT OF WEALTH!</bold> <yellow>Pay 5k or lose a random item!</yellow>";
            case SUPPLY_DROP ->
                    message = prefix + " <yellow><bold>SUPPLY DROP!</bold> <green>Rich Man Bobby dropped his riches at coordinates!</green>";
            default -> message = prefix + " <gray>An unknown event occurred.";
        }

        String finalMessage = message;
        session.getPlayers().forEach(uuid ->
                Messages.send(Objects.requireNonNull(Bukkit.getPlayer(uuid)), finalMessage)
        );
    }

    public void resetRoundEvents() {
        eventsThisRound = 0;
    }

    public void cleanup() {
        if (eventTask != null) {
            eventTask.cancel();
            eventTask = null;
        }
    }

    public int getEventsThisGame() {
        return eventsThisGame;
    }

    public int getEventsThisRound() {
        return eventsThisRound;
    }
}
