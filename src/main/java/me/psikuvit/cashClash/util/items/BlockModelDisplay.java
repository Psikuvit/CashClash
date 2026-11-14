package me.psikuvit.cashClash.util.items;

import me.psikuvit.cashClash.shop.items.CustomItem;
import org.bukkit.Location;
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
 * the work: collision, bouncing, and being what the item's handler looks for. On an empty spot
 * (the Blooming Rose's rose) it's just the model.
 */
public final class BlockModelDisplay {

    // Just enough to enclose the real block without its faces flickering through the model's
    private static final float COVER_SCALE = 1.002f;

    private BlockModelDisplay() {
        throw new AssertionError("Nope.");
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
