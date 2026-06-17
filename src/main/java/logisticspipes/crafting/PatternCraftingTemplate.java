package logisticspipes.crafting;

import logisticspipes.crafting.pattern.ItemPattern;
import logisticspipes.interfaces.routing.ICraftFluids;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.request.BaseCraftingTemplate;
import logisticspipes.request.IExtraPromise;
import logisticspipes.request.IPromise;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.FluidExtraPromise;
import logisticspipes.routing.LogisticsExtraPromise;
import logisticspipes.utils.FluidIdentifierStack;
import logisticspipes.utils.item.ItemIdentifierStack;

import java.util.ArrayList;
import java.util.List;

public class PatternCraftingTemplate extends BaseCraftingTemplate {

    private final ItemIdentifierStack result;
    private final List<ItemIdentifierStack> byproducts = new ArrayList<>();
    private final List<FluidIdentifierStack> fluidByproducts = new ArrayList<>();
    private final ICraftItems crafter;
    private final int patternSlot;

    public PatternCraftingTemplate(ItemIdentifierStack result, ICraftItems crafter, int priority, int patternSlot) {
        super(ItemPattern.INGREDIENT_SLOTS, priority);
        this.result = result;
        this.crafter = crafter;
        this.patternSlot = patternSlot;
    }

    /**
     * Registers an item output from the same pattern that is not the requested result.
     * <p>
     * These outputs become extra promises when the request tree decides to craft this template, so the crafting pipe
     * will later extract them from the connected inventory and route them to storage or a consumer.
     */
    public void addByproduct(ItemIdentifierStack stack) {
        byproducts.add(stack);
    }

    /**
     * Registers a fluid output from the same pattern that is not the requested item result.
     * <p>
     * Pattern item crafts may still produce fluid byproducts. They are tracked separately from item byproducts because
     * the request tree must create {@link FluidExtraPromise}s and the pipe must drain those fluids from a fluid
     * handler.
     */
    public void addFluidByproduct(FluidIdentifierStack stack) {
        if (stack == null || stack.getStackSize() <= 0) {
            return;
        }
        for (int i = 0; i < fluidByproducts.size(); i++) {
            FluidIdentifierStack existing = fluidByproducts.get(i);
            if (existing.getFluidIdentifier().equals(stack.getFluidIdentifier())) {
                fluidByproducts.set(
                        i,
                        new FluidIdentifierStack(
                                existing.getFluidIdentifier(),
                                existing.getStackSize() + stack.getStackSize()));
                return;
            }
        }
        fluidByproducts.add(new FluidIdentifierStack(stack.getFluidIdentifier(), stack.getStackSize()));
    }

    /**
     * Creates extra promises for every item and fluid byproduct produced by the requested number of pattern work sets.
     * <p>
     * The promises are registered during request fulfilment and become destinationless extra orders. Once the craft has
     * run, those orders force the pipe to remove the byproducts from the adjacent inventory or fluid handler.
     */
    @Override
    public List<IExtraPromise> getByproducts(int workSets) {
        List<IExtraPromise> result = new ArrayList<>();
        for (ItemIdentifierStack byproduct : byproducts) {
            result.add(
                    new LogisticsExtraPromise(
                            byproduct.getItem(),
                            byproduct.getStackSize() * workSets,
                            crafter,
                            false));
        }
        if (crafter instanceof ICraftFluids) {
            for (FluidIdentifierStack byproduct : fluidByproducts) {
                result.add(
                        new FluidExtraPromise(
                                byproduct.getFluidIdentifier(),
                                byproduct.getStackSize() * workSets,
                                (ICraftFluids) crafter,
                                false));
            }
        }
        return result;
    }

    /**
     * Creates the staged promise for the requested item result and records the pattern slot that produced it.
     * <p>
     * The slot and per-set result amount let {@link ModulePatternCrafting} request ingredients gradually while the
     * output order remains visible in the normal item order manager.
     */
    @Override
    public IPromise generatePromise(int nCraftingSetsNeeded) {
        return new PatternCraftingPromise(
                result.getItem(),
                result.getStackSize() * nCraftingSetsNeeded,
                crafter,
                patternSlot,
                result.getStackSize());
    }

    @Override
    public ICraftItems getCrafter() {
        return crafter;
    }

    @Override
    public boolean canCraft(IResource requestType) {
        return requestType.matches(result.getItem(), IResource.MatchSettings.NORMAL);
    }

    @Override
    public IResource getResultResource() {
        return new ItemResource(result, null);
    }

    @Override
    public ItemIdentifierStack getResultStack() {
        return result;
    }
}
