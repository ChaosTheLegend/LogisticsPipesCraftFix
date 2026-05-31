package logisticspipes.crafting;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.items.LogisticsItem;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.proxy.MainProxy;

public class Pattern extends LogisticsItem {

    public static final int INGREDIENT_SLOTS = DefaultPattern.INGREDIENT_SLOTS;
    public static final int RESULT_SLOTS = DefaultPattern.RESULT_SLOTS;
    public static final int ITEM_SLOT_COUNT = DefaultPattern.ITEM_SLOT_COUNT;
    public static final int SLOT_COUNT = ITEM_SLOT_COUNT;

    public Pattern() {
        setMaxStackSize(1);
    }

    public static AbstractPattern fromStack(ItemStack pattern) {
        if (pattern != null && pattern.getItem() instanceof Pattern) {
            return ((Pattern) pattern.getItem()).createPattern(pattern);
        }
        return new DefaultPattern(pattern);
    }

    public AbstractPattern createPattern(ItemStack pattern) {
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
        fromStack(stack).addTooltipInformation(tooltip);
    }
}
