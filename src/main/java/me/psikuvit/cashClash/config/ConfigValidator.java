package me.psikuvit.cashClash.config;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Validates configuration files and ensures all required keys exist.
 * Logs warnings for missing keys and can auto-add defaults.
 */
public class ConfigValidator {

    private final List<String> errors;
    private final List<String> warnings;
    private final List<String> added;

    public ConfigValidator() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.added = new ArrayList<>();
    }

    /**
     * Validate shop.yml configuration.
     * @param config The config to validate
     * @param autoAdd If true, missing keys will be added with defaults
     * @return true if valid, false if critical errors found
     */
    public boolean validateShopConfig(FileConfiguration config, boolean autoAdd) {
        errors.clear();
        warnings.clear();
        added.clear();

        // Check schema version
        validateAndSet(config, "schema-version", 1, autoAdd);

        // Validate armor section
        Map<String, Object> armorDefaults = new HashMap<>();
        armorDefaults.put("iron-boots", 2250L);
        armorDefaults.put("iron-helmet", 2500L);
        armorDefaults.put("iron-leggings", 2750L);
        armorDefaults.put("iron-chestplate", 3000L);
        armorDefaults.put("diamond-boots", 4500L);
        armorDefaults.put("diamond-helmet", 5000L);
        armorDefaults.put("diamond-leggings", 5500L);
        armorDefaults.put("diamond-chestplate", 6000L);
        validateSection(config, "armor", armorDefaults, autoAdd);

        // Validate weapons section
        Map<String, Object> weaponDefaults = new HashMap<>();
        weaponDefaults.put("iron-sword", 1250L);
        weaponDefaults.put("iron-axe", 1250L);
        weaponDefaults.put("diamond-sword", 3000L);
        weaponDefaults.put("diamond-axe", 3000L);
        weaponDefaults.put("netherite-sword", 10000L);
        weaponDefaults.put("netherite-axe", 10000L);
        validateSection(config, "weapons", weaponDefaults, autoAdd);

        // Validate food section
        Map<String, Object> foodDefaults = new HashMap<>();
        foodDefaults.put("bread", 25L);
        foodDefaults.put("cooked-mutton", 50L);
        foodDefaults.put("steak", 75L);
        foodDefaults.put("porkchop", 100L);
        foodDefaults.put("golden-carrot", 150L);
        foodDefaults.put("golden-apple", 500L);
        foodDefaults.put("speed-carrot", 1000L);
        foodDefaults.put("golden-chicken", 1500L);
        foodDefaults.put("cookie-of-life", 2000L);
        foodDefaults.put("sunscreen", 2500L);
        foodDefaults.put("can-of-spinach", 3000L);
        foodDefaults.put("enchanted-golden-apple", 10000L);
        validateSection(config, "food", foodDefaults, autoAdd);

        // Validate utility section
        Map<String, Object> utilityDefaults = new HashMap<>();
        utilityDefaults.put("lava-bucket", 500L);
        utilityDefaults.put("fishing-rod", 750L);
        utilityDefaults.put("cobweb", 1000L);
        utilityDefaults.put("crossbow", 1500L);
        utilityDefaults.put("water-bucket", 2000L);
        utilityDefaults.put("wind-charge", 3000L);
        utilityDefaults.put("bow", 3500L);
        utilityDefaults.put("arrow", 500L);
        utilityDefaults.put("leaves", 750L);
        utilityDefaults.put("soul-sand", 1000L);
        validateSection(config, "utility", utilityDefaults, autoAdd);

        // Validate custom items section
        Map<String, Object> customItemDefaults = new HashMap<>();
        customItemDefaults.put("grenade", 5000L);
        customItemDefaults.put("smoke-grenade", 7500L);
        customItemDefaults.put("bounce-pad", 10000L);
        customItemDefaults.put("medic-pouch", 12500L);
        customItemDefaults.put("tablet-of-hacking", 15000L);
        customItemDefaults.put("bag-of-potatoes", 17500L);
        customItemDefaults.put("boombox", 20000L);
        customItemDefaults.put("invis-cloak", 25000L);
        customItemDefaults.put("cash-blaster", 30000L);
        customItemDefaults.put("respawn-anchor", 50000L);
        validateSection(config, "custom-items", customItemDefaults, autoAdd);

        // Validate custom armor section
        Map<String, Object> customArmorDefaults = new HashMap<>();
        customArmorDefaults.put("magic-helmet", 15000L);
        customArmorDefaults.put("guardians-vest", 20000L);
        customArmorDefaults.put("tax-evasion-pants", 25000L);
        customArmorDefaults.put("bunny-shoes", 30000L);
        customArmorDefaults.put("investors-helmet", 35000L);
        customArmorDefaults.put("investors-chestplate", 40000L);
        customArmorDefaults.put("investors-leggings", 45000L);
        customArmorDefaults.put("investors-boots", 50000L);
        customArmorDefaults.put("flamebringer-leggings", 65000L);
        customArmorDefaults.put("flamebringer-boots", 70000L);
        customArmorDefaults.put("deathmauler-chestplate", 80000L);
        customArmorDefaults.put("deathmauler-leggings", 85000L);
        customArmorDefaults.put("dragon-head", 95000L);
        customArmorDefaults.put("dragon-chestplate", 100000L);
        customArmorDefaults.put("dragon-boots", 110000L);
        validateSection(config, "custom-armor", customArmorDefaults, autoAdd);

        // Validate mythic items section
        Map<String, Object> mythicDefaults = new HashMap<>();
        mythicDefaults.put("coin-cleaver", 75000L);
        mythicDefaults.put("carls-battleaxe", 100000L);
        mythicDefaults.put("wind-bow", 100000L);
        mythicDefaults.put("electric-eel-sword", 125000L);
        mythicDefaults.put("goblin-spear", 125000L);
        mythicDefaults.put("bloodwrench-crossbow", 125000L);
        mythicDefaults.put("warden-gloves", 150000L);
        mythicDefaults.put("blazebite-crossbows", 150000L);
        validateSection(config, "mythic-items", mythicDefaults, autoAdd);

        // Validate enchantments section
        validateEnchantSection(config, "enchants.protection", 3, 10000, autoAdd);
        validateEnchantSection(config, "enchants.sharpness", 5, 5000, autoAdd);
        validateEnchantSection(config, "enchants.power", 5, 5000, autoAdd);
        validateEnchantSection(config, "enchants.knockback", 2, 3000, autoAdd);
        validateEnchantSection(config, "enchants.punch", 2, 3000, autoAdd);
        validateEnchantSection(config, "enchants.fire-aspect", 2, 8000, autoAdd);
        validateEnchantSection(config, "enchants.flame", 1, 10000, autoAdd);
        validateEnchantSection(config, "enchants.projectile_protection", 4, 8000, autoAdd);
        validateEnchantSection(config, "enchants.soul-speed", 3, 5000, autoAdd);
        validateEnchantSection(config, "enchants.piercing", 4, 3000, autoAdd);

        // Validate investments section
        validateAndSet(config, "investments.wallet.cost", 10000, autoAdd);
        validateAndSet(config, "investments.wallet.bonus-return", 30000, autoAdd);
        validateAndSet(config, "investments.wallet.negative-return", 5000, autoAdd);
        validateAndSet(config, "investments.purse.cost", 30000, autoAdd);
        validateAndSet(config, "investments.purse.bonus-return", 60000, autoAdd);
        validateAndSet(config, "investments.purse.negative-return", 10000, autoAdd);
        validateAndSet(config, "investments.ender-bag.cost", 50000, autoAdd);
        validateAndSet(config, "investments.ender-bag.bonus-return", 100000, autoAdd);
        validateAndSet(config, "investments.ender-bag.negative-return", 20000, autoAdd);

        logResults("shop.yml");
        return errors.isEmpty();
    }

    /**
     * Validate items.yml configuration.
     * @param config The config to validate
     * @param autoAdd If true, missing keys will be added with defaults
     * @return true if valid, false if critical errors found
     */
    public boolean validateItemsConfig(FileConfiguration config, boolean autoAdd) {
        errors.clear();
        warnings.clear();
        added.clear();

        // Check schema version
        validateAndSet(config, "schema-version", 1, autoAdd);

        // Mythic items section
        validateAndSet(config, "mythic-items.legendaries-per-game", 5, autoAdd);

        // Coin Cleaver
        validateAndSet(config, "mythic-items.coin-cleaver.damage-bonus-multiplier", 1.25, autoAdd);
        validateAndSet(config, "mythic-items.coin-cleaver.grenade.cooldown-seconds", 3, autoAdd);
        validateAndSet(config, "mythic-items.coin-cleaver.grenade.cost", 2000, autoAdd);
        validateAndSet(config, "mythic-items.coin-cleaver.grenade.damage", 4.0, autoAdd);
        validateAndSet(config, "mythic-items.coin-cleaver.grenade.radius", 5, autoAdd);

        // Carl's Battleaxe
        validateAndSet(config, "mythic-items.carls-battleaxe.charged-attack.cooldown-seconds", 45, autoAdd);
        validateAndSet(config, "mythic-items.carls-battleaxe.charged-attack.buff-duration-seconds", 25, autoAdd);
        validateAndSet(config, "mythic-items.carls-battleaxe.critical-hit.cooldown-milliseconds", 10000, autoAdd);
        validateAndSet(config, "mythic-items.carls-battleaxe.critical-hit.launch-power", 1.5, autoAdd);

        // Wind Bow
        validateAndSet(config, "mythic-items.wind-bow.boost.cooldown-seconds", 30, autoAdd);
        validateAndSet(config, "mythic-items.wind-bow.boost.power", 2.0, autoAdd);
        validateAndSet(config, "mythic-items.wind-bow.arrow-push.radius", 3, autoAdd);
        validateAndSet(config, "mythic-items.wind-bow.arrow-push.power", 1.5, autoAdd);

        // Electric Eel Sword
        validateAndSet(config, "mythic-items.electric-eel.chain-damage.cooldown-seconds", 1, autoAdd);
        validateAndSet(config, "mythic-items.electric-eel.chain-damage.damage", 1.0, autoAdd);
        validateAndSet(config, "mythic-items.electric-eel.chain-damage.radius", 5, autoAdd);
        validateAndSet(config, "mythic-items.electric-eel.teleport.cooldown-seconds", 15, autoAdd);
        validateAndSet(config, "mythic-items.electric-eel.teleport.distance", 4.0, autoAdd);

        // Goblin Spear
        validateAndSet(config, "mythic-items.goblin-spear.throw.cooldown-seconds", 15, autoAdd);
        validateAndSet(config, "mythic-items.goblin-spear.throw.damage", 9.0, autoAdd);
        validateAndSet(config, "mythic-items.goblin-spear.poison.duration-seconds", 2, autoAdd);
        validateAndSet(config, "mythic-items.goblin-spear.poison.level", 3, autoAdd);

        // Bloodwrench Crossbow
        validateAndSet(config, "mythic-items.bloodwrench.burst.shots", 3, autoAdd);
        validateAndSet(config, "mythic-items.bloodwrench.burst.reload-cooldown-seconds", 14, autoAdd);
        validateAndSet(config, "mythic-items.bloodwrench.supercharge.charge-time-seconds", 28, autoAdd);
        validateAndSet(config, "mythic-items.bloodwrench.supercharge.storm-duration-seconds", 4, autoAdd);

        // Warden Gloves
        validateAndSet(config, "mythic-items.warden-gloves.shockwave.cooldown-seconds", 41, autoAdd);
        validateAndSet(config, "mythic-items.warden-gloves.shockwave.damage", 12.0, autoAdd);
        validateAndSet(config, "mythic-items.warden-gloves.shockwave.range", 8, autoAdd);
        validateAndSet(config, "mythic-items.warden-gloves.melee.cooldown-seconds", 22, autoAdd);

        // BlazeBite Crossbows
        validateAndSet(config, "mythic-items.blazebite-crossbows.shots-per-magazine", 8, autoAdd);
        validateAndSet(config, "mythic-items.blazebite-crossbows.reload-cooldown-seconds", 25, autoAdd);
        validateAndSet(config, "mythic-items.blazebite-crossbows.glacier.slowness-duration-seconds", 3, autoAdd);
        validateAndSet(config, "mythic-items.blazebite-crossbows.glacier.frostbite-duration-seconds", 3, autoAdd);
        validateAndSet(config, "mythic-items.blazebite-crossbows.volcano.direct-damage", 4.0, autoAdd);
        validateAndSet(config, "mythic-items.blazebite-crossbows.volcano.fire-duration-seconds", 5, autoAdd);

        // Custom armor section
        validateAndSet(config, "custom-armor.magic-helmet.stand-still-delay-seconds", 3, autoAdd);
        validateAndSet(config, "custom-armor.magic-helmet.cooldown-seconds", 30, autoAdd);
        validateAndSet(config, "custom-armor.bunny-shoes.duration-seconds", 15, autoAdd);
        validateAndSet(config, "custom-armor.bunny-shoes.cooldown-seconds", 25, autoAdd);
        validateAndSet(config, "custom-armor.guardians-vest.trigger-health", 8.0, autoAdd);
        validateAndSet(config, "custom-armor.guardians-vest.uses-per-round", 3, autoAdd);
        validateAndSet(config, "custom-armor.deathmauler.kill-heal-amount", 8.0, autoAdd);
        validateAndSet(config, "custom-armor.deathmauler.absorption.no-damage-delay-seconds", 8, autoAdd);
        validateAndSet(config, "custom-armor.dragon.double-jump.cooldown-seconds", 10, autoAdd);
        validateAndSet(config, "custom-armor.dragon.double-jump.forward-velocity", 1.2, autoAdd);
        validateAndSet(config, "custom-armor.dragon.double-jump.upward-velocity", 0.8, autoAdd);
        validateAndSet(config, "custom-armor.flamebringer.ignite-chance", 0.30, autoAdd);
        validateAndSet(config, "custom-armor.tax-evasion.death-penalty-percentage", 0.075, autoAdd);
        validateAndSet(config, "custom-armor.tax-evasion.survival-bonus", 3000, autoAdd);
        validateAndSet(config, "custom-armor.investors.money-bonus-per-piece", 0.125, autoAdd);

        // Custom items section
        validateAndSet(config, "custom-items.grenade.fuse-seconds", 3, autoAdd);
        validateAndSet(config, "custom-items.grenade.damage.inner-radius", 4, autoAdd);
        validateAndSet(config, "custom-items.grenade.damage.inner-damage", 8.0, autoAdd);
        validateAndSet(config, "custom-items.grenade.damage.outer-radius", 6, autoAdd);
        validateAndSet(config, "custom-items.grenade.damage.outer-damage", 2.0, autoAdd);
        validateAndSet(config, "custom-items.smoke-grenade.fuse-seconds", 3, autoAdd);
        validateAndSet(config, "custom-items.smoke-grenade.cloud-duration-seconds", 8, autoAdd);
        validateAndSet(config, "custom-items.bounce-pad.forward-velocity", 1.4, autoAdd);
        validateAndSet(config, "custom-items.bounce-pad.upward-velocity", 1.0, autoAdd);
        validateAndSet(config, "custom-items.medic-pouch.cooldown-seconds", 10, autoAdd);
        validateAndSet(config, "custom-items.medic-pouch.self-heal", 6.0, autoAdd);
        validateAndSet(config, "custom-items.medic-pouch.ally-heal", 10.0, autoAdd);
        validateAndSet(config, "custom-items.bag-of-potatoes.knockback-level", 3, autoAdd);
        validateAndSet(config, "custom-items.bag-of-potatoes.durability", 3, autoAdd);
        validateAndSet(config, "custom-items.boombox.pulse-interval-seconds", 3, autoAdd);
        validateAndSet(config, "custom-items.boombox.total-duration-seconds", 12, autoAdd);
        validateAndSet(config, "custom-items.invis-cloak.cost-per-second", 100, autoAdd);
        validateAndSet(config, "custom-items.invis-cloak.uses-per-round", 5, autoAdd);
        validateAndSet(config, "custom-items.cash-blaster.coins-per-hit", 500, autoAdd);
        validateAndSet(config, "custom-items.respawn-anchor.revive-duration-seconds", 10, autoAdd);
        validateAndSet(config, "custom-items.respawn-anchor.max-uses-per-round", 2, autoAdd);

        // Consumables section
        validateAndSet(config, "consumables.effect-cooldown-seconds", 2, autoAdd);

        // Lobby items section
        validateAndSet(config, "lobby-items.stats.material", "PAPER", autoAdd);
        validateAndSet(config, "lobby-items.stats.slot", 0, autoAdd);
        validateAndSet(config, "lobby-items.arena-selector.material", "COMPASS", autoAdd);
        validateAndSet(config, "lobby-items.arena-selector.slot", 4, autoAdd);
        validateAndSet(config, "lobby-items.layout-configurator.material", "ANVIL", autoAdd);
        validateAndSet(config, "lobby-items.layout-configurator.slot", 8, autoAdd);

        logResults("items.yml");
        return errors.isEmpty();
    }

    /**
     * Validate config.yml configuration.
     * @param config The config to validate
     * @param autoAdd If true, missing keys will be added with defaults
     * @return true if valid, false if critical errors found
     */
    public boolean validateMainConfig(FileConfiguration config, boolean autoAdd) {
        errors.clear();
        warnings.clear();
        added.clear();

        // Note: Don't use Messages.debug() here as ConfigManager may not be initialized yet

        // Debug
        validateAndSet(config, "debug", false, autoAdd);

        // Game settings
        validateAndSet(config, "game.min-players", 8, autoAdd);
        validateAndSet(config, "game.max-players", 8, autoAdd);
        validateAndSet(config, "game.total-rounds", 5, autoAdd);
        validateAndSet(config, "game.first-round", 1, autoAdd);
        validateAndSet(config, "game.combat-phase-duration", 360, autoAdd);
        validateAndSet(config, "game.shopping-phase-duration", 90, autoAdd);
        validateAndSet(config, "game.first-round-shopping-duration", 120, autoAdd);
        validateAndSet(config, "game.respawn-delay", 5, autoAdd);
        validateAndSet(config, "game.respawn-protection", 15, autoAdd);
        validateAndSet(config, "game.forfeit-enabled", true, autoAdd);
        validateAndSet(config, "game.forfeit-delay", 10, autoAdd);
        validateAndSet(config, "game.forfeit-combat-grace", 5, autoAdd);

        // Round settings
        validateAndSet(config, "rounds.early-round-lives", 3, autoAdd);
        validateAndSet(config, "rounds.late-round-lives", 1, autoAdd);
        validateAndSet(config, "rounds.forfeit-bonus", 10000, autoAdd);

        // Armor restrictions
        validateAndSet(config, "armor.diamond-unlock-round", 4, autoAdd);
        validateAndSet(config, "armor.max-diamond-pieces-early", 2, autoAdd);

        // Economy settings
        validateAndSet(config, "economy.round-1-start", 10000, autoAdd);
        validateAndSet(config, "economy.round-2-bonus", 30000, autoAdd);
        validateAndSet(config, "economy.round-3-bonus", 50000, autoAdd);
        validateAndSet(config, "economy.round-4-bonus", 100000, autoAdd);
        validateAndSet(config, "economy.round-5-minimum", 20000, autoAdd);
        validateAndSet(config, "economy.round-5-bonus", 10000, autoAdd);
        validateAndSet(config, "economy.round-1-kill-reward", 3000, autoAdd);
        validateAndSet(config, "economy.round-1-transfer-fee", 0.50, autoAdd);
        validateAndSet(config, "economy.round-2-3-transfer-fee", 0.10, autoAdd);
        validateAndSet(config, "economy.round-4-5-transfer-fee", 0.05, autoAdd);
        validateAndSet(config, "economy.late-round-steal-percentage", 0.25, autoAdd);

        // Cash Quake settings
        validateAndSet(config, "cash-quake.min-guaranteed-events", 2, autoAdd);
        validateAndSet(config, "cash-quake.max-events-per-game", 10, autoAdd);
        validateAndSet(config, "cash-quake.max-events-per-round", 2, autoAdd);
        validateAndSet(config, "cash-quake.event-check-interval-ticks", 600L, autoAdd);
        validateAndSet(config, "cash-quake.event-base-chance", 0.30, autoAdd);

        // Cash Quake - Lottery
        validateAndSet(config, "cash-quake.lottery.entry-cost", 5000, autoAdd);
        validateAndSet(config, "cash-quake.lottery.prize", 10000, autoAdd);
        validateAndSet(config, "cash-quake.lottery.duration-seconds", 30, autoAdd);

        // Cash Quake - Weight of Wealth
        validateAndSet(config, "cash-quake.weight-of-wealth.tax-cost", 5000, autoAdd);
        validateAndSet(config, "cash-quake.weight-of-wealth.duration-seconds", 20, autoAdd);

        // Cash Quake - Life Steal
        validateAndSet(config, "cash-quake.life-steal.duration-minutes", 2, autoAdd);
        validateAndSet(config, "cash-quake.life-steal.health-per-kill", 4.0, autoAdd);
        validateAndSet(config, "cash-quake.life-steal.max-health", 40.0, autoAdd);

        // Cash Quake - Check Up
        validateAndSet(config, "cash-quake.check-up.duration-minutes", 2, autoAdd);
        validateAndSet(config, "cash-quake.check-up.min-hearts", 1, autoAdd);
        validateAndSet(config, "cash-quake.check-up.max-hearts", 5, autoAdd);

        // Cash Quake - Broken Gear
        validateAndSet(config, "cash-quake.broken-gear.duration-seconds", 30, autoAdd);

        // Cash Quake - Supply Drop
        validateAndSet(config, "cash-quake.supply-drop.min-chests", 3, autoAdd);
        validateAndSet(config, "cash-quake.supply-drop.max-extra-chests", 4, autoAdd);
        validateAndSet(config, "cash-quake.supply-drop.min-coins", 1000, autoAdd);
        validateAndSet(config, "cash-quake.supply-drop.max-extra-coins", 1001, autoAdd);

        // Player defaults
        validateAndSet(config, "player.default-health", 20.0, autoAdd);
        validateAndSet(config, "player.max-health-cap", 40.0, autoAdd);

        // Messages
        validateAndSet(config, "messages.prefix", "<gold><bold>[Cash Clash]</bold></gold>", autoAdd);
        validateAndSet(config, "messages.game-starting", "<green>Game starting in {time} seconds!</green>", autoAdd);
        validateAndSet(config, "messages.round-starting", "<yellow>Round {round} - Shopping Phase!</yellow>", autoAdd);
        validateAndSet(config, "messages.combat-starting", "<red>Combat Phase Starting!</red>", autoAdd);
        validateAndSet(config, "messages.team-eliminated", "<red>Team {team} has been eliminated!</red>", autoAdd);
        validateAndSet(config, "messages.team-forfeited", "<yellow>Team {team} has forfeited! Team {winner} earns {amount} each!</yellow>", autoAdd);
        validateAndSet(config, "messages.winner", "<gold><bold>Team {team} wins with ${amount}!</bold></gold>", autoAdd);

        // Scoreboard - Lobby
        validateAndSet(config, "scoreboard.lobby.enabled", true, autoAdd);
        validateAndSet(config, "scoreboard.lobby.title", "<gold><bold>Cash Clash</bold></gold>", autoAdd);
        validateAndSetList(config, "scoreboard.lobby.lines", List.of(
                "",
                "<gray>Player:</gray> <white>{player}",
                "",
                "<yellow>Your Stats:</yellow>",
                "<gray>Wins:</gray> <green>{wins}",
                "<gray>Losses:</gray> <red>{losses}",
                "<gray>K/D:</gray> <white>{kdr}",
                "",
                "<gray>Kills:</gray> <white>{kills}",
                "<gray>Deaths:</gray> <white>{deaths}",
                "",
                "<gray>Online:</gray> <white>{online}/{max_online}",
                "",
                "<yellow>play.cashclash.net</yellow>"
        ), autoAdd);

        // Scoreboard - Game
        validateAndSet(config, "scoreboard.game.enabled", true, autoAdd);
        validateAndSet(config, "scoreboard.game.title", "<gold><bold>Round {round} - {phase}</bold></gold>", autoAdd);
        validateAndSetList(config, "scoreboard.game.lines", List.of(
                "",
                "<yellow>Time:</yellow> <white>{time}",
                "<yellow>Phase:</yellow> <white>{phase}",
                "",
                "<green>Your Team ({your_team}):</green>",
                "<gray>Coins:</gray> <white>${your_team_coins}",
                "<gray>Alive:</gray> <white>{your_team_alive}/4",
                "",
                "<red>Enemy Team ({enemy_team}):</red>",
                "<gray>Coins:</gray> <white>${enemy_team_coins}",
                "<gray>Alive:</gray> <white>{enemy_team_alive}/4",
                "",
                "<aqua>You:</aqua>",
                "<gray>Coins:</gray> <gold>${player_coins}",
                "<gray>Lives:</gray> <white>{player_lives}",
                "<gray>Kills:</gray> <white>{player_kills}",
                "",
                "<yellow>play.cashclash.net</yellow>"
        ), autoAdd);

        // NPC settings
        validateAndSet(config, "npc.arena.display-name", "<gold><bold>Arena Selector</bold></gold>", autoAdd);
        validateAndSet(config, "npc.arena.skin-url", "https://textures.minecraft.net/texture/c2e93825cdc4c7ec014143f170ff05ef7ca5f3606716eb5429eb427bb05b7e17", autoAdd);
        logResults("config.yml");
        return errors.isEmpty();
    }

    private void validateSection(FileConfiguration config, String section, Map<String, Object> defaults, boolean autoAdd) {
        if (!config.isConfigurationSection(section)) {
            if (autoAdd) {
                for (Map.Entry<String, Object> entry : defaults.entrySet()) {
                    String path = section + "." + entry.getKey();
                    config.set(path, entry.getValue());
                    added.add("Added missing section: " + section + " with " + defaults.size() + " keys");
                }
            } else {
                errors.add("Missing section: " + section);
            }
            return;
        }

        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String path = section + "." + entry.getKey();
            if (!config.contains(path)) {
                if (autoAdd) {
                    config.set(path, entry.getValue());
                    added.add("Added: " + path + " = " + entry.getValue());
                } else {
                    warnings.add("Missing key: " + path + " (default: " + entry.getValue() + ")");
                }
            }
        }
    }

    private void validateEnchantSection(FileConfiguration config, String path, int maxTier, int basePrice, boolean autoAdd) {
        for (int tier = 1; tier <= maxTier; tier++) {
            String tierPath = path + ".tier-" + tier;
            if (!config.contains(tierPath)) {
                int defaultPrice = basePrice * tier;
                if (autoAdd) {
                    config.set(tierPath, defaultPrice);
                    added.add("Added: " + tierPath + " = " + defaultPrice);
                } else {
                    warnings.add("Missing key: " + tierPath + " (default: " + defaultPrice + ")");
                }
            }
        }
    }

    private void validateAndSet(FileConfiguration config, String path, Object defaultValue, boolean autoAdd) {
        boolean exists = config.contains(path);
        if (!exists) {
            if (autoAdd) {
                config.set(path, defaultValue);
                added.add("Added: " + path + " = " + defaultValue);
                CashClashPlugin.getInstance().getLogger().info("[CashClash] [VALIDATOR] Added missing key: " + path);
            } else {
                warnings.add("Missing key: " + path + " (default: " + defaultValue + ")");
            }
        }
    }

    private void validateAndSetList(FileConfiguration config, String path, List<String> defaultValue, boolean autoAdd) {
        if (!config.contains(path) || config.getStringList(path).isEmpty()) {
            if (autoAdd) {
                config.set(path, defaultValue);
                added.add("Added: " + path + " = [" + defaultValue.size() + " items]");
                CashClashPlugin.getInstance().getLogger().info("[CashClash] [VALIDATOR] Added missing list: " + path);
            } else {
                warnings.add("Missing key: " + path + " (default: [" + defaultValue.size() + " items])");
            }
        }
    }

    private void logResults(String fileName) {
        Logger logger = CashClashPlugin.getInstance().getLogger();

        if (errors.isEmpty() && warnings.isEmpty() && added.isEmpty()) {
            logger.info("[CashClash] ✓ " + fileName + " validated successfully (all keys present)");
            return;
        }

        if (!added.isEmpty()) {
            logger.info("[CashClash] ➕ " + fileName + " - Added " + added.size() + " missing keys:");
            for (String addedKey : added) {
                logger.info("[CashClash]   - " + addedKey);
            }
        }

        if (!warnings.isEmpty()) {
            logger.warning("[CashClash] ⚠ " + fileName + " has " + warnings.size() + " validation warnings:");
            for (String warning : warnings) {
                logger.warning("[CashClash]   - " + warning);
            }
        }

        if (!errors.isEmpty()) {
            logger.severe("[CashClash] ✗ " + fileName + " has " + errors.size() + " validation errors:");
            for (String error : errors) {
                logger.severe("[CashClash]   - " + error);
            }
        }
    }

    /**
     * Get the number of fields that were added.
     */
    public int getAddedCount() {
        return added.size();
    }

    /**
     * Log configuration differences after reload.
     */
    public void logConfigDiff(String configName, int keysChanged) {
        // Use Bukkit logger directly to avoid circular dependency with ConfigManager
        Logger logger = CashClashPlugin.getInstance().getLogger();
        String prefix = "[CashClash] ";
        if (keysChanged > 0) {
            logger.info(prefix + "↻ Reloaded " + configName + " (" + keysChanged + " values changed)");
        } else {
            logger.info(prefix + "↻ Reloaded " + configName + " (no changes)");
        }
    }
}

