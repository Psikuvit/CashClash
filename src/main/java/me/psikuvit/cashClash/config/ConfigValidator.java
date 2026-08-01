package me.psikuvit.cashClash.config;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Validates configuration files against the bundled (jar) resource files.
 * <p>
 * The bundled resources ({@code config.yml}, {@code items.yml}, {@code shop.yml},
 * {@code sequences.yml}) are the single source of truth for defaults. Any key the
 * resource contains but the on-disk file lacks is added automatically, so new feature
 * settings always appear on existing installs after a reload.
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
        return validateAgainstResource(config, "shop.yml", autoAdd);
    }

    /**
     * Validate items.yml configuration.
     * @param config The config to validate
     * @param autoAdd If true, missing keys will be added with defaults
     * @return true if valid, false if critical errors found
     */
    public boolean validateItemsConfig(FileConfiguration config, boolean autoAdd) {
        return validateAgainstResource(config, "items.yml", autoAdd);
    }

    /**
     * Validate config.yml configuration.
     * @param config The config to validate
     * @param autoAdd If true, missing keys will be added with defaults
     * @return true if valid, false if critical errors found
     */
    public boolean validateMainConfig(FileConfiguration config, boolean autoAdd) {
        return validateAgainstResource(config, "config.yml", autoAdd);
    }

    /**
     * Validate sequences.yml configuration.
     * @param config The config to validate
     * @param autoAdd If true, missing keys will be added with defaults
     * @return true if valid, false if critical errors found
     */
    public boolean validateSequencesConfig(FileConfiguration config, boolean autoAdd) {
        return validateAgainstResource(config, "sequences.yml", autoAdd);
    }

    private boolean validateAgainstResource(FileConfiguration config, String resourcePath, boolean autoAdd) {
        errors.clear();
        warnings.clear();
        added.clear();

        FileConfiguration resource = ConfigMergeUtil.loadBundledResource(resourcePath);
        if (resource == null) {
            errors.add("Bundled resource '" + resourcePath + "' not found - cannot auto-add defaults");
            logResults(resourcePath);
            return false;
        }

        if (autoAdd) {
            for (String path : ConfigMergeUtil.mergeAllMissing(config, resource)) {
                added.add("Added: " + path);
            }
        } else {
            collectMissing(config, resource, "");
        }

        logResults(resourcePath);
        return errors.isEmpty();
    }

    private void collectMissing(FileConfiguration config, ConfigurationSection section, String path) {
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            Object value = section.get(key);

            if (value instanceof ConfigurationSection) {
                collectMissing(config, section.getConfigurationSection(key), fullPath);
            } else if (!config.contains(fullPath)) {
                warnings.add("Missing key: " + fullPath + " (default: " + value + ")");
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
}
