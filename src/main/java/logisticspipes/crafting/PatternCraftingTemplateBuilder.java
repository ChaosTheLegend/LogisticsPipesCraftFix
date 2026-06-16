package logisticspipes.crafting;

import java.util.List;

import net.minecraft.item.ItemStack;

import logisticspipes.request.BaseCraftingTemplate;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.utils.FluidIdentifierStack;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Builds request-tree crafting templates from configured pattern items.
 * <p>
 * The module owns the live state and request interfaces; this helper only translates pattern inputs and outputs into
 * item or fluid crafting templates with matching byproduct promises.
 */
class PatternCraftingTemplateBuilder {

    private final ModuleItemCrafting module;
    private final PatternHandler patternHandler;

    /**
     * Creates a builder backed by the module request hooks and its current pattern inventory.
     */
    PatternCraftingTemplateBuilder(ModuleItemCrafting module, PatternHandler patternHandler) {
        this.module = module;
        this.patternHandler = patternHandler;
    }

    /**
     * Finds the first configured pattern output that matches the requested resource and returns its crafting template.
     */
    ICraftingTemplate addCrafting(IResource toCraft) {
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = patternHandler.getConfiguredPatternStack(slot);
            if (pattern == null) {
                continue;
            }
            if (!module.isPatternCraftingSupported(pattern)) {
                module.debug(
                        "crafting template skipped slot=%d request=%s: fluid crafting upgrade missing",
                        slot,
                        toCraft);
                continue;
            }
            AbstractPattern configuredPattern = Pattern.fromStack(pattern);
            List<IPatternStack> outputs = configuredPattern.getOutputs();
            ICraftingTemplate itemTemplate = buildItemTemplate(toCraft, slot, configuredPattern, outputs);
            if (itemTemplate != null) {
                return itemTemplate;
            }
            ICraftingTemplate fluidTemplate = buildFluidTemplate(toCraft, slot, configuredPattern, outputs);
            if (fluidTemplate != null) {
                return fluidTemplate;
            }
        }
        return null;
    }

    /**
     * Builds an item crafting template when one output item identity matches the requested resource.
     */
    private ICraftingTemplate buildItemTemplate(IResource toCraft, int slot, AbstractPattern configuredPattern,
            List<IPatternStack> outputs) {
        for (IPatternStack output : outputs) {
            ItemIdentifierStack result = PatternStackHelper.asSolidStack(output);
            if (result == null || !toCraft.matches(result.getItem(), IResource.MatchSettings.NORMAL)) {
                continue;
            }
            module.debug("crafting template matched item output slot=%d result=%s request=%s", slot, result, toCraft);
            PatternCraftingTemplate template = new PatternCraftingTemplate(result.clone(), module, 0, slot);
            addPatternIngredients(template, configuredPattern.getAggregatedInputs(), slot);
            addItemResultByproducts(template, result, outputs);
            return template;
        }
        return null;
    }

    /**
     * Builds a fluid crafting template when one output fluid display item identity matches the requested resource.
     */
    private ICraftingTemplate buildFluidTemplate(IResource toCraft, int slot, AbstractPattern configuredPattern,
            List<IPatternStack> outputs) {
        for (IPatternStack output : outputs) {
            if (!(output instanceof PatternFluidStack)) {
                continue;
            }
            PatternFluidStack result = (PatternFluidStack) output;
            if (!toCraft.matches(result.getFluid().getItemIdentifier(), IResource.MatchSettings.NORMAL)) {
                continue;
            }
            module.debug("crafting template matched fluid output slot=%d result=%s request=%s", slot, result, toCraft);
            PatternFluidCraftingTemplate template = new PatternFluidCraftingTemplate(
                    new FluidResource(result.getFluid(), result.getAmount(), module),
                    module,
                    0,
                    slot);
            addPatternIngredients(template, configuredPattern.getAggregatedInputs(), slot);
            addFluidResultByproducts(template, result, outputs);
            return template;
        }
        return null;
    }

    /**
     * Adds every non-requested output from an item-producing pattern as an extra item or fluid byproduct.
     */
    private void addItemResultByproducts(PatternCraftingTemplate template, ItemIdentifierStack result,
            List<IPatternStack> outputs) {
        for (IPatternStack byproductStack : outputs) {
            ItemIdentifierStack byproduct = PatternStackHelper.asSolidStack(byproductStack);
            if (byproduct != null && !byproduct.getItem().equals(result.getItem())) {
                template.addByproduct(byproduct.clone());
                continue;
            }
            if (byproductStack instanceof PatternFluidStack) {
                PatternFluidStack fluidByproduct = (PatternFluidStack) byproductStack;
                template.addFluidByproduct(
                        new FluidIdentifierStack(fluidByproduct.getFluid(), fluidByproduct.getAmount()));
            }
        }
    }

    /**
     * Adds every secondary output from a fluid-producing pattern as an extra item or fluid byproduct.
     */
    private void addFluidResultByproducts(PatternFluidCraftingTemplate template, PatternFluidStack result,
            List<IPatternStack> outputs) {
        for (IPatternStack byproductStack : outputs) {
            ItemIdentifierStack byproduct = PatternStackHelper.asSolidStack(byproductStack);
            if (byproduct != null) {
                template.addByproduct(byproduct.clone());
                continue;
            }
            if (byproductStack instanceof PatternFluidStack) {
                PatternFluidStack fluidByproduct = (PatternFluidStack) byproductStack;
                if (!fluidByproduct.getFluid().equals(result.getFluid())) {
                    template.addFluidByproduct(
                            new FluidIdentifierStack(fluidByproduct.getFluid(), fluidByproduct.getAmount()));
                }
            }
        }
    }

    /**
     * Adds every local item or fluid ingredient from a pattern to a request-tree template.
     */
    private void addPatternIngredients(BaseCraftingTemplate template, List<IPatternStack> ingredients, int slot) {
        for (IPatternStack ingredient : ingredients) {
            ItemIdentifierStack item = PatternStackHelper.asSolidStack(ingredient);
            if (item != null) {
                module.debug("template ingredient slot=%d item=%s", slot, item);
                template.addIngredient(new ItemResource(item.clone(), module), new PatternTargetInformation(slot));
                continue;
            }
            if (ingredient instanceof PatternFluidStack) {
                PatternFluidStack fluid = (PatternFluidStack) ingredient;
                module.debug("template ingredient slot=%d fluid=%s", slot, fluid);
                template.addIngredient(
                        new FluidResource(fluid.getFluid(), fluid.getAmount(), module),
                        new PatternTargetInformation(slot));
            }
        }
    }
}
