package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.interfaces.routing.ISaveState;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;

class PatternStackBufferHandler implements ISaveState {

    private static final String BUFFER_TAG = "patternIngredientBuffer";
    private static final String LEGACY_BUFFER_TAG = "bufferedIngredients";
    private static final int TAG_COMPOUND = 10;

    private final Map<Integer, List<IPatternStack>> bufferedIngredients;
    private final Runnable changeListener;

    PatternStackBufferHandler(Runnable changeListener) {
        this.bufferedIngredients = new HashMap<>();
        this.changeListener = changeListener;
    }

    int amount(int patternSlot, IPatternStack stack) {
        if (stack == null) {
            return 0;
        }
        int amount = 0;
        for (IPatternStack buffered : getExistingBuffer(patternSlot)) {
            if (buffered.canMerge(stack)) {
                amount += buffered.getAmount();
            }
        }
        return amount;
    }

    int amount(int patternSlot, ItemIdentifier item) {
        int amount = 0;
        for (IPatternStack buffered : getExistingBuffer(patternSlot)) {
            if (PatternStackHelper.matches(buffered, item)) {
                amount += buffered.getAmount();
            }
        }
        return amount;
    }

    int amount(int patternSlot, FluidIdentifier fluid) {
        int amount = 0;
        for (IPatternStack buffered : getExistingBuffer(patternSlot)) {
            if (PatternStackHelper.matches(buffered, fluid)) {
                amount += buffered.getAmount();
            }
        }
        return amount;
    }

    void add(int patternSlot, IPatternStack stack) {
        if (stack == null || stack.getAmount() <= 0) {
            return;
        }
        List<IPatternStack> buffer = getOrCreateBuffer(patternSlot);
        PatternStackHelper.addAggregated(buffer, stack);
        markChanged();
    }

    int completeSets(int patternSlot, List<IPatternStack> ingredients) {
        int sets = Integer.MAX_VALUE;
        for (IPatternStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getAmount() <= 0) {
                continue;
            }
            sets = Math.min(sets, amount(patternSlot, ingredient) / ingredient.getAmount());
        }
        return sets == Integer.MAX_VALUE ? 0 : Math.max(0, sets);
    }

    boolean canCompleteOneSetAfterAdding(int patternSlot, List<IPatternStack> ingredients,
            IPatternStack arrivingStack) {
        if (arrivingStack == null || arrivingStack.getAmount() <= 0) {
            return completeSets(patternSlot, ingredients) > 0;
        }
        for (IPatternStack ingredient : ingredients) {
            int available = amount(patternSlot, ingredient);
            if (ingredient.canMerge(arrivingStack)) {
                available += arrivingStack.getAmount();
            }
            if (available < ingredient.getAmount()) {
                return false;
            }
        }
        return true;
    }

    void removePatternSets(int patternSlot, List<IPatternStack> ingredients, int sets) {
        if (sets <= 0) {
            return;
        }
        for (IPatternStack ingredient : ingredients) {
            remove(patternSlot, ingredient, ingredient.getAmount() * sets);
        }
    }

    void remove(int patternSlot, IPatternStack stack, int amount) {
        if (stack == null || amount <= 0) {
            return;
        }
        List<IPatternStack> buffer = bufferedIngredients.get(patternSlot);
        if (buffer == null) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < buffer.size() && amount > 0; i++) {
            IPatternStack buffered = buffer.get(i);
            if (!buffered.canMerge(stack)) {
                continue;
            }
            int removed = Math.min(amount, buffered.getAmount());
            buffered.addAmount(-removed);
            amount -= removed;
            changed = true;
            if (buffered.getAmount() <= 0) {
                buffer.remove(i);
                i--;
            }
        }
        if (buffer.isEmpty()) {
            bufferedIngredients.remove(patternSlot);
        }
        if (changed) {
            markChanged();
        }
    }

    private List<IPatternStack> getExistingBuffer(int patternSlot) {
        List<IPatternStack> buffer = bufferedIngredients.get(patternSlot);
        return buffer == null ? Collections.emptyList() : buffer;
    }

    private List<IPatternStack> getOrCreateBuffer(int patternSlot) {
        return bufferedIngredients.computeIfAbsent(patternSlot, k -> new ArrayList<>());
    }

    public void dropContents(World world, int x, int y, int z) {
        if (MainProxy.isServer(world)) {
            for (List<IPatternStack> patternStacks : new ArrayList<>(bufferedIngredients.values())) {
                for (IPatternStack patternStack : patternStacks) {
                    for (ItemStack stack : makeItemStacks(patternStack)) {
                        ItemIdentifierInventory.dropItems(world, stack, x, y, z);
                    }
                }
            }
            clear();
        }
    }

    public void clear() {
        if (bufferedIngredients.isEmpty()) {
            return;
        }
        bufferedIngredients.clear();
        markChanged();
    }

    public int size() {
        return bufferedIngredients.size();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        bufferedIngredients.clear();
        String tagName = nbttagcompound.hasKey(BUFFER_TAG) ? BUFFER_TAG : LEGACY_BUFFER_TAG;
        NBTTagList buffer = nbttagcompound.getTagList(tagName, TAG_COMPOUND);
        for (int i = 0; i < buffer.tagCount(); i++) {
            NBTTagCompound stackTag = buffer.getCompoundTagAt(i);
            int patternSlot = stackTag.getInteger("patternSlot");
            IPatternStack stack = IPatternStack.readFromNBT(stackTag);
            if (stack != null) {
                getOrCreateBuffer(patternSlot).add(stack);
            }
        }
        markChanged();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        NBTTagList buffer = new NBTTagList();
        for (Map.Entry<Integer, List<IPatternStack>> entry : bufferedIngredients.entrySet()) {
            for (IPatternStack stack : entry.getValue()) {
                NBTTagCompound stackTag = new NBTTagCompound();
                stack.writeToNBT(stackTag);
                stackTag.setInteger("patternSlot", entry.getKey());
                buffer.appendTag(stackTag);
            }
        }
        nbttagcompound.setTag(BUFFER_TAG, buffer);
    }

    public Map<Integer, List<IPatternStack>> asMap() {
        return bufferedIngredients;
    }

    /**
     * Removes this from the
     *
     * @param patternSlot the slot
     */
    public List<IPatternStack> removeAll(int patternSlot) {
        List<IPatternStack> removed = bufferedIngredients.remove(patternSlot);
        if (removed == null) {
            return new ArrayList<>();
        }
        List<IPatternStack> copy = new ArrayList<>(removed.size());
        for (IPatternStack stack : removed) {
            if (stack != null && stack.getAmount() > 0) {
                copy.add(stack.copy());
            }
        }
        markChanged();
        return copy;
    }

    /**
     * @return an unchangeable, unbacked list of the keys
     */
    public List<Integer> keySet() {
        return new ArrayList<>(bufferedIngredients.keySet());
    }

    private void markChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    static List<ItemStack> makeItemStacks(IPatternStack patternStack) {
        List<ItemStack> stacks = new ArrayList<>();
        if (patternStack == null || patternStack.getAmount() <= 0) {
            return stacks;
        }
        ItemStack stack = patternStack.makePatternStack();
        if (stack == null || stack.stackSize <= 0) {
            return stacks;
        }
        int amount = stack.stackSize;
        int maxStackSize = Math.max(1, stack.getMaxStackSize());
        while (amount > 0) {
            ItemStack split = stack.copy();
            split.stackSize = Math.min(amount, maxStackSize);
            stacks.add(split);
            amount -= split.stackSize;
        }
        return stacks;
    }
}
