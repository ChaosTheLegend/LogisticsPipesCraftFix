package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.world.World;

import logisticspipes.items.LogisticsItem;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.item.ItemIdentifierStack;

public class Pattern extends LogisticsItem {

    public static final int INGREDIENT_SLOTS = 9;
    public static final int RESULT_SLOTS = 3;
    public static final int SLOT_COUNT = INGREDIENT_SLOTS + RESULT_SLOTS;
    private static final String ITEMS_TAG = "patternItems";
    private static final String TYPE_TAG = "patternType";

    public enum PatternType {
        CRAFTING,
        PROCESSING
    }

    public Pattern() {
        setMaxStackSize(1);
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

    public static List<ItemIdentifierStack> getIngredients(ItemStack pattern) {
        return readRange(pattern, 0, INGREDIENT_SLOTS);
    }

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

    public static PatternType getPatternType(ItemStack pattern) {
        if (pattern == null || !pattern.hasTagCompound()) {
            return PatternType.CRAFTING;
        }
        PatternType[] values = PatternType.values();
        int type = pattern.getTagCompound().getInteger(TYPE_TAG);
        return values[Math.max(0, Math.min(values.length - 1, type))];
    }

    public static void setPatternType(ItemStack pattern, PatternType type) {
        if (pattern == null || type == null) {
            return;
        }
        getOrCreateTag(pattern).setInteger(TYPE_TAG, type.ordinal());
    }

    public static PatternType getNextPatternType(ItemStack pattern) {
        PatternType[] values = PatternType.values();
        return values[(getPatternType(pattern).ordinal() + 1) % values.length];
    }

    public static boolean respectsIngredientSlots(ItemStack pattern) {
        return getPatternType(pattern) == PatternType.CRAFTING;
    }

    public static boolean isConfigured(ItemStack pattern) {
        return !getIngredients(pattern).isEmpty() && !getResults(pattern).isEmpty();
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
}
