package me.psikuvit.cashClash.manager;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.ShopItems;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles runtime behavior for custom armor pieces (effects, cooldowns, detection helpers).
 */
public class CustomArmorManager {

    private static CustomArmorManager instance;

    // Magic Helmet tracking
    private final Map<UUID, Long> magicHelmetLastMove;
    private final Map<UUID, Long> magicHelmetCooldownUntil;
    private final Set<UUID> magicHelmetActive;
    private final Map<UUID, BukkitTask> magicHelmetDelayTask;

    // Bunny Shoes tracking
    private final Map<UUID, Long> bunnyCooldownUntil;
    private final Map<UUID, Boolean> bunnyToggleReady;

    // Guardian's Vest tracking
    private final Map<UUID, Integer> guardianUsesThisRound;
    private final Map<UUID, Long> guardianLastActivated;

    // Deathmauler tracking
    private final Map<UUID, Long> deathmaulerLastDamage;
    private final Map<UUID, Integer> deathmaulerExtraHearts;

    // Dragon Set tracking
    private final Map<UUID, Long> dragonDoubleJumpCooldown;
    private final Set<UUID> dragonCanDoubleJump;

    // Tax Evasion tracking
    private final Map<UUID, Long> taxEvasionLastMinuteCheck;

    private final Random random;

    private CustomArmorManager() {
        this.magicHelmetLastMove = new ConcurrentHashMap<>();
        this.magicHelmetCooldownUntil = new ConcurrentHashMap<>();
        this.magicHelmetActive = ConcurrentHashMap.newKeySet();
        this.magicHelmetDelayTask = new ConcurrentHashMap<>();

        this.bunnyCooldownUntil = new ConcurrentHashMap<>();
        this.bunnyToggleReady = new ConcurrentHashMap<>();

        this.guardianUsesThisRound = new ConcurrentHashMap<>();
        this.guardianLastActivated = new ConcurrentHashMap<>();

        this.deathmaulerLastDamage = new ConcurrentHashMap<>();
        this.deathmaulerExtraHearts = new ConcurrentHashMap<>();

        this.dragonDoubleJumpCooldown = new ConcurrentHashMap<>();
        this.dragonCanDoubleJump = ConcurrentHashMap.newKeySet();

        this.taxEvasionLastMinuteCheck = new ConcurrentHashMap<>();

        this.random = new Random();
    }

    public static CustomArmorManager getInstance() {
        if (instance == null) {
            instance = new CustomArmorManager();
        }
        return instance;
    }

    private List<CustomArmorItem> getEquippedCustomArmor(Player p) {
        List<CustomArmorItem> found = new ArrayList<>();
        for (ItemStack is : p.getInventory().getArmorContents()) {
            if (is == null) continue;
            ItemMeta m = is.getItemMeta();
            if (m == null) continue;

            PersistentDataContainer c = m.getPersistentDataContainer();
            String val = c.get(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING);
            if (val == null) continue;

            found.add(ShopItems.getCustomArmor(val));
        }
        return found;
    }

    public int countInvestorsPieces(Player p) {
        int cnt = 0;
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca.isInvestorsSet()) cnt++;
        }
        return cnt;
    }

    public boolean hasTaxEvasion(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.TAX_EVASION_PANTS) return true;
        }
        return false;
    }

    public boolean hasMagicHelmet(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.MAGIC_HELMET) return true;
        }
        return false;
    }

    public boolean hasDeathmaulerSet(Player p) {
        boolean hasChest = false, hasLegs = false;
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.DEATHMAULER_CHESTPLATE) hasChest = true;
            if (ca == CustomArmorItem.DEATHMAULER_LEGGINGS) hasLegs = true;
        }
        return hasChest && hasLegs;
    }

    public boolean hasDragonSet(Player p) {
        boolean hasChest = false, hasBoots = false, hasHelmet = false;
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.DRAGON_CHESTPLATE) hasChest = true;
            if (ca == CustomArmorItem.DRAGON_BOOTS) hasBoots = true;
            if (ca == CustomArmorItem.DRAGON_HELMET) hasHelmet = true;
        }
        return hasChest && hasBoots && hasHelmet;
    }

    public boolean hasBunnyShoes(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.BUNNY_SHOES) return true;
        }
        return false;
    }

    public boolean hasGuardianVest(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.GUARDIANS_VEST) return true;
        }
        return false;
    }

    public boolean hasFlamebringerLeggings(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.FLAMEBRINGER_LEGGINGS) return true;
        }
        return false;
    }

    // ==================== MAGIC HELMET ====================

    public void onPlayerMove(Player p) {
        if (hasMagicHelmet(p)) return;

        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (magicHelmetActive.contains(id)) {
            cancelMagicHelmetInvisibility(p);
            return;
        }

        long cd = magicHelmetCooldownUntil.getOrDefault(id, 0L);
        if (now < cd) {
            return;
        }

        BukkitTask existingTask = magicHelmetDelayTask.remove(id);
        if (existingTask != null) {
            existingTask.cancel();
        }

        magicHelmetLastMove.put(id, now);

        int delaySeconds = cfg.getMagicHelmetStandDelay();
        BukkitTask task = SchedulerUtils.runTaskLater(() -> {
            magicHelmetDelayTask.remove(id);

            if (hasMagicHelmet(p)) return;
            if (magicHelmetActive.contains(id)) return;

            long currentCd = magicHelmetCooldownUntil.getOrDefault(id, 0L);
            if (System.currentTimeMillis() < currentCd) return;

            Long lastMove = magicHelmetLastMove.get(id);
            long delayMillis = (delaySeconds * 1000L) - 100L;
            if (lastMove != null && System.currentTimeMillis() - lastMove >= delayMillis) {
                activateMagicHelmetInvisibility(p);
            }
        }, delaySeconds * 20L);

        magicHelmetDelayTask.put(id, task);
    }

    private void activateMagicHelmetInvisibility(Player p) {
        UUID id = p.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();
        int duration = cfg.getMagicHelmetInvisDuration();

        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration * 20, 0, false, false, false));
        magicHelmetActive.add(id);

        Messages.send(p, "<dark_purple>You turned invisible!</dark_purple>");

        SchedulerUtils.runTaskLater(() -> {
            if (magicHelmetActive.contains(id)) {
                cancelMagicHelmetInvisibility(p);
            }
        }, duration * 20L);
    }

    public void cancelMagicHelmetInvisibility(Player p) {
        UUID id = p.getUniqueId();
        if (!magicHelmetActive.remove(id)) return;

        ItemsConfig cfg = ItemsConfig.getInstance();
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        magicHelmetCooldownUntil.put(id, System.currentTimeMillis() + (cfg.getMagicHelmetCooldown() * 1000L));
        Messages.send(p, "<gray>Invisibility ended. Cooldown: " + cfg.getMagicHelmetCooldown() + " seconds.</gray>");
    }

    public void onMagicHelmetRightClick(Player p) {
        if (hasMagicHelmet(p)) return;
        if (magicHelmetActive.contains(p.getUniqueId())) {
            cancelMagicHelmetInvisibility(p);
        }
    }

    public void onPlayerAttack(Player attacker, Player target) {
        // Magic Helmet: attacking cancels invisibility
        if (magicHelmetActive.contains(attacker.getUniqueId())) {
            cancelMagicHelmetInvisibility(attacker);
        }

        // Flamebringer Leggings: 30% chance to ignite
        if (hasFlamebringerLeggings(attacker)) {
            if (random.nextDouble() < 0.30) {
                target.setFireTicks(8 * 20);
                Messages.send(attacker, "<red>🔥 Blue flames ignited your enemy!</red>");
            }
        }

        // Dragon Set: regen and speed on hit
        if (hasDragonSet(attacker)) {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 3 * 20, 0));
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 3 * 20, 1));
        }
    }

    // ==================== BUNNY SHOES ====================

    public void onPlayerToggleSneak(Player p, boolean sneaking) {
        if (!hasBunnyShoes(p)) return;
        UUID id = p.getUniqueId();

        if (sneaking) {
            bunnyToggleReady.put(id, true);
        } else {
            Boolean ready = bunnyToggleReady.get(id);
            if (ready != null && ready) {
                bunnyToggleReady.put(id, false);
                tryActivateBunnyShoes(p);
            }
        }
    }

    private void tryActivateBunnyShoes(Player p) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long cd = bunnyCooldownUntil.getOrDefault(id, 0L);
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (now < cd) {
            long remaining = (cd - now) / 1000;
            Messages.send(p, "<red>Bunny Shoes on cooldown: " + remaining + "s</red>");
            return;
        }

        int duration = cfg.getBunnyShoesDuration();
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration * 20, 0));
        bunnyCooldownUntil.put(id, now + (cfg.getBunnyShoesCooldown() * 1000L));

        Messages.send(p, "<green>Bunny Shoes activated! Speed II & Jump Boost for " + duration + " seconds.</green>");
        SoundUtils.play(p, Sound.ENTITY_RABBIT_JUMP, 1.0f, 1.5f);
    }

    // ==================== GUARDIAN'S VEST ====================

    public void onPlayerDamaged(Player p, double healthAfter) {
        if (!hasGuardianVest(p)) return;
        UUID id = p.getUniqueId();

        if (healthAfter > 8.0) return; // 4 hearts = 8 HP

        int used = guardianUsesThisRound.getOrDefault(id, 0);
        if (used >= 3) return;

        long now = System.currentTimeMillis();
        long last = guardianLastActivated.getOrDefault(id, 0L);
        if (now < last + 20_000L) return;

        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 15 * 20, 1));
        guardianUsesThisRound.put(id, used + 1);
        guardianLastActivated.put(id, now);

        Messages.send(p, "<gold>Guardian's Vest activated! Resistance II for 15 seconds. (" + (used + 1) + "/3 uses)</gold>");
        SoundUtils.play(p, Sound.ITEM_TOTEM_USE, 0.5f, 1.5f);
    }

    // ==================== DEATHMAULER'S OUTFIT ====================

    public void onPlayerKill(Player killer) {
        if (hasDeathmaulerSet(killer)) return;
        UUID id = killer.getUniqueId();

        // Heal 4 hearts (8 HP)
        var attr = killer.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            double maxHealth = attr.getValue();
            double newHealth = Math.min(maxHealth, killer.getHealth() + 8.0);
            killer.setHealth(newHealth);
        }

        Messages.send(killer, "<dark_red>Deathmauler healed you +4 hearts!</dark_red>");

        // 30% chance for extra heart this round
        if (random.nextDouble() < 0.30) {
            if (attr != null) {
                int extraHearts = deathmaulerExtraHearts.getOrDefault(id, 0);
                attr.setBaseValue(attr.getBaseValue() + 2.0);
                deathmaulerExtraHearts.put(id, extraHearts + 1);
                Messages.send(killer, "<dark_red>+1 extra heart for this round!</dark_red>");
            }
        }
    }

    public void onDeathmaulerDamageTaken(Player p) {
        if (!hasDeathmaulerSet(p)) return;
        UUID id = p.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();
        deathmaulerLastDamage.put(id, System.currentTimeMillis());

        int delaySeconds = cfg.getDeathmaulerAbsorptionDelay();
        // Schedule absorption check after configured delay without damage
        SchedulerUtils.runTaskLater(() -> {
            Long last = deathmaulerLastDamage.get(id);
            if (last == null) return;
            if (System.currentTimeMillis() - last >= (delaySeconds * 1000L)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60 * 20, 0));
                Messages.send(p, "<dark_red>Deathmauler granted 2 absorption hearts!</dark_red>");
            }
        }, delaySeconds * 20L);
    }

    // ==================== DRAGON SET ====================

    public void onDragonJump(Player p) {
        if (!hasDragonSet(p)) return;
        UUID id = p.getUniqueId();

        // Enable double jump on first jump
        if (!dragonCanDoubleJump.contains(id)) {
            Long cd = dragonDoubleJumpCooldown.getOrDefault(id, 0L);
            if (System.currentTimeMillis() >= cd) {
                dragonCanDoubleJump.add(id);
            }
        }
    }

    public boolean tryDragonDoubleJump(Player p) {
        if (!hasDragonSet(p)) return false;
        UUID id = p.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (!dragonCanDoubleJump.remove(id)) return false;

        // Launch player ~5 blocks forward, 3 blocks up
        Vector direction = p.getLocation().getDirection().normalize();
        Vector velocity = direction.multiply(1.2).setY(0.8);
        p.setVelocity(velocity);

        dragonDoubleJumpCooldown.put(id, System.currentTimeMillis() + (cfg.getDragonDoubleJumpCooldown() * 1000L));

        Messages.send(p, "<light_purple>Dragon Double Jump!</light_purple>");
        SoundUtils.play(p, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);
        return true;
    }

    public void onPlayerLand(Player p) {
        dragonCanDoubleJump.remove(p.getUniqueId());
    }

    public boolean isDragonSetImmuneToExplosion(Player p) {
        return hasDragonSet(p);
    }

    // ==================== TAX EVASION PANTS ====================

    public void onTaxEvasionTick(Player p, GameSession session) {
        if (!hasTaxEvasion(p)) return;
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        Long lastCheck = taxEvasionLastMinuteCheck.get(id);
        if (lastCheck == null) {
            taxEvasionLastMinuteCheck.put(id, now);
            return;
        }

        // Living for 1 minute grants 3k
        if (now - lastCheck >= 60_000L) {
            CashClashPlayer ccp = session.getCashClashPlayer(id);
            if (ccp != null) {
                ccp.addCoins(3000);
                Messages.send(p, "<green>Tax Evasion Pants: +3,000 coins for surviving 1 minute!</green>");
                SoundUtils.play(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
            taxEvasionLastMinuteCheck.put(id, now);
        }
    }

    public double getTaxEvasionDeathPenalty() {
        return 0.075; // Only lose 7.5% on death
    }

    // ==================== INVESTOR'S SET ====================

    public double getInvestorMultiplier(Player p) {
        int pieces = countInvestorsPieces(p);
        if (pieces <= 0) return 1.0;
        return 1.0 + (0.125 * pieces); // +12.5% per piece
    }

    public double getInvestorMeleeDamageMultiplier(Player p, int currentRound) {
        if (currentRound < 4) return 1.0;
        int pieces = countInvestorsPieces(p);
        if (pieces <= 0) return 1.0;
        return 1.0 + (0.05 * pieces); // +5% per piece in rounds 4/5
    }

    /**
     * Calculate the price for an investor piece based on how many are already owned.
     */
    public long getInvestorPrice(CustomArmorItem armor, int piecesOwned) {
        if (!armor.isInvestorsSet()) return armor.getBasePrice();
        // Each piece increases price by 25%
        double multiplier = Math.pow(1.25, piecesOwned);
        return Math.round(armor.getBasePrice() * multiplier);
    }

    // ==================== RESET ====================

    public void resetRound(Player p) {
        UUID id = p.getUniqueId();
        guardianUsesThisRound.remove(id);
        guardianLastActivated.remove(id);

        // Reset deathmauler extra hearts
        Integer extraHearts = deathmaulerExtraHearts.remove(id);
        if (extraHearts != null && extraHearts > 0) {
            var attr = p.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(20.0);
            }
        }

        deathmaulerLastDamage.remove(id);
        taxEvasionLastMinuteCheck.remove(id);
        dragonCanDoubleJump.remove(id);
        dragonDoubleJumpCooldown.remove(id);

        // Cancel any pending magic helmet task
        BukkitTask task = magicHelmetDelayTask.remove(id);
        if (task != null) {
            task.cancel();
        }
        magicHelmetActive.remove(id);
        magicHelmetCooldownUntil.remove(id);

        bunnyToggleReady.remove(id);
        bunnyCooldownUntil.remove(id);
    }

    public void resetPlayer(Player p) {
        resetRound(p);
        magicHelmetLastMove.remove(p.getUniqueId());
    }
}
