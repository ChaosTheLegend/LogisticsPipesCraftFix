package logisticspipes.crafting;

import logisticspipes.interfaces.routing.ISaveState;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class ModuleItemCrafting extends LogisticsGuiModule {

    private final CraftingPatternHolder craftingPatterns = new CraftingPatternHolder();
    private final ModuleItemCraftingStorage craftingStorage = new ModuleItemCraftingStorage();
    private SinkReply baseSinkReply;
    private TileEntity connectedEntity;

    protected void addCraftingPattern(Pattern pattern) {
        craftingPatterns.addPattern(pattern);
    }

    @Override
    protected ModuleCoordinatesGuiProvider getPipeGuiProvider() {
        return null;
    }

    @Override
    protected ModuleInHandGuiProvider getInHandGuiProvider() {
        return null;
    }

    @Override
    public SinkReply sinksItem(ItemIdentifier stack, int bestPriority, int bestCustomPriority, boolean allowDefault, boolean includeInTransit) {
        return craftingPatterns.acceptsItem(stack);
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    @Override
    public void tick() {

    }

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    @Override
    public Collection<ItemIdentifier> getSpecificInterests() {
        return List.of();
    }

    @Override
    public boolean interestedInAttachedInventory() {
        return false;
    }

    @Override
    public boolean interestedInUndamagedID() {
        return false;
    }

    @Override
    public boolean recievePassive() {
        return false;
    }

    @Override
    public IIcon getIconTexture(IIconRegister register) {
        return null;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {

    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {

    }

    @Override
    public void registerPosition(ModulePositionType slot, int positionInt) {
        super.registerPosition(slot, positionInt);
        baseSinkReply = new SinkReply(
            SinkReply.FixedPriority.ItemSink,0,true,false,1,0, new PipeLogisticsChassi.ChassiTargetInformation(getPositionInt())
        );
    }

    private static class ModuleItemCraftingStorage implements ISaveState {
        protected List<ItemIdentifierStack> stacks = new ArrayList<>();

        /**
         * Adds an itemStack to the inventory.
         * If it already contains this stack, merges it
         * @param stack the stack to add
         */
        void addItemStack(ItemIdentifierStack stack) {
            for (ItemIdentifierStack s : stacks) {
                if (s.getItem().equals(stack.getItem())) {
                    s.setStackSize(stack.getStackSize() + s.getStackSize());
                    return;
                }
            }
            stacks.add(stack);
        }

        /**
         * removes the item stack from this storage with the same ItemIdentifier
         * @param itemIdentifier the identifier
         * @return the stack
         */
        ItemIdentifierStack getStack(ItemIdentifier itemIdentifier) {
            return getStack(itemIdentifier, Integer.MAX_VALUE);
        }

        /**
         * removes the item stack with a maximum size
         * @param itemIdentifier the identifier
         * @param maxStackSize the maximum number of items to remove
         * @return the stack
         */
        ItemIdentifierStack getStack(ItemIdentifier itemIdentifier, int maxStackSize) {
            ItemIdentifierStack foundStack = null;
            for (ItemIdentifierStack stack : stacks) {
                if (stack.getItem().equals(itemIdentifier)) {
                    foundStack = stack;
                    break;
                }
            }

            if (foundStack == null) return null;
            if (foundStack.getStackSize() < maxStackSize) return foundStack;

            // we have more items in the inventory than we want to retrieve
            // split the stack into a new one we return, and remove the count in the inventory
            foundStack.setStackSize(foundStack.getStackSize() - maxStackSize);
            var stackToReturn = foundStack.clone();
            stackToReturn.setStackSize(maxStackSize);
            return stackToReturn;
        }

        @Override
        public void readFromNBT(NBTTagCompound nbttagcompound) {
            var tagList = nbttagcompound.getTagList("items", nbttagcompound.getId());

            for (int i = 0; i < tagList.tagCount(); i++) {
                var tag = tagList.getCompoundTagAt(i);
                var stack = ItemStack.loadItemStackFromNBT(tag);
                if (stack == null) continue;

                var idStack = ItemIdentifierStack.getFromStack(stack);
                stacks.add(idStack);
            }
        }

        @Override
        public void writeToNBT(NBTTagCompound nbttagcompound) {
            var tagList = new NBTTagList();
            for (ItemIdentifierStack stack : stacks) {
                var tag = new NBTTagCompound();
                stack.unsafeMakeNormalStack().writeToNBT(tag);
                tagList.appendTag(tag);
            }
            nbttagcompound.setTag("items", tagList);
        }
    }

    private static class CraftingPatternHolder implements ISaveState {
        List<Pattern> patterns = new ArrayList<>();
        HashSet<ItemIdentifier> acceptedItemIdentifier = new HashSet<>();
        HashSet<ItemIdentifier> producedItemIdentifier = new HashSet<>();

        /**
         * Adds a crafting pattern to this Holder
         * @param pattern the pattern to add
         */
        void addPattern(Pattern pattern) {
            patterns.add(pattern);
            acceptedItemIdentifier.addAll(pattern.ingredients.stream().map(ItemIdentifierStack::getItem).toList());
            producedItemIdentifier.addAll(pattern.results.stream().map(ItemIdentifierStack::getItem).toList());
        }

        /**
         * Returns true if there is a pattern that accepts the given itemIdentifier
         * @param itemIdentifier the itemIdentifier
         * @return true if accepts, otherwise false
         */
        boolean acceptsItem(ItemIdentifier itemIdentifier) {
            return acceptedItemIdentifier.contains(itemIdentifier);
        }

        /**
         * Returns true if there is a pattern that produces the given itemIdentifier
         * @param itemIdentifier the itemIdentifier
         * @return true if produces, otherwise false
         */
        boolean producesItem(ItemIdentifier itemIdentifier) {
            return producedItemIdentifier.contains(itemIdentifier);
        }

        @Override
        public void readFromNBT(NBTTagCompound nbttagcompound) {

        }

        @Override
        public void writeToNBT(NBTTagCompound nbttagcompound) {

        }
    }
}
