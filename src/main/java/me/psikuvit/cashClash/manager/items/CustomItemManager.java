package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.CooldownManager;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registry and facade for every custom item's behaviour. Holds one
 * {@link CustomItemHandler} per item and routes public calls to the right one, so
 * callers (listeners, GUIs, commands) keep a single stable entry point while each
 * item's state and logic live in its own handler class.
 *
 * <p>Anything shared across items lives here instead of in a single handler: the
 * config/cooldown/armor dependencies handed to every handler, and the healing-
 * reduction hook (Soul Katana applies it; Radiating Lotus and Blooming Rose
 * consume it). {@link #cleanup()} fans out to every handler and clears the shared
 * hook state.</p>
 */
public class CustomItemManager {

    private static CustomItemManager instance;

    private final CooldownManager cooldownManager;
    private final ItemsConfig cfg;
    private final CustomArmorManager armorManager;

    // Handler registry - one per item
    private final GrenadeHandler grenadeHandler;
    private final MedicPouchHandler medicPouchHandler;
    private final TabletOfHackingHandler tabletOfHackingHandler;
    private final InvisCloakHandler invisCloakHandler;
    private final BagOfPotatoesHandler bagOfPotatoesHandler;
    private final CashBlasterHandler cashBlasterHandler;
    private final BouncePadHandler bouncePadHandler;
    private final BoomboxHandler boomboxHandler;
    private final RespawnAnchorHandler respawnAnchorHandler;
    private final TotemOfHauntingHandler totemOfHauntingHandler;
    private final RadiatingLotusHandler radiatingLotusHandler;
    private final IceFanHandler iceFanHandler;
    private final OverdriveHandler overdriveHandler;
    private final HuntersMarkHandler huntersMarkHandler;
    private final BloomingRoseHandler bloomingRoseHandler;
    private final OrbOfGravitationHandler orbOfGravitationHandler;
    private final SoulKatanaHandler soulKatanaHandler;

    // Shared: healing-reduction hook (e.g. Soul Katana's debuff), consumed by any item's heals
    private final Map<UUID, Long> healingReducedUntil;
    private final Map<UUID, Double> healingReductionMultiplier;

    private CustomItemManager() {
        this.cooldownManager = CooldownManager.getInstance();
        this.cfg = ItemsConfig.getInstance();
        this.armorManager = CustomArmorManager.getInstance();
        this.grenadeHandler = new GrenadeHandler(this);
        this.medicPouchHandler = new MedicPouchHandler(this);
        this.tabletOfHackingHandler = new TabletOfHackingHandler(this);
        this.invisCloakHandler = new InvisCloakHandler(this);
        this.bagOfPotatoesHandler = new BagOfPotatoesHandler(this);
        this.cashBlasterHandler = new CashBlasterHandler(this);
        this.bouncePadHandler = new BouncePadHandler(this);
        this.boomboxHandler = new BoomboxHandler(this);
        this.respawnAnchorHandler = new RespawnAnchorHandler(this);
        this.totemOfHauntingHandler = new TotemOfHauntingHandler(this);
        this.radiatingLotusHandler = new RadiatingLotusHandler(this);
        this.iceFanHandler = new IceFanHandler(this);
        this.overdriveHandler = new OverdriveHandler(this);
        this.huntersMarkHandler = new HuntersMarkHandler(this);
        this.bloomingRoseHandler = new BloomingRoseHandler(this);
        this.orbOfGravitationHandler = new OrbOfGravitationHandler(this);
        this.soulKatanaHandler = new SoulKatanaHandler(this);
        this.healingReducedUntil = new HashMap<>();
        this.healingReductionMultiplier = new HashMap<>();
    }

    public static CustomItemManager getInstance() {
        if (instance == null) {
            instance = new CustomItemManager();
        }
        return instance;
    }

    // ---- Shared dependencies handed to every handler ----

    CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    ItemsConfig getCfg() {
        return cfg;
    }

    CustomArmorManager getArmorManager() {
        return armorManager;
    }

    Map<UUID, Long> getHealingReducedUntil() {
        return healingReducedUntil;
    }

    Map<UUID, Double> getHealingReductionMultiplier() {
        return healingReductionMultiplier;
    }

    // ==================== SHARED EFFECT HOOKS ====================

    /**
     * Applies a temporary healing-reduction debuff to a target (e.g. Soul Katana's Phantom
     * Slice). Any item's heal application should multiply its heal amount by
     * {@link #getHealingMultiplier(UUID)} before applying it.
     */
    public void applyHealingReduction(UUID target, double multiplier, long durationSeconds) {
        healingReducedUntil.put(target, System.currentTimeMillis() + durationSeconds * 1000L);
        healingReductionMultiplier.put(target, multiplier);
    }

    /**
     * @return 1.0 if the target has no active healing-reduction debuff, else the active multiplier
     */
    public double getHealingMultiplier(UUID target) {
        Long until = healingReducedUntil.get(target);
        if (until == null || System.currentTimeMillis() >= until) {
            healingReducedUntil.remove(target);
            healingReductionMultiplier.remove(target);
            return 1.0;
        }
        return healingReductionMultiplier.getOrDefault(target, 1.0);
    }

    // ==================== GRENADE ====================

    public void throwGrenade(Player player, ItemStack item, boolean isSmoke) {
        grenadeHandler.throwGrenade(player, item, isSmoke);
    }

    // ==================== MEDIC POUCH ====================

    public void useMedicPouchSelf(Player player, ItemStack item) {
        medicPouchHandler.useMedicPouchSelf(player, item);
    }

    public void useMedicPouchAlly(Player player, Player target, ItemStack item, GameSession session) {
        medicPouchHandler.useMedicPouchAlly(player, target, item, session);
    }

    // ==================== TABLET OF HACKING ====================

    public void useTabletOfHacking(Player player) {
        tabletOfHackingHandler.useTabletOfHacking(player);
    }

    // Called when a player selects an enemy in the PlayerSelector for Tablet of Hacking
    public void handleTabletOfHackingSelection(Player viewer, Player target) {
        tabletOfHackingHandler.handleTabletOfHackingSelection(viewer, target);
    }

    // ==================== INVIS CLOAK ====================

    public void toggleInvisCloak(Player player, boolean turnOn) {
        invisCloakHandler.toggleInvisCloak(player, turnOn);
    }

    public void handleInvisCloakRightClick(Player player) {
        invisCloakHandler.handleInvisCloakRightClick(player);
    }

    public boolean isInvisActive(UUID uuid) {
        return invisCloakHandler.isInvisActive(uuid);
    }

    public void clearInvisCloakOnDeath(Player player) {
        invisCloakHandler.clearInvisCloakOnDeath(player);
    }

    // ==================== BAG OF POTATOES ====================

    public void handleBagOfPotatoesHit(Player attacker, ItemStack item, GameSession session) {
        bagOfPotatoesHandler.handleBagOfPotatoesHit(attacker, item, session);
    }

    // ==================== CASH BLASTER ====================

    public void handleCashBlasterHit(Player attacker) {
        cashBlasterHandler.handleCashBlasterHit(attacker);
    }

    public void onCashBlasterToggle(Player player) {
        cashBlasterHandler.onCashBlasterToggle(player);
    }

    public boolean hasCashBlasterInHand(Player player) {
        return cashBlasterHandler.hasCashBlasterInHand(player);
    }

    public void onCashBlasterShoot(EntityShootBowEvent event) {
        cashBlasterHandler.onCashBlasterShoot(event);
    }

    public void onProfitVortexArrowHit(Arrow arrow) {
        cashBlasterHandler.onProfitVortexArrowHit(arrow);
    }

    public void onProfitVortexDeath(PlayerDeathEvent event) {
        cashBlasterHandler.onProfitVortexDeath(event);
    }

    // ==================== BOUNCE PAD ====================

    public void placeBouncePad(Player player, ItemStack item, Block clickedBlock, BlockFace face) {
        bouncePadHandler.placeBouncePad(player, item, clickedBlock, face);
    }

    public void handleBouncePad(Player player, Block block) {
        bouncePadHandler.handleBouncePad(player, block);
    }

    public boolean isBouncePad(Block block) {
        return bouncePadHandler.isBouncePad(block);
    }

    // ==================== BOOMBOX ====================

    public void placeBoombox(Player player, ItemStack item, Block clickedBlock) {
        boomboxHandler.placeBoombox(player, item, clickedBlock);
    }

    public boolean isBoombox(Block block) {
        return boomboxHandler.isBoombox(block);
    }

    // ==================== RESPAWN ANCHOR ====================

    public void useRespawnAnchor(Player reviver, Player target, ItemStack item) {
        respawnAnchorHandler.useRespawnAnchor(reviver, target, item);
    }

    public boolean canBeRevived(Player reviver, Player target) {
        return respawnAnchorHandler.canBeRevived(reviver, target);
    }

    // ==================== TOTEM OF HAUNTING ====================

    public boolean isTotemInvincible(UUID uuid) {
        return totemOfHauntingHandler.isTotemInvincible(uuid);
    }

    public void triggerTotemOfHaunting(Player player, ItemStack totemItem) {
        totemOfHauntingHandler.triggerTotemOfHaunting(player, totemItem);
    }

    // ==================== RADIATING LOTUS ====================

    public void startRadiatingLotusCharge(Player player, ItemStack item) {
        radiatingLotusHandler.startRadiatingLotusCharge(player, item);
    }

    // ==================== ICE FAN ====================

    public boolean isIceFanAbilityDamage(UUID attackerUuid) {
        return iceFanHandler.isIceFanAbilityDamage(attackerUuid);
    }

    public void handleIceFanLeftClick(Player player, ItemStack item) {
        iceFanHandler.handleIceFanLeftClick(player, item);
    }

    public void handleIceFanRightClick(Player player, ItemStack item) {
        iceFanHandler.handleIceFanRightClick(player, item);
    }

    // ==================== OVERDRIVE POTION ====================

    public boolean isOverdriveInvincible(UUID uuid) {
        return overdriveHandler.isOverdriveInvincible(uuid);
    }

    public void useOverdrivePotion(Player player, ItemStack item) {
        overdriveHandler.useOverdrivePotion(player, item);
    }

    public void cancelOverdriveEarly(Player player) {
        overdriveHandler.cancelOverdriveEarly(player);
    }

    // ==================== HUNTER'S MARK ====================

    public void startHunterMarkCharge(Player player, ItemStack item) {
        huntersMarkHandler.startHunterMarkCharge(player, item);
    }

    public void clearHunterMark(UUID targetUuid) {
        huntersMarkHandler.clearHunterMark(targetUuid);
    }

    public double getVulnerabilityMultiplier(UUID targetUuid) {
        return huntersMarkHandler.getVulnerabilityMultiplier(targetUuid);
    }

    // ==================== BLOOMING ROSE ====================

    public void placeBloomingRose(Player player, ItemStack item, Location loc) {
        bloomingRoseHandler.placeBloomingRose(player, item, loc);
    }

    public void onRoseStructureBroken(Block block) {
        bloomingRoseHandler.onRoseStructureBroken(block);
    }

    public double getBloomingRoseDamageReduction(Player player) {
        return bloomingRoseHandler.getBloomingRoseDamageReduction(player);
    }

    public double getBloomingRoseMinHealth(Player player) {
        return bloomingRoseHandler.getBloomingRoseMinHealth(player);
    }

    // ==================== ORB OF GRAVITATION ====================

    public boolean isOrbEntity(Entity entity) {
        return orbOfGravitationHandler.isOrbEntity(entity);
    }

    public boolean hasLiveOrb(Player player) {
        return orbOfGravitationHandler.hasLiveOrb(player);
    }

    public void activateOrbByOwner(Player player) {
        orbOfGravitationHandler.activateOrbByOwner(player);
    }

    public void throwOrbOfGravitation(Player player) {
        orbOfGravitationHandler.throwOrbOfGravitation(player);
    }

    public void activateOrb(Snowball orb) {
        orbOfGravitationHandler.activateOrb(orb);
    }

    public void handleOrbHitByChargedArrow(Arrow arrow, Snowball orb) {
        orbOfGravitationHandler.handleOrbHitByChargedArrow(arrow, orb);
    }

    // ==================== SOUL KATANA ====================

    public void usePhantomSlice(Player player) {
        soulKatanaHandler.usePhantomSlice(player);
    }

    public void handleSoulKatanaLand(Player player) {
        soulKatanaHandler.handleSoulKatanaLand(player);
    }

    public boolean isPhantomSliceDamage(UUID attackerUuid) {
        return soulKatanaHandler.isPhantomSliceDamage(attackerUuid);
    }

    // ==================== UTILITY METHODS ====================

    public void consumeItem(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        grenadeHandler.cleanup();
        medicPouchHandler.cleanup();
        tabletOfHackingHandler.cleanup();
        invisCloakHandler.cleanup();
        bagOfPotatoesHandler.cleanup();
        cashBlasterHandler.cleanup();
        bouncePadHandler.cleanup();
        boomboxHandler.cleanup();
        respawnAnchorHandler.cleanup();
        totemOfHauntingHandler.cleanup();
        radiatingLotusHandler.cleanup();
        iceFanHandler.cleanup();
        overdriveHandler.cleanup();
        huntersMarkHandler.cleanup();
        bloomingRoseHandler.cleanup();
        orbOfGravitationHandler.cleanup();
        soulKatanaHandler.cleanup();

        healingReducedUntil.clear();
        healingReductionMultiplier.clear();
    }

    /**
     * Disable all active invisibility cloaks - used when shopping phase starts
     */
    public void disableAllInvisibilityCloaks() {
        invisCloakHandler.disableAll();
    }
}
