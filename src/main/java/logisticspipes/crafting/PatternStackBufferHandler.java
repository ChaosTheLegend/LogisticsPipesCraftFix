package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import logisticspipes.interfaces.routing.ISaveState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;

class PatternStackBufferHandler implements ISaveState {

    private static final String BUFFER_TAG = "patternIngredientBuffer";
    private static final String LEGACY_BUFFER_TAG = "bufferedIngredients";
    private static final int TAG_COMPOUND = 10;

    private final Map<Integer, List<IPatternStack>> bufferedIngredients;

    PatternStackBufferHandler() {
        this.bufferedIngredients = new HashMap<>();
    }

    int amount(int patternSlot, IPatternStack stack) {
        if (stack == null) {
            return 0;
        }
        int amount = 0;
        for (IPatternStack buffered : getBuffer(patternSlot)) {
            if (buffered.canMerge(stack)) {
                amount += buffered.getAmount();
            }
        }
        return amount;
    }

    int amount(int patternSlot, ItemIdentifier item) {
        int amount = 0;
        for (IPatternStack buffered : getBuffer(patternSlot)) {
            if (PatternStackHelper.matches(buffered, item)) {
                amount += buffered.getAmount();
            }
        }
        return amount;
    }

    int amount(int patternSlot, FluidIdentifier fluid) {
        int amount = 0;
        for (IPatternStack buffered : getBuffer(patternSlot)) {
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
        List<IPatternStack> buffer = getBuffer(patternSlot);
        PatternStackHelper.addAggregated(buffer, stack);
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

    boolean remove(int patternSlot, IPatternStack stack, int amount) {
        if (stack == null || amount <= 0) {
            return true;
        }
        List<IPatternStack> buffer = getBuffer(patternSlot);
        for (int i = 0; i < buffer.size() && amount > 0; i++) {
            IPatternStack buffered = buffer.get(i);
            if (!buffered.canMerge(stack)) {
                continue;
            }
            int removed = Math.min(amount, buffered.getAmount());
            buffered.addAmount(-removed);
            amount -= removed;
            if (buffered.getAmount() <= 0) {
                buffer.remove(i);
                i--;
            }
        }
        if (buffer.isEmpty()) {
            bufferedIngredients.remove(patternSlot);
        }
        return amount == 0;
    }

    private List<IPatternStack> getBuffer(int patternSlot) {
        return bufferedIngredients.computeIfAbsent(patternSlot, k -> new ArrayList<>());
    }

    public void dropContents(World world, int x, int y, int z) {
        if (MainProxy.isServer(world)) {
            for (List<IPatternStack> patternStacks : bufferedIngredients.values()) {
                // we need to drop stack by stack in case we stored a higher stack count than possible (otherwise we
                // could get a 256 stack of oak planks)
                for (IPatternStack patternStack : patternStacks) {
                    var item = patternStack.getItem();
                    var maxStackSize = new ItemStack(item, 0).getMaxStackSize();
                    while (patternStack.getAmount() > 0) {
                        ItemStack toDrop = new ItemStack(item);
                        toDrop.stackSize = Math.min(patternStack.getAmount(), maxStackSize);
                        patternStack.addAmount(-toDrop.stackSize);
                    }
                }
            }
        }
    }

    public void clear() {
        bufferedIngredients.clear();
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
                getBuffer(patternSlot).add(stack);
            }
        }
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
     * @param patternSlot
     */
    public void removeAll(int patternSlot) {
        //TODO resend the stored items
        bufferedIngredients.remove(patternSlot);
    }

    /**
     * @return an unchangeable, unbacked list of the keys
     */
    public List<Integer> keySet() {
        return new ArrayList<>(bufferedIngredients.keySet());
    }
}
