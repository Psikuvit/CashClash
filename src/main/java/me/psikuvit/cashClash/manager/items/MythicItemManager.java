package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.CustomModelDataMapper;
import me.psikuvit.cashClash.util.items.PDCDetection;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages Mythic (Legendary) item behaviors, cooldowns, and special abilities.
 * Each player can only purchase ONE mythic per game.
 * Each mythic can only be purchased ONCE per game (globally).
 */
public class MythicItemManager {

    private static MythicItemManager instance;
    private final ItemsConfig cfg;
    private final CooldownManager cooldownManager;

    private final Map<UUID, Map<UUID, MythicItem>> playerMythics;
    private final Map<UUID, Set<MythicItem>> sessionPurchasedMythics;
    private final Map<UUID, List<MythicItem>> sessionAvailableMythics;

    // BlazeBite shots tracking (shared between both crossbows)
    private final Map<UUID, Integer> blazebiteShotsRemaining;

    // BlazeBite Glacier frozen players tracking (UUID -> expiration timestamp)
    private final Map<UUID, Long> glacierFrozenPlayers;

    // Goblin Spear shots tracking
    private final Map<UUID, Integer> goblinSpearShotsRemaining;

    // Goblin Spear charge state tracking (player -> list of caught players)
    private final Map<UUID, List<Player>> goblinSpearCharging;

    // BloodWrench mode tracking (true = rapid fire, false = supercharged)
    private final Map<UUID, Boolean> bloodwrenchRapidMode;

    // BloodWrench rapid fire shots remaining (must fire all 3 before switching)
    private final Map<UUID, Integer> bloodwrenchRapidShotsRemaining;

    // BloodWrench rapid fire in progress (cannot switch modes while firing)
    private final Set<UUID> bloodwrenchRapidFiring;

    // Active tasks for cleanup
    private final Map<UUID, List<BukkitTask>> activeTasks;

    // Warden Gloves boxing punch counter (UUID -> punch count)
    private final Map<UUID, Integer> wardenPunchCount;
    // Warden Gloves boxing ability active (UUID -> true if ability is active)
    private final Set<UUID> wardenBoxingActive;

    // Glacier frostbite particle tasks (UUID -> particle task)
    private final Map<UUID, BukkitTask> glacierFrostbiteParticleTasks;

    // Wind Bow shots tracking (10 shots then reload cooldown)
    private final Map<UUID, Integer> windBowShotsRemaining;

    private MythicItemManager() {
        cfg = ItemsConfig.getInstance();
        cooldownManager = CooldownManager.getInstance();
        playerMythics = new HashMap<>();
        sessionPurchasedMythics = new HashMap<>();
        sessionAvailableMythics = new HashMap<>();
        blazebiteShotsRemaining = new HashMap<>();
        glacierFrozenPlayers = new HashMap<>();
        goblinSpearShotsRemaining = new HashMap<>();
        goblinSpearCharging = new HashMap<>();
        bloodwrenchRapidMode = new HashMap<>();
        bloodwrenchRapidShotsRemaining = new HashMap<>();
        bloodwrenchRapidFiring = new HashSet<>();
        activeTasks = new HashMap<>();
        wardenPunchCount = new HashMap<>();
        wardenBoxingActive = new HashSet<>();
        glacierFrostbiteParticleTasks = new HashMap<>();
        windBowShotsRemaining = new HashMap<>();
    }

    public static MythicItemManager getInstance() {
        if (instance == null) {
            instance = new MythicItemManager();
        }
        return instance;
    }


    // ==================== PURCHASE & OWNERSHIP ====================

    /**
     * Check if a player has already purchased a mythic (only one per player per game).
     * @return true if player already owns a mythic
     */
    public boolean hasPlayerPurchasedMythic(GameSession session, UUID playerUuid) {
        if (session == null || playerUuid == null) return false;
        UUID sessionId = session.getSessionId();
        Map<UUID, MythicItem> sessionPlayerMythics = playerMythics.get(sessionId);
        if (sessionPlayerMythics == null) return false;
        return sessionPlayerMythics.containsKey(playerUuid);
    }

    /**
     * Check if a specific mythic has been purchased by anyone.
     * @return true if mythic is already purchased
     */
    public boolean isMythicPurchased(GameSession session, MythicItem mythic) {
        if (session == null || mythic == null) return false;
        UUID sessionId = session.getSessionId();
        Set<MythicItem> purchased = sessionPurchasedMythics.get(sessionId);
        if (purchased == null) return false;
        return purchased.contains(mythic);
    }

    /**
     * Get the player who owns a specific mythic, if any.
     */
    public UUID getMythicOwner(GameSession session, MythicItem mythic) {
        if (session == null || mythic == null) return null;
        UUID sessionId = session.getSessionId();

        Map<UUID, MythicItem> sessionPlayerMythics = playerMythics.get(sessionId);
        if (sessionPlayerMythics == null) return null;

        for (Map.Entry<UUID, MythicItem> entry : sessionPlayerMythics.entrySet()) {
            if (entry.getValue() == mythic) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Register that a player has purchased a mythic.
     */
    public void registerMythicPurchase(GameSession session, UUID playerUuid, MythicItem mythic) {
        if (session == null || playerUuid == null || mythic == null) return;
        UUID sessionId = session.getSessionId();
        playerMythics.computeIfAbsent(sessionId, k -> new HashMap<>())
                   .put(playerUuid, mythic);
        sessionPurchasedMythics.computeIfAbsent(sessionId, k -> new HashSet<>())
                               .add(mythic);
    }

    /**
     * Get the mythic owned by a player, if any.
     */
    public MythicItem getPlayerMythic(GameSession session, UUID playerUuid) {
        if (session == null || playerUuid == null) return null;
        UUID sessionId = session.getSessionId();

        Map<UUID, MythicItem> sessionPlayerMythics = playerMythics.get(sessionId);
        if (sessionPlayerMythics == null) return null;
        return sessionPlayerMythics.getOrDefault(playerUuid, null);
    }

    // ==================== RANDOM LEGENDARY SELECTION ====================

    /**
     * Select 5 random legendaries for a game session.
     * Should be called when the game starts.
     */
    public void selectLegendariesForSession(GameSession session) {
        if (session == null) return;
        UUID sessionId = session.getSessionId();

        // Get all mythic items and shuffle them
        List<MythicItem> allMythics = new ArrayList<>(Arrays.asList(MythicItem.values()));
        Collections.shuffle(allMythics, ThreadLocalRandom.current());

        // Select first 5 (or all if less than 5 exist)
        int count = Math.min(ItemsConfig.getInstance().getLegendsPerGame(), allMythics.size());
        List<MythicItem> selectedMythics = new ArrayList<>(allMythics.subList(0, count));

        sessionAvailableMythics.put(sessionId, selectedMythics);

        Messages.debug("MYTHIC", "Selected " + count + " legendaries for session " + sessionId + ": " +
            selectedMythics.stream().map(MythicItem::getDisplayName).reduce((a, b) -> a + ", " + b).orElse("none")
        );
    }

    /**
     * Get the available legendaries for a game session.
     * Returns an empty list if none have been selected.
     */
    public List<MythicItem> getAvailableMythics(GameSession session) {
        if (session == null) return Collections.emptyList();
        UUID sessionId = session.getSessionId();
        return sessionAvailableMythics.getOrDefault(sessionId, Collections.emptyList());
    }

    private static final int COIN_CLEAVER_HITS_REQUIRED = 10;

    /**
     * Create the mythic item with proper tags and appearance.
     * For BlazeBite, use createBlazebiteBundle() instead to get both crossbows.
     */
    public ItemStack createMythicItem(MythicItem mythic, Player owner) {
        // For BlazeBite, return just the Glacier crossbow (use createBlazebiteBundle for both)
        if (mythic == MythicItem.BLAZEBITE_CROSSBOWS) {
            return createBlazebiteItem(owner, true); // Default to Glacier
        }

        ItemStack item = new ItemStack(mythic.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Display name with mythic color
        meta.displayName(Messages.parse("<light_purple><bold>" + mythic.getDisplayName() + "</bold></light_purple>"));

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Messages.parse("<dark_purple>✦ MYTHIC WEAPON ✦</dark_purple>"));
        lore.add(Component.empty());
        lore.addAll(Messages.wrapLines(mythic.getDescription()));
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Owner: " + owner.getName() + "</gray>"));
        meta.lore(lore);

        // PDC tags
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.ITEM_ID, PersistentDataType.STRING, mythic.name());
        pdc.set(Keys.ITEM_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());

        // Apply special attributes based on mythic type
        applyMythicAttributes(mythic, meta);

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);

        // Apply custom model data using string key for resource pack
        CustomModelDataMapper.applyCustomModel(item, mythic);

        return item;
    }

    /**
     * Create the BlazeBite crossbow bundle - returns array of [Glacier, Volcano] crossbows.
     */
    public ItemStack[] createBlazebiteBundle(Player owner) {
        return new ItemStack[] {
            createBlazebiteItem(owner, true),  // Glacier
            createBlazebiteItem(owner, false)  // Volcano
        };
    }

    /**
     * Create a single BlazeBite crossbow (Glacier or Volcano).
     */
    private ItemStack createBlazebiteItem(Player owner, boolean isGlacier) {
        ItemStack item = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = isGlacier ? "Glacier Crossbow" : "Volcano Crossbow";
        String color = isGlacier ? "<aqua>" : "<red>";

        meta.displayName(Messages.parse("<light_purple><bold>" + name + "</bold></light_purple>"));

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Messages.parse("<dark_purple>✦ MYTHIC WEAPON ✦</dark_purple>"));
        lore.add(Component.empty());
        if (isGlacier) {
            lore.add(Messages.parse(color + "Glacier Mode: Arrows inflict Slowness I"));
            lore.add(Messages.parse(color + "and Frostbite for 3 seconds."));
        } else {
            lore.add(Messages.parse(color + "Volcano Mode: Explosive fire arrows!"));
            lore.add(Messages.parse(color + "2 hearts direct, 1 heart splash (3 blocks)."));
        }
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>8 shots per magazine, 25s reload</gray>"));
        lore.add(Messages.parse("<gray>Owner: " + owner.getName() + "</gray>"));
        meta.lore(lore);

        // PDC tags - mark as BlazeBite and store mode
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.ITEM_ID, PersistentDataType.STRING, MythicItem.BLAZEBITE_CROSSBOWS.name());
        pdc.set(Keys.ITEM_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());
        pdc.set(Keys.BLAZEBITE_MODE, PersistentDataType.STRING, isGlacier ? "glacier" : "volcano");

        // Apply enchantments - Piercing 3, Quick Charge 1
        meta.addEnchant(Enchantment.PIERCING, 3, true);
        meta.addEnchant(Enchantment.QUICK_CHARGE, 1, true);

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);

        return item;
    }

    private void applyMythicAttributes(MythicItem mythic, ItemMeta meta) {
        switch (mythic) {
            case GOBLIN_SPEAR -> {
                NamespacedKey speedKey = new NamespacedKey(CashClashPlugin.getInstance(), "goblin_speed");
                AttributeModifier speedMod = new AttributeModifier(
                        speedKey,
                        0.8, // Much faster attack speed
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                );
                meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedMod);

                // +1 block range
                NamespacedKey rangeKey = new NamespacedKey(CashClashPlugin.getInstance(), "goblin_range");
                AttributeModifier rangeMod = new AttributeModifier(
                        rangeKey,
                        1.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                );
                meta.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE, rangeMod);
                meta.addEnchant(Enchantment.LOYALTY, 3, true);
            }
            case WARDEN_GLOVES -> {
                // +2 block reach for both hands (takes up main and off hand conceptually)
                NamespacedKey reachKey = new NamespacedKey(CashClashPlugin.getInstance(), "warden_reach");
                AttributeModifier reachMod = new AttributeModifier(
                        reachKey,
                        2.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                );
                meta.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE, reachMod);
            }
            case BLOODWRENCH_CROSSBOW -> {
                // No enchantments - mode system handles functionality
            }
            default -> {
            }
        }
    }

    // ==================== COIN CLEAVER ====================
    private static final int COIN_CLEAVER_NO_KB_DURATION_SECONDS = 15;
    private static final int COIN_CLEAVER_MAX_USES_PER_ROUND = 3;
    // Track charged hits for No KB ability (UUID -> hit count)
    private final Map<UUID, Integer> coinCleaverChargedHits = new HashMap<>();
    // Track No KB uses this round (UUID -> uses remaining, max 3)
    private final Map<UUID, Integer> coinCleaverNoKBUsesRemaining = new HashMap<>();
    // Track players currently with active No KB buff
    private final Map<UUID, Long> coinCleaverNoKBActiveUntil = new HashMap<>();
    // Track players currently in spin attack
    private final Set<UUID> spinningPlayers = new HashSet<>();

    /**
     * Check if a specific mythic is available in this session.
     */
    public boolean isUnavailable(GameSession session, MythicItem mythic) {
        if (session == null || mythic == null) return true;
        List<MythicItem> available = getAvailableMythics(session);
        return !available.contains(mythic);
    }

    /**
     * Handle Coin Cleaver damage bonus against richer players.
     * +25% damage if victim has more coins than attacker.
     * Also tracks fully charged hits for the No KB ability.
     */
    public double handleCoinCleaverDamage(Player attacker, Player victim, double baseDamage, boolean isFullyCharged) {
        Messages.debug(attacker, "COIN_CLEAVER: Checking damage bonus...");

        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
        if (session == null) {
            Messages.debug(attacker, "COIN_CLEAVER: No session found");
            return baseDamage;
        }

        CashClashPlayer attackerCcp = session.getCashClashPlayer(attacker.getUniqueId());
        CashClashPlayer victimCcp = session.getCashClashPlayer(victim.getUniqueId());

        if (attackerCcp == null || victimCcp == null) {
            Messages.debug(attacker, "COIN_CLEAVER: Missing player data");
            return baseDamage;
        }

        // Track fully charged hits for No KB ability
        if (isFullyCharged) {
            trackCoinCleaverChargedHit(attacker);
        }

        if (victimCcp.getCoins() > attackerCcp.getCoins()) {
            double newDamage = baseDamage * ItemsConfig.getInstance().getCoinCleaverDamageBonus();
            Messages.debug(attacker, "COIN_CLEAVER: +25% damage! (" + baseDamage + " -> " + newDamage + ") Victim has more coins");
            return newDamage;
        }
        Messages.debug(attacker, "COIN_CLEAVER: No bonus - you have more coins");
        return baseDamage;
    }

    /**
     * Track a fully charged hit with Coin Cleaver.
     * After 10 hits, grants No KB for 15 seconds (max 3 uses per round).
     */
    private void trackCoinCleaverChargedHit(Player player) {
        UUID uuid = player.getUniqueId();

        // Check if player has uses remaining this round
        int usesRemaining = coinCleaverNoKBUsesRemaining.getOrDefault(uuid, COIN_CLEAVER_MAX_USES_PER_ROUND);
        if (usesRemaining <= 0) {
            Messages.debug(player, "COIN_CLEAVER: No KB uses exhausted for this round");
            return;
        }

        // Check if already has active No KB
        if (hasCoinCleaverNoKBActive(uuid)) {
            Messages.debug(player, "COIN_CLEAVER: Already has active No KB buff");
            return;
        }

        // Increment hit count
        int hits = coinCleaverChargedHits.getOrDefault(uuid, 0) + 1;
        coinCleaverChargedHits.put(uuid, hits);

        Messages.debug(player, "COIN_CLEAVER: Charged hit! " + hits + "/" + COIN_CLEAVER_HITS_REQUIRED);

        // Check if reached threshold
        if (hits >= COIN_CLEAVER_HITS_REQUIRED) {
            activateCoinCleaverNoKB(player);
        } else if (hits == COIN_CLEAVER_HITS_REQUIRED - 3) {
            // Warn player they're close
            Messages.send(player, "<yellow>Coin Cleaver: " + (COIN_CLEAVER_HITS_REQUIRED - hits) + " more charged hits for No KB!</yellow>");
        }
    }

    /**
     * Activate the No KB buff for a player.
     */
    private void activateCoinCleaverNoKB(Player player) {
        UUID uuid = player.getUniqueId();

        // Reset hit counter
        coinCleaverChargedHits.put(uuid, 0);

        // Decrement uses remaining
        int usesRemaining = coinCleaverNoKBUsesRemaining.getOrDefault(uuid, COIN_CLEAVER_MAX_USES_PER_ROUND);
        coinCleaverNoKBUsesRemaining.put(uuid, usesRemaining - 1);

        // Set active until timestamp
        long activeUntil = System.currentTimeMillis() + (COIN_CLEAVER_NO_KB_DURATION_SECONDS * 1000L);
        coinCleaverNoKBActiveUntil.put(uuid, activeUntil);

        int usesLeft = usesRemaining - 1;
        Messages.send(player, "<gold><bold>COIN CLEAVER: NO KNOCKBACK ACTIVATED!</bold></gold>");
        Messages.send(player, "<yellow>Duration: " + COIN_CLEAVER_NO_KB_DURATION_SECONDS + "s | Uses remaining: " + usesLeft + "/" + COIN_CLEAVER_MAX_USES_PER_ROUND + "</yellow>");

        SoundUtils.play(player, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
        ParticleUtils.totem(player.getLocation().add(0, 1, 0), 30, 0.5);

        Messages.debug(player, "COIN_CLEAVER: No KB activated! " + COIN_CLEAVER_NO_KB_DURATION_SECONDS + "s, " + usesLeft + " uses left this round");

        // Schedule expiration message
        SchedulerUtils.runTaskLater(() -> {
            if (player.isOnline()) {
                Messages.send(player, "<gray>Coin Cleaver No KB expired.</gray>");
                if (usesLeft > 0) {
                    Messages.send(player, "<gray>Land " + COIN_CLEAVER_HITS_REQUIRED + " more charged hits for another use.</gray>");
                } else {
                    Messages.send(player, "<red>No more No KB uses available this round.</red>");
                }
            }
        }, COIN_CLEAVER_NO_KB_DURATION_SECONDS * 20L);
    }

    /**
     * Check if player currently has active No KB from charged hits.
     */
    private boolean hasCoinCleaverNoKBActive(UUID uuid) {
        Long activeUntil = coinCleaverNoKBActiveUntil.get(uuid);
        if (activeUntil == null) return false;
        return System.currentTimeMillis() < activeUntil;
    }

    /**
     * Check if player should have no knockback.
     * Returns true if player has earned No KB buff from 10 charged hits.
     */
    public boolean hasCoinCleaverNoKnockback(Player player) {
        UUID uuid = player.getUniqueId();

        // Check if player has Coin Cleaver equipped
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        boolean hasCleaver = PDCDetection.getMythic(mainHand) == MythicItem.COIN_CLEAVER ||
               PDCDetection.getMythic(offHand) == MythicItem.COIN_CLEAVER;

        if (!hasCleaver) {
            return false;
        }

        // Check if has active No KB buff
        if (hasCoinCleaverNoKBActive(uuid)) {
            Messages.debug(player, "COIN_CLEAVER: No KB active - knockback prevented");
            return true;
        }

        return false;
    }

    /**
     * Reset Coin Cleaver tracking for a player (call on round start/end).
     */
    public void resetCoinCleaverTracking(UUID playerId) {
        coinCleaverChargedHits.remove(playerId);
        coinCleaverNoKBUsesRemaining.remove(playerId);
        coinCleaverNoKBActiveUntil.remove(playerId);
        Messages.debug("COIN_CLEAVER: Reset tracking for " + playerId);
    }

    /**
     * Reset Coin Cleaver tracking for all players in a session (call on round end).
     */
    public void resetCoinCleaverTrackingForSession(GameSession session) {
        for (UUID playerId : session.getPlayers()) {
            resetCoinCleaverTracking(playerId);
        }
    }

    /**
     * Get the current charged hit count for a player.
     */
    public int getCoinCleaverChargedHits(UUID playerId) {
        return coinCleaverChargedHits.getOrDefault(playerId, 0);
    }

    /**
     * Coin Cleaver right-click grenade.
     * Instant grenade at feet dealing 2 hearts in 5 block radius.
     * Costs $2,000, 3 second cooldown.
     */
    public void useCoinCleaverGrenade(Player player) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "COIN_CLEAVER: Grenade ability triggered");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.COIN_CLEAVER_GRENADE)) {
            Messages.debug(player, "COIN_CLEAVER: On cooldown - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.COIN_CLEAVER_GRENADE) + "s remaining");
            Messages.send(player, "<red>Grenade on cooldown! (" + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.COIN_CLEAVER_GRENADE) + "s)</red>");
            return;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.debug(player, "COIN_CLEAVER: No session found");
            return;
        }

        CashClashPlayer ccp = session.getCashClashPlayer(uuid);
        if (ccp == null || ccp.getCoins() < cfg.getCoinCleaverGrenadeCost()) {
            Messages.debug(player, "COIN_CLEAVER: Not enough coins - need " + cfg.getCoinCleaverGrenadeCost());
            Messages.send(player, "<red>Not enough coins! (Costs $" + String.format("%,d", cfg.getCoinCleaverGrenadeCost()) + ")</red>");
            return;
        }

        ccp.deductCoins(cfg.getCoinCleaverGrenadeCost());
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.COIN_CLEAVER_GRENADE, cfg.getCoinCleaverGrenadeCooldown());

        Messages.debug(player, "COIN_CLEAVER: Grenade fired! Cost: $" + cfg.getCoinCleaverGrenadeCost() + ", Cooldown: " + cfg.getCoinCleaverGrenadeCooldown() + "s");

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        // Instant explosion at feet
        ParticleUtils.explosion(loc);
        SoundUtils.playAt(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

        Team playerTeam = session.getPlayerTeam(player);
        int radius = cfg.getCoinCleaverGrenadeRadius();
        int hitCount = 0;

        // Damage enemies in radius (does NOT hurt player or teammates)
        for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;

            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam != null && playerTeam != null &&
                targetTeam.getTeamNumber() == playerTeam.getTeamNumber()) continue;

            target.damage(cfg.getCoinCleaverGrenadeDamage(), player);
            hitCount++;
        }

        Messages.debug(player, "COIN_CLEAVER: Grenade hit " + hitCount + " enemies");

        Messages.send(player, "<gold>-$" + String.format("%,d", cfg.getCoinCleaverGrenadeCost()) + " for grenade!</gold>");
    }

    // ==================== CARL'S BATTLEAXE ====================

    /**
     * Get the remaining No KB uses for a player this round.
     */
    public int getCoinCleaverNoKBUsesRemaining(UUID playerId) {
        return coinCleaverNoKBUsesRemaining.getOrDefault(playerId, COIN_CLEAVER_MAX_USES_PER_ROUND);
    }

    /**
     * Activate Carl's Battleaxe spinning attack.
     * Player spins the axe around their body, slowed down, dealing high damage to nearby enemies.
     * Includes a spinning Item Display visual effect.
     */
    public void activateCarlsSpinAttack(Player attacker) {
        UUID uuid = attacker.getUniqueId();

        Messages.debug(attacker, "CARLS_BATTLEAXE: Spin attack activated");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_SLASH)) {
            Messages.debug(attacker, "CARLS_BATTLEAXE: Spin attack on cooldown - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_SLASH) + "s");
            Messages.send(attacker, "<red>Spin attack on cooldown! (" + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_SLASH) + "s)</red>");
            return;
        }

        if (spinningPlayers.contains(uuid)) {
            Messages.send(attacker, "<red>You're already spinning!</red>");
            return;
        }

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_SLASH, cfg.getCarlsSpinCooldown());
        spinningPlayers.add(uuid);

        // Apply slowness during spin
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, cfg.getCarlsSpinDuration(), 1, false, false));

        Messages.send(attacker, "<gold>Carl's Battleaxe: SPIN ATTACK!</gold>");
        SoundUtils.play(attacker, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

        // Spawn the spinning axe display
        ItemDisplay axeDisplay = spawnSpinningAxeDisplay(attacker);

        // Start the spin attack runnable
        final int duration = cfg.getCarlsSpinDuration();
        final double damage = cfg.getCarlsSpinDamage();
        final double radius = cfg.getCarlsSpinRadius();
        final int hitInterval = cfg.getCarlsSpinHitInterval();
        final Set<UUID> recentlyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;
            double heightOffset = 0;
            boolean goingUp = true;

            @Override
            public void run() {
                if (ticks >= duration || !attacker.isOnline() || attacker.isDead()) {
                    cleanup();
                    return;
                }

                // Update axe display position - spin around player and bob up/down
                if (axeDisplay.isValid()) {
                    angle += Math.PI / 8; // Spin speed

                    // Bob up and down
                    if (goingUp) {
                        heightOffset += 0.05;
                        if (heightOffset >= 0.5) goingUp = false;
                    } else {
                        heightOffset -= 0.05;
                        if (heightOffset <= -0.3) goingUp = true;
                    }

                    double x = Math.cos(angle) * 1.2;
                    double z = Math.sin(angle) * 1.2;
                    Location newLoc = attacker.getLocation().add(x, 1.0 + heightOffset, z);

                    // Make the axe face the player (handle towards player)
                    float yaw = (float) Math.toDegrees(Math.atan2(-x, -z));
                    newLoc.setYaw(yaw);
                    newLoc.setPitch(0);

                    axeDisplay.teleport(newLoc);
                    // Rotate the axe itself for spinning visual
                    axeDisplay.setRotation(yaw + (ticks * 15), 90); // Vertical orientation with spin
                }

                // Deal damage every hitInterval ticks
                if (ticks % hitInterval == 0) {
                    recentlyHit.clear();

                    for (Entity entity : attacker.getNearbyEntities(radius, radius, radius)) {
                        if (!(entity instanceof Player victim)) continue;
                        if (victim.equals(attacker)) continue;
                        if (recentlyHit.contains(victim.getUniqueId())) continue;

                        // Check if in same game and different team
                        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
                        if (session == null) continue;

                        Team attackerTeam = session.getPlayerTeam(attacker);
                        Team victimTeam = session.getPlayerTeam(victim);
                        if (attackerTeam == null || victimTeam == null) continue;
                        if (attackerTeam.equals(victimTeam)) continue;

                        // Deal damage
                        victim.damage(damage, attacker);
                        recentlyHit.add(victim.getUniqueId());

                        // Visual feedback
                        SoundUtils.playAt(victim.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
                        ParticleUtils.hitFeedback(victim.getLocation(), 15, 0.3);
                        Messages.debug(attacker, "CARLS_BATTLEAXE: Spin hit " + victim.getName() + " for " + damage + " damage");
                    }
                }

                // Spinning particles around player
                if (ticks % 2 == 0) {
                    ParticleUtils.spinSweep(attacker.getLocation(), angle, radius);
                }

                // Sound every 10 ticks
                if (ticks % 10 == 0) {
                    SoundUtils.playAt(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
                }

                ticks++;
            }

            private void cleanup() {
                spinningPlayers.remove(uuid);
                if (axeDisplay.isValid()) {
                    axeDisplay.remove();
                }
                attacker.removePotionEffect(PotionEffectType.SLOWNESS);
                Messages.send(attacker, "<gray>Spin attack ended.</gray>");
                cancel();
            }
        }.runTaskTimer(CashClashPlugin.getInstance(), 0L, 1L);

        Messages.debug(attacker, "CARLS_BATTLEAXE: Spin attack started! Duration: " + (duration / 20) + "s, Damage: " + damage + ", Radius: " + radius);
    }

    /**
     * Spawn an ItemDisplay entity showing Carl's Battleaxe spinning around the player.
     */
    private ItemDisplay spawnSpinningAxeDisplay(Player player) {
        Location spawnLoc = player.getLocation().add(1.2, 1.0, 0);

        return player.getWorld().spawn(spawnLoc, ItemDisplay.class, display -> {
            // Create the axe item
            ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
            display.setItemStack(axe);

            // Set the transformation for vertical orientation (handle facing player)
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);

            // Make it larger and rotate to vertical (handle down)
            Transformation transform = display.getTransformation();
            Quaternionf leftRotation = new Quaternionf();
            leftRotation.rotateX((float) Math.toRadians(90)); // Rotate to vertical

            display.setTransformation(new Transformation(
                    transform.getTranslation(),
                    leftRotation,
                    new Vector3f(1.5f, 1.5f, 1.5f), // Scale up
                    transform.getRightRotation()
            ));

            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
        });
    }

    /**
     * Check if a player is currently in a spin attack.
     */
    public boolean isSpinning(UUID playerId) {
        return spinningPlayers.contains(playerId);
    }

    /**
     * Handle Carl's Battleaxe critical hit launch.
     * Critical hits (while falling) launch enemies into the air.
     * 10 second cooldown.
     */
    public void handleCarlsCriticalHit(Player attacker, Player victim) {
        UUID uuid = attacker.getUniqueId();

        Messages.debug(attacker, "CARLS_BATTLEAXE: Critical hit detected (falling)");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_CRIT)) {
            Messages.debug(attacker, "CARLS_BATTLEAXE: Crit launch on cooldown");
            return;
        }

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_CRIT, cfg.getCarlsCritCooldown() / 1000);

        // Launch victim into the air with consistent velocity
        double launchPower = cfg.getCarlsCritLaunchPower();

        // Ensure minimum launch power for noticeable effect
        if (launchPower < 1.5) {
            launchPower = 1.5;
        }

        final double finalLaunchPower = launchPower;

        // Create launch velocity - primarily upward with horizontal push
        Vector direction = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        direction.setY(0);
        if (direction.lengthSquared() > 0) {
            direction.normalize().multiply(0.5);
        } else {
            direction = new Vector(0, 0, 0);
        }

        final Vector horizontalPush = direction;

        // Apply velocity on next tick to ensure it takes effect after damage knockback
        SchedulerUtils.runTaskLater(() -> {
            if (victim.isOnline()) {
                Vector launchVelocity = new Vector(horizontalPush.getX(), finalLaunchPower, horizontalPush.getZ());
                victim.setVelocity(launchVelocity);
                Messages.debug(attacker, "CARLS_BATTLEAXE: Applied velocity to " + victim.getName() + " - Y: " + finalLaunchPower);
            }
        }, 1L);

        Messages.debug(attacker, "CARLS_BATTLEAXE: Launching " + victim.getName() + " with power " + launchPower);
        SoundUtils.play(victim, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.8f);
        SoundUtils.play(attacker, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
        ParticleUtils.crit(victim.getLocation().add(0, 1, 0), 20, 0.5);
        Messages.send(attacker, "<gold>Critical launch!</gold>");
    }

    // ==================== WIND BOW ====================

    /**
     * Handle Wind Bow shot.
     * 10 shots per magazine, then 30 second reload cooldown.
     * @return true if shot is allowed, false if on cooldown
     */
    public boolean handleWindBowShot(Player player) {
        UUID uuid = player.getUniqueId();

        // Check if on reload cooldown
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WIND_BOW_RELOAD)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WIND_BOW_RELOAD);
            Messages.send(player, "<red>Wind Bow reloading! (" + remaining + "s)</red>");
            return false;
        }

        // Get or initialize shots remaining
        int shots = windBowShotsRemaining.getOrDefault(uuid, cfg.getWindBowShotsPerMagazine());

        if (shots <= 0) {
            // Start reload cooldown
            cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.WIND_BOW_RELOAD, cfg.getWindBowReloadCooldown());
            windBowShotsRemaining.put(uuid, cfg.getWindBowShotsPerMagazine());
            Messages.send(player, "<yellow>Wind Bow out of shots! Reloading...</yellow>");
            SoundUtils.play(player, Sound.ITEM_CROSSBOW_LOADING_END, 1.0f, 0.5f);
            return false;
        }

        // Consume a shot
        shots--;
        windBowShotsRemaining.put(uuid, shots);

        if (shots == 0) {
            // Start reload cooldown
            cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.WIND_BOW_RELOAD, cfg.getWindBowReloadCooldown());
            windBowShotsRemaining.put(uuid, cfg.getWindBowShotsPerMagazine());
            Messages.send(player, "<yellow>Wind Bow magazine empty! Reloading...</yellow>");
            SoundUtils.play(player, Sound.ITEM_CROSSBOW_LOADING_END, 1.0f, 0.5f);
        } else if (shots <= 3) {
            Messages.send(player, "<yellow>Wind Bow: " + shots + " shots remaining</yellow>");
        }

        Messages.debug(player, "WIND_BOW: Shot fired, " + shots + " remaining");
        return true;
    }

    /**
     * Wind Bow right-click boost (while sneaking).
     * Boosts player up and forward.
     * 30 second cooldown.
     */
    public void useWindBowBoost(Player player) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "WIND_BOW: Boost ability triggered");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WIND_BOW_BOOST)) {
            Messages.debug(player, "WIND_BOW: Boost on cooldown - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WIND_BOW_BOOST) + "s");
            Messages.send(player, "<red>Wind boost on cooldown! (" + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WIND_BOW_BOOST) + "s)</red>");
            return;
        }

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.WIND_BOW_BOOST, cfg.getWindBowBoostCooldown());

        Vector direction = player.getLocation().getDirection();
        direction.setY(Math.max(direction.getY() + 0.5, 0.5));

        Vector velocity = direction.multiply(cfg.getWindBowBoostPower());
        player.setVelocity(velocity);

        Messages.debug(player, "WIND_BOW: Boosted! Power: " + cfg.getWindBowBoostPower() + ", Cooldown: " + cfg.getWindBowBoostCooldown() + "s");
        SoundUtils.play(player, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f);
        ParticleUtils.cloud(player.getLocation(), 20, 0.5);

        Messages.send(player, "<aqua>Wind boost!</aqua>");
    }

    /**
     * Wind Bow arrow hit - propel target and nearby players backwards.
     * Acts as a wind gust in 3 block radius. Passive ability.
     */
    public void handleWindBowHit(Player shooter, Player target) {
        Location hitLoc = target.getLocation();
        Vector pushDirection = hitLoc.toVector().subtract(shooter.getLocation().toVector()).normalize();
        pushDirection.setY(0.3);

        int hitCount = 0;
        // Push target and all nearby players
        for (Entity entity : target.getWorld().getNearbyEntities(hitLoc, cfg.getWindBowPushRadius(), cfg.getWindBowPushRadius(), cfg.getWindBowPushRadius())) {
            if (!(entity instanceof Player p)) continue;
            if (p.equals(shooter)) continue;

            p.setVelocity(pushDirection.clone().multiply(cfg.getWindBowPushPower()));
            hitCount++;
        }

        Messages.debug(shooter, "WIND_BOW: Wind gust pushed " + hitCount + " players, radius: " + cfg.getWindBowPushRadius() + ", power: " + cfg.getWindBowPushPower());
        SoundUtils.playAt(hitLoc, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 0.8f);
        ParticleUtils.cloud(hitLoc, 30, 1);
    }

    // ==================== ELECTRIC EEL SWORD ====================

    /**
     * Electric Eel Sword chain damage.
     * Fully charged hits damage nearby enemies in 5 block radius for 0.5 hearts.
     * 1 second cooldown.
     */
    public void handleElectricEelChain(Player attacker, LivingEntity victim) {
        UUID uuid = attacker.getUniqueId();
        

        Messages.debug(attacker, "ELECTRIC_EEL: Chain damage check");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.ELECTRIC_EEL_CHAIN)) {
            Messages.debug(attacker, "ELECTRIC_EEL: Chain on cooldown");
            return;
        }
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.ELECTRIC_EEL_CHAIN, cfg.getEelChainCooldown());

        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
        if (session == null) {
            Messages.debug(attacker, "ELECTRIC_EEL: No session");
            return;
        }

        Team attackerTeam = session.getPlayerTeam(attacker);
        Location victimLoc = victim.getLocation();
        int radius = cfg.getEelChainRadius();
        int chainCount = 0;

        // Chain damage to nearby enemies
        for (Entity entity : victim.getWorld().getNearbyEntities(victimLoc, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(attacker) || target.equals(victim)) continue;

            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam != null && attackerTeam != null &&
                targetTeam.getTeamNumber() == attackerTeam.getTeamNumber()) continue;

            target.damage(cfg.getEelChainDamage(), attacker);
            chainCount++;

            // Lightning spark effect
            ParticleUtils.electricSpark(target.getLocation().add(0, 1, 0), 15, 0.3);
        }

        Messages.debug(attacker, "ELECTRIC_EEL: Chained to " + chainCount + " enemies, radius: " + radius + ", damage: " + cfg.getEelChainDamage());
        SoundUtils.playAt(victimLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.5f);
    }

    /**
     * Electric Eel Sword teleport.
     * Zaps player 4 blocks forward but not through walls.
     * 15 second cooldown.
     */
    public void useElectricEelTeleport(Player player) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "ELECTRIC_EEL: Teleport ability triggered");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.ELECTRIC_EEL_LIGHTNING)) {
            Messages.debug(player, "ELECTRIC_EEL: Teleport on cooldown - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.ELECTRIC_EEL_LIGHTNING) + "s");
            Messages.send(player, "<red>Teleport on cooldown! (" + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.ELECTRIC_EEL_LIGHTNING) + "s)</red>");
            return;
        }

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.ELECTRIC_EEL_LIGHTNING, cfg.getEelTeleportCooldown());

        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();
        World world = player.getWorld();

        double distance = cfg.getEelTeleportDistance(); // treat as push strength

        RayTraceResult result = world.rayTraceBlocks(start, direction, distance, FluidCollisionMode.NEVER, true);

        double pushStrength = distance; // you can tune this

        if (result != null && result.getHitBlock() != null) {
            // Stop before the wall
            double hitDistance = result.getHitPosition().distance(start.toVector());
            pushStrength = Math.max(0.1, hitDistance - 0.5);
            Messages.debug(player, "ELECTRIC_EEL: Hit wall, reduced push strength");
        } else {
            Messages.debug(player, "ELECTRIC_EEL: Full push strength");
        }

        Vector velocity = direction.multiply(pushStrength);
        player.setVelocity(velocity);

        // Effects at destination
        ParticleUtils.electricSpark(player.getLocation().add(0, 1, 0), 30, 0.5);

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.ELECTRIC_EEL_LIGHTNING, cfg.getEelTeleportCooldown());
        Messages.debug(player, "ELECTRIC_EEL: Teleported! Distance: " + distance + ", Cooldown: " + cfg.getEelTeleportCooldown() + "s");
        Messages.send(player, "<aqua>Zap!</aqua>");
    }

    /**
     * Handle Goblin Spear throw.
     * 8 shots per magazine, 15 second reload.
     * @return true if shot was successful, false if on reload cooldown
     */
    public boolean handleGoblinSpearThrow(Player player) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "GOBLIN_SPEAR: Throw triggered");

        int shots = goblinSpearShotsRemaining.getOrDefault(uuid, cfg.getGoblinShotsPerMag());
        if (shots <= 0) {
            if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.GOBLIN_SPEAR_RELOAD)) {
                Messages.debug(player, "GOBLIN_SPEAR: Reloading - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.GOBLIN_SPEAR_RELOAD) + "s");
                Messages.send(player, "<red>Goblin Spear reloading! (" + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.GOBLIN_SPEAR_RELOAD) + "s)</red>");
                return false;
            }
            goblinSpearShotsRemaining.put(uuid, cfg.getGoblinShotsPerMag());
            shots = cfg.getGoblinShotsPerMag();
            Messages.debug(player, "GOBLIN_SPEAR: Magazine reloaded to " + shots);
        }

        goblinSpearShotsRemaining.put(uuid, shots - 1);
        Messages.debug(player, "GOBLIN_SPEAR: Shot fired! Remaining: " + (shots - 1));

        if (shots - 1 <= 0) {
            cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.GOBLIN_SPEAR_RELOAD, cfg.getGoblinReloadCooldown());
            Messages.debug(player, "GOBLIN_SPEAR: Out of shots, reloading for " + cfg.getGoblinReloadCooldown() + "s");
            Messages.send(player, "<yellow>Goblin Spear reloading...</yellow>");
        }

        return true;
    }

    /**
     * Handle Goblin Spear hit.
     * Deals damage + Poison.
     */
    public void handleGoblinSpearHit(Player shooter, LivingEntity victim) {
        Messages.debug(shooter, "GOBLIN_SPEAR: Hit " + victim.getName());

        victim.damage(cfg.getGoblinSpearDamage(), shooter);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, cfg.getGoblinPoisonDuration(), cfg.getGoblinPoisonLevel(), false, true));

        Messages.debug(shooter, "GOBLIN_SPEAR: Dealt " + cfg.getGoblinSpearDamage() + " damage + Poison " + (cfg.getGoblinPoisonLevel() + 1));

        ParticleUtils.slime(victim.getLocation().add(0, 1, 0), 20, 0.5);
    }

    /**
     * Start Goblin Spear charge ability.
     * Player charges forward, catching enemies and dealing damage + poison when hitting a wall.
     */
    public void startGoblinSpearCharge(Player player) {
        UUID uuid = player.getUniqueId();

        // Check cooldown
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.GOBLIN_SPEAR_CHARGE)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.GOBLIN_SPEAR_CHARGE);
            Messages.send(player, "<red>Charge on cooldown! (" + remaining + "s)</red>");
            return;
        }

        // Check if already charging
        if (goblinSpearCharging.containsKey(uuid)) {
            return;
        }

        Messages.debug(player, "GOBLIN_SPEAR: Charge started!");
        Messages.send(player, "<green>Charge!</green>");
        SoundUtils.play(player, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 1.5f);

        // Initialize caught players list
        goblinSpearCharging.put(uuid, new ArrayList<>());

        // Get session for team checking
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        Team playerTeam = session != null ? session.getPlayerTeam(player) : null;

        // Get charge direction
        Vector chargeDirection = player.getLocation().getDirection().setY(0).normalize();
        double chargeSpeed = cfg.getGoblinChargeSpeed();
        int maxDuration = cfg.getGoblinChargeMaxDuration();

        // Start charge runnable
        BukkitTask chargeTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= maxDuration) {
                    endCharge(player, false);
                    cancel();
                    return;
                }

                // Move player forward
                Vector velocity = chargeDirection.clone().multiply(chargeSpeed);
                velocity.setY(player.getVelocity().getY()); // Preserve Y velocity
                player.setVelocity(velocity);

                // Check for wall collision
                Location checkLoc = player.getLocation().add(chargeDirection.clone().multiply(0.5));
                if (checkLoc.getBlock().getType().isSolid()) {
                    endCharge(player, true);
                    cancel();
                    return;
                }

                // Check for nearby enemies to catch
                List<Player> caughtPlayers = goblinSpearCharging.get(uuid);
                for (Entity entity : player.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (!(entity instanceof Player target)) continue;
                    if (target.equals(player)) continue;
                    if (caughtPlayers.contains(target)) continue;

                    // Team check
                    if (session != null && playerTeam != null) {
                        Team targetTeam = session.getPlayerTeam(target);
                        if (targetTeam != null && targetTeam.getTeamNumber() == playerTeam.getTeamNumber()) {
                            continue;
                        }
                    }

                    // Catch the player
                    caughtPlayers.add(target);
                    Messages.debug(player, "GOBLIN_SPEAR: Caught " + target.getName());
                    SoundUtils.play(target, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
                }

                // Drag caught players along
                Location dragLoc = player.getLocation().add(0, 0.5, 0);
                for (Player caught : caughtPlayers) {
                    if (caught.isOnline()) {
                        caught.teleport(dragLoc);
                    }
                }

                // Spawn particles
                ParticleUtils.crit(player.getLocation().add(0, 1, 0), 5, 0.3);

                ticks++;
            }
        }.runTaskTimer(CashClashPlugin.getInstance(), 0L, 1L);

        // Track the task
        activeTasks.computeIfAbsent(uuid, k -> new ArrayList<>()).add(chargeTask);
    }

    /**
     * End Goblin Spear charge, applying damage if hit wall.
     */
    private void endCharge(Player player, boolean hitWall) {
        UUID uuid = player.getUniqueId();
        List<Player> caughtPlayers = goblinSpearCharging.remove(uuid);

        if (caughtPlayers == null || caughtPlayers.isEmpty()) {
            Messages.debug(player, "GOBLIN_SPEAR: Charge ended, no players caught");
        } else if (hitWall) {
            // Deal damage and poison to all caught players
            double damage = cfg.getGoblinChargeWallDamage();
            int poisonDuration = cfg.getGoblinChargePoisonDuration();
            int poisonLevel = cfg.getGoblinChargePoisonLevel();

            for (Player caught : caughtPlayers) {
                if (!caught.isOnline()) continue;

                caught.damage(damage, player);
                caught.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonDuration, poisonLevel, false, true));

                // Visual effects
                ParticleUtils.damageIndicator(caught.getLocation().add(0, 1, 0), 20, 0.5);
                SoundUtils.play(caught, Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);

                Messages.debug(player, "GOBLIN_SPEAR: Wall impact dealt " + damage + " damage + Poison to " + caught.getName());
            }

            Messages.send(player, "<gold>Wall impact! Dealt " + (int) damage + " damage to " + caughtPlayers.size() + " enemies!</gold>");
            SoundUtils.play(player, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.8f);
        } else {
            Messages.debug(player, "GOBLIN_SPEAR: Charge ended without wall impact");
        }

        // Set cooldown
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.GOBLIN_SPEAR_CHARGE, cfg.getGoblinChargeCooldown());
        Messages.debug(player, "GOBLIN_SPEAR: Charge cooldown set to " + cfg.getGoblinChargeCooldown() + "s");

        // Stop the player
        player.setVelocity(new Vector(0, 0, 0));
    }

    /**
     * Check if player is currently charging with Goblin Spear.
     */
    public boolean isGoblinSpearCharging(UUID playerId) {
        return goblinSpearCharging.containsKey(playerId);
    }

    // ==================== BLOODWRENCH_CROSSBOW ====================

    /**
     * Toggle BloodWrench mode between Rapid Fire and Supercharged.
     * Cannot switch modes while rapid firing or on cooldown.
     * 1 second cooldown between toggles.
     */
    public void toggleBloodwrenchMode(Player player) {
        UUID uuid = player.getUniqueId();

        // Cannot switch while in rapid fire burst
        if (bloodwrenchRapidFiring.contains(uuid)) {
            Messages.send(player, "<red>Cannot switch modes while firing!</red>");
            return;
        }

        // Check toggle cooldown
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.BLOODWRENCH_MODE_TOGGLE)) {
            Messages.send(player, "<red>Mode switch on cooldown! (" +
                cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.BLOODWRENCH_MODE_TOGGLE) + "s)</red>");
            return;
        }

        // Toggle mode (default is rapid mode = true)
        boolean currentRapid = bloodwrenchRapidMode.getOrDefault(uuid, true);
        boolean newRapid = !currentRapid;
        bloodwrenchRapidMode.put(uuid, newRapid);

        // Set toggle cooldown
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.BLOODWRENCH_MODE_TOGGLE, cfg.getBloodwrenchModeToggleCooldown());

        String modeName = newRapid ? "<red>Rapid Fire</red>" : "<dark_purple>Supercharged</dark_purple>";
        Messages.send(player, "<gold>BloodWrench mode: " + modeName);
        SoundUtils.play(player, Sound.BLOCK_LEVER_CLICK, 1.0f, newRapid ? 1.5f : 0.8f);
        Messages.debug(player, "BLOODWRENCH: Switched to " + (newRapid ? "Rapid Fire" : "Supercharged") + " mode");
    }

    /**
     * Check if BloodWrench is in Rapid Fire mode.
     */
    public boolean isBloodwrenchRapidMode(Player player) {
        return bloodwrenchRapidMode.getOrDefault(player.getUniqueId(), true);
    }

    /**
     * Handle BloodWrench shot based on current mode.
     * Returns false if shot should be cancelled.
     */
    public boolean handleBloodwrenchShot(Player player) {
        player.getUniqueId();
        boolean isRapid = isBloodwrenchRapidMode(player);

        Messages.debug(player, "BLOODWRENCH: Shot triggered (" + (isRapid ? "Rapid" : "Supercharged") + " mode)");

        if (isRapid) {
            return handleBloodwrenchRapidShot(player);
        } else {
            return handleBloodwrenchSuperchargedShot(player);
        }
    }

    /**
     * Handle Rapid Fire mode shot.
     * Player fires 3 blood shots. Once started, must complete all 3 before switching modes.
     * After 3 shots, cooldown begins.
     */
    private boolean handleBloodwrenchRapidShot(Player player) {
        UUID uuid = player.getUniqueId();

        // Check if on reload cooldown
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.BLOODWRENCH_RAPID_RELOAD)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.BLOODWRENCH_RAPID_RELOAD);
            Messages.send(player, "<red>BloodWrench reloading! (" + remaining + "s)</red>");
            Messages.debug(player, "BLOODWRENCH: Rapid reloading - " + remaining + "s");
            return false;
        }

        int shots = bloodwrenchRapidShotsRemaining.getOrDefault(uuid, cfg.getBloodwrenchRapidShots());

        // First shot starts the burst
        if (shots == cfg.getBloodwrenchRapidShots()) {
            bloodwrenchRapidFiring.add(uuid);
            Messages.debug(player, "BLOODWRENCH: Rapid fire burst started");
        }

        // Fire the shot
        bloodwrenchRapidShotsRemaining.put(uuid, shots - 1);
        Messages.debug(player, "BLOODWRENCH: Rapid shot fired! Remaining: " + (shots - 1));

        // Check if burst complete
        if (shots - 1 <= 0) {
            bloodwrenchRapidFiring.remove(uuid);
            bloodwrenchRapidShotsRemaining.remove(uuid);
            cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.BLOODWRENCH_RAPID_RELOAD, cfg.getBloodwrenchRapidReloadCooldown());
            Messages.send(player, "<yellow>BloodWrench reloading...</yellow>");
            Messages.debug(player, "BLOODWRENCH: Rapid burst complete, reloading for " + cfg.getBloodwrenchRapidReloadCooldown() + "s");
        }

        return true;
    }

    /**
     * Handle Supercharged mode shot.
     * Single powerful shot creating a blood vortex.
     */
    private boolean handleBloodwrenchSuperchargedShot(Player player) {
        UUID uuid = player.getUniqueId();

        // Check cooldown
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.BLOODWRENCH_SUPERCHARGE_COOLDOWN)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.BLOODWRENCH_SUPERCHARGE_COOLDOWN);
            Messages.send(player, "<red>Supercharged shot on cooldown! (" + remaining + "s)</red>");
            Messages.debug(player, "BLOODWRENCH: Supercharged on cooldown - " + remaining + "s");
            return false;
        }

        Messages.debug(player, "BLOODWRENCH: Supercharged shot fired!");
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.BLOODWRENCH_SUPERCHARGE_COOLDOWN, cfg.getBloodwrenchSuperchargeCooldown());
        return true;
    }

    /**
     * Handle BloodWrench Rapid Fire hit - creates blood sphere.
     * Blood sphere gives Slowness I when inside and burst damage (nerfed grenade).
     */
    public void handleBloodwrenchRapidHit(Player shooter, Location hitLocation) {
        World world = hitLocation.getWorld();
        if (world == null) return;

        GameSession session = GameManager.getInstance().getPlayerSession(shooter);
        Team shooterTeam = session != null ? session.getPlayerTeam(shooter) : null;

        Messages.debug(shooter, "BLOODWRENCH: Rapid hit - creating blood sphere at " + hitLocation);

        // Visual blood sphere
        double radius = cfg.getBloodwrenchSphereRadius();
        ParticleUtils.bloodSphere(hitLocation, radius, 50);
        SoundUtils.playAt(hitLocation, Sound.BLOCK_SLIME_BLOCK_BREAK, 1.0f, 0.5f);

        // Create blood sphere effect that lingers
        int durationTicks = cfg.getBloodwrenchSphereDuration();
        double damage = cfg.getBloodwrenchSphereDamage();

        // Initial burst damage (nerfed grenade - smaller radius, less damage)
        for (Entity entity : world.getNearbyEntities(hitLocation, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(shooter)) continue;

            if (session != null) {
                Team targetTeam = session.getPlayerTeam(target);
                if (targetTeam != null && shooterTeam != null &&
                    targetTeam.getTeamNumber() == shooterTeam.getTeamNumber()) continue;
            }

            // Burst damage
            target.damage(damage, shooter);
            Messages.debug(shooter, "BLOODWRENCH: Blood sphere damaged " + target.getName() + " for " + damage);
        }

        // Lingering sphere effect
        final double sphereRadius = radius;
        BukkitTask sphereTask = SchedulerUtils.runTaskTimer(() -> {
            // Particle effect
            ParticleUtils.bloodSphereLingering(hitLocation, sphereRadius);

            // Apply slowness to enemies inside
            for (Entity entity : world.getNearbyEntities(hitLocation, sphereRadius, sphereRadius, sphereRadius)) {
                if (!(entity instanceof Player target)) continue;
                if (target.equals(shooter)) continue;

                if (session != null) {
                    Team targetTeam = session.getPlayerTeam(target);
                    if (targetTeam != null && shooterTeam != null &&
                        targetTeam.getTeamNumber() == shooterTeam.getTeamNumber()) continue;
                }

                // Slowness I while inside sphere
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
            }
        }, 0L, 10L);

        // Cancel after duration
        SchedulerUtils.runTaskLater(() -> {
            Objects.requireNonNull(sphereTask).cancel();
            Messages.debug(shooter, "BLOODWRENCH: Blood sphere expired");
        }, durationTicks);

        activeTasks.computeIfAbsent(shooter.getUniqueId(), k -> new ArrayList<>()).add(sphereTask);
    }

    /**
     * Handle BloodWrench Supercharged hit - creates blood vortex.
     * Vortex has red particles, gives Levitation and deals damage to enemies inside.
     */
    public void handleBloodwrenchSuperchargedHit(Player shooter, Location hitLocation) {
        World world = hitLocation.getWorld();
        if (world == null) return;

        GameSession session = GameManager.getInstance().getPlayerSession(shooter);
        Team shooterTeam = session != null ? session.getPlayerTeam(shooter) : null;

        Messages.debug(shooter, "BLOODWRENCH: Supercharged hit - creating blood vortex at " + hitLocation);
        Messages.send(shooter, "<dark_purple>Blood Vortex activated!</dark_purple>");

        SoundUtils.playAt(hitLocation, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.5f);
        SoundUtils.playAt(hitLocation, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.2f);

        int durationTicks = cfg.getBloodwrenchVortexDuration();
        double radius = cfg.getBloodwrenchVortexRadius();
        double damagePerTick = cfg.getBloodwrenchVortexDamage();

        // Vortex effect with spiraling particles
        final int[] tick = {0};
        BukkitTask vortexTask = SchedulerUtils.runTaskTimer(() -> {
            tick[0]++;

            // Spiraling red particles using helper method
            ParticleUtils.bloodVortexSpiral(hitLocation, radius, tick[0]);

            // Apply effects every 10 ticks (0.5 seconds)
            if (tick[0] % 10 == 0) {
                for (Entity entity : world.getNearbyEntities(hitLocation, radius, radius + 2, radius)) {
                    if (!(entity instanceof Player target)) continue;
                    if (target.equals(shooter)) continue;

                    if (session != null) {
                        Team targetTeam = session.getPlayerTeam(target);
                        if (targetTeam != null && shooterTeam != null &&
                            targetTeam.getTeamNumber() == shooterTeam.getTeamNumber()) continue;
                    }

                    // Levitation and damage while inside vortex
                    target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 30, cfg.getBloodwrenchVortexLevitationLevel() - 1, false, false));
                    target.damage(damagePerTick, shooter);
                    Messages.debug(shooter, "BLOODWRENCH: Vortex affecting " + target.getName());
                }
            }
        }, 0L, 2L);

        // Cancel after duration
        SchedulerUtils.runTaskLater(() -> {
            Objects.requireNonNull(vortexTask).cancel();
            Messages.debug(shooter, "BLOODWRENCH: Blood vortex expired");
            // Final burst effect
            ParticleUtils.bloodVortexExplosion(hitLocation, radius);
            SoundUtils.playAt(hitLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
        }, durationTicks);

        activeTasks.computeIfAbsent(shooter.getUniqueId(), k -> new ArrayList<>()).add(vortexTask);
    }

    /**
     * Check if player is currently in rapid fire burst (cannot switch modes).
     */
    public boolean isBloodwrenchRapidFiring(UUID playerId) {
        return bloodwrenchRapidFiring.contains(playerId);
    }

    // ==================== WARDEN GLOVES (BOXING GLOVES) ====================

    /**
     * Warden Gloves boxing ability - Left click to punch.
     * On every 5th punch, speed increases.
     * Ability lasts for 20 seconds, 35 second cooldown.
     */
    public void useWardenPunch(Player player, Player victim) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "WARDEN_GLOVES: Punch attack on " + victim.getName());

        // Check if boxing ability is on cooldown (ability hasn't been started yet)
        if (!wardenBoxingActive.contains(uuid) && cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WARDEN_BOXING)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_BOXING);
            Messages.send(player, "<red>Boxing gloves on cooldown! (" + remaining + "s)</red>");
            return;
        }

        // Start boxing ability if not already active
        if (!wardenBoxingActive.contains(uuid)) {
            startWardenBoxingAbility(player);
        }

        // Increment punch count
        int punchCount = wardenPunchCount.getOrDefault(uuid, 0) + 1;
        wardenPunchCount.put(uuid, punchCount);

        // Apply punch knockback
        Vector knockback = victim.getLocation().toVector()
                .subtract(player.getLocation().toVector())
                .normalize()
                .multiply(1.2)
                .setY(0.4);
        victim.setVelocity(knockback);

        // Punch sound effect
        SoundUtils.play(victim, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.0f, 1.0f);
        ParticleUtils.sweep(victim.getLocation().add(0, 1, 0));

        // Every 5th punch, increase speed
        if (punchCount % 5 == 0) {
            int currentSpeedLevel = 0;
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (effect.getType() == PotionEffectType.SPEED) {
                    currentSpeedLevel = effect.getAmplifier() + 1;
                    break;
                }
            }

            // Cap speed at level 3 (amplifier 2)
            int newSpeedLevel = Math.min(currentSpeedLevel + 1, 3);
            int remainingDuration = cfg.getWardenBoxingDuration() * 20; // Convert seconds to ticks

            // Apply or increase speed
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED,
                    remainingDuration,
                    newSpeedLevel - 1, // Amplifier is 0-indexed
                    false, true
            ));

            Messages.send(player, "<dark_aqua>Speed increased! (Level " + newSpeedLevel + ")</dark_aqua>");
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            Messages.debug(player, "WARDEN_GLOVES: Speed increased to level " + newSpeedLevel + " (punch #" + punchCount + ")");
        }

        Messages.debug(player, "WARDEN_GLOVES: Punch hit! Count: " + punchCount);
    }

    /**
     * Start the Warden boxing ability (20 second duration).
     */
    private void startWardenBoxingAbility(Player player) {
        UUID uuid = player.getUniqueId();

        wardenBoxingActive.add(uuid);
        wardenPunchCount.put(uuid, 0);

        Messages.send(player, "<dark_aqua><bold>BOXING GLOVES ACTIVATED!</bold></dark_aqua>");
        Messages.send(player, "<gray>Punch enemies to build up speed!</gray>");
        SoundUtils.play(player, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);

        int durationTicks = cfg.getWardenBoxingDuration() * 20; // Convert seconds to ticks

        // End the ability after duration
        BukkitTask endTask = SchedulerUtils.runTaskLater(() -> endWardenBoxingAbility(player), durationTicks);

        activeTasks.computeIfAbsent(uuid, k -> new ArrayList<>()).add(endTask);

        Messages.debug(player, "WARDEN_GLOVES: Boxing ability started - " + cfg.getWardenBoxingDuration() + "s duration");
    }

    /**
     * End the Warden boxing ability and start cooldown.
     */
    private void endWardenBoxingAbility(Player player) {
        UUID uuid = player.getUniqueId();

        if (!wardenBoxingActive.contains(uuid)) return;

        wardenBoxingActive.remove(uuid);
        int finalPunchCount = wardenPunchCount.getOrDefault(uuid, 0);
        wardenPunchCount.remove(uuid);

        // Remove speed effect
        player.removePotionEffect(PotionEffectType.SPEED);

        // Start cooldown
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_BOXING, cfg.getWardenBoxingCooldown());

        Messages.send(player, "<yellow>Boxing gloves deactivated! Total punches: " + finalPunchCount + "</yellow>");
        Messages.send(player, "<gray>Cooldown: " + cfg.getWardenBoxingCooldown() + "s</gray>");
        SoundUtils.play(player, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.8f);

        Messages.debug(player, "WARDEN_GLOVES: Boxing ability ended - " + finalPunchCount + " punches, " + cfg.getWardenBoxingCooldown() + "s cooldown");
    }

    /**
     * Check if player has boxing ability active.
     */
    public boolean isWardenBoxingActive(UUID playerId) {
        return wardenBoxingActive.contains(playerId);
    }

    /**
     * Warden Gloves shockwave attack (Right-click ability).
     * Unleashes shockwave dealing damage + big knockback in cone.
     */
    public void useWardenShockwave(Player player) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "WARDEN_GLOVES: Shockwave ability triggered");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE)) {
            Messages.debug(player, "WARDEN_GLOVES: Shockwave on cooldown - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE) + "s");
            Messages.send(player, "<red>Shockwave on cooldown! (" + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE) + "s)</red>");
            return;
        }

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE, cfg.getWardenShockwaveCooldown());

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.debug(player, "WARDEN_GLOVES: No session");
            return;
        }

        Team playerTeam = session.getPlayerTeam(player);
        Location loc = player.getLocation();
        Vector direction = loc.getDirection().setY(0).normalize();
        World world = player.getWorld();

        // Sonic boom visual effect
        ParticleUtils.sonicBoom(loc.clone().add(direction.clone().multiply(2)).add(0, 1, 0));
        SoundUtils.playAt(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);

        int range = cfg.getWardenShockwaveRange();
        int hitCount = 0;

        // Damage and knockback enemies in cone
        for (Entity entity : world.getNearbyEntities(loc, range, 4, range)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;

            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam != null && playerTeam != null &&
                targetTeam.getTeamNumber() == playerTeam.getTeamNumber()) continue;

            // Check if target is in front of player (cone check)
            Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
            if (direction.dot(toTarget) < 0.3) continue; // Not in cone (about 70 degree cone)

            target.damage(cfg.getWardenShockwaveDamage(), player);

            Vector knockback = toTarget.multiply(cfg.getWardenKnockbackPower()).setY(0.8);
            target.setVelocity(knockback);
            hitCount++;
        }

        Messages.debug(player, "WARDEN_GLOVES: Shockwave hit " + hitCount + " enemies, damage: " + cfg.getWardenShockwaveDamage() + ", cooldown: " + cfg.getWardenShockwaveCooldown() + "s");
        Messages.send(player, "<dark_aqua>SHOCKWAVE!</dark_aqua>");
    }


    // ==================== BLAZEBITE CROSSBOWS ====================

    /**
     * Handle BlazeBite shot.
     * 8 shots per magazine, 25 second reload.
     * Mode is determined by which crossbow is being used (stored in item PDC).
     */
    public boolean handleBlazebiteShot(Player player, ItemStack crossbow) {
        UUID uuid = player.getUniqueId();
        
        String mode = PDCDetection.getBlazebiteMode(crossbow);
        boolean isGlacier = "glacier".equals(mode);

        Messages.debug(player, "BLAZEBITE: Shot triggered (" + (isGlacier ? "Glacier" : "Volcano") + " mode)");

        int shots = blazebiteShotsRemaining.getOrDefault(uuid, cfg.getBlazebiteShotsPerMag());
        if (shots <= 0) {
            if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.BLAZEBITE_RELOAD)) {
                Messages.debug(player, "BLAZEBITE: Reloading - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.BLAZEBITE_RELOAD) + "s");
                Messages.send(player, "<red>BlazeBite reloading! (" + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.BLAZEBITE_RELOAD) + "s)</red>");
                return false;
            }
            blazebiteShotsRemaining.put(uuid, cfg.getBlazebiteShotsPerMag());
            shots = cfg.getBlazebiteShotsPerMag();
            Messages.debug(player, "BLAZEBITE: Magazine reloaded to " + shots);
        }

        blazebiteShotsRemaining.put(uuid, shots - 1);
        Messages.debug(player, "BLAZEBITE: Shot fired! Remaining: " + (shots - 1));

        if (shots - 1 <= 0) {
            cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.BLAZEBITE_RELOAD, cfg.getBlazebiteReloadCooldown());
            Messages.debug(player, "BLAZEBITE: Out of shots, reloading for " + cfg.getBlazebiteReloadCooldown() + "s");
            Messages.send(player, "<yellow>BlazeBite reloading...</yellow>");
        }

        return true;
    }

    /**
     * Handle BlazeBite hit effects.
     * Glacier: First hit applies frostbite for 5 seconds. Second hit while frozen freezes player in place for 3 seconds.
     * Volcano: Explosive fire arrow (2 hearts direct, 1 heart splash in 3 blocks).
     */
    public void handleBlazebiteHit(Player shooter, Entity hitEntity, Location hitLoc, boolean isGlacierMode) {
        World world = hitLoc.getWorld();
        if (world == null) return;

        Messages.debug(shooter, "BLAZEBITE: Hit detected (" + (isGlacierMode ? "Glacier" : "Volcano") + " mode)");

        if (isGlacierMode) {
            if (hitEntity instanceof Player victim) {
                UUID victimId = victim.getUniqueId();
                long currentTime = System.currentTimeMillis();

                // Check if player is already frozen (hit while frozen)
                boolean alreadyFrozen = glacierFrozenPlayers.containsKey(victimId)
                        && glacierFrozenPlayers.get(victimId) > currentTime;

                if (alreadyFrozen) {
                    // FREEZE IN PLACE - Apply max slowness (level 255 = completely frozen) for 3 seconds
                    int freezeInPlaceDuration = cfg.getBlazebiteMaxSlownessDuration();
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeInPlaceDuration, 255, false, true));
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, freezeInPlaceDuration, 128, false, true));

                    Messages.debug(shooter, "BLAZEBITE: Glacier DOUBLE HIT on " + victim.getName() + " - FROZEN IN PLACE for " + (freezeInPlaceDuration / 20) + "s");
                    Messages.send(shooter, "<aqua>Target frozen solid!</aqua>");
                    Messages.send(victim, "<aqua>You are frozen in place!</aqua>");

                    SoundUtils.play(victim, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
                    SoundUtils.play(victim, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.8f);

                    // Continuous freeze particles above head
                    final UUID victimUUID = victimId;
                    BukkitTask particleTask = SchedulerUtils.runTaskTimer(() -> {
                        Player frozenPlayer = Bukkit.getPlayer(victimUUID);
                        if (frozenPlayer == null || !frozenPlayer.isOnline()) return;
                        ParticleUtils.freezeParticles(frozenPlayer.getLocation());
                    }, 0L, 5L);

                    // Cancel particle task after freeze duration
                    final BukkitTask taskToCancel = particleTask;
                    SchedulerUtils.runTaskLater(() -> {
                        if (taskToCancel != null && !taskToCancel.isCancelled()) {
                            taskToCancel.cancel();
                        }
                    }, freezeInPlaceDuration);

                    activeTasks.computeIfAbsent(victimId, k -> new ArrayList<>()).add(particleTask);
                    glacierFrozenPlayers.remove(victimId);
                } else {
                    // FIRST HIT - Apply frostbite for 5 seconds
                    int frostbiteDuration = cfg.getBlazebiteFreezeDuration();
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, frostbiteDuration, 0, false, true));

                    Messages.debug(shooter, "BLAZEBITE: Glacier hit " + victim.getName() + " - Frostbite for " + (frostbiteDuration / 20) + "s");
                    ParticleUtils.glacierFrost(victim.getLocation());
                    SoundUtils.play(victim, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);

                    int freezeTicks = 140 + frostbiteDuration;
                    victim.setFreezeTicks(freezeTicks);

                    // Cancel any existing frostbite particle task
                    BukkitTask existingTask = glacierFrostbiteParticleTasks.remove(victimId);
                    if (existingTask != null && !existingTask.isCancelled()) {
                        existingTask.cancel();
                    }

                    // Frostbite particles during initial freeze
                    final UUID victimUUID = victimId;
                    BukkitTask frostbiteParticleTask = SchedulerUtils.runTaskTimer(() -> {
                        Player frostbittenPlayer = Bukkit.getPlayer(victimUUID);
                        if (frostbittenPlayer == null || !frostbittenPlayer.isOnline()) return;
                        ParticleUtils.frostbiteParticles(frostbittenPlayer.getLocation());
                    }, 0L, 5L);

                    glacierFrostbiteParticleTasks.put(victimId, frostbiteParticleTask);

                    final BukkitTask taskToCancel = frostbiteParticleTask;
                    SchedulerUtils.runTaskLater(() -> {
                        if (taskToCancel != null && !taskToCancel.isCancelled()) {
                            taskToCancel.cancel();
                        }
                        glacierFrostbiteParticleTasks.remove(victimUUID);
                    }, frostbiteDuration);

                    activeTasks.computeIfAbsent(victimId, k -> new ArrayList<>()).add(frostbiteParticleTask);

                    long expirationTime = currentTime + (frostbiteDuration / 20 * 1000L);
                    glacierFrozenPlayers.put(victimId, expirationTime);
                }
            }
        } else {
            // Volcano mode
            ParticleUtils.volcanoExplosion(hitLoc);
            SoundUtils.playAt(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

            GameSession session = GameManager.getInstance().getPlayerSession(shooter);
            Team shooterTeam = session != null ? session.getPlayerTeam(shooter) : null;
            int radius = cfg.getBlazebiteVolcanoRadius();
            int hitCount = 0;

            for (Entity entity : world.getNearbyEntities(hitLoc, radius, radius, radius)) {
                if (!(entity instanceof Player target)) continue;
                if (target.equals(shooter)) continue;

                if (session != null) {
                    Team targetTeam = session.getPlayerTeam(target);
                    if (targetTeam != null && shooterTeam != null &&
                        targetTeam.getTeamNumber() == shooterTeam.getTeamNumber()) continue;
                }

                double damage = entity.equals(hitEntity) ? cfg.getBlazebiteVolcanoDirectDamage() : cfg.getBlazebiteVolcanoSplashDamage();
                target.damage(damage, shooter);
                target.setFireTicks(4 * 20);
                hitCount++;
            }
            Messages.debug(shooter, "BLAZEBITE: Volcano explosion hit " + hitCount + " enemies, radius: " + radius);
        }
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        blazebiteShotsRemaining.clear();
        glacierFrozenPlayers.clear();
        goblinSpearShotsRemaining.clear();
        goblinSpearCharging.clear();
        bloodwrenchRapidMode.clear();
        bloodwrenchRapidShotsRemaining.clear();
        bloodwrenchRapidFiring.clear();
        wardenPunchCount.clear();
        wardenBoxingActive.clear();
        windBowShotsRemaining.clear();

        // Cancel and clear frostbite particle tasks
        glacierFrostbiteParticleTasks.values().forEach(task -> {
            if (task != null && !task.isCancelled()) task.cancel();
        });
        glacierFrostbiteParticleTasks.clear();

        activeTasks.values().forEach(tasks -> tasks.forEach(BukkitTask::cancel));
        activeTasks.clear();

        // Note: cooldowns are managed by CooldownManager and will be cleared when players are cleared
    }
}

