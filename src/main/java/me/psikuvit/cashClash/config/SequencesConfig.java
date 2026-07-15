package me.psikuvit.cashClash.config;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages sequences.yml - titles/subtitles for scripted round/game presentation moments
 * (round start, president reveal, round end, round 4 shield transition, sudden death,
 * victory).
 */
public class SequencesConfig {

    private static SequencesConfig instance;
    private final ConfigValidator validator;
    private final Map<String, String> messages;

    private SequencesConfig() {
        this.validator = new ConfigValidator();
        this.messages = new HashMap<>();
        loadConfig();
    }

    public static SequencesConfig getInstance() {
        if (instance == null) {
            instance = new SequencesConfig();
        }
        return instance;
    }

    private void loadConfig() {
        File configFile = new File(CashClashPlugin.getInstance().getDataFolder(), "sequences.yml");

        if (!configFile.exists()) {
            CashClashPlugin.getInstance().saveResource("sequences.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Validate and auto-add missing fields
        if (!validator.validateSequencesConfig(config, true)) {
            Messages.debug("CONFIG", "Sequences configuration has errors - check warnings above");
        }

        // Save if any fields were added
        if (validator.getAddedCount() > 0) {
            try {
                config.save(configFile);
                Messages.debug("CONFIG", "Saved sequences.yml with " + validator.getAddedCount() + " new default values");
            } catch (Exception e) {
                Messages.debug("CONFIG", "Failed to save sequences.yml: " + e.getMessage());
            }
        }

        messages.clear();
        loadMessagesFromSection(config, "");
    }

    /**
     * Recursively flatten the config into dot-path keys, skipping schema-version.
     */
    private void loadMessagesFromSection(ConfigurationSection section, String path) {
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            if (path.isEmpty() && key.equals("schema-version")) continue;

            String fullKey = path.isEmpty() ? key : path + "." + key;
            Object value = section.get(key);

            if (value instanceof ConfigurationSection) {
                loadMessagesFromSection(section.getConfigurationSection(key), fullKey);
            } else if (value instanceof String) {
                messages.put(fullKey, (String) value);
            }
        }
    }

    /**
     * Get a message by key with placeholder replacement.
     * @param key Message key (e.g., "round-start.selecting")
     * @param placeholders Pairs of (placeholder_name, replacement_value)
     */
    public String getMessage(String key, Object... placeholders) {
        String message = messages.getOrDefault(key, "{MISSING_MESSAGE: " + key + "}");

        if (placeholders.length > 0 && placeholders.length % 2 == 0) {
            for (int i = 0; i < placeholders.length; i += 2) {
                String placeholderName = placeholders[i].toString();
                if (placeholderName.startsWith("{") && placeholderName.endsWith("}") && placeholderName.length() > 2) {
                    placeholderName = placeholderName.substring(1, placeholderName.length() - 1);
                }

                String placeholder = "{" + placeholderName + "}";
                String replacement = placeholders[i + 1].toString();
                message = message.replace(placeholder, replacement);
            }
        }

        return message;
    }

    /**
     * Get a message by key without placeholder replacement.
     */
    public String getRaw(String key) {
        return messages.getOrDefault(key, "{MISSING_MESSAGE: " + key + "}");
    }

    public void reload() {
        loadConfig();
        validator.logConfigDiff("sequences.yml", 0);
    }
}
