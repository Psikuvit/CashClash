package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Totem of Haunting: a one-time death save. On a would-be-lethal hit the totem is
 * consumed, the holder is healed to the configured revival health and gains a few
 * seconds of invincibility, while an expanding 3-arm smoke spiral debuffs nearby
 * enemies with Slowness I + Weakness I.
 */
public class TotemOfHauntingHandler extends CustomItemHandler {

    // Totem of Haunting - active death-save invincibility window
    private final Set<UUID> totemInvincible;

    public TotemOfHauntingHandler(CustomItemManager manager) {
        super(manager);
        this.totemInvincible = new HashSet<>();
    }

    public boolean isTotemInvincible(UUID uuid) {
        return totemInvincible.contains(uuid);
    }

    /**
     * Called from DamageListener when a would-be-lethal hit from another player is detected
     * on a player holding the Totem of Haunting (main or off hand).
     */
    public void triggerTotemOfHaunting(Player player, ItemStack totemItem) {
        UUID uuid = player.getUniqueId();

        consumeTotem(player, totemItem);
        CashClashPlayer.setHealth(player, cfg.getTotemRevivalHealth());

        totemInvincible.add(uuid);
        int invincibilitySeconds = cfg.getTotemInvincibilitySeconds();
        SchedulerUtils.runTaskLater(() -> totemInvincible.remove(uuid), invincibilitySeconds * 20L);

        Messages.send(player, "customitem.totem-haunting-triggered");
        SoundUtils.play(player, Sound.ITEM_TOTEM_USE, 1.0f, 0.6f);
        SoundUtils.play(player, Sound.ENTITY_WITHER_AMBIENT, 0.4f, 0.5f);

        spawnHauntingSpiral(player);
    }

    /**
     * Removes one Totem of Haunting from whichever hand held it (main or off hand).
     */
    private void consumeTotem(Player player, ItemStack totemItem) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main.equals(totemItem)) {
            consumeItem(player, main);
            return;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off.getAmount() > 1) {
            off.setAmount(off.getAmount() - 1);
        } else {
            player.getInventory().setItemInOffHand(null);
        }
    }

    /**
     * Expanding black smoke spiral - applies Slowness I + Weakness I to enemy players the first
     * moment the expanding smoke reaches them, rather than as an instant flat-radius check.
     */
    private void spawnHauntingSpiral(Player player) {
        Location origin = player.getLocation();
        double maxRadius = cfg.getTotemDebuffRadius();
        int debuffDurationTicks = cfg.getTotemDebuffDurationSeconds() * 20;
        int totalTicks = 20; // ~1s spiral formation
        int arms = 3;
        Set<UUID> alreadyDebuffed = new HashSet<>();

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        Team playerTeam = session != null ? session.getPlayerTeam(player) : null;

        int[] tick = {0};
        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            @Override
            public void run() {
                tick[0]++;
                double currentRadius = maxRadius * tick[0] / totalTicks;
                for (int arm = 0; arm < arms; arm++) {
                    ParticleUtils.smokeSpiralFrame(origin, currentRadius, arm, arms);
                }

                World world = origin.getWorld();
                if (world != null) {
                    for (Entity entity : world.getNearbyEntities(origin, maxRadius, maxRadius, maxRadius)) {
                        if (!(entity instanceof Player target) || target.equals(player)) continue;
                        if (alreadyDebuffed.contains(target.getUniqueId())) continue;
                        if (playerTeam != null && session.getPlayerTeam(target) == playerTeam) continue;
                        if (target.getLocation().distance(origin) > currentRadius + 1.0) continue;

                        alreadyDebuffed.add(target.getUniqueId());
                        CashClashPlayer.applyEffect(target, PotionEffectType.SLOWNESS, debuffDurationTicks, 0, false, true);
                        CashClashPlayer.applyEffect(target, PotionEffectType.WEAKNESS, debuffDurationTicks, 0, false, true);
                        Messages.send(target, "customitem.totem-haunting-witness", "player_name", player.getName());
                    }
                }

                if (tick[0] >= totalTicks) {
                    cancel();
                }
            }
        }, 0L, 1L);
    }

    @Override
    public void cleanup() {
        totemInvincible.clear();
    }
}
