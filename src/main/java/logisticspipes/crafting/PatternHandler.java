package logisticspipes.crafting;

import logisticspipes.LogisticsPipes;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.item.SimpleStackInventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
        if (stack == null || stack.getItem() != LogisticsPipes.LogisticsPattern || !Pattern.isConfigured(stack)) {
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
            for (ItemIdentifierStack ingredient : Pattern.getIngredients(pattern)) {
                items.add(ingredient.getItem());
            }
        }
        return items;
    }

    boolean isIngredient(ItemIdentifier item) {
        return getIngredientItems().contains(item);
    }

    boolean containsIngredient(ItemStack pattern, ItemIdentifier item) {
        return ingredientAmount(pattern, item) > 0;
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
        for (ItemIdentifierStack result : Pattern.getResults(pattern)) {
            if (result.getItem().equalsForCrafting(item)) {
                amount += result.getStackSize();
            }
        }
        return amount;
    }

    int ingredientAmount(ItemStack pattern, ItemIdentifier item) {
        int amount = 0;
        if (pattern == null || item == null) {
            return amount;
        }
        for (ItemIdentifierStack ingredient : Pattern.getIngredients(pattern)) {
            if (ingredient.getItem().equalsForCrafting(item)) {
                amount += ingredient.getStackSize();
            }
        }
        return amount;
    }

    List<ItemIdentifierStack> getAggregatedIngredients(ItemStack pattern) {
        List<ItemIdentifierStack> result = new ArrayList<>();
        if (pattern == null) {
            return result;
        }
        for (ItemIdentifierStack ingredient : Pattern.getIngredients(pattern)) {
            boolean merged = false;
            for (ItemIdentifierStack existing : result) {
                if (existing.getItem().equalsForCrafting(ingredient.getItem())) {
                    existing.setStackSize(existing.getStackSize() + ingredient.getStackSize());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                result.add(ingredient.clone());
            }
        }
        return result;
    }
}
