package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

final class PatternStackHelper {

    private PatternStackHelper() {}

    static List<IPatternStack> aggregate(List<? extends IPatternStack> stacks) {
        List<IPatternStack> result = new ArrayList<>();
        if (stacks == null) {
            return result;
        }
        for (IPatternStack stack : stacks) {
            addAggregated(result, stack);
        }
        return result;
    }

    static void addAggregated(List<IPatternStack> stacks, IPatternStack stack) {
        if (stack == null || stack.getAmount() <= 0) {
            return;
        }
        for (IPatternStack existing : stacks) {
            if (existing.canMerge(stack)) {
                existing.addAmount(stack.getAmount());
                return;
            }
        }
        stacks.add(stack.copy());
    }

    static IPatternStack copyWithAmount(IPatternStack stack, int amount) {
        if (stack == null || amount <= 0) {
            return null;
        }
        IPatternStack copy = stack.copy();
        copy.addAmount(amount - copy.getAmount());
        return copy;
    }

    static ItemIdentifierStack asSolidStack(IPatternStack stack) {
        if (stack instanceof PatternSolidStack) {
            return ((PatternSolidStack) stack).getItemIdentifierStack();
        }
        return null;
    }

    static FluidIdentifier asFluid(IPatternStack stack) {
        if (stack instanceof PatternFluidStack) {
            return ((PatternFluidStack) stack).getFluid();
        }
        return null;
    }

    static boolean isSolid(IPatternStack stack) {
        return stack instanceof PatternSolidStack;
    }

    static boolean isFluid(IPatternStack stack) {
        return stack instanceof PatternFluidStack;
    }

    static boolean matches(IPatternStack stack, ItemIdentifier item) {
        if (stack == null || item == null) {
            return false;
        }
        ItemIdentifierStack solid = asSolidStack(stack);
        if (solid != null) {
            return solid.getItem().equalsForCrafting(item);
        }
        FluidIdentifier fluid = asFluid(stack);
        if (fluid == null || !item.isFluidContainer()) {
            return false;
        }
        return fluid.equals(FluidIdentifier.get(item));
    }

    static boolean matches(IPatternStack stack, FluidIdentifier fluid) {
        FluidIdentifier stackFluid = asFluid(stack);
        return stackFluid != null && stackFluid.equals(fluid);
    }

    static ItemIdentifier getRoutingItem(IPatternStack stack) {
        ItemIdentifierStack solid = asSolidStack(stack);
        if (solid != null) {
            return solid.getItem();
        }
        FluidIdentifier fluid = asFluid(stack);
        return fluid == null ? null : fluid.getItemIdentifier();
    }

    static ItemIdentifierStack makeDisplayStack(IPatternStack stack) {
        ItemIdentifierStack solid = asSolidStack(stack);
        if (solid != null) {
            return solid.clone();
        }
        if (stack instanceof PatternFluidStack) {
            return ((PatternFluidStack) stack).makeDisplayStack();
        }
        return null;
    }
}
