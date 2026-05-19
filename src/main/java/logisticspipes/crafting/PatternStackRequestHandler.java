package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;

class PatternStackRequestHandler {

    private final Map<Integer, List<IPatternStack>> requestedIngredients;

    PatternStackRequestHandler(Map<Integer, List<IPatternStack>> requestedIngredients) {
        this.requestedIngredients = requestedIngredients;
    }

    int amount(int patternSlot, IPatternStack stack) {
        if (stack == null) {
            return 0;
        }
        int amount = 0;
        for (IPatternStack requested : getRequested(patternSlot)) {
            if (requested.canMerge(stack)) {
                amount += requested.getAmount();
            }
        }
        return amount;
    }

    int amount(int patternSlot, ItemIdentifier item) {
        int amount = 0;
        for (IPatternStack requested : getRequested(patternSlot)) {
            if (PatternStackHelper.matches(requested, item)) {
                amount += requested.getAmount();
            }
        }
        return amount;
    }

    int amount(int patternSlot, FluidIdentifier fluid) {
        int amount = 0;
        for (IPatternStack requested : getRequested(patternSlot)) {
            if (PatternStackHelper.matches(requested, fluid)) {
                amount += requested.getAmount();
            }
        }
        return amount;
    }

    void add(int patternSlot, IPatternStack stack) {
        if (stack == null || stack.getAmount() <= 0) {
            return;
        }
        List<IPatternStack> requested = getRequested(patternSlot);
        PatternStackHelper.addAggregated(requested, stack);
    }

    void remove(int patternSlot, IPatternStack stack, int amount) {
        if (stack == null || amount <= 0) {
            return;
        }
        List<IPatternStack> requested = getRequested(patternSlot);
        for (int i = 0; i < requested.size() && amount > 0; i++) {
            IPatternStack current = requested.get(i);
            if (!current.canMerge(stack)) {
                continue;
            }
            int removed = Math.min(amount, current.getAmount());
            current.addAmount(-removed);
            amount -= removed;
            if (current.getAmount() <= 0) {
                requested.remove(i);
                i--;
            }
        }
        if (requested.isEmpty()) {
            requestedIngredients.remove(patternSlot);
        }
    }

    private List<IPatternStack> getRequested(int patternSlot) {
        return requestedIngredients.computeIfAbsent(patternSlot, k -> new ArrayList<>());
    }
}
