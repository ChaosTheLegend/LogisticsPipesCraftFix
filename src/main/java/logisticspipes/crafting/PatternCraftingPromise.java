package logisticspipes.crafting;

import logisticspipes.interfaces.routing.IProvideItems;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.utils.item.ItemIdentifier;

public class PatternCraftingPromise extends LogisticsPromise {

    private final int patternSlot;
    private final int resultAmountPerSet;

    public PatternCraftingPromise(
            ItemIdentifier item,
            int numberOfItems,
            IProvideItems sender,
            int patternSlot,
            int resultAmountPerSet) {
        super(item, numberOfItems, sender, ResourceType.CRAFTING);
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
    public PatternCraftingPromise copy() {
        return new PatternCraftingPromise(item, numberOfItems, sender, patternSlot, resultAmountPerSet);
    }
}
