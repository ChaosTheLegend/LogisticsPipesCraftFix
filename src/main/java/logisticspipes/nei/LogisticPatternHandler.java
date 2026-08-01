package logisticspipes.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.client.gui.inventory.GuiContainer;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;
import logisticspipes.crafting.pattern.PatternGui;
import logisticspipes.crafting.patternStack.IPatternStack;

public class LogisticPatternHandler implements IOverlayHandler {

    private LogisticPatternHandler() {}

    public static final LogisticPatternHandler INSTANCE = new LogisticPatternHandler();

    @Override
    public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer) {
        if (!(firstGui instanceof PatternGui gui)) {}

        // we can just steal ae2 fluid implementation here for now, so we dont need to rewrite all the handlers.
        // List<OrderStack<?>> in = FluidRecipe.getPackageInputs(recipe, recipeIndex, false);
        // List<OrderStack<?>> out = FluidRecipe.getPackageOutputs(recipe, recipeIndex, true);
        //
        // List<IPatternStack> inputs = new ArrayList<>();
        // List<Integer> indices = new ArrayList<>();
        // List<IPatternStack> outputs = new ArrayList<>();
        //
        // for (OrderStack<?> orderStack : in) {
        // var stack = orderStack.getStack();
        // if (stack instanceof ItemStack itemStack) {
        // PatternItemStack patternSolidStack = PatternItemStack.fromItemStack(itemStack);
        // if (patternSolidStack == null) continue;
        //
        // inputs.add(patternSolidStack);
        // indices.add(orderStack.getIndex());
        // }
        // if (stack instanceof FluidStack fluidStack) {
        // PatternFluidStack patternFluidStack = PatternFluidStack.fromFluidStack(fluidStack);
        // if (patternFluidStack == null) continue;
        //
        // inputs.add(patternFluidStack);
        // indices.add(orderStack.getIndex());
        // }
        // }
        //
        // for (var outputStack : out) {
        // if (outputStack == null) continue;
        // var stack = outputStack.getStack();
        // if (stack instanceof ItemStack itemStack) {
        // PatternItemStack patternSolidStack = PatternItemStack.fromItemStack(itemStack);
        // if (patternSolidStack == null) continue;
        // outputs.add(patternSolidStack);
        // }
        //
        // if (stack instanceof FluidStack fluidStack) {
        // PatternFluidStack patternFluidStack = PatternFluidStack.fromFluidStack(fluidStack);
        // if (patternFluidStack == null) continue;
        // outputs.add(patternFluidStack);
        // }
        // }

        // MainProxy.sendPacketToServer(
        // PacketHandler.getPacket(NEISetPatternCraftingRecipe.class)
        // .setPatternInventorySlot(gui.getInventorySlot()).setInputs(inputs).setIndices(indices)
        // .setOutputs(outputs));

    }

    /**
     * Collects the inputs of a given recipe, transformed into IPatternStacks.
     *
     * @param recipe      the recipe
     * @param recipeIndex the recipe index
     * @return the inputs of the given recipe
     */
    private List<IPatternStack> getInputs(IRecipeHandler recipe, int recipeIndex) {
        return recipe.getIngredientStacks(recipeIndex).stream()
                .map(stack -> IPatternStack.fromItemStack(stack.item.copy())).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Collects the aggregated outputs of a given recipe, transformed into IPatternStacks.
     *
     * @param recipe      the recipe
     * @param recipeIndex the recipe index
     * @return the aggregated outputs of the given recipe
     */
    private List<IPatternStack> getAggregatedOutputs(IRecipeHandler recipe, int recipeIndex) {
        List<IPatternStack> outputs = new ArrayList<>();

        var resultStack = recipe.getResultStack(recipeIndex);
        if (resultStack != null) addAggregated(outputs, IPatternStack.fromItemStack(resultStack.item.copy()));

        List<PositionedStack> otherStacks = recipe.getOtherStacks(recipeIndex);
        if (otherStacks == null) return outputs;

        for (PositionedStack stack : otherStacks) {
            if (stack == null || stack.item == null) continue;
            var patternStack = IPatternStack.fromItemStack(stack.item.copy());
            if (patternStack == null) continue;
            addAggregated(outputs, patternStack);
        }

        return outputs;
    }

    /**
     * Adds a patternStack to a list of patternStacks, aggregating if possible.
     *
     * @param stacks the list of stacks
     * @param stack  the stack to add
     */
    public static void addAggregated(List<IPatternStack> stacks, IPatternStack stack) {
        if (stack == null || stack.getAmount() <= 0) return;

        for (IPatternStack existing : stacks) {
            if (existing.canMerge(stack)) {
                existing.addAmount(stack.getAmount());
                return;
            }
        }
        stacks.add(stack.copy());
    }
}
