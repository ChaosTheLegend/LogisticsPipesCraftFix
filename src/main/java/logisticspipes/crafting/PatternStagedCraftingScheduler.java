package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;

import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.CacheHolder.CacheTypes;

/**
 * Decides when staged pattern crafting orders may request their next ingredient sets.
 * <p>
 * The scheduler balances three constraints before it asks a {@link PatternCraftingOrder} to consume branch state:
 * remaining output work, branch ingredient availability, and local capacity in the pipe buffer or adjacent target.
 */
class PatternStagedCraftingScheduler {

    private final ModuleItemCrafting module;
    private final PipeItemsPatternCraftingLogistics pipe;
    private final AdjacentInventoryHandler adjacentInventory;
    private final PatternStackRequestHandler requestedIngredient;
    private final List<PatternCraftingOrder> stagedCrafts;
    private final Set<Integer> requestingPatterns = new HashSet<>();

    PatternStagedCraftingScheduler(ModuleItemCrafting module, PipeItemsPatternCraftingLogistics pipe,
            AdjacentInventoryHandler adjacentInventory, PatternStackRequestHandler requestedIngredient,
            List<PatternCraftingOrder> stagedCrafts) {
        this.module = module;
        this.pipe = pipe;
        this.adjacentInventory = adjacentInventory;
        this.requestedIngredient = requestedIngredient;
        this.stagedCrafts = stagedCrafts;
    }

    /**
     * Requests ingredients for every staged order that still has room in the module or adjacent inventory.
     */
    void requestIngredients() {
        for (PatternCraftingOrder order : new ArrayList<>(stagedCrafts)) {
            requestIngredients(order.patternSlot);
        }
    }

    /**
     * Requests ingredients for one pattern slot.
     * <p>
     * The per-pattern guard allows different patterns in the same module to stage work independently while preventing
     * recursive requests for the same pattern from re-entering through branch fulfillment.
     */
    void requestIngredients(int patternSlot) {
        if (!requestingPatterns.add(patternSlot)) {
            module.debug("request ingredients slot=%d skipped: already requesting", patternSlot);
            return;
        }
        try {
            requestIngredientsGuarded(patternSlot);
        } finally {
            requestingPatterns.remove(patternSlot);
        }
    }

    private void requestIngredientsGuarded(int patternSlot) {
        module.debug("request ingredients slot=%d start stagedCrafts=%d", patternSlot, stagedCrafts.size());

        for (PatternCraftingOrder order : new ArrayList<>(stagedCrafts)) {
            if (removeFinishedOrder(order)) {
                continue;
            }
            if (order.patternSlot != patternSlot) {
                continue;
            }

            ItemStack pattern = module.getPatternStack(order.patternSlot);
            if (removeOrderWithoutPattern(order, pattern) || removeFullyRequestedOrder(order)) {
                continue;
            }
            if (isBlockedByAnotherRunningCraft(order)) {
                continue;
            }

            requestOrderIngredients(order, pattern);
        }
    }

    private boolean removeFinishedOrder(PatternCraftingOrder order) {
        if (!order.outputOrder.isFinished()) {
            return false;
        }
        module.debug(
                "request ingredients slot=%d removing staged order: the order output is already satisfied",
                order.patternSlot);
        stagedCrafts.remove(order);
        return true;
    }

    private boolean removeOrderWithoutPattern(PatternCraftingOrder order, ItemStack pattern) {
        if (pattern != null) {
            return false;
        }
        module.debug("request ingredients slot=%d removing staged order: pattern missing", order.patternSlot);
        order.releaseReservations();
        stagedCrafts.remove(order);
        return true;
    }

    private boolean removeFullyRequestedOrder(PatternCraftingOrder order) {
        if (!order.isFullyRequested()) {
            return false;
        }
        module.debug("request ingredients slot=%d removing staged order: fully requested", order.patternSlot);
        order.releaseReservations();
        stagedCrafts.remove(order);
        return true;
    }

    private boolean isBlockedByAnotherRunningCraft(PatternCraftingOrder order) {
        PipeItemsPatternCraftingLogistics.BlockingMode mode = module.getEffectiveBlockingMode();
        if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.OFF || !module.isRunningCraftLocked()) {
            return false;
        }
        int runningCraft = module.getRunningCraftForHandler();
        if (runningCraft == order.patternSlot) {
            return false;
        }
        module.debug(
                "request ingredients slot=%d skipped: running craft locked by slot=%d",
                order.patternSlot,
                runningCraft);
        return true;
    }

    private void requestOrderIngredients(PatternCraftingOrder order, ItemStack pattern) {
        int orderableSets = orderableSetsForPattern(order.patternSlot, pattern);
        int branchSets = order.availableSetsFromBranches(pattern);
        int sets = Math.min(order.remainingSets, orderableSets);
        sets = Math.min(sets, branchSets);
        module.debugEvent(
                "REQUEST",
                "request ingredients slot=%d remainingSets=%d orderableSets=%d branchSets=%d selectedSets=%d",
                order.patternSlot,
                order.remainingSets,
                orderableSets,
                branchSets,
                sets);
        if (sets <= 0) {
            return;
        }

        int requestedSets = order.requestIngredients(pattern, sets);
        if (requestedSets <= 0) {
            module.debug("request ingredients slot=%d requested no sets", order.patternSlot);
            return;
        }

        module.debugEvent(
                "REQUEST",
                "request ingredients slot=%d requestedSets=%d remainingSets=%d",
                order.patternSlot,
                requestedSets,
                order.remainingSets);
        pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        if (order.isFullyRequested()) {
            module.debugEvent(
                    "REQUEST",
                    "request ingredients slot=%d completed staged order after request",
                    order.patternSlot);
            order.releaseReservations();
            stagedCrafts.remove(order);
        }
    }

    /**
     * Calculates how many complete pattern sets can be ordered now without overcommitting the module buffer or adjacent
     * inventory. Requested but not-yet-arrived ingredients are subtracted so repeated recalculation only orders newly
     * freed capacity.
     */
    private int orderableSetsForPattern(int patternSlot, ItemStack pattern) {
        if (!module.canReceiveForPattern(patternSlot)) {
            module.debug("orderable sets slot=%d result=0 cannot receive", patternSlot);
            return 0;
        }
        AdjacentTile connected = module.getConnectedInventoryTile();
        int sets = Integer.MAX_VALUE;
        PipeItemsPatternCraftingLogistics.BlockingMode mode = module.getEffectiveBlockingMode();
        for (IPatternStack ingredient : module.getLocalAggregatedIngredients(pattern)) {
            int room = module.spaceForPatternIngredient(patternSlot, pattern, ingredient);
            if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING && connected != null
                    && adjacentInventory.isEmpty(connected)) {
                room += ingredient.getAmount();
            }
            room -= requestedIngredient.amount(patternSlot, ingredient);
            module.debug(
                    "orderable ingredient slot=%d ingredient=%s roomAfterRequested=%d amountPerSet=%d",
                    patternSlot,
                    ingredient,
                    room,
                    ingredient.getAmount());
            sets = Math.min(sets, Math.max(0, room) / ingredient.getAmount());
        }
        int result = sets == Integer.MAX_VALUE ? 0 : Math.max(0, sets);
        module.debug("orderable sets slot=%d result=%d", patternSlot, result);
        return result;
    }
}
