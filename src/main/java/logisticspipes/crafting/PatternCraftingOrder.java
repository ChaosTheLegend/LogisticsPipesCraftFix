package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;

import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

class PatternCraftingOrder {

    final int patternSlot;
    final int resultAmountPerSet;
    final List<PatternCraftingBranch> ingredientBranches;
    int remainingSets;

    final IOrderInfoProvider outputOrder;
    private final ModuleItemCrafting module;
    private final PatternHandler patternHandler;
    private final PatternStackRequestHandler requestedIngredient;

    PatternCraftingOrder(int patternSlot, int resultAmountPerSet, PatternCraftingBranch branch,
            IOrderInfoProvider outputOrder, ModuleItemCrafting module, PatternHandler patternHandler,
            PatternStackRequestHandler requestedIngredient) {
        this.patternSlot = patternSlot;
        this.resultAmountPerSet = Math.max(1, resultAmountPerSet);
        this.ingredientBranches = new ArrayList<>(branch.getSubRequests());
        this.outputOrder = outputOrder;
        this.module = module;
        this.patternHandler = patternHandler;
        this.requestedIngredient = requestedIngredient;
        this.remainingSets = initialRemainingSets(branch);
    }

    PatternCraftingOrder(int patternSlot, int resultAmountPerSet, int remainingSets,
            List<PatternCraftingBranch> ingredientBranches, IOrderInfoProvider outputOrder,
            ModuleItemCrafting module, PatternHandler patternHandler, PatternStackRequestHandler requestedIngredient) {
        this.patternSlot = patternSlot;
        this.resultAmountPerSet = Math.max(1, resultAmountPerSet);
        this.ingredientBranches = new ArrayList<>(ingredientBranches);
        this.outputOrder = outputOrder;
        this.module = module;
        this.patternHandler = patternHandler;
        this.requestedIngredient = requestedIngredient;
        this.remainingSets = capRemainingSets(Math.max(0, remainingSets));
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
     * Counts the recipe sets that still need ingredient requests for this staged slice.
     * <p>
     * Output orders may be split at amounts that are not recipe-set aligned. The extra items produced by an earlier
     * slice remain in the adjacent inventory and can satisfy the next output order without another ingredient set, so
     * the staged ingredient work is capped by the branch capacity that was allocated to this slice.
     */
    private int initialRemainingSets(PatternCraftingBranch branch) {
        int outputSets = (branch.getRequestType().getRequestedAmount() + resultAmountPerSet - 1) / resultAmountPerSet;
        return capRemainingSets(outputSets);
    }

    private int capRemainingSets(int sets) {
        ItemStack pattern = module.getPatternStack(patternSlot);
        if (pattern == null) {
            return sets;
        }
        return Math.min(sets, availableSetsFromBranches(pattern));
    }

    /**
     * Calculates how many pattern sets can still be produced from the remaining request-tree branches.
     */
    int availableSetsFromBranches(ItemStack pattern) {
        int sets = Integer.MAX_VALUE;
        for (IPatternStack ingredient : patternHandler.getAggregatedInputs(pattern)) {
            int available = availableFromBranches(ingredient);
            module.debug(
                    "branch availability slot=%d ingredient=%s available=%d amountPerSet=%d",
                    patternSlot,
                    ingredient,
                    available,
                    ingredient.getAmount());
            sets = Math.min(sets, available / ingredient.getAmount());
        }
        int result = sets == Integer.MAX_VALUE ? 0 : Math.max(0, sets);
        module.debug("branch availability slot=%d sets=%d", patternSlot, result);
        return result;
    }

    /**
     * Requests ingredients for up to {@code sets} pattern sets and records those in-flight ingredients as reserved
     * module buffer space.
     */
    int requestIngredients(ItemStack pattern, int sets) {
        int requestedSets = sets;
        List<RequestedIngredient> requestedIngredients = new ArrayList<>();
        module.debugEvent(
                "REQUEST",
                "order request ingredients slot=%d requestedSetsStart=%d remainingSets=%d",
                patternSlot,
                sets,
                remainingSets);
        for (PatternIngredientTarget ingredient : module.getIngredientTargets(pattern)) {
            int requested = requestFromBranches(
                    ingredient.stack,
                    ingredient.stack.getAmount() * requestedSets,
                    ingredient.target);
            requestedIngredients.add(new RequestedIngredient(ingredient, requested));
            module.debugEvent(
                    "REQUEST",
                    "order requested ingredient slot=%d ingredient=%s target=%s requested=%d amountPerSet=%d",
                    patternSlot,
                    ingredient.stack,
                    ingredient.target,
                    requested,
                    ingredient.stack.getAmount());
            requestedSets = Math.min(requestedSets, requested / ingredient.stack.getAmount());
        }
        for (RequestedIngredient requested : requestedIngredients) {
            if (requested.ingredient.target == null) {
                int reserved = Math.min(
                        requested.amount,
                        requested.ingredient.stack.getAmount() * requestedSets);
                if (reserved <= 0) {
                    continue;
                }
                requestedIngredient.add(
                        patternSlot,
                        PatternStackHelper.copyWithAmount(requested.ingredient.stack, reserved));
                module.debugEvent(
                        "BUFFER",
                        "order reserved local requested ingredient slot=%d ingredient=%s requested=%d",
                        patternSlot,
                        requested.ingredient.stack,
                        reserved);
            }
        }
        remainingSets -= requestedSets;
        module.debug(
                "order request ingredients slot=%d requestedSetsFinal=%d remainingSets=%d",
                patternSlot,
                requestedSets,
                remainingSets);
        return requestedSets;
    }

    /**
     * Releases provider reservations still owned by this staged order.
     */
    void releaseReservations() {
        module.debug("order release reservations slot=%d branches=%d", patternSlot, ingredientBranches.size());
        for (PatternCraftingBranch branch : ingredientBranches) {
            branch.releaseProviderPromises();
        }
    }

    /**
     * Appends this staged order and its ingredient branches to the crafting request debug dump.
     */
    void appendDebugState(StringBuilder out, String prefix) {
        out.append(prefix).append("- Pattern slot ").append(patternSlot).append(" remainingSets=").append(remainingSets)
                .append(" resultAmountPerSet=").append(resultAmountPerSet).append(" outputOrder=")
                .append(outputOrder == null ? "<none>" : outputOrder.getAsDisplayItem()).append(" branches=")
                .append(ingredientBranches.size()).append("\n");
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
    private int availableFromBranches(IPatternStack ingredient) {
        int available = 0;
        for (PatternCraftingBranch branch : ingredientBranches) {
            if (branchMatches(branch, ingredient)) {
                available += branch.getRemainingAmount();
            }
        }
        return available;
    }

    /**
     * Places provider or staged crafting orders for an ingredient, consuming the matching branch state as it goes.
     */
    private int requestFromBranches(IPatternStack ingredient, int amount, IRequestItems targetOverride) {
        int requested = 0;
        for (PatternCraftingBranch branch : ingredientBranches) {
            if (requested >= amount) {
                break;
            }
            if (!branchMatches(branch, ingredient)) {
                continue;
            }
            if (PatternStackHelper.isFluid(ingredient)) {
                int branchRequested = branch.request(amount - requested);
                requested += branchRequested;
                module.debugEvent(
                        "REQUEST",
                        "branch fluid request slot=%d ingredient=%s requested=%d total=%d/%d",
                        patternSlot,
                        ingredient,
                        branchRequested,
                        requested,
                        amount);
            } else {
                int branchRequested = branch.request(
                        amount - requested,
                        targetOverride,
                        targetOverride == null ? new PatternTargetInformation(patternSlot) : null);
                requested += branchRequested;
                module.debugEvent(
                        "REQUEST",
                        "branch item request slot=%d ingredient=%s target=%s requested=%d total=%d/%d",
                        patternSlot,
                        ingredient,
                        targetOverride,
                        branchRequested,
                        requested,
                        amount);
            }
        }
        return requested;
    }

    /**
     * Checks whether a staged branch can provide the requested item or fluid ingredient.
     */
    private boolean branchMatches(PatternCraftingBranch branch, IPatternStack ingredient) {
        FluidIdentifier fluid = PatternStackHelper.asFluid(ingredient);
        if (fluid != null) {
            return branch.matches(fluid);
        }
        ItemIdentifier item = PatternStackHelper.getRoutingItem(ingredient);
        return item != null && branch.matches(item);
    }

    private static class RequestedIngredient {

        private final PatternIngredientTarget ingredient;
        private final int amount;

        private RequestedIngredient(PatternIngredientTarget ingredient, int amount) {
            this.ingredient = ingredient;
            this.amount = amount;
        }
    }
}
