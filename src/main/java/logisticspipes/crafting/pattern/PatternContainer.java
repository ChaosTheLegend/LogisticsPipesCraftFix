package logisticspipes.crafting.pattern;

import logisticspipes.crafting.patternStack.PatternFluidStack;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.gui.DummyContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public class PatternContainer extends DummyContainer {

    public PatternContainer(IInventory playerInventory, IInventory dummyInventory) {
        super(playerInventory, dummyInventory);
    }

    public void setSelectedPatternSlot(int patternSlot) {
        if (_dummyInventory instanceof PipePatternInventory) {
            ((PipePatternInventory) _dummyInventory).setPatternSlot(patternSlot);
            detectAndSendChanges();
        }
    }

    @Override
    public void handleDummyClick(Slot slot, int slotId, ItemStack currentlyEquippedStack, int mouseButton, int isShift,
            EntityPlayer entityplayer) {
        if (isPatternSlot(slotId)) {
            FluidStack fluid = getFluidFromItem(currentlyEquippedStack);
            if (fluid != null && fluid.amount > 0) {
                // Handle fluid insertion
                switch (mouseButton) {
                    case 0: // Left click
                        slot.putStack(
                                SimpleServiceLocator.logisticsFluidManager.getFluidContainer(fluid).makeNormalStack());
                        syncSlot(slot, slotId, entityplayer);
                        return;
                    case 1: // Right click
                        // Maybe split or something, but for now, same as left
                        slot.putStack(
                                SimpleServiceLocator.logisticsFluidManager.getFluidContainer(fluid).makeNormalStack());
                        syncSlot(slot, slotId, entityplayer);
                        return;
                }
            }
        }
        super.handleDummyClick(slot, slotId, currentlyEquippedStack, mouseButton, isShift, entityplayer);
        syncSlot(slot, slotId, entityplayer);
    }

    private boolean isPatternSlot(int slotId) {
        return slotId >= 0 && slotId < ItemPattern.ITEM_SLOT_COUNT;
    }

    private FluidStack getFluidFromItem(ItemStack stack) {
        if (stack == null) return null;

        FluidStack fluid = null;

        fluid = PatternFluidStack.getFluidStack(stack, fluid);

        if (fluid != null) {
            FluidStack fluid0 = fluid.copy();
            fluid0.amount *= stack.stackSize;
            return fluid0;
        }
        return null;
    }

    private void syncSlot(Slot slot, int slotId, EntityPlayer entityplayer) {
        if (entityplayer instanceof EntityPlayerMP && MainProxy.isServer(entityplayer.worldObj)) {
            ((EntityPlayerMP) entityplayer).sendSlotContents(this, slotId, slot.getStack());
            detectAndSendChanges();
        }
    }

    public void reloadFromPattern(AbstractPattern pattern) {
        boolean change = false;

        for (int i = 0; i < _dummyInventory.getSizeInventory(); ++i) {
            var oldSlot = _dummyInventory.getStackInSlot(i);
            var newSlot = pattern.getStackInSlot(i);

            if (newSlot != oldSlot) {
                _dummyInventory.setInventorySlotContents(i, newSlot);
                change = true;
            }
        }

        if (change) detectAndSendChanges();

    }
}
