package logisticspipes.nei;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;
import logisticspipes.crafting.PatternGui;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.item.ItemIdentifierStack;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import java.util.HashMap;

public class LogisticPatternHandler implements IOverlayHandler {

    private LogisticPatternHandler() {
    }

    public static final LogisticPatternHandler INSTANCE = new LogisticPatternHandler();

    @Override
    public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer) {
        if (!(firstGui instanceof PatternGui)) return;
        PatternGui gui = (PatternGui) firstGui;

        //resolve inputs
        HashMap<Item, Integer> inputsSolidMap = new HashMap<>();
        HashMap<Fluid, Integer> inputsFluidMap = new HashMap<>();

        for (PositionedStack input : recipe.getIngredientStacks(recipeIndex)) {
            ItemStack itemStack = input.item;

            FluidStack fluidStack = fromItemStack(input.item);
            if (fluidStack != null) {
                inputsFluidMap.merge(fluidStack.getFluid(), fluidStack.amount, Integer::sum);
            } else {
                inputsSolidMap.merge(itemStack.getItem(), itemStack.stackSize, Integer::sum);
            }
        }

        //resolve outputs
        HashMap<Item, Integer> outputsSolidMap = new HashMap<>();
        HashMap<Fluid, Integer> outputsFluidMap = new HashMap<>();

        PositionedStack outputStack = recipe.getResultStack(recipeIndex);
        if (outputStack != null) {
            ItemStack itemStack = outputStack.item;
            FluidStack fluidStack = fromItemStack(outputStack.item);
            if (fluidStack != null) {
                outputsFluidMap.merge(fluidStack.getFluid(), fluidStack.amount, Integer::sum);
            } else {
                outputsSolidMap.merge(itemStack.getItem(), itemStack.stackSize, Integer::sum);
            }
        }

        for (PositionedStack output : recipe.getOtherStacks(recipeIndex)) {
            ItemStack itemStack = output.item;

            FluidStack fluidStack = fromItemStack(output.item);
            if (fluidStack != null) {
                outputsFluidMap.merge(fluidStack.getFluid(), fluidStack.amount, Integer::sum);
            } else {
                outputsSolidMap.merge(itemStack.getItem(), itemStack.stackSize, Integer::sum);
            }
        }



    }

    private FluidStack fromItemStack(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return null;
        }
        FluidStack fluidStack = FluidContainerRegistry.getFluidForFilledItem(stack);
        if (fluidStack == null && stack.getItem() instanceof IFluidContainerItem) {
            fluidStack = ((IFluidContainerItem) stack.getItem()).drain(stack, Integer.MAX_VALUE, false);
        }
        if (fluidStack == null) {
            fluidStack = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(ItemIdentifierStack.getFromStack(stack));
        }
        if (fluidStack == null) {
            return null;
        }
        int amount = fluidStack.amount > 0 ? fluidStack.amount : (stack.stackSize > 1 ? stack.stackSize : 1000);
        return new FluidStack(fluidStack, amount);
    }
}
