package logisticspipes.crafting;

import logisticspipes.interfaces.routing.IProvideFluids;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.utils.FluidIdentifier;

public class PatternFluidCraftingPromise extends FluidLogisticsPromise {

    private final int patternSlot;
    private final int resultAmountPerSet;

    public PatternFluidCraftingPromise(
            FluidIdentifier fluid,
            int amount,
            IProvideFluids sender,
            int patternSlot,
            int resultAmountPerSet) {
        super(fluid, amount, sender, ResourceType.CRAFTING);
        this.patternSlot = patternSlot;
        this.resultAmountPerSet = resultAmountPerSet;
    }

    public int getPatternSlot() {
        return patternSlot;
    }

    public int getResultAmountPerSet() {
        return resultAmountPerSet;
    }

    @Override
    public PatternFluidCraftingPromise copy() {
        return new PatternFluidCraftingPromise(getLiquid(), getAmount(), getSender(), patternSlot, resultAmountPerSet);
    }

    @Override
    public PatternFluidCraftingPromise copyWithAmount(int amount) {
        return new PatternFluidCraftingPromise(getLiquid(), amount, getSender(), patternSlot, resultAmountPerSet);
    }
}
