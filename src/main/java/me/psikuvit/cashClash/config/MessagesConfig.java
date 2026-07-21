package me.psikuvit.cashClash.config;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration manager for all in-game messages.
 * Loads messages from messages.yml and provides them throughout the application.
 */
public class MessagesConfig {

    private static MessagesConfig instance;
    private final Map<String, String> messages;

    private MessagesConfig() {
        this.messages = new HashMap<>();
        loadMessages();
    }

    public static MessagesConfig getInstance() {
        if (instance == null) {
            instance = new MessagesConfig();
        }
        return instance;
    }

    /**
     * Load messages from messages.yml
     */
    public void loadMessages() {
        messages.clear();

        // Get or create messages.yml
        File messagesFile = new File(CashClashPlugin.getInstance().getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            CashClashPlugin.getInstance().saveResource("messages.yml", false);
        }

        FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Merge any keys present in the bundled resource but missing from the on-disk file.
        // Unlike config.yml/shop.yml/items.yml, messages.yml is never auto-repaired by
        // ConfigValidator, so a pre-existing on-disk file silently misses new keys added to
        // the resource in later plugin versions (saveResource above only runs when the file
        // doesn't exist at all).
        int addedCount = mergeMissingKeys(messagesConfig);
        if (addedCount > 0) {
            try {
                messagesConfig.save(messagesFile);
                CashClashPlugin.getInstance().getLogger().info(
                        "[CashClash] Added " + addedCount + " missing message keys to messages.yml");
            } catch (IOException e) {
                CashClashPlugin.getInstance().getLogger().severe("Failed to save messages.yml: " + e.getMessage());
            }
        }

        // Load all messages from config
        if (messagesConfig.contains("messages")) {
            loadMessagesFromSection(messagesConfig.getConfigurationSection("messages"), "");
        }
    }

    /**
     * Merge message keys present in the bundled resource but missing from {@code messagesConfig},
     * writing defaults in-place. Returns the number of keys added.
     */
    private int mergeMissingKeys(FileConfiguration messagesConfig) {
        try (InputStream resourceStream = CashClashPlugin.getInstance().getResource("messages.yml")) {
            if (resourceStream == null) return 0;

            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resourceStream, StandardCharsets.UTF_8));

            if (!defaults.contains("messages")) return 0;

            int[] addedCount = {0};
            mergeSection(defaults.getConfigurationSection("messages"), messagesConfig, "messages", addedCount);
            return addedCount[0];
        } catch (IOException e) {
            CashClashPlugin.getInstance().getLogger().warning(
                    "[CashClash] Failed to merge default messages.yml keys: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Recursively copy leaf keys from {@code defaultsSection} into {@code target} at
     * {@code path} wherever {@code target} doesn't already have them.
     */
    private void mergeSection(ConfigurationSection defaultsSection, FileConfiguration target, String path, int[] addedCount) {
        if (defaultsSection == null) return;

        for (String key : defaultsSection.getKeys(false)) {
            String fullPath = path + "." + key;
            Object value = defaultsSection.get(key);

            if (value instanceof ConfigurationSection) {
                mergeSection(defaultsSection.getConfigurationSection(key), target, fullPath, addedCount);
            } else if (!target.contains(fullPath)) {
                target.set(fullPath, value);
                addedCount[0]++;
            }
        }
    }

    /**
     * Recursively load messages from configuration section
     */
    private void loadMessagesFromSection(ConfigurationSection section, String path) {
        if (section == null) return;

        for (String key : section.getKeys(false)) {
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
     * <p><b>Intended use:</b> only {@link me.psikuvit.cashClash.util.Messages#send}
     * (and its broadcast helpers) should call this; callers should use
     * {@code Messages.send(..., key, placeholder, value, ...)} instead.</p>
     *
     * @param key Message key (e.g., "chat.switched-to-global")
     * @param placeholders Pairs of (placeholder_name, replacement_value)
     * @return The message with placeholders replaced, or a default if not found
     */
    public String getMessage(String key, Object... placeholders) {
        String message = messages.getOrDefault(key, "{MISSING_MESSAGE: " + key + "}");

        // Replace placeholders
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
     * <p><b>Intended use:</b> only {@link me.psikuvit.cashClash.util.Messages}
     * (e.g. {@code send} with no placeholder args, {@link me.psikuvit.cashClash.util.Messages#commandPrefix}) should call this.</p>
     *
     * @param key Message key
     * @return The raw message value
     */
    public String getRaw(String key) {
        return messages.getOrDefault(key, "{MISSING_MESSAGE: " + key + "}");
    }

    /**
     * Check if a message key exists
     */
    public boolean hasMessage(String key) {
        return messages.containsKey(key);
    }

    /**
     * Reload messages from file
     */
    public void reload() {
        loadMessages();
        CashClashPlugin.getInstance().getLogger().info("Messages configuration reloaded.");
    }

    /**
     * Get all messages (for debugging)
     */
    public Map<String, String> getAllMessages() {
        return new HashMap<>(messages);
    }
}

