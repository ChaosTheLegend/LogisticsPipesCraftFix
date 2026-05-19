package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.List;

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

public class PatternCraftingTemplate extends BaseCraftingTemplate {

    private final ItemIdentifierStack result;
    private final List<ItemIdentifierStack> byproducts = new ArrayList<>();
    private final List<FluidIdentifierStack> fluidByproducts = new ArrayList<>();
    private final ICraftItems crafter;
    private final int patternSlot;

    public PatternCraftingTemplate(ItemIdentifierStack result, ICraftItems crafter, int priority, int patternSlot) {
        super(Pattern.INGREDIENT_SLOTS, priority);
        this.result = result;
        this.crafter = crafter;
        this.patternSlot = patternSlot;
    }

    public void addByproduct(ItemIdentifierStack stack) {
        byproducts.add(stack);
    }

    public void addFluidByproduct(FluidIdentifierStack stack) {
        if (stack == null || stack.getStackSize() <= 0) {
            return;
        }
        for (int i = 0; i < fluidByproducts.size(); i++) {
            FluidIdentifierStack existing = fluidByproducts.get(i);
            if (existing.getFluidIdentifier().equals(stack.getFluidIdentifier())) {
                fluidByproducts.set(i, new FluidIdentifierStack(
                        existing.getFluidIdentifier(),
                        existing.getStackSize() + stack.getStackSize()));
                return;
            }
        }
        fluidByproducts.add(new FluidIdentifierStack(stack.getFluidIdentifier(), stack.getStackSize()));
    }

    @Override
    public List<IExtraPromise> getByproducts(int workSets) {
        List<IExtraPromise> result = new ArrayList<>();
        for (ItemIdentifierStack byproduct : byproducts) {
            result.add(new LogisticsExtraPromise(
                    byproduct.getItem(),
                    byproduct.getStackSize() * workSets,
                    crafter,
                    false));
        }
        if (crafter instanceof ICraftFluids) {
            for (FluidIdentifierStack byproduct : fluidByproducts) {
                result.add(new FluidExtraPromise(
                        byproduct.getFluidIdentifier(),
                        byproduct.getStackSize() * workSets,
                        (ICraftFluids) crafter,
                        false));
            }
        }
        return result;
    }

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
