package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;

class PatternStackBufferHandler {

    private final Map<Integer, List<IPatternStack>> bufferedIngredients;

    PatternStackBufferHandler(Map<Integer, List<IPatternStack>> bufferedIngredients) {
        this.bufferedIngredients = bufferedIngredients;
    }

    int amount(int patternSlot, IPatternStack stack) {
        if (stack == null) {
            return 0;
        }
        int amount = 0;
        for (IPatternStack buffered : getBuffer(patternSlot)) {
            if (buffered.canMerge(stack)) {
                amount += buffered.getAmount();
            }
        }
        return amount;
    }

    int amount(int patternSlot, ItemIdentifier item) {
        int amount = 0;
        for (IPatternStack buffered : getBuffer(patternSlot)) {
            if (PatternStackHelper.matches(buffered, item)) {
                amount += buffered.getAmount();
            }
        }
        return amount;
    }

    int amount(int patternSlot, FluidIdentifier fluid) {
        int amount = 0;
        for (IPatternStack buffered : getBuffer(patternSlot)) {
            if (PatternStackHelper.matches(buffered, fluid)) {
                amount += buffered.getAmount();
            }
        }
        return amount;
    }

    void add(int patternSlot, IPatternStack stack) {
        if (stack == null || stack.getAmount() <= 0) {
            return;
        }
        List<IPatternStack> buffer = getBuffer(patternSlot);
        PatternStackHelper.addAggregated(buffer, stack);
    }

    int completeSets(int patternSlot, List<IPatternStack> ingredients) {
        int sets = Integer.MAX_VALUE;
        for (IPatternStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getAmount() <= 0) {
                continue;
            }
            sets = Math.min(sets, amount(patternSlot, ingredient) / ingredient.getAmount());
        }
        return sets == Integer.MAX_VALUE ? 0 : Math.max(0, sets);
    }

    boolean canCompleteOneSetAfterAdding(
            int patternSlot,
            List<IPatternStack> ingredients,
            IPatternStack arrivingStack) {
        if (arrivingStack == null || arrivingStack.getAmount() <= 0) {
            return completeSets(patternSlot, ingredients) > 0;
        }
        for (IPatternStack ingredient : ingredients) {
            int available = amount(patternSlot, ingredient);
            if (ingredient.canMerge(arrivingStack)) {
                available += arrivingStack.getAmount();
            }
            if (available < ingredient.getAmount()) {
                return false;
            }
        }
        return true;
    }

    void removePatternSets(int patternSlot, List<IPatternStack> ingredients, int sets) {
        if (sets <= 0) {
            return;
        }
        for (IPatternStack ingredient : ingredients) {
            remove(patternSlot, ingredient, ingredient.getAmount() * sets);
        }
    }

    boolean remove(int patternSlot, IPatternStack stack, int amount) {
        if (stack == null || amount <= 0) {
            return true;
        }
        List<IPatternStack> buffer = getBuffer(patternSlot);
        for (int i = 0; i < buffer.size() && amount > 0; i++) {
            IPatternStack buffered = buffer.get(i);
            if (!buffered.canMerge(stack)) {
                continue;
            }
            int removed = Math.min(amount, buffered.getAmount());
            buffered.addAmount(-removed);
            amount -= removed;
            if (buffered.getAmount() <= 0) {
                buffer.remove(i);
                i--;
            }
        }
        if (buffer.isEmpty()) {
            bufferedIngredients.remove(patternSlot);
        }
        return amount == 0;
    }

    private List<IPatternStack> getBuffer(int patternSlot) {
        return bufferedIngredients.computeIfAbsent(patternSlot, k -> new ArrayList<>());
    }
}
