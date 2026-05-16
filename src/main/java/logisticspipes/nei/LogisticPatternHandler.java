package logisticspipes.nei;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;
import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.IPatternStack;
import logisticspipes.crafting.PatternGui;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.NEISetPatternCraftingRecipe;
import logisticspipes.proxy.MainProxy;
import net.minecraft.client.gui.inventory.GuiContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class LogisticPatternHandler implements IOverlayHandler {

    private LogisticPatternHandler() {
    }

    public static final LogisticPatternHandler INSTANCE = new LogisticPatternHandler();

    @Override
    public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer) {
        if (!(firstGui instanceof PatternGui gui)) return;

        try {
            List<IPatternStack> inputs = getInputs(recipe, recipeIndex);

            List<IPatternStack> outputs = getAggregatedOutputs(recipe, recipeIndex);

            MainProxy.sendPacketToServer(PacketHandler.getPacket(NEISetPatternCraftingRecipe.class)
                .setPatternInventorySlot(gui.getInventorySlot())
                .setInputs(inputs)
                .setOutputs(outputs));
        } catch (Exception e) {
            LogisticsPipes.log.error(e.getMessage(), e);
        }

    }

    /**
     * Collects the inputs of a given recipe, transformed into IPatternStacks.
     * @param recipe the recipe
     * @param recipeIndex the recipe index
     * @return the inputs of the given recipe
     */
    private List<IPatternStack> getInputs(IRecipeHandler recipe, int recipeIndex) {
        return recipe.getIngredientStacks(recipeIndex).stream().map(stack -> IPatternStack.fromItemStack(stack.item.copy())).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * Collects the aggregated outputs of a given recipe, transformed into IPatternStacks.
     * @param recipe the recipe
     * @param recipeIndex the recipe index
     * @return the aggregated outputs of the given recipe
     */
    private List<IPatternStack> getAggregatedOutputs(IRecipeHandler recipe, int recipeIndex) {
        List<IPatternStack> outputs = new ArrayList<>();

        var resultStack = recipe.getResultStack(recipeIndex);
        if (resultStack != null)
            addAggregated(outputs, IPatternStack.fromItemStack(resultStack.item.copy()));

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
     * @param stacks the list of stacks
     * @param stack the stack to add
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
