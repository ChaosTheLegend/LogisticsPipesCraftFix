package logisticspipes.crafting;

import lombok.Getter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import logisticspipes.LogisticsPipes;

public class PatternInventory implements IInventory {

    private final EntityPlayer player;
    @Getter
    private final ItemStack pattern;
    @Getter
    private final int inventorySlot;

    public PatternInventory(EntityPlayer player, int inventorySlot) {
        this.player = player;
        this.inventorySlot = inventorySlot;
        this.pattern = getPatternStack();
    }

    @Override
    public int getSizeInventory() {
        return Pattern.SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot >= Pattern.FLUID_INPUT_START && slot < Pattern.SLOT_COUNT) {
            PatternFluidStack fluid = Pattern.getFluidInSlot(pattern, slot);
            return fluid == null ? null : fluid.makeGuiStack();
        }
        return Pattern.getStackInSlot(pattern, slot);
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        ItemStack stack = getStackInSlot(slot);
        if (stack == null) {
            return null;
        }
        if (slot >= Pattern.FLUID_INPUT_START && slot < Pattern.SLOT_COUNT) {
            Pattern.setFluidInSlot(pattern, slot, null);
            return stack;
        }
        Pattern.setStackInSlot(pattern, slot, null);
        return stack;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return getStackInSlot(slot);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot >= Pattern.FLUID_INPUT_START && slot < Pattern.SLOT_COUNT) {
            PatternFluidStack fluid = PatternFluidStack.fromItemStack(stack);
            if (fluid != null && fluid.getAmount() <= 0) {
                fluid = new PatternFluidStack(fluid.getFluid(), 1000);
            }
            Pattern.setFluidInSlot(pattern, slot, fluid);
            return;
        }
        Pattern.setStackInSlot(pattern, slot, stack);
    }

    @Override
    public String getInventoryName() {
        return "Pattern";
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
    public void markDirty() {}

    @Override
    public boolean isUseableByPlayer(EntityPlayer entityPlayer) {
        ItemStack stack = getPatternStack();
        return stack != null && stack.getItem() == LogisticsPipes.LogisticsPattern;
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return stack != null;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    private ItemStack getPatternStack() {
        if (inventorySlot < 0 || inventorySlot >= player.inventory.mainInventory.length) {
            return null;
        }
        return player.inventory.mainInventory[inventorySlot];
    }

    public void clear() {
        Pattern.clear(getPatternStack());
    }
}
