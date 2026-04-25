package me.psikuvit.cashClash.config;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration manager for all in-game messages.
 * Loads messages from messages.yml and provides them throughout the application.
 */
public class MessagesConfig {

    private static MessagesConfig instance;
    private final Map<String, String> messages;
    private File messagesFile;
    private FileConfiguration messagesConfig;

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
        messagesFile = new File(CashClashPlugin.getInstance().getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            CashClashPlugin.getInstance().saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Load all messages from config
        if (messagesConfig.contains("messages")) {
            loadMessagesFromSection(messagesConfig.getConfigurationSection("messages"), "");
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

