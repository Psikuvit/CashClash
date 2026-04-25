package me.psikuvit.cashClash.manager.game;

import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.CashQuakeEvent;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Manages Cash Quake events during rounds.
 * Events happen guaranteed twice per game and up to 10 times per game.
 * Max 2 events per round. 50% chance for supply drop, 50% for normal cash quake.
 */
@Deprecated(since = "2024-06", forRemoval = true)
public class CashQuakeManager {

    private final GameSession session;
    private final Random random;
    private final ConfigManager cfg;
    private BukkitTask eventTask;
    private int eventsThisGame;
    private int eventsThisRound;
    private int guaranteedEventsTriggered;

    // Broken Gear tracking
    private final Map<UUID, ItemStack[]> brokenGearStorage;
    private final Map<UUID, BukkitTask> brokenGearRestoreTasks;

    // Life Steal tracking
    private final Set<UUID> lifeStealActive;
    private BukkitTask lifeStealEndTask;

    // Supply drop tracking
    private final List<Location> activeSupplyDropChests;

    // Lottery tracking
    private final Set<UUID> lotteryParticipants;
    private BukkitTask lotteryEndTask;
    private boolean lotteryActive;

    // Weight of Wealth tracking
    private final Set<UUID> weightOfWealthPaid;
    private BukkitTask weightOfWealthEndTask;
    private boolean weightOfWealthActive;

    public CashQuakeManager(GameSession session) {
        this.session = session;
        this.random = new Random();
        this.cfg = ConfigManager.getInstance();
        this.brokenGearStorage = new HashMap<>();
        this.brokenGearRestoreTasks = new HashMap<>();
        this.lifeStealActive = new HashSet<>();
        this.activeSupplyDropChests = new ArrayList<>();
        this.lotteryParticipants = new HashSet<>();
        this.weightOfWealthPaid = new HashSet<>();
    }

    /**
     * Start the event scheduler for the combat phase.
     * Triggers the first event immediately at the start of the round,
     * then schedules checks for additional events.
     */
    public void startEventScheduler() {
        if (eventTask != null) {
            eventTask.cancel();
        }

        // Skip events in Round 1
        int currentRound = session.getCurrentRound();
        if (currentRound == cfg.getFirstRound()) {
            return;
        }

        // Trigger first event immediately at the start of combat phase
        if (eventsThisGame < cfg.getMaxEventsPerGame() && eventsThisRound < cfg.getMaxEventsPerRound()) {
            triggerRandomEvent();
        }

        // Schedule checks for additional events during the round
        eventTask = SchedulerUtils.runTaskTimer(() -> {
            if (session.getState() != GameState.COMBAT) return;

            // Skip events in Round 1
            if (session.getCurrentRound() == cfg.getFirstRound()) return;

            if (eventsThisGame >= cfg.getMaxEventsPerGame() ||
                    eventsThisRound >= cfg.getMaxEventsPerRound()) return;

            // Check for additional events (second event of the round)
            if (eventsThisRound < cfg.getMaxEventsPerRound()) {
                int eventsLeft = cfg.getMaxEventsPerGame() - eventsThisGame;
                int guaranteedLeft = 2 - guaranteedEventsTriggered;

                double chance = 0.5;

                if (eventsLeft <= guaranteedLeft) chance = 1.0;

                if (random.nextDouble() < chance) triggerRandomEvent();
            }

        }, cfg.getEventCheckIntervalTicks(), cfg.getEventCheckIntervalTicks());
    }

    private void triggerRandomEvent() {
        CashQuakeEvent event = random.nextBoolean() ? CashQuakeEvent.SUPPLY_DROP : getRandomCashQuakeEvent();
        executeEvent(event);
        eventsThisGame++;
        eventsThisRound++;
        guaranteedEventsTriggered++;
    }

    private CashQuakeEvent getRandomCashQuakeEvent() {
        CashQuakeEvent[] events = {
                CashQuakeEvent.LOTTERY,
                CashQuakeEvent.LIFE_STEAL,
                CashQuakeEvent.CHECK_UP,
                CashQuakeEvent.BONUS_JACKPOT,
                CashQuakeEvent.TAX_TIME,
                CashQuakeEvent.MYSTERY_LOOT,
                CashQuakeEvent.BROKEN_GEAR,
                CashQuakeEvent.WEIGHT_OF_WEALTH
        };
        return events[random.nextInt(events.length)];
    }

    private void executeEvent(CashQuakeEvent event) {
        String prefix = ConfigManager.getInstance().getPrefix();
        SoundUtils.playTo(session.getPlayers(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);

        switch (event) {
            case LOTTERY -> executeLottery(prefix);
            case LIFE_STEAL -> executeLifeSteal(prefix);
            case CHECK_UP -> executeCheckUp(prefix);
            case BONUS_JACKPOT -> executeBonusJackpot(prefix);
            case TAX_TIME -> executeTaxTime(prefix);
            case MYSTERY_LOOT -> executeMysteryLoot(prefix);
            case BROKEN_GEAR -> executeBrokenGear(prefix);
            case WEIGHT_OF_WEALTH -> executeWeightOfWealth(prefix);
            case SUPPLY_DROP -> executeSupplyDrop(prefix);
        }
    }

    private void executeLottery(String prefix) {
        lotteryActive = true;
        lotteryParticipants.clear();

        Messages.broadcast(session.getPlayers(), "cashquake.lottery-announce", "prefix", prefix);
        Messages.broadcast(session.getPlayers(), "cashquake.lottery-subtitle");

        lotteryEndTask = SchedulerUtils.runTaskLater(() -> {
            lotteryActive = false;
            Messages.broadcast(session.getPlayers(), "cashquake.lottery-ended");
        }, 30 * 20L);
    }

    public void enterLottery(Player player) {
        if (!lotteryActive) {
            Messages.send(player, "cashquake.no-active-lottery");
            return;
        }

        UUID uuid = player.getUniqueId();
        if (lotteryParticipants.contains(uuid)) {
            Messages.send(player, "cashquake.already-entered-lottery");
            return;
        }

        CashClashPlayer ccp = session.getCashClashPlayer(uuid);
        if (ccp == null || !ccp.canAfford(5000)) {
            Messages.send(player, "cashquake.lottery-entry-cost");
            return;
        }

        ccp.deductCoins(5000);
        lotteryParticipants.add(uuid);

        if (random.nextBoolean()) {
            ccp.addCoins(10000);
            Messages.send(player, "cashquake.lottery-win");
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            Messages.broadcast(session.getPlayers(), "cashquake.lottery-winner", "player_name", player.getName());
        } else {
            Messages.send(player, "cashquake.lottery-lose");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    // ==================== LIFE STEAL ====================
    private void executeLifeSteal(String prefix) {
        lifeStealActive.addAll(session.getPlayers());

        Messages.broadcast(session.getPlayers(), "cashquake.life-steal-announce", "prefix", prefix);
        Messages.broadcast(session.getPlayers(), "cashquake.life-steal-active");

        lifeStealEndTask = SchedulerUtils.runTaskLater(() -> {
            lifeStealActive.clear();
            for (UUID uuid : session.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    var attr = p.getAttribute(Attribute.MAX_HEALTH);
                    if (attr != null) {
                        attr.setBaseValue(20.0);
                    }
                    p.setHealth(Math.min(p.getHealth(), 20.0));
                }
            }
            Messages.broadcast(session.getPlayers(), "cashquake.life-steal-ended");
        }, 2 * 60 * 20L);
    }

    public void onLifeStealKill(Player killer) {
        if (!lifeStealActive.contains(killer.getUniqueId())) {
            return;
        }
        var attr = killer.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            double newMax = Math.min(attr.getValue() + 4.0, 40.0);
            attr.setBaseValue(newMax);
            killer.setHealth(Math.min(killer.getHealth() + 4.0, newMax));
        }
        Messages.send(killer, "cashquake.life-steal-kill");
    }

    public boolean isLifeStealActive() {
        return !lifeStealActive.isEmpty();
    }

    // ==================== CHECK UP ====================
    private void executeCheckUp(String prefix) {
        Messages.broadcast(session.getPlayers(), "cashquake.checkup-announce", "prefix", prefix);

        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            int extraHearts = random.nextInt(5) + 1;
            double extraHP = extraHearts * 2.0;

            var attr = p.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                double newMax = Math.min(attr.getValue() + extraHP, 40.0);
                attr.setBaseValue(newMax);
                p.setHealth(Math.min(p.getHealth() + extraHP, newMax));
                Messages.send(p, "cashquake.checkup-hearts", "hearts", String.valueOf(extraHearts));
            }
        }

        SchedulerUtils.runTaskLater(() -> {
            for (UUID uuid : session.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    var attr = p.getAttribute(Attribute.MAX_HEALTH);
                    if (attr != null) {
                        attr.setBaseValue(20.0);
                    }
                    p.setHealth(Math.min(p.getHealth(), 20.0));
                }
            }
            Messages.broadcast(session.getPlayers(), "cashquake.checkup-expired");
        }, 2 * 60 * 20L);
    }

    // ==================== BONUS JACKPOT ====================
    private void executeBonusJackpot(String prefix) {
        Messages.broadcast(session.getPlayers(), "cashquake.bonus-jackpot-announce", "prefix", prefix);

        boolean megaJackpot = random.nextInt(100) == 0;

        for (UUID uuid : session.getPlayers()) {
            CashClashPlayer ccp = session.getCashClashPlayer(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (ccp == null || p == null) continue;

            long bonus = megaJackpot ? 50000 : (random.nextInt(15) + 1) * 1000L;
            ccp.addCoins(bonus);

            if (megaJackpot) {
                Messages.send(p, "cashquake.mega-jackpot-personal");
            } else {
                Messages.send(p, "cashquake.bonus-coins", "amount", String.format("%,d", bonus));
            }
            SoundUtils.play(p, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }

        if (megaJackpot) {
            Messages.broadcast(session.getPlayers(), "cashquake.mega-jackpot");
        }
    }

    // ==================== TAX TIME ====================
    private void executeTaxTime(String prefix) {
        int taxPercent = random.nextInt(6) + 5;

        Messages.broadcast(session.getPlayers(), "cashquake.tax-time-announce",
                "prefix", prefix, "tax_percent", String.valueOf(taxPercent));

        for (UUID uuid : session.getPlayers()) {
            CashClashPlayer ccp = session.getCashClashPlayer(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (ccp == null || p == null) continue;

            long taxAmount = (ccp.getCoins() * taxPercent) / 100;
            ccp.deductCoins(taxAmount);

            Messages.send(p, "cashquake.tax-deducted", "amount", String.format("%,d", taxAmount));
            SoundUtils.play(p, Sound.ENTITY_VILLAGER_HURT, 1.0f, 0.8f);
        }
    }

    // ==================== MYSTERY LOOT ====================
    private void executeMysteryLoot(String prefix) {
        Messages.broadcast(session.getPlayers(), "cashquake.mystery-loot-announce", "prefix", prefix);

        ItemStack[] items = {
                new ItemStack(Material.IRON_SWORD),
                new ItemStack(Material.DIAMOND_SWORD),
                new ItemStack(Material.BOW),
                new ItemStack(Material.CROSSBOW),
                new ItemStack(Material.ARROW, 32),
                new ItemStack(Material.GOLDEN_APPLE, 3),
                new ItemStack(Material.ENDER_PEARL, 2),
                new ItemStack(Material.COBWEB, 8),
                new ItemStack(Material.IRON_CHESTPLATE),
                new ItemStack(Material.DIAMOND_BOOTS),
                new ItemStack(Material.SHIELD),
                new ItemStack(Material.GOLDEN_CARROT, 16)
        };

        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            ItemStack item = items[random.nextInt(items.length)].clone();
            p.getInventory().addItem(item);

            String name = item.getType().name().toLowerCase().replace("_", " ");
            Messages.send(p, "cashquake.mystery-loot-received", "item_name", name);
            SoundUtils.play(p, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        }
    }

    // ==================== BROKEN GEAR ====================
    private void executeBrokenGear(String prefix) {
        Messages.broadcast(session.getPlayers(), "cashquake.broken-gear-announce", "prefix", prefix);

        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            PlayerInventory inv = p.getInventory();
            List<Integer> validSlots = new ArrayList<>();

            if (inv.getHelmet() != null) validSlots.add(-1);
            if (inv.getChestplate() != null) validSlots.add(-2);
            if (inv.getLeggings() != null) validSlots.add(-3);
            if (inv.getBoots() != null) validSlots.add(-4);

            for (int i = 0; i < 9; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && isWeaponOrTool(item.getType())) {
                    validSlots.add(i);
                }
            }

            if (validSlots.isEmpty()) continue;

            int slot = validSlots.get(random.nextInt(validSlots.size()));
            ItemStack removedItem = removeItemFromSlot(inv, slot);

            if (removedItem != null) {
                brokenGearStorage.put(uuid, new ItemStack[]{removedItem, new ItemStack(Material.STONE, slot + 100)});

                String name = removedItem.getType().name().toLowerCase().replace("_", " ");
                Messages.send(p, "cashquake.item-broke-temporary", "item_name", name);
                SoundUtils.play(p, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

                BukkitTask task = SchedulerUtils.runTaskLater(() -> restoreBrokenGear(uuid, slot), 30 * 20L);
                brokenGearRestoreTasks.put(uuid, task);
            }
        }
    }

    private ItemStack removeItemFromSlot(PlayerInventory inv, int slot) {
        ItemStack item;
        switch (slot) {
            case -1 -> { item = inv.getHelmet(); inv.setHelmet(null); }
            case -2 -> { item = inv.getChestplate(); inv.setChestplate(null); }
            case -3 -> { item = inv.getLeggings(); inv.setLeggings(null); }
            case -4 -> { item = inv.getBoots(); inv.setBoots(null); }
            default -> { item = inv.getItem(slot); inv.setItem(slot, null); }
        }
        return item;
    }

    private void restoreBrokenGear(UUID uuid, int slot) {
        ItemStack[] stored = brokenGearStorage.remove(uuid);
        brokenGearRestoreTasks.remove(uuid);
        if (stored == null) return;

        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) return;

        ItemStack item = stored[0];
        PlayerInventory inv = p.getInventory();

        switch (slot) {
            case -1 -> inv.setHelmet(item);
            case -2 -> inv.setChestplate(item);
            case -3 -> inv.setLeggings(item);
            case -4 -> inv.setBoots(item);
            default -> inv.addItem(item);
        }

        String name = item.getType().name().toLowerCase().replace("_", " ");
        Messages.send(p, "cashquake.item-restored", "item_name", name);
        SoundUtils.play(p, Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
    }

    private boolean isWeaponOrTool(Material mat) {
        String name = mat.name();
        return name.contains("SWORD") || name.contains("AXE") || name.contains("BOW") ||
                name.contains("CROSSBOW") || name.contains("TRIDENT") || name.contains("MACE");
    }

    private void executeWeightOfWealth(String prefix) {
        weightOfWealthActive = true;
        weightOfWealthPaid.clear();

        Messages.broadcast(session.getPlayers(), "cashquake.weight-of-wealth-announce", "prefix", prefix);
        Messages.broadcast(session.getPlayers(), "cashquake.weight-of-wealth-prompt");

        weightOfWealthEndTask = SchedulerUtils.runTaskLater(() -> {
            weightOfWealthActive = false;

            for (UUID uuid : session.getPlayers()) {
                if (weightOfWealthPaid.contains(uuid)) continue;

                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;

                PlayerInventory inv = p.getInventory();
                List<Integer> slots = new ArrayList<>();
                for (int i = 0; i < 36; i++) {
                    if (inv.getItem(i) != null) slots.add(i);
                }
                if (slots.isEmpty()) continue;

                int slot = slots.get(random.nextInt(slots.size()));
                ItemStack item = inv.getItem(slot);

                if (item != null) {
                    String name = item.getType().name().toLowerCase().replace("_", " ");
                    if (item.getAmount() > 10) {
                        item.setAmount(item.getAmount() - 10);
                        Messages.send(p, "cashquake.lost-items-ten", "item_name", name);
                    } else {
                        inv.setItem(slot, null);
                        Messages.send(p, "cashquake.lost-item", "item_name", name);
                    }
                    SoundUtils.play(p, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
                }
            }

            Messages.broadcast(session.getPlayers(), "cashquake.weight-of-wealth-ended");
        }, 20 * 20L);
    }

    public void payWeightOfWealthTax(Player player) {
        if (!weightOfWealthActive) {
            Messages.send(player, "cashquake.no-active-weight-of-wealth");
            return;
        }

        UUID uuid = player.getUniqueId();
        if (weightOfWealthPaid.contains(uuid)) {
            Messages.send(player, "cashquake.already-paid-tax");
            return;
        }

        CashClashPlayer ccp = session.getCashClashPlayer(uuid);
        if (ccp == null || !ccp.canAfford(5000)) {
            Messages.send(player, "cashquake.tax-payment-cost");
            return;
        }

        ccp.deductCoins(5000);
        weightOfWealthPaid.add(uuid);
        Messages.send(player, "cashquake.tax-paid-safe");
        SoundUtils.play(player, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
    }

    private void executeSupplyDrop(String prefix) {
        World world = session.getGameWorld();
        if (world == null) return;

        Location dropLoc = null;
        var arena = ArenaManager.getInstance().getArena(session.getArenaNumber());
        if (arena != null) {
            var tpl = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
            if (tpl != null) {
                double sumX = 0, sumZ = 0;
                int count = 0;
                for (int i = 0; i < 4; i++) {
                    var t1 = tpl.getTeamRedSpawn(i);
                    var t2 = tpl.getTeamBlueSpawn(i);
                    if (t1 != null) {
                        sumX += t1.getX();
                        sumZ += t1.getZ();
                        count++;
                    }
                    if (t2 != null) {
                        sumX += t2.getX();
                        sumZ += t2.getZ();
                        count++;
                    }
                }

                if (count > 0) {
                    double avgX = sumX / count;
                    double avgZ = sumZ / count;
                    // Map template coordinates into the session's copied world (same coordinates)
                    dropLoc = new Location(world, avgX, 0, avgZ);
                    dropLoc.setY(world.getHighestBlockYAt(dropLoc) + 1);
                }
            }
        }

        // Fallback if no valid drop location found
        if (dropLoc == null) {
            dropLoc = world.getSpawnLocation();
            dropLoc.setY(world.getHighestBlockYAt(dropLoc) + 1);
        }

        Messages.broadcast(session.getPlayers(), "cashquake.supply-drop-announce", "prefix", prefix);
        Messages.broadcast(session.getPlayers(), "cashquake.supply-drop-location",
                "x", String.valueOf(dropLoc.getBlockX()), "z", String.valueOf(dropLoc.getBlockZ()));
        Messages.broadcast(session.getPlayers(), "cashquake.auction-prompt");

        int chestCount = random.nextInt(4) + 3;
        activeSupplyDropChests.clear();

        for (int i = 0; i < chestCount; i++) {
            int dx = random.nextInt(5) - 2;
            int dz = random.nextInt(5) - 2;
            Location chestLoc = dropLoc.clone().add(dx, 0, dz);
            chestLoc.setY(world.getHighestBlockYAt(chestLoc) + 1);

            Block block = chestLoc.getBlock();
            block.setType(Material.CHEST);
            activeSupplyDropChests.add(chestLoc);

            if (block.getState() instanceof Chest chest) {
                int money = random.nextInt(1001) + 1000;
                ItemStack moneyItem = new ItemStack(Material.EMERALD, 1);
                var meta = moneyItem.getItemMeta();
                if (meta != null) {
                    meta.displayName(Messages.parse("<gold><bold>$" + String.format("%,d", money) + " Coins</bold></gold>"));
                    meta.lore(Messages.wrapLines("<yellow>Amount: " + money + "</yellow>"));

                    meta.getPersistentDataContainer().set(Keys.SUPPLY_DROP_AMOUNT, PersistentDataType.INTEGER, money);

                    moneyItem.setItemMeta(meta);
                }
                chest.getInventory().addItem(moneyItem);
            }
        }

        SoundUtils.playTo(session.getPlayers(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
    }

    public void onSupplyDropPickup(Player player, ItemStack item) {
        if (item == null || item.getType() != Material.EMERALD) return;

        var meta = item.getItemMeta();
        if (meta == null) return;

        // Prefer reading the PDC amount stored on the emerald
        Integer pdcAmount = meta.getPersistentDataContainer().get(me.psikuvit.cashClash.util.Keys.SUPPLY_DROP_AMOUNT, org.bukkit.persistence.PersistentDataType.INTEGER);
        int amount;
        if (pdcAmount != null) {
            amount = pdcAmount;
        } else {
            // Fallback to older lore parsing
            var loreList = meta.lore();
            if (loreList == null || loreList.isEmpty()) return;
            String lore = Messages.parseToLegacy(loreList.getFirst());
            if (!lore.contains("Amount:")) return;
            try {
                amount = Integer.parseInt(lore.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                return;
            }
        }
        // Give coins to player
        if (amount <= 0) return;
        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp != null) {
            ccp.addCoins(amount);
            Messages.send(player, "cashquake.supply-drop-reward", "amount", String.format("%,d", amount));
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }
        // Remove one emerald instance from inventory (consume)
        player.getInventory().removeItem(new ItemStack(Material.EMERALD, 1));
    }

    public void cleanupSupplyDrops() {
        for (Location loc : activeSupplyDropChests) {
            Block block = loc.getBlock();
            if (block.getType() == Material.CHEST) {
                if (block.getState() instanceof Chest chest) {
                    chest.getInventory().clear();
                }
                block.setType(Material.AIR);
            }
        }
        activeSupplyDropChests.clear();
    }

    public void resetRoundEvents() {
        eventsThisRound = 0;
        cleanupSupplyDrops();

        if (lotteryEndTask != null) { lotteryEndTask.cancel(); lotteryEndTask = null; }
        lotteryActive = false;
        lotteryParticipants.clear();

        if (weightOfWealthEndTask != null) { weightOfWealthEndTask.cancel(); weightOfWealthEndTask = null; }
        weightOfWealthActive = false;
        weightOfWealthPaid.clear();

        if (lifeStealEndTask != null) { lifeStealEndTask.cancel(); lifeStealEndTask = null; }
        lifeStealActive.clear();

        for (UUID uuid : new HashSet<>(brokenGearRestoreTasks.keySet())) {
            BukkitTask task = brokenGearRestoreTasks.get(uuid);
            if (task != null) task.cancel();
            ItemStack[] stored = brokenGearStorage.get(uuid);
            if (stored != null) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    restoreBrokenGear(uuid, stored[1].getAmount());
                }
            }
        }
        brokenGearStorage.clear();
        brokenGearRestoreTasks.clear();
    }

    public void cleanup() {
        if (eventTask != null) {
            eventTask.cancel();
            eventTask = null;
        }
        cleanupSupplyDrops();
    }
}



