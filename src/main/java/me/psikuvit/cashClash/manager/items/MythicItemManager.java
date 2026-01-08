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
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.CustomModelDataMapper;
import me.psikuvit.cashClash.util.items.PDCDetection;
import net.kyori.adventure.text.Component;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
    private final ItemsConfig cfg = ItemsConfig.getInstance();
    private final CooldownManager cooldownManager;

    private final Map<UUID, Map<UUID, MythicItem>> playerMythics;
    private final Map<UUID, Set<MythicItem>> sessionPurchasedMythics;
    private final Map<UUID, List<MythicItem>> sessionAvailableMythics;

    // Sandstormer charge tracking
    private final Map<UUID, Integer> sandstormerShotsRemaining;

    // BlazeBite shots tracking (shared between both crossbows)
    private final Map<UUID, Integer> blazebiteShotsRemaining;

    // Active tasks for cleanup
    private final Map<UUID, List<BukkitTask>> activeTasks;

    private MythicItemManager() {
        cooldownManager = CooldownManager.getInstance();
        playerMythics = new HashMap<>();
        sessionPurchasedMythics = new HashMap<>();
        sessionAvailableMythics = new HashMap<>();
        sandstormerShotsRemaining = new HashMap<>();
        blazebiteShotsRemaining = new HashMap<>();
        activeTasks = new HashMap<>();
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

    /**
     * Check if a specific mythic is available in this session.
     */
    public boolean isMythicAvailableInSession(GameSession session, MythicItem mythic) {
        if (session == null || mythic == null) return false;
        List<MythicItem> available = getAvailableMythics(session);
        return available.contains(mythic);
    }

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

    /**
     * Check if an item is a BlazeBite crossbow and return its mode.
     * @return "glacier", "volcano", or null if not a BlazeBite item
     */
    public String getBlazebiteMode(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(Keys.BLAZEBITE_MODE, PersistentDataType.STRING)) return null;
        return pdc.get(Keys.BLAZEBITE_MODE, PersistentDataType.STRING);
    }

    /**
     * Check if a BlazeBite crossbow is in Glacier mode.
     */
    public boolean isGlacierMode(ItemStack item) {
        return "glacier".equals(getBlazebiteMode(item));
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
            case BLOODWRENCH_CROSSBOW -> meta.addEnchant(Enchantment.MULTISHOT, 1, false);
            default -> {
            }
        }
    }

    // ==================== COIN CLEAVER ====================

    /**
     * Handle Coin Cleaver damage bonus against richer players.
     * +25% damage if victim has more coins than attacker.
     */
    public double handleCoinCleaverDamage(Player attacker, Player victim, double baseDamage) {
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

        if (victimCcp.getCoins() > attackerCcp.getCoins()) {
            double newDamage = baseDamage * ItemsConfig.getInstance().getCoinCleaverDamageBonus();
            Messages.debug(attacker, "COIN_CLEAVER: +25% damage! (" + baseDamage + " -> " + newDamage + ") Victim has more coins");
            return newDamage;
        }
        Messages.debug(attacker, "COIN_CLEAVER: No bonus - you have more coins");
        return baseDamage;
    }

    /**
     * Check if player should have no knockback (Coin Cleaver in hand/offhand).
     * Sturdy Feet passive ability.
     */
    public boolean hasCoinCleaverNoKnockback(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        boolean hasIt = PDCDetection.getMythic(mainHand) == MythicItem.COIN_CLEAVER ||
               PDCDetection.getMythic(offHand) == MythicItem.COIN_CLEAVER;

        if (hasIt) {
            Messages.debug(player, "COIN_CLEAVER: Sturdy Feet - No knockback applied");
        }
        return hasIt;
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
        cooldownManager.setCooldown(uuid, CooldownManager.Keys.COIN_CLEAVER_GRENADE, cfg.getCoinCleaverGrenadeCooldown());

        Messages.debug(player, "COIN_CLEAVER: Grenade fired! Cost: $" + cfg.getCoinCleaverGrenadeCost() + ", Cooldown: " + cfg.getCoinCleaverGrenadeCooldown() + "s");

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        // Instant explosion at feet
        world.spawnParticle(Particle.EXPLOSION, loc, 1);
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

        cooldownManager.setCooldown(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_SLASH, cfg.getCarlsSpinCooldown());
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
                if (axeDisplay != null && axeDisplay.isValid()) {
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
                        victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);
                        Messages.debug(attacker, "CARLS_BATTLEAXE: Spin hit " + victim.getName() + " for " + damage + " damage");
                    }
                }

                // Spinning particles around player
                if (ticks % 2 == 0) {
                    double particleAngle = angle + Math.PI;
                    double px = Math.cos(particleAngle) * radius;
                    double pz = Math.sin(particleAngle) * radius;
                    attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                            attacker.getLocation().add(px, 1, pz), 1, 0, 0, 0, 0);
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

        cooldownManager.setCooldown(uuid, CooldownManager.Keys.CARLS_BATTLEAXE_CRIT, cfg.getCarlsCritCooldown() / 1000);

        // Launch victim into the air with consistent velocity
        double launchPower = cfg.getCarlsCritLaunchPower();
        Vector launchVelocity = new Vector(0, launchPower, 0);

        // Add slight horizontal push away from attacker for consistency
        Vector direction = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize();
        direction.setY(0);
        launchVelocity.add(direction.multiply(0.3));

        victim.setVelocity(launchVelocity);

        Messages.debug(attacker, "CARLS_BATTLEAXE: Launched " + victim.getName() + " with power " + launchPower);
        SoundUtils.play(victim, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.8f);
        victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        Messages.send(attacker, "<gold>Critical launch!</gold>");
    }

    // ==================== WIND BOW ====================

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

        cooldownManager.setCooldown(uuid, CooldownManager.Keys.WIND_BOW_BOOST, cfg.getWindBowBoostCooldown());

        Vector direction = player.getLocation().getDirection();
        direction.setY(Math.max(direction.getY() + 0.5, 0.5));

        Vector velocity = direction.multiply(cfg.getWindBowBoostPower());
        player.setVelocity(velocity);

        Messages.debug(player, "WIND_BOW: Boosted! Power: " + cfg.getWindBowBoostPower() + ", Cooldown: " + cfg.getWindBowBoostCooldown() + "s");
        SoundUtils.play(player, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);

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
        target.getWorld().spawnParticle(Particle.CLOUD, hitLoc, 30, 1, 1, 1, 0.2);
    }

    // ==================== ELECTRIC EEL SWORD ====================

    /**
     * Electric Eel Sword chain damage.
     * Fully charged hits damage nearby enemies in 5 block radius for 0.5 hearts.
     * 1 second cooldown.
     */
    public void handleElectricEelChain(Player attacker, Player victim) {
        UUID uuid = attacker.getUniqueId();
        

        Messages.debug(attacker, "ELECTRIC_EEL: Chain damage check");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.ELECTRIC_EEL_CHAIN)) {
            Messages.debug(attacker, "ELECTRIC_EEL: Chain on cooldown");
            return;
        }
        cooldownManager.setCooldown(uuid, CooldownManager.Keys.ELECTRIC_EEL_CHAIN, cfg.getEelChainCooldown());

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
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
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

        cooldownManager.setCooldown(uuid, CooldownManager.Keys.ELECTRIC_EEL_LIGHTNING, cfg.getEelTeleportCooldown());

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
        world.spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);

        cooldownManager.setCooldown(uuid, CooldownManager.Keys.ELECTRIC_EEL_LIGHTNING, cfg.getEelTeleportCooldown());
        Messages.debug(player, "ELECTRIC_EEL: Teleported! Distance: " + distance + ", Cooldown: " + cfg.getEelTeleportCooldown() + "s");
        Messages.send(player, "<aqua>Zap!</aqua>");
    }

    /**
     * Handle Goblin Spear hit.
     * Power 4 bow damage (~9 damage) + Poison III for 2 seconds.
     */
    public void handleGoblinSpearHit(Player shooter, LivingEntity victim) {
        Messages.debug(shooter, "GOBLIN_SPEAR: Hit " + victim.getName());

        victim.damage(cfg.getGoblinSpearDamage(), shooter);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, cfg.getGoblinPoisonDuration(), cfg.getGoblinPoisonLevel(), false, true));

        Messages.debug(shooter, "GOBLIN_SPEAR: Dealt " + cfg.getGoblinSpearDamage() + " damage + Poison III");

        victim.getWorld().spawnParticle(Particle.ITEM_SLIME, victim.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.1);
    }

    // ==================== BLOODWRENCH_CROSSBOW ====================

    /**
     * Handle Sandstormer shot.
     * Burst attack - 3 arrows before reload.
     * 14 second cooldown after all shots used.
     */
    public boolean handleBloodwrenchShot(Player player) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "BLOODWRENCH_CROSSBOW: Shot triggered");

        int shots = sandstormerShotsRemaining.getOrDefault(uuid, cfg.getBloodwrenchBurstShots());
        if (shots <= 0) {
            if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.SANDSTORMER_RELOAD)) {
                Messages.debug(player, "BLOODWRENCH_CROSSBOW: Reloading - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.SANDSTORMER_RELOAD) + "s");
                Messages.send(player, "<red>Sandstormer reloading! (" + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.SANDSTORMER_RELOAD) + "s)</red>");
                return false;
            }
            sandstormerShotsRemaining.put(uuid, cfg.getBloodwrenchBurstShots());
            shots = cfg.getBloodwrenchBurstShots();
            Messages.debug(player, "BLOODWRENCH_CROSSBOW: Reloaded! Shots reset to " + shots);
        }

        sandstormerShotsRemaining.put(uuid, shots - 1);
        Messages.debug(player, "BLOODWRENCH_CROSSBOW: Shot fired! Remaining: " + (shots - 1));

        if (shots - 1 <= 0) {
            cooldownManager.setCooldown(uuid, CooldownManager.Keys.SANDSTORMER_RELOAD, cfg.getBloodwrenchReloadCooldown());
            Messages.debug(player, "BLOODWRENCH_CROSSBOW: Out of shots, reloading for " + cfg.getBloodwrenchReloadCooldown() + "s");
            Messages.send(player, "<yellow>Sandstormer reloading...</yellow>");
        }

        return true;
    }

    /**
     * Start charging Sandstormer for supercharged shot.
     */
    public void startBloodwrenchCharge(Player player) {
        cooldownManager.setTimestamp(player.getUniqueId(), CooldownManager.Keys.SANDSTORMER_CHARGE);
        Messages.debug(player, "BLOODWRENCH_CROSSBOW: Started charging for supercharged shot");
    }

    /**
     * Check if Sandstormer is supercharged (held charged for 28 seconds).
     */
    public boolean isBloodwrenchSupercharged(Player player) {
        long start = cooldownManager.getTimestamp(player.getUniqueId(), CooldownManager.Keys.SANDSTORMER_CHARGE);
        if (start == 0) return false;
        boolean supercharged = cooldownManager.hasTimePassedSeconds(player.getUniqueId(), CooldownManager.Keys.SANDSTORMER_CHARGE,
                ItemsConfig.getInstance().getBloodwrenchSuperchargeTime() / 1000);
        if (supercharged) {
            Messages.debug(player, "BLOODWRENCH_CROSSBOW: Supercharged! (held for 28s+)");
        }
        return supercharged;
    }

    /**
     * Fire supercharged Sandstormer shot.
     * Creates sandstorm effect dealing 1-3 hearts per second for 4 seconds.
     * Target gets Levitation IV for 4 seconds.
     */
    public void fireSuperchargedSandstormer(Player shooter, Player victim) {
        cooldownManager.clearTimestamp(shooter.getUniqueId(), CooldownManager.Keys.SANDSTORMER_CHARGE);

        Messages.debug(shooter, "BLOODWRENCH_CROSSBOW: Supercharged shot hit " + victim.getName());

        // Sandstorm damage effect for 4 seconds (damage every second)
        BukkitTask damageTask = SchedulerUtils.runTaskTimer(() -> {
            if (!victim.isOnline() || victim.isDead()) return;

            // Damage 1-3 hearts randomly (2-6 damage)
            int hearts = 1 + ThreadLocalRandom.current().nextInt(3);
            double damage = hearts * 2.0;
            victim.damage(damage, shooter);

            // Sand particles around victim
            victim.getWorld().spawnParticle(Particle.BLOCK,
                    victim.getLocation().add(0, 1, 0),
                    50, 2, 2, 2, 0.5,
                    Material.SAND.createBlockData()
            );
        }, 0L, 20L);

        // Levitation IV for 4 seconds
        victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, cfg.getBloodwrenchDuration(), 3, false, true));

        SchedulerUtils.runTaskLater(Objects.requireNonNull(damageTask)::cancel, cfg.getBloodwrenchDuration());

        activeTasks.computeIfAbsent(shooter.getUniqueId(), k -> new ArrayList<>()).add(damageTask);

        Messages.debug(shooter, "BLOODWRENCH_CROSSBOW: Sandstorm activated! Duration: " + (cfg.getBloodwrenchDuration() / 20) + "s");
        SoundUtils.play(victim, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.5f);
        Messages.send(shooter, "<gold>Supercharged shot!</gold>");
    }

    // ==================== WARDEN GLOVES ====================

    /**
     * Warden Gloves shockwave attack.
     * Unleashes shockwave dealing 6 hearts damage + big knockback in cone.
     * 41 second cooldown.
     */
    public void useWardenShockwave(Player player) {
        UUID uuid = player.getUniqueId();
        

        Messages.debug(player, "WARDEN_GLOVES: Shockwave ability triggered");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE)) {
            Messages.debug(player, "WARDEN_GLOVES: Shockwave on cooldown - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE) + "s");
            Messages.send(player, "<red>Shockwave on cooldown! (" + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE) + "s)</red>");
            return;
        }

        cooldownManager.setCooldown(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE, cfg.getWardenShockwaveCooldown());

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
        world.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(direction.clone().multiply(2)).add(0, 1, 0), 1);
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

    /**
     * Warden Gloves melee attack.
     * Deals Sharp III Netherite Axe damage + Knockback II.
     * 22 second cooldown.
     */
    public void useWardenMelee(Player player, Player victim) {
        UUID uuid = player.getUniqueId();
        

        Messages.debug(player, "WARDEN_GLOVES: Melee attack on " + victim.getName());

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WARDEN_MELEE)) {
            Messages.debug(player, "WARDEN_GLOVES: Melee on cooldown");
            return;
        }

        cooldownManager.setCooldown(uuid, CooldownManager.Keys.WARDEN_MELEE, cfg.getWardenMeleeCooldown());

        // Knockback II equivalent
        Vector knockback = victim.getLocation().toVector()
                .subtract(player.getLocation().toVector())
                .normalize()
                .multiply(1.5)
                .setY(0.5);
        victim.setVelocity(knockback);

        Messages.debug(player, "WARDEN_GLOVES: Melee hit! Knockback applied, cooldown: " + cfg.getWardenMeleeCooldown() + "s");
        SoundUtils.play(victim, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.0f, 1.0f);
    }

    // ==================== BLAZEBITE CROSSBOWS ====================

    /**
     * Handle BlazeBite shot.
     * 8 shots per magazine, 25 second reload.
     * Mode is determined by which crossbow is being used (stored in item PDC).
     */
    public boolean handleBlazebiteShot(Player player, ItemStack crossbow) {
        UUID uuid = player.getUniqueId();
        
        String mode = getBlazebiteMode(crossbow);
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
            cooldownManager.setCooldown(uuid, CooldownManager.Keys.BLAZEBITE_RELOAD, cfg.getBlazebiteReloadCooldown());
            Messages.debug(player, "BLAZEBITE: Out of shots, reloading for " + cfg.getBlazebiteReloadCooldown() + "s");
            Messages.send(player, "<yellow>BlazeBite reloading...</yellow>");
        }

        return true;
    }

    /**
     * Handle BlazeBite hit effects.
     * Glacier: Slowness I + Frostbite for 3 seconds.
     * Volcano: Explosive fire arrow (2 hearts direct, 1 heart splash in 3 blocks).
     */
    public void handleBlazebiteHit(Player shooter, Entity hitEntity, Location hitLoc, boolean isGlacierMode) {
        World world = hitLoc.getWorld();
        if (world == null) return;

        Messages.debug(shooter, "BLAZEBITE: Hit detected (" + (isGlacierMode ? "Glacier" : "Volcano") + " mode)");

        if (isGlacierMode) {
            // Glacier mode - slowness and frostbite
            if (hitEntity instanceof Player victim) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, cfg.getBlazebiteFreezeDuration(), 0, false, true));
                // Freeze ticks need to be set high enough - max is 140 for full freeze effect
                // We set it to 140 + duration ticks so it stays frozen for the duration
                int freezeTicks = 140 + cfg.getBlazebiteFreezeDuration();
                victim.setFreezeTicks(freezeTicks);

                Messages.debug(shooter, "BLAZEBITE: Glacier hit " + victim.getName() + " - Slowness + Frostbite for " + (cfg.getBlazebiteFreezeDuration() / 20) + "s");
                world.spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
                SoundUtils.play(victim, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
            }
        } else {
            // Volcano mode - explosive fire arrow
            world.spawnParticle(Particle.FLAME, hitLoc, 50, 1, 1, 1, 0.2);
            world.spawnParticle(Particle.EXPLOSION, hitLoc, 1);
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

                // Direct hit = 2 hearts, splash = 1 heart
                double damage = entity.equals(hitEntity) ? cfg.getBlazebiteVolcanoDirectDamage() : cfg.getBlazebiteVolcanoSplashDamage();
                target.damage(damage, shooter);
                target.setFireTicks(4 * 20); // Set on fire
                hitCount++;
            }
            Messages.debug(shooter, "BLAZEBITE: Volcano explosion hit " + hitCount + " enemies, radius: " + radius);
        }
    }

    // ==================== PARTICLE EFFECTS ====================

    /**
     * Spawn ambient sand particles for Sandstormer when held in hotbar.
     */
    public void spawnSandstormerParticles(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.BLOCK, loc, 5, 0.5, 0.5, 0.5, 0.01,
            Material.SAND.createBlockData());
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        sandstormerShotsRemaining.clear();
        blazebiteShotsRemaining.clear();

        activeTasks.values().forEach(tasks -> tasks.forEach(BukkitTask::cancel));
        activeTasks.clear();

        // Note: cooldowns are managed by CooldownManager and will be cleared when players are cleared
    }
}

