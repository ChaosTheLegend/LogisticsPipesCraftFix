package logisticspipes.crafting;

import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.routing.order.LogisticsFluidOrder;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.CacheHolder.CacheTypes;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides when staged pattern crafting orders may request their next ingredient sets.
 * <p>
 * The scheduler balances three constraints before it asks a {@link PatternCraftingOrder} to consume branch state:
 * remaining output work, branch ingredient availability, and local capacity in the pipe buffer or adjacent target.
 */
class PatternStagedCraftingScheduler {

    private final ModulePatternCrafting module;
    private final PipeItemsPatternCraftingLogistics pipe;
    private final AdjacentInventoryHandler adjacentInventory;
    private final List<PatternCraftingOrder> stagedCrafts;
    private final Set<Integer> requestingPatterns = new HashSet<>();

    PatternStagedCraftingScheduler(ModulePatternCrafting module, PipeItemsPatternCraftingLogistics pipe,
                                   AdjacentInventoryHandler adjacentInventory, List<PatternCraftingOrder> stagedCrafts) {
        this.module = module;
        this.pipe = pipe;
        this.adjacentInventory = adjacentInventory;
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
            module.debugEventThrottled("SCHED", "request ingredients slot=%d skipped: already requesting", patternSlot);
            return;
        }
        try {
            requestIngredientsGuarded(patternSlot);
        } finally {
            requestingPatterns.remove(patternSlot);
        }
    }

    private void requestIngredientsGuarded(int patternSlot) {
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
        if (!order.isFullyRequested() && isSamePipeOutput(order)) {
            module.debugEventThrottled(
                    "SCHED",
                    60,
                    "request ingredients slot=%d kept finished same-pipe staged order until ingredients requested remainingSets=%d",
                    order.patternSlot,
                    order.remainingSets);
            return false;
        }
        module.debugEvent(
                "SCHED",
                "request ingredients slot=%d removing staged order: the order output is already satisfied remainingSets=%d",
                order.patternSlot,
                order.remainingSets);
        order.releaseReservations();
        stagedCrafts.remove(order);
        module.markHudStateDirty();
        return true;
    }

    private boolean isSamePipeOutput(PatternCraftingOrder order) {
        if (order.outputOrder instanceof LogisticsItemOrder itemOrder) {
            return module.isOrderDestinationThisModule(itemOrder)
                    && itemOrder.getInformation() instanceof PatternTargetInformation;
        }
        if (order.outputOrder instanceof LogisticsFluidOrder fluidOrder) {
            return module.isOrderDestinationThisModule(fluidOrder)
                    && fluidOrder.getInformation() instanceof PatternTargetInformation;
        }
        return false;
    }

    private boolean removeOrderWithoutPattern(PatternCraftingOrder order, ItemStack pattern) {
        if (pattern != null) {
            return false;
        }
        module.debugEvent(
            "SCHED",
            "request ingredients slot=%d removing staged order: pattern missing",
            order.patternSlot);
        order.releaseReservations();
        stagedCrafts.remove(order);
        module.markHudStateDirty();
        return true;
    }

    private boolean removeFullyRequestedOrder(PatternCraftingOrder order) {
        if (!order.isFullyRequested()) {
            return false;
        }
        module.debugEvent(
                "SCHED",
                "request ingredients slot=%d removing staged order: fully requested remainingSets=%d",
                order.patternSlot,
                order.remainingSets);
        order.releaseReservations();
        stagedCrafts.remove(order);
        module.markHudStateDirty();
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
        module.debugEventThrottled(
                "SCHED",
                100,
                "request ingredients slot=%d skipped: running craft locked by slot=%d",
                order.patternSlot,
                runningCraft);
        return true;
    }

    private void requestOrderIngredients(PatternCraftingOrder order, ItemStack pattern) {
        int orderableSets = orderableSetsForPattern(order, pattern);
        if (usesSatelliteBlocking(pattern)) {
            if (order.hasSatelliteBlockingBatchInProgress()) {
                module.debugEventThrottled(
                    "SCHED",
                    60,
                    "request ingredients slot=%d paused: satellite blocking batch still running remainingSets=%d orderableSets=%d",
                    order.patternSlot,
                    order.remainingSets,
                    orderableSets);
                return;
            }
            if (order.hasBlockingDependencyInFlight()) {
                module.debugEventThrottled(
                    "SCHED",
                    60,
                    "request ingredients slot=%d paused: blocking satellite dependency in flight remainingSets=%d orderableSets=%d",
                    order.patternSlot,
                    order.remainingSets,
                    orderableSets);
                return;
            }
            if (orderableSets > 0 && order.requestBlockingPrerequisite(pattern)) {
                pipe.getCacheHolder().trigger(CacheTypes.Inventory);
                module.markHudStateDirty();
                return;
            }
        }
        int branchSets = order.availableSetsFromBranches(pattern);
        int sets = Math.min(order.remainingSets, orderableSets);
        sets = Math.min(sets, branchSets);
        if (sets <= 0) {
            module.debugEventThrottled(
                    "SCHED",
                    100,
                    "request ingredients slot=%d paused: no selectable sets remainingSets=%d orderableSets=%d branchSets=%d",
                    order.patternSlot,
                    order.remainingSets,
                    orderableSets,
                    branchSets);
            return;
        }
        module.debugEvent(
                "SCHED",
                "request ingredients slot=%d remainingSets=%d orderableSets=%d branchSets=%d selectedSets=%d",
                order.patternSlot,
                order.remainingSets,
                orderableSets,
                branchSets,
                sets);

        int requestedSets = order.requestIngredients(pattern, sets);
        if (requestedSets <= 0) {
            module.debugEventThrottled(
                    "SCHED",
                    "request ingredients slot=%d requested no sets selectedSets=%d",
                    order.patternSlot,
                    sets);
            return;
        }

        module.debugEvent(
                "SCHED",
                "request ingredients slot=%d requestedSets=%d remainingSets=%d",
                order.patternSlot,
                requestedSets,
                order.remainingSets);
        if (usesSatelliteBlocking(pattern)) {
            order.markSatelliteBlockingBatchStarted();
        }
        pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        module.markHudStateDirty();
        if (order.isFullyRequested()) {
            module.debugEvent(
                    "REQUEST",
                    "request ingredients slot=%d completed staged order after request",
                    order.patternSlot);
            order.releaseReservations();
            stagedCrafts.remove(order);
            module.markHudStateDirty();
        }
    }

    /**
     * Calculates how many complete pattern sets can be ordered now without overcommitting the module buffer or adjacent
     * inventory. Requested but not-yet-arrived ingredients are subtracted so repeated recalculation only orders newly
     * freed capacity.
     */
    private int orderableSetsForPattern(PatternCraftingOrder order, ItemStack pattern) {
        if (!module.canReceiveForPattern(order.patternSlot)) {
            module.debugEventThrottled("SCHED", "orderable sets slot=%d result=0 cannot receive", order.patternSlot);
            return 0;
        }
        List<IPatternStack> localIngredients = module.getLocalAggregatedIngredients(pattern);
        if (localIngredients.isEmpty()) {
            return capOrderableSetsForBlocking(pattern, orderableSetsWithoutLocalBuffer(order, pattern));
        }
        AdjacentTile connected = module.getConnectedInventoryTile();
        int sets = Integer.MAX_VALUE;
        PipeItemsPatternCraftingLogistics.BlockingMode mode = module.getEffectiveBlockingMode();
        for (IPatternStack ingredient : localIngredients) {
            int room = module.spaceForPatternIngredient(order.patternSlot, pattern, ingredient);
            if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING && connected != null
                    && adjacentInventory.isEmpty(connected)) {
                room += ingredient.getAmount();
            }
            room -= module.requestedIngredientAmount(order.patternSlot, pattern, ingredient);
            sets = Math.min(sets, Math.max(0, room) / ingredient.getAmount());
        }
        return capOrderableSetsForBlocking(pattern, sets == Integer.MAX_VALUE ? 0 : Math.max(0, sets));
    }

    /**
     * Allows staged orders whose ingredients are routed away from this pipe to progress.
     * <p>
     * Satellite-only patterns do not reserve local buffer space, so the normal local-capacity calculation has no
     * ingredient to measure and would otherwise return zero forever.
     */
    private int orderableSetsWithoutLocalBuffer(PatternCraftingOrder order, ItemStack pattern) {
        if (!hasAnyIngredientTarget(pattern)) {
            return 0;
        }
        module.debugEventThrottled(
            "SCHED",
            100,
            "orderable sets slot=%d localBuffer=none allowing remainingSets=%d",
            order.patternSlot,
            order.remainingSets);
        return Math.max(0, order.remainingSets);
    }

    private int capOrderableSetsForBlocking(ItemStack pattern, int sets) {
        if (!usesSatelliteBlocking(pattern)) {
            return sets;
        }
        return Math.min(sets, 1);
    }

    private boolean usesSatelliteBlocking(ItemStack pattern) {
        return module.getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING
            && hasSatelliteTarget(pattern);
    }

    /**
     * Checks whether a pattern still has ingredient work even though none of it is local to this pipe.
     */
    private boolean hasAnyIngredientTarget(ItemStack pattern) {
        for (PatternIngredientTarget ingredient : module.getIngredientTargets(pattern)) {
            if (ingredient.stack() != null && ingredient.stack().getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSatelliteTarget(ItemStack pattern) {
        for (PatternIngredientTarget ingredient : module.getIngredientTargets(pattern)) {
            if (ingredient.stack() != null && ingredient.stack().getAmount() > 0 && !ingredient.isLocal()) {
                return true;
            }
        }
        return false;
    }
}
