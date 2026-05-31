package logisticspipes.crafting.requesttable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.config.Configs;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Fixed-slot fluid storage for the new request table.
 * <p>
 * The storage exposes an {@link IInventory} view for GUI synchronization. The returned item stacks are display
 * containers only; the authoritative data stays in the {@link FluidStack} array.
 */
public class RequestTableFluidStorage implements IInventory {

    private static final String NBT_FLUIDS = "fluids";
    private static final String NBT_INDEX = "index";
    private static final String NBT_SIZE = "size";
    private static final String NBT_CAPACITY = "capacity";

    private FluidStack[] fluids;
    private int slotCapacity;
    private final String name;

    /**
     * Creates a new fixed-size fluid storage.
     *
     * @param size         number of visible fluid slots
     * @param name         inventory name used by Minecraft's container sync
     * @param slotCapacity capacity of each fluid slot in millibuckets
     */
    public RequestTableFluidStorage(int size, String name, int slotCapacity) {
        this.fluids = new FluidStack[size];
        this.name = name;
        this.slotCapacity = slotCapacity;
    }

    /**
     * Creates the default one-row request-table fluid storage.
     */
    public static RequestTableFluidStorage createDefault() {
        return new RequestTableFluidStorage(9, "Request Table Fluids", Configs.MAX_LOGISTICS_FLUID_TRANSPORT_INNER_CAPACITY);
    }

    /**
     * Resizes the storage and adjusts the capacity of each fluid slot.
     *
     * @param size            new slot count
     * @param newSlotCapacity new capacity per slot in millibuckets
     * @param world           world used to drop overflow, or {@code null}
     * @param x               pipe x-coordinate for dropped overflow
     * @param y               pipe y-coordinate for dropped overflow
     * @param z               pipe z-coordinate for dropped overflow
     */
    public void resize(int size, int newSlotCapacity, World world, int x, int y, int z) {
        if (size < 0) {
            throw new IllegalArgumentException("Fluid storage size cannot be negative");
        }
        int capacity = Math.max(1, newSlotCapacity);
        if (fluids.length == size && slotCapacity == capacity) {
            return;
        }
        FluidStack[] oldFluids = fluids;
        fluids = new FluidStack[size];
        slotCapacity = capacity;
        for (FluidStack fluid : oldFluids) {
            if (fluid == null || fluid.amount <= 0) {
                continue;
            }
            FluidStack moving = fluid.copy();
            int accepted = fill(moving, true);
            if (accepted < moving.amount) {
                FluidStack overflow = moving.copy();
                overflow.amount -= accepted;
                dropFluid(world, x, y, z, overflow);
            }
        }
        markDirty();
    }

    /**
     * @return capacity of a single fluid slot in millibuckets
     */
    public int getSlotCapacity() {
        return slotCapacity;
    }

    /**
     * Attempts to store fluid in matching slots first and then in empty slots.
     *
     * @param stack  fluid to insert
     * @param doFill whether the storage should be mutated
     * @return the amount that was accepted
     */
    public int fill(FluidStack stack, boolean doFill) {
        if (stack == null || stack.amount <= 0) {
            return 0;
        }
        int remaining = stack.amount;
        remaining = fillExisting(stack, remaining, doFill);
        remaining = fillEmpty(stack, remaining, doFill);
        if (doFill && remaining != stack.amount) {
            markDirty();
        }
        return stack.amount - remaining;
    }

    /**
     * Attempts to fill exactly one storage slot.
     *
     * @param slot   target slot
     * @param stack  fluid to insert
     * @param doFill whether the storage should be mutated
     * @return amount accepted by the slot
     */
    public int fillSlot(int slot, FluidStack stack, boolean doFill) {
        if (slot < 0 || slot >= fluids.length || stack == null || stack.amount <= 0) {
            return 0;
        }
        FluidStack current = fluids[slot];
        if (current != null && !FluidIdentifier.get(current).equals(FluidIdentifier.get(stack))) {
            return 0;
        }
        int stored = current == null ? 0 : current.amount;
        int accepted = Math.min(slotCapacity - stored, stack.amount);
        if (accepted <= 0) {
            return 0;
        }
        if (doFill) {
            if (current == null) {
                fluids[slot] = stack.copy();
                fluids[slot].amount = accepted;
            } else {
                current.amount += accepted;
            }
            markDirty();
        }
        return accepted;
    }

    private int fillExisting(FluidStack stack, int remaining, boolean doFill) {
        for (int i = 0; i < fluids.length && remaining > 0; i++) {
            FluidStack current = fluids[i];
            if (current == null || !FluidIdentifier.get(current).equals(FluidIdentifier.get(stack))) {
                continue;
            }
            int accepted = Math.min(slotCapacity - current.amount, remaining);
            if (accepted <= 0) {
                continue;
            }
            if (doFill) {
                current.amount += accepted;
            }
            remaining -= accepted;
        }
        return remaining;
    }

    private int fillEmpty(FluidStack stack, int remaining, boolean doFill) {
        for (int i = 0; i < fluids.length && remaining > 0; i++) {
            if (fluids[i] != null) {
                continue;
            }
            int accepted = Math.min(slotCapacity, remaining);
            if (doFill) {
                fluids[i] = stack.copy();
                fluids[i].amount = accepted;
            }
            remaining -= accepted;
        }
        return remaining;
    }

    /**
     * Drains fluid from one slot.
     *
     * @param slot    storage slot
     * @param amount  maximum amount to drain
     * @param doDrain whether the storage should be mutated
     * @return the drained fluid stack, or {@code null}
     */
    public FluidStack drain(int slot, int amount, boolean doDrain) {
        if (slot < 0 || slot >= fluids.length || amount <= 0 || fluids[slot] == null) {
            return null;
        }
        FluidStack drained = fluids[slot].copy();
        drained.amount = Math.min(amount, drained.amount);
        if (doDrain) {
            fluids[slot].amount -= drained.amount;
            if (fluids[slot].amount <= 0) {
                fluids[slot] = null;
            }
            markDirty();
        }
        return drained;
    }

    /**
     * @param slot storage slot
     * @return a defensive copy of the stored fluid, or {@code null}
     */
    public FluidStack getFluid(int slot) {
        if (slot < 0 || slot >= fluids.length || fluids[slot] == null) {
            return null;
        }
        return fluids[slot].copy();
    }

    /**
     * @return currently stored fluid amount in millibuckets
     */
    public int getStoredAmount() {
        int stored = 0;
        for (FluidStack fluid : fluids) {
            if (fluid != null) {
                stored += fluid.amount;
            }
        }
        return stored;
    }

    /**
     * @return total fluid capacity in millibuckets
     */
    public int getTotalCapacity() {
        return fluids.length * slotCapacity;
    }

    /**
     * Drops all stored fluids as LogisticsPipes fluid container items.
     */
    public void dropContents(World world, int x, int y, int z) {
        for (int i = 0; i < fluids.length; i++) {
            dropFluid(world, x, y, z, fluids[i]);
            fluids[i] = null;
        }
        markDirty();
    }

    private void dropFluid(World world, int x, int y, int z, FluidStack fluid) {
        if (world == null || fluid == null || fluid.amount <= 0) {
            return;
        }
        ItemStack stack = SimpleServiceLocator.logisticsFluidManager.getFluidContainer(fluid).makeNormalStack();
        ItemIdentifierInventory.dropItems(world, stack, x, y, z);
    }

    /**
     * Reads the storage from NBT.
     */
    public void readFromNBT(NBTTagCompound tag, String prefix) {
        if (tag.hasKey(prefix + NBT_SIZE)) {
            resize(tag.getInteger(prefix + NBT_SIZE), tag.getInteger(prefix + NBT_CAPACITY), null, 0, 0, 0);
        }
        NBTTagList list = tag.getTagList(prefix + NBT_FLUIDS, tag.getId());
        for (int i = 0; i < fluids.length; i++) {
            fluids[i] = null;
        }
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound fluidTag = list.getCompoundTagAt(i);
            int index = fluidTag.getInteger(NBT_INDEX);
            if (index >= 0 && index < fluids.length) {
                fluids[index] = FluidStack.loadFluidStackFromNBT(fluidTag);
            }
        }
    }

    /**
     * Writes the storage to NBT.
     */
    public void writeToNBT(NBTTagCompound tag, String prefix) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < fluids.length; i++) {
            if (fluids[i] == null || fluids[i].amount <= 0) {
                continue;
            }
            NBTTagCompound fluidTag = new NBTTagCompound();
            fluids[i].writeToNBT(fluidTag);
            fluidTag.setInteger(NBT_INDEX, i);
            list.appendTag(fluidTag);
        }
        tag.setTag(prefix + NBT_FLUIDS, list);
        tag.setInteger(prefix + NBT_SIZE, fluids.length);
        tag.setInteger(prefix + NBT_CAPACITY, slotCapacity);
    }

    @Override
    public int getSizeInventory() {
        return fluids.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        FluidStack fluid = getFluid(slot);
        if (fluid == null) {
            return null;
        }
        return SimpleServiceLocator.logisticsFluidManager.getFluidContainer(fluid).makeNormalStack();
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= fluids.length) {
            return;
        }
        if (stack == null) {
            fluids[slot] = null;
            markDirty();
            return;
        }
        FluidStack fluid = SimpleServiceLocator.logisticsFluidManager
                .getFluidFromContainer(ItemIdentifierStack.getFromStack(stack));
        fluids[slot] = fluid == null || fluid.amount <= 0 ? null : fluid;
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return name;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return true;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public void markDirty() {}

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return false;
    }
}
