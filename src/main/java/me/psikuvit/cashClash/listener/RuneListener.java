package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.RuneManager;
import me.psikuvit.cashClash.manager.items.armor.CustomArmorManager;
import me.psikuvit.cashClash.manager.items.custom.CustomItemManager;
import me.psikuvit.cashClash.manager.items.custom.OverdriveHandler;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class RuneListener implements Listener {

    private final CustomArmorManager armorManager = CustomArmorManager.getInstance();
    private final CustomItemManager customItemManager = CustomItemManager.getInstance();

    @EventHandler
    public void onRuneToggle(PlayerInteractEvent event) {
        // Only use main hand interaction
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();

        // Sneak + right click toggles the rune
        if (action != Action.RIGHT_CLICK_AIR &&
                action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();

        // Must be sneaking
        if (!player.isSneaking()) return;

        ItemStack rune = player.getInventory().getItemInMainHand();

        // Not a rune
        if (!RuneManager.isRune(rune)) return;

        // Prevent normal item behavior
        event.setCancelled(true);

        // Overdrive Potion: no inventory items can be used while invincible (this listener
        // runs independently of InteractListener's own overdrive block, so it needs its own)
        if (customItemManager.getHandler(OverdriveHandler.class).isOverdriveInvincible(player.getUniqueId())) {
            return;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null && session.getState() == GameState.SHOPPING) {
            return;
        }

        // Block a same-tick Bunny Shoes activation from the sneak this toggle rides on
        armorManager.lockMythicShift(player);
        RuneManager.toggleRune(player, rune);
    }

    @EventHandler
    public void onRuneClick(PlayerInteractEvent event) {

        // Only use main hand interaction
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();

        // Only left click
        if (action != Action.LEFT_CLICK_AIR &&
                action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();

        // Must be sneaking
        if (!player.isSneaking()) return;
        ItemStack item = player.getInventory().getItemInMainHand();

        // Not a rune
        if (!RuneManager.isRune(item)) return;
        event.setCancelled(true);
    }

    /**
     * Dragging a rune onto another item in an inventory click links the two (or unlinks if
     * already linked to that exact item). Armor runes (Protection/Projectile Protection) refuse
     * manual linking - they auto-apply to the whole worn set instead, see RuneManager.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRuneLinkClick(InventoryClickEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getWhoClicked() instanceof Player p)) return;

        ItemStack cursor = event.getCursor();
        if (!RuneManager.isRune(cursor)) return;

        if (RuneManager.isRuneBroken(cursor)) {
            event.setCancelled(true);
            Messages.send(p, "rune.broken-cannot-use");
            SoundUtils.play(p, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(p);
        if (session != null && session.getState() == GameState.SHOPPING) {
            event.setCancelled(true);
            return;
        }

        if (RuneManager.isRuneActive(cursor)) {
            event.setCancelled(true);
            Messages.send(p, "rune.active-cannot-switch");
            SoundUtils.play(p, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (CooldownManager.getInstance().isOnCooldown(p.getUniqueId(), CooldownManager.Keys.RUNE_LINK)) {
            event.setCancelled(true);
            return;
        }

        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType().isAir()) return;

        EnchantEntry enchant = PDCDetection.getRune(cursor);
        if (enchant == null) return;

        boolean isArmor = target.getType().name().endsWith("_HELMET")
                || target.getType().name().endsWith("_CHESTPLATE")
                || target.getType().name().endsWith("_LEGGINGS")
                || target.getType().name().endsWith("_BOOTS");
        if (isArmor && (enchant == EnchantEntry.PROTECTION || enchant == EnchantEntry.PROJECTILE_PROTECTION)) {
            Messages.send(p, "rune.auto-linked");
            SoundUtils.play(p, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (!enchant.canApplyTo(target)) {
            event.setCancelled(true);
            Messages.send(p, "rune.ineligible-target", "enchant", enchant.getDisplayName());
            SoundUtils.play(p, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ItemStack linked = RuneManager.getLinkedItem(p, cursor);
        if (linked != null && linked.isSimilar(target)) {
            RuneManager.clearRuneLink(cursor);
            CooldownManager.getInstance().setCooldownSeconds(p.getUniqueId(), CooldownManager.Keys.RUNE_LINK, 1);
            event.setCancelled(true);
            p.setItemOnCursor(null);
            p.getInventory().addItem(cursor);
            SoundUtils.play(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.85f);
            Messages.send(p, "rune.unlinked");
            return;
        }

        RuneManager.setRuneLink(cursor, target);
        CooldownManager.getInstance().setCooldownSeconds(p.getUniqueId(), CooldownManager.Keys.RUNE_LINK, 1);
        event.setCancelled(true);
        p.setItemOnCursor(null);
        p.getInventory().addItem(cursor);
        SoundUtils.play(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        Messages.send(p, "rune.linked", "target", target.getType().name().toLowerCase().replace("_", " "));
    }

    public RuneListener() {
        RuneManager.startRuneRechargeTask();
    }

    @EventHandler
    public void onWeaponRuneUse(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir()) return;
        // Sharpness
        ItemStack sharpnessRune = RuneManager.getActiveRune(
                player,
                EnchantEntry.SHARPNESS
        );

        if (sharpnessRune != null &&
                EnchantEntry.SHARPNESS.canApplyTo(weapon)) {
            RuneManager.consumeRuneDurability(player, sharpnessRune);
        }
        // Fire Aspect
        ItemStack fireRune = RuneManager.getActiveRune(
                player,
                EnchantEntry.FIRE_ASPECT
        );

        if (fireRune != null &&
                EnchantEntry.FIRE_ASPECT.canApplyTo(weapon)) {
            RuneManager.consumeRuneDurability(player, fireRune);
        }

        // Knockback
        ItemStack knockbackRune = RuneManager.getActiveRune(
                player,
                EnchantEntry.KNOCKBACK
        );

        if (knockbackRune != null &&
                EnchantEntry.KNOCKBACK.canApplyTo(weapon)) {
            RuneManager.consumeRuneDurability(player, knockbackRune);
        }
    }

    @EventHandler
    public void onBowRuneUse(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        if (bow == null || bow.getType().isAir()) return;

        // Power
        ItemStack powerRune = RuneManager.getActiveRune(
                player,
                EnchantEntry.POWER
        );

        if (powerRune != null &&
                EnchantEntry.POWER.canApplyTo(bow)) {
            RuneManager.consumeRuneDurability(player, powerRune);
        }

        // Flame
        ItemStack flameRune = RuneManager.getActiveRune(
                player,
                EnchantEntry.FLAME
        );

        if (flameRune != null &&
                EnchantEntry.FLAME.canApplyTo(bow)) {
            RuneManager.consumeRuneDurability(player, flameRune);
        }

        // Punch
        ItemStack punchRune = RuneManager.getActiveRune(
                player,
                EnchantEntry.PUNCH
        );

        if (punchRune != null &&
                EnchantEntry.PUNCH.canApplyTo(bow)) {
            RuneManager.consumeRuneDurability(player, punchRune);
        }

        ItemStack crossbow = player.getInventory().getItemInMainHand();
        if (crossbow == null || crossbow.getType().isAir()) return;

        // Piercing
        ItemStack piercingRune = RuneManager.getActiveRune(
                player,
                EnchantEntry.PIERCING
        );

        if (piercingRune != null &&
                EnchantEntry.PIERCING.canApplyTo(crossbow)) {
            RuneManager.consumeRuneDurability(player, piercingRune);
        }


        // Quick Charge
        ItemStack quickChargeRune = RuneManager.getActiveRune(
                player,
                EnchantEntry.QUICK_CHARGE
        );

        if (quickChargeRune != null &&
                EnchantEntry.QUICK_CHARGE.canApplyTo(crossbow)) {
            RuneManager.consumeRuneDurability(player, quickChargeRune);
        }
    }

    @EventHandler
    public void onArmorRuneUse(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // Protection
        ItemStack protectionRune = RuneManager.getActiveRune(
                player,
                EnchantEntry.PROTECTION
        );

        if (protectionRune != null) {
            RuneManager.consumeRuneDurability(player, protectionRune);
        }

        // Projectile Protection
        if (event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
            ItemStack projectileRune = RuneManager.getActiveRune(
                    player,
                    EnchantEntry.PROJECTILE_PROTECTION
            );
            if (projectileRune != null) {
                RuneManager.consumeRuneDurability(player, projectileRune);
            }
        }
    }

    @EventHandler
    public void onRuneVanillaUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        if (item == null) return;

        if (PDCDetection.getRune(item) == null) return;
        event.setCancelled(true);
    }
}
