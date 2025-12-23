package me.psikuvit.cashClash.config;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Validates configuration files and ensures all required keys exist.
 * Logs warnings for missing keys and provides defaults.
 */
public class ConfigValidator {

    private final Logger logger;
    private final List<String> errors;
    private final List<String> warnings;

    public ConfigValidator() {
        this.logger = CashClashPlugin.getInstance().getLogger();
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    /**
     * Validate shop.yml configuration.
     * @return true if valid, false if critical errors found
     */
    public boolean validateShopConfig(FileConfiguration config) {
        errors.clear();
        warnings.clear();

        logger.info("Validating shop.yml...");

        // Check schema version
        int schemaVersion = config.getInt("schema-version", 0);
        if (schemaVersion == 0) {
            warnings.add("Missing 'schema-version' key - assuming version 1");
        } else if (schemaVersion > 1) {
            errors.add("Unsupported schema-version: " + schemaVersion + " (expected 1)");
        }

        // Validate armor section
        validateSection(config, "armor", List.of(
            "iron-boots", "iron-helmet", "iron-leggings", "iron-chestplate",
            "diamond-boots", "diamond-helmet", "diamond-leggings", "diamond-chestplate",
            "netherite-boots", "netherite-helmet", "netherite-leggings", "netherite-chestplate"
        ));

        // Validate weapons section
        validateSection(config, "weapons", List.of(
            "wooden-sword", "stone-sword", "iron-sword", "diamond-sword", "netherite-sword",
            "wooden-axe", "stone-axe", "iron-axe", "diamond-axe", "netherite-axe"
        ));

        // Validate food section
        validateSection(config, "food", List.of(
            "bread", "cooked-mutton", "steak", "porkchop", "golden-carrot",
            "golden-apple", "enchanted-golden-apple"
        ));

        // Validate utility section
        validateSection(config, "utility", List.of(
            "lava-bucket", "water-bucket", "cobweb", "crossbow", "bow",
            "fishing-rod", "ender-pearl", "wind-charge"
        ));

        // Validate custom items section
        validateSection(config, "custom-items", List.of(
            "grenade", "smoke-grenade", "bounce-pad", "medic-pouch",
            "tablet-of-hacking", "bag-of-potatoes", "boombox", "invis-cloak",
            "cash-blaster", "respawn-anchor"
        ));

        // Validate custom armor section
        validateSection(config, "custom-armor", List.of(
            "magic-helmet", "guardians-vest", "tax-evasion-pants", "bunny-shoes",
            "investors-helmet", "investors-chestplate", "investors-leggings", "investors-boots"
        ));

        // Validate mythic items section
        validateSection(config, "mythic-items", List.of(
            "coin-cleaver", "carls-battleaxe", "wind-bow", "electric-eel-sword",
            "goblin-spear", "sandstormer", "warden-gloves", "blazebite-crossbows"
        ));

        // Validate enchantments section
        validateEnchantSection(config, "enchants.protection", 4);
        validateEnchantSection(config, "enchants.sharpness", 5);
        validateEnchantSection(config, "enchants.power", 5);
        validateEnchantSection(config, "enchants.knockback", 2);
        validateEnchantSection(config, "enchants.punch", 2);
        validateEnchantSection(config, "enchants.fire-aspect", 2);
        validateEnchantSection(config, "enchants.flame", 1);
        validateEnchantSection(config, "enchants.unbreaking", 3);

        logResults("shop.yml");
        return errors.isEmpty();
    }

    /**
     * Validate items.yml configuration.
     * @return true if valid, false if critical errors found
     */
    public boolean validateItemsConfig(FileConfiguration config) {
        errors.clear();
        warnings.clear();

        logger.info("Validating items.yml...");

        // Check schema version
        int schemaVersion = config.getInt("schema-version", 0);
        if (schemaVersion == 0) {
            warnings.add("Missing 'schema-version' key - assuming version 1");
        }

        // Validate mythic items section
        validatePath(config, "mythic-items.legendaries-per-game");
        validatePath(config, "mythic-items.coin-cleaver.damage-bonus-multiplier");
        validatePath(config, "mythic-items.coin-cleaver.grenade.cooldown-seconds");
        validatePath(config, "mythic-items.carls-battleaxe.charged-attack.cooldown-seconds");
        validatePath(config, "mythic-items.wind-bow.boost.cooldown-seconds");
        validatePath(config, "mythic-items.electric-eel.chain-damage.cooldown-seconds");
        validatePath(config, "mythic-items.goblin-spear.throw.cooldown-seconds");
        validatePath(config, "mythic-items.sandstormer.burst-shots");
        validatePath(config, "mythic-items.warden-gloves.shockwave.cooldown-seconds");
        validatePath(config, "mythic-items.blazebite.shots-per-magazine");

        // Validate custom items section
        validatePath(config, "custom-items.grenade.fuse-seconds");
        validatePath(config, "custom-items.bounce-pad.forward-velocity");
        validatePath(config, "custom-items.medic-pouch.cooldown-seconds");
        validatePath(config, "custom-items.boombox.pulse-interval-seconds");
        validatePath(config, "custom-items.invis-cloak.cost-per-second");
        validatePath(config, "custom-items.cash-blaster.coins-per-hit");
        validatePath(config, "custom-items.respawn-anchor.revive-duration-seconds");

        // Validate custom armor section
        validatePath(config, "custom-armor.magic-helmet.stand-still-delay-seconds");
        validatePath(config, "custom-armor.bunny-shoes.duration-seconds");
        validatePath(config, "custom-armor.deathmauler.absorption-delay-seconds");
        validatePath(config, "custom-armor.dragon.double-jump-cooldown-seconds");

        logResults("items.yml");
        return errors.isEmpty();
    }

    private void validateSection(FileConfiguration config, String section, List<String> keys) {
        if (!config.isConfigurationSection(section)) {
            errors.add("Missing section: " + section);
            return;
        }

        for (String key : keys) {
            String path = section + "." + key;
            if (!config.contains(path)) {
                warnings.add("Missing key: " + path + " (will use default: 0)");
            } else if (config.getLong(path, -1) < 0) {
                warnings.add("Invalid value for " + path + ": " + config.get(path));
            }
        }
    }

    private void validateEnchantSection(FileConfiguration config, String path, int maxTier) {
        if (!config.isConfigurationSection(path)) {
            warnings.add("Missing enchantment section: " + path);
            return;
        }

        for (int tier = 1; tier <= maxTier; tier++) {
            String tierPath = path + ".tier-" + tier;
            if (!config.contains(tierPath)) {
                warnings.add("Missing key: " + tierPath);
            }
        }
    }

    private void validatePath(FileConfiguration config, String path) {
        if (!config.contains(path)) {
            warnings.add("Missing key: " + path + " (will use default)");
        }
    }

    private void logResults(String fileName) {
        if (errors.isEmpty() && warnings.isEmpty()) {
            logger.info("✓ " + fileName + " validated successfully");
            return;
        }

        if (!warnings.isEmpty()) {
            logger.warning("⚠ " + fileName + " validation warnings:");
            warnings.forEach(w -> logger.warning("  - " + w));
        }

        if (!errors.isEmpty()) {
            logger.severe("✗ " + fileName + " validation errors:");
            errors.forEach(e -> logger.severe("  - " + e));
        }
    }

    /**
     * Log configuration differences after reload.
     */
    public void logConfigDiff(String configName, int keysChanged) {
        if (keysChanged > 0) {
            logger.info("↻ Reloaded " + configName + " (" + keysChanged + " values changed)");
        } else {
            logger.info("↻ Reloaded " + configName + " (no changes)");
        }
    }
}

