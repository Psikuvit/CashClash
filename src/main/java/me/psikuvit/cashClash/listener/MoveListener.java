package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.armor.CustomArmorManager;
import me.psikuvit.cashClash.manager.items.armor.DragonSetHandler;
import me.psikuvit.cashClash.manager.items.armor.TectonicCapHandler;
import me.psikuvit.cashClash.manager.items.custom.BouncePadHandler;
import me.psikuvit.cashClash.manager.items.custom.CustomItemManager;
import me.psikuvit.cashClash.manager.items.weapon.SoulKatanaHandler;
import me.psikuvit.cashClash.manager.items.weapon.WeaponItemManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Consolidated listener for all PlayerMoveEvent handling.
 * Handles: bounce pads, custom armor effects (landing, dragon jump).
 */
public class MoveListener implements Listener {

    private final GameManager gameManager;
    private final CustomItemManager customItemManager;
    private final CustomArmorManager armorManager;
    private final WeaponItemManager weaponItemManager;
    private final Map<UUID, Float> fallDistances = new HashMap<>();

    public MoveListener(GameManager gameManager, CustomItemManager customItemManager, CustomArmorManager armorManager,
                       WeaponItemManager weaponItemManager) {
        this.gameManager = gameManager;
        this.customItemManager = customItemManager;
        this.armorManager = armorManager;
        this.weaponItemManager = weaponItemManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        GameSession session = gameManager.getPlayerSession(player);
        if (session == null) {
            weaponItemManager.getHandler(SoulKatanaHandler.class).handleSoulKatanaLand(player);
            armorManager.getHandler(TectonicCapHandler.class).handleFallWarning(player);
            checkLandingOutsideGame(player);
            checkBouncePads(player);
            return;
        }

        // A Sequence is locking players (title/reveal moments) - cancel positional
        // movement but let players keep looking around.
        if (session.isSequenceLocked()) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(new Location(to.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
            }
            return;
        }

        // Skip if in shopping phase
        if (session.getState() == GameState.SHOPPING || session.isActionsRestricted()) return;

        // Soul Katana Phantom Slice: the dash strike resolves once the player leaves the ground
        // and touches back down
        weaponItemManager.getHandler(SoulKatanaHandler.class).handleSoulKatanaLand(player);

        // Tectonic Cap: falling warning aura when a slam charge is ready
        armorManager.getHandler(TectonicCapHandler.class).handleFallWarning(player);

        checkBouncePads(player);
    }

    /**
     * In a game Tectonic Cap's slam and Dragon Outrage's landing hang off the fall-damage event.
     * Outside one there's no fall damage to hang them off - the practice range has none, and hubs
     * often turn it off - so a landing is handed to them as the fall damage vanilla would have
     * dealt.
     */
    private void checkLandingOutsideGame(Player player) {
        UUID id = player.getUniqueId();
        if (player.isInWater() || player.isFlying() || player.isGliding()) {
            fallDistances.remove(id);
            return;
        }
        if (!player.isOnGround()) {
            fallDistances.put(id, player.getFallDistance());
            return;
        }

        Float fell = fallDistances.remove(id);
        AttributeInstance safeFall = player.getAttribute(Attribute.SAFE_FALL_DISTANCE);
        double damage = fell == null ? 0 : fell - (safeFall != null ? safeFall.getValue() : 3.0);
        if (damage <= 0) return;

        EntityDamageEvent landing = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL,
                DamageSource.builder(DamageType.FALL).build(), damage);
        armorManager.getHandler(DragonSetHandler.class).onDragonOutrageLanding(landing, player);
        armorManager.getHandler(TectonicCapHandler.class).onTectonicCapFall(landing, player);
    }

    /**
     * Launches a player standing on a floor bounce pad or touching a wall-mounted one.
     */
    private void checkBouncePads(Player player) {
        Block blockBelow = player.getLocation().subtract(0, 0.1, 0).getBlock();
        if (blockBelow.getType() == Material.SLIME_BLOCK) {
            if (customItemManager.getHandler(BouncePadHandler.class).isBouncePad(blockBelow)) {
                customItemManager.getHandler(BouncePadHandler.class).handleBouncePad(player, blockBelow);
            }
        }

        // Check the four horizontal neighbors for wall-mounted bounce pads
        Location loc = player.getLocation();
        Block currentBlock = loc.getBlock();
        double fracX = loc.getX() - currentBlock.getX();
        double fracZ = loc.getZ() - currentBlock.getZ();

        double touchThreshold = CashClashPlugin.getInstance().getItemsConfig().getBouncePadWallTouchThreshold();
        checkWallBouncePad(player, currentBlock, BlockFace.WEST, fracX < touchThreshold);
        checkWallBouncePad(player, currentBlock, BlockFace.EAST, fracX > 1 - touchThreshold);
        checkWallBouncePad(player, currentBlock, BlockFace.NORTH, fracZ < touchThreshold);
        checkWallBouncePad(player, currentBlock, BlockFace.SOUTH, fracZ > 1 - touchThreshold);
    }

    private void checkWallBouncePad(Player player, Block currentBlock, BlockFace face, boolean nearFace) {
        if (!nearFace) return;
        Block neighbor = currentBlock.getRelative(face);
        if (customItemManager.getHandler(BouncePadHandler.class).isWallMountedBouncePad(neighbor)) {
            customItemManager.getHandler(BouncePadHandler.class).handleBouncePad(player, neighbor);
        }
    }
}

