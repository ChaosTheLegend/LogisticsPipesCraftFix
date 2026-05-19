package logisticspipes.crafting;

import logisticspipes.interfaces.routing.ICraftFluids;
import logisticspipes.request.FluidCraftingTemplate;
import logisticspipes.request.resources.FluidResource;

public class PatternFluidCraftingTemplate extends FluidCraftingTemplate {

    private final FluidResource result;
    private final ICraftFluids crafter;
    private final int patternSlot;

    public PatternFluidCraftingTemplate(FluidResource result, ICraftFluids crafter, int priority, int patternSlot) {
        super(result, crafter, priority);
        this.result = result;
        this.crafter = crafter;
        this.patternSlot = patternSlot;
    }

    @Override
    public PatternFluidCraftingPromise generatePromise(int nResultSets) {
        return new PatternFluidCraftingPromise(
                result.getFluid(),
                result.getRequestedAmount() * nResultSets,
                crafter,
                patternSlot,
                result.getRequestedAmount());
    }
}
