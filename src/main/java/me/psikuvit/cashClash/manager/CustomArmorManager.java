package me.psikuvit.cashClash.manager;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.items.CustomArmor;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles runtime behavior for custom armor pieces (effects, cooldowns, detection helpers).
 */
public class CustomArmorManager {
    
    private static CustomArmorManager instance;
    // cooldowns and state maps
    private final Map<UUID, Long> gillieLastMove;
    private final Map<UUID, Long> gillieInvisUntil;
    private final Map<UUID, Long> gillieCooldownUntil;
    private final Map<UUID, Long> lightfootCooldownUntil;
    private final Map<UUID, Integer> guardianUsesThisRound;
    private final Map<UUID, Long> guardianLastActivated;
    private final Map<UUID, Long> deathmaulerLastDamage;

    private final Random random;

    private CustomArmorManager() {
        this.gillieLastMove = new ConcurrentHashMap<>();
        this.gillieInvisUntil = new ConcurrentHashMap<>();
        this.gillieCooldownUntil = new ConcurrentHashMap<>();
        this.lightfootCooldownUntil = new ConcurrentHashMap<>();
        this.guardianUsesThisRound = new ConcurrentHashMap<>();
        this.guardianLastActivated = new ConcurrentHashMap<>();
        this.deathmaulerLastDamage = new ConcurrentHashMap<>();

        this.random = new Random();
    }

    public static CustomArmorManager getInstance() { 
        if (instance == null) {
            instance = new CustomArmorManager();
        }
        return instance;
    }

    private List<CustomArmor> getEquippedCustomArmor(Player p) {
        List<CustomArmor> found = new ArrayList<>();
        for (ItemStack is : p.getInventory().getArmorContents()) {
            if (is == null) continue;

            ItemMeta m = is.getItemMeta();
            if (m == null) continue;

            PersistentDataContainer c = m.getPersistentDataContainer();
            String val = c.get(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING);

            if (val == null) continue;
            try {
                CustomArmor ca = CustomArmor.valueOf(val);
                found.add(ca);
            } catch (IllegalArgumentException ignored) {}
        }
        return found;
    }

    public int countEquipped(Player p, CustomArmor typePrefix) {
        // counts exact match or set pieces with same prefix
        int cnt = 0;
        for (CustomArmor ca : getEquippedCustomArmor(p)) {
            if (ca == typePrefix) cnt++;
            else if (typePrefix == CustomArmor.INVESTORS_BOOTS && ca.name().startsWith("INVESTORS_")) cnt++;
        }
        return cnt;
    }

    public int countInvestorsPieces(Player p) {
        int cnt = 0;
        for (CustomArmor ca : getEquippedCustomArmor(p)) if (ca.isInvestorsSet()) cnt++;
        return cnt;
    }

    public boolean hasTaxEvasion(Player p) {
        for (CustomArmor ca : getEquippedCustomArmor(p)) if (ca == CustomArmor.TAX_EVASION_PANTS) return true;
        return false;
    }

    public boolean hasGillie(Player p) {
        for (CustomArmor ca : getEquippedCustomArmor(p)) if (ca == CustomArmor.GILLIE_SUIT_HAT) return true;
        return false;
    }

    public boolean hasLightfoot(Player p) {
        for (CustomArmor ca : getEquippedCustomArmor(p)) if (ca == CustomArmor.LIGHTFOOT_SHOES) return true;
        return false;
    }

    public boolean hasGuardianVest(Player p) {
        for (CustomArmor ca : getEquippedCustomArmor(p)) if (ca == CustomArmor.GUARDIANS_VEST) return true;
        return false;
    }

    public boolean hasFlamebringerBoots(Player p) {
        for (CustomArmor ca : getEquippedCustomArmor(p)) if (ca == CustomArmor.FLAMEBRINGER_BOOTS) return true;
        return false;
    }

    public boolean hasFlamebringerLeggings(Player p) {
        for (CustomArmor ca : getEquippedCustomArmor(p)) if (ca == CustomArmor.FLAMEBRINGER_LEGGINGS) return true;
        return false;
    }

    public boolean hasDeathmauler(Player p) {
        int count = 0;
        for (CustomArmor ca : getEquippedCustomArmor(p)) if (ca == CustomArmor.DEATHMAULER_OUTFIT) count++;
        return count >= 2;
    }

    public boolean hasDragonSet(Player p) {
        int count = 0;
        for (CustomArmor ca : getEquippedCustomArmor(p)) if (ca == CustomArmor.DRAGON_SET) count++;
        return count >= 3;
    }

    public void onPlayerMove(Player p) {
        if (!hasGillie(p)) return;

        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        gillieLastMove.put(id, now);

        SchedulerUtils.runTaskLater(() -> {
             Long l = gillieLastMove.get(id);
             if (l == null) return;
             if (l.equals(now)) {
                 Long cd = gillieCooldownUntil.getOrDefault(id, 0L);
                 if (now < cd) return;

                 Long invisUntil = gillieInvisUntil.getOrDefault(id, 0L);
                 if (now < invisUntil) return; // already invisible

                 p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 10 * 20, 0, false, false, false));

                 gillieInvisUntil.put(id, System.currentTimeMillis() + (10 * 1000L));
                 gillieCooldownUntil.put(id, System.currentTimeMillis() + (30 * 1000L));
             }
        }, 60L);
    }

    public boolean tryActivateLightfoot(Player p) {
        if (!hasLightfoot(p)) return false;
        UUID id = p.getUniqueId();

        long now = System.currentTimeMillis();
        Long cd = lightfootCooldownUntil.getOrDefault(id, 0L);

        if (now < cd) return false;

        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 15 * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 15 * 20, 0));

        lightfootCooldownUntil.put(id, now + (25 * 1000L));
        return true;
    }

    // Guardian: called on damage to check threshold
    public void onPlayerDamaged(Player p) {
        if (!hasGuardianVest(p)) return;
        UUID id = p.getUniqueId();
        double health = p.getHealth();

        if (health > 8.0) return;

        int used = guardianUsesThisRound.getOrDefault(id, 0);
        if (used >= 3) return;

        long now = System.currentTimeMillis();
        long last = guardianLastActivated.getOrDefault(id, 0L);

        if (now < last + 20 * 1000L) return;
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 15 * 20, 1));
        guardianUsesThisRound.put(id, used + 1);
        guardianLastActivated.put(id, now);
    }

    // Flamebringer: on attack
    public void onPlayerAttack(Player attacker, Player target) {
        if (hasFlamebringerLeggings(attacker)) {
            if (random.nextDouble() < 0.40) {
                // attempt to ignite target - give fire ticks and a small immediate damage to bypass res
                target.setFireTicks(8 * 20);
                target.damage(1.0, attacker);
            }
        }
    }

    // Deathmauler: on kill
    public void onPlayerKill(Player killer) {
        if (!hasDeathmauler(killer)) return;
        double max = killer.getAttribute(Attribute.MAX_HEALTH).getValue();
        double cur = killer.getHealth();

        double newHealth = Math.min(max, cur + 8.0); // +4 hearts = 8.0
        killer.setHealth(newHealth);

        if (random.nextDouble() < 0.30) {
            int ticks = CashClashPlugin.getInstance().getConfig().getInt("rounds.round-duration-seconds", 60) * 20;
            killer.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ticks, 0));
        }
    }

    // Deathmauler: schedule absorption when player hasn't taken damage for 5s
    public void onPlayerTookDamageForDeathmauler(Player p) {
        if (!hasDeathmauler(p)) return;
        UUID id = p.getUniqueId();
        deathmaulerLastDamage.put(id, System.currentTimeMillis());

        me.psikuvit.cashClash.util.SchedulerUtils.runTaskLater(() -> {
             Long last = deathmaulerLastDamage.get(id);
             if (last == null) return;
             if (System.currentTimeMillis() - last >= 5000L) {
                 p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60 * 20, 0));
             }
        }, 100L); // 5s = 100 ticks
    }

    // Investor: get multiplier to apply for money bonuses (kills/objectives)
    public double getInvestorMultiplier(Player p) {
        int pieces = countInvestorsPieces(p);
        if (pieces <= 0) return 1.0;
        return 1.0 + (0.125 * pieces); // each piece +12.5%
    }

    // Dragon set: when player hits someone, give regen1 and speed2 for 3s
    public void onDragonHit(Player p) {
        if (!hasDragonSet(p)) return;
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 3 * 20, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 3 * 20, 1));
    }

    // Utility: reset per-round counters
    public void resetRound(Player p) {
        UUID id = p.getUniqueId();
        guardianUsesThisRound.remove(id);
        guardianLastActivated.remove(id);
    }
}
