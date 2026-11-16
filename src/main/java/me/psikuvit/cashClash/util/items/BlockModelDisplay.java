package me.psikuvit.cashClash.util.items;

import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Puts the resource pack's block model on a custom item's placed block. A pack can't restyle one
 * block without restyling every block of that type, so the model is an item display of the item
 * sitting over the real block, a hair bigger so it hides it. The real block underneath still does
 * the work: collision, bouncing, and being what the item's handler looks for.
 */
public final class BlockModelDisplay {

    // Just enough to enclose the real block without its faces flickering through the model's
    private static final float COVER_SCALE = 1.002f;
    // A client draws a new display entity only from its first tick, so the real block waits this long
    private static final long REVEAL_DELAY_TICKS = 2L;

    private BlockModelDisplay() {
        throw new AssertionError("Nope.");
    }

    /**
     * Places {@code material} at {@code block} under {@code item}'s pack model without the real
     * block ever showing on its own. The spot is an invisible barrier until the model is drawn,
     * then the real block goes in underneath it; a model removed before then takes the barrier
     * with it.
     */
    public static ItemDisplay place(Block block, Material material, CustomItem item) {
        block.setType(Material.BARRIER);
        ItemDisplay display = show(block, item);
        SchedulerUtils.runTaskLater(() -> {
            if (block.getType() != Material.BARRIER) return;
            block.setType(display.isValid() ? material : Material.AIR);
        }, REVEAL_DELAY_TICKS);
        return display;
    }

    /**
     * Shows {@code item}'s pack model over {@code block}. The display isn't saved with its chunk,
     * so a restart can't leave one behind; the caller removes it along with the block.
     */
    public static ItemDisplay show(Block block, CustomItem item) {
        ItemStack model = new ItemStack(item.getMaterial());
        CustomModelDataMapper.applyCustomModel(model, item);

        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        Block above = block.getRelative(BlockFace.UP);
        Display.Brightness light = new Display.Brightness(above.getLightFromBlocks(), above.getLightFromSky());

        return block.getWorld().spawn(center, ItemDisplay.class, display -> {
            display.setItemStack(model);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(COVER_SCALE, COVER_SCALE, COVER_SCALE), new AxisAngle4f()));
            display.setBrightness(light);
            display.setPersistent(false);
        });
    }

    public static void remove(ItemDisplay display) {
        if (display != null && display.isValid()) display.remove();
    }
}
