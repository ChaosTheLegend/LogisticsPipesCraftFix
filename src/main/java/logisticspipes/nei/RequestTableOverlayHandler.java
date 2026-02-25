package logisticspipes.nei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.oredict.OreDictionary;

import codechicken.lib.inventory.InventoryUtils;
import codechicken.nei.FastTransferManager;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;
import cpw.mods.fml.client.FMLClientHandler;
import logisticspipes.gui.orderer.GuiRequestTable;
import logisticspipes.gui.popup.GuiRecipeImport;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.NEISetCraftingRecipe;
import logisticspipes.pipes.PipeBlockRequestTable;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;

public class RequestTableOverlayHandler implements IOverlayHandler {

    public static class DistributedIngred {

        public DistributedIngred(ItemStack item) {
            stack = InventoryUtils.copyStack(item, 1);
        }

        public ItemStack stack;
        public int invAmount;
        public int distributed;
        public int numSlots;
        public int recipeAmount;
    }

    public static class IngredientDistribution {

        public IngredientDistribution(DistributedIngred distrib, ItemStack permutation) {
            this.distrib = distrib;
            this.permutation = permutation;
        }

        public DistributedIngred distrib;
        public ItemStack permutation;
        public Slot[] slots;
    }

    @Override
    public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean shift) {
        if (shift) {
            transferRecipe(firstGui, recipe, recipeIndex, 1);
            return;
        }

        TileEntity tile;
        LogisticsBaseGuiScreen gui;
        if (firstGui instanceof GuiRequestTable) {
            tile = ((GuiRequestTable) firstGui)._table.container;
            gui = (GuiRequestTable) firstGui;
        } else {
            return;
        }

        ItemStack[] stack = new ItemStack[9];
        ItemStack[][] stacks = new ItemStack[9][];
        boolean hasCandidates = false;
        NEISetCraftingRecipe packet = PacketHandler.getPacket(NEISetCraftingRecipe.class);
        for (PositionedStack ps : recipe.getIngredientStacks(recipeIndex)) {
            int x = (ps.relx - 25) / 18;
            int y = (ps.rely - 6) / 18;
            int slot = x + y * 3;
            if (x < 0 || x > 2 || y < 0 || y > 2 || slot < 0 || slot > 8) {
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
                    hasCandidates = true;
                } else if (stacks[slot].length == 1) {
                    stack[slot] = stacks[slot][0];
                }
            }
        }
        if (hasCandidates) {
            gui.setSubGui(new GuiRecipeImport(tile, stacks));
        } else {
            MainProxy.sendPacketToServer(
                    packet.setContent(stack).setPosX(tile.xCoord).setPosY(tile.yCoord).setPosZ(tile.zCoord));
        }
    }

    public int transferRecipe(GuiContainer gui, IRecipeHandler handler, int recipeIndex, int multiplier) {
        if (!(gui instanceof GuiRequestTable)) {
            return 0;
        }

        final List<PositionedStack> ingredients = handler.getIngredientStacks(recipeIndex);
        final List<DistributedIngred> ingredStacks = getPermutationIngredients(ingredients);

        findInventoryQuantities(gui, ingredStacks);

        final List<IngredientDistribution> assignedIngredients = assignIngredients(ingredients, ingredStacks);
        if (assignedIngredients == null) return 0;

        multiplier = Math.min(multiplier == 0 ? 64 : multiplier, calculateRecipeQuantity(assignedIngredients));

        if (multiplier > 0) {
            moveIngredients(gui, assignedIngredients, multiplier);
        }

        final int finalMultiplier = multiplier;
        if (multiplier > 0 && assignedIngredients.stream().allMatch(distrib -> {
            DistributedIngred istack = distrib.distrib;
            if (istack.recipeAmount == 0) return true;
            return istack.invAmount >= istack.recipeAmount * finalMultiplier;
        })) {
            ((GuiRequestTable) gui).requestMatrix(finalMultiplier);
        }

        return multiplier;
    }

    private void moveIngredients(GuiContainer gui, List<IngredientDistribution> assignedIngredients, int multiplier) {
        for (IngredientDistribution distrib : assignedIngredients) {
            distrib.slots = getInternalTargetSlots(gui, distrib.permutation);
        }

        for (Slot slot : (List<Slot>) gui.inventorySlots.inventorySlots) {
            if (!slot.getHasStack() || !canMoveFrom(slot, gui) || !slot.canTakeStack(gui.mc.thePlayer))
                continue;
            ItemStack stack = slot.getStack();
            int slotTransferCap = stack.getMaxStackSize();

            for (IngredientDistribution distrib : assignedIngredients) {
                if (distrib.slots.length == 0 || !slot.getHasStack() || !canStack(distrib.permutation, stack)) continue;
                int transferCap = Math.min(slotTransferCap, multiplier * distrib.permutation.stackSize);
                int stackSize = slot.getStack().stackSize;
                boolean pickup = false;

                for (Slot dest : distrib.slots) {
                    if (slot == dest) continue;
                    int amount = Math.min(transferCap - (dest.getHasStack() ? dest.getStack().stackSize : 0), stackSize);

                    if (stackSize <= amount) {
                        if (!pickup) {
                            FastTransferManager.clickSlot(gui, slot.slotNumber);
                        }
                        FastTransferManager.clickSlot(gui, dest.slotNumber);
                        break;
                    } else if (amount > 0) {
                        for (int c = 0; c < amount; c++) {
                            if (pickup != (pickup = true)) {
                                FastTransferManager.clickSlot(gui, slot.slotNumber);
                            }
                            FastTransferManager.clickSlot(gui, dest.slotNumber, 1);
                            stackSize--;
                        }
                    }
                }

                if (gui.mc.thePlayer.inventory.getItemStack() != null) {
                    FastTransferManager.clickSlot(gui, slot.slotNumber);
                }
            }
        }
    }

    private int calculateRecipeQuantity(List<IngredientDistribution> assignedIngredients) {
        int quantity = Integer.MAX_VALUE;

        for (IngredientDistribution distrib : assignedIngredients) {
            final DistributedIngred istack = distrib.distrib;
            if (istack.distributed == 0) continue;
            if (istack.numSlots == 0) return 0;

            final int maxStackSize = istack.stack.getMaxStackSize();
            final int allSlots = Math.min(istack.invAmount, istack.numSlots * maxStackSize);
            quantity = Math.min(quantity, allSlots / istack.distributed);
        }

        return quantity == Integer.MAX_VALUE ? 1 : quantity;
    }

    private List<IngredientDistribution> assignIngredients(List<PositionedStack> ingredients,
            List<DistributedIngred> ingredStacks) {
        ArrayList<IngredientDistribution> assignedIngredients = new ArrayList<>();
        for (PositionedStack posstack : ingredients) {
            DistributedIngred biggestIngred = null;
            ItemStack permutation = null;
            int biggestSize = 0;
            for (ItemStack pstack : posstack.items) {
                for (DistributedIngred istack : ingredStacks) {
                    if (!canStack(pstack, istack.stack) || istack.invAmount - istack.distributed < pstack.stackSize
                            || istack.recipeAmount == 0
                            || pstack.stackSize == 0)
                        continue;

                    int relsize = (istack.invAmount - istack.invAmount / istack.recipeAmount * istack.distributed)
                            / pstack.stackSize;
                    if (relsize > biggestSize) {
                        biggestSize = relsize;
                        biggestIngred = istack;
                        permutation = pstack;
                        break;
                    }
                }
            }

            if (biggestIngred == null) {
                biggestIngred = new DistributedIngred(posstack.item);
                permutation = InventoryUtils.copyStack(posstack.item, 0);
            }

            biggestIngred.distributed += permutation.stackSize;
            assignedIngredients.add(new IngredientDistribution(biggestIngred, permutation));
        }

        return assignedIngredients;
    }

    private void findInventoryQuantities(GuiContainer gui, List<DistributedIngred> ingredStacks) {
        for (Slot slot : (List<Slot>) gui.inventorySlots.inventorySlots) {
            if (slot.getHasStack() && canMoveFrom(slot, gui)) {
                final ItemStack pstack = slot.getStack();
                final DistributedIngred istack = findIngred(ingredStacks, pstack);

                if (istack != null) {
                    istack.invAmount += pstack.stackSize;
                }
            }
        }
        if (gui instanceof GuiRequestTable) {
            PipeBlockRequestTable table = ((GuiRequestTable) gui)._table;
            for (int i = 0; i < table.inv.getSizeInventory(); i++) {
                ItemStack stack = table.inv.getStackInSlot(i);
                if (stack != null) {
                    DistributedIngred istack = findIngred(ingredStacks, stack);
                    if (istack != null) {
                        istack.invAmount += stack.stackSize;
                    }
                }
            }
        }
    }

    private List<DistributedIngred> getPermutationIngredients(List<PositionedStack> ingredients) {
        ArrayList<DistributedIngred> ingredStacks = new ArrayList<>();
        for (PositionedStack posstack : ingredients) {
            for (ItemStack pstack : posstack.items) {
                DistributedIngred istack = findIngred(ingredStacks, pstack);
                if (istack == null) ingredStacks.add(istack = new DistributedIngred(pstack));
                istack.recipeAmount += pstack.stackSize;
            }
        }
        return ingredStacks;
    }

    public boolean canMoveFrom(Slot slot, GuiContainer gui) {
        if (gui instanceof GuiRequestTable) {
            return slot.inventory instanceof InventoryPlayer || slot.inventory == ((GuiRequestTable) gui)._table.inv;
        }
        return slot.inventory instanceof InventoryPlayer;
    }

    private Slot[] getInternalTargetSlots(GuiContainer gui, ItemStack stack) {
        List<Slot> targetSlots = new ArrayList<>();
        if (gui instanceof GuiRequestTable) {
            PipeBlockRequestTable table = ((GuiRequestTable) gui)._table;
            for (Slot slot : (List<Slot>) gui.inventorySlots.inventorySlots) {
                if (slot.inventory == table.inv) {
                    if (!slot.getHasStack() || (canStack(slot.getStack(), stack) && slot.getStack().stackSize < slot.getSlotStackLimit())) {
                        targetSlots.add(slot);
                    }
                }
            }
        }
        return targetSlots.toArray(new Slot[0]);
    }

    public DistributedIngred findIngred(List<DistributedIngred> ingredStacks, ItemStack pstack) {
        for (DistributedIngred istack : ingredStacks) if (canStack(istack.stack, pstack)) return istack;
        return null;
    }

    protected boolean canStack(ItemStack stack1, ItemStack stack2) {
        if (stack1 == null || stack2 == null) return true;
        return NEIClientUtils.areStacksSameTypeCraftingWithNBT(stack1, stack2);
    }
}
