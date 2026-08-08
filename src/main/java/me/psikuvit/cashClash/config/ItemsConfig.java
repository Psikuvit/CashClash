package me.psikuvit.cashClash.config;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    public int getWindBowShotsPerMagazine() {
        return config.getInt("mythic-items.wind-bow.shots-per-magazine", 10);
    }

    public int getWindBowReloadCooldown() {
        return config.getInt("mythic-items.wind-bow.reload-cooldown-seconds", 30);
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
    public int getGoblinShotsPerMag() {
        return config.getInt("mythic-items.goblin-spear.throw.shots-per-magazine", 8);
    }

    public int getGoblinReloadCooldown() {
        return config.getInt("mythic-items.goblin-spear.throw.reload-cooldown-seconds", 15);
    }

    public double getGoblinSpearDamage() {
        return config.getDouble("mythic-items.goblin-spear.throw.damage", 9.0);
    }

    public int getGoblinPoisonDuration() {
        return config.getInt("mythic-items.goblin-spear.poison.duration-seconds", 3) * 20;
    }

    public int getGoblinPoisonLevel() {
        return config.getInt("mythic-items.goblin-spear.poison.level", 1) - 1; // 0-indexed
    }

    public int getGoblinChargeCooldown() {
        return config.getInt("mythic-items.goblin-spear.charge.cooldown-seconds", 30);
    }

    public double getGoblinChargeSpeed() {
        return config.getDouble("mythic-items.goblin-spear.charge.speed", 1.5);
    }

    public int getGoblinChargeMaxDuration() {
        return config.getInt("mythic-items.goblin-spear.charge.max-duration-ticks", 60);
    }

    public double getGoblinChargeWallDamage() {
        return config.getDouble("mythic-items.goblin-spear.charge.wall-impact-damage", 12.0);
    }

    public int getGoblinChargePoisonDuration() {
        return config.getInt("mythic-items.goblin-spear.charge.poison-duration-seconds", 3) * 20;
    }

    public int getGoblinChargePoisonLevel() {
        return config.getInt("mythic-items.goblin-spear.charge.poison-level", 1) - 1; // 0-indexed
    }

    // BloodWrench Crossbow - Mode Toggle
    public int getBloodwrenchModeToggleCooldown() {
        return config.getInt("mythic-items.bloodwrench.mode-toggle-cooldown-seconds", 1);
    }

    // BloodWrench Crossbow - Rapid Fire Mode
    public int getBloodwrenchRapidShots() {
        return config.getInt("mythic-items.bloodwrench.rapid.shots", 3);
    }

    public int getBloodwrenchRapidReloadCooldown() {
        return config.getInt("mythic-items.bloodwrench.rapid.reload-cooldown-seconds", 14);
    }

    public double getBloodwrenchSphereRadius() {
        return config.getDouble("mythic-items.bloodwrench.rapid.sphere-radius", 2.0);
    }

    public int getBloodwrenchSphereDuration() {
        return config.getInt("mythic-items.bloodwrench.rapid.sphere-duration-ticks", 60);
    }

    public double getBloodwrenchSphereDamage() {
        return config.getDouble("mythic-items.bloodwrench.rapid.sphere-burst-damage", 4.0);
    }

    // BloodWrench Crossbow - Supercharged Mode
    public int getBloodwrenchSuperchargeCooldown() {
        return config.getInt("mythic-items.bloodwrench.supercharge.cooldown-seconds", 25);
    }

    public double getBloodwrenchVortexRadius() {
        return config.getDouble("mythic-items.bloodwrench.supercharge.vortex-radius", 4.0);
    }

    public int getBloodwrenchVortexDuration() {
        return config.getInt("mythic-items.bloodwrench.supercharge.vortex-duration-ticks", 80);
    }

    public double getBloodwrenchVortexDamage() {
        return config.getDouble("mythic-items.bloodwrench.supercharge.vortex-damage-per-tick", 2.0);
    }

    public int getBloodwrenchVortexLevitationLevel() {
        return config.getInt("mythic-items.bloodwrench.supercharge.vortex-levitation-level", 3);
    }

    public int getBloodwrenchVortexSelfHealPercent() {
        return config.getInt("mythic-items.bloodwrench.supercharge.self-heal-percent", 10);
    }

    /** Shared by both the Rapid-Fire sphere and the Supercharge vortex heal-negation zones. */
    public int getBloodwrenchHealNegationDuration() {
        return config.getInt("mythic-items.bloodwrench.heal-negation-duration-seconds", 2);
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

    // Warden Boxing ability
    public int getWardenBoxingDuration() {
        return config.getInt("mythic-items.warden-gloves.boxing.duration-seconds", 20);
    }

    public int getWardenBoxingCooldown() {
        return config.getInt("mythic-items.warden-gloves.boxing.cooldown-seconds", 35);
    }

    public int getWardenBoxingPunchesForSpeed() {
        return config.getInt("mythic-items.warden-gloves.boxing.punches-for-speed", 5);
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

    public int getBlazebiteMaxSlownessDuration() {
        return config.getInt("mythic-items.blazebite-crossbows.glacier.max-slowness-duration-seconds", 5) * 20;
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

    // Alchemist Wand Tidy Up (Blink Swap stays hardcoded, not config-driven)
    public int getAlchemistTidyUpDuration() {
        return config.getInt("mythic-items.alchemist-wand.tidy-up.duration-seconds", 10);
    }

    public int getAlchemistTidyUpCooldown() {
        return config.getInt("mythic-items.alchemist-wand.tidy-up.cooldown-seconds", 24);
    }

    // Alchemist Wand Taunt
    public int getAlchemistTauntDuration() {
        return config.getInt("mythic-items.alchemist-wand.taunt.duration-seconds", 7);
    }

    public int getAlchemistTauntCooldown() {
        return config.getInt("mythic-items.alchemist-wand.taunt.cooldown-seconds", 26);
    }

    public double getAlchemistTauntChainRadius() {
        return config.getDouble("mythic-items.alchemist-wand.taunt.chain-radius", 6.5);
    }

    public double getAlchemistTauntDamageIncreasePercent() {
        return config.getDouble("mythic-items.alchemist-wand.taunt.wielder-damage-increase-percent", 15);
    }

    public boolean isAlchemistTauntTestModeAllPlayers() {
        return config.getBoolean("mythic-items.alchemist-wand.taunt.test-mode-all-players", false);
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
        return config.getDouble("custom-items.bounce-pad.forward-velocity", 1.2);
    }

    public double getBouncePadUpwardVelocity() {
        return config.getDouble("custom-items.bounce-pad.upward-velocity", 0.85);
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

    public double getBoomboxRadius() {
        return config.getDouble("custom-items.boombox.radius", 5);
    }

    public int getBoomboxSpeedBoostPercent() {
        return config.getInt("custom-items.boombox.speed-boost-percent", 20);
    }

    public int getBoomboxSpeedBoostDuration() {
        return config.getInt("custom-items.boombox.speed-boost-duration-seconds", 5);
    }

    // Invis Cloak
    public int getInvisCloakCostPerSecond() {
        return config.getInt("custom-items.invis-cloak.cost-per-second", 100);
    }

    public int getInvisCloakCooldown() {
        return config.getInt("custom-items.invis-cloak.cooldown-seconds", 15);
    }

    // Cash Blaster
    public int getCashBlasterVortexCooldown() {
        return config.getInt("weapons.cash-blaster.vortex-cooldown-seconds", 10);
    }

    public double getCashBlasterVortexRadius() {
        return config.getDouble("weapons.cash-blaster.vortex-radius", 3.5);
    }

    public int getCashBlasterVortexDurationTicks() {
        return config.getInt("weapons.cash-blaster.vortex-duration-ticks", 80);
    }

    public int getCashBlasterVortexKillReward() {
        return config.getInt("weapons.cash-blaster.vortex-kill-reward", 400);
    }

    // Respawn Anchor
    public int getRespawnAnchorDuration() {
        return config.getInt("custom-items.respawn-anchor.revive-duration-seconds", 10);
    }

    public int getRespawnAnchorMaxDistance() {
        return config.getInt("custom-items.respawn-anchor.max-distance", 5);
    }

    // Totem of Haunting
    public int getTotemInvincibilitySeconds() {
        return config.getInt("custom-items.totem-of-haunting.invincibility-seconds", 2);
    }

    public double getTotemDebuffRadius() {
        return config.getDouble("custom-items.totem-of-haunting.debuff-radius", 6);
    }

    public int getTotemDebuffDurationSeconds() {
        return config.getInt("custom-items.totem-of-haunting.debuff-duration-seconds", 6);
    }

    public double getTotemRevivalHealth() {
        return config.getDouble("custom-items.totem-of-haunting.revive-health", 1.0);
    }

    // Radiating Lotus
    public int getLotusMaxChargeSeconds() {
        return config.getInt("custom-items.radiating-lotus.max-charge-seconds", 2);
    }

    public int getLotusGraceSeconds() {
        return config.getInt("custom-items.radiating-lotus.grace-seconds", 1);
    }

    public double getLotusKnockbackDistance() {
        return config.getDouble("custom-items.radiating-lotus.knockback-distance", 3.0);
    }

    public double getLotusHealRadiusPerSecond() {
        return config.getDouble("custom-items.radiating-lotus.heal-radius-per-second", 2.5);
    }

    public double getLotusHealAmount() {
        return config.getDouble("custom-items.radiating-lotus.heal-amount", 4.0);
    }

    public int getLotusSlowPercentWhileCharging() {
        return config.getInt("custom-items.radiating-lotus.slow-percent-while-charging", 80);
    }

    // Ice Fan
    public int getIceFanMaxDurability() {
        return config.getInt("custom-items.ice-fan.max-durability", 75);
    }

    public int getIceFanGustDurabilityPerSecond() {
        return config.getInt("custom-items.ice-fan.gust-durability-per-second", 5);
    }

    public double getIceFanGustDamagePerTick() {
        return config.getDouble("custom-items.ice-fan.gust-damage-per-tick", 1.0);
    }

    public int getIceFanBurstDurabilityCost() {
        return config.getInt("custom-items.ice-fan.burst-durability-cost", 25);
    }

    public double getIceFanBurstDamage() {
        return config.getDouble("custom-items.ice-fan.burst-damage", 4.5);
    }

    public int getIceFanBurstMinDurability() {
        return config.getInt("custom-items.ice-fan.burst-min-durability", 25);
    }

    // Overdrive Potion
    public int getOverdriveInvincibilitySeconds() {
        return config.getInt("custom-items.overdrive-potion.invincibility-seconds", 4);
    }

    public int getOverdriveSpeedPercent() {
        return config.getInt("custom-items.overdrive-potion.speed-boost-percent", 25);
    }

    // Hunter's Mark
    public double getHuntersMarkRange() {
        return config.getDouble("custom-items.hunters-mark.range", 1);
    }

    public int getHuntersMarkDurationSeconds() {
        return config.getInt("custom-items.hunters-mark.duration-seconds", 8);
    }

    public int getHuntersMarkBaseVulnerabilityPercent() {
        return config.getInt("custom-items.hunters-mark.base-vulnerability-percent", 15);
    }

    // Blooming Rose
    public double getBloomingRoseZoneRadius() {
        return config.getDouble("custom-items.blooming-rose.zone-radius", 3);
    }

    public int getBloomingRoseZoneDurationSeconds() {
        return config.getInt("custom-items.blooming-rose.zone-duration-seconds", 6);
    }

    public int getBloomingRoseDamageReductionPercent() {
        return config.getInt("custom-items.blooming-rose.damage-reduction-percent", 25);
    }

    public double getBloomingRoseMinHealthFloor() {
        return config.getDouble("custom-items.blooming-rose.min-health-floor", 4.0);
    }

    public int getBloomingRoseRegenDurationSeconds() {
        return config.getInt("custom-items.blooming-rose.regen-duration-seconds", 5);
    }

    // Orb of Gravitation
    public double getOrbThrowSpeed() {
        return config.getDouble("custom-items.orb-of-gravitation.throw-speed", 0.35);
    }

    public double getOrbPullRadius() {
        return config.getDouble("custom-items.orb-of-gravitation.pull-radius", 4);
    }

    public int getOrbPullDurationTicks() {
        return config.getInt("custom-items.orb-of-gravitation.pull-duration-ticks", 40);
    }

    public int getOrbSlownessDurationSeconds() {
        return config.getInt("custom-items.orb-of-gravitation.slowness-duration-seconds", 5);
    }

    public int getOrbHitsToDestroy() {
        return config.getInt("custom-items.orb-of-gravitation.hits-to-destroy", 4);
    }

    // Soul Katana
    public double getSoulKatanaLeapDistance() {
        return config.getDouble("weapons.soul-katana.leap-distance", 3);
    }

    public double getSoulKatanaStrikeDamage() {
        return config.getDouble("weapons.soul-katana.strike-damage", 6.0);
    }

    public int getSoulKatanaHealingReductionPercent() {
        return config.getInt("weapons.soul-katana.healing-reduction-percent", 30);
    }

    public int getSoulKatanaHealingReductionDurationSeconds() {
        return config.getInt("weapons.soul-katana.healing-reduction-duration-seconds", 3);
    }

    public int getSoulKatanaCooldownSeconds() {
        return config.getInt("weapons.soul-katana.cooldown-seconds", 18);
    }

    public double getSoulKatanaStrikeRadius() {
        return config.getDouble("weapons.soul-katana.strike-radius", 4);
    }

    // ==================== CUSTOM ARMOR ====================

    // Deathmauler
    public int getDeathmaulerAbsorptionDelay() {
        return config.getInt("custom-armor.deathmauler.absorption.no-damage-delay-seconds", 8);
    }

    // Bunny Shoes
    public int getBunnyShoesDuration() {
        return config.getInt("custom-armor.bunny-shoes.duration-seconds", 15);
    }

    public int getBunnyShoesCooldown() {
        return config.getInt("custom-armor.bunny-shoes.cooldown-seconds", 25);
    }

    // Tectonic Cap
    public int getTectonicCapRechargeSeconds() {
        return config.getInt("custom-armor.tectonic-cap.recharge-seconds", 22);
    }

    public double getTectonicCapDamage() {
        return config.getDouble("custom-armor.tectonic-cap.base-damage", 4.0);
    }

    public double getTectonicCapRadius() {
        return config.getDouble("custom-armor.tectonic-cap.radius", 4.0);
    }

    // Investor's Set
    public double getInvestorMeleeDamageBonusPerPiece() {
        return config.getDouble("custom-armor.investors.melee-damage-bonus-per-piece", 0.05);
    }

    // Dragon Set
    public int getDragonHitsForScale() {
        return config.getInt("custom-armor.dragon.hits-for-scale", 5);
    }

    public int getDragonMaxScales() {
        return config.getInt("custom-armor.dragon.max-scales", 3);
    }

    public int getDragonRushRange() {
        return config.getInt("custom-armor.dragon.rush-range", 5);
    }

    public double getDragonRushDamagePercent() {
        return config.getDouble("custom-armor.dragon.rush-damage-percentage", 0.25);
    }

    public int getDragonRushBuffSeconds() {
        return config.getInt("custom-armor.dragon.rush-buff-seconds", 3);
    }

    public int getDragonKillStrengthLevel() {
        return config.getInt("custom-armor.dragon.kill-strength-level", 0);
    }

    public int getDragonKillStrengthDuration() {
        return config.getInt("custom-armor.dragon.kill-strength-duration-seconds", 4);
    }

    // Flamebringer Set
    public boolean getFlamebringerNoFireKb() {
        return config.getBoolean("custom-armor.flamebringer.no-fire-kb", true);
    }

    public int getFlamebringerSpeedLevel() {
        return config.getInt("custom-armor.flamebringer.speed-level", 0);
    }

    public int getFlamebringerSpeedDuration() {
        // Flamebringer speed effect lasts 12 seconds by default
        return config.getInt("custom-armor.flamebringer.speed-duration-seconds", 12);
    }

    public double getFlamebringerPullRadius() {
        return config.getDouble("custom-armor.flamebringer.pull-radius", 6.0);
    }

    public double getFlamebringerPullDuration() {
        return config.getDouble("custom-armor.flamebringer.pull-duration-seconds", 1.5);
    }

    public double getFlamebringerPullStrength() {
        return config.getDouble("custom-armor.flamebringer.pull-strength", 0.4);
    }

    public int getFlamebringerKillsForPull() {
        return config.getInt("custom-armor.flamebringer.kills-for-pull", 2);
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

    // ==================== ITEM LORE CONFIGURATION ====================

    /**
     * Get lore lines for an item by category and key.
     * Returns empty list if not configured.
     *
     * @param category The item category (e.g., "weapons", "armor", "food", etc.)
     * @param itemKey The item key (enum name or config key)
     * @return List of lore lines as strings, or empty list if none configured
     */
    public List<String> getItemLore(String category, String itemKey) {
        if (category == null || itemKey == null) {
            return Collections.emptyList();
        }

        for (String resolvedCategory : resolveLoreCategories(category)) {
            for (String resolvedKey : resolveLoreKeys(itemKey)) {
                String path = "item-lore." + resolvedCategory + "." + resolvedKey + ".lore";
                List<String> lore = config.getStringList(path);
                if (!lore.isEmpty()) {
                    return lore;
                }

                // Backward compatibility: allow direct list under item key.
                path = "item-lore." + resolvedCategory + "." + resolvedKey;
                lore = config.getStringList(path);
                if (!lore.isEmpty()) {
                    return lore;
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Get configurable hover lore for main shop category icons.
     */
    public List<String> getCategoryLore(String categoryKey) {
        if (categoryKey == null || categoryKey.isBlank()) {
            return Collections.emptyList();
        }
        return config.getStringList("item-lore.categories." + categoryKey.toLowerCase(Locale.ROOT) + ".lore");
    }

    /**
     * Get optional configurable display name for a main shop category icon.
     */
    public String getCategoryName(String categoryKey, String fallback) {
        if (categoryKey == null || categoryKey.isBlank()) {
            return fallback;
        }
        return config.getString("item-lore.categories." + categoryKey.toLowerCase(Locale.ROOT) + ".name", fallback);
    }

    private List<String> resolveLoreCategories(String category) {
        String normalized = category.toLowerCase(Locale.ROOT);
        Set<String> values = new LinkedHashSet<>();
        values.add(normalized);

        if (normalized.equals("mythic")) {
            values.add("mythic-items");
        }
        if (normalized.equals("legendaries")) {
            values.add("mythic-items");
        }
        if (normalized.equals("mythic-items")) {
            values.add("mythic");
        }

        return new ArrayList<>(values);
    }

    private List<String> resolveLoreKeys(String itemKey) {
        String normalized = itemKey.toLowerCase(Locale.ROOT);
        Set<String> values = new LinkedHashSet<>();
        values.add(normalized);
        values.add(normalized.replace("_", "-"));

        if (normalized.equals("arrow")) values.add("arrows");
        if (normalized.equals("arrows")) values.add("arrow");

        if (normalized.equals("tax-evasion-pants")) values.add("tax-evasion");
        if (normalized.equals("electric-eel-sword")) values.add("electric-eel");
        if (normalized.equals("bloodwrench-crossbow")) values.add("bloodwrench");

        if (normalized.startsWith("investors-")) values.add("investors");
        if (normalized.startsWith("deathmauler-")) values.add("deathmauler");
        if (normalized.startsWith("flamebringer-")) values.add("flamebringer");
        if (normalized.startsWith("dragon-")) values.add("dragon");
        if (normalized.equals("dragon-head")) values.add("dragon");

        return new ArrayList<>(values);
    }

    /**
     * Get description for an item by category and key.
     * Returns null or empty string if not configured.
     *
     * @param category The item category
     * @param itemKey The item key
     * @return Description string, or empty string if none configured
     */
    public String getItemDescription(String category, String itemKey) {
        if (category == null || itemKey == null) {
            return "";
        }

        String path = "item-lore." + category + "." + itemKey + ".description";
        return config.getString(path, "");
    }
}

