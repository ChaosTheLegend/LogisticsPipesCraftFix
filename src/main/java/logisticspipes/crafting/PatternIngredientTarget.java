package logisticspipes.crafting;

import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.interfaces.routing.IRequestItems;

/**
 * One aggregated pattern ingredient together with the destination that should receive it.
 * <p>
 * A null target means the ingredient is local to the pattern crafting pipe and must be buffered before the craft is
 * inserted into the adjacent target. A non-null target routes the ingredient directly to a linked pattern satellite.
 */
record PatternIngredientTarget(IPatternStack stack, IRequestItems target) {

}
