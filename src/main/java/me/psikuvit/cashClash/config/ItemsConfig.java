package me.psikuvit.cashClash.config;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Manages items.yml configuration for all item-related constants.
 */
public class ItemsConfig {

    private static ItemsConfig instance;
    private FileConfiguration config;
    private final ConfigValidator validator;

    private ItemsConfig() {
        this.validator = new ConfigValidator();
        loadConfig();
    }

    public static ItemsConfig getInstance() {
        if (instance == null) {
            instance = new ItemsConfig();
        }
        return instance;
    }

    private void loadConfig() {
        File configFile = new File(CashClashPlugin.getInstance().getDataFolder(), "items.yml");

        if (!configFile.exists()) {
            CashClashPlugin.getInstance().saveResource("items.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Validate and auto-add missing fields
        if (!validator.validateItemsConfig(config, true)) {
            Messages.debug("CONFIG", "Items configuration has errors - check warnings above");
        }

        // Save if any fields were added
        if (validator.getAddedCount() > 0) {
            try {
                config.save(configFile);
                Messages.debug("CONFIG", "Saved items.yml with " + validator.getAddedCount() + " new default values");
            } catch (Exception e) {
                Messages.debug("CONFIG", "Failed to save items.yml: " + e.getMessage());
            }
        }
    }

    public void reload() {
        loadConfig();
        validator.logConfigDiff("items.yml", 0); // TODO: track actual changes
    }

    // ==================== MYTHIC ITEMS ====================

    public int getLegendsPerGame() {
        return config.getInt("mythic-items.legendaries-per-game", 5);
    }

    // Coin Cleaver
    public double getCoinCleaverDamageBonus() {
        return config.getDouble("mythic-items.coin-cleaver.damage-bonus-multiplier", 1.25);
    }

    public int getCoinCleaverGrenadeCooldown() {
        return config.getInt("mythic-items.coin-cleaver.grenade.cooldown-seconds", 3);
    }

    public int getCoinCleaverGrenadeCost() {
        return config.getInt("mythic-items.coin-cleaver.grenade.cost", 2000);
    }

    public double getCoinCleaverGrenadeDamage() {
        return config.getDouble("mythic-items.coin-cleaver.grenade.damage", 4.0);
    }

    public int getCoinCleaverGrenadeRadius() {
        return config.getInt("mythic-items.coin-cleaver.grenade.radius", 5);
    }

    // Carl's Battleaxe
    public int getCarlsSpinCooldown() {
        return config.getInt("mythic-items.carls-battleaxe.spin-attack.cooldown-seconds", 30);
    }

    public int getCarlsSpinDuration() {
        return config.getInt("mythic-items.carls-battleaxe.spin-attack.duration-ticks", 80); // 4 seconds
    }

    public double getCarlsSpinDamage() {
        return config.getDouble("mythic-items.carls-battleaxe.spin-attack.damage", 6.0);
    }

    public double getCarlsSpinRadius() {
        return config.getDouble("mythic-items.carls-battleaxe.spin-attack.radius", 2.5);
    }

    public int getCarlsSpinHitInterval() {
        return config.getInt("mythic-items.carls-battleaxe.spin-attack.hit-interval-ticks", 10);
    }

    public int getCarlsCritCooldown() {
        return config.getInt("mythic-items.carls-battleaxe.critical-hit.cooldown-milliseconds", 10000);
    }

    public double getCarlsCritLaunchPower() {
        return config.getDouble("mythic-items.carls-battleaxe.critical-hit.launch-power", 1.2);
    }

    // Wind Bow
    public int getWindBowBoostCooldown() {
        return config.getInt("mythic-items.wind-bow.boost.cooldown-seconds", 30);
    }

    public double getWindBowBoostPower() {
        return config.getDouble("mythic-items.wind-bow.boost.power", 2.0);
    }

    public int getWindBowPushRadius() {
        return config.getInt("mythic-items.wind-bow.arrow-push.radius", 3);
    }

    public double getWindBowPushPower() {
        return config.getDouble("mythic-items.wind-bow.arrow-push.power", 1.5);
    }

    // Electric Eel
    public int getEelChainCooldown() {
        return config.getInt("mythic-items.electric-eel.chain-damage.cooldown-seconds", 1);
    }

    public double getEelChainDamage() {
        return config.getDouble("mythic-items.electric-eel.chain-damage.damage", 1.0);
    }

    public int getEelChainRadius() {
        return config.getInt("mythic-items.electric-eel.chain-damage.radius", 5);
    }

    public int getEelTeleportCooldown() {
        return config.getInt("mythic-items.electric-eel.teleport.cooldown-seconds", 15);
    }

    public double getEelTeleportDistance() {
        return config.getDouble("mythic-items.electric-eel.teleport.distance", 4.0);
    }

    // Goblin Spear
    public int getGoblinThrowCooldown() {
        return config.getInt("mythic-items.goblin-spear.throw.cooldown-seconds", 15);
    }

    public double getGoblinSpearDamage() {
        return config.getDouble("mythic-items.goblin-spear.throw.damage", 9.0);
    }

    public int getGoblinPoisonDuration() {
        return config.getInt("mythic-items.goblin-spear.poison.duration-seconds", 2) * 20;
    }

    public int getGoblinPoisonLevel() {
        return config.getInt("mythic-items.goblin-spear.poison.level", 3) - 1; // 0-indexed
    }

    // BloodWrench Crossbow
    public int getBloodwrenchBurstShots() {
        return config.getInt("mythic-items.bloodwrench.burst.shots", 3);
    }

    public int getBloodwrenchReloadCooldown() {
        return config.getInt("mythic-items.bloodwrench.burst.reload-cooldown-seconds", 14);
    }

    public long getBloodwrenchSuperchargeTime() {
        return config.getLong("mythic-items.bloodwrench.supercharge.charge-time-seconds", 28) * 1000;
    }

    public int getBloodwrenchDuration() {
        return config.getInt("mythic-items.bloodwrench.supercharge.storm-duration-seconds", 4) * 20;
    }

    // Warden Gloves
    public int getWardenShockwaveCooldown() {
        return config.getInt("mythic-items.warden-gloves.shockwave.cooldown-seconds", 41);
    }

    public double getWardenShockwaveDamage() {
        return config.getDouble("mythic-items.warden-gloves.shockwave.damage", 12.0);
    }

    public int getWardenShockwaveRange() {
        return config.getInt("mythic-items.warden-gloves.shockwave.range", 8);
    }

    public double getWardenKnockbackPower() {
        return config.getDouble("mythic-items.warden-gloves.shockwave.knockback-power", 2.5);
    }

    public int getWardenMeleeCooldown() {
        return config.getInt("mythic-items.warden-gloves.melee.cooldown-seconds", 22);
    }

    // BlazeBite
    public int getBlazebiteShotsPerMag() {
        return config.getInt("mythic-items.blazebite-crossbows.shots-per-magazine", 8);
    }

    public int getBlazebiteReloadCooldown() {
        return config.getInt("mythic-items.blazebite-crossbows.reload-cooldown-seconds", 25);
    }

    public int getBlazebiteFreezeDuration() {
        return config.getInt("mythic-items.blazebite-crossbows.glacier.slowness-duration-seconds", 3) * 20;
    }

    public double getBlazebiteVolcanoDirectDamage() {
        return config.getDouble("mythic-items.blazebite-crossbows.volcano.direct-damage", 4.0);
    }

    public double getBlazebiteVolcanoSplashDamage() {
        return config.getDouble("mythic-items.blazebite-crossbows.volcano.splash-damage", 2.0);
    }

    public int getBlazebiteVolcanoRadius() {
        return config.getInt("mythic-items.blazebite-crossbows.volcano.explosion-radius", 3);
    }

    // ==================== CUSTOM ITEMS ====================

    // Grenade
    public int getGrenadeFuseSeconds() {
        return config.getInt("custom-items.grenade.fuse-seconds", 3);
    }

    public double getGrenadeInnerDamage() {
        return config.getDouble("custom-items.grenade.damage.inner-damage", 8.0);
    }

    public double getGrenadeOuterDamage() {
        return config.getDouble("custom-items.grenade.damage.outer-damage", 2.0);
    }

    // Bounce Pad
    public double getBouncePadForwardVelocity() {
        return config.getDouble("custom-items.bounce-pad.forward-velocity", 1.4);
    }

    public double getBouncePadUpwardVelocity() {
        return config.getDouble("custom-items.bounce-pad.upward-velocity", 1.0);
    }

    // Medic Pouch
    public int getMedicPouchCooldown() {
        return config.getInt("custom-items.medic-pouch.cooldown-seconds", 10);
    }

    public double getMedicPouchSelfHeal() {
        return config.getDouble("custom-items.medic-pouch.self-heal", 6.0);
    }

    public double getMedicPouchAllyHeal() {
        return config.getDouble("custom-items.medic-pouch.ally-heal", 10.0);
    }

    // Boombox
    public int getBoomboxPulseInterval() {
        return config.getInt("custom-items.boombox.pulse-interval-seconds", 3);
    }

    public int getBoomboxDuration() {
        return config.getInt("custom-items.boombox.total-duration-seconds", 12);
    }

    // Invis Cloak
    public int getInvisCloakCostPerSecond() {
        return config.getInt("custom-items.invis-cloak.cost-per-second", 100);
    }

    public int getInvisCloakCooldown() {
        return config.getInt("custom-items.invis-cloak.cooldown-seconds", 15);
    }

    // Cash Blaster
    public int getCashBlasterCoinsPerHit() {
        return config.getInt("custom-items.cash-blaster.coins-per-hit", 500);
    }

    // Respawn Anchor
    public int getRespawnAnchorDuration() {
        return config.getInt("custom-items.respawn-anchor.revive-duration-seconds", 10);
    }

    public int getRespawnAnchorMaxDistance() {
        return config.getInt("custom-items.respawn-anchor.max-distance", 5);
    }

    // ==================== CUSTOM ARMOR ====================

    // Deathmauler
    public int getDeathmaulerAbsorptionDelay() {
        return config.getInt("custom-armor.deathmauler.absorption.no-damage-delay-seconds", 8);
    }

    // Magic Helmet
    public int getMagicHelmetStandDelay() {
        return config.getInt("custom-armor.magic-helmet.stand-still-delay-seconds", 3);
    }

    public int getMagicHelmetInvisDuration() {
        return config.getInt("custom-armor.magic-helmet.invisibility-duration-seconds", 10);
    }

    public int getMagicHelmetCooldown() {
        return config.getInt("custom-armor.magic-helmet.cooldown-seconds", 30);
    }

    // Bunny Shoes
    public int getBunnyShoesDuration() {
        return config.getInt("custom-armor.bunny-shoes.duration-seconds", 15);
    }

    public int getBunnyShoesCooldown() {
        return config.getInt("custom-armor.bunny-shoes.cooldown-seconds", 25);
    }

    // Dragon Set
    public int getDragonDoubleJumpCooldown() {
        return config.getInt("custom-armor.dragon.double-jump.cooldown-seconds", 10);
    }

    // ==================== CONSUMABLES ====================

    /**
     * Global cooldown for special effect consumables (speed carrot, golden chicken, etc.)
     * Prevents spam-eating. Default: 2 seconds.
     */
    public int getConsumableCooldown() {
        return config.getInt("consumables.effect-cooldown-seconds", 2);
    }

    // ==================== LOBBY ITEMS ====================

    // Stats Item
    public String getLobbyStatsMaterial() {
        return config.getString("lobby-items.stats.material", "PAPER");
    }

    public String getLobbyStatsName() {
        return config.getString("lobby-items.stats.name", "<gold><bold>Your Statistics</bold></gold>");
    }

    public java.util.List<String> getLobbyStatsLore() {
        return config.getStringList("lobby-items.stats.lore");
    }

    public int getLobbyStatsSlot() {
        return config.getInt("lobby-items.stats.slot", 0);
    }

    // Arena Selector Item
    public String getLobbyArenaSelectorMaterial() {
        return config.getString("lobby-items.arena-selector.material", "COMPASS");
    }

    public String getLobbyArenaSelectorName() {
        return config.getString("lobby-items.arena-selector.name", "<green><bold>Arena Selector</bold></green>");
    }

    public java.util.List<String> getLobbyArenaSelectorLore() {
        return config.getStringList("lobby-items.arena-selector.lore");
    }

    public int getLobbyArenaSelectorSlot() {
        return config.getInt("lobby-items.arena-selector.slot", 4);
    }

    // Layout Configurator Item
    public String getLobbyLayoutConfiguratorMaterial() {
        return config.getString("lobby-items.layout-configurator.material", "ANVIL");
    }

    public String getLobbyLayoutConfiguratorName() {
        return config.getString("lobby-items.layout-configurator.name", "<yellow><bold>Layout Configurator</bold></yellow>");
    }

    public java.util.List<String> getLobbyLayoutConfiguratorLore() {
        return config.getStringList("lobby-items.layout-configurator.lore");
    }

    public int getLobbyLayoutConfiguratorSlot() {
        return config.getInt("lobby-items.layout-configurator.slot", 8);
    }
}

