package logisticspipes.crafting;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider;

public interface IStagedCraftingProvider {

    IOrderInfoProvider fullFillStagedCrafting(
            LogisticsPromise promise,
            IResource requestType,
            IAdditionalTargetInformation info,
            PatternCraftingBranch branch);
}
