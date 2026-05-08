package logisticspipes.crafting;

import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class IngredientBufferHandler {

    private final Map<Integer, List<ItemIdentifierStack>> bufferedIngredients;
    private final PatternHandler patternHandler;

    IngredientBufferHandler(Map<Integer, List<ItemIdentifierStack>> bufferedIngredients, PatternHandler patternHandler) {
        this.bufferedIngredients = bufferedIngredients;
        this.patternHandler = patternHandler;
    }

    int amount(int patternSlot, ItemIdentifier item) {
        int amount = 0;
        for (ItemIdentifierStack stack : getBuffer(patternSlot)) {
            if (stack.getItem().equalsForCrafting(item)) {
                amount += stack.getStackSize();
            }
        }
        return amount;
    }

    void add(int patternSlot, ItemIdentifier item, int amount) {
        if (amount <= 0) {
            return;
        }
        List<ItemIdentifierStack> buffer = getBuffer(patternSlot);
        for (ItemIdentifierStack buffered : buffer) {
            if (buffered.getItem().equalsForCrafting(item)) {
                buffered.setStackSize(buffered.getStackSize() + amount);
                return;
            }
        }
        buffer.add(new ItemIdentifierStack(item, amount));
    }

    int completeSets(int patternSlot, ItemStack pattern) {
        int sets = Integer.MAX_VALUE;
        for (ItemIdentifierStack ingredient : patternHandler.getAggregatedIngredients(pattern)) {
            if (ingredient.getStackSize() <= 0) {
                continue;
            }
            sets = Math.min(sets, amount(patternSlot, ingredient.getItem()) / ingredient.getStackSize());
        }
        return sets == Integer.MAX_VALUE ? 0 : Math.max(0, sets);
    }

    boolean canCompleteOneSetAfterAdding(int patternSlot, ItemStack pattern, ItemIdentifier arrivingItem, int arrivingAmount) {
        if (arrivingAmount <= 0) {
            return completeSets(patternSlot, pattern) > 0;
        }
        for (ItemIdentifierStack ingredient : patternHandler.getAggregatedIngredients(pattern)) {
            int available = amount(patternSlot, ingredient.getItem());
            if (ingredient.getItem().equalsForCrafting(arrivingItem)) {
                available += arrivingAmount;
            }
            if (available < ingredient.getStackSize()) {
                return false;
            }
        }
        return true;
    }

    void removePatternSets(int patternSlot, ItemStack pattern, int sets) {
        if (sets <= 0) {
            return;
        }
        for (ItemIdentifierStack ingredient : patternHandler.getAggregatedIngredients(pattern)) {
            remove(patternSlot, ingredient.getItem(), ingredient.getStackSize() * sets);
        }
    }

    boolean remove(int patternSlot, ItemIdentifier item, int amount) {
        List<ItemIdentifierStack> buffer = getBuffer(patternSlot);
        for (int i = 0; i < buffer.size() && amount > 0; i++) {
            ItemIdentifierStack buffered = buffer.get(i);
            if (!buffered.getItem().equalsForCrafting(item)) {
                continue;
            }
            int removed = Math.min(amount, buffered.getStackSize());
            buffered.setStackSize(buffered.getStackSize() - removed);
            amount -= removed;
            if (buffered.getStackSize() <= 0) {
                buffer.remove(i);
                i--;
            }
        }
        if (buffer.isEmpty()) {
            bufferedIngredients.remove(patternSlot);
        }
        return amount == 0;
    }

    private List<ItemIdentifierStack> getBuffer(int patternSlot) {
        return bufferedIngredients.computeIfAbsent(patternSlot, k -> new ArrayList<>());
    }
}
