package logisticspipes.crafting;

import logisticspipes.utils.FluidIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class FluidIngredientRequestHandler {

    private final Map<Integer, List<PatternFluidStack>> requestedIngredients;

    FluidIngredientRequestHandler(Map<Integer, List<PatternFluidStack>> requestedIngredients) {
        this.requestedIngredients = requestedIngredients;
    }

    int amount(int patternSlot, FluidIdentifier fluid) {
        int amount = 0;
        for (PatternFluidStack stack : getRequested(patternSlot)) {
            if (stack.getFluid().equals(fluid)) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }

    void add(int patternSlot, FluidIdentifier fluid, int amount) {
        if (amount <= 0) {
            return;
        }
        List<PatternFluidStack> requested = getRequested(patternSlot);
        for (PatternFluidStack stack : requested) {
            if (stack.getFluid().equals(fluid)) {
                stack.addAmount(amount);
                return;
            }
        }
        requested.add(new PatternFluidStack(fluid, amount));
    }

    void remove(int patternSlot, FluidIdentifier fluid, int amount) {
        if (amount <= 0) {
            return;
        }
        List<PatternFluidStack> requested = getRequested(patternSlot);
        for (int i = 0; i < requested.size() && amount > 0; i++) {
            PatternFluidStack stack = requested.get(i);
            if (!stack.getFluid().equals(fluid)) {
                continue;
            }
            int removed = Math.min(amount, stack.getAmount());
            stack.addAmount(-removed);
            amount -= removed;
            if (stack.getAmount() <= 0) {
                requested.remove(i);
                i--;
            }
        }
        if (requested.isEmpty()) {
            requestedIngredients.remove(patternSlot);
        }
    }

    private List<PatternFluidStack> getRequested(int patternSlot) {
        return requestedIngredients.computeIfAbsent(patternSlot, k -> new ArrayList<>());
    }
}
