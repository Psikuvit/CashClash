package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Bounce Pad: places a 5-second SLIME_BLOCK pad that launches same-team players
 * who step on it (wall-mounted pads launch outward along their face, floor pads
 * along the player's look direction). Enemy players are rejected by the pad.
 */
public class BouncePadHandler extends CustomItemHandler {

    // Bounce pad tracking - stores location -> owner team + the face it's attached to
    private final Map<Location, BouncePadInfo> bouncePadTeams;

    /**
     * @param attachedFace The block face the pad was placed against (UP for a floor pad,
     *                      a horizontal face for a wall-mounted pad); also the direction
     *                      players are launched when they touch it.
     */
    private record BouncePadInfo(int teamNumber, BlockFace attachedFace) {}

    public BouncePadHandler(CustomItemManager manager) {
        super(manager);
        this.bouncePadTeams = new HashMap<>();
    }

    public void placeBouncePad(Player player, ItemStack item, Block clickedBlock, BlockFace face) {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        if (session == null) return;

        Team team = session.getPlayerTeam(player);
        if (team == null) return;

        if (session.getState() == GameState.SHOPPING || session.isActionsRestricted()) {
            Messages.send(player, "customitem.cannot-place-during-shopping");
            return;
        }

        Block placeBlock = clickedBlock.getRelative(face);

        if (!placeBlock.getType().isAir()) {
            Messages.send(player, "customitem.cannot-place-bounce-pad");
            return;
        }

        // Use a full block so players can reliably trigger it
        placeBlock.setType(Material.SLIME_BLOCK);
        Location blockLoc = placeBlock.getLocation();
        bouncePadTeams.put(blockLoc, new BouncePadInfo(team.getTeamNumber(), face));

        consumeItem(player, item);
        Messages.send(player, "customitem.bounce-pad-placed");
        SoundUtils.play(player, Sound.BLOCK_SLIME_BLOCK_PLACE, 1.0f, 1.0f);

        SchedulerUtils.runTaskLater(() -> {
            bouncePadTeams.remove(blockLoc);
            if (placeBlock.getType() == Material.SLIME_BLOCK) {
                placeBlock.setType(Material.AIR);
            }
        }, 5 * 20L);
    }

    public void handleBouncePad(Player player, Block block) {
        Location blockLoc = block.getLocation();
        BouncePadInfo pad = bouncePadTeams.get(blockLoc);
        if (pad == null) return;

        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        if (session == null) return;

        Team playerTeam = session.getPlayerTeam(player);
        if (playerTeam == null) return;

        if (playerTeam.getTeamNumber() != pad.teamNumber()) {
            Messages.send(player, "customitem.bounce-pad-enemy-team");
            return;
        }

        boolean wallMounted = pad.attachedFace() != BlockFace.UP && pad.attachedFace() != BlockFace.DOWN;

        Vector direction;
        if (wallMounted) {
            // Wall-mounted pad: launch outward along the wall's face instead of the player's look direction
            direction = pad.attachedFace().getDirection();
        } else {
            direction = player.getLocation().getDirection();
        }
        direction.setY(0).normalize();
        // Side bounces (wall-mounted) launch much flatter than a floor pad - 80% less vertical height.
        double upwardVelocity = wallMounted ? cfg.getBouncePadUpwardVelocity() * 0.2 : cfg.getBouncePadUpwardVelocity();
        Vector velocity = direction.multiply(cfg.getBouncePadForwardVelocity()).setY(upwardVelocity);
        player.setVelocity(velocity);

        SoundUtils.play(player, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);
    }

    public boolean isBouncePad(Block block) {
        if (block.getType() != Material.SLIME_BLOCK) return false;
        return bouncePadTeams.containsKey(block.getLocation());
    }

    /**
     * Whether this block is a bounce pad that was placed wall-mounted (as opposed to a
     * floor pad). Used to gate the side-touch bounce trigger to wall pads only - a floor
     * pad brushed from the side shouldn't launch the player.
     */
    public boolean isWallMountedBouncePad(Block block) {
        if (block.getType() != Material.SLIME_BLOCK) return false;
        BouncePadInfo pad = bouncePadTeams.get(block.getLocation());
        if (pad == null) return false;
        return pad.attachedFace() != BlockFace.UP && pad.attachedFace() != BlockFace.DOWN;
    }

    @Override
    public void cleanup() {
        bouncePadTeams.keySet().forEach(loc -> {
            Block block = loc.getBlock();
            if (block.getType() == Material.SLIME_BLOCK) {
                block.setType(Material.AIR);
            }
        });
        bouncePadTeams.clear();
    }
}
