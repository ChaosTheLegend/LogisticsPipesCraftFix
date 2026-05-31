package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.item.ItemStack;

import logisticspipes.LogisticsPipes;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.SimpleStackInventory;

class PatternHandler {

    private final SimpleStackInventory patternInventory;

    PatternHandler(SimpleStackInventory patternInventory) {
        this.patternInventory = patternInventory;
    }

    int size() {
        return patternInventory.getSizeInventory();
    }

    ItemStack getConfiguredPatternStack(int slot) {
        if (slot < 0 || slot >= size()) {
            return null;
        }
        ItemStack stack = patternInventory.getStackInSlot(slot);
        if (stack == null || stack.getItem() != LogisticsPipes.LogisticsPattern
                || !Pattern.fromStack(stack).isConfigured()) {
            return null;
        }
        return stack;
    }

    List<ItemStack> getConfiguredPatterns() {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < size(); i++) {
            ItemStack pattern = getConfiguredPatternStack(i);
            if (pattern != null) {
                result.add(pattern);
            }
        }
        return result;
    }

    Set<ItemIdentifier> getIngredientItems() {
        Set<ItemIdentifier> items = new TreeSet<>();
        for (ItemStack pattern : getConfiguredPatterns()) {
            AbstractPattern configuredPattern = Pattern.fromStack(pattern);
            for (IPatternStack ingredient : configuredPattern.getInputs()) {
                ItemIdentifier item = PatternStackHelper.getRoutingItem(ingredient);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    boolean isIngredient(ItemIdentifier item) {
        FluidIdentifier fluid = item != null && item.isFluidContainer() ? FluidIdentifier.get(item) : null;
        if (fluid != null) {
            return isFluidIngredient(fluid);
        }
        return getIngredientItems().contains(item);
    }

    boolean containsIngredient(ItemStack pattern, ItemIdentifier item) {
        FluidIdentifier fluid = item != null && item.isFluidContainer() ? FluidIdentifier.get(item) : null;
        if (fluid != null) {
            return fluidIngredientAmount(pattern, fluid) > 0;
        }
        return ingredientAmount(pattern, item) > 0;
    }

    boolean isFluidIngredient(FluidIdentifier fluid) {
        for (ItemStack pattern : getConfiguredPatterns()) {
            if (fluidIngredientAmount(pattern, fluid) > 0) {
                return true;
            }
        }
        return false;
    }

    int findPatternSlotForResult(ItemIdentifier item) {
        for (int slot = 0; slot < size(); slot++) {
            ItemStack pattern = getConfiguredPatternStack(slot);
            if (pattern == null) {
                continue;
            }
            if (resultAmount(slot, item) > 0) {
                return slot;
            }
        }
        return -1;
    }

    int resultAmount(int patternSlot, ItemIdentifier item) {
        ItemStack pattern = getConfiguredPatternStack(patternSlot);
        if (pattern == null || item == null) {
            return 0;
        }
        int amount = 0;
        for (IPatternStack result : Pattern.fromStack(pattern).getOutputs()) {
            if (PatternStackHelper.matches(result, item)) {
                amount += result.getAmount();
            }
        }
        return amount;
    }

    int fluidIngredientAmount(ItemStack pattern, FluidIdentifier fluid) {
        int amount = 0;
        if (pattern == null || fluid == null) {
            return amount;
        }
        for (IPatternStack ingredient : Pattern.fromStack(pattern).getInputs()) {
            if (PatternStackHelper.matches(ingredient, fluid)) {
                amount += ingredient.getAmount();
            }
        }
        return amount;
    }

    int ingredientAmount(ItemStack pattern, ItemIdentifier item) {
        int amount = 0;
        if (pattern == null || item == null) {
            return amount;
        }
        for (IPatternStack ingredient : Pattern.fromStack(pattern).getInputs()) {
            if (PatternStackHelper.matches(ingredient, item)) {
                amount += ingredient.getAmount();
            }
        }
        return amount;
    }

    List<IPatternStack> getAggregatedInputs(ItemStack pattern) {
        if (pattern == null) {
            return new ArrayList<>();
        }
        return Pattern.fromStack(pattern).getAggregatedInputs();
    }

    List<IPatternStack> getAggregatedOutputs(ItemStack pattern) {
        if (pattern == null) {
            return new ArrayList<>();
        }
        return Pattern.fromStack(pattern).getAggregatedOutputs();
    }

}
