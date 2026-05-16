package logisticspipes.crafting;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import logisticspipes.utils.item.ItemIdentifierStack;

public class PatternSolidStack implements IPatternStack {

    private final ItemIdentifierStack stack;

    public PatternSolidStack(ItemIdentifierStack stack) {
        this.stack = stack;
    }

    public static PatternSolidStack fromItemStack(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return null;
        }
        return new PatternSolidStack(ItemIdentifierStack.getFromStack(stack));
    }

    public static PatternSolidStack readFromNBT(NBTTagCompound tag) {
        ItemStack stack = ItemStack.loadItemStackFromNBT(tag);
        return fromItemStack(stack);
    }

    public ItemIdentifierStack getItem() {
        return stack;
    }

    @Override
    public int getAmount() {
        return stack.getStackSize();
    }

    @Override
    public void addAmount(int amount) {
        stack.setStackSize(stack.getStackSize() + amount);
    }

    @Override
    public boolean canMerge(IPatternStack other) {
        return other instanceof PatternSolidStack
                && stack.getItem().equalsForCrafting(((PatternSolidStack) other).stack.getItem());
    }

    @Override
    public PatternSolidStack copy() {
        return new PatternSolidStack(stack.clone());
    }

    @Override
    public ItemStack makePatternStack() {
        return stack.makeNormalStack();
    }

    @Override
    public ItemStack makeDisplayItemStack() {
        return makePatternStack();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        makePatternStack().writeToNBT(tag);
        tag.setString(TYPE_TAG, TYPE_SOLID);
    }

    @Override
    public String toString() {
        return stack.toString();
    }
}
