package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.routing.order.LogisticsFluidOrder;
import logisticspipes.routing.order.LogisticsItemOrder;

/**
 * Resolves a user cancellation request to the staged request tree it belongs to.
 * <p>
 * The pipe UI can only address a pattern slot, but a staged craft can contain several nested pattern slots. Cancelling
 * a middle node would leave its parent waiting for ingredients that can no longer arrive, so this resolver expands the
 * selected slot to the owning parent chain first and then includes every descendant staged order.
 */
final class PatternCraftingCancellationResolver {

    /**
     * Builds the complete cancellation group for a pattern slot.
     * <p>
     * Live branch order references are used when available. Saved {@link PatternTargetInformation} is used as a
     * fallback so restored crafts can still be cancelled as a coherent tree after a world restart.
     */
    List<PatternCraftingOrder> resolve(int patternSlot, List<PatternCraftingOrder> outputOrders) {
        Set<PatternCraftingOrder> group = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PatternCraftingOrder order : outputOrders) {
            if (isActiveOrder(order) && order.patternSlot == patternSlot) {
                group.add(order);
            }
        }
        if (group.isEmpty()) {
            return Collections.emptyList();
        }

        expandToAncestors(group, outputOrders);
        expandToDescendants(group, outputOrders);
        return orderLike(outputOrders, group);
    }

    private void expandToAncestors(Set<PatternCraftingOrder> group, List<PatternCraftingOrder> outputOrders) {
        boolean added;
        do {
            added = false;
            for (PatternCraftingOrder candidate : outputOrders) {
                if (!isActiveOrder(candidate) || group.contains(candidate)) {
                    continue;
                }
                if (isParentOfAny(candidate, group)) {
                    group.add(candidate);
                    added = true;
                }
            }
        } while (added);
    }

    private void expandToDescendants(Set<PatternCraftingOrder> group, List<PatternCraftingOrder> outputOrders) {
        boolean added;
        do {
            added = false;
            for (PatternCraftingOrder candidate : outputOrders) {
                if (!isActiveOrder(candidate) || group.contains(candidate)) {
                    continue;
                }
                if (isChildOfAny(candidate, group)) {
                    group.add(candidate);
                    added = true;
                }
            }
        } while (added);
    }

    private boolean isParentOfAny(PatternCraftingOrder candidate, Set<PatternCraftingOrder> possibleChildren) {
        for (PatternCraftingOrder child : possibleChildren) {
            if (isParentOf(candidate, child)) {
                return true;
            }
        }
        return false;
    }

    private boolean isChildOfAny(PatternCraftingOrder candidate, Set<PatternCraftingOrder> possibleParents) {
        for (PatternCraftingOrder parent : possibleParents) {
            if (isParentOf(parent, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean isParentOf(PatternCraftingOrder parent, PatternCraftingOrder child) {
        if (parent == child) {
            return false;
        }
        if (parent.getChildStagedOrders().contains(child)) {
            return true;
        }
        PatternTargetInformation target = getPatternTarget(child);
        return target != null && parent.patternSlot == target.patternSlot();
    }

    private PatternTargetInformation getPatternTarget(PatternCraftingOrder order) {
        if (order.outputOrder instanceof LogisticsItemOrder) {
            IAdditionalTargetInformation info = ((LogisticsItemOrder) order.outputOrder).getInformation();
            return info instanceof PatternTargetInformation ? (PatternTargetInformation) info : null;
        }
        if (order.outputOrder instanceof LogisticsFluidOrder) {
            IAdditionalTargetInformation info = ((LogisticsFluidOrder) order.outputOrder).getInformation();
            return info instanceof PatternTargetInformation ? (PatternTargetInformation) info : null;
        }
        return null;
    }

    private List<PatternCraftingOrder> orderLike(List<PatternCraftingOrder> outputOrders,
            Set<PatternCraftingOrder> group) {
        List<PatternCraftingOrder> result = new ArrayList<>();
        for (PatternCraftingOrder order : outputOrders) {
            if (group.contains(order)) {
                result.add(order);
            }
        }
        return result;
    }

    private boolean isActiveOrder(PatternCraftingOrder order) {
        return order != null && order.outputOrder != null && !order.outputOrder.isFinished();
    }
}
