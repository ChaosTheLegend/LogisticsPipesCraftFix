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

    /**
     * Removes an amount of an Item that is stored in the request handler.
     * Normally called on arrival of items on the pipe.
     * <br>
     * If the requested items are empty after the removal, remove the entry in the backing map
     * @param patternSlot the slot of the pattern
     * @param stack the stack of the item
     */
    void remove(int patternSlot, IPatternStack stack) {
        if (stack == null || stack.getAmount() <= 0) return;

        //get the stored requested item stack
        IPatternStack requested = requestedItemForPattern(patternSlot, stack);
        if (requested == null) return;

        //make sure we cant go negative
        int removed = Math.min(stack.getAmount(), requested.getAmount());
        requested.addAmount(-removed);

        removeEntryIfEmpty(patternSlot);
    }

    /**
     * Removes the entry for the given pattern, if it has no more request buffer
     * @param patternSlot the slot to check
     */
    private void removeEntryIfEmpty(int patternSlot) {
        getRequested(patternSlot).removeIf(requested -> requested.getAmount() <= 0);
        if (getRequested(patternSlot).isEmpty()) requestedIngredients.remove(patternSlot);
    }

    private IPatternStack requestedItemForPattern(int patternSlot, IPatternStack stack) {
        for (var requested : getRequested(patternSlot)) {
            if (requested.canMerge(stack)) return requested;
        }
        return null;
    }

    private List<IPatternStack> getRequested(int patternSlot) {
        return requestedIngredients.computeIfAbsent(patternSlot, k -> new ArrayList<>());
    }
}
