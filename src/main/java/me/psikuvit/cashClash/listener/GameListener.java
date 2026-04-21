package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.event.PlayerBackToGameEvent;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

/**
 * General game listener for events that are only tracked once.
 * Handles: death, food, drops, sneak, flight, bow shoot, projectile hit, entity interactions, inventory clicks.
 */
public class GameListener implements Listener {

    private final CustomArmorManager armorManager = CustomArmorManager.getInstance();
    private final CustomItemManager customItemManager = CustomItemManager.getInstance();
    private final MythicItemManager mythicManager = MythicItemManager.getInstance();

    // ==================== PLAYER DEATH ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.isCancelled()) return;

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

        // Clear invisibility cloak state on death (armor is preserved by keepInventory)
        customItemManager.clearInvisCloakOnDeath(player);

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

        // Notify gamemode of death
        Gamemode gamemode = session.getGamemode();
        if (gamemode != null) {
            gamemode.onPlayerDeath(player, killer);
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
                return LocationUtils.copyToWorld(template.getSpectatorSpawn(), session.getGameWorld());
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
        
        // Apply investor set bonus (12.5% per piece)
        double investorMultiplier = armorManager.getInvestorMultiplier(killer);
        long finalReward = (long) (killReward * investorMultiplier);
        
        killerCCP.addCoins(finalReward);
        
        if (investorMultiplier > 1.0) {
            Messages.send(killer, "bonus.bonus-investor", "multiplier_percent",
                    String.format("%.1f", (investorMultiplier - 1.0) * 100));
        }

        // Apply kill team split bonus to entire team
        applyKillTeamSplitBonus(session, killer);

        // Handle armor set kill effects
        armorManager.onPlayerKill(killer);
        armorManager.onDragonKill(killer);
        armorManager.onFlamebringerKill(killer);
    }

    private void handlePermanentSpectator(Player player, Location spectatorLocation) {
        Messages.send(player, "listener.out-of-lives");
        SchedulerUtils.runTask(() -> {
            player.spigot().respawn();
            player.teleport(spectatorLocation);
            player.setGameMode(GameMode.SPECTATOR);
        });
    }

    private void handleTemporarySpectatorAndRespawn(Player player, Location spectatorLocation) {
        int respawnDelaySec = ConfigManager.getInstance().getRespawnDelay();
        int respawnProtectionSec = ConfigManager.getInstance().getRespawnProtection();

        Messages.send(player, "listener.respawn-delay", "seconds", String.valueOf(respawnDelaySec));

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

        // If round ended (moved to shopping phase), don't respawn into combat
        if (!isGameInCombat(session)) {
            Messages.send(player, "listener.round-ended-no-respawn");
            return;
        }

        teleportToSpawn(player, session);
        restorePlayerHealth(player);
        preparePlayerForCombat(player, session, respawnProtectionSec);
        Messages.send(player, "listener.respawned");

        // Fire custom event when player is back in game
        Bukkit.getPluginManager().callEvent(new PlayerBackToGameEvent(player));
    }

    /**
     * Check if game is in combat phase
     */
    private boolean isGameInCombat(GameSession session) {
        return session.getState() == GameState.COMBAT;
    }

    /**
     * Teleport player to spawn location
     */
    private void teleportToSpawn(Player player, GameSession session) {
        Location spawnLocation = session.getSpawnForPlayer(player.getUniqueId());
        if (spawnLocation != null) {
            player.teleport(spawnLocation);
        }
    }

    /**
     * Restore player health to max
     */
    private void restorePlayerHealth(Player player) {
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        player.setHealth(Math.max(1.0, maxHealth));
    }

    /**
     * Prepare player for combat (food, gamemode, protection, bonus)
     */
    private void preparePlayerForCombat(Player player, GameSession session, int respawnProtectionSec) {
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player p = event.getPlayer();
        ItemStack consumed = event.getItem();

        if (consumed.getType().isAir()) return;

        // Check consumable restrictions
        if (checkConsumableRestrictions(event, p, consumed)) {
            return;
        }

        // Handle special consumables
        handleSpecialConsumable(p, consumed);
    }

    /**
     * Check consumable restrictions (buff potions, shopping phase, dead players)
     * @return true if consumption should be cancelled
     */
    private boolean checkConsumableRestrictions(PlayerItemConsumeEvent event, Player p, ItemStack consumed) {
        // Check if this is a Protect the President buff selection potion (undrinkable)
        if (PDCDetection.isBuffSelectionPotion(consumed)) {
            event.setCancelled(true);
            Messages.send(p, "listener.buff-potion-undrinkable");
            return true;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(p);
        if (session == null) {
            return false;
        }

        // Prevent consumption in shopping phase
        if (session.getState() == GameState.SHOPPING) {
            FoodItem fi = PDCDetection.getFood(consumed);
            if (fi != null) {
                event.setCancelled(true);
                Messages.send(p, "gamestate.cannot-use-during-shopping");
                return true;
            }
        }

        // Prevent dead players from consuming
        return preventDeadPlayerConsumption(event, p, session);
    }

    /**
     * Prevent dead players from using consumables
     * @return true if consumption should be cancelled
     */
    private boolean preventDeadPlayerConsumption(PlayerItemConsumeEvent event, Player p, GameSession session) {
        if (session.getState() == GameState.COMBAT) {
            RoundData roundData = session.getCurrentRoundData();
            if (roundData != null && !roundData.isAlive(p.getUniqueId())) {
                event.setCancelled(true);
                Messages.send(p, "listener.cannot-use-consumables-dead");
                return true;
            }
        }
        return false;
    }

    /**
     * Handle special consumables with cooldowns
     */
    private void handleSpecialConsumable(Player p, ItemStack consumed) {
        FoodItem fi = PDCDetection.getFood(consumed);
        if (fi == null) return;

        // Data components already handle applying effects; we only gate cooldown + empty bottle cleanup
        if (isSpecialConsumable(fi)) {
            if (!checkConsumableCooldown(p)) {
                return;
            }
        }

        if (fi == FoodItem.SUNSCREEN) {
            SchedulerUtils.runTaskLater(() -> removeEmptyBottle(p), 1L);
        }
    }

    /**
     * Check if consumable is on cooldown and apply it
     * @return true if not on cooldown
     */
    private boolean checkConsumableCooldown(Player p) {
        CooldownManager cooldownManager = CooldownManager.getInstance();
        if (cooldownManager.isOnCooldown(p.getUniqueId(), CooldownManager.Keys.CONSUMABLE)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(p.getUniqueId(), CooldownManager.Keys.CONSUMABLE);
            Messages.send(p, "listener.consumable-cooldown", "remaining", String.valueOf(remaining));
            return false;
        }
        int cooldownSeconds = ItemsConfig.getInstance().getConsumableCooldown();
        cooldownManager.setCooldownSeconds(p.getUniqueId(), CooldownManager.Keys.CONSUMABLE, cooldownSeconds);
        return true;
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

    private void removeEmptyBottle(Player p) {
        ItemStack mainHand = p.getInventory().getItemInMainHand();
        if (mainHand.getType() == Material.GLASS_BOTTLE) {
            mainHand.setAmount(Math.max(0, mainHand.getAmount() - 1));
            return;
        }
        ItemStack offHand = p.getInventory().getItemInOffHand();
        if (offHand.getType() == Material.GLASS_BOTTLE) {
            offHand.setAmount(Math.max(0, offHand.getAmount() - 1));
            return;
        }
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item != null && item.getType() == Material.GLASS_BOTTLE) {
                item.setAmount(Math.max(0, item.getAmount() - 1));
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

        if (session.getState() == GameState.SHOPPING) return;

        armorManager.onPlayerToggleSneak(p, event.isSneaking());
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

        handleMythicBowShot(event, player, mythic);
    }

    /**
     * Handle mythic bow shots with mode tagging
     */
    private void handleMythicBowShot(EntityShootBowEvent event, Player player, MythicItem mythic) {
        switch (mythic) {
            case BLOODWRENCH_CROSSBOW -> handleBloodwrenchShot(event, player);
            case BLAZEBITE_CROSSBOWS -> handleBlazebiteShot(event, player, event.getBow());
            case WIND_BOW -> handleWindBowShot(event, player);
            default -> { /* No special handling */ }
        }
    }

    /**
     * Handle BloodWrench crossbow shot
     */
    private void handleBloodwrenchShot(EntityShootBowEvent event, Player player) {
        if (!mythicManager.handleBloodwrenchShot(player)) {
            event.setCancelled(true);
        } else if (event.getProjectile() instanceof Arrow arrow) {
            String mode = mythicManager.isBloodwrenchRapidMode(player) ? "rapid" : "supercharged";
            arrow.getPersistentDataContainer().set(Keys.BLOODWRENCH_MODE, PersistentDataType.STRING, mode);
        }
    }

    /**
     * Handle BlazeBite crossbow shot
     */
    private void handleBlazebiteShot(EntityShootBowEvent event, Player player, ItemStack bow) {
        if (!mythicManager.handleBlazebiteShot(player, bow)) {
            event.setCancelled(true);
        } else if (event.getProjectile() instanceof Arrow arrow) {
            String mode = PDCDetection.getBlazebiteMode(bow);
            if (mode != null) {
                arrow.getPersistentDataContainer().set(Keys.BLAZEBITE_MODE, PersistentDataType.STRING, mode);
            }
        }
    }

    /**
     * Handle Wind Bow shot
     */
    private void handleWindBowShot(EntityShootBowEvent event, Player player) {
        if (!mythicManager.handleWindBowShot(player)) {
            event.setCancelled(true);
        }
    }

    // ==================== PROJECTILE HIT ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.isCancelled()) return;

        if (event.getEntity() instanceof Arrow arrow) {
            handleArrowHit(arrow, event);
        } else if (event.getEntity() instanceof Trident trident) {
            handleTridentHit(trident, event);
        }

        handleCustomProjectileHit(event);
    }

    /**
     * Handle arrow hit with mythic effects
     */
    private void handleArrowHit(Arrow arrow, ProjectileHitEvent event) {
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        handleBlazebiteArrow(shooter, arrow, event);
        handleWindBowArrow(shooter, event);
        handleBloodwrenchArrow(shooter, arrow, event);
    }

    /**
     * Handle BlazeBite arrow hit
     */
    private void handleBlazebiteArrow(Player shooter, Arrow arrow, ProjectileHitEvent event) {
        String blazebiteMode = PDCDetection.getArrowBlazebiteMode(arrow);
        if (blazebiteMode == null) return;

        Location hitLoc = event.getHitEntity() != null ? event.getHitEntity().getLocation() : arrow.getLocation();
        boolean isGlacier = "glacier".equals(blazebiteMode);
        mythicManager.handleBlazebiteHit(shooter, event.getHitEntity(), hitLoc, isGlacier);
    }

    /**
     * Handle Wind Bow arrow hit
     */
    private void handleWindBowArrow(Player shooter, ProjectileHitEvent event) {
        ItemStack bow = shooter.getInventory().getItemInMainHand();
        MythicItem mythic = PDCDetection.getMythic(bow);

        if (mythic == MythicItem.WIND_BOW && event.getHitEntity() instanceof Player hitPlayer) {
            mythicManager.handleWindBowHit(shooter, hitPlayer);
        }
    }

    /**
     * Handle BloodWrench arrow hit
     */
    private void handleBloodwrenchArrow(Player shooter, Arrow arrow, ProjectileHitEvent event) {
        String bloodwrenchMode = PDCDetection.getArrowBloodwrenchMode(arrow);
        if (bloodwrenchMode == null) return;

        Location hitLoc = event.getHitEntity() != null ? event.getHitEntity().getLocation() : arrow.getLocation();

        if ("rapid".equals(bloodwrenchMode)) {
            mythicManager.handleBloodwrenchRapidHit(shooter, hitLoc);
        } else if ("supercharged".equals(bloodwrenchMode)) {
            mythicManager.handleBloodwrenchSuperchargedHit(shooter, hitLoc);
        }
    }

    /**
     * Handle trident hit with mythic effects
     */
    private void handleTridentHit(Trident trident, ProjectileHitEvent event) {
        if (!(trident.getShooter() instanceof Player shooter)) return;

        MythicItem mythic = resolveTridentMythic(shooter, trident);
        if (mythic == MythicItem.GOBLIN_SPEAR && event.getHitEntity() instanceof LivingEntity target) {
            mythicManager.handleGoblinSpearHit(shooter, target);
            Messages.debug("GOBLIN_SPEAR hit handled for " + target.getName());
        }
    }

    /**
     * Resolve which mythic type a trident is
     */
    private MythicItem resolveTridentMythic(Player shooter, Trident trident) {
        MythicItem mythic = PDCDetection.getMythic(trident);
        if (mythic == null) {
            ItemStack mainHand = shooter.getInventory().getItemInMainHand();
            mythic = PDCDetection.getMythic(mainHand);
        }
        return mythic;
    }

    /**
     * Handle custom item projectile hits (Cash Blaster, etc.)
     */
    private void handleCustomProjectileHit(ProjectileHitEvent event) {
        if (!(event.getHitEntity() instanceof Player)) return;

        ProjectileSource projectileShooter = event.getEntity().getShooter();
        if (!(projectileShooter instanceof Player attacker)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (!item.hasItemMeta()) return;

        // Prevent custom projectiles during shopping
        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
        if (session != null && session.getState() == GameState.SHOPPING) {
            event.setCancelled(true);
            return;
        }

        CustomItem type = PDCDetection.getCustomItem(item);
        if (type == CustomItem.CASH_BLASTER) {
            customItemManager.handleCashBlasterHit(attacker);
        }
    }

    // ==================== ENTITY INTERACTIONS ====================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteractAtEntityShop(PlayerInteractAtEntityEvent event) {
        if (event.isCancelled()) return;

        var e = event.getRightClicked();
        if (e.getType() != EntityType.VILLAGER) return;

        if (!PDCDetection.isShopNPC(e)) return;

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
        if (session != null && session.getState() == GameState.SHOPPING) return;

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

        Integer amount = PDCDetection.getSupplyDropAmount(current);
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
        Messages.send(p, "cashquake.supply-drop-reward", "amount", String.format("%,d", amount));
        SoundUtils.play(p, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    // ==================== PLAYER RESPAWN ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null && session.getState() == GameState.SHOPPING) {
            Bukkit.getScheduler().runTaskLater(CashClashPlugin.getInstance(), () -> {
                var attr = player.getAttribute(Attribute.MAX_HEALTH);
                double maxHealth = attr != null ? attr.getValue() : 20.0;
                player.setHealth(Math.clamp(maxHealth, 1.0, player.getHealth()));
                player.setFoodLevel(20);
            }, 2L);
        }
    }

    @EventHandler
    public void onPlayerBackInGame(PlayerBackToGameEvent event) {
        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null) {
            session.getGamemode().onPlayerSpawn(player);
        }
    }

    /**
     * Distribute the kill team split bonus to the entire team of the killer.
     * Total bonus is split evenly among all team members.
     */
    private void applyKillTeamSplitBonus(GameSession session, Player killer) {
        Team killerTeam = session.getTeamRed().hasPlayer(killer.getUniqueId()) ? session.getTeamRed() : session.getTeamBlue();
        if (killerTeam == null) return;

        long totalBonus = ConfigManager.getInstance().getKillTeamSplitBonus();
        if (totalBonus <= 0) return;

        int teamSize = killerTeam.getPlayers().size();
        if (teamSize == 0) return;

        long bonusPerPlayer = totalBonus / teamSize;

        for (UUID uuid : killerTeam.getPlayers()) {
            CashClashPlayer player = session.getCashClashPlayer(uuid);
            if (player != null) {
                player.addCoins(bonusPerPlayer);
                Player bukkitPlayer = Bukkit.getPlayer(uuid);
                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    Messages.send(bukkitPlayer, "listener.team-kill-bonus", "bonus", String.format("%,d", bonusPerPlayer));
                }
            }
        }
    }

    // ==================== CTF FLAG PICKUP/SCORE (Pressure Plate) ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractPressurePlate(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;

        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);

        if (session == null || session.getState() != GameState.COMBAT) return;
        if (!(session.getGamemode() instanceof CaptureTheFlagGamemode gamemode)) return;

        // Let the gamemode handle all plate logic (pickup + scoring)
        gamemode.checkPlateCapture(player);
    }
}



