package logisticspipes.crafting;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.item.ItemIdentifierStack;

public class PatternContainer extends DummyContainer {

    public PatternContainer(IInventory playerInventory, IInventory dummyInventory) {
        super(playerInventory, dummyInventory);
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
                        slot.putStack(SimpleServiceLocator.logisticsFluidManager.getFluidContainer(fluid).makeNormalStack());
                        return;
                    case 1: // Right click
                        // Maybe split or something, but for now, same as left
                        slot.putStack(SimpleServiceLocator.logisticsFluidManager.getFluidContainer(fluid).makeNormalStack());
                        return;
                }
            }
        }
        super.handleDummyClick(slot, slotId, currentlyEquippedStack, mouseButton, isShift, entityplayer);
    }

    private boolean isPatternSlot(int slotId) {
        return slotId >= 0 && slotId < 9;
    }

    private FluidStack getFluidFromItem(ItemStack stack) {
        if (stack == null) return null;

        FluidStack fluid = null;

        if (stack.getItem() instanceof IFluidContainerItem) {
            fluid = ((IFluidContainerItem) stack.getItem()).getFluid(stack);
        } else if (FluidContainerRegistry.isContainer(stack)) {
            fluid = FluidContainerRegistry.getFluidForFilledItem(stack);
        }

        if (fluid == null) {
            // Check if it's already a fluid container
            fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(ItemIdentifierStack.getFromStack(stack));
        }

        if (fluid != null) {
            FluidStack fluid0 = fluid.copy();
            fluid0.amount *= stack.stackSize;
            return fluid0;
        }
        return null;
    }
}
