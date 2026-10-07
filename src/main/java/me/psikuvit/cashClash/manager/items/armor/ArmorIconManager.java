package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.manager.Shutdownable;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.SchedulerUtils;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.sound.Sounds;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Shows each armor ability as an effect icon in the top-right corner while it's ready to use
 * during combat, and takes it away while it recharges or the piece isn't worn. The effects are
 * harmless ones the resource pack draws as the armor pieces ({@code custom-armor.ready-icons}).
 * Trial Omen plays a sound to everyone nearby whenever it's added, so that sound is dropped on its
 * way to the clients - nothing else in a match gives Trial Omen.
 */
public class ArmorIconManager implements PacketListener, Shutdownable {

    private static final long UPDATE_TICKS = 5L;

    private final GameManager gameManager;
    private final CustomArmorManager armorManager;
    private final ItemsConfig itemsConfig;
    private final PacketListenerCommon packetListener;
    private BukkitTask task;

    public ArmorIconManager(GameManager gameManager, CustomArmorManager armorManager, ItemsConfig itemsConfig) {
        this.gameManager = gameManager;
        this.armorManager = armorManager;
        this.itemsConfig = itemsConfig;
        this.packetListener = PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.NORMAL);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.SOUND_EFFECT) return;

        WrapperPlayServerSoundEffect sound = new WrapperPlayServerSoundEffect(event);
        if (Sounds.EVENT_MOB_EFFECT_TRIAL_OMEN.getSoundId().equals(sound.getSound().getSoundId())) {
            event.setCancelled(true);
        }
    }

    public void start() {
        task = SchedulerUtils.runTaskTimer(this::tick, UPDATE_TICKS, UPDATE_TICKS);
    }

    private void tick() {
        for (GameSession session : gameManager.getActiveSessions()) {
            boolean combat = session.getState() == GameState.COMBAT;
            for (UUID uuid : session.getPlayers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;

                boolean active = combat && !CashClashPlayer.isPlayerDead(player);
                showIcon(player, "bunny-shoes", active && armorManager.getHandler(BunnyShoesHandler.class).isAbilityReady(player));
                showIcon(player, "guardians-vest", active && armorManager.getHandler(GuardianVestHandler.class).isAbilityReady(player));
                showIcon(player, "flamebringer", active && armorManager.getHandler(FlamebringerSetHandler.class).isAbilityReady(player));
                showIcon(player, "deathmauler", active && armorManager.getHandler(DeathmaulerSetHandler.class).isAbilityReady(player));
            }
        }
    }

    private void showIcon(Player player, String piece, boolean ready) {
        PotionEffectType icon = itemsConfig.getArmorReadyIcon(piece);
        if (icon == null) return;

        boolean shown = player.hasPotionEffect(icon);
        if (ready && !shown) {
            CashClashPlayer.applyEffect(player, icon, PotionEffect.INFINITE_DURATION, 0, true, false, true);
        } else if (!ready && shown) {
            CashClashPlayer.removeEffect(player, icon);
        }
    }

    /**
     * PacketEvents is its own plugin and outlives this one on a disable or reload, so the packet
     * listener has to be taken off it explicitly.
     */
    @Override
    public void shutdown() {
        PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
