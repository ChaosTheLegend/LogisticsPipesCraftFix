package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.request.IExtraPromise;
import logisticspipes.request.IPromise;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.utils.item.ItemIdentifier;

public class PatternCraftingBranch {

    private final IResource requestType;
    private final IAdditionalTargetInformation info;
    private final int originalAmount;
    private int remainingAmount;
    private final int originalCraftingAmount;
    private int remainingCraftingAmount;
    private final List<PromiseState> promises;
    private final List<ExtraState> extraPromises;
    private final List<ExtraState> byproducts;
    private final List<PatternCraftingBranch> subRequests;

    public PatternCraftingBranch(
            IResource requestType,
            IAdditionalTargetInformation info,
            List<IPromise> promises,
            List<IExtraPromise> extraPromises,
            List<IExtraPromise> byproducts,
            List<PatternCraftingBranch> subRequests) {
        this(requestType, info, requestType.getRequestedAmount(), requestType.getRequestedAmount(),
                copyPromiseStates(promises), copyExtraStates(extraPromises), copyExtraStates(byproducts), subRequests);
    }

    private PatternCraftingBranch(
            IResource requestType,
            IAdditionalTargetInformation info,
            int originalAmount,
            int remainingAmount,
            List<PromiseState> promises,
            List<ExtraState> extraPromises,
            List<ExtraState> byproducts,
            List<PatternCraftingBranch> subRequests) {
        this.requestType = requestType;
        this.info = info;
        this.originalAmount = originalAmount;
        this.remainingAmount = remainingAmount;
        this.promises = promises;
        this.originalCraftingAmount = countCraftingAmount(promises);
        this.remainingCraftingAmount = this.originalCraftingAmount;
        this.extraPromises = extraPromises;
        this.byproducts = byproducts;
        this.subRequests = subRequests;
    }

    public IResource getRequestType() {
        return requestType;
    }

    public List<PatternCraftingBranch> getSubRequests() {
        return Collections.unmodifiableList(subRequests);
    }

    public int getRemainingAmount() {
        return remainingAmount;
    }

    /**
     * Checks whether this branch represents the requested item.
     */
    public boolean matches(ItemIdentifier item) {
        return requestType.matches(item, IResource.MatchSettings.NORMAL);
    }

    /**
     * Fulfils up to {@code amount} items from this branch and advances the branch state by the amount actually ordered.
     * <p>
     * Crafting promises pass their proportional child branch to the staged crafting pipe. Provider promises are fulfilled
     * directly, releasing any reservation that was made for this staged craft.
     */
    public int request(int amount) {
        int wanted = Math.min(amount, remainingAmount);
        int requested = 0;
        for (PromiseState promiseState : promises) {
            if (requested >= wanted) {
                break;
            }
            int toRequest = Math.min(wanted - requested, promiseState.remainingAmount);
            if (toRequest <= 0) {
                continue;
            }
            IPromise promise = copyPromiseForAmount(promiseState.promise, toRequest);
            IResource request = requestType.copyForDisplayWith(toRequest);
            if (promise.getType() == ResourceType.CRAFTING
                    && promise instanceof LogisticsPromise
                    && promise.getProvider() instanceof IStagedCraftingProvider) {
                PatternCraftingBranch stagedBranch = copyForAmount(toRequest);
                reserveSubRequestsFor(toRequest);
                IOrderInfoProvider result = ((IStagedCraftingProvider) promise.getProvider())
                        .fullFillStagedCrafting((LogisticsPromise) promise, request, info, stagedBranch);
                if (result == null) {
                    stagedBranch.releaseProviderPromises();
                }
            } else {
                if (promise.getType() == ResourceType.CRAFTING) {
                    requestSubRequestsFor(toRequest);
                }
                promise.fullFill(request, info);
            }
            if (promise.getType() == ResourceType.CRAFTING) {
                registerExtrasFor(toRequest);
                remainingCraftingAmount -= toRequest;
            }
            promiseState.remainingAmount -= toRequest;
            remainingAmount -= toRequest;
            requested += toRequest;
        }
        return requested;
    }

    /**
     * Creates an immutable work branch for the next {@code amount} items without consuming this branch.
     * <p>
     * Child branches are copied by cumulative consumption instead of scaling the current remainder. This keeps repeated
     * small requests from rounding up the same child dependency over and over again.
     */
    public PatternCraftingBranch copyForAmount(int amount) {
        int copiedAmount = Math.min(amount, remainingAmount);
        IResource copiedRequest = requestType.copyForDisplayWith(copiedAmount);
        List<PromiseState> copiedPromises = copyPromiseStatesFor(copiedAmount);
        int copiedCraftingAmount = countCraftingAmount(copiedPromises);
        List<ExtraState> copiedExtras = copyExtraStatesFor(extraPromises, copiedCraftingAmount);
        List<ExtraState> copiedByproducts = copyExtraStatesFor(byproducts, copiedCraftingAmount);
        List<PatternCraftingBranch> copiedChildren = new ArrayList<>();
        for (BranchAllocation allocation : allocateChildrenFor(copiedAmount)) {
            copiedChildren.add(allocation.branch.copyForAmount(allocation.amount));
        }
        return new PatternCraftingBranch(
                copiedRequest,
                info,
                copiedAmount,
                copiedAmount,
                copiedPromises,
                copiedExtras,
                copiedByproducts,
                copiedChildren);
    }

    /**
     * Registers the extra promises and recipe byproducts that belong to the next consumed crafting slice of this branch.
     */
    private void registerExtrasFor(int craftingAmount) {
        registerExtrasFor(extraPromises, craftingAmount);
        registerExtrasFor(byproducts, craftingAmount);
    }

    /**
     * Registers amount-proportional extras using cumulative floor allocation so rounded craft outputs are registered when
     * the craft set that actually produces the surplus is ordered.
     */
    private void registerExtrasFor(List<ExtraState> states, int craftingAmount) {
        int consumedBefore = originalCraftingAmount - remainingCraftingAmount;
        int consumedAfter = Math.min(originalCraftingAmount, consumedBefore + craftingAmount);
        for (ExtraState state : states) {
            int extraAmount = state.amountForRange(consumedBefore, consumedAfter, originalCraftingAmount);
            if (extraAmount <= 0) {
                continue;
            }
            IExtraPromise promise = state.promise.copy();
            promise.setAmount(extraAmount);
            promise.registerExtras(requestType.copyForDisplayWith(Math.max(1, craftingAmount)));
        }
    }

    /**
     * Copies the next {@code amount} items and consumes that amount from this branch.
     */
    public PatternCraftingBranch copyAndReserve(int amount) {
        int copiedAmount = Math.min(amount, remainingAmount);
        PatternCraftingBranch copy = copyForAmount(copiedAmount);
        reserve(copiedAmount);
        return copy;
    }

    /**
     * Reserves all provider promises in this branch so separate request trees cannot consume those provider items before
     * this staged craft asks for them.
     */
    public void reserveProviderPromises() {
        for (PromiseState promise : promises) {
            if (promise.providerReserved || promise.remainingAmount <= 0) {
                continue;
            }
            if (promise.promise.getType() == ResourceType.PROVIDER
                    && promise.promise.getProvider() instanceof IStagedProviderReservation) {
                ((IStagedProviderReservation) promise.promise.getProvider())
                        .reserveStagedCrafting(promise.promise.getItemType(), promise.remainingAmount);
                promise.providerReserved = true;
            }
        }
        for (PatternCraftingBranch child : subRequests) {
            child.reserveProviderPromises();
        }
    }

    /**
     * Releases provider reservations that are still owned by this branch.
     */
    public void releaseProviderPromises() {
        for (PromiseState promise : promises) {
            if (!promise.providerReserved || promise.remainingAmount <= 0) {
                continue;
            }
            if (promise.promise.getType() == ResourceType.PROVIDER
                    && promise.promise.getProvider() instanceof IStagedProviderReservation) {
                ((IStagedProviderReservation) promise.promise.getProvider())
                        .releaseStagedCrafting(promise.promise.getItemType(), promise.remainingAmount);
                promise.providerReserved = false;
            }
        }
        for (PatternCraftingBranch child : subRequests) {
            child.releaseProviderPromises();
        }
    }

    /**
     * Requests the child branches required for {@code amount} items of this branch.
     */
    private void requestSubRequestsFor(int amount) {
        for (BranchAllocation allocation : allocateChildrenFor(amount)) {
            allocation.branch.request(allocation.amount);
        }
    }

    /**
     * Consumes child branch capacity that is being handed to another staged crafting pipe.
     */
    private void reserveSubRequestsFor(int amount) {
        for (BranchAllocation allocation : allocateChildrenFor(amount)) {
            allocation.branch.reserve(allocation.amount);
        }
    }

    /**
     * Consumes {@code amount} items from this branch without placing orders.
     */
    private void reserve(int amount) {
        int reserved = Math.min(amount, remainingAmount);
        List<BranchAllocation> childAllocations = allocateChildrenFor(reserved);
        consumePromises(reserved);
        remainingAmount -= reserved;
        for (BranchAllocation allocation : childAllocations) {
            allocation.branch.reserve(allocation.amount);
        }
    }

    /**
     * Copies promise states in request order until {@code amount} items are represented.
     */
    private List<PromiseState> copyPromiseStatesFor(int amount) {
        List<PromiseState> copiedPromises = new ArrayList<>();
        int amountLeft = amount;
        for (PromiseState promise : promises) {
            if (amountLeft <= 0) {
                break;
            }
            int copied = Math.min(amountLeft, promise.remainingAmount);
            if (copied > 0) {
                copiedPromises.add(new PromiseState(
                        copyPromiseForAmount(promise.promise, copied),
                        copied,
                        promise.providerReserved));
                amountLeft -= copied;
            }
        }
        return copiedPromises;
    }

    /**
     * Consumes promise capacity in the same order the request tree selected it.
     */
    private void consumePromises(int amount) {
        int amountLeft = amount;
        for (PromiseState promise : promises) {
            if (amountLeft <= 0) {
                break;
            }
            int moved = Math.min(amountLeft, promise.remainingAmount);
            promise.remainingAmount -= moved;
            if (promise.promise.getType() == ResourceType.CRAFTING) {
                remainingCraftingAmount -= moved;
            }
            amountLeft -= moved;
        }
    }

    /**
     * Calculates child branch deltas for the next {@code amount} parent items.
     */
    private List<BranchAllocation> allocateChildrenFor(int amount) {
        List<BranchAllocation> allocations = new ArrayList<>();
        int parentAmount = Math.min(amount, remainingAmount);
        if (parentAmount <= 0 || originalAmount <= 0) {
            return allocations;
        }
        int parentConsumedBefore = originalAmount - remainingAmount;
        int parentConsumedAfter = Math.min(originalAmount, parentConsumedBefore + parentAmount);
        for (PatternCraftingBranch child : subRequests) {
            int childConsumedBefore = child.originalAmount - child.remainingAmount;
            int childConsumedAfter = scaleAmount(child.originalAmount, parentConsumedAfter, originalAmount);
            int childAmount = Math.min(child.remainingAmount, Math.max(0, childConsumedAfter - childConsumedBefore));
            if (childAmount > 0) {
                allocations.add(new BranchAllocation(child, childAmount));
            }
        }
        return allocations;
    }

    private static List<PromiseState> copyPromiseStates(List<IPromise> promises) {
        List<PromiseState> result = new ArrayList<>();
        for (IPromise promise : promises) {
            result.add(new PromiseState(promise.copy(), promise.getAmount(), false));
        }
        return result;
    }

    /**
     * Copies extra promises with their original branch amount.
     */
    private static List<ExtraState> copyExtraStates(List<IExtraPromise> promises) {
        List<ExtraState> result = new ArrayList<>();
        for (IExtraPromise promise : promises) {
            result.add(new ExtraState(promise.copy()));
        }
        return result;
    }

    /**
     * Copies the extra promise amounts that belong to the next {@code craftingAmount} crafted items of this branch.
     */
    private List<ExtraState> copyExtraStatesFor(List<ExtraState> states, int craftingAmount) {
        List<ExtraState> copied = new ArrayList<>();
        int consumedBefore = originalCraftingAmount - remainingCraftingAmount;
        int consumedAfter = Math.min(originalCraftingAmount, consumedBefore + craftingAmount);
        for (ExtraState state : states) {
            int extraAmount = state.amountForRange(consumedBefore, consumedAfter, originalCraftingAmount);
            if (extraAmount <= 0) {
                continue;
            }
            IExtraPromise promise = state.promise.copy();
            promise.setAmount(extraAmount);
            copied.add(new ExtraState(promise));
        }
        return copied;
    }

    /**
     * Counts how much of this branch is fulfilled by crafting promises and can therefore produce extras or byproducts.
     */
    private static int countCraftingAmount(List<PromiseState> promises) {
        int amount = 0;
        for (PromiseState promise : promises) {
            if (promise.promise.getType() == ResourceType.CRAFTING) {
                amount += promise.remainingAmount;
            }
        }
        return amount;
    }

    /**
     * Creates a promise copy with the requested amount while keeping the source promise untouched.
     */
    private static IPromise copyPromiseForAmount(IPromise promise, int amount) {
        IPromise copy = promise.copy();
        if (copy.getAmount() > amount) {
            copy.split(copy.getAmount() - amount);
        }
        return copy;
    }

    /**
     * Scales {@code amount} by {@code numerator / denominator}, rounding up so partial craft sets are represented.
     */
    private static int scaleAmount(int amount, int numerator, int denominator) {
        if (amount <= 0 || numerator <= 0 || denominator <= 0) {
            return 0;
        }
        long scaled = (long) amount * numerator;
        int result = (int) (scaled / denominator);
        if (scaled % denominator != 0) {
            result++;
        }
        return Math.min(amount, result);
    }

    private static class BranchAllocation {

        private final PatternCraftingBranch branch;
        private final int amount;

        private BranchAllocation(PatternCraftingBranch branch, int amount) {
            this.branch = branch;
            this.amount = amount;
        }
    }

    private static class PromiseState {

        private final IPromise promise;
        private int remainingAmount;
        private boolean providerReserved;

        private PromiseState(IPromise promise, int remainingAmount, boolean providerReserved) {
            this.promise = promise;
            this.remainingAmount = remainingAmount;
            this.providerReserved = providerReserved;
        }
    }

    private static class ExtraState {

        private final IExtraPromise promise;
        private final int originalAmount;

        private ExtraState(IExtraPromise promise) {
            this.promise = promise;
            this.originalAmount = promise.getAmount();
        }

        private int amountForRange(int consumedBefore, int consumedAfter, int parentAmount) {
            return scaledAmount(consumedAfter, parentAmount) - scaledAmount(consumedBefore, parentAmount);
        }

        private int scaledAmount(int consumed, int parentAmount) {
            if (originalAmount <= 0 || consumed <= 0 || parentAmount <= 0) {
                return 0;
            }
            return (int) ((long) originalAmount * consumed / parentAmount);
        }
    }
}
