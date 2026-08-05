package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
import me.psikuvit.cashClash.gamemode.impl.ProtectThePresidentGamemode;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.armor.CustomArmorManager;
import me.psikuvit.cashClash.manager.items.custom.BloomingRoseHandler;
import me.psikuvit.cashClash.manager.items.custom.BouncePadHandler;
import me.psikuvit.cashClash.manager.items.custom.BoomboxHandler;
import me.psikuvit.cashClash.manager.items.custom.CustomItemManager;
import me.psikuvit.cashClash.manager.items.custom.GrenadeHandler;
import me.psikuvit.cashClash.manager.items.custom.HuntersMarkHandler;
import me.psikuvit.cashClash.manager.items.custom.IceFanHandler;
import me.psikuvit.cashClash.manager.items.custom.InvisCloakHandler;
import me.psikuvit.cashClash.manager.items.custom.MedicPouchHandler;
import me.psikuvit.cashClash.manager.items.custom.OrbOfGravitationHandler;
import me.psikuvit.cashClash.manager.items.custom.OverdriveHandler;
import me.psikuvit.cashClash.manager.items.custom.RadiatingLotusHandler;
import me.psikuvit.cashClash.manager.items.custom.TabletOfHackingHandler;
import me.psikuvit.cashClash.manager.items.mythic.AlchemistWandHandler;
import me.psikuvit.cashClash.manager.items.mythic.BloodwrenchHandler;
import me.psikuvit.cashClash.manager.items.mythic.CarlsBattleaxeHandler;
import me.psikuvit.cashClash.manager.items.mythic.ElectricEelHandler;
import me.psikuvit.cashClash.manager.items.mythic.GoblinSpearHandler;
import me.psikuvit.cashClash.manager.items.mythic.MythicItemManager;
import me.psikuvit.cashClash.manager.items.mythic.WardenGlovesHandler;
import me.psikuvit.cashClash.manager.items.mythic.WindBowHandler;
import me.psikuvit.cashClash.manager.items.weapon.CashBlasterHandler;
import me.psikuvit.cashClash.manager.items.weapon.SoulKatanaHandler;
import me.psikuvit.cashClash.manager.items.weapon.WeaponItemManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.shop.items.WeaponItem;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.enums.RewardType;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Consolidated listener for all PlayerInteractEvent handling.
 * Handles: ender pearls, fire charges, supply drops, custom items, custom armor, mythic items, consumables.
 */
public class InteractListener implements Listener {

    private final CustomItemManager customItemManager = CustomItemManager.getInstance();
    private final MythicItemManager mythicManager = MythicItemManager.getInstance();
    private final CustomArmorManager armorManager = CustomArmorManager.getInstance();
    private final WeaponItemManager weaponItemManager = WeaponItemManager.getInstance();

    // ==================== ENDER PEARL RESTRICTIONS ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.isCancelled()) return;

        // Handle Ender Pearl restrictions
        if (event.getEntity() instanceof EnderPearl pearl) {
            if (pearl.getShooter() instanceof Player player) {
                GameSession session = GameManager.getInstance().getPlayerSession(player);
                if (session == null) return;

                CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
                if (ccp != null && ccp.isRespawnProtected()) {
                    event.setCancelled(true);
                    Messages.send(player, "listener.no-enderpearl-after-spawn");
                    return;
                }

                Team team = session.getPlayerTeam(player);
                if (team != null && team.isEnderPearlsDisabled()) {
                    event.setCancelled(true);
                    Messages.send(player, "listener.enderpearls-disabled");
                }
            }
        }

        // Handle Trident (Goblin Spear) shot system
        if (event.getEntity() instanceof Trident trident) {
            if (trident.getShooter() instanceof Player player) {
                GameSession session = GameManager.getInstance().getPlayerSession(player);
                if (session == null) return;

                // Check if player is dead - cannot use any abilities
                if (session.getState() == GameState.COMBAT) {
                    RoundData roundData = session.getCurrentRoundData();
                    if (roundData != null && !roundData.isAlive(player.getUniqueId())) {
                        event.setCancelled(true);
                        return;
                    }
                }

                // Check respawn protection
                CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
                if (ccp != null && ccp.isRespawnProtected()) {
                    event.setCancelled(true);
                    Messages.send(player, "listener.no-throw-respawn-protection");
                    return;
                }

                ItemStack mainHand = player.getInventory().getItemInMainHand();
                MythicItem mythic = PDCDetection.getMythic(mainHand);

                if (mythic == MythicItem.GOBLIN_SPEAR) {
                    // Check if player is charging - prevent throw during charge
                    if (mythicManager.getHandler(GoblinSpearHandler.class).isGoblinSpearCharging(player.getUniqueId())) {
                        event.setCancelled(true);
                        return;
                    }

                    // Check shot system - if out of shots or reloading, cancel the throw
                    if (!mythicManager.getHandler(GoblinSpearHandler.class).handleGoblinSpearThrow(player)) {
                        event.setCancelled(true);
                        return;
                    }

                    // Tag the projectile with mythic id so hit detection works even when hand is empty
                    trident.getPersistentDataContainer().set(Keys.ITEM_ID, PersistentDataType.STRING, mythic.getConfigKey());
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
        Block block = event.getClickedBlock();
        Action action = event.getAction();

        // Check for ready-up sign clicks FIRST, before item checks
        if (block != null && action.name().contains("RIGHT_CLICK")) {
            handleReadyUp(event, player, block);
        }

        if (isPlayerDead(player)) {
            // Exceptions for dead players (if any)
            if (block != null && block.getType().name().contains("SIGN")) return;

            event.setCancelled(true);
            Messages.send(player, "listener.cannot-use-items-dead");
            return;
        }

        // Overdrive Potion: while invincible the player cannot use ANY inventory item except
        // the Overdrive Potion itself (to cancel early). Left-clicks (block breaking, melee)
        // are untouched.
        if (action.isRightClick() && customItemManager.getHandler(OverdriveHandler.class).isOverdriveInvincible(player.getUniqueId())) {
            CustomItem inHand = item != null ? PDCDetection.getCustomItem(item) : null;
            if (inHand != CustomItem.OVERDRIVE_POTION) {
                event.setCancelled(true);
                return;
            }
        }

        if (item != null) {
            // Check various item types and delegate
            if (handleEnderPearl(event, player, item)) return;
            if (handleFireCharge(event, player, item)) return;
            if (handleSupplyDrop(event, player, item, action)) return;
            if (handleCustomItem(event, player, item, action)) return;
            if (handleWeaponItem(event, player, item, action)) return;
            if (handleMythicItem(event, player, item, action)) return;
        }
    }

    private void handleReadyUp(PlayerInteractEvent event, Player player, Block block) {
        if (!block.getType().name().contains("SIGN")) return;
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        Team team = session.getPlayerTeam(player);
        if (team == null) return;

        team.toggleReadyStatus(player.getUniqueId());
        Messages.send(player, "listener.ready-state",
                "state", team.isPlayerReady(player.getUniqueId()) ? "<green>READY</green>" : "<red>NOT READY</red>");
        Messages.debug(Messages.DebugCategory.GAME, "Player " + player.getName() + " toggled ready status to " + team.isPlayerReady(player.getUniqueId()));
        event.setCancelled(true);

    }

    // ==================== ENDER PEARL ====================
    private boolean handleEnderPearl(PlayerInteractEvent event, Player player, ItemStack item) {
        if (item.getType() != Material.ENDER_PEARL) return false;

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;

        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp != null && ccp.isRespawnProtected()) {
            event.setCancelled(true);
            Messages.send(player, "listener.no-enderpearl-after-spawn");
            return true;
        }

        Team team = session.getPlayerTeam(player);
        if (team != null && team.isEnderPearlsDisabled()) {
            event.setCancelled(true);
            Messages.send(player, "listener.enderpearls-disabled");
            return true;
        }

        return false;
    }

    // ==================== FIRE CHARGE ====================

    private boolean handleFireCharge(PlayerInteractEvent event, Player player, ItemStack item) {
        if (item.getType() != Material.FIRE_CHARGE) return false;
        if (PDCDetection.getAnyShopTag(item) == null) {
            // Prevent dead players from using fire charges
            if (isPlayerDead(player)) {
                event.setCancelled(true);
                Messages.send(player, "listener.cannot-use-items-dead");
                return true;
            }

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

        Integer amount = PDCDetection.getSupplyDropAmount(item);
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

        session.getRewardManager().grant(player, RewardType.SUPPLY_DROP, amount,
                "amount", String.format("%,d", amount));
        SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        event.setCancelled(true);
        return true;
    }

    // ==================== CUSTOM ITEMS ====================

    private boolean handleCustomItem(PlayerInteractEvent event, Player player, ItemStack item, Action action) {
        CustomItem type = PDCDetection.getCustomItem(item);
        if (type == null) return false;

        // Tablet of Hacking is ONLY usable in shopping phase
        if (type == CustomItem.TABLET_OF_HACKING) {
            if (action.isRightClick()) {
                event.setCancelled(true);
                if (isInShoppingPhase(player)) {
                    customItemManager.getHandler(TabletOfHackingHandler.class).useTabletOfHacking(player);
                } else {
                    Messages.send(player, "listener.tablet-shopping-only");
                }
                return true;
            }
            return false;
        }

        // All other custom items cannot be used during shopping
        if (isInShoppingPhase(player)) return false;

        // Flag holder cannot use invisibility cloak
        if (type == CustomItem.INVIS_CLOAK) {
            GameSession session = GameManager.getInstance().getPlayerSession(player);
            if (session != null && session.getGamemode() instanceof CaptureTheFlagGamemode ctf) {
                if (ctf.isSilenced(player.getUniqueId())) {
                    event.setCancelled(true);
                    Messages.send(player, "gamemode-ctf.cannot-use-invis-with-flag");
                    return true;
                }
            }
        }

        switch (type) {
            case GRENADE -> {
                if (action.isRightClick()) {
                    event.setCancelled(true);
                    customItemManager.getHandler(GrenadeHandler.class).throwGrenade(player, item, false);
                    return true;
                }
            }
            case SMOKE_CLOUD_GRENADE -> {
                if (action.isRightClick()) {
                    event.setCancelled(true);
                    customItemManager.getHandler(GrenadeHandler.class).throwGrenade(player, item, true);
                    return true;
                }
            }
            case MEDIC_POUCH -> {
                if (action == Action.RIGHT_CLICK_AIR) {
                    event.setCancelled(true);
                    customItemManager.getHandler(MedicPouchHandler.class).useMedicPouchSelf(player, item);
                    return true;
                }
            }
            case INVIS_CLOAK -> {
                if (action.isRightClick()) {
                    event.setCancelled(true);
                    customItemManager.getHandler(InvisCloakHandler.class).handleInvisCloakRightClick(player);
                    return true;
                }
            }
            case BOUNCE_PAD -> {
                if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                    if (isSilenced(player)) {
                        event.setCancelled(true);
                        Messages.send(player, "listener.cannot-use-items-while-silenced");
                        return true;
                    }
                    event.setCancelled(true);
                    customItemManager.getHandler(BouncePadHandler.class).placeBouncePad(player, item, event.getClickedBlock(), event.getBlockFace());
                    return true;
                }
            }
            case BOOMBOX -> {
                if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                    event.setCancelled(true);
                    customItemManager.getHandler(BoomboxHandler.class).placeBoombox(player, item, event.getClickedBlock());
                    return true;
                }
            }
            case RADIATING_LOTUS -> {
                if (action.isRightClick()) {
                    if (isSilenced(player)) {
                        event.setCancelled(true);
                        Messages.send(player, "gamemode-ctf.cannot-use-while-carrying-flag");
                        return true;
                    }
                    event.setCancelled(true);
                    customItemManager.getHandler(RadiatingLotusHandler.class).startRadiatingLotusCharge(player, item);
                    return true;
                }
            }
            case ICE_FAN -> {
                if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    customItemManager.getHandler(IceFanHandler.class).handleIceFanLeftClick(player, item);
                    return true;
                } else if (action.isRightClick()) {
                    event.setCancelled(true);
                    customItemManager.getHandler(IceFanHandler.class).handleIceFanRightClick(player, item);
                    return true;
                }
            }
            case OVERDRIVE_POTION -> {
                if (action.isRightClick()) {
                    // Already active: right-click again cancels the invincibility early
                    if (customItemManager.getHandler(OverdriveHandler.class).isOverdriveInvincible(player.getUniqueId())) {
                        event.setCancelled(true);
                        customItemManager.getHandler(OverdriveHandler.class).cancelOverdriveEarly(player);
                        return true;
                    }
                    if (isSilenced(player)) {
                        event.setCancelled(true);
                        Messages.send(player, "gamemode-ctf.cannot-use-while-carrying-flag");
                        return true;
                    }
                    event.setCancelled(true);
                    customItemManager.getHandler(OverdriveHandler.class).useOverdrivePotion(player, item);
                    return true;
                }
            }
            case HUNTERS_MARK -> {
                if (action.isRightClick()) {
                    event.setCancelled(true);
                    customItemManager.getHandler(HuntersMarkHandler.class).startHunterMarkCharge(player, item);
                    return true;
                }
            }
            case BLOOMING_ROSE -> {
                if (action.isRightClick()) {
                    event.setCancelled(true);
                    Location loc = event.getClickedBlock() != null
                            ? event.getClickedBlock().getLocation()
                            : player.getLocation();
                    customItemManager.getHandler(BloomingRoseHandler.class).placeBloomingRose(player, item, loc);
                    return true;
                }
            }
            case ORB_OF_GRAVITATION -> {
                if (action.isRightClick()) {
                    event.setCancelled(true);
                    if (customItemManager.getHandler(OrbOfGravitationHandler.class).hasLiveOrb(player)) {
                        customItemManager.getHandler(OrbOfGravitationHandler.class).activateOrbByOwner(player);
                    } else {
                        customItemManager.getHandler(OrbOfGravitationHandler.class).throwOrbOfGravitation(player);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== WEAPONS ====================

    private boolean handleWeaponItem(PlayerInteractEvent event, Player player, ItemStack item, Action action) {
        WeaponItem weapon = PDCDetection.getWeapon(item);
        if (weapon == null) return false;

        // Special weapon abilities cannot be used during shopping
        if (isInShoppingPhase(player)) return false;

        switch (weapon) {
            case CASH_BLASTER -> {
                if (player.isSneaking() && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
                    event.setCancelled(true);
                    armorManager.lockMythicShift(player);
                    weaponItemManager.getHandler(CashBlasterHandler.class).onCashBlasterToggle(player);
                    return true;
                }
            }
            case SOUL_KATANA -> {
                if (action.isRightClick() && player.isSneaking()) {
                    event.setCancelled(true);
                    armorManager.lockMythicShift(player);
                    weaponItemManager.getHandler(SoulKatanaHandler.class).usePhantomSlice(player);
                    return true;
                }
            }
            default -> {
                // No special interaction for standard weapons
            }
        }
        return false;
    }

    /**
     * Prevents "fake consumable" items (used only to leverage the vanilla hand-raise/use
     * animation for hold-to-charge abilities) from ever actually being eaten.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCustomItemConsumeCancel(PlayerItemConsumeEvent event) {
        CustomItem type = PDCDetection.getCustomItem(event.getItem());
        if (type == CustomItem.RADIATING_LOTUS || type == CustomItem.HUNTERS_MARK) {
            event.setCancelled(true);
        }
    }

    // ==================== MYTHIC ITEMS ====================

    private boolean handleMythicItem(PlayerInteractEvent event, Player player, ItemStack item, Action action) {
        MythicItem mythic = PDCDetection.getMythic(item);
        if (mythic == null) return false;

        if (!action.isRightClick()) return false;

        if (isInShoppingPhase(player)) {
            event.setCancelled(true);
            Messages.send(player, "gamestate.cannot-use-abilities-shopping");
            return true;
        }

        switch (mythic) {
            case CARLS_BATTLEAXE -> {
                event.setCancelled(true);
                mythicManager.getHandler(CarlsBattleaxeHandler.class).activateCarlsSpinAttack(player);
                return true;
            }
            case WIND_BOW -> {
                if (player.isSneaking()) {
                    if (isSilenced(player)) {
                        event.setCancelled(true);
                        Messages.send(player, "listener.cannot-use-abilities-while-silenced");
                        return true;
                    }
                    event.setCancelled(true);
                    armorManager.lockMythicShift(player);
                    mythicManager.getHandler(WindBowHandler.class).useWindBowBoost(player);
                    return true;
                }
            }
            case ELECTRIC_EEL_SWORD -> {
                if (isSilenced(player)) {
                    event.setCancelled(true);
                    Messages.send(player, "listener.cannot-use-abilities-while-silenced");
                    return true;
                }
                event.setCancelled(true);
                mythicManager.getHandler(ElectricEelHandler.class).useElectricEelTeleport(player);
                return true;
            }
            case GOBLIN_SPEAR -> {
                // Allow normal throws; only cancel when activating charge (sneaking)
                if (player.isSneaking()) {
                    if (isSilenced(player)) {
                        event.setCancelled(true);
                        Messages.send(player, "listener.cannot-use-abilities-while-silenced");
                        return true;
                    }
                    event.setCancelled(true);
                    armorManager.lockMythicShift(player);
                    mythicManager.getHandler(GoblinSpearHandler.class).startGoblinSpearCharge(player);
                    return true;
                }
            }
            case WARDEN_GLOVES -> {
                event.setCancelled(true);
                mythicManager.getHandler(WardenGlovesHandler.class).useWardenShockwave(player);
                return true;
            }
            case ALCHEMIST_WAND -> {
                event.setCancelled(true);

                if (player.isSneaking()) {
                    armorManager.lockMythicShift(player);
                    mythicManager.getHandler(AlchemistWandHandler.class).useAlchemistTaunt(player);
                } else {
                    mythicManager.getHandler(AlchemistWandHandler.class).useAlchemistBlinkSwap(player);
                }
                return true;
            }
            case BLOODWRENCH_CROSSBOW -> {
                // Shift + Right-click to toggle mode
                if (player.isSneaking()) {
                    event.setCancelled(true);
                    armorManager.lockMythicShift(player);
                    mythicManager.getHandler(BloodwrenchHandler.class).toggleBloodwrenchMode(player);
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== GAMEMODE SPECIFIC HANDLERS ====================

    /**
     * Handle Protect the President buff selection
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPresidentBuffSelection(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        
        if (session == null || session.getGamemode() == null) return;
        if (!(session.getGamemode() instanceof ProtectThePresidentGamemode gamemode)) return;

        // Check if in selection phase
        if (!gamemode.isInBuffSelectionPhase()) return;

        // Check if this is a right-click and main hand
        if (event.getAction().name().contains("LEFT")) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return;

        int slot = player.getInventory().getHeldItemSlot();
        
        // Only handle slots 1, 3, 5, 7 (buff selection slots)
        if (slot != 1 && slot != 3 && slot != 5 && slot != 7) return;
        
        if (gamemode.handlePresidentBuffSelection(player, slot)) {
            event.setCancelled(true);
        }
    }

    // ==================== UTILITIES ====================

    private boolean isInShoppingPhase(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        return session != null && (session.getState() == GameState.SHOPPING || session.isActionsRestricted());
    }

    private boolean isRespawnProtected(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;
        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        return ccp != null && ccp.isRespawnProtected();
    }

    private boolean isPlayerDead(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;
        if (session.getState() != GameState.COMBAT) return false;
        RoundData roundData = session.getCurrentRoundData();
        return roundData != null && !roundData.isAlive(player.getUniqueId());
    }

    private boolean isSilenced(Player player) {
        if (isPlayerDead(player)) {
            return true;
        }
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null || session.getGamemode() == null) return false;
        if (!(session.getGamemode() instanceof CaptureTheFlagGamemode gamemode)) return false;
        return gamemode.isSilenced(player.getUniqueId());
    }
}

