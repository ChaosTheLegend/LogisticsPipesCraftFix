package logisticspipes.crafting;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.items.LogisticsItem;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.proxy.MainProxy;
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
import java.util.List;

public class Pattern extends LogisticsItem {

    public static final int INGREDIENT_SLOTS = 9;
    public static final int RESULT_SLOTS = 3;
    public static final int SLOT_COUNT = INGREDIENT_SLOTS + RESULT_SLOTS;
    private static final String ITEMS_TAG = "patternItems";

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

    /**
     * Clears a given pattern
     * @param pattern the pattern
     */
    public static void clear(ItemStack pattern) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            setStackInSlot(pattern, i, null);
        }
    }

    /**
     * Multiplies the pattern with the given factor
     * @param pattern the pattern to change
     * @param factor the factor
     */
    public static void multiply(ItemStack pattern, int factor) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            getStackInSlot(pattern, i).stackSize *= factor;
        }
    }

    /**
     * Returns the ItemStack that is in the specified slotId of a given pattern.
     * If the given slotId is out of bounds returns null.
     * @param pattern the pattern
     * @param slot the slotId
     * @return the ItemStack or null if not present
     */
    public static ItemStack getStackInSlot(ItemStack pattern, int slot) {
        if (pattern == null || slot < 0 || slot >= SLOT_COUNT || !pattern.hasTagCompound()) {
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
        if (pattern == null || slot < 0 || slot >= SLOT_COUNT) {
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
        return readRange(pattern, INGREDIENT_SLOTS, SLOT_COUNT);
    }

    public static ItemStack getPrimaryResultStack(ItemStack pattern) {
        List<ItemIdentifierStack> results = getResults(pattern);
        if (results.isEmpty()) {
            return null;
        }
        return results.get(0).makeNormalStack();
    }

    public static boolean isConfigured(ItemStack pattern) {
        return !getIngredients(pattern).isEmpty() && !getResults(pattern).isEmpty();
    }

    private static void addStacksToTooltip(List<String> tooltip, List<ItemIdentifierStack> stacks, ChatColor color) {
        for (ItemIdentifierStack stack : stacks) {
            ItemStack normalStack = stack.makeNormalStack();
            tooltip.add("  " + ChatColor.WHITE + normalStack.stackSize + " " + color + normalStack.getDisplayName());
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
        if (results.isEmpty()) {
            return;
        }
        tooltip.add(ChatColor.AQUA + "Results:");
        addStacksToTooltip(tooltip, results, ChatColor.DARK_BLUE);
        if (!getIngredients(stack).isEmpty()) {
            StringUtils.addShiftAction(tooltip, () -> {
                tooltip.add(ChatColor.DARK_GREEN + "Ingredients:");
                addStacksToTooltip(tooltip, getAggregatedIngredients(stack), ChatColor.GREEN);
            });
        }
    }
}
