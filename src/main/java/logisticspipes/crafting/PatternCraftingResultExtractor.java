package logisticspipes.crafting;

import java.util.List;

import logisticspipes.utils.item.ItemIdentifierStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.config.Configs;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsFluidOrder;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * Drains completed pattern crafting outputs from the selected adjacent inventory or fluid handler.
 * <p>
 * Normal crafting orders are routed to their requester. Extra orders have no requester, so they are sent back through
 * normal storage routing and may drop if no storage accepts them. Keeping this logic outside {@link ModuleItemCrafting}
 * keeps the module focused on request planning and buffer state.
 */
class PatternCraftingResultExtractor {

    private static final int MAX_EXTRACTED_ITEMS_PER_TICK = 64;
    private static final int MAX_EXTRACTED_STACKS_PER_TICK = 16;

    private final ModuleItemCrafting module;
    private final PipeItemsPatternCraftingLogistics pipe;
    private final AdjacentInventoryHandler adjacentInventory;

    /**
     * Creates an extractor for one pattern crafting module and its selected adjacent handlers.
     */
    PatternCraftingResultExtractor(ModuleItemCrafting module, PipeItemsPatternCraftingLogistics pipe,
                                   AdjacentInventoryHandler adjacentInventory) {
        this.module = module;
        this.pipe = pipe;
        this.adjacentInventory = adjacentInventory;
    }

    /**
     * Attempts to extract both item and fluid outputs for this tick.
     */
    void tick() {
        extractItemsFromAdjacentInventory();
        extractFluidsFromAdjacentHandlers();
    }

    /**
     * Drains completed craft results, including extra and byproduct orders that were produced by the same staged craft.
     */
    private void extractItemsFromAdjacentInventory() {
        if (!pipe.isNthTick(6)) {
            return;
        }

        var orderManager = pipe.getItemOrderManager();
        if (!orderManager.hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
            return;
        }

        if (!adjacentInventory.hasConnectedTE()) {
            module.debug("extract items failed: no connected tile entity");
            return;
        }
        List<ItemStack> extractableItems = adjacentInventory.getExtractableItems();
        if (extractableItems == null || extractableItems.isEmpty()) {
            return;
        }

        pipe.spawnParticle(Particles.VioletParticle, 2);

        int itemsLeft = MAX_EXTRACTED_ITEMS_PER_TICK;
        int stacksLeft = MAX_EXTRACTED_STACKS_PER_TICK;
        int ordersLeftToTry = orderManager.getAllOrders().size();
        boolean extractedAny = false;

        while (itemsLeft > 0 && stacksLeft > 0 && ordersLeftToTry > 0
                && orderManager.hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
            LogisticsItemOrder order = orderManager.peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA);
            if (order == null) {
                break;
            }
            int maxToSend = maxExtractableItemAmount(order, itemsLeft);
            if (maxToSend <= 0) {
                module.debugEvent(
                    "FLOW",
                    "extract item deferred order=%s amount=%d localRequested=%d",
                    order.getResource().getItem(),
                    order.getAmount(),
                    module.requestedSamePipeItemAmount(order));
                orderManager.deferSend();
                ordersLeftToTry--;
                continue;
            }

            ItemStack extracted = adjacentInventory.extract(order.getResource(), maxToSend);
            if (extracted == null || extracted.stackSize <= 0) {
                module.debugEvent(
                    "FLOW",
                    "extract item deferred order=%s amount=%d",
                    order.getResource().getItem(),
                    order.getAmount());
                orderManager.deferSend();
                ordersLeftToTry--;
                continue;
            }

            module.debugEvent(
                "FLOW",
                "extract item success order=%s extracted=%d source=%s",
                order.getResource().getItem(),
                extracted.stackSize,
                adjacentInventory.getConnected());

            extractedAny = true;
            itemsLeft -= extracted.stackSize;
            stacksLeft--;

            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
            sendExtracted(order, extracted, adjacentInventory.getConnected().orientation);
            ordersLeftToTry = orderManager.getAllOrders().size();
        }

        if (extractedAny) module.requestIngredientsForStagedCrafts();
    }

    private int maxExtractableItemAmount(LogisticsItemOrder order, int itemsLeft) {
        int maxToSend = Math.min(itemsLeft, order.getAmount());
        maxToSend = Math.min(maxToSend, order.getResource().getItem().getMaxStackSize());
        if (module.isOrderDestinationThisModule(order) && order.getInformation() instanceof PatternTargetInformation) {
            maxToSend = Math.min(maxToSend, module.requestedSamePipeItemAmount(order));
        }
        return maxToSend;
    }

    /**
     * Routes an extracted item result either to its requester or, for extra outputs, back through normal storage
     * routing.
     */
    private void sendExtracted(LogisticsItemOrder order, ItemStack extracted, ForgeDirection orientation) {
        if (module.isOrderDestinationThisModule(order) && order.getInformation() instanceof PatternTargetInformation) {
            sendExtractedToLocalBuffer(order, extracted);
            return;
        }
        if (order.getDestination() != null) {
            IRoutedItem item = SimpleServiceLocator.routedItemHelper.createNewTravelItem(extracted);
            item.setDestination(order.getDestination().getRouter().getSimpleID());
            item.setTransportMode(TransportMode.Active);
            item.setAdditionalTargetInformation(order.getInformation());
            pipe.queueRoutedItem(item, orientation);
            pipe.getItemOrderManager().sendSuccessfull(extracted.stackSize, false, item);
            module.debugEvent(
                "FLOW",
                "sent extracted item=%s amount=%d destination=%d",
                ItemIdentifier.get(extracted),
                extracted.stackSize,
                order.getDestination().getRouter().getSimpleID());
        } else {
            pipe.sendStack(extracted, -1, CoreRoutedPipe.ItemSendMode.Normal, order.getInformation());
            pipe.getItemOrderManager().sendSuccessfull(extracted.stackSize, false, null);
            module.debugEvent(
                "FLOW",
                "sent extracted item=%s amount=%d without routed destination",
                ItemIdentifier.get(extracted),
                extracted.stackSize);
        }
    }

    /**
     * Hands a same-pipe intermediate result directly to the parent pattern buffer.
     * <p>
     * Routing an item to the pipe that just extracted it can make the order manager and transport retry logic disagree
     * about whether the intermediate result is already delivered. Direct arrival keeps the local requested buffer and
     * the live output order in lockstep.
     */
    private void sendExtractedToLocalBuffer(LogisticsItemOrder order, ItemStack extracted) {
        ItemIdentifierStack arrived = ItemIdentifierStack.getFromStack(extracted);
        int original = arrived.getStackSize();
        module.itemArrived(arrived, order.getInformation());
        int accepted = original - arrived.getStackSize();
        if (accepted > 0) {
            pipe.getItemOrderManager().sendSuccessfull(accepted, false, null);
        }
        module.debugEvent(
            "FLOW",
            "accepted extracted same-pipe item=%s amount=%d remaining=%d",
            ItemIdentifier.get(extracted),
            accepted,
            arrived.getStackSize());
        if (arrived.getStackSize() > 0) {
            pipe.sendStack(arrived.makeNormalStack(), -1, CoreRoutedPipe.ItemSendMode.Normal, order.getInformation());
            module.debugEvent(
                "FLOW",
                "sent unaccepted same-pipe remainder item=%s amount=%d",
                arrived.getItem(),
                arrived.getStackSize());
        }
    }

    /**
     * Drains completed fluid craft results, including extra and byproduct orders from the connected fluid handler.
     */
    private void extractFluidsFromAdjacentHandlers() {
        if (!pipe.isNthTick(6)
            || !pipe.getPatternFluidOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
            return;
        }
        List<AdjacentTile> handlers = adjacentInventory.locateFluidHandlers();
        if (handlers.isEmpty()) {
            module.debug("extract fluids failed: no adjacent fluid handlers");
            pipe.getPatternFluidOrderManager().sendFailed();
            return;
        }
        LogisticsFluidOrder order = pipe.getPatternFluidOrderManager()
            .peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA);
        if (order == null) {
            module.debug("extract fluids skipped: no top fluid order");
            return;
        }

        int amountToDrain = maxExtractableFluidAmount(order);
        if (amountToDrain <= 0) {
            module.debugEvent(
                "FLOW",
                "extract fluid deferred fluid=%s amount=%d localRequested=%d",
                order.getFluid(),
                order.getAmount(),
                module.requestedSamePipeFluidAmount(order));
            pipe.getPatternFluidOrderManager().deferSend();
            return;
        }
        PatternFluidStack wanted = new PatternFluidStack(order.getFluid(), amountToDrain);
        for (AdjacentTile tile : handlers) {
            FluidStack drained = adjacentInventory.extractFluid(tile, wanted, amountToDrain);
            if (drained == null || drained.amount <= 0) {
                continue;
            }
            module.debugEvent(
                "FLOW",
                "extract fluid success fluid=%s amount=%d source=%s",
                order.getFluid(),
                drained.amount,
                tile.tile);
            sendExtractedFluid(order, drained, tile.orientation);
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
            module.requestIngredientsForStagedCrafts();
            return;
        }
        module.debugEvent("FLOW", "extract fluid deferred fluid=%s amount=%d", order.getFluid(), amountToDrain);
        pipe.getPatternFluidOrderManager().deferSend();
    }

    private int maxExtractableFluidAmount(LogisticsFluidOrder order) {
        int amountToDrain = Math.min(order.getAmount(), Configs.MAX_LOGISTICS_FLUID_TRANSPORT_INNER_CAPACITY / 2);
        if (module.isOrderDestinationThisModule(order) && order.getInformation() instanceof PatternTargetInformation) {
            amountToDrain = Math.min(amountToDrain, module.requestedSamePipeFluidAmount(order));
        }
        return amountToDrain;
    }

    /**
     * Routes an extracted fluid result either to its requester or, for extra outputs, back through normal storage
     * routing.
     */
    private void sendExtractedFluid(LogisticsFluidOrder order, FluidStack drained, ForgeDirection orientation) {
        if (module.isOrderDestinationThisModule(order) && order.getInformation() instanceof PatternTargetInformation) {
            sendExtractedFluidToLocalBuffer(order, drained);
            return;
        }
        if (order.getDestination() != null) {
            IRoutedItem item = SimpleServiceLocator.routedItemHelper
                .createNewTravelItem(SimpleServiceLocator.logisticsFluidManager.getFluidContainer(drained));
            item.setDestination(order.getRouter().getSimpleID());
            item.setTransportMode(TransportMode.Active);
            item.setAdditionalTargetInformation(order.getInformation());
            pipe.queueRoutedItem(item, orientation);
            pipe.getPatternFluidOrderManager().sendSuccessfull(drained.amount, false, item);
            module.debugEvent(
                "FLOW",
                "sent extracted fluid=%s amount=%d destination=%d",
                order.getFluid(),
                drained.amount,
                order.getDestination().getRouter().getSimpleID());
        } else {
            pipe.sendStack(
                SimpleServiceLocator.logisticsFluidManager.getFluidContainer(drained).makeNormalStack(),
                -1,
                CoreRoutedPipe.ItemSendMode.Normal,
                order.getInformation());
            pipe.getPatternFluidOrderManager().sendSuccessfull(drained.amount, false, null);
            module.debugEvent(
                "FLOW",
                "sent extracted fluid=%s amount=%d without routed destination",
                order.getFluid(),
                drained.amount);
        }
    }

    private void sendExtractedFluidToLocalBuffer(LogisticsFluidOrder order, FluidStack drained) {
        ItemIdentifierStack arrived = ItemIdentifierStack.getFromStack(
            SimpleServiceLocator.logisticsFluidManager.getFluidContainer(drained).makeNormalStack());
        module.itemArrived(arrived, order.getInformation());
        if (arrived.getStackSize() <= 0) {
            pipe.getPatternFluidOrderManager().sendSuccessfull(drained.amount, false, null);
            module.debugEvent(
                "FLOW",
                "accepted extracted same-pipe fluid=%s amount=%d",
                order.getFluid(),
                drained.amount);
            return;
        }
        pipe.sendStack(arrived.makeNormalStack(), -1, CoreRoutedPipe.ItemSendMode.Normal, order.getInformation());
        module.debugEvent(
            "FLOW",
            "sent unaccepted same-pipe fluid container fluid=%s amount=%d",
            order.getFluid(),
            drained.amount);
    }
}
