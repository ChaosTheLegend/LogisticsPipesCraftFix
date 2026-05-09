package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;

import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

class PatternCraftingOrder {

    final int patternSlot;
    final int resultAmountPerSet;
    final List<PatternCraftingBranch> ingredientBranches;
    int remainingSets;

    private final IOrderInfoProvider outputOrder;
    private final PatternHandler patternHandler;
    private final IngredientRequestHandler requestedIngredient;
    private final FluidIngredientRequestHandler requestedFluidIngredient;

    PatternCraftingOrder(
            int patternSlot,
            int resultAmountPerSet,
            PatternCraftingBranch branch,
            IOrderInfoProvider outputOrder,
            PatternHandler patternHandler,
            IngredientRequestHandler requestedIngredient,
            FluidIngredientRequestHandler requestedFluidIngredient) {
        this.patternSlot = patternSlot;
        this.resultAmountPerSet = Math.max(1, resultAmountPerSet);
        this.remainingSets = (branch.getRequestType().getRequestedAmount() + this.resultAmountPerSet - 1)
                / this.resultAmountPerSet;
        this.ingredientBranches = new ArrayList<>(branch.getSubRequests());
        this.outputOrder = outputOrder;
        this.patternHandler = patternHandler;
        this.requestedIngredient = requestedIngredient;
        this.requestedFluidIngredient = requestedFluidIngredient;
    }

    /**
     * Returns true once all required ingredient sets were requested.
     * <p>
     * A branch with no ingredients is also complete here: the output order can still be fulfilled from already-produced
     * items in the connected inventory, but there is no additional subtree work to request.
     */
    boolean isFullyRequested() {
        return remainingSets <= 0 || ingredientBranches.isEmpty();
    }

    /**
     * Calculates how many pattern sets can still be produced from the remaining request-tree branches.
     */
    int availableSetsFromBranches(ItemStack pattern) {
        int sets = Integer.MAX_VALUE;
        for (ItemIdentifierStack ingredient : patternHandler.getAggregatedIngredients(pattern)) {
            sets = Math.min(sets, availableFromBranches(ingredient.getItem()) / ingredient.getStackSize());
        }
        for (PatternFluidStack ingredient : patternHandler.getAggregatedFluidIngredients(pattern)) {
            sets = Math.min(sets, availableFromBranches(ingredient.getFluid()) / ingredient.getAmount());
        }
        return sets == Integer.MAX_VALUE ? 0 : Math.max(0, sets);
    }

    /**
     * Requests ingredients for up to {@code sets} pattern sets and records those in-flight ingredients as reserved
     * module buffer space.
     */
    int requestIngredients(ItemStack pattern, int sets) {
        int requestedSets = sets;
        for (ItemIdentifierStack ingredient : patternHandler.getAggregatedIngredients(pattern)) {
            int requested = requestFromBranches(ingredient.getItem(), ingredient.getStackSize() * requestedSets);
            requestedIngredient.add(patternSlot, ingredient.getItem(), requested);
            requestedSets = Math.min(requestedSets, requested / ingredient.getStackSize());
        }
        for (PatternFluidStack ingredient : patternHandler.getAggregatedFluidIngredients(pattern)) {
            int requested = requestFromBranches(ingredient.getFluid(), ingredient.getAmount() * requestedSets);
            requestedFluidIngredient.add(patternSlot, ingredient.getFluid(), requested);
            requestedSets = Math.min(requestedSets, requested / ingredient.getAmount());
        }
        remainingSets -= requestedSets;
        return requestedSets;
    }

    /**
     * Releases provider reservations still owned by this staged order.
     */
    void releaseReservations() {
        for (PatternCraftingBranch branch : ingredientBranches) {
            branch.releaseProviderPromises();
        }
    }

    /**
     * Appends this staged order and its ingredient branches to the crafting request debug dump.
     */
    void appendDebugState(StringBuilder out, String prefix) {
        out.append(prefix)
                .append("- Pattern slot ")
                .append(patternSlot)
                .append(" remainingSets=")
                .append(remainingSets)
                .append(" resultAmountPerSet=")
                .append(resultAmountPerSet)
                .append(" outputOrder=")
                .append(outputOrder == null ? "<none>" : outputOrder.getAsDisplayItem())
                .append(" branches=")
                .append(ingredientBranches.size())
                .append("\n");
        for (PatternCraftingBranch branch : ingredientBranches) {
            branch.appendDebugState(out, prefix + "  ");
        }
    }

    /**
     * Builds a renderer node whose count follows the live output order amount.
     */
    PatternCraftingMonitorNode toMonitorNode(Set<PatternCraftingOrder> visitedOrders) {
        visitedOrders.add(this);
        ItemIdentifierStack display = outputOrder.getAsDisplayItem().clone();
        display.setStackSize(Math.max(0, display.getStackSize()));
        PatternCraftingMonitorNode node = new PatternCraftingMonitorNode(
                display,
                0,
                display.getStackSize(),
                outputOrder != null && (outputOrder.isInProgress() || !outputOrder.getProgresses().isEmpty()));
        for (PatternCraftingBranch branch : ingredientBranches) {
            node.addChild(branch.toMonitorNode(visitedOrders));
        }
        return node;
    }

    /**
     * Returns the amount still available for one ingredient across matching branches.
     */
    private int availableFromBranches(ItemIdentifier item) {
        int available = 0;
        for (PatternCraftingBranch branch : ingredientBranches) {
            if (branch.matches(item)) {
                available += branch.getRemainingAmount();
            }
        }
        return available;
    }

    /**
     * Returns the amount still available for one fluid ingredient across matching branches.
     */
    private int availableFromBranches(FluidIdentifier fluid) {
        int available = 0;
        for (PatternCraftingBranch branch : ingredientBranches) {
            if (branch.matches(fluid)) {
                available += branch.getRemainingAmount();
            }
        }
        return available;
    }

    /**
     * Places provider or staged crafting orders for an ingredient, consuming the matching branch state as it goes.
     */
    private int requestFromBranches(ItemIdentifier item, int amount) {
        int requested = 0;
        for (PatternCraftingBranch branch : ingredientBranches) {
            if (requested >= amount) {
                break;
            }
            if (!branch.matches(item)) {
                continue;
            }
            requested += branch.request(amount - requested);
        }
        return requested;
    }

    /**
     * Places provider orders for a fluid ingredient, consuming the matching branch state as it goes.
     */
    private int requestFromBranches(FluidIdentifier fluid, int amount) {
        int requested = 0;
        for (PatternCraftingBranch branch : ingredientBranches) {
            if (requested >= amount) {
                break;
            }
            if (!branch.matches(fluid)) {
                continue;
            }
            requested += branch.request(amount - requested);
        }
        return requested;
    }
}
