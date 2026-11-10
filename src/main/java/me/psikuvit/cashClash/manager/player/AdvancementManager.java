package me.psikuvit.cashClash.manager.player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import me.psikuvit.cashClash.manager.Shutdownable;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Iterator;

/**
 * Keeps vanilla advancements out of the game. Every player is given all of them when they join,
 * so none can trigger mid-match, and never sees any of it: clients don't receive the advancement
 * packets (no toasts, an empty advancements screen) and the chat announcements are muted. Checking
 * on every join rather than only the first also covers players from before this existed and
 * advancements added by a Minecraft update; a player who has them all gets nothing new.
 */
public class AdvancementManager implements PacketListener, Listener, Shutdownable {

    private final PacketListenerCommon packetListener;

    public AdvancementManager() {
        this.packetListener = PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.NORMAL);
    }

    /**
     * PacketEvents is its own plugin and outlives this one on a disable or reload, so the packet
     * listener has to be taken off it explicitly.
     */
    @Override
    public void shutdown() {
        PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.UPDATE_ADVANCEMENTS) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        awardAll(event.getPlayer());
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        event.message(null);
    }

    /**
     * Awards everything with a display, the advancements players actually see. Display-less ones
     * only unlock recipes, and awarding those would pop a recipe toast. Some advancements pay out
     * experience, which is put back so nobody's level jumps.
     */
    private void awardAll(Player player) {
        int level = player.getLevel();
        float progressToNextLevel = player.getExp();
        int totalExperience = player.getTotalExperience();

        Iterator<Advancement> advancements = Bukkit.advancementIterator();
        while (advancements.hasNext()) {
            Advancement advancement = advancements.next();
            if (advancement.getDisplay() == null) continue;

            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            progress.getRemainingCriteria().forEach(progress::awardCriteria);
        }

        player.setTotalExperience(totalExperience);
        player.setLevel(level);
        player.setExp(progressToNextLevel);
    }
}
