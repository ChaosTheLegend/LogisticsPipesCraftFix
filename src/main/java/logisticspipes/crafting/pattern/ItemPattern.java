package logisticspipes.crafting.pattern;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.items.LogisticsItem;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.proxy.MainProxy;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.List;

public class ItemPattern extends LogisticsItem {

    public static final int INGREDIENT_SLOTS = DefaultPattern.INGREDIENT_SLOTS;
    public static final int RESULT_SLOTS = DefaultPattern.RESULT_SLOTS;
    public static final int ITEM_SLOT_COUNT = DefaultPattern.ITEM_SLOT_COUNT;
    public static final int MAX_INGREDIENT_SLOTS = ProcessingPattern.INGREDIENT_SLOTS;
    public static final int MAX_RESULT_SLOTS = ProcessingPattern.RESULT_SLOTS;
    public static final int MAX_ITEM_SLOT_COUNT = ProcessingPattern.ITEM_SLOT_COUNT;
    private static final String PATTERN_TYPE_TAG = "patternType";
    private static final String PROCESSING_TYPE = "processing";

    public ItemPattern() {
        setMaxStackSize(1);
    }

    public static AbstractPattern fromStack(ItemStack pattern) {
        if (pattern != null && pattern.getItem() instanceof ItemPattern) {
            return ((ItemPattern) pattern.getItem()).createPattern(pattern);
        }
        return new DefaultPattern(pattern);
    }

    public static boolean isProcessingPattern(ItemStack pattern) {
        return pattern != null && pattern.hasTagCompound()
            && PROCESSING_TYPE.equals(pattern.getTagCompound().getString(PATTERN_TYPE_TAG));
    }

    /**
     * Changes the stored pattern type and clears incompatible slot contents.
     * <p>
     * Switching changes the meaning of result-slot indexes, so preserving old slots would leave hidden or shifted
     * outputs behind.
     */
    public static void setProcessingPattern(ItemStack pattern, boolean processing) {
        if (pattern == null) {
            return;
        }
        if (isProcessingPattern(pattern) == processing) {
            return;
        }
        fromStack(pattern).clear();
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            pattern.setTagCompound(tag);
        }
        if (processing) {
            tag.setString(PATTERN_TYPE_TAG, PROCESSING_TYPE);
        } else {
            tag.removeTag(PATTERN_TYPE_TAG);
        }
    }

    public static void toggleProcessingPattern(ItemStack pattern) {
        setProcessingPattern(pattern, !isProcessingPattern(pattern));
    }

    public AbstractPattern createPattern(ItemStack pattern) {
        if (isProcessingPattern(pattern)) {
            return new ProcessingPattern(pattern);
        }
        return new DefaultPattern(pattern);
    }

    @Override
    public void registerIcons(IIconRegister register) {
        itemIcon = register.registerIcon("logisticspipes:itemModule/ModuleCrafter");
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (MainProxy.isServer(world)) {
            NewGuiHandler.getGui(PatternGuiProvider.class).setInventorySlot(player.inventory.currentItem).open(player);
        }
        return stack;
    }

    @Override
    public boolean addShiftInfo() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(isProcessingPattern(stack) ? "Processing pattern" : "Crafting pattern");
        fromStack(stack).addTooltipInformation(tooltip);
    }
}
