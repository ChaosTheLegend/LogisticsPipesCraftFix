package logisticspipes.crafting;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.items.LogisticsItem;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.string.ChatColor;
import logisticspipes.utils.string.StringUtils;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class Pattern extends LogisticsItem {

    public static final int INGREDIENT_SLOTS = 9;
    public static final int RESULT_SLOTS = 3;
    public static final int ITEM_SLOT_COUNT = INGREDIENT_SLOTS + RESULT_SLOTS;
    public static final int FLUID_INPUT_SLOTS = 3;
    public static final int FLUID_RESULT_SLOTS = 3;
    public static final int FLUID_INPUT_START = ITEM_SLOT_COUNT;
    public static final int FLUID_RESULT_START = FLUID_INPUT_START + FLUID_INPUT_SLOTS;
    public static final int SLOT_COUNT = FLUID_RESULT_START + FLUID_RESULT_SLOTS;
    private static final String ITEMS_TAG = "patternItems";
    private static final String FLUID_INPUTS_TAG = "patternFluidInputs";
    private static final String FLUID_RESULTS_TAG = "patternFluidResults";

    public Pattern() {
        setMaxStackSize(1);
    }

    public static List<ItemIdentifierStack> getAggregatedIngredients(ItemStack pattern) {
        var ingredientCounts = new HashMap<ItemIdentifier, Integer>();

        for (ItemIdentifierStack ingredient : getIngredients(pattern)) {
            ItemIdentifier item = ingredient.getItem();
            ingredientCounts.putIfAbsent(item, 0);
            ingredientCounts.compute(item, (key, value) -> value + ingredient.getStackSize());
        }

        var result = new ArrayList<ItemIdentifierStack>();
        for (var entry : ingredientCounts.entrySet()) {
            result.add(new ItemIdentifierStack(entry.getKey(), entry.getValue()));
        }

        return result;
    }

    public static List<PatternFluidStack> getAggregatedFluidIngredients(ItemStack pattern) {
        var fluidCounts = new LinkedHashMap<FluidIdentifier, Integer>();
        for (PatternFluidStack ingredient : getFluidIngredients(pattern)) {
            fluidCounts.putIfAbsent(ingredient.getFluid(), 0);
            fluidCounts.compute(ingredient.getFluid(), (key, value) -> value + ingredient.getAmount());
        }

        var result = new ArrayList<PatternFluidStack>();
        for (var entry : fluidCounts.entrySet()) {
            result.add(new PatternFluidStack(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /**
     * Clears a given pattern
     * @param pattern the pattern
     */
    public static void clear(ItemStack pattern) {
        for (int i = 0; i < ITEM_SLOT_COUNT; i++) {
            setStackInSlot(pattern, i, null);
        }
        setFluidIngredients(pattern, new ArrayList<>());
        setFluidResults(pattern, new ArrayList<>());
    }

    /**
     * Multiplies the pattern with the given factor
     * @param pattern the pattern to change
     * @param factor the factor
     */
    public static void multiply(ItemStack pattern, int factor) {
        for (int i = 0; i < ITEM_SLOT_COUNT; i++) {
            ItemStack stack = getStackInSlot(pattern, i);
            if (stack != null) {
                stack.stackSize *= factor;
                setStackInSlot(pattern, i, stack);
            }
        }
        multiplyFluids(pattern, factor, true);
        multiplyFluids(pattern, factor, false);
    }

    /**
     * Returns the ItemStack that is in the specified slotId of a given pattern.
     * If the given slotId is out of bounds returns null.
     * @param pattern the pattern
     * @param slot the slotId
     * @return the ItemStack or null if not present
     */
    public static ItemStack getStackInSlot(ItemStack pattern, int slot) {
        if (pattern == null || slot < 0 || slot >= ITEM_SLOT_COUNT || !pattern.hasTagCompound()) {
            return null;
        }
        NBTTagList list = pattern.getTagCompound().getTagList(ITEMS_TAG, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getInteger("slot") == slot) {
                return ItemStack.loadItemStackFromNBT(tag);
            }
        }
        return null;
    }

    /**
     * Sets the ItemStack for a given slotId of a given pattern.
     * Always replaces the old ItemStack at the given slotId.
     * If the slotId is out of bounds returns without error.
     * @param pattern the pattern
     * @param slot the slotId to set
     * @param stack the new ItemStack the slot will be set to
     */
    public static void setStackInSlot(ItemStack pattern, int slot, ItemStack stack) {
        if (pattern == null || slot < 0 || slot >= ITEM_SLOT_COUNT) {
            return;
        }
        NBTTagCompound root = getOrCreateTag(pattern);
        NBTTagList oldList = root.getTagList(ITEMS_TAG, 10);
        NBTTagList newList = new NBTTagList();
        for (int i = 0; i < oldList.tagCount(); i++) {
            NBTTagCompound tag = oldList.getCompoundTagAt(i);
            if (tag.getInteger("slot") != slot) {
                newList.appendTag(tag);
            }
        }
        if (stack != null && stack.stackSize > 0) {
            NBTTagCompound tag = new NBTTagCompound();
            ItemStack saved = stack.copy();
            saved.stackSize = Math.max(1, saved.stackSize);
            saved.writeToNBT(tag);
            tag.setInteger("slot", slot);
            newList.appendTag(tag);
        }
        root.setTag(ITEMS_TAG, newList);
    }

    /**
     * @param pattern the pattern
     * @return the ingredients of a pattern, not aggregated.
     */
    public static List<ItemIdentifierStack> getIngredients(ItemStack pattern) {
        return readRange(pattern, 0, INGREDIENT_SLOTS);
    }

    /**
     * @param pattern the pattern
     * @return the results of a pattern, not aggregated.
     */
    public static List<ItemIdentifierStack> getResults(ItemStack pattern) {
        return readRange(pattern, INGREDIENT_SLOTS, ITEM_SLOT_COUNT);
    }

    public static PatternFluidStack getFluidInSlot(ItemStack pattern, int slot) {
        if (slot >= FLUID_INPUT_START && slot < FLUID_INPUT_START + FLUID_INPUT_SLOTS) {
            return readFluidSlot(pattern, FLUID_INPUTS_TAG, slot - FLUID_INPUT_START);
        }
        if (slot >= FLUID_RESULT_START && slot < FLUID_RESULT_START + FLUID_RESULT_SLOTS) {
            return readFluidSlot(pattern, FLUID_RESULTS_TAG, slot - FLUID_RESULT_START);
        }
        return null;
    }

    public static void setFluidInSlot(ItemStack pattern, int slot, PatternFluidStack stack) {
        if (slot >= FLUID_INPUT_START && slot < FLUID_INPUT_START + FLUID_INPUT_SLOTS) {
            setFluidSlot(pattern, FLUID_INPUTS_TAG, slot - FLUID_INPUT_START, stack);
        } else if (slot >= FLUID_RESULT_START && slot < FLUID_RESULT_START + FLUID_RESULT_SLOTS) {
            setFluidSlot(pattern, FLUID_RESULTS_TAG, slot - FLUID_RESULT_START, stack);
        }
    }

    public static List<PatternFluidStack> getFluidIngredients(ItemStack pattern) {
        return readFluidList(pattern, FLUID_INPUTS_TAG, FLUID_INPUT_SLOTS);
    }

    public static List<PatternFluidStack> getFluidResults(ItemStack pattern) {
        return readFluidList(pattern, FLUID_RESULTS_TAG, FLUID_RESULT_SLOTS);
    }

    public static void setFluidIngredients(ItemStack pattern, List<PatternFluidStack> fluids) {
        setFluidList(pattern, FLUID_INPUTS_TAG, fluids, FLUID_INPUT_SLOTS);
    }

    public static void setFluidResults(ItemStack pattern, List<PatternFluidStack> fluids) {
        setFluidList(pattern, FLUID_RESULTS_TAG, fluids, FLUID_RESULT_SLOTS);
    }

    public static ItemStack getPrimaryResultStack(ItemStack pattern) {
        List<ItemIdentifierStack> results = getResults(pattern);
        if (!results.isEmpty()) {
            return results.get(0).makeNormalStack();
        }
        List<PatternFluidStack> fluidResults = getFluidResults(pattern);
        if (!fluidResults.isEmpty()) {
            return fluidResults.get(0).makeDisplayStack().makeNormalStack();
        }
        return null;
    }

    public static boolean isConfigured(ItemStack pattern) {
        boolean hasInputs = !getIngredients(pattern).isEmpty() || !getFluidIngredients(pattern).isEmpty();
        boolean hasResults = !getResults(pattern).isEmpty() || !getFluidResults(pattern).isEmpty();
        return hasInputs && hasResults;
    }

    private static void addStacksToTooltip(List<String> tooltip, List<ItemIdentifierStack> stacks, ChatColor color) {
        for (ItemIdentifierStack stack : stacks) {
            ItemStack normalStack = stack.makeNormalStack();
            tooltip.add("  " + ChatColor.WHITE + normalStack.stackSize + " " + color + normalStack.getDisplayName());
        }
    }

    private static void addFluidsToTooltip(List<String> tooltip, List<PatternFluidStack> stacks, ChatColor color) {
        for (PatternFluidStack stack : stacks) {
            tooltip.add("  " + ChatColor.WHITE + stack.getAmount() + "mB " + color
                    + stack.makeFluidStack().getLocalizedName());
        }
    }

    private static List<ItemIdentifierStack> readRange(ItemStack pattern, int start, int end) {
        List<ItemIdentifierStack> stacks = new ArrayList<>();
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = getStackInSlot(pattern, slot);
            if (stack != null && stack.stackSize > 0) {
                stacks.add(ItemIdentifierStack.getFromStack(stack));
            }
        }
        return stacks;
    }

    private static PatternFluidStack readFluidSlot(ItemStack pattern, String key, int slot) {
        if (pattern == null || slot < 0 || !pattern.hasTagCompound()) {
            return null;
        }
        NBTTagList list = pattern.getTagCompound().getTagList(key, 10);
        if (slot >= list.tagCount()) {
            return null;
        }
        return PatternFluidStack.readFromNBT(list.getCompoundTagAt(slot));
    }

    private static void setFluidSlot(ItemStack pattern, String key, int slot, PatternFluidStack stack) {
        if (pattern == null || slot < 0) {
            return;
        }
        List<PatternFluidStack> fluids = readFluidList(pattern, key, key.equals(FLUID_INPUTS_TAG)
                ? FLUID_INPUT_SLOTS
                : FLUID_RESULT_SLOTS);
        while (fluids.size() <= slot) {
            fluids.add(null);
        }
        fluids.set(slot, stack);
        setFluidList(pattern, key, fluids, key.equals(FLUID_INPUTS_TAG) ? FLUID_INPUT_SLOTS : FLUID_RESULT_SLOTS);
    }

    private static List<PatternFluidStack> readFluidList(ItemStack pattern, String key, int maxSlots) {
        List<PatternFluidStack> fluids = new ArrayList<>();
        if (pattern == null || !pattern.hasTagCompound()) {
            return fluids;
        }
        NBTTagList list = pattern.getTagCompound().getTagList(key, 10);
        for (int i = 0; i < list.tagCount() && i < maxSlots; i++) {
            PatternFluidStack stack = PatternFluidStack.readFromNBT(list.getCompoundTagAt(i));
            if (stack != null && stack.getAmount() > 0) {
                fluids.add(stack);
            }
        }
        return fluids;
    }

    private static void setFluidList(ItemStack pattern, String key, List<PatternFluidStack> fluids, int maxSlots) {
        if (pattern == null) {
            return;
        }
        NBTTagCompound root = getOrCreateTag(pattern);
        NBTTagList list = new NBTTagList();
        for (PatternFluidStack stack : fluids) {
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }
            list.appendTag(stack.writeToNBT());
            if (list.tagCount() >= maxSlots) {
                break;
            }
        }
        root.setTag(key, list);
    }

    private static void multiplyFluids(ItemStack pattern, int factor, boolean inputs) {
        List<PatternFluidStack> fluids = inputs ? getFluidIngredients(pattern) : getFluidResults(pattern);
        List<PatternFluidStack> multiplied = new ArrayList<>();
        for (PatternFluidStack fluid : fluids) {
            multiplied.add(new PatternFluidStack(fluid.getFluid(), fluid.getAmount() * factor));
        }
        if (inputs) {
            setFluidIngredients(pattern, multiplied);
        } else {
            setFluidResults(pattern, multiplied);
        }
    }

    private static NBTTagCompound getOrCreateTag(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }

    @Override
    public void registerIcons(IIconRegister register) {
        itemIcon = register.registerIcon("logisticspipes:itemModule/ModuleCrafter");
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (MainProxy.isServer(world)) {
            NewGuiHandler.getGui(PatternGuiProvider.class).setInventorySlot(player.inventory.currentItem).open(player);
        }
        return stack;
    }

    @Override
    public boolean addShiftInfo() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        List<ItemIdentifierStack> results = getResults(stack);
        List<PatternFluidStack> fluidResults = getFluidResults(stack);
        if (results.isEmpty() && fluidResults.isEmpty()) {
            return;
        }
        tooltip.add(ChatColor.AQUA + "Results:");
        addStacksToTooltip(tooltip, results, ChatColor.DARK_BLUE);
        addFluidsToTooltip(tooltip, fluidResults, ChatColor.DARK_BLUE);
        if (!getIngredients(stack).isEmpty() || !getFluidIngredients(stack).isEmpty()) {
            StringUtils.addShiftAction(tooltip, () -> {
                tooltip.add(ChatColor.DARK_GREEN + "Ingredients:");
                addStacksToTooltip(tooltip, getAggregatedIngredients(stack), ChatColor.GREEN);
                addFluidsToTooltip(tooltip, getAggregatedFluidIngredients(stack), ChatColor.GREEN);
            });
        }
    }
}
