package me.psikuvit.cashClash.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers who recently landed damage on whom in a session, so a kill can credit the players
 * who helped secure it.
 */
public class AssistTracker {

    private final Map<UUID, Map<UUID, Long>> lastHitTimes = new HashMap<>();

    /**
     * Records that {@code attacker} just landed damage on {@code victim}.
     */
    public void recordDamage(UUID attacker, UUID victim) {
        lastHitTimes.computeIfAbsent(victim, v -> new HashMap<>()).put(attacker, System.currentTimeMillis());
    }

    /**
     * Everyone other than the killer who damaged the victim within the last {@code windowMillis}.
     */
    public List<UUID> getRecentAttackers(UUID victim, UUID killer, long windowMillis) {
        Map<UUID, Long> hits = lastHitTimes.get(victim);
        if (hits == null) return List.of();

        long cutoff = System.currentTimeMillis() - windowMillis;
        List<UUID> attackers = new ArrayList<>();
        hits.forEach((attacker, time) -> {
            if (!attacker.equals(killer) && time >= cutoff) attackers.add(attacker);
        });
        return attackers;
    }

    /**
     * Whoever hit the victim last, if that was within the last {@code windowMillis} - else null.
     */
    public UUID getLastAttacker(UUID victim, long windowMillis) {
        Map<UUID, Long> hits = lastHitTimes.get(victim);
        if (hits == null) return null;

        long cutoff = System.currentTimeMillis() - windowMillis;
        UUID last = null;
        long lastTime = cutoff;
        for (Map.Entry<UUID, Long> hit : hits.entrySet()) {
            if (hit.getValue() >= lastTime) {
                last = hit.getKey();
                lastTime = hit.getValue();
            }
        }
        return last;
    }

    /**
     * Forgets every hit recorded on a player, once their death has been resolved.
     */
    public void clearVictim(UUID victim) {
        lastHitTimes.remove(victim);
    }
}
