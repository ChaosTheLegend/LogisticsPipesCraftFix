package logisticspipes.crafting;

import logisticspipes.utils.FluidIdentifier;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class FluidIngredientBufferHandler {

    private final Map<Integer, List<PatternFluidStack>> bufferedIngredients;
    private final PatternHandler patternHandler;

    FluidIngredientBufferHandler(
            Map<Integer, List<PatternFluidStack>> bufferedIngredients,
            PatternHandler patternHandler) {
        this.bufferedIngredients = bufferedIngredients;
        this.patternHandler = patternHandler;
    }

    int amount(int patternSlot, FluidIdentifier fluid) {
        int amount = 0;
        for (PatternFluidStack stack : getBuffer(patternSlot)) {
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
        List<PatternFluidStack> buffer = getBuffer(patternSlot);
        for (PatternFluidStack buffered : buffer) {
            if (buffered.getFluid().equals(fluid)) {
                buffered.addAmount(amount);
                return;
            }
        }
        buffer.add(new PatternFluidStack(fluid, amount));
    }

    int completeSets(int patternSlot, ItemStack pattern) {
        int sets = Integer.MAX_VALUE;
        boolean hasIngredient = false;
        for (PatternFluidStack ingredient : patternHandler.getAggregatedFluidIngredients(pattern)) {
            if (ingredient.getAmount() <= 0) {
                continue;
            }
            hasIngredient = true;
            sets = Math.min(sets, amount(patternSlot, ingredient.getFluid()) / ingredient.getAmount());
        }
        return hasIngredient ? Math.max(0, sets) : Integer.MAX_VALUE;
    }

    boolean canCompleteOneSetAfterAdding(
            int patternSlot,
            ItemStack pattern,
            FluidIdentifier arrivingFluid,
            int arrivingAmount) {
        for (PatternFluidStack ingredient : patternHandler.getAggregatedFluidIngredients(pattern)) {
            int available = amount(patternSlot, ingredient.getFluid());
            if (ingredient.getFluid().equals(arrivingFluid)) {
                available += arrivingAmount;
            }
            if (available < ingredient.getAmount()) {
                return false;
            }
        }
        return true;
    }

    void removePatternSets(int patternSlot, ItemStack pattern, int sets) {
        if (sets <= 0) {
            return;
        }
        for (PatternFluidStack ingredient : patternHandler.getAggregatedFluidIngredients(pattern)) {
            remove(patternSlot, ingredient.getFluid(), ingredient.getAmount() * sets);
        }
    }

    boolean remove(int patternSlot, FluidIdentifier fluid, int amount) {
        List<PatternFluidStack> buffer = getBuffer(patternSlot);
        for (int i = 0; i < buffer.size() && amount > 0; i++) {
            PatternFluidStack buffered = buffer.get(i);
            if (!buffered.getFluid().equals(fluid)) {
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

    private List<PatternFluidStack> getBuffer(int patternSlot) {
        return bufferedIngredients.computeIfAbsent(patternSlot, k -> new ArrayList<>());
    }
}
