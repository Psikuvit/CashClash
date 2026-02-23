package me.psikuvit.cashClash.util.items;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Configuration-based implementation of ItemLoreProvider.
 * Loads lore from items.yml and applies MiniMessage formatting.
 */
public class ConfigurableItemLoreProvider implements ItemLoreProvider {

    private static ConfigurableItemLoreProvider instance;
    private final ItemsConfig config;

    private ConfigurableItemLoreProvider() {
        this.config = ItemsConfig.getInstance();
    }

    public static ConfigurableItemLoreProvider getInstance() {
        if (instance == null) {
            instance = new ConfigurableItemLoreProvider();
        }
        return instance;
    }

    @Override
    public List<Component> getLore(String category, String itemKey) {
        List<String> loreLinesRaw = config.getItemLore(category, itemKey);

        if (loreLinesRaw.isEmpty()) {
            return List.of();
        }

        // Parse each line with MiniMessage formatting
        return loreLinesRaw.stream()
                .map(Messages::parse)
                .toList();
    }

    @Override
    public Component getDescription(String category, String itemKey) {
        String descriptionRaw = config.getItemDescription(category, itemKey);

        if (descriptionRaw == null || descriptionRaw.isEmpty()) {
            return Component.empty();
        }

        return Messages.parse(descriptionRaw);
    }
}

