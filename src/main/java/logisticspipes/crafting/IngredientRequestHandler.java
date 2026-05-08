package logisticspipes.crafting;

import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class IngredientRequestHandler {

    private final Map<Integer, List<ItemIdentifierStack>> requestedIngredients;

    IngredientRequestHandler(Map<Integer, List<ItemIdentifierStack>> requestedIngredients) {
        this.requestedIngredients = requestedIngredients;
    }

    int amount(int patternSlot, ItemIdentifier item) {
        int amount = 0;
        for (ItemIdentifierStack stack : getRequested(patternSlot)) {
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
        List<ItemIdentifierStack> requested = getRequested(patternSlot);
        for (ItemIdentifierStack stack : requested) {
            if (stack.getItem().equalsForCrafting(item)) {
                stack.setStackSize(stack.getStackSize() + amount);
                return;
            }
        }
        requested.add(new ItemIdentifierStack(item, amount));
    }

    void remove(int patternSlot, ItemIdentifier item, int amount) {
        if (amount <= 0) {
            return;
        }
        List<ItemIdentifierStack> requested = getRequested(patternSlot);
        for (int i = 0; i < requested.size() && amount > 0; i++) {
            ItemIdentifierStack stack = requested.get(i);
            if (!stack.getItem().equalsForCrafting(item)) {
                continue;
            }
            int removed = Math.min(amount, stack.getStackSize());
            stack.setStackSize(stack.getStackSize() - removed);
            amount -= removed;
            if (stack.getStackSize() <= 0) {
                requested.remove(i);
                i--;
            }
        }
        if (requested.isEmpty()) {
            requestedIngredients.remove(patternSlot);
        }
    }

    private List<ItemIdentifierStack> getRequested(int patternSlot) {
        return requestedIngredients.computeIfAbsent(patternSlot, k -> new ArrayList<>());
    }
}
