package logisticspipes.crafting;

import com.github.bsideup.jabel.Desugar;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;

/**
 * One aggregated pattern ingredient together with the destination that should receive it.
 * <p>
 * Null item and fluid targets mean the ingredient is local to the pattern crafting pipe and must be buffered before the
 * craft is inserted into the adjacent target. A non-null target routes the ingredient directly to a linked pattern
 * satellite of the matching transport type.
 */
@Desugar
record PatternIngredientTarget(IPatternStack stack, IRequestItems itemTarget, IRequestFluid fluidTarget) {

    boolean isLocal() {
        return itemTarget == null && fluidTarget == null;
    }
}
