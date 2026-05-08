package logisticspipes.crafting;

import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

class PatternCraftingOrder {

    final int patternSlot;
    final int resultAmountPerSet;
    final List<PatternCraftingBranch> ingredientBranches;
    int remainingSets;

    private final PatternHandler patternHandler;
    private final IngredientRequestHandler requestedIngredient;

    PatternCraftingOrder(
            int patternSlot,
            int resultAmountPerSet,
            PatternCraftingBranch branch,
            PatternHandler patternHandler,
            IngredientRequestHandler requestedIngredient) {
        this.patternSlot = patternSlot;
        this.resultAmountPerSet = Math.max(1, resultAmountPerSet);
        this.remainingSets = (branch.getRequestType().getRequestedAmount() + this.resultAmountPerSet - 1)
                / this.resultAmountPerSet;
        this.ingredientBranches = new ArrayList<>(branch.getSubRequests());
        this.patternHandler = patternHandler;
        this.requestedIngredient = requestedIngredient;
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
        return sets == Integer.MAX_VALUE ? 0 : Math.max(0, sets);
    }

    /**
     * Requests ingredients for up to {@code sets} pattern sets and records those in-flight ingredients as reserved
     * module buffer space.
     */
    int requestIngredients(ItemStack pattern, int sets) {
        int requestedSets = sets;
        for (ItemIdentifierStack ingredient : patternHandler.getAggregatedIngredients(pattern)) {
            int requested = requestFromBranches(ingredient.getItem(), ingredient.getStackSize() * sets);
            requestedIngredient.add(patternSlot, ingredient.getItem(), requested);
            requestedSets = Math.min(requestedSets, requested / ingredient.getStackSize());
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
}
