package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.request.IPromise;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider;

/**
 * Owns the lifecycle of staged pattern crafting output orders.
 * <p>
 * The coordinator is the first stop after the request tree decides that this pipe should craft something. It validates
 * that the request has a real destination, creates the live output order, records the branch state required to request
 * ingredients later, and asks the scheduler to request any ingredient sets that can fit immediately.
 */
class PatternStagedCraftingCoordinator {

    private final ModuleItemCrafting module;
    private final PatternHandler patternHandler;
    private final PatternStackRequestHandler requestedIngredient;
    private final List<PatternCraftingOrder> stagedCrafts = new ArrayList<>();
    private final PatternStagedCraftingScheduler scheduler;

    PatternStagedCraftingCoordinator(ModuleItemCrafting module, PipeItemsPatternCraftingLogistics pipe,
            PatternHandler patternHandler, PatternStackRequestHandler requestedIngredient,
            AdjacentInventoryHandler adjacentInventory) {
        this.module = module;
        this.patternHandler = patternHandler;
        this.requestedIngredient = requestedIngredient;
        this.scheduler = new PatternStagedCraftingScheduler(
                module,
                pipe,
                adjacentInventory,
                requestedIngredient,
                stagedCrafts);
    }

    IOrderInfoProvider fulfill(IPromise promise, IResource requestType, IAdditionalTargetInformation info,
            PatternCraftingBranch branch) {
        if (!hasRequestTarget(promise, requestType)) {
            module.debugEvent(
                    "STAGED",
                    "staged craft rejected without target promise=%s request=%s info=%s",
                    promise,
                    requestType,
                    info);
            return null;
        }

        module.debugEvent(
                "STAGED",
                "staged craft start promise=%s amount=%d request=%s info=%s branch=%s",
                promise.getItemType(),
                promise.getAmount(),
                requestType,
                info,
                branch == null ? "<none>" : "available");

        IOrderInfoProvider order = promise.fullFill(requestType, info);
        int patternSlot = resolvePatternSlot(promise);
        int resultAmountPerSet = resolveResultAmountPerSet(promise, patternSlot);

        module.debug(
                "staged craft output order=%s patternSlot=%d resultAmountPerSet=%d",
                order == null ? "<none>" : order.getAsDisplayItem(),
                patternSlot,
                resultAmountPerSet);

        if (patternSlot >= 0 && branch != null && order != null) {
            registerOrder(patternSlot, resultAmountPerSet, branch, order);
        }
        return order;
    }

    void requestIngredients() {
        scheduler.requestIngredients();
    }

    int remainingSets(int patternSlot) {
        int sets = 0;
        for (PatternCraftingOrder order : stagedCrafts) {
            if (order.patternSlot == patternSlot && !order.outputOrder.isFinished()) {
                sets += Math.max(0, order.remainingSets);
            }
        }
        return sets;
    }

    void appendDebugState(StringBuilder out, String prefix) {
        if (stagedCrafts.isEmpty()) {
            out.append(prefix).append("<none>\n");
            return;
        }
        for (PatternCraftingOrder order : stagedCrafts) {
            order.appendDebugState(out, prefix);
        }
    }

    void releaseAll() {
        for (PatternCraftingOrder order : stagedCrafts) {
            module.debug(
                    "removal releases staged order slot=%d remainingSets=%d",
                    order.patternSlot,
                    order.remainingSets);
            order.releaseReservations();
        }
        stagedCrafts.clear();
    }

    private void registerOrder(int patternSlot, int resultAmountPerSet, PatternCraftingBranch branch,
            IOrderInfoProvider order) {
        PatternCraftingOrder stagedOrder = new PatternCraftingOrder(
                patternSlot,
                resultAmountPerSet,
                branch,
                order,
                module,
                patternHandler,
                requestedIngredient);
        stagedCrafts.add(stagedOrder);
        PatternCraftingMonitorRegistry.register(order, stagedOrder);
        module.debugEvent(
                "STAGED",
                "staged craft registered slot=%d remainingSets=%d ingredientBranches=%d",
                patternSlot,
                stagedOrder.remainingSets,
                stagedOrder.ingredientBranches.size());
        scheduler.requestIngredients(patternSlot);
    }

    private boolean hasRequestTarget(IPromise promise, IResource requestType) {
        if (promise instanceof FluidLogisticsPromise) {
            return requestType instanceof FluidResource && ((FluidResource) requestType).getTarget() != null;
        }
        return getRequestTarget(requestType) != null;
    }

    private IRequestItems getRequestTarget(IResource requestType) {
        if (requestType instanceof ItemResource) {
            return ((ItemResource) requestType).getTarget();
        }
        if (requestType instanceof DictResource) {
            return ((DictResource) requestType).getTarget();
        }
        return null;
    }

    private int resolvePatternSlot(IPromise promise) {
        if (promise instanceof PatternCraftingPromise) {
            return ((PatternCraftingPromise) promise).getPatternSlot();
        }
        if (promise instanceof PatternFluidCraftingPromise) {
            return ((PatternFluidCraftingPromise) promise).getPatternSlot();
        }
        return patternHandler.findPatternSlotForResult(promise.getItemType());
    }

    private int resolveResultAmountPerSet(IPromise promise, int patternSlot) {
        if (promise instanceof PatternCraftingPromise) {
            return ((PatternCraftingPromise) promise).getResultAmountPerSet();
        }
        if (promise instanceof PatternFluidCraftingPromise) {
            return ((PatternFluidCraftingPromise) promise).getResultAmountPerSet();
        }
        return Math.max(1, patternHandler.resultAmount(patternSlot, promise.getItemType()));
    }
}
