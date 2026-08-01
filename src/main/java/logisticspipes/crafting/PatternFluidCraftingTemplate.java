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

    /**
     * Creates a fluid promise that keeps the source pattern slot and per-set fluid amount.
     * <p>
     * The staged crafting module uses those values to request the matching ingredient sets gradually and to drain the
     * correct amount of crafted fluid from the connected handler.
     */
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
