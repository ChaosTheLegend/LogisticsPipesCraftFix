package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.NonNull;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.string.ChatColor;
import logisticspipes.utils.string.StringUtils;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractPattern {

    private static final String ITEMS_TAG = "patternItems";
    private static final String SATELLITE_TARGETS_TAG = "patternSatelliteTargets";
    private static final String CRAFTING_PATTERN_TAG = "patternCraftingType";

    private final ItemStack patternStack;

    protected AbstractPattern(ItemStack patternStack) {
        this.patternStack = patternStack;
    }

    public ItemStack getPatternStack() {
        return patternStack;
    }

    public abstract int getIngredientSlotCount();

    public abstract int getResultSlotCount();

    public int getResultSlotStart() {
        return getIngredientSlotCount();
    }

    public int getItemSlotCount() {
        return getIngredientSlotCount() + getResultSlotCount();
    }

    public List<ItemIdentifierStack> getAggregatedIngredients() {
        HashMap<ItemIdentifier, Integer> ingredientCounts = new HashMap<>();

        for (ItemIdentifierStack ingredient : getIngredients()) {
            ItemIdentifier item = ingredient.getItem();
            ingredientCounts.putIfAbsent(item, 0);
            ingredientCounts.compute(item, (key, value) -> value + ingredient.getStackSize());
        }

        ArrayList<ItemIdentifierStack> result = new ArrayList<>();
        for (Map.Entry<ItemIdentifier, Integer> entry : ingredientCounts.entrySet()) {
            result.add(new ItemIdentifierStack(entry.getKey(), entry.getValue()));
        }

        return result;
    }

    public List<PatternFluidStack> getAggregatedFluidIngredients() {
        LinkedHashMap<FluidIdentifier, Integer> fluidCounts = new LinkedHashMap<>();
        for (PatternFluidStack ingredient : getFluidIngredients()) {
            fluidCounts.putIfAbsent(ingredient.getFluid(), 0);
            fluidCounts.compute(ingredient.getFluid(), (key, value) -> value + ingredient.getAmount());
        }

        ArrayList<PatternFluidStack> result = new ArrayList<>();
        for (Map.Entry<FluidIdentifier, Integer> entry : fluidCounts.entrySet()) {
            result.add(new PatternFluidStack(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /**
     * Clears all item and fluid representations stored by this pattern.
     */
    public void clear() {
        for (int i = 0; i < getItemSlotCount(); i++) {
            setStackInSlot(i, null);
        }
        for (int i = 0; i < getIngredientSlotCount(); i++) {
            setSatelliteIdForInputSlot(i, 0);
        }
    }

    /**
     * Multiplies both item stack sizes and fluid amounts stored by this pattern.
     */
    public void multiply(int factor) {
        for (int i = 0; i < getItemSlotCount(); i++) {
            IPatternStack stack = getPatternStackInSlot(i);
            if (stack != null) {
                IPatternStack copy = stack.copy();
                copy.addAmount(stack.getAmount() * (factor - 1));
                setPatternStackInSlot(i, copy);
            }
        }
    }

    public ItemStack getStackInSlot(int slot) {
        IPatternStack stack = getPatternStackInSlot(slot);
        return stack == null ? null : stack.makePatternStack();
    }

    public IPatternStack getPatternStackInSlot(int slot) {
        if (patternStack == null || slot < 0 || slot >= getItemSlotCount() || !patternStack.hasTagCompound()) {
            return null;
        }
        NBTTagList list = patternStack.getTagCompound().getTagList(ITEMS_TAG, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getInteger("slot") == slot) {
                return IPatternStack.readFromNBT(tag);
            }
        }
        return null;
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        setPatternStackInSlot(slot, IPatternStack.fromItemStack(stack));
    }

    public void setPatternStackInSlot(int slot, IPatternStack stack) {
        if (patternStack == null || slot < 0 || slot >= getItemSlotCount()) {
            return;
        }
        NBTTagCompound root = getOrCreateTag(patternStack);
        NBTTagList oldList = root.getTagList(ITEMS_TAG, 10);
        NBTTagList newList = new NBTTagList();
        for (int i = 0; i < oldList.tagCount(); i++) {
            NBTTagCompound tag = oldList.getCompoundTagAt(i);
            if (tag.getInteger("slot") != slot) {
                newList.appendTag(tag);
            }
        }
        if (stack != null && stack.getAmount() > 0) {
            NBTTagCompound tag = new NBTTagCompound();
            stack.writeToNBT(tag);
            tag.setInteger("slot", slot);
            newList.appendTag(tag);
        }
        root.setTag(ITEMS_TAG, newList);
    }

    public int getSatelliteIdForInputSlot(int slot) {
        if (patternStack == null || slot < 0 || slot >= getIngredientSlotCount() || !patternStack.hasTagCompound()) {
            return 0;
        }
        int[] targets = patternStack.getTagCompound().getIntArray(SATELLITE_TARGETS_TAG);
        return slot < targets.length ? Math.max(0, targets[slot]) : 0;
    }

    public void setSatelliteIdForInputSlot(int slot, int satelliteId) {
        if (patternStack == null || slot < 0 || slot >= getIngredientSlotCount()) {
            return;
        }
        NBTTagCompound root = getOrCreateTag(patternStack);
        int[] existing = root.getIntArray(SATELLITE_TARGETS_TAG);
        int[] targets = new int[getIngredientSlotCount()];
        System.arraycopy(existing, 0, targets, 0, Math.min(existing.length, targets.length));
        targets[slot] = Math.max(0, satelliteId);
        root.setIntArray(SATELLITE_TARGETS_TAG, targets);
    }

    public boolean isCraftingPattern() {
        if (patternStack == null) {
            return false;
        }
        return patternStack.getTagCompound().getBoolean(CRAFTING_PATTERN_TAG);
    }

    public void setCraftingPattern(boolean isCrafting) {
        if (patternStack == null) {
            return;
        }
        NBTTagCompound root = getOrCreateTag(patternStack);
        root.setBoolean(CRAFTING_PATTERN_TAG, isCrafting);
    }

    public List<IPatternStack> getInputs() {
        return readPatternRange(0, getIngredientSlotCount());
    }

    public List<IPatternStack> getOutputs() {
        return readPatternRange(getResultSlotStart(), getItemSlotCount());
    }

    public List<ItemIdentifierStack> getIngredients() {
        return toItemIdentifierStacks(readSolidRange(0, getIngredientSlotCount()));
    }

    public List<ItemIdentifierStack> getResults() {
        return toItemIdentifierStacks(readSolidRange(getResultSlotStart(), getItemSlotCount()));
    }

    public List<PatternFluidStack> getFluidIngredients() {
        return readFluidRange(0, getIngredientSlotCount());
    }

    public List<PatternFluidStack> getFluidResults() {
        return readFluidRange(getResultSlotStart(), getItemSlotCount());
    }

    public void setFluidIngredients(List<PatternFluidStack> fluids) {
        setFluidStacksInRange(0, getIngredientSlotCount(), fluids);
    }

    public void setFluidResults(List<PatternFluidStack> fluids) {
        setFluidStacksInRange(getResultSlotStart(), getItemSlotCount(), fluids);
    }

    public ItemStack getPrimaryResultStack() {
        List<IPatternStack> outputs = getOutputs();
        if (!outputs.isEmpty()) {
            return outputs.get(0).makeDisplayItemStack();
        }
        return null;
    }

    public boolean isConfigured() {
        boolean hasInputs = !getInputs().isEmpty();
        boolean hasResults = !getOutputs().isEmpty();
        return hasInputs && hasResults;
    }

    public void addTooltipInformation(List<String> tooltip) {
        List<IPatternStack> outputs = getOutputs();
        if (outputs.isEmpty()) {
            return;
        }
        tooltip.add(ChatColor.AQUA + "Results:");
        addPatternStacksToTooltip(tooltip, outputs, ChatColor.DARK_BLUE);
        if (!getInputs().isEmpty()) {
            StringUtils.addShiftAction(tooltip, () -> {
                tooltip.add(ChatColor.DARK_GREEN + "Ingredients:");
                List<IPatternStack> inputs = new ArrayList<>();
                inputs.addAll(getAggregatedSolidPatternStacks(readSolidRange(0, getIngredientSlotCount())));
                inputs.addAll(getAggregatedFluidIngredients());
                addPatternStacksToTooltip(tooltip, inputs, ChatColor.GREEN);
            });
        }
    }

    private void addPatternStacksToTooltip(List<String> tooltip, List<IPatternStack> stacks, ChatColor color) {
        for (IPatternStack stack : stacks) {
            if (stack instanceof PatternFluidStack) {
                tooltip.add("  " + ChatColor.WHITE + stack.getAmount() + "mB " + color
                    + ((PatternFluidStack) stack).makeFluidStack().getLocalizedName());
            } else {
                ItemStack normalStack = stack.makeDisplayItemStack();
                tooltip.add("  " + ChatColor.WHITE + normalStack.stackSize + " " + color
                    + normalStack.getDisplayName());
            }
        }
    }

    private List<ItemIdentifierStack> toItemIdentifierStacks(List<PatternSolidStack> stacks) {
        List<ItemIdentifierStack> result = new ArrayList<>();
        for (PatternSolidStack stack : stacks) {
            result.add(stack.getItem().clone());
        }
        return result;
    }

    private List<PatternSolidStack> getAggregatedSolidPatternStacks(List<PatternSolidStack> stacks) {
        List<PatternSolidStack> result = new ArrayList<>();
        for (PatternSolidStack stack : stacks) {
            PatternSolidStack patternStack = stack.copy();
            boolean merged = false;
            for (PatternSolidStack existing : result) {
                if (existing.canMerge(patternStack)) {
                    existing.addAmount(patternStack.getAmount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                result.add(patternStack);
            }
        }
        return result;
    }

    private List<PatternFluidStack> readFluidRange(int start, int end) {
        List<PatternFluidStack> fluids = new ArrayList<>();
        for (int slot = start; slot < end; slot++) {
            IPatternStack stack = getPatternStackInSlot(slot);
            if (stack instanceof PatternFluidStack && stack.getAmount() > 0) {
                fluids.add((PatternFluidStack) stack);
            }
        }
        return fluids;
    }

    private List<PatternSolidStack> readSolidRange(int start, int end) {
        List<PatternSolidStack> stacks = new ArrayList<>();
        for (int slot = start; slot < end; slot++) {
            IPatternStack stack = getPatternStackInSlot(slot);
            if (stack instanceof PatternSolidStack && stack.getAmount() > 0) {
                stacks.add((PatternSolidStack) stack);
            }
        }
        return stacks;
    }

    private List<IPatternStack> readPatternRange(int start, int end) {
        List<IPatternStack> stacks = new ArrayList<>();
        for (int slot = start; slot < end; slot++) {
            IPatternStack stack = getPatternStackInSlot(slot);
            if (stack != null && stack.getAmount() > 0) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private void setFluidStacksInRange(int start, int end, List<PatternFluidStack> fluids) {
        int fluidIndex = 0;
        for (int slot = start; slot < end; slot++) {
            IPatternStack existing = getPatternStackInSlot(slot);
            if (existing != null && !(existing instanceof PatternFluidStack)) {
                continue;
            }
            PatternFluidStack fluid = fluidIndex < fluids.size() ? fluids.get(fluidIndex++) : null;
            setPatternStackInSlot(slot, fluid);
        }
    }

    private NBTTagCompound getOrCreateTag(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }

    /**
     * Clears the pattern, and sets the given in and outputs.
     * If this is a processing pattern, null items in the inputs will be ignored.
     * If this is a crafting pattern, null items in the inputs will be respected, and the slot will be kept empty.
     * @param inputs the new inputs
     * @param outputs the new outputs
     */
    public void setInputsAndOutputs(@NonNull List<@Nullable IPatternStack> inputs, @NonNull List<@NonNull IPatternStack> outputs) {
        clear();

        boolean isCraftingPattern = isCraftingPattern();

        int patternSlotId = 0;
        for (int i = 0; i < inputs.size() && patternSlotId < getIngredientSlotCount(); i++) {
            IPatternStack input = inputs.get(i);

            // in processing patterns we ignore empty stacks
            if (!isCraftingPattern && input == null) continue;

            setPatternStackInSlot(patternSlotId, input);
            patternSlotId++;
        }

        patternSlotId = getIngredientSlotCount();
        for (int i = 0; i < outputs.size() && patternSlotId < getItemSlotCount(); i++) {
            IPatternStack output = outputs.get(i);
            setPatternStackInSlot(patternSlotId, output);
            patternSlotId++;
        }
    }
}
