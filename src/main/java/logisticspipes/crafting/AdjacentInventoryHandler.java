package logisticspipes.crafting;

import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.resources.IResource;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.InventoryHelper;
import logisticspipes.utils.SidedInventoryMinecraftAdapter;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.transactor.ITransactor;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

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
        return pipe.getConnectedInventoryTile();
    }

    boolean isConnectedToPatternCraftingTable() {
        AdjacentTile connected = getConnected();
        return connected != null && connected.tile instanceof PatternLogisticsCraftingTableTileEntity;
    }

    List<AdjacentTile> locateInventories() {
        List<AdjacentTile> inventories = new ArrayList<>();
        AdjacentTile connected = getConnected();
        if (connected != null && connected.tile instanceof IInventory) {
            inventories.add(connected);
        }
        return inventories;
    }

    List<AdjacentTile> locateFluidHandlers() {
        List<AdjacentTile> handlers = new ArrayList<>();
        AdjacentTile connected = getConnected();
        if (connected != null && connected.tile instanceof IFluidHandler) {
            handlers.add(connected);
        }
        return handlers;
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

    int availablePatternSets(ItemStack pattern) {
        AdjacentTile connected = getConnected();
        if (connected == null || pattern == null) {
            return 0;
        }
        int sets = Integer.MAX_VALUE;
        boolean hasIngredient = false;
        List<ItemIdentifierStack> localIngredients = module.getLocalAggregatedIngredients(pattern);
        if (!localIngredients.isEmpty()) {
            hasIngredient = true;
            if (connected.tile instanceof PatternLogisticsCraftingTableTileEntity) {
                sets = Math.min(
                        sets,
                        availablePatternSetsForPatternTable(
                                pattern,
                                (PatternLogisticsCraftingTableTileEntity) connected.tile));
            } else if (connected.tile instanceof IInventory) {
                sets = Math.min(sets, availablePatternSetsDisregardingSlots(localIngredients, connected));
            } else {
                return 0;
            }
        }
        if (!patternHandler.getAggregatedFluidIngredients(pattern).isEmpty()) {
            hasIngredient = true;
            if (!(connected.tile instanceof IFluidHandler)) {
                return 0;
            }
            sets = Math.min(sets, availablePatternSetsForFluids(pattern, connected));
        }
        return hasIngredient && sets != Integer.MAX_VALUE ? Math.max(0, sets) : 0;
    }

    boolean insertPatternSets(ItemStack pattern, int sets) {
        if (sets <= 0) {
            return false;
        }
        AdjacentTile connected = getConnected();
        if (connected != null
                && connected.tile instanceof PatternLogisticsCraftingTableTileEntity
                && !module.hasLinkedSatelliteAssignments(pattern)
                && patternHandler.getAggregatedFluidIngredients(pattern).isEmpty()) {
            return ((PatternLogisticsCraftingTableTileEntity) connected.tile).insertPatternFromPatternPipe(pattern, sets);
        }
        for (ItemIdentifierStack ingredient : module.getLocalAggregatedIngredients(pattern)) {
            ItemIdentifierStack stack = new ItemIdentifierStack(ingredient.getItem(), ingredient.getStackSize() * sets);
            if (insert(pattern, stack) != stack.getStackSize()) {
                return false;
            }
        }
        for (PatternFluidStack ingredient : patternHandler.getAggregatedFluidIngredients(pattern)) {
            PatternFluidStack stack = new PatternFluidStack(ingredient.getFluid(), ingredient.getAmount() * sets);
            if (insertFluid(stack) != stack.getAmount()) {
                return false;
            }
        }
        return true;
    }

    private int availablePatternSetsForFluids(ItemStack pattern, AdjacentTile connected) {
        IFluidHandler handler = (IFluidHandler) connected.tile;
        ForgeDirection side = getFluidInsertionOrientation(connected);
        int sets = Integer.MAX_VALUE;
        for (PatternFluidStack ingredient : patternHandler.getAggregatedFluidIngredients(pattern)) {
            int upperBound = ingredient.getFluid().getFreeSpaceInsideTank(handler, side) / ingredient.getAmount();
            int low = 0;
            int high = upperBound;
            while (low < high) {
                int mid = low + (high - low + 1) / 2;
                FluidStack stack = ingredient.getFluid().makeFluidStack(ingredient.getAmount() * mid);
                if (handler.fill(side, stack, false) == stack.amount) {
                    low = mid;
                } else {
                    high = mid - 1;
                }
            }
            sets = Math.min(sets, low);
        }
        return sets == Integer.MAX_VALUE ? 0 : Math.max(0, sets);
    }

    private int availablePatternSetsDisregardingSlots(List<ItemIdentifierStack> ingredients, AdjacentTile connected) {
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
        AbstractPattern configuredPattern = Pattern.fromStack(pattern);
        for (int slot = 0; slot < configuredPattern.getIngredientSlotCount(); slot++) {
            IPatternStack patternStack = configuredPattern.getPatternStackInSlot(slot);
            if (!(patternStack instanceof PatternSolidStack)) {
                continue;
            }
            if (module.hasLinkedSatelliteAssignment(pattern, slot)) {
                continue;
            }
            ItemStack ingredient = patternStack.makePatternStack();
            hasIngredient = true;
            int room = table.roomForPatternPipeSlot(slot, ingredient);
            sets = Math.min(sets, room / ingredient.stackSize);
        }
        return hasIngredient ? Math.max(0, sets) : 0;
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

    private int insertFluid(PatternFluidStack fluid) {
        AdjacentTile connected = getConnected();
        if (connected == null || !(connected.tile instanceof IFluidHandler) || fluid.getAmount() <= 0) {
            return 0;
        }
        IFluidHandler handler = (IFluidHandler) connected.tile;
        return handler.fill(getFluidInsertionOrientation(connected), fluid.makeFluidStack(), true);
    }

    private ForgeDirection getFluidInsertionOrientation(AdjacentTile connected) {
        if (module.getUpgradeManager().hasSneakyUpgrade()) {
            return module.getUpgradeManager().getSneakyOrientation();
        }
        return connected.orientation.getOpposite();
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
        return Math.max(0, module.localIngredientAmount(pattern, item) - amountOf(item));
    }

    boolean isEmpty(AdjacentTile connected) {
        if (connected == null || (!(connected.tile instanceof IInventory) && !(connected.tile instanceof IFluidHandler))) {
            return true;
        }
        if (connected.tile instanceof PatternLogisticsCraftingTableTileEntity) {
            return ((PatternLogisticsCraftingTableTileEntity) connected.tile).isIdle();
        }
        if (connected.tile instanceof IInventory) {
            IInventory inventory = (IInventory) connected.tile;
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack != null && stack.stackSize > 0) {
                    return false;
                }
            }
        }
        if (connected.tile instanceof IFluidHandler) {
            FluidTankInfo[] tanks = ((IFluidHandler) connected.tile).getTankInfo(getFluidInsertionOrientation(connected));
            if (tanks != null) {
                for (FluidTankInfo tank : tanks) {
                    if (tank != null && tank.fluid != null && tank.fluid.amount > 0) {
                        return false;
                    }
                }
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

    FluidStack extractFluid(AdjacentTile tile, PatternFluidStack wanted, int amount) {
        if (!(tile.tile instanceof IFluidHandler) || wanted == null || amount <= 0) {
            return null;
        }
        IFluidHandler handler = (IFluidHandler) tile.tile;
        ForgeDirection side = tile.orientation.getOpposite();
        FluidStack simulated = handler.drain(side, amount, false);
        if (simulated == null || simulated.amount <= 0 || !wanted.getFluid().equals(logisticspipes.utils.FluidIdentifier.get(simulated))) {
            return null;
        }
        if (!pipe.useEnergy(Math.min(amount, simulated.amount))) {
            return null;
        }
        return handler.drain(side, Math.min(amount, simulated.amount), true);
    }
}
