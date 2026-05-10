package logisticspipes.crafting;

import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

public class PatternFluidStack {

    private final FluidIdentifier fluid;
    private int amount;

    public PatternFluidStack(FluidIdentifier fluid, int amount) {
        this.fluid = fluid;
        this.amount = Math.max(0, amount);
    }

    public static PatternFluidStack fromItemStack(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return null;
        }
        FluidStack fluidStack = FluidContainerRegistry.getFluidForFilledItem(stack);
        if (fluidStack == null && stack.getItem() instanceof IFluidContainerItem) {
            fluidStack = ((IFluidContainerItem) stack.getItem()).drain(stack, Integer.MAX_VALUE, false);
        }
        if (fluidStack == null) {
            fluidStack = SimpleServiceLocator.logisticsFluidManager
                    .getFluidFromContainer(ItemIdentifierStack.getFromStack(stack));
        }
        if (fluidStack == null) {
            return null;
        }
        int amount = fluidStack.amount > 0 ? fluidStack.amount : (stack.stackSize > 1 ? stack.stackSize : 1000);
        return new PatternFluidStack(FluidIdentifier.get(fluidStack), amount);
    }

    public static PatternFluidStack readFromNBT(NBTTagCompound tag) {
        FluidStack fluidStack = FluidStack.loadFluidStackFromNBT(tag);
        if (fluidStack == null) {
            return null;
        }
        return new PatternFluidStack(FluidIdentifier.get(fluidStack), fluidStack.amount);
    }

    public FluidIdentifier getFluid() {
        return fluid;
    }

    public int getAmount() {
        return amount;
    }

    public void addAmount(int amount) {
        this.amount += amount;
    }

    public FluidStack makeFluidStack() {
        return fluid.makeFluidStack(amount);
    }

    public ItemIdentifierStack makeDisplayStack() {
        return fluid.getItemIdentifier().makeStack(amount);
    }

    public ItemStack makeGuiStack() {
        return fluid.getItemIdentifier().unsafeMakeNormalStack(1);
    }

    public ItemStack makePatternStack() {
        return SimpleServiceLocator.logisticsFluidManager.getFluidContainer(makeFluidStack()).makeNormalStack();
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        makeFluidStack().writeToNBT(tag);
        return tag;
    }

    public PatternFluidStack copy() {
        return new PatternFluidStack(fluid, amount);
    }

    @Override
    public String toString() {
        return amount + "mB " + fluid;
    }
}
