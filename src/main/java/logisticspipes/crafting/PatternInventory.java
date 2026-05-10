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
        return Pattern.ITEM_SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return Pattern.getStackInSlot(pattern, slot);
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        ItemStack stack = getStackInSlot(slot);
        if (stack == null) {
            return null;
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
        Pattern.setStackInSlot(pattern, slot, stack);
        markDirty();
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
    public void markDirty() {
        player.inventory.markDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
    }

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
