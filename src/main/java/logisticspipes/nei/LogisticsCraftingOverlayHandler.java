package logisticspipes.nei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.oredict.OreDictionary;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;
import cpw.mods.fml.client.FMLClientHandler;
import logisticspipes.crafting.PatternGui;
import logisticspipes.crafting.PatternFluidStack;
import logisticspipes.gui.GuiLogisticsCraftingTable;
import logisticspipes.gui.orderer.GuiRequestTable;
import logisticspipes.gui.popup.GuiRecipeImport;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.NEISetCraftingRecipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;

public class LogisticsCraftingOverlayHandler implements IOverlayHandler {

    private LogisticsCraftingOverlayHandler() {
    }

    public static final LogisticsCraftingOverlayHandler INSTANCE = new LogisticsCraftingOverlayHandler();

    @Override
    public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean shift) {

        TileEntity tile = null;
        LogisticsBaseGuiScreen gui;
        int patternInventorySlot = -1;
        if (firstGui instanceof GuiLogisticsCraftingTable) {
            tile = ((GuiLogisticsCraftingTable) firstGui)._crafter;
            gui = (GuiLogisticsCraftingTable) firstGui;
        } else if (firstGui instanceof GuiRequestTable) {
            tile = ((GuiRequestTable) firstGui)._table.container;
            gui = (GuiRequestTable) firstGui;
        } else if (firstGui instanceof PatternGui) {
            patternInventorySlot = ((PatternGui) firstGui).getInventorySlot();
            gui = (PatternGui) firstGui;
        } else {
            return;
        }

        ItemStack[] stack = new ItemStack[9];
        ItemStack[][] stacks = new ItemStack[9][];
        ItemStack result = getRecipeResult(recipe, recipeIndex);
        boolean hasCanidates = false;
        NEISetCraftingRecipe packet = PacketHandler.getPacket(NEISetCraftingRecipe.class);
        for (PositionedStack ps : recipe.getIngredientStacks(recipeIndex)) {
            int x = (ps.relx - 25) / 18;
            int y = (ps.rely - 6) / 18;
            int slot = x + y * 3;
            if (x < 0 || x > 2 || y < 0 || y > 2 || slot < 0 || slot > 8) {
                if (patternInventorySlot >= 0 && ps.items != null && ps.items.length > 0
                    && PatternFluidStack.fromItemStack(ps.items[0]) != null
                    && addToFirstFreeSlot(stack, ps.items[0])) {
                    continue;
                }
                FMLClientHandler.instance().getClient().thePlayer
                    .sendChatMessage("Internal Error. This button is broken.");
                return;
            }
            if (slot < 9) {
                stack[slot] = ps.items[0];
                List<ItemStack> list = new ArrayList<>(Arrays.asList(ps.items));
                Iterator<ItemStack> iter = list.iterator();
                while (iter.hasNext()) {
                    ItemStack wildCardCheckStack = iter.next();
                    if (wildCardCheckStack.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
                        iter.remove();
                        wildCardCheckStack.getItem().getSubItems(
                            wildCardCheckStack.getItem(),
                            wildCardCheckStack.getItem().getCreativeTab(),
                            list);
                        iter = list.iterator();
                    }
                }
                stacks[slot] = list.toArray(new ItemStack[0]);
                if (stacks[slot].length > 1) {
                    hasCanidates = true;
                } else if (stacks[slot].length == 1) {
                    stack[slot] = stacks[slot][0];
                }
            }
        }
        if (hasCanidates) {
            if (patternInventorySlot >= 0) {
                gui.setSubGui(new GuiRecipeImport(patternInventorySlot, stacks, result));
            } else {
                gui.setSubGui(new GuiRecipeImport(tile, stacks));
            }
        } else if (patternInventorySlot >= 0) {
            MainProxy.sendPacketToServer(PacketHandler.getPacket(NEISetCraftingRecipe.class)
                .setPatternInventorySlot(patternInventorySlot)
                .setContent(stack)
                .setResult(result));
        } else {
            MainProxy.sendPacketToServer(
                packet.setContent(stack).setPosX(tile.xCoord).setPosY(tile.yCoord).setPosZ(tile.zCoord));
        }
    }

    private ItemStack getRecipeResult(IRecipeHandler recipe, int recipeIndex) {
        PositionedStack result = recipe.getResultStack(recipeIndex);
        if (result == null || result.items == null || result.items.length == 0 || result.items[0] == null) {
            return null;
        }
        return result.items[0].copy();
    }

    private boolean addToFirstFreeSlot(ItemStack[] stacks, ItemStack stack) {
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] == null) {
                stacks[i] = stack.copy();
                return true;
            }
        }
        return false;
    }
}
