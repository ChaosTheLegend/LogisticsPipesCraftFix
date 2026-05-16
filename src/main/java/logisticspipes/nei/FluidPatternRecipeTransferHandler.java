package logisticspipes.nei;

import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;
import logisticspipes.crafting.PatternGui;
import net.minecraft.client.gui.inventory.GuiContainer;

public class FluidPatternRecipeTransferHandler implements IOverlayHandler {

    public static final  FluidPatternRecipeTransferHandler INSTANCE = new FluidPatternRecipeTransferHandler();

    @Override
    public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer) {
        if (firstGui instanceof PatternGui) {
            LogisticPatternHandler.INSTANCE.overlayRecipe(firstGui, recipe, recipeIndex, maxTransfer);
        }
    }
}
