package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.CashQuakeManager;
import me.psikuvit.cashClash.manager.game.EconomyManager;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.CustomArmorManager;
import me.psikuvit.cashClash.manager.items.CustomItemManager;
import me.psikuvit.cashClash.manager.items.MythicItemManager;
import me.psikuvit.cashClash.manager.player.BonusManager;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.manager.shop.ShopManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

/**
 * General game listener for events that are only tracked once.
 * Handles: death, food, drops, sneak, flight, bow shoot, projectile hit, entity interactions, inventory clicks.
 */
public class GameListener implements Listener {

    private final CustomArmorManager armorManager = CustomArmorManager.getInstance();
    private final CustomItemManager customItemManager = CustomItemManager.getInstance();
    private final MythicItemManager mythicManager = MythicItemManager.getInstance();

    // ==================== PLAYER DEATH ====================

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);

        if (session == null) return;

        CashClashPlayer victim = session.getCashClashPlayer(player.getUniqueId());
        if (victim == null) return;

        // Prevent item drops and experience loss
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);

        PlayerDataManager.getInstance().incDeaths(player.getUniqueId());

        victim.handleDeath();
        session.getCurrentRoundData().removeLife(player.getUniqueId());

        BonusManager bonusManager = session.getBonusManager();
        if (bonusManager != null) {
            bonusManager.onDeath(player.getUniqueId());
        }

        Player killer = player.getKiller();
        if (killer != null) {
            handleKillerRewards(session, killer);
        }

        Location spectatorLocation = getSpectatorLocation(session);

        if (victim.getLives() <= 0) {
            handlePermanentSpectator(player, spectatorLocation);
        } else {
            handleTemporarySpectatorAndRespawn(player, spectatorLocation);
        }
    }

    private Location getSpectatorLocation(GameSession session) {
        Arena arena = ArenaManager.getInstance().getArena(session.getArenaNumber());
        if (arena != null) {
            TemplateWorld template = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
            if (template != null && template.getSpectatorSpawn() != null) {
                return LocationUtils.adjustLocationToWorld(template.getSpectatorSpawn(), session.getGameWorld());
            }
        }
        return session.getGameWorld() != null
                ? session.getGameWorld().getSpawnLocation()
                : Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    private void handleKillerRewards(GameSession session, Player killer) {
        CashClashPlayer killerCCP = session.getCashClashPlayer(killer.getUniqueId());
        if (killerCCP == null) return;

        PlayerDataManager.getInstance().incKills(killer.getUniqueId());
        killerCCP.handleKill();
        session.getCurrentRoundData().addKill(killer.getUniqueId());

        BonusManager bonusManager = session.getBonusManager();
        if (bonusManager != null) {
            bonusManager.onKill(killer.getUniqueId());
        }

        long killReward = EconomyManager.getKillReward(session, killerCCP);
        killerCCP.addCoins(killReward);

        armorManager.onPlayerKill(killer);

        CashQuakeManager cashQuakeManager = session.getCashQuakeManager();
        if (cashQuakeManager != null && cashQuakeManager.isLifeStealActive()) {
            cashQuakeManager.onLifeStealKill(killer);
        }
    }

    private void handlePermanentSpectator(Player player, Location spectatorLocation) {
        Messages.send(player, "<red>You are out of lives for this round!</red>");
        SchedulerUtils.runTask(() -> {
            player.spigot().respawn();
            player.teleport(spectatorLocation);
            player.setGameMode(GameMode.SPECTATOR);
        });
    }

    private void handleTemporarySpectatorAndRespawn(Player player, Location spectatorLocation) {
        int respawnDelaySec = ConfigManager.getInstance().getRespawnDelay();
        int respawnProtectionSec = ConfigManager.getInstance().getRespawnProtection();

        Messages.send(player, "<yellow>You will respawn in " + respawnDelaySec + " seconds...</yellow>");

        SchedulerUtils.runTask(() -> {
            player.spigot().respawn();
            player.teleport(spectatorLocation);
            player.setGameMode(GameMode.SPECTATOR);
        });

        SchedulerUtils.runTaskLater(() -> respawnPlayer(player, respawnProtectionSec), respawnDelaySec * 20L);
    }

    private void respawnPlayer(Player player, int respawnProtectionSec) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        Location spawnLocation = session.getSpawnForPlayer(player.getUniqueId());
        if (spawnLocation != null) {
            player.teleport(spawnLocation);
        }

        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            player.setHealth(Math.max(1.0, maxHealthAttr.getValue()));
        } else {
            player.setHealth(20.0);
        }

        player.setFoodLevel(20);
        player.setGameMode(GameMode.SURVIVAL);

        CashClashPlayer cashClashPlayer = session.getCashClashPlayer(player.getUniqueId());
        if (cashClashPlayer != null) {
            cashClashPlayer.setRespawnProtection(respawnProtectionSec * 1000L);
        }

        BonusManager bonusManager = session.getBonusManager();
        if (bonusManager != null) {
            bonusManager.onRespawn(player.getUniqueId());
        }

        Messages.send(player, "<green>You have respawned.</green>");
    }

    // ==================== FOOD LEVEL ====================

    @EventHandler(priority = EventPriority.LOW)
    public void onFoodLevelChangeLobby(FoodLevelChangeEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (GameManager.getInstance().getPlayerSession(player) != null) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onFoodLevelChangeShop(FoodLevelChangeEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null && session.getState().isShopping()) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    // ==================== ITEM DROP ====================

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (GameManager.getInstance().getPlayerSession(player) != null) return;

        event.setCancelled(true);
    }

    // ==================== ITEM CONSUME ====================

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player p = event.getPlayer();
        ItemStack consumed = event.getItem();

        if (consumed.getType().isAir()) return;

        GameSession session = GameManager.getInstance().getPlayerSession(p);
        if (session != null && session.getState().isShopping()) {
            FoodItem fi = PDCDetection.getFood(consumed);
            if (fi != null && !fi.getDescription().isEmpty()) {
                event.setCancelled(true);
                Messages.send(p, "<red>You cannot use special consumables during the shopping phase!</red>");
                return;
            }
        }

        FoodItem fi = PDCDetection.getFood(consumed);
        if (fi == null) return;

        // Check if this is a special consumable (has custom effects)
        if (isSpecialConsumable(fi)) {
            CooldownManager cooldownManager = CooldownManager.getInstance();
            if (cooldownManager.isOnCooldown(p.getUniqueId(), CooldownManager.Keys.CONSUMABLE)) {
                long remaining = cooldownManager.getRemainingCooldownSeconds(p.getUniqueId(), CooldownManager.Keys.CONSUMABLE);
                event.setCancelled(true);
                Messages.send(p, "<red>Consumable on cooldown! (" + remaining + "s)</red>");
                return;
            }
            // Set cooldown
            int cooldownSeconds = ItemsConfig.getInstance().getConsumableCooldown();
            cooldownManager.setCooldownSeconds(p.getUniqueId(), CooldownManager.Keys.CONSUMABLE, cooldownSeconds);
        }

        switch (fi) {
            case SPEED_CARROT -> applyConsumable(p, new PotionEffect(PotionEffectType.SPEED, 20 * 20, 0), "<green>Speed I activated!</green>");
            case GOLDEN_CHICKEN -> applyAbsorption(p);
            case COOKIE_OF_LIFE -> applyConsumable(p, new PotionEffect(PotionEffectType.REGENERATION, 14 * 20, 0), "<dark_green>Regeneration I activated!</dark_green>");
            case SUNSCREEN -> {
                applyConsumable(p, new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30 * 20, 0), "<aqua>Fire Resistance activated!</aqua>");
                SchedulerUtils.runTaskLater(() -> removeEmptyBottle(p), 1L);
            }
            case CAN_OF_SPINACH -> applyConsumable(p, new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0), "<gold>Spinach Strength activated!</gold>");
        }
    }

    /**
     * Check if a food item is a special consumable with custom effects.
     */
    private boolean isSpecialConsumable(FoodItem fi) {
        return fi == FoodItem.SPEED_CARROT ||
               fi == FoodItem.GOLDEN_CHICKEN ||
               fi == FoodItem.COOKIE_OF_LIFE ||
               fi == FoodItem.SUNSCREEN ||
               fi == FoodItem.CAN_OF_SPINACH;
    }

    private void applyConsumable(Player p, PotionEffect effect, String msg) {
        p.removePotionEffect(effect.getType());
        p.addPotionEffect(effect);
        Messages.send(p, msg);
        SoundUtils.play(p, Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
    }

    private void applyAbsorption(Player p) {
        double maxAbsorption = 3 * 2.0;
        if (p.getAbsorptionAmount() < maxAbsorption) {
            p.setAbsorptionAmount(maxAbsorption);
        }
        Messages.send(p, "<gold>+3 Absorption Hearts!</gold>");
        SoundUtils.play(p, Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
    }

    private void removeEmptyBottle(Player p) {
        ItemStack mainHand = p.getInventory().getItemInMainHand();
        if (mainHand.getType() == Material.GLASS_BOTTLE) {
            mainHand.setAmount(mainHand.getAmount() - 1);
            return;
        }
        ItemStack offHand = p.getInventory().getItemInOffHand();
        if (offHand.getType() == Material.GLASS_BOTTLE) {
            offHand.setAmount(offHand.getAmount() - 1);
            return;
        }
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item != null && item.getType() == Material.GLASS_BOTTLE) {
                item.setAmount(item.getAmount() - 1);
                return;
            }
        }
    }

    // ==================== SNEAK TOGGLE ====================

    @EventHandler(ignoreCancelled = true)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player p = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(p);
        if (session == null) return;

        if (session.getState().isShopping()) return;

        armorManager.onPlayerToggleSneak(p, event.isSneaking());
    }

    // ==================== FLIGHT TOGGLE ====================

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(p);
        if (session == null) return;

        if (session.getState().isShopping()) return;

        if (event.isFlying() && armorManager.tryDragonDoubleJump(p)) {
            event.setCancelled(true);
            p.setAllowFlight(false);
        }
    }

    // ==================== BOW SHOOT ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack bow = event.getBow();
        if (bow == null) return;

        MythicItem mythic = PDCDetection.getMythic(bow);
        if (mythic == null) return;

        switch (mythic) {
            case BLOODWRENCH_CROSSBOW -> {
                if (!mythicManager.handleSandstormerShot(player)) {
                    event.setCancelled(true);
                }
            }
            case BLAZEBITE_CROSSBOWS -> {
                if (!mythicManager.handleBlazebiteShot(player)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // ==================== PROJECTILE HIT ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.isCancelled()) return;

        // Handle arrow hits
        if (event.getEntity() instanceof Arrow arrow) {
            if (!(arrow.getShooter() instanceof Player shooter)) return;

            ItemStack bow = shooter.getInventory().getItemInMainHand();
            MythicItem mythic = PDCDetection.getMythic(bow);

            if (mythic == MythicItem.WIND_BOW && event.getHitEntity() instanceof Player hitPlayer) {
                mythicManager.handleWindBowHit(shooter, hitPlayer);
            }
            if (mythic == MythicItem.BLOODWRENCH_CROSSBOW) {
                if (event.getHitEntity() instanceof Player hitPlayer) {
                    mythicManager.fireSuperchargedSandstormer(shooter, hitPlayer);
                }
            }
        }

        // Handle trident hits
        if (event.getEntity() instanceof Trident trident) {
            if (!(trident.getShooter() instanceof Player shooter)) return;

            ItemStack mainHand = shooter.getInventory().getItemInMainHand();
            MythicItem mythic = PDCDetection.getMythic(mainHand);

            if (mythic == MythicItem.GOBLIN_SPEAR && event.getHitEntity() instanceof LivingEntity target) {
                mythicManager.handleGoblinSpearHit(shooter, target);
            }
        }

        // Handle custom item projectile hits (Cash Blaster)
        if (event.getHitEntity() instanceof Player) {
            ProjectileSource projectileShooter = event.getEntity().getShooter();
            if (!(projectileShooter instanceof Player attacker)) return;

            ItemStack item = attacker.getInventory().getItemInMainHand();
            if (!item.hasItemMeta()) return;

            GameSession session = GameManager.getInstance().getPlayerSession(attacker);
            if (session != null && session.getState().isShopping()) {
                event.setCancelled(true);
                return;
            }

            CustomItem type = PDCDetection.getCustomItem(item);
            if (type == CustomItem.CASH_BLASTER) {
                customItemManager.handleCashBlasterHit(attacker);
            }
        }
    }

    // ==================== ENTITY INTERACTIONS ====================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteractAtEntityShop(PlayerInteractAtEntityEvent event) {
        if (event.isCancelled()) return;

        var e = event.getRightClicked();
        if (e.getType() != EntityType.VILLAGER) return;

        if (!e.getPersistentDataContainer().has(Keys.SHOP_NPC_KEY, PersistentDataType.BYTE)) return;

        event.setCancelled(true);
        ShopManager.getInstance().onPlayerInteractShop(event.getPlayer(), e);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!item.hasItemMeta()) return;

        CustomItem type = PDCDetection.getCustomItem(item);
        if (type == null) return;

        if (!(event.getRightClicked() instanceof Player target)) return;

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null && session.getState().isShopping()) return;

        switch (type) {
            case MEDIC_POUCH -> {
                event.setCancelled(true);
                customItemManager.useMedicPouchAlly(player, target, item);
            }
            case RESPAWN_ANCHOR -> {
                event.setCancelled(true);
                customItemManager.useRespawnAnchor(player, target, item);
            }
        }
    }

    // ==================== INVENTORY CLICK (Supply Drop) ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.isCancelled()) return;

        if (!(event.getWhoClicked() instanceof Player p)) return;
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() != Material.EMERALD) return;

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(Keys.SUPPLY_DROP_AMOUNT, PersistentDataType.INTEGER)) return;

        Integer amount = pdc.get(Keys.SUPPLY_DROP_AMOUNT, PersistentDataType.INTEGER);
        if (amount == null) return;

        GameSession session = GameManager.getInstance().getPlayerSession(p);
        if (session == null) return;

        CashClashPlayer ccp = session.getCashClashPlayer(p.getUniqueId());
        if (ccp == null) return;

        event.setCancelled(true);
        int left = current.getAmount() - 1;
        if (left > 0) {
            current.setAmount(left);
            event.setCurrentItem(current);
        } else {
            event.setCurrentItem(null);
        }

        ccp.addCoins(amount);
        Messages.send(p, "<gold>+$" + String.format("%,d", amount) + " from supply drop!</gold>");
        SoundUtils.play(p, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    // ==================== PLAYER RESPAWN ====================

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null && session.getState().isShopping()) {
            Bukkit.getScheduler().runTaskLater(CashClashPlugin.getInstance(), () -> {
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
                player.setFoodLevel(20);
            }, 2L);
        }
    }
}

