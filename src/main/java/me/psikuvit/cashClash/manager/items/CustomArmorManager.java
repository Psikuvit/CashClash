package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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

    private final CooldownManager cooldownManager;

    private final Map<UUID, Integer> magicHelmetEffectIndex;

    // Bunny Shoes tracking
    private final Map<UUID, Boolean> bunnyToggleReady;

    // Guardian's Vest tracking
    private final Map<UUID, Integer> guardianUsesThisRound;

    // Deathmauler tracking
    private final Map<UUID, Integer> deathmaulerExtraHearts;

    // Dragon Set tracking
    private final Set<UUID> dragonCanDoubleJump;

    private final Random random;

    private CustomArmorManager() {
        this.cooldownManager = CooldownManager.getInstance();
        this.magicHelmetEffectIndex = new ConcurrentHashMap<>();

        this.bunnyToggleReady = new ConcurrentHashMap<>();

        this.guardianUsesThisRound = new ConcurrentHashMap<>();

        this.deathmaulerExtraHearts = new ConcurrentHashMap<>();

        this.dragonCanDoubleJump = ConcurrentHashMap.newKeySet();


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

            CustomArmorItem P = PDCDetection.getCustomArmor(is);
            if (P == null) continue;
            found.add(P);
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

    public boolean hasFlamebringerSet(Player p) {
        boolean hasBoots = false, hasLegs = false;
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.FLAMEBRINGER_BOOTS) hasBoots = true;
            if (ca == CustomArmorItem.FLAMEBRINGER_LEGGINGS) hasLegs = true;
        }
        return hasBoots && hasLegs;
    }

    // ==================== MAGIC HELMET ====================
    
    // Track if magic helmet has been activated this round (reset on round start)
    private final Set<UUID> magicHelmetActivated = ConcurrentHashMap.newKeySet();

    /**
     * Magic Helmet activates on first melee damage taken:
     * Plays all three effects sequentially in order:
     * 1. Resistance I (4s)
     * 2. Absorption I (4s) 
     * 3. Speed I (4s)
     * 25 second cooldown after speed wears off
     */
    public void onMagicHelmetMeleeDamage(Player p) {
        if (!hasMagicHelmet(p)) return;

        UUID id = p.getUniqueId();
        
        // Only activate once per round (until cooldown ends)
        if (magicHelmetActivated.contains(id)) return;
        
        // Check if on cooldown
        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.MAGIC_HELMET)) {
            return;
        }

        // Mark as activated
        magicHelmetActivated.add(id);
        
        // Play effects sequentially
        // 1. Resistance I (4s)
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 4 * 20, 0, false, true, true));
        Messages.send(p, "<aqua>Magic Helmet: Resistance I activated! (4s)</aqua>");
        SoundUtils.play(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        
        // 2. Absorption I after 4 seconds
        SchedulerUtils.runTaskLater(() -> {
            if (p.isOnline() && hasMagicHelmet(p)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 4 * 20, 0, false, true, true));
                Messages.send(p, "<gold>Magic Helmet: Absorption I activated! (4s)</gold>");
                SoundUtils.play(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.4f);
            }
        }, 4 * 20L);
        
        // 3. Speed I after 8 seconds (4s resistance + 4s absorption)
        SchedulerUtils.runTaskLater(() -> {
            if (p.isOnline() && hasMagicHelmet(p)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 4 * 20, 0, false, true, true));
                Messages.send(p, "<green>Magic Helmet: Speed I activated! (4s)</green>");
                SoundUtils.play(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.6f);
                
                // Start 25 second cooldown after speed wears off (4 seconds)
                SchedulerUtils.runTaskLater(() -> {
                    if (p.isOnline()) {
                        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.MAGIC_HELMET, 25);
                        magicHelmetActivated.remove(id); // Reset activation flag after cooldown starts
                        Messages.send(p, "<gray>Magic Helmet: 25s cooldown started</gray>");
                    }
                }, 4 * 20L);
            }
        }, 8 * 20L);
    }
    
    /**
     * Reset magic helmet activation tracking for a new round.
     */
    public void resetMagicHelmetForRound(UUID playerId) {
        magicHelmetActivated.remove(playerId);
    }

    public void onPlayerAttack(Player attacker, Player target) {

        // Flamebringer Set: 30% chance to ignite enemy (requires full set: boots + leggings)
        if (hasFlamebringerSet(attacker)) {
            if (random.nextDouble() < 0.30) {
                target.setFireTicks(8 * 20);
                Messages.send(attacker, "<red>🔥 Blue flames ignited your enemy!</red>");
            }
        }

        // Dragon Set: regen and speed on hit (requires full set: helmet + chestplate + boots)
        if (hasDragonSet(attacker)) {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 3 * 20, 0));
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 3 * 20, 0));
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
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.BUNNY_SHOES)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(id, CooldownManager.Keys.BUNNY_SHOES);
            Messages.send(p, "<red>Bunny Shoes on cooldown: " + remaining + "s</red>");
            return;
        }

        int duration = cfg.getBunnyShoesDuration();
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration * 20, 0));
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.BUNNY_SHOES, cfg.getBunnyShoesCooldown());

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

        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.GUARDIAN_VEST)) return;

        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 15 * 20, 1));
        guardianUsesThisRound.put(id, used + 1);
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.GUARDIAN_VEST, 20);

        Messages.send(p, "<gold>Guardian's Vest activated! Resistance II for 15 seconds. (" + (used + 1) + "/3 uses)</gold>");
        SoundUtils.play(p, Sound.ITEM_TOTEM_USE, 0.5f, 1.5f);
    }

    // ==================== DEATHMAULER'S OUTFIT ====================

    public void onPlayerKill(Player killer) {
        if (!hasDeathmaulerSet(killer)) return;
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
        cooldownManager.setTimestamp(id, CooldownManager.Keys.DEATHMAULER_DAMAGE);

        int delaySeconds = cfg.getDeathmaulerAbsorptionDelay();
        // Schedule absorption check after configured delay without damage
        SchedulerUtils.runTaskLater(() -> {
            if (!cooldownManager.hasTimePassedSeconds(id, CooldownManager.Keys.DEATHMAULER_DAMAGE, delaySeconds)) {
                return;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60 * 20, 0));
            Messages.send(p, "<dark_red>Deathmauler granted 2 absorption hearts!</dark_red>");
        }, delaySeconds * 20L);
    }

    // ==================== DRAGON SET ====================

    public void onDragonJump(Player p) {
        if (!hasDragonSet(p)) return;
        UUID id = p.getUniqueId();

        // Enable double jump on first jump
        if (!dragonCanDoubleJump.contains(id)) {
            if (!cooldownManager.isOnCooldown(id, CooldownManager.Keys.DRAGON_DOUBLE_JUMP)) {
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

        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.DRAGON_DOUBLE_JUMP, cfg.getDragonDoubleJumpCooldown());

        Messages.send(p, "<light_purple>Dragon Double Jump!</light_purple>");
        SoundUtils.play(p, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);
        return true;
    }

    public void onPlayerLand(Player p) {
        dragonCanDoubleJump.remove(p.getUniqueId());
    }

    // ==================== TAX EVASION PANTS ====================

    public void onTaxEvasionTick(Player p, GameSession session) {
        if (!hasTaxEvasion(p)) return;
        UUID id = p.getUniqueId();

        long lastCheck = cooldownManager.getTimestamp(id, CooldownManager.Keys.TAX_EVASION_MINUTE);
        if (lastCheck == 0) {
            cooldownManager.setTimestamp(id, CooldownManager.Keys.TAX_EVASION_MINUTE);
            return;
        }

        // Living for 1 minute grants 3k
        if (cooldownManager.hasTimePassedSeconds(id, CooldownManager.Keys.TAX_EVASION_MINUTE, 60)) {
            CashClashPlayer ccp = session.getCashClashPlayer(id);
            if (ccp != null) {
                ccp.addCoins(3000);
                Messages.send(p, "<green>Tax Evasion Pants: +3,000 coins for surviving 1 minute!</green>");
                SoundUtils.play(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
            cooldownManager.setTimestamp(id, CooldownManager.Keys.TAX_EVASION_MINUTE);
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

    public void cleanup() {
        magicHelmetEffectIndex.clear();
        magicHelmetActivated.clear();

        bunnyToggleReady.clear();

        guardianUsesThisRound.clear();

        deathmaulerExtraHearts.clear();

        dragonCanDoubleJump.clear();

        // Note: cooldowns are managed by CooldownManager and will be cleared when players are cleared
    }
    
    /**
     * Reset per-round tracking for all players (called at round start).
     */
    public void resetRoundTracking() {
        magicHelmetActivated.clear();
        guardianUsesThisRound.clear();
        deathmaulerExtraHearts.clear();
        dragonCanDoubleJump.clear();
    }
}

