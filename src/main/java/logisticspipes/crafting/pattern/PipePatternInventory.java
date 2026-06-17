package logisticspipes.crafting.pattern;

import logisticspipes.LogisticsPipes;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

public class PipePatternInventory implements IInventory {

    private final PipeItemsPatternCraftingLogistics pipe;
    private int patternSlot;

    public PipePatternInventory(PipeItemsPatternCraftingLogistics pipe, int patternSlot) {
        this.pipe = pipe;
        setPatternSlot(patternSlot);
    }

    public void setPatternSlot(int patternSlot) {
        this.patternSlot = Math.max(0, Math.min(8, patternSlot));
    }

    public ItemStack getPatternStack() {
        return pipe.getPatternModule().getPatternItemStack(patternSlot);
    }

    @Override
    public int getSizeInventory() {
        return ItemPattern.ITEM_SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemPattern.fromStack(getPatternStack()).getStackInSlot(slot);
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        ItemStack stack = getStackInSlot(slot);
        if (stack == null) {
            return null;
        }
        ItemPattern.fromStack(getPatternStack()).setStackInSlot(slot, null);
        markDirty();
        return stack;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return getStackInSlot(slot);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        ItemPattern.fromStack(getPatternStack()).setStackInSlot(slot, stack);
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "Pipe Pattern";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 127;
    }

    @Override
    public void markDirty() {
        pipe.getPatternModule().markPatternInventoryDirty();
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        ItemStack pattern = getPatternStack();
        return pattern != null && pattern.getItem() == LogisticsPipes.LogisticsPattern;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return stack != null;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}
}
