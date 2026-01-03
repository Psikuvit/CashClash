package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.CustomArmorManager;
import me.psikuvit.cashClash.manager.items.CustomItemManager;
import me.psikuvit.cashClash.manager.items.MythicItemManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Consolidated listener for all PlayerInteractEvent handling.
 * Handles: ender pearls, fire charges, supply drops, custom items, custom armor, mythic items, consumables.
 */
public class InteractListener implements Listener {

    private final CustomItemManager customItemManager = CustomItemManager.getInstance();
    private final CustomArmorManager armorManager = CustomArmorManager.getInstance();
    private final MythicItemManager mythicManager = MythicItemManager.getInstance();

    // ==================== ENDER PEARL RESTRICTIONS ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.isCancelled()) return;

        if (event.getEntity() instanceof EnderPearl pearl) {
            if (pearl.getShooter() instanceof Player player) {
                GameSession session = GameManager.getInstance().getPlayerSession(player);
                if (session == null) return;

                CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
                if (ccp != null && ccp.isRespawnProtected()) {
                    event.setCancelled(true);
                    Messages.send(player, "<red>You cannot use ender pearls right after spawning.</red>");
                    return;
                }

                Team team = session.getPlayerTeam(player);
                if (team != null && team.isEnderPearlsDisabled()) {
                    event.setCancelled(true);
                    Messages.send(player, "<red>Your team's Ender Pearls are currently disabled.</red>");
                }
            }
        }
    }

    // ==================== MAIN INTERACT HANDLER ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.useItemInHand() == Event.Result.DENY) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Action action = event.getAction();

        if (item == null) return;

        // Check various item types and delegate
        if (handleEnderPearl(event, player, item)) return;
        if (handleFireCharge(event, player, item)) return;
        if (handleSupplyDrop(event, player, item, action)) return;
        if (handleCustomItem(event, player, item, action)) return;
        if (handleMythicItem(event, player, item, action)) return;
        if (handleCustomArmor(player, action)) return;
        handleConsumable(event, player, item, action);
    }

    // ==================== ENDER PEARL ====================

    private boolean handleEnderPearl(PlayerInteractEvent event, Player player, ItemStack item) {
        if (item.getType() != Material.ENDER_PEARL) return false;

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;

        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp != null && ccp.isRespawnProtected()) {
            event.setCancelled(true);
            Messages.send(player, "<red>You cannot use ender pearls right after spawning.</red>");
            return true;
        }

        Team team = session.getPlayerTeam(player);
        if (team != null && team.isEnderPearlsDisabled()) {
            event.setCancelled(true);
            Messages.send(player, "<red>Your team's Ender Pearls are currently disabled.</red>");
            return true;
        }

        return false;
    }

    // ==================== FIRE CHARGE ====================

    private boolean handleFireCharge(PlayerInteractEvent event, Player player, ItemStack item) {
        if (item.getType() != Material.FIRE_CHARGE) return false;
        if (PDCDetection.getAnyShopTag(item) == null) {
            Fireball fireball = player.launchProjectile(Fireball.class);
            fireball.setIsIncendiary(true);
            fireball.setYield(0f);

            item.setAmount(item.getAmount() - 1);
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    // ==================== SUPPLY DROP ====================

    private boolean handleSupplyDrop(PlayerInteractEvent event, Player player, ItemStack item, Action action) {
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return false;
        if (item.getType() != Material.EMERALD) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(Keys.SUPPLY_DROP_AMOUNT, PersistentDataType.INTEGER)) return false;

        Integer amount = pdc.get(Keys.SUPPLY_DROP_AMOUNT, PersistentDataType.INTEGER);
        if (amount == null) return false;

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;

        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return false;

        // Consume one from hand
        int left = item.getAmount() - 1;
        if (left > 0) {
            item.setAmount(left);
            player.getInventory().setItemInMainHand(item);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        ccp.addCoins(amount);
        Messages.send(player, "<gold>+$" + String.format("%,d", amount) + " from supply drop!</gold>");
        SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        event.setCancelled(true);
        return true;
    }

    // ==================== CUSTOM ITEMS ====================

    private boolean handleCustomItem(PlayerInteractEvent event, Player player, ItemStack item, Action action) {
        if (!item.hasItemMeta()) return false;

        CustomItem type = PDCDetection.getCustomItem(item);
        if (type == null) return false;

        if (isInShoppingPhase(player)) return false;

        switch (type) {
            case GRENADE -> {
                if (action.isRightClick()) {
                    event.setCancelled(true);
                    customItemManager.throwGrenade(player, item, false);
                    return true;
                }
            }
            case SMOKE_CLOUD_GRENADE -> {
                if (action.isRightClick()) {
                    event.setCancelled(true);
                    customItemManager.throwGrenade(player, item, true);
                    return true;
                }
            }
            case MEDIC_POUCH -> {
                if (action == Action.RIGHT_CLICK_AIR) {
                    event.setCancelled(true);
                    customItemManager.useMedicPouchSelf(player, item);
                    return true;
                }
            }
            case TABLET_OF_HACKING -> {
                if (action.isRightClick()) {
                    event.setCancelled(true);
                    customItemManager.useTabletOfHacking(player, item);
                    return true;
                }
            }
            case INVIS_CLOAK -> {
                if (action.isRightClick()) {
                    event.setCancelled(true);
                    customItemManager.handleInvisCloakRightClick(player);
                    return true;
                }
            }
            case BOUNCE_PAD -> {
                if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                    event.setCancelled(true);
                    customItemManager.placeBouncePad(player, item, event.getClickedBlock());
                    return true;
                }
            }
            case BOOMBOX -> {
                if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                    event.setCancelled(true);
                    customItemManager.placeBoombox(player, item, event.getClickedBlock());
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== MYTHIC ITEMS ====================

    private boolean handleMythicItem(PlayerInteractEvent event, Player player, ItemStack item, Action action) {
        MythicItem mythic = PDCDetection.getMythic(item);
        if (mythic == null) return false;

        if (!action.isRightClick()) return false;

        if (isInShoppingPhase(player)) {
            event.setCancelled(true);
            Messages.send(player, "<red>You cannot use mythic abilities during the shopping phase!</red>");
            return true;
        }

        switch (mythic) {
            case COIN_CLEAVER -> {
                event.setCancelled(true);
                mythicManager.useCoinCleaverGrenade(player);
                return true;
            }
            case WIND_BOW -> {
                if (player.isSneaking()) {
                    event.setCancelled(true);
                    mythicManager.useWindBowBoost(player);
                    return true;
                }
            }
            case ELECTRIC_EEL_SWORD -> {
                event.setCancelled(true);
                mythicManager.useElectricEelTeleport(player);
                return true;
            }
            case WARDEN_GLOVES -> {
                event.setCancelled(true);
                mythicManager.useWardenShockwave(player);
                return true;
            }
            case BLAZEBITE_CROSSBOWS -> {
                if (player.isSneaking()) {
                    event.setCancelled(true);
                    mythicManager.toggleBlazebiteMode(player);
                    return true;
                }
            }
            case BLOODWRENCH_CROSSBOW -> {
                mythicManager.startSandstormerCharge(player);
                return true;
            }
        }
        return false;
    }

    // ==================== CUSTOM ARMOR (Magic Helmet) ====================

    private boolean handleCustomArmor(Player player, Action action) {
        if (!action.isRightClick()) return false;

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;

        if (isInShoppingPhase(player)) return false;

        armorManager.onMagicHelmetRightClick(player);
        return true; // Don't cancel - let other handlers process too
    }

    // ==================== CONSUMABLES ====================

    private void handleConsumable(PlayerInteractEvent event, Player player, ItemStack item, Action action) {
        if (!action.isRightClick()) return;

        FoodItem fi = PDCDetection.getFood(item);
        if (fi == null) return;

        if (fi.getDescription().isEmpty()) return;

        if (isInShoppingPhase(player)) {
            event.setCancelled(true);
            Messages.send(player, "<red>You cannot use special consumables during the shopping phase!</red>");
            return;
        }

        if (player.getFoodLevel() >= 20) {
            event.setCancelled(true);

            switch (fi) {
                case SPEED_CARROT -> applyConsumable(player, new PotionEffect(PotionEffectType.SPEED, 20 * 20, 0), "<green>Speed I activated!</green>");
                case GOLDEN_CHICKEN -> applyAbsorption(player);
                case COOKIE_OF_LIFE -> applyConsumable(player, new PotionEffect(PotionEffectType.REGENERATION, 14 * 20, 0), "<dark_green>Regeneration I activated!</dark_green>");
                case SUNSCREEN -> applyConsumable(player, new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30 * 20, 0), "<aqua>Fire Resistance activated!</aqua>");
                case CAN_OF_SPINACH -> applyConsumable(player, new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0), "<gold>Spinach Strength activated!</gold>");
            }

            item.setAmount(item.getAmount() - 1);
        }
    }

    private void applyConsumable(Player p, PotionEffect effect, String msg) {
        p.removePotionEffect(effect.getType());
        p.addPotionEffect(effect);
        Messages.send(p, msg);
        SoundUtils.play(p, Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
    }

    private void applyAbsorption(Player p) {
        double maxAbsorption = 3 * 2.0;
        double currentAbsorption = p.getAbsorptionAmount();
        if (currentAbsorption < maxAbsorption) {
            p.setAbsorptionAmount(maxAbsorption);
        }
        Messages.send(p, "<gold>+3 Absorption Hearts!</gold>");
        SoundUtils.play(p, Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
    }

    // ==================== UTILITIES ====================

    private boolean isInShoppingPhase(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        return session != null && session.getState().isShopping();
    }
}

