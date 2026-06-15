package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import logisticspipes.interfaces.routing.ISaveState;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;

class PatternStackRequestHandler implements ISaveState {

    private static final String REQUESTED_TAG = "patternRequestedIngredients";
    private static final int TAG_COMPOUND = 10;

    private final Map<Integer, List<IPatternStack>> requestedIngredients;

    PatternStackRequestHandler(Map<Integer, List<IPatternStack>> requestedIngredients) {
        this.requestedIngredients = requestedIngredients;
    }

    int amount(int patternSlot, IPatternStack stack) {
        if (stack == null) {
            return 0;
        }
        int amount = 0;
        for (IPatternStack requested : getRequested(patternSlot)) {
            if (requested.canMerge(stack)) {
                amount += requested.getAmount();
            }
        }
        return amount;
    }

    int amount(int patternSlot, ItemIdentifier item) {
        int amount = 0;
        for (IPatternStack requested : getRequested(patternSlot)) {
            if (PatternStackHelper.matches(requested, item)) {
                amount += requested.getAmount();
            }
        }
        return amount;
    }

    int amount(int patternSlot, FluidIdentifier fluid) {
        int amount = 0;
        for (IPatternStack requested : getRequested(patternSlot)) {
            if (PatternStackHelper.matches(requested, fluid)) {
                amount += requested.getAmount();
            }
        }
        return amount;
    }

    void add(int patternSlot, IPatternStack stack) {
        if (stack == null || stack.getAmount() <= 0) {
            return;
        }
        List<IPatternStack> requested = getRequested(patternSlot);
        PatternStackHelper.addAggregated(requested, stack);
    }

    /**
     * Removes an amount of an Item that is stored in the request handler.
     * Normally called on arrival of items on the pipe.
     * <br>
     * If the requested items are empty after the removal, remove the entry in the backing map
     * @param patternSlot the slot of the pattern
     * @param stack the stack of the item
     */
    void remove(int patternSlot, IPatternStack stack) {
        if (stack == null || stack.getAmount() <= 0) return;

        //get the stored requested item stack
        IPatternStack requested = requestedItemForPattern(patternSlot, stack);
        if (requested == null) return;

        //make sure we cant go negative
        int removed = Math.min(stack.getAmount(), requested.getAmount());
        requested.addAmount(-removed);

        removeEntryIfEmpty(patternSlot);
    }

    /**
     * Removes the entry for the given pattern, if it has no more request buffer
     * @param patternSlot the slot to check
     */
    private void removeEntryIfEmpty(int patternSlot) {
        getRequested(patternSlot).removeIf(requested -> requested.getAmount() <= 0);
        if (getRequested(patternSlot).isEmpty()) requestedIngredients.remove(patternSlot);
    }

    private IPatternStack requestedItemForPattern(int patternSlot, IPatternStack stack) {
        for (var requested : getRequested(patternSlot)) {
            if (requested.canMerge(stack)) return requested;
        }
        return null;
    }

    boolean removeAll(int patternSlot) {
        List<IPatternStack> removed = requestedIngredients.remove(patternSlot);
        if (removed == null) {
            return false;
        }
        for (IPatternStack stack : removed) {
            if (stack != null && stack.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    private List<IPatternStack> getRequested(int patternSlot) {
        return requestedIngredients.computeIfAbsent(patternSlot, k -> new ArrayList<>());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        requestedIngredients.clear();
        NBTTagList requested = tag.getTagList(REQUESTED_TAG, TAG_COMPOUND);
        for (int i = 0; i < requested.tagCount(); i++) {
            NBTTagCompound stackTag = requested.getCompoundTagAt(i);
            IPatternStack stack = IPatternStack.readFromNBT(stackTag);
            if (stack != null && stack.getAmount() > 0) {
                add(stackTag.getInteger("patternSlot"), stack);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList requested = new NBTTagList();
        for (Map.Entry<Integer, List<IPatternStack>> entry : requestedIngredients.entrySet()) {
            for (IPatternStack stack : entry.getValue()) {
                if (stack == null || stack.getAmount() <= 0) {
                    continue;
                }
                NBTTagCompound stackTag = new NBTTagCompound();
                stack.writeToNBT(stackTag);
                stackTag.setInteger("patternSlot", entry.getKey());
                requested.appendTag(stackTag);
            }
        }
        tag.setTag(REQUESTED_TAG, requested);
    }
}
