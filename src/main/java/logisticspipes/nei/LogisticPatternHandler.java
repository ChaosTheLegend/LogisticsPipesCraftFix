package logisticspipes.nei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;
import cpw.mods.fml.client.FMLClientHandler;
import logisticspipes.crafting.IPatternStack;
import logisticspipes.crafting.PatternFluidStack;
import logisticspipes.crafting.PatternGui;
import logisticspipes.crafting.PatternNEIImportHandler;
import logisticspipes.gui.popup.GuiRecipeImport;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.NEISetCraftingRecipe;
import logisticspipes.proxy.MainProxy;

public class LogisticPatternHandler implements IOverlayHandler {

    private LogisticPatternHandler() {}

    public static final LogisticPatternHandler INSTANCE = new LogisticPatternHandler();

    @Override
    public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer) {
        if (!(firstGui instanceof PatternGui)) {
            return;
        }

        PatternGui gui = (PatternGui) firstGui;
        ItemStack[] inputs = new ItemStack[9];
        ItemStack[][] candidates = new ItemStack[9][];
        boolean hasCandidates = false;

        for (PositionedStack stack : recipe.getIngredientStacks(recipeIndex)) {
            int x = (stack.relx - 25) / 18;
            int y = (stack.rely - 6) / 18;
            int slot = x + y * 3;
            if (x < 0 || x > 2 || y < 0 || y > 2 || slot < 0 || slot > 8) {
                if (isFluid(stack) && addToFirstFreeSlot(inputs, firstStack(stack))) {
                    continue;
                }
                FMLClientHandler.instance().getClient().thePlayer
                        .sendChatMessage("Internal Error. This button is broken.");
                return;
            }

            List<ItemStack> expandedCandidates = expandCandidates(stack);
            candidates[slot] = expandedCandidates.toArray(new ItemStack[0]);
            if (candidates[slot].length > 1) {
                hasCandidates = true;
            } else if (candidates[slot].length == 1) {
                inputs[slot] = candidates[slot][0];
            }
        }

        ItemStack[] outputs = getAggregatedOutputs(recipe, recipeIndex);
        if (hasCandidates) {
            gui.setSubGui(new GuiRecipeImport(gui.getInventorySlot(), candidates, outputs));
            return;
        }

        MainProxy.sendPacketToServer(PacketHandler.getPacket(NEISetCraftingRecipe.class)
                .setPatternInventorySlot(gui.getInventorySlot())
                .setContent(inputs)
                .setResult(outputs.length > 0 ? outputs[0] : null)
                .setOutputs(outputs));
    }

    private List<ItemStack> expandCandidates(PositionedStack stack) {
        if (stack == null || stack.items == null || stack.items.length == 0) {
            return new ArrayList<>();
        }
        List<ItemStack> list = new ArrayList<>(Arrays.asList(stack.items));
        Iterator<ItemStack> iter = list.iterator();
        while (iter.hasNext()) {
            ItemStack wildCardCheckStack = iter.next();
            if (wildCardCheckStack == null) {
                iter.remove();
                continue;
            }
            if (wildCardCheckStack.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
                iter.remove();
                wildCardCheckStack.getItem().getSubItems(
                        wildCardCheckStack.getItem(),
                        wildCardCheckStack.getItem().getCreativeTab(),
                        list);
                iter = list.iterator();
            }
        }
        return list;
    }

    private ItemStack[] getAggregatedOutputs(IRecipeHandler recipe, int recipeIndex) {
        List<IPatternStack> outputs = new ArrayList<>();
        addPositionedStack(outputs, recipe.getResultStack(recipeIndex));
        List<PositionedStack> otherStacks = recipe.getOtherStacks(recipeIndex);
        if (otherStacks != null) {
            for (PositionedStack stack : otherStacks) {
                addPositionedStack(outputs, stack);
            }
        }
        return PatternNEIImportHandler.toPatternItemStacks(outputs);
    }

    private void addPositionedStack(List<IPatternStack> outputs, PositionedStack stack) {
        PatternNEIImportHandler.addAggregated(outputs, IPatternStack.fromItemStack(firstStack(stack)));
    }

    private boolean isFluid(PositionedStack stack) {
        return PatternFluidStack.fromItemStack(firstStack(stack)) != null;
    }

    private ItemStack firstStack(PositionedStack stack) {
        if (stack == null) {
            return null;
        }
        if (stack.item != null) {
            return stack.item.copy();
        }
        if (stack.items != null && stack.items.length > 0 && stack.items[0] != null) {
            return stack.items[0].copy();
        }
        return null;
    }

    private boolean addToFirstFreeSlot(ItemStack[] stacks, ItemStack stack) {
        if (stack == null) {
            return false;
        }
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] == null) {
                stacks[i] = stack.copy();
                return true;
            }
        }
        return false;
    }
}
