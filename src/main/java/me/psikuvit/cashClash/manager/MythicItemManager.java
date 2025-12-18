package me.psikuvit.cashClash.manager;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.SoundUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages Mythic (Legendary) item behaviors, cooldowns, and special abilities.
 * Each team can only purchase ONE mythic per game.
 */
public class MythicItemManager {

    // ==================== CONSTANTS ====================

    // Coin Cleaver
    private static final int COIN_CLEAVER_GRENADE_COOLDOWN = 3;
    private static final int COIN_CLEAVER_GRENADE_COST = 2000;
    private static final double COIN_CLEAVER_DAMAGE_BONUS = 1.25;
    private static final double COIN_CLEAVER_GRENADE_DAMAGE = 4.0; // 2 hearts
    private static final int COIN_CLEAVER_GRENADE_RADIUS = 5;

    // Carl's Battleaxe
    private static final int CARLS_CHARGED_COOLDOWN = 45;
    private static final int CARLS_BUFF_DURATION = 25 * 20; // 25 seconds in ticks
    private static final int CARLS_CRIT_COOLDOWN = 10000; // 10 seconds in ms
    private static final double CARLS_CRIT_LAUNCH_POWER = 1.5;

    // Wind Bow
    private static final int WIND_BOW_BOOST_COOLDOWN = 30;
    private static final double WIND_BOW_BOOST_POWER = 2.0;
    private static final int WIND_BOW_PUSH_RADIUS = 3;
    private static final double WIND_BOW_PUSH_POWER = 1.5;

    // Electric Eel Sword
    private static final int EEL_CHAIN_COOLDOWN = 1;
    private static final int EEL_TELEPORT_COOLDOWN = 15;
    private static final double EEL_CHAIN_DAMAGE = 1.0; // 0.5 hearts
    private static final int EEL_CHAIN_RADIUS = 5;
    private static final double EEL_TELEPORT_DISTANCE = 4.0;

    // Goblin Spear
    private static final int GOBLIN_THROW_COOLDOWN = 15;
    private static final double GOBLIN_SPEAR_DAMAGE = 9.0; // Power 4 equivalent
    private static final int GOBLIN_POISON_DURATION = 2 * 20; // 2 seconds
    private static final int GOBLIN_POISON_LEVEL = 2; // Poison III (0-indexed)

    // Sandstormer
    private static final int SANDSTORMER_BURST_SHOTS = 3;
    private static final int SANDSTORMER_RELOAD_COOLDOWN = 14;
    private static final long SANDSTORMER_SUPERCHARGE_TIME = 28000; // 28 seconds in ms
    private static final int SANDSTORMER_STORM_DURATION = 4 * 20; // 4 seconds

    // Warden Gloves
    private static final int WARDEN_SHOCKWAVE_COOLDOWN = 41;
    private static final int WARDEN_MELEE_COOLDOWN = 22;
    private static final double WARDEN_SHOCKWAVE_DAMAGE = 12.0; // 6 hearts
    private static final int WARDEN_SHOCKWAVE_RANGE = 8;
    private static final double WARDEN_KNOCKBACK_POWER = 2.5;

    // BlazeBite Crossbows
    private static final int BLAZEBITE_SHOTS_PER_MAGAZINE = 8;
    private static final int BLAZEBITE_RELOAD_COOLDOWN = 25;
    private static final int BLAZEBITE_FREEZE_DURATION = 3 * 20; // 3 seconds
    private static final double BLAZEBITE_VOLCANO_DIRECT_DAMAGE = 4.0; // 2 hearts
    private static final double BLAZEBITE_VOLCANO_SPLASH_DAMAGE = 2.0; // 1 heart
    private static final int BLAZEBITE_VOLCANO_RADIUS = 3;

    // Number of legendaries to select per game
    private static final int LEGENDARIES_PER_GAME = 5;

    private static MythicItemManager instance;

    // Track which team owns which mythic per game session
    private final Map<UUID, Map<Integer, MythicItem>> teamMythics = new ConcurrentHashMap<>();

    // Track which 5 legendaries are available for each game session
    private final Map<UUID, List<MythicItem>> sessionAvailableMythics = new ConcurrentHashMap<>();

    // Cooldowns per player per ability
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    // Sandstormer charge tracking
    private final Map<UUID, Long> sandstormerChargeStart = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sandstormerShotsRemaining = new ConcurrentHashMap<>();

    // BlazeBite mode tracking (true = glacier, false = volcano)
    private final Map<UUID, Boolean> blazebiteMode = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> blazebiteShotsRemaining = new ConcurrentHashMap<>();

    // Carl's Battleaxe crit tracking
    private final Map<UUID, Long> carlsCritCooldown = new ConcurrentHashMap<>();

    // Active tasks for cleanup
    private final Map<UUID, List<BukkitTask>> activeTasks = new ConcurrentHashMap<>();

    private MythicItemManager() {}

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

        try {
            return MythicItem.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ==================== PURCHASE & OWNERSHIP ====================

    /**
     * Check if a team can purchase a mythic (only one per team per game).
     */
    public boolean canTeamPurchaseMythic(GameSession session, Team team) {
        if (session == null || team == null) return false;
        UUID sessionId = session.getSessionId();
        Map<Integer, MythicItem> sessionMythics = teamMythics.get(sessionId);
        if (sessionMythics == null) return true;
        return !sessionMythics.containsKey(team.getTeamNumber());
    }

    /**
     * Register that a team has purchased a mythic.
     */
    public void registerMythicPurchase(GameSession session, Team team, MythicItem mythic) {
        if (session == null || team == null || mythic == null) return;
        UUID sessionId = session.getSessionId();
        teamMythics.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                   .put(team.getTeamNumber(), mythic);
    }

    /**
     * Unregister a team's mythic purchase (used for refunds).
     */
    public void unregisterMythicPurchase(GameSession session, Team team) {
        if (session == null || team == null) return;
        UUID sessionId = session.getSessionId();
        Map<Integer, MythicItem> sessionMythics = teamMythics.get(sessionId);
        if (sessionMythics != null) {
            sessionMythics.remove(team.getTeamNumber());
        }
    }

    /**
     * Get the mythic owned by a team, if any.
     */
    public MythicItem getTeamMythic(GameSession session, Team team) {
        if (session == null || team == null) return null;
        UUID sessionId = session.getSessionId();
        Map<Integer, MythicItem> sessionMythics = teamMythics.get(sessionId);
        if (sessionMythics == null) return null;
        return sessionMythics.get(team.getTeamNumber());
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
        int count = Math.min(LEGENDARIES_PER_GAME, allMythics.size());
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

                // Sharpness 3 equivalent for netherite axe damage
                meta.addEnchant(Enchantment.SHARPNESS, 3, true);
            }
            case SANDSTORMER -> {
                // Crossbow - no base modifications
            }
            case BLAZEBITE_CROSSBOWS -> {
                // Piercing 3, Quick Charge 1
                meta.addEnchant(Enchantment.PIERCING, 3, true);
                meta.addEnchant(Enchantment.QUICK_CHARGE, 1, true);
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
        cooldowns.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
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
            return baseDamage * COIN_CLEAVER_DAMAGE_BONUS;
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
    public boolean useCoinCleaverGrenade(Player player) {
        UUID uuid = player.getUniqueId();

        if (isOnCooldown(uuid, "coin_cleaver_grenade")) {
            Messages.send(player, "<red>Grenade on cooldown! (" + getRemainingCooldown(uuid, "coin_cleaver_grenade") + "s)</red>");
            return false;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;

        CashClashPlayer ccp = session.getCashClashPlayer(uuid);
        if (ccp == null || ccp.getCoins() < COIN_CLEAVER_GRENADE_COST) {
            Messages.send(player, "<red>Not enough coins! (Costs $" + String.format("%,d", COIN_CLEAVER_GRENADE_COST) + ")</red>");
            return false;
        }

        ccp.deductCoins(COIN_CLEAVER_GRENADE_COST);
        setCooldown(uuid, "coin_cleaver_grenade", COIN_CLEAVER_GRENADE_COOLDOWN);

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return false;

        // Instant explosion at feet
        world.spawnParticle(Particle.EXPLOSION, loc, 1);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

        Team playerTeam = session.getPlayerTeam(player);

        // Damage enemies in radius (does NOT hurt player or teammates)
        for (Entity entity : world.getNearbyEntities(loc, COIN_CLEAVER_GRENADE_RADIUS, COIN_CLEAVER_GRENADE_RADIUS, COIN_CLEAVER_GRENADE_RADIUS)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;

            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam != null && playerTeam != null &&
                targetTeam.getTeamNumber() == playerTeam.getTeamNumber()) continue;

            target.damage(COIN_CLEAVER_GRENADE_DAMAGE, player);
        }

        Messages.send(player, "<gold>-$" + String.format("%,d", COIN_CLEAVER_GRENADE_COST) + " for grenade!</gold>");
        return true;
    }

    // ==================== CARL'S BATTLEAXE ====================

    /**
     * Handle Carl's Battleaxe charged attack.
     * Fully charged hit grants Speed III + Strength I for 25 seconds.
     * 45 second cooldown.
     */
    public void handleCarlsChargedAttack(Player attacker, Player victim) {
        UUID uuid = attacker.getUniqueId();

        if (isOnCooldown(uuid, "carls_charged")) {
            return;
        }

        setCooldown(uuid, "carls_charged", CARLS_CHARGED_COOLDOWN);

        // Grant Speed III (level 2) and Strength I (level 0) for 25 seconds
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, CARLS_BUFF_DURATION, 2, false, true));
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, CARLS_BUFF_DURATION, 0, false, true));

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
        long now = System.currentTimeMillis();

        Long lastCrit = carlsCritCooldown.get(uuid);
        if (lastCrit != null && now - lastCrit < CARLS_CRIT_COOLDOWN) return;

        carlsCritCooldown.put(uuid, now);

        // Launch victim into the air
        victim.setVelocity(new Vector(0, CARLS_CRIT_LAUNCH_POWER, 0));

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

        if (isOnCooldown(uuid, "wind_bow_boost")) {
            Messages.send(player, "<red>Wind boost on cooldown! (" + getRemainingCooldown(uuid, "wind_bow_boost") + "s)</red>");
            return;
        }

        setCooldown(uuid, "wind_bow_boost", WIND_BOW_BOOST_COOLDOWN);

        Vector direction = player.getLocation().getDirection();
        direction.setY(Math.max(direction.getY() + 0.5, 0.5)); // Ensure upward boost
        player.setVelocity(direction.multiply(WIND_BOW_BOOST_POWER));

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

        // Push target and all nearby players
        for (Entity entity : target.getWorld().getNearbyEntities(hitLoc, WIND_BOW_PUSH_RADIUS, WIND_BOW_PUSH_RADIUS, WIND_BOW_PUSH_RADIUS)) {
            if (!(entity instanceof Player p)) continue;
            if (p.equals(shooter)) continue;

            p.setVelocity(pushDirection.clone().multiply(WIND_BOW_PUSH_POWER));
        }

        target.getWorld().playSound(hitLoc, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 0.8f);
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

        if (isOnCooldown(uuid, "eel_chain")) return;
        setCooldown(uuid, "eel_chain", EEL_CHAIN_COOLDOWN);

        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
        if (session == null) return;

        Team attackerTeam = session.getPlayerTeam(attacker);
        Location victimLoc = victim.getLocation();

        // Chain damage to nearby enemies
        for (Entity entity : victim.getWorld().getNearbyEntities(victimLoc, EEL_CHAIN_RADIUS, EEL_CHAIN_RADIUS, EEL_CHAIN_RADIUS)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(attacker) || target.equals(victim)) continue;

            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam != null && attackerTeam != null &&
                targetTeam.getTeamNumber() == attackerTeam.getTeamNumber()) continue;

            target.damage(EEL_CHAIN_DAMAGE, attacker);

            // Lightning spark effect
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
        }

        victim.getWorld().playSound(victimLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.5f);
    }

    /**
     * Electric Eel Sword teleport.
     * Zaps player 4 blocks forward but not through walls.
     * 15 second cooldown.
     */
    public void useElectricEelTeleport(Player player) {
        UUID uuid = player.getUniqueId();

        if (isOnCooldown(uuid, "eel_teleport")) {
            Messages.send(player, "<red>Teleport on cooldown! (" + getRemainingCooldown(uuid, "eel_teleport") + "s)</red>");
            return;
        }

        Location start = player.getEyeLocation();
        Vector direction = start.getDirection();
        World world = player.getWorld();

        // Ray trace to find destination (stop at walls)
        RayTraceResult result = world.rayTraceBlocks(start, direction, EEL_TELEPORT_DISTANCE, FluidCollisionMode.NEVER, true);

        Location destination;
        if (result != null && result.getHitBlock() != null) {
            // Hit a wall, teleport just before it
            destination = result.getHitPosition().toLocation(world).subtract(direction.multiply(0.5));
        } else {
            // No wall, teleport full distance
            destination = start.add(direction.multiply(EEL_TELEPORT_DISTANCE));
        }

        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());

        // Effects at start location
        world.spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);

        player.teleport(destination);

        // Effects at destination
        world.spawnParticle(Particle.ELECTRIC_SPARK, destination.add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);

        setCooldown(uuid, "eel_teleport", EEL_TELEPORT_COOLDOWN);
        Messages.send(player, "<aqua>Zap!</aqua>");
    }

    // ==================== GOBLIN SPEAR ====================

    /**
     * Throw Goblin Spear like a trident.
     * Deals Power 4 bow damage + Poison III for 2 seconds. Goes through shields.
     * 15 second cooldown.
     */
    public void throwGoblinSpear(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();

        if (isOnCooldown(uuid, "goblin_throw")) {
            Messages.send(player, "<red>Spear throw on cooldown! (" + getRemainingCooldown(uuid, "goblin_throw") + "s)</red>");
            return;
        }

        setCooldown(uuid, "goblin_throw", GOBLIN_THROW_COOLDOWN);

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
        // Apply damage (goes through shields via direct damage)
        victim.damage(GOBLIN_SPEAR_DAMAGE, shooter);

        // Apply Poison III for 2 seconds
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, GOBLIN_POISON_DURATION, GOBLIN_POISON_LEVEL, false, true));

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

        int shots = sandstormerShotsRemaining.getOrDefault(uuid, SANDSTORMER_BURST_SHOTS);
        if (shots <= 0) {
            if (isOnCooldown(uuid, "sandstormer")) {
                Messages.send(player, "<red>Sandstormer reloading! (" + getRemainingCooldown(uuid, "sandstormer") + "s)</red>");
                return false;
            }
            sandstormerShotsRemaining.put(uuid, SANDSTORMER_BURST_SHOTS);
            shots = SANDSTORMER_BURST_SHOTS;
        }

        sandstormerShotsRemaining.put(uuid, shots - 1);

        if (shots - 1 <= 0) {
            setCooldown(uuid, "sandstormer", SANDSTORMER_RELOAD_COOLDOWN);
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
        return System.currentTimeMillis() - start >= SANDSTORMER_SUPERCHARGE_TIME;
    }

    /**
     * Fire supercharged Sandstormer shot.
     * Creates sandstorm effect dealing 1-3 hearts per second for 4 seconds.
     * Target gets Levitation IV for 4 seconds.
     */
    public void fireSuperchargedSandstormer(Player shooter, Player victim) {
        sandstormerChargeStart.remove(shooter.getUniqueId());


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
        victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, SANDSTORMER_STORM_DURATION, 3, false, true));

        // Cancel damage task after 4 seconds
        SchedulerUtils.runTaskLater(damageTask::cancel, SANDSTORMER_STORM_DURATION);

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
    public boolean useWardenShockwave(Player player) {
        UUID uuid = player.getUniqueId();

        if (isOnCooldown(uuid, "warden_shockwave")) {
            Messages.send(player, "<red>Shockwave on cooldown! (" + getRemainingCooldown(uuid, "warden_shockwave") + "s)</red>");
            return false;
        }

        setCooldown(uuid, "warden_shockwave", WARDEN_SHOCKWAVE_COOLDOWN);

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;

        Team playerTeam = session.getPlayerTeam(player);
        Location loc = player.getLocation();
        Vector direction = loc.getDirection().setY(0).normalize();
        World world = player.getWorld();

        // Sonic boom visual effect
        world.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(direction.clone().multiply(2)).add(0, 1, 0), 1);
        world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);

        // Damage and knockback enemies in cone
        for (Entity entity : world.getNearbyEntities(loc, WARDEN_SHOCKWAVE_RANGE, 4, WARDEN_SHOCKWAVE_RANGE)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;

            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam != null && playerTeam != null &&
                targetTeam.getTeamNumber() == playerTeam.getTeamNumber()) continue;

            // Check if target is in front of player (cone check)
            Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
            if (direction.dot(toTarget) < 0.3) continue; // Not in cone (about 70 degree cone)

            target.damage(WARDEN_SHOCKWAVE_DAMAGE, player);

            Vector knockback = toTarget.multiply(WARDEN_KNOCKBACK_POWER).setY(0.8);
            target.setVelocity(knockback);
        }

        Messages.send(player, "<dark_aqua>SHOCKWAVE!</dark_aqua>");
        return true;
    }

    /**
     * Warden Gloves melee attack.
     * Deals Sharp III Netherite Axe damage + Knockback II.
     * 22 second cooldown.
     */
    public boolean useWardenMelee(Player player, Player victim) {
        UUID uuid = player.getUniqueId();

        if (isOnCooldown(uuid, "warden_melee")) {
            return false;
        }

        setCooldown(uuid, "warden_melee", WARDEN_MELEE_COOLDOWN);

        // Knockback II equivalent
        Vector knockback = victim.getLocation().toVector()
                .subtract(player.getLocation().toVector())
                .normalize()
                .multiply(1.5)
                .setY(0.5);
        victim.setVelocity(knockback);

        SoundUtils.play(victim, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.0f, 1.0f);
        return true;
    }

    // ==================== BLAZEBITE CROSSBOWS ====================

    /**
     * Toggle BlazeBite mode between Glacier and Volcano.
     * Can only toggle when not reloading and at full shots.
     */
    public void toggleBlazebiteMode(Player player) {
        UUID uuid = player.getUniqueId();
        boolean isGlacier = blazebiteMode.getOrDefault(uuid, true);

        if (isOnCooldown(uuid, "blazebite")) {
            Messages.send(player, "<red>Cannot switch modes while reloading!</red>");
            return;
        }

        blazebiteMode.put(uuid, !isGlacier);
        blazebiteShotsRemaining.put(uuid, BLAZEBITE_SHOTS_PER_MAGAZINE);

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

        int shots = blazebiteShotsRemaining.getOrDefault(uuid, BLAZEBITE_SHOTS_PER_MAGAZINE);
        if (shots <= 0) {
            if (isOnCooldown(uuid, "blazebite")) {
                Messages.send(player, "<red>BlazeBite reloading! (" + getRemainingCooldown(uuid, "blazebite") + "s)</red>");
                return false;
            }
            blazebiteShotsRemaining.put(uuid, BLAZEBITE_SHOTS_PER_MAGAZINE);
            shots = BLAZEBITE_SHOTS_PER_MAGAZINE;
        }

        blazebiteShotsRemaining.put(uuid, shots - 1);

        if (shots - 1 <= 0) {
            setCooldown(uuid, "blazebite", BLAZEBITE_RELOAD_COOLDOWN);
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
        boolean isGlacier = blazebiteMode.getOrDefault(uuid, true);
        World world = hitLoc.getWorld();
        if (world == null) return;

        if (isGlacier) {
            // Glacier mode - slowness and frostbite
            if (hitEntity instanceof Player victim) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, BLAZEBITE_FREEZE_DURATION, 0, false, true));
                victim.setFreezeTicks(BLAZEBITE_FREEZE_DURATION); // Frostbite effect

                world.spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
                SoundUtils.play(victim, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
            }
        } else {
            // Volcano mode - explosive fire arrow
            world.spawnParticle(Particle.FLAME, hitLoc, 50, 1, 1, 1, 0.2);
            world.spawnParticle(Particle.EXPLOSION, hitLoc, 1);
            world.playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

            GameSession session = GameManager.getInstance().getPlayerSession(shooter);
            Team shooterTeam = session != null ? session.getPlayerTeam(shooter) : null;

            for (Entity entity : world.getNearbyEntities(hitLoc, BLAZEBITE_VOLCANO_RADIUS, BLAZEBITE_VOLCANO_RADIUS, BLAZEBITE_VOLCANO_RADIUS)) {
                if (!(entity instanceof Player target)) continue;
                if (target.equals(shooter)) continue;

                if (session != null) {
                    Team targetTeam = session.getPlayerTeam(target);
                    if (targetTeam != null && shooterTeam != null &&
                        targetTeam.getTeamNumber() == shooterTeam.getTeamNumber()) continue;
                }

                // Direct hit = 2 hearts, splash = 1 heart
                double damage = entity.equals(hitEntity) ? BLAZEBITE_VOLCANO_DIRECT_DAMAGE : BLAZEBITE_VOLCANO_SPLASH_DAMAGE;
                target.damage(damage, shooter);
                target.setFireTicks(4 * 20); // Set on fire
            }
        }
    }

    /**
     * Get current BlazeBite mode for a player.
     * @return true if Glacier mode, false if Volcano mode
     */
    public boolean isGlacierMode(UUID playerId) {
        return blazebiteMode.getOrDefault(playerId, true);
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
     * Cleanup mythic ownership for a game session (called on game end).
     */
    public void cleanupSession(UUID sessionId) {
        teamMythics.remove(sessionId);
        sessionAvailableMythics.remove(sessionId);
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
        teamMythics.clear();
        sessionAvailableMythics.clear();

        activeTasks.values().forEach(tasks -> tasks.forEach(BukkitTask::cancel));
        activeTasks.clear();
    }
}

