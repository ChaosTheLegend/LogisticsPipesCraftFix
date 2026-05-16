package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import logisticspipes.LogisticsPipes;

public final class PatternNEIImportHandler {

    private PatternNEIImportHandler() {}

    public static void importRecipe(EntityPlayer player, int patternInventorySlot, ItemStack[] inputs, ItemStack[] outputs) {
        if (patternInventorySlot < 0 || patternInventorySlot >= player.inventory.mainInventory.length) {
            return;
        }
        ItemStack patternStack = player.inventory.mainInventory[patternInventorySlot];
        if (patternStack == null || patternStack.getItem() != LogisticsPipes.LogisticsPattern) {
            return;
        }

        AbstractPattern pattern = Pattern.fromStack(patternStack);
        clearRange(pattern, 0, pattern.getIngredientSlotCount());
        clearRange(pattern, pattern.getResultSlotStart(), pattern.getItemSlotCount());

        ItemStack[] recipeInputs = inputs != null ? inputs : new ItemStack[0];
        for (int i = 0; i < pattern.getIngredientSlotCount(); i++) {
            pattern.setPatternStackInSlot(i, i < recipeInputs.length ? IPatternStack.fromItemStack(copy(recipeInputs[i])) : null);
        }

        List<IPatternStack> aggregatedOutputs = aggregate(outputs);
        for (int i = 0; i < pattern.getResultSlotCount() && i < aggregatedOutputs.size(); i++) {
            pattern.setPatternStackInSlot(pattern.getResultSlotStart() + i, aggregatedOutputs.get(i));
        }

        player.inventory.markDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
    }

    public static List<IPatternStack> aggregate(ItemStack[] stacks) {
        List<IPatternStack> result = new ArrayList<>();
        if (stacks == null) {
            return result;
        }
        for (ItemStack stack : stacks) {
            addAggregated(result, IPatternStack.fromItemStack(copy(stack)));
        }
        return result;
    }

    public static ItemStack[] toPatternItemStacks(List<IPatternStack> stacks) {
        ItemStack[] result = new ItemStack[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) {
            result[i] = stacks.get(i).makePatternStack();
        }
        return result;
    }

    public static void addAggregated(List<IPatternStack> stacks, IPatternStack stack) {
        if (stack == null || stack.getAmount() <= 0) {
            return;
        }
        for (IPatternStack existing : stacks) {
            if (existing.canMerge(stack)) {
                existing.addAmount(stack.getAmount());
                return;
            }
        }
        stacks.add(stack.copy());
    }

    private static void clearRange(AbstractPattern pattern, int start, int end) {
        for (int slot = start; slot < end; slot++) {
            pattern.setPatternStackInSlot(slot, null);
        }
    }

    private static ItemStack copy(ItemStack stack) {
        return stack != null && stack.stackSize > 0 ? stack.copy() : null;
    }
}
