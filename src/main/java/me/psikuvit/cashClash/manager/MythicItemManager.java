package me.psikuvit.cashClash.manager;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.shop.items.ShopItems;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    private final Map<UUID, Map<UUID, MythicItem>> playerMythics;
    private final Map<UUID, Set<MythicItem>> sessionPurchasedMythics;
    private final Map<UUID, List<MythicItem>> sessionAvailableMythics;
    private final Map<UUID, Map<String, Long>> cooldowns;

    // Sandstormer charge tracking
    private final Map<UUID, Long> sandstormerChargeStart;
    private final Map<UUID, Integer> sandstormerShotsRemaining;

    // BlazeBite mode tracking (true = glacier, false = volcano)
    private final Map<UUID, Boolean> blazebiteMode;
    private final Map<UUID, Integer> blazebiteShotsRemaining;

    // Carl's Battleaxe crit tracking
    private final Map<UUID, Long> carlsCritCooldown;

    // Active tasks for cleanup
    private final Map<UUID, List<BukkitTask>> activeTasks;

    private MythicItemManager() {
        playerMythics = new HashMap<>();
        sessionPurchasedMythics = new HashMap<>();
        sessionAvailableMythics = new HashMap<>();
        cooldowns = new HashMap<>();
        sandstormerChargeStart = new HashMap<>();
        sandstormerShotsRemaining = new HashMap<>();
        blazebiteMode = new HashMap<>();
        blazebiteShotsRemaining = new HashMap<>();
        carlsCritCooldown = new HashMap<>();
        activeTasks = new HashMap<>();
            
    }

    public static MythicItemManager getInstance() {
        if (instance == null) {
            instance = new MythicItemManager();
        }
        return instance;
    }

    // ==================== MYTHIC DETECTION ====================

    public MythicItem getMythicType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String typeStr = pdc.get(Keys.MYTHIC_ITEM_KEY, PersistentDataType.STRING);

        if (typeStr == null) return null;

        return ShopItems.getMythic(typeStr);
    }

    // ==================== PURCHASE & OWNERSHIP ====================

    /**
     * Check if a player can purchase a mythic (only one per player per game).
     * Returns false if the player already owns a mythic.
     */
    public boolean canPlayerPurchaseMythic(GameSession session, UUID playerUuid) {
        if (session == null || playerUuid == null) return true;
        UUID sessionId = session.getSessionId();
        Map<UUID, MythicItem> sessionPlayerMythics = playerMythics.get(sessionId);
        if (sessionPlayerMythics == null) return true;
        return !sessionPlayerMythics.containsKey(playerUuid);
    }

    /**
     * Check if a specific mythic is still available for purchase (not bought by anyone).
     */
    public boolean isMythicAvailable(GameSession session, MythicItem mythic) {
        if (session == null || mythic == null) return true;
        UUID sessionId = session.getSessionId();
        Set<MythicItem> purchased = sessionPurchasedMythics.get(sessionId);
        if (purchased == null) return true;
        return !purchased.contains(mythic);
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
        return sessionPlayerMythics.get(playerUuid);
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

        CashClashPlugin.getInstance().getLogger().info(
            "Selected " + count + " legendaries for session " + sessionId + ": " +
            selectedMythics.stream().map(MythicItem::getDisplayName).reduce((a, b) -> a + ", " + b).orElse("none")
        );
    }

    /**
     * Get the available legendaries for a game session.
     * Returns an empty list if none have been selected.
     */
    public List<MythicItem> getAvailableLegendaries(GameSession session) {
        if (session == null) return Collections.emptyList();
        UUID sessionId = session.getSessionId();
        return sessionAvailableMythics.getOrDefault(sessionId, Collections.emptyList());
    }

    /**
     * Check if a specific mythic is available in this session.
     */
    public boolean isMythicAvailableInSession(GameSession session, MythicItem mythic) {
        if (session == null || mythic == null) return false;
        List<MythicItem> available = getAvailableLegendaries(session);
        return available.contains(mythic);
    }

    /**
     * Create the mythic item with proper tags and appearance.
     */
    public ItemStack createMythicItem(MythicItem mythic, Player owner) {
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
        pdc.set(Keys.MYTHIC_ITEM_KEY, PersistentDataType.STRING, mythic.name());
        pdc.set(Keys.CUSTOM_ITEM_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());

        // Apply special attributes based on mythic type
        applyMythicAttributes(mythic, meta);

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);

        return item;
    }

    private void applyMythicAttributes(MythicItem mythic, ItemMeta meta) {
        // NOTE: Mythics come with NO enchants when first purchased
        // Players must buy enchants separately from the shop
        switch (mythic) {
            case COIN_CLEAVER -> {
                // Diamond axe with base stats - no modifications needed
            }
            case CARLS_BATTLEAXE -> {
                // Netherite axe with base stats - no modifications needed
            }
            case ELECTRIC_EEL_SWORD -> {
                // Diamond sword damage - no modifications needed
            }
            case GOBLIN_SPEAR -> {
                // Trident with faster attack speed and +1 block range
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
                // Note: NO sharpness enchant - must be bought separately
            }
            case SANDSTORMER -> {
                // Crossbow - no base modifications
            }
            case BLAZEBITE_CROSSBOWS -> {
                // NO enchants on purchase - must buy them separately
            }
            case WIND_BOW -> {
                // Bow - no base modifications
            }
        }
    }

    // ==================== COOLDOWN MANAGEMENT ====================

    private boolean isOnCooldown(UUID player, String ability) {
        Map<String, Long> playerCooldowns = cooldowns.get(player);
        if (playerCooldowns == null) return false;
        Long cooldownEnd = playerCooldowns.get(ability);
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }

    private long getRemainingCooldown(UUID player, String ability) {
        Map<String, Long> playerCooldowns = cooldowns.get(player);
        if (playerCooldowns == null) return 0;
        Long cooldownEnd = playerCooldowns.get(ability);
        if (cooldownEnd == null) return 0;
        return Math.max(0, (cooldownEnd - System.currentTimeMillis()) / 1000);
    }

    private void setCooldown(UUID player, String ability, long seconds) {
        cooldowns.computeIfAbsent(player, k -> new HashMap<>())
                 .put(ability, System.currentTimeMillis() + (seconds * 1000));
    }

    // ==================== COIN CLEAVER ====================

    /**
     * Handle Coin Cleaver damage bonus against richer players.
     * +25% damage if victim has more coins than attacker.
     */
    public double handleCoinCleaverDamage(Player attacker, Player victim, double baseDamage) {
        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
        if (session == null) return baseDamage;

        CashClashPlayer attackerCcp = session.getCashClashPlayer(attacker.getUniqueId());
        CashClashPlayer victimCcp = session.getCashClashPlayer(victim.getUniqueId());

        if (attackerCcp == null || victimCcp == null) return baseDamage;

        if (victimCcp.getCoins() > attackerCcp.getCoins()) {
            return baseDamage * ItemsConfig.getInstance().getCoinCleaverDamageBonus();
        }
        return baseDamage;
    }

    /**
     * Check if player should have no knockback (Coin Cleaver in hand/offhand).
     * Sturdy Feet passive ability.
     */
    public boolean hasCoinCleaverNoKnockback(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        return getMythicType(mainHand) == MythicItem.COIN_CLEAVER ||
               getMythicType(offHand) == MythicItem.COIN_CLEAVER;
    }

    /**
     * Coin Cleaver right-click grenade.
     * Instant grenade at feet dealing 2 hearts in 5 block radius.
     * Costs $2,000, 3 second cooldown.
     */
    public void useCoinCleaverGrenade(Player player) {
        UUID uuid = player.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (isOnCooldown(uuid, "coin_cleaver_grenade")) {
            Messages.send(player, "<red>Grenade on cooldown! (" + getRemainingCooldown(uuid, "coin_cleaver_grenade") + "s)</red>");
            return;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        CashClashPlayer ccp = session.getCashClashPlayer(uuid);
        if (ccp == null || ccp.getCoins() < cfg.getCoinCleaverGrenadeCost()) {
            Messages.send(player, "<red>Not enough coins! (Costs $" + String.format("%,d", cfg.getCoinCleaverGrenadeCost()) + ")</red>");
            return;
        }

        ccp.deductCoins(cfg.getCoinCleaverGrenadeCost());
        setCooldown(uuid, "coin_cleaver_grenade", cfg.getCoinCleaverGrenadeCooldown());

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        // Instant explosion at feet
        world.spawnParticle(Particle.EXPLOSION, loc, 1);
        SoundUtils.playAt(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

        Team playerTeam = session.getPlayerTeam(player);
        int radius = cfg.getCoinCleaverGrenadeRadius();

        // Damage enemies in radius (does NOT hurt player or teammates)
        for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;

            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam != null && playerTeam != null &&
                targetTeam.getTeamNumber() == playerTeam.getTeamNumber()) continue;

            target.damage(cfg.getCoinCleaverGrenadeDamage(), player);
        }

        Messages.send(player, "<gold>-$" + String.format("%,d", cfg.getCoinCleaverGrenadeCost()) + " for grenade!</gold>");
    }

    // ==================== CARL'S BATTLEAXE ====================

    /**
     * Handle Carl's Battleaxe charged attack.
     * Fully charged hit grants Speed III + Strength I for 25 seconds.
     * 45 second cooldown.
     */
    public void handleCarlsChargedAttack(Player attacker) {
        UUID uuid = attacker.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (isOnCooldown(uuid, "carls_charged")) {
            return;
        }

        setCooldown(uuid, "carls_charged", cfg.getCarlsChargedCooldown());

        // Grant Speed III (level 2) and Strength I (level 0) for 25 seconds
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, cfg.getCarlsBuffDuration(), 2, false, true));
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, cfg.getCarlsBuffDuration(), 0, false, true));

        Messages.send(attacker, "<gold>Carl's Battleaxe activated! Speed III + Strength I for 25s!</gold>");
        SoundUtils.play(attacker, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
    }

    /**
     * Handle Carl's Battleaxe critical hit launch.
     * Critical hits (while falling) launch enemies into the air.
     * 10 second cooldown.
     */
    public void handleCarlsCriticalHit(Player attacker, Player victim) {
        UUID uuid = attacker.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();
        long now = System.currentTimeMillis();

        Long lastCrit = carlsCritCooldown.get(uuid);
        if (lastCrit != null && now - lastCrit < cfg.getCarlsCritCooldown()) return;

        carlsCritCooldown.put(uuid, now);

        // Launch victim into the air
        victim.setVelocity(new Vector(0, cfg.getCarlsCritLaunchPower(), 0));

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
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (isOnCooldown(uuid, "wind_bow_boost")) {
            Messages.send(player, "<red>Wind boost on cooldown! (" + getRemainingCooldown(uuid, "wind_bow_boost") + "s)</red>");
            return;
        }

        setCooldown(uuid, "wind_bow_boost", cfg.getWindBowBoostCooldown());

        Vector direction = player.getLocation().getDirection();
        direction.setY(Math.max(direction.getY() + 0.5, 0.5)); // Ensure upward boost
        player.setVelocity(direction.multiply(cfg.getWindBowBoostPower()));

        SoundUtils.play(player, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);

        Messages.send(player, "<aqua>Wind boost!</aqua>");
    }

    /**
     * Wind Bow arrow hit - propel target and nearby players backwards.
     * Acts as a wind gust in 3 block radius. Passive ability.
     */
    public void handleWindBowHit(Player shooter, Player target) {
        ItemsConfig cfg = ItemsConfig.getInstance();
        Location hitLoc = target.getLocation();
        Vector pushDirection = hitLoc.toVector().subtract(shooter.getLocation().toVector()).normalize();
        pushDirection.setY(0.3);

        // Push target and all nearby players
        for (Entity entity : target.getWorld().getNearbyEntities(hitLoc, cfg.getWindBowPushRadius(), cfg.getWindBowPushRadius(), cfg.getWindBowPushRadius())) {
            if (!(entity instanceof Player p)) continue;
            if (p.equals(shooter)) continue;

            p.setVelocity(pushDirection.clone().multiply(cfg.getWindBowPushPower()));
        }

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
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (isOnCooldown(uuid, "eel_chain")) return;
        setCooldown(uuid, "eel_chain", cfg.getEelChainCooldown());

        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
        if (session == null) return;

        Team attackerTeam = session.getPlayerTeam(attacker);
        Location victimLoc = victim.getLocation();
        int radius = cfg.getEelChainRadius();

        // Chain damage to nearby enemies
        for (Entity entity : victim.getWorld().getNearbyEntities(victimLoc, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(attacker) || target.equals(victim)) continue;

            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam != null && attackerTeam != null &&
                targetTeam.getTeamNumber() == attackerTeam.getTeamNumber()) continue;

            target.damage(cfg.getEelChainDamage(), attacker);

            // Lightning spark effect
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
        }
        SoundUtils.playAt(victimLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.5f);
    }

    /**
     * Electric Eel Sword teleport.
     * Zaps player 4 blocks forward but not through walls.
     * 15 second cooldown.
     */
    public void useElectricEelTeleport(Player player) {
        UUID uuid = player.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (isOnCooldown(uuid, "eel_teleport")) {
            Messages.send(player, "<red>Teleport on cooldown! (" + getRemainingCooldown(uuid, "eel_teleport") + "s)</red>");
            return;
        }

        Location start = player.getEyeLocation();
        Vector direction = start.getDirection();
        World world = player.getWorld();

        double distance = cfg.getEelTeleportDistance();
        // Ray trace to find destination (stop at walls)
        RayTraceResult result = world.rayTraceBlocks(start, direction, distance, FluidCollisionMode.NEVER, true);

        Location destination;
        if (result != null && result.getHitBlock() != null) {
            // Hit a wall, teleport just before it
            destination = result.getHitPosition().toLocation(world).subtract(direction.multiply(0.5));
        } else {
            // No wall, teleport full distance
            destination = start.add(direction.multiply(distance));
        }

        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());

        // Effects at start location
        world.spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
        SoundUtils.playAt(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);

        player.teleport(destination);

        // Effects at destination
        world.spawnParticle(Particle.ELECTRIC_SPARK, destination.add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);

        setCooldown(uuid, "eel_teleport", cfg.getEelTeleportCooldown());
        Messages.send(player, "<aqua>Zap!</aqua>");
    }

    // ==================== GOBLIN SPEAR ====================

    /**
     * Throw Goblin Spear like a trident.
     * Deals Power 4 bow damage + Poison III for 2 seconds. Goes through shields.
     * 15 second cooldown.
     */
    public void throwGoblinSpear(Player player) {
        UUID uuid = player.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (isOnCooldown(uuid, "goblin_throw")) {
            Messages.send(player, "<red>Spear throw on cooldown! (" + getRemainingCooldown(uuid, "goblin_throw") + "s)</red>");
            return;
        }

        setCooldown(uuid, "goblin_throw", cfg.getGoblinThrowCooldown());

        // Launch trident projectile
        Trident trident = player.launchProjectile(Trident.class);
        trident.setVelocity(player.getLocation().getDirection().multiply(2.5));

        // Tag it as goblin spear for hit detection
        trident.getPersistentDataContainer().set(Keys.MYTHIC_ITEM_KEY, PersistentDataType.STRING, "GOBLIN_SPEAR");

        SoundUtils.play(player, Sound.ITEM_TRIDENT_THROW, 1.0f, 1.0f);
        Messages.send(player, "<green>Goblin Spear thrown!</green>");

        // Remove the trident after 5 seconds if it hasn't hit anything
        SchedulerUtils.runTaskLater(() -> {
            if (trident.isValid() && !trident.isDead()) {
                trident.remove();
            }
        }, 5 * 20L);

    }

    /**
     * Handle Goblin Spear hit.
     * Power 4 bow damage (~9 damage) + Poison III for 2 seconds.
     */
    public void handleGoblinSpearHit(Player shooter, Player victim, Trident trident) {
        ItemsConfig cfg = ItemsConfig.getInstance();
        
        // Apply damage (goes through shields via direct damage)
        victim.damage(cfg.getGoblinSpearDamage(), shooter);

        // Apply Poison III for 2 seconds
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, cfg.getGoblinPoisonDuration(), cfg.getGoblinPoisonLevel(), false, true));

        // Visual effects
        victim.getWorld().spawnParticle(Particle.ITEM_SLIME, victim.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.1);
        SoundUtils.play(victim, Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.8f);

        trident.remove();
    }

    // ==================== SANDSTORMER ====================

    /**
     * Handle Sandstormer shot.
     * Burst attack - 3 arrows before reload.
     * 14 second cooldown after all shots used.
     */
    public boolean handleSandstormerShot(Player player) {
        UUID uuid = player.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        int shots = sandstormerShotsRemaining.getOrDefault(uuid, cfg.getSandstormerBurstShots());
        if (shots <= 0) {
            if (isOnCooldown(uuid, "sandstormer")) {
                Messages.send(player, "<red>Sandstormer reloading! (" + getRemainingCooldown(uuid, "sandstormer") + "s)</red>");
                return false;
            }
            sandstormerShotsRemaining.put(uuid, cfg.getSandstormerBurstShots());
            shots = cfg.getSandstormerBurstShots();
        }

        sandstormerShotsRemaining.put(uuid, shots - 1);

        if (shots - 1 <= 0) {
            setCooldown(uuid, "sandstormer", cfg.getSandstormerReloadCooldown());
            Messages.send(player, "<yellow>Sandstormer reloading...</yellow>");
        }

        return true;
    }

    /**
     * Start charging Sandstormer for supercharged shot.
     */
    public void startSandstormerCharge(Player player) {
        sandstormerChargeStart.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Check if Sandstormer is supercharged (held charged for 28 seconds).
     */
    public boolean isSandstormerSupercharged(Player player) {
        Long start = sandstormerChargeStart.get(player.getUniqueId());
        if (start == null) return false;
        return System.currentTimeMillis() - start >= ItemsConfig.getInstance().getSandstormerSuperchargeTime();
    }

    /**
     * Fire supercharged Sandstormer shot.
     * Creates sandstorm effect dealing 1-3 hearts per second for 4 seconds.
     * Target gets Levitation IV for 4 seconds.
     */
    public void fireSuperchargedSandstormer(Player shooter, Player victim) {
        sandstormerChargeStart.remove(shooter.getUniqueId());
        ItemsConfig cfg = ItemsConfig.getInstance();

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
        victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, cfg.getSandstormerStormDuration(), 3, false, true));

        SchedulerUtils.runTaskLater(damageTask::cancel, cfg.getSandstormerStormDuration());

        // Track task for cleanup
        activeTasks.computeIfAbsent(shooter.getUniqueId(), k -> new ArrayList<>()).add(damageTask);

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
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (isOnCooldown(uuid, "warden_shockwave")) {
            Messages.send(player, "<red>Shockwave on cooldown! (" + getRemainingCooldown(uuid, "warden_shockwave") + "s)</red>");
            return;
        }

        setCooldown(uuid, "warden_shockwave", cfg.getWardenShockwaveCooldown());

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        Team playerTeam = session.getPlayerTeam(player);
        Location loc = player.getLocation();
        Vector direction = loc.getDirection().setY(0).normalize();
        World world = player.getWorld();

        // Sonic boom visual effect
        world.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(direction.clone().multiply(2)).add(0, 1, 0), 1);
        SoundUtils.playAt(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);

        int range = cfg.getWardenShockwaveRange();
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
        }

        Messages.send(player, "<dark_aqua>SHOCKWAVE!</dark_aqua>");
    }

    /**
     * Warden Gloves melee attack.
     * Deals Sharp III Netherite Axe damage + Knockback II.
     * 22 second cooldown.
     */
    public void useWardenMelee(Player player, Player victim) {
        UUID uuid = player.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        if (isOnCooldown(uuid, "warden_melee")) {
            return;
        }

        setCooldown(uuid, "warden_melee", cfg.getWardenMeleeCooldown());

        // Knockback II equivalent
        Vector knockback = victim.getLocation().toVector()
                .subtract(player.getLocation().toVector())
                .normalize()
                .multiply(1.5)
                .setY(0.5);
        victim.setVelocity(knockback);

        SoundUtils.play(victim, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.0f, 1.0f);
    }

    // ==================== BLAZEBITE CROSSBOWS ====================

    /**
     * Toggle BlazeBite mode between Glacier and Volcano.
     * Can only toggle when not reloading and at full shots.
     */
    public void toggleBlazebiteMode(Player player) {
        UUID uuid = player.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();
        boolean isGlacier = blazebiteMode.getOrDefault(uuid, true);

        if (isOnCooldown(uuid, "blazebite")) {
            Messages.send(player, "<red>Cannot switch modes while reloading!</red>");
            return;
        }

        blazebiteMode.put(uuid, !isGlacier);
        blazebiteShotsRemaining.put(uuid, cfg.getBlazebiteShotsPerMag());

        String mode = !isGlacier ? "<aqua>Glacier</aqua>" : "<red>Volcano</red>";
        Messages.send(player, "<light_purple>BlazeBite switched to " + mode + " mode!</light_purple>");
        SoundUtils.play(player, Sound.ITEM_CROSSBOW_LOADING_END, 1.0f, isGlacier ? 0.5f : 1.5f);
    }

    /**
     * Handle BlazeBite shot.
     * 8 shots per magazine, 25 second reload.
     */
    public boolean handleBlazebiteShot(Player player) {
        UUID uuid = player.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        int shots = blazebiteShotsRemaining.getOrDefault(uuid, cfg.getBlazebiteShotsPerMag());
        if (shots <= 0) {
            if (isOnCooldown(uuid, "blazebite")) {
                Messages.send(player, "<red>BlazeBite reloading! (" + getRemainingCooldown(uuid, "blazebite") + "s)</red>");
                return false;
            }
            blazebiteShotsRemaining.put(uuid, cfg.getBlazebiteShotsPerMag());
            shots = cfg.getBlazebiteShotsPerMag();
        }

        blazebiteShotsRemaining.put(uuid, shots - 1);

        if (shots - 1 <= 0) {
            setCooldown(uuid, "blazebite", cfg.getBlazebiteReloadCooldown());
            Messages.send(player, "<yellow>BlazeBite reloading...</yellow>");
        }

        return true;
    }

    /**
     * Handle BlazeBite hit effects.
     * Glacier: Slowness I + Frostbite for 3 seconds.
     * Volcano: Explosive fire arrow (2 hearts direct, 1 heart splash in 3 blocks).
     */
    public void handleBlazebiteHit(Player shooter, Entity hitEntity, Location hitLoc) {
        UUID uuid = shooter.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();
        boolean isGlacier = blazebiteMode.getOrDefault(uuid, true);
        World world = hitLoc.getWorld();
        if (world == null) return;

        if (isGlacier) {
            // Glacier mode - slowness and frostbite
            if (hitEntity instanceof Player victim) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, cfg.getBlazebiteFreezeDuration(), 0, false, true));
                victim.setFreezeTicks(cfg.getBlazebiteFreezeDuration()); // Frostbite effect

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
            }
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

    /**
     * Reset all mythic tracking for a player (called on round end/disconnect).
     */
    public void resetForPlayer(UUID playerId) {
        cooldowns.remove(playerId);
        sandstormerChargeStart.remove(playerId);
        sandstormerShotsRemaining.remove(playerId);
        blazebiteMode.remove(playerId);
        blazebiteShotsRemaining.remove(playerId);
        carlsCritCooldown.remove(playerId);

        List<BukkitTask> tasks = activeTasks.remove(playerId);
        if (tasks != null) {
            tasks.forEach(BukkitTask::cancel);
        }
    }

    /**
     * Full cleanup (called on plugin disable).
     */
    public void cleanup() {
        cooldowns.clear();
        sandstormerChargeStart.clear();
        sandstormerShotsRemaining.clear();
        blazebiteMode.clear();
        blazebiteShotsRemaining.clear();
        carlsCritCooldown.clear();
        sessionAvailableMythics.clear();

        activeTasks.values().forEach(tasks -> tasks.forEach(BukkitTask::cancel));
        activeTasks.clear();
    }
}

