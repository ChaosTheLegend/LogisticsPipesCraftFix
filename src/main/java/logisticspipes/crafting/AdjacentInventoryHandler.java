package logisticspipes.crafting;

import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.resources.IResource;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.InventoryHelper;
import logisticspipes.utils.SidedInventoryMinecraftAdapter;
import logisticspipes.utils.WorldUtil;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.transactor.ITransactor;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.List;

class AdjacentInventoryHandler {

    private final ModuleItemCrafting module;
    private final PipeItemsPatternCraftingLogistics pipe;
    private final PatternHandler patternHandler;

    AdjacentInventoryHandler(ModuleItemCrafting module, PipeItemsPatternCraftingLogistics pipe, PatternHandler patternHandler) {
        this.module = module;
        this.pipe = pipe;
        this.patternHandler = patternHandler;
    }

    AdjacentTile getConnected() {
        WorldUtil worldUtil = new WorldUtil(pipe.getWorld(), pipe.getX(), pipe.getY(), pipe.getZ());
        for (AdjacentTile tile : worldUtil.getAdjacentTileEntities(true)) {
            if (tile.tile instanceof IInventory) return tile;
        }
        return null;
    }

    boolean isConnectedToPatternCraftingTable() {
        AdjacentTile connected = getConnected();
        return connected != null && connected.tile instanceof PatternLogisticsCraftingTableTileEntity;
    }

    List<AdjacentTile> locateInventories() {
        List<AdjacentTile> inventories = new ArrayList<>();
        WorldUtil worldUtil = new WorldUtil(pipe.getWorld(), pipe.getX(), pipe.getY(), pipe.getZ());
        for (AdjacentTile tile : worldUtil.getAdjacentTileEntities(true)) {
            if (tile.tile instanceof IInventory) {
                inventories.add(tile);
            }
        }
        return inventories;
    }

    int roomFor(ItemStack pattern, ItemIdentifier item) {
        AdjacentTile connected = getConnected();
        if (connected == null) return 0;
        return roomFor(connected, item);
    }

    int roomFor(AdjacentTile connected, ItemIdentifier item) {
        if (connected.tile instanceof PatternLogisticsCraftingTableTileEntity) {
            return ((PatternLogisticsCraftingTableTileEntity) connected.tile).roomForPatternPipeItem(item);
        }
        IInventory inventory = (IInventory) connected.tile;
        if (inventory instanceof net.minecraft.inventory.ISidedInventory) {
            inventory = new SidedInventoryMinecraftAdapter((net.minecraft.inventory.ISidedInventory) inventory, connected.orientation.getOpposite(), false);
        }
        IInventoryUtil inv = SimpleServiceLocator.inventoryUtilFactory.getInventoryUtil(inventory, module.getInsertionOrientation(connected));
        return inv.roomForItem(item, 9999);
    }

    int availablePatternSets(ItemStack pattern, boolean respectPatternSlots) {
        AdjacentTile connected = getConnected();
        if (connected == null || pattern == null) {
            return 0;
        }
        if (connected.tile instanceof PatternLogisticsCraftingTableTileEntity) {
            return availablePatternSetsForPatternTable(pattern, (PatternLogisticsCraftingTableTileEntity) connected.tile);
        }
        if (respectPatternSlots) {
            return availablePatternSetsRespectingSlots(pattern, connected);
        }
        return availablePatternSetsDisregardingSlots(pattern, connected);
    }

    boolean insertPatternSets(ItemStack pattern, int sets, boolean respectPatternSlots) {
        if (sets <= 0) {
            return false;
        }
        AdjacentTile connected = getConnected();
        if (connected != null && connected.tile instanceof PatternLogisticsCraftingTableTileEntity) {
            return ((PatternLogisticsCraftingTableTileEntity) connected.tile).insertPatternFromPatternPipe(pattern, sets);
        }
        if (respectPatternSlots) {
            return insertPatternSetsRespectingSlots(pattern, sets);
        }
        for (ItemIdentifierStack ingredient : patternHandler.getAggregatedIngredients(pattern)) {
            ItemIdentifierStack stack = new ItemIdentifierStack(ingredient.getItem(), ingredient.getStackSize() * sets);
            if (insert(pattern, stack) != stack.getStackSize()) {
                return false;
            }
        }
        return true;
    }

    private int availablePatternSetsRespectingSlots(ItemStack pattern, AdjacentTile connected) {
        IInventory inventory = (IInventory) connected.tile;
        int sets = Integer.MAX_VALUE;
        boolean hasIngredient = false;
        for (int slot = 0; slot < Pattern.INGREDIENT_SLOTS; slot++) {
            ItemStack ingredient = Pattern.getStackInSlot(pattern, slot);
            if (ingredient == null) {
                continue;
            }
            hasIngredient = true;
            if (slot >= inventory.getSizeInventory()) {
                return 0;
            }
            if (!canInsertIntoSlot(inventory, connected, slot, ingredient)) {
                return 0;
            }
            ItemStack existing = inventory.getStackInSlot(slot);
            if (existing != null && !ItemIdentifier.get(existing).equalsForCrafting(ItemIdentifier.get(ingredient))) {
                return 0;
            }
            int stored = existing != null ? existing.stackSize : 0;
            int room = Math.min(inventory.getInventoryStackLimit(), ingredient.getMaxStackSize()) - stored;
            sets = Math.min(sets, room / ingredient.stackSize);
        }
        return hasIngredient ? Math.max(0, sets) : 0;
    }

    private int availablePatternSetsDisregardingSlots(ItemStack pattern, AdjacentTile connected) {
        List<ItemIdentifierStack> ingredients = patternHandler.getAggregatedIngredients(pattern);
        if (ingredients.isEmpty()) {
            return 0;
        }
        int upperBound = Integer.MAX_VALUE;
        for (ItemIdentifierStack ingredient : ingredients) {
            upperBound = Math.min(upperBound, roomFor(connected, ingredient.getItem()) / ingredient.getStackSize());
        }
        if (upperBound <= 0 || upperBound == Integer.MAX_VALUE) {
            return 0;
        }
        IInventory inventory = getInsertableInventory(connected);
        int low = 0;
        int high = upperBound;
        while (low < high) {
            int mid = low + (high - low + 1) / 2;
            if (canFitPatternSetsDisregardingSlots(inventory, ingredients, mid)) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    private IInventory getInsertableInventory(AdjacentTile connected) {
        IInventory inventory = (IInventory) connected.tile;
        if (inventory instanceof net.minecraft.inventory.ISidedInventory) {
            return new SidedInventoryMinecraftAdapter((net.minecraft.inventory.ISidedInventory) inventory, connected.orientation.getOpposite(), false);
        }
        return inventory;
    }

    private boolean canFitPatternSetsDisregardingSlots(IInventory inventory, List<ItemIdentifierStack> ingredients, int sets) {
        ItemStack[] snapshot = new ItemStack[inventory.getSizeInventory()];
        for (int i = 0; i < snapshot.length; i++) {
            ItemStack existing = inventory.getStackInSlot(i);
            snapshot[i] = existing == null ? null : existing.copy();
        }
        for (ItemIdentifierStack ingredient : ingredients) {
            ItemStack stack = ingredient.makeNormalStack();
            stack.stackSize = ingredient.getStackSize() * sets;
            if (!insertIntoSnapshot(inventory, snapshot, stack)) {
                return false;
            }
        }
        return true;
    }

    private boolean insertIntoSnapshot(IInventory inventory, ItemStack[] snapshot, ItemStack stack) {
        int remaining = stack.stackSize;
        for (int i = 0; i < snapshot.length && remaining > 0; i++) {
            ItemStack existing = snapshot[i];
            if (existing == null || !ItemIdentifier.get(existing).equalsForCrafting(ItemIdentifier.get(stack))) {
                continue;
            }
            int room = Math.min(inventory.getInventoryStackLimit(), existing.getMaxStackSize()) - existing.stackSize;
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, remaining);
            existing.stackSize += moved;
            remaining -= moved;
        }
        for (int i = 0; i < snapshot.length && remaining > 0; i++) {
            if (snapshot[i] != null || !inventory.isItemValidForSlot(i, stack)) {
                continue;
            }
            int moved = Math.min(remaining, Math.min(inventory.getInventoryStackLimit(), stack.getMaxStackSize()));
            ItemStack inserted = stack.copy();
            inserted.stackSize = moved;
            snapshot[i] = inserted;
            remaining -= moved;
        }
        return remaining <= 0;
    }

    private int availablePatternSetsForPatternTable(ItemStack pattern, PatternLogisticsCraftingTableTileEntity table) {
        int sets = Integer.MAX_VALUE;
        boolean hasIngredient = false;
        for (int slot = 0; slot < Pattern.INGREDIENT_SLOTS; slot++) {
            ItemStack ingredient = Pattern.getStackInSlot(pattern, slot);
            if (ingredient == null) {
                continue;
            }
            hasIngredient = true;
            int room = table.roomForPatternPipeSlot(slot, ingredient);
            sets = Math.min(sets, room / ingredient.stackSize);
        }
        return hasIngredient ? Math.max(0, sets) : 0;
    }

    private boolean insertPatternSetsRespectingSlots(ItemStack pattern, int sets) {
        AdjacentTile connected = getConnected();
        if (connected == null || pattern == null) return false;

        if (connected.tile instanceof PatternLogisticsCraftingTableTileEntity) {
            return insertPatternSetsIntoPatternTable(pattern, sets, (PatternLogisticsCraftingTableTileEntity) connected.tile);
        }
        IInventory inventory = (IInventory) connected.tile;
        for (int slot = 0; slot < Pattern.INGREDIENT_SLOTS; slot++) {
            ItemStack ingredient = Pattern.getStackInSlot(pattern, slot);
            if (ingredient == null) {
                continue;
            }
            if (slot >= inventory.getSizeInventory()) {
                return false;
            }
            if (!canInsertIntoSlot(inventory, connected, slot, ingredient)) {
                return false;
            }
            ItemStack existing = inventory.getStackInSlot(slot);
            if (existing != null && !ItemIdentifier.get(existing).equalsForCrafting(ItemIdentifier.get(ingredient))) {
                return false;
            }
            int amount = ingredient.stackSize * sets;
            int stored = existing != null ? existing.stackSize : 0;
            int room = Math.min(inventory.getInventoryStackLimit(), ingredient.getMaxStackSize()) - stored;
            if (room < amount) {
                return false;
            }
            ItemStack toStore = ingredient.copy();
            toStore.stackSize = stored + amount;
            inventory.setInventorySlotContents(slot, toStore);
        }
        inventory.markDirty();
        return true;
    }

    private boolean insertPatternSetsIntoPatternTable(ItemStack pattern, int sets, PatternLogisticsCraftingTableTileEntity table) {
        return table.insertPatternFromPatternPipe(pattern, sets);
    }

    private boolean canInsertIntoSlot(IInventory inventory, AdjacentTile connected, int slot, ItemStack stack) {
        if (!inventory.isItemValidForSlot(slot, stack)) {
            return false;
        }
        if (inventory instanceof net.minecraft.inventory.ISidedInventory) {
            return ((net.minecraft.inventory.ISidedInventory) inventory).canInsertItem(slot, stack, connected.orientation.getOpposite().ordinal());
        }
        return true;
    }

    private int insert(ItemStack pattern, ItemIdentifierStack item) {
        AdjacentTile connected = getConnected();
        if (connected == null || item.getStackSize() <= 0) {
            return 0;
        }
        int amount = item.getStackSize();
        if (module.getBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING && module.getRunningCraftForHandler() >= 0) {
            amount = Math.min(amount, missingFor(pattern, item.getItem()));
        }
        if (amount <= 0) {
            return 0;
        }
        ItemStack toInsert = item.makeNormalStack();
        toInsert.stackSize = amount;
        if (connected.tile instanceof PatternLogisticsCraftingTableTileEntity) {
            return ((PatternLogisticsCraftingTableTileEntity) connected.tile).insertFromPatternPipe(toInsert);
        }
        ITransactor transactor = InventoryHelper.getTransactorFor(connected.tile, connected.orientation.getOpposite());
        if (transactor == null) {
            return 0;
        }
        ItemStack added = transactor.add(toInsert, module.getInsertionOrientation(connected), true);
        return added != null ? added.stackSize : 0;
    }

    private int amountOf(ItemIdentifier item) {
        AdjacentTile connected = getConnected();
        if (connected == null) {
            return 0;
        }
        IInventory inventory = (IInventory) connected.tile;
        int amount = 0;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack != null && ItemIdentifier.get(stack).equalsForCrafting(item)) {
                amount += stack.stackSize;
            }
        }
        return amount;
    }

    private int missingFor(ItemStack pattern, ItemIdentifier item) {
        return Math.max(0, patternHandler.ingredientAmount(pattern, item) - amountOf(item));
    }

    boolean isEmpty(AdjacentTile connected) {
        if (connected == null || !(connected.tile instanceof IInventory)) {
            return true;
        }
        if (connected.tile instanceof PatternLogisticsCraftingTableTileEntity) {
            return ((PatternLogisticsCraftingTableTileEntity) connected.tile).isIdle();
        }
        IInventory inventory = (IInventory) connected.tile;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack != null && stack.stackSize > 0) {
                return false;
            }
        }
        return true;
    }

    ItemStack extract(AdjacentTile tile, IResource wanted, int count) {
        if (tile.tile instanceof PatternLogisticsCraftingTableTileEntity) {
            if (!pipe.useEnergy(Math.min(count, wanted.getRequestedAmount()))) {
                return null;
            }
            return ((PatternLogisticsCraftingTableTileEntity) tile.tile).extractOutput(wanted, count);
        }
        IInventory inventory = (IInventory) tile.tile;
        if (inventory instanceof net.minecraft.inventory.ISidedInventory) {
            inventory = new SidedInventoryMinecraftAdapter((net.minecraft.inventory.ISidedInventory) inventory, ForgeDirection.UNKNOWN, true);
        }
        IInventoryUtil util = SimpleServiceLocator.inventoryUtilFactory.getInventoryUtil(inventory, tile.orientation);
        ItemIdentifier item = wanted.getAsItem();
        int available = util.itemCount(item);
        if (available <= 0 || !pipe.useEnergy(Math.min(count, available))) {
            return null;
        }
        return util.getMultipleItems(item, Math.min(count, available));
    }
}
