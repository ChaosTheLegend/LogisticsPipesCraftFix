package logisticspipes.crafting;

import logisticspipes.crafting.pattern.AbstractPattern;
import logisticspipes.crafting.pattern.ItemPattern;
import logisticspipes.crafting.pattern.PatternHandler;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternFluidStack;
import logisticspipes.crafting.patternStack.PatternItemStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraftFluids;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IItemSpaceControl;
import logisticspipes.interfaces.routing.IRequest;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.logistics.LogisticsManager;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.debug.CraftingRequestDebugManager;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.FluidExtraPromise;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.LogisticsDictPromise;
import logisticspipes.routing.LogisticsExtraPromise;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsFluidOrder;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.DelayedGeneric;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.BufferMode;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.item.SimpleStackInventory;
import logisticspipes.utils.tuples.Pair;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;

public class ModulePatternCrafting extends LogisticsGuiModule
    implements ICraftItems, ICraftFluids, IRequestFluid, IRequireReliableTransport, IStagedCraftingProvider {

    private static final String LOST_INGREDIENTS_TAG = "patternLostIngredients";
    private static final String LOST_DELAY_TAG = "delay";
    private static final String STAGED_CRAFTING_TAG = "patternStagedCrafting";
    private static final String TARGET_PATTERN_SLOT_TAG = "targetPatternSlot";
    private static final int RESTORED_REQUESTED_RETRY_DELAY = 8000;
    private static final int RESTORE_DEBUG_INTERVAL = 40;
    private static final int DEFAULT_THROTTLE_TICKS = 40;
    private static final int TAG_COMPOUND = 10;

    private final PipeItemsPatternCraftingLogistics pipe;
    private final SimpleStackInventory patternInventory = new SimpleStackInventory(9, "Patterns", 1);
    private final Map<Integer, List<IPatternStack>> requestedIngredients = new HashMap<>();
    private final Set<Integer> cancelledPatternSlots = new HashSet<>();
    private final Map<String, ThrottledDebugEvent> throttledDebugEvents = new HashMap<>();
    private final DelayQueue<DelayedGeneric<Pair<IPatternStack, IAdditionalTargetInformation>>> lostIngredients = new DelayQueue<>();
    private final PatternHandler patternHandler = new PatternHandler(patternInventory);
    private final AdjacentInventoryHandler adjacentInventory;
    private final PatternStackBufferHandler ingredientBuffer = new PatternStackBufferHandler();
    private final PatternStackRequestHandler requestedIngredient = new PatternStackRequestHandler(requestedIngredients);
    private final PatternStagedCraftingCoordinator stagedCrafting;
    private final PatternCraftingTemplateBuilder templateBuilder;
    private final PatternCraftingResultExtractor resultExtractor;
    private SinkReply sinkReply;
    private PipeItemsPatternCraftingLogistics.BlockingMode blockingMode = PipeItemsPatternCraftingLogistics.BlockingMode.OFF;
    private int runningCraft = -1;
    private boolean runningCraftInAdjacent = false;
    private boolean checkingBufferedOrders = false;
    private NBTTagCompound pendingStagedCrafting;
    private boolean pendingRequestedIngredientRestoreRetries;
    private int stagedCraftingRestoreAttempts;

    public ModulePatternCrafting(PipeItemsPatternCraftingLogistics pipe) {
        this.pipe = pipe;
        patternInventory.addListener(inventory -> {
            if (pipe.container != null) {
                pipe.container.markDirty();
            }
            pipe.listenedChanged();
        });
        adjacentInventory = new AdjacentInventoryHandler(this, pipe);
        stagedCrafting = new PatternStagedCraftingCoordinator(
            this,
            pipe,
            patternHandler,
            requestedIngredient,
            adjacentInventory);
        templateBuilder = new PatternCraftingTemplateBuilder(this, patternHandler);
        resultExtractor = new PatternCraftingResultExtractor(this, pipe, adjacentInventory);
        _service = pipe;
        _world = pipe;
        registerPosition(ModulePositionType.IN_PIPE, 0);
    }

    public IInventory getPatternInventory() {
        return patternInventory;
    }

    public ItemStack getPatternStack(int slot) {
        return patternHandler.getConfiguredPatternStack(slot);
    }

    public ItemStack getPatternItemStack(int slot) {
        if (slot < 0 || slot >= patternInventory.getSizeInventory()) {
            return null;
        }
        return patternInventory.getStackInSlot(slot);
    }

    public void markPatternInventoryDirty() {
        patternInventory.markDirty();
    }

    public int assignSatelliteToAllPatternIngredients(int satelliteId, String satelliteUuid) {
        int changed = 0;
        for (int patternSlot = 0; patternSlot < patternInventory.getSizeInventory(); patternSlot++) {
            ItemStack patternStack = patternInventory.getStackInSlot(patternSlot);
            if (patternStack == null) {
                continue;
            }
            AbstractPattern pattern = ItemPattern.fromStack(patternStack);
            for (int inputSlot = 0; inputSlot < pattern.getIngredientSlotCount(); inputSlot++) {
                IPatternStack ingredient = pattern.getPatternStackInSlot(inputSlot);
                if (!PatternStackHelper.isSolid(ingredient)) {
                    continue;
                }
                pattern.setSatelliteTargetForInputSlot(inputSlot, satelliteId, satelliteUuid);
                changed++;
            }
        }
        if (changed > 0) {
            markPatternInventoryDirty();
        }
        return changed;
    }

    public PipeItemsPatternCraftingLogistics.BlockingMode getBlockingMode() {
        return getEffectiveBlockingMode();
    }

    public void setBlockingMode(PipeItemsPatternCraftingLogistics.BlockingMode blockingMode) {
        this.blockingMode = adjacentInventory.isConnectedToPatternCraftingTable()
            ? PipeItemsPatternCraftingLogistics.BlockingMode.SMART
            : blockingMode;
    }

    public boolean isBlockingModeFixed() {
        return adjacentInventory.isConnectedToPatternCraftingTable();
    }

    PipeItemsPatternCraftingLogistics.BlockingMode getEffectiveBlockingMode() {
        if (adjacentInventory.isConnectedToPatternCraftingTable()) {
            return PipeItemsPatternCraftingLogistics.BlockingMode.SMART;
        }
        return blockingMode;
    }

    /**
     * Reports how many ingredients this module can currently receive for its configured patterns.
     * <p>
     * The result includes reserved space for ingredients this module has already requested through staged crafting, so
     * subcraft results from this same pipe can be routed back into the module buffer instead of being rejected while
     * the connected inventory is busy.
     */
    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault,
                               boolean includeInTransit) {
        if (bestPriority > sinkReply.fixedPriority.ordinal() || (bestPriority == sinkReply.fixedPriority.ordinal()
            && bestCustomPriority >= sinkReply.customPriority)) {
            return null;
        }
        FluidIdentifier fluid = item != null && item.isFluidContainer() ? FluidIdentifier.get(item) : null;
        if (fluid != null) {
            if (!supportsFluidCrafting()) {
                debug("sink rejected fluid ingredient %s: fluid crafting upgrade missing", fluid);
                return null;
            }
            int room = spaceForFluid(fluid, includeInTransit);
            if (room <= 0) {
                debug("sink rejected fluid ingredient %s includeInTransit=%s", fluid, includeInTransit);
                return null;
            }
            debug("sink accepts fluid ingredient %s room=%d includeInTransit=%s", fluid, room, includeInTransit);
            return new SinkReply(
                sinkReply,
                room,
                areAllOrdersBuffered() ? BufferMode.DESTINATION_BUFFERED : BufferMode.NONE);
        }
        if (!patternHandler.isIngredient(item)) {
            debug("sink ignored non-ingredient %s", item);
            return null;
        }
        int room = spaceFor(item, includeInTransit);
        if (room <= 0) {
            debug("sink rejected item ingredient %s includeInTransit=%s", item, includeInTransit);
            return null;
        }
        debug("sink accepts item ingredient %s room=%d includeInTransit=%s", item, room, includeInTransit);
        return new SinkReply(
            sinkReply,
            room,
            areAllOrdersBuffered() ? BufferMode.DESTINATION_BUFFERED : BufferMode.NONE);
    }

    /**
     * Reports how much of a routed fluid container can currently be accepted as a pattern ingredient.
     * <p>
     * The pattern pipe advertises itself as a fluid sink so storage routing can find it, but the accepted fluid is
     * still delivered as a LogisticsFluidContainer item and buffered by this module.
     */
    public int sinkAmount(FluidStack stack) {
        if (stack == null || stack.amount <= 0) {
            return 0;
        }
        if (!supportsFluidCrafting()) {
            debug("fluid sink amount rejected %s: fluid crafting upgrade missing", FluidIdentifier.get(stack));
            return 0;
        }
        int room = spaceForFluid(FluidIdentifier.get(stack), true);
        debug("fluid sink amount check %s amount=%d room=%d", FluidIdentifier.get(stack), stack.amount, room);
        return room >= stack.amount ? stack.amount : 0;
    }

    @Override
    public void tick() {
        restoreStagedCraftingIfNeeded();
        cancelUnsupportedFluidPatternCrafts();
        scheduleRequestedIngredientRestoreRetriesIfReady();
        retryLostItems();
        pushBufferedIngredients();
        stagedCrafting.requestIngredients();
        clearRunningCraftIfFinished();
        resultExtractor.tick();
    }

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    @Override
    public Collection<ItemIdentifier> getSpecificInterests() {
        return patternHandler.getIngredientItems();
    }

    /**
     * Returns every item identity this module can craft, including fluid outputs represented by their display item.
     */
    public Set<ItemIdentifier> getCraftedItems() {
        Set<ItemIdentifier> crafted = new TreeSet<>();
        for (ItemStack pattern : patternHandler.getConfiguredPatterns()) {
            if (!isPatternCraftingSupported(pattern)) {
                continue;
            }
            AbstractPattern configuredPattern = ItemPattern.fromStack(pattern);
            for (IPatternStack result : configuredPattern.getOutputs()) {
                ItemIdentifier item = PatternStackHelper.getRoutingItem(result);
                if (item != null) {
                    crafted.add(item);
                }
            }
        }
        return crafted;
    }

    // ------- DEFAULT OVERRIDES ------ //
    // region DEFAULT OVERRIDES

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
    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    @Override
    public Map<FluidIdentifier, Integer> getAvailableFluids() {
        return Collections.emptyMap();
    }

    @Override
    protected ModuleCoordinatesGuiProvider getPipeGuiProvider() {
        return NewGuiHandler.getGui(PatternCraftingPipeGuiProvider.class).setBlockingMode(getBlockingMode().ordinal());
    }

    @Override
    protected ModuleInHandGuiProvider getInHandGuiProvider() {
        return null;
    }

    @Override
    public net.minecraft.util.IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleCrafter");
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        patternInventory.readFromNBT(tag, "PatternCrafting");
        blockingMode = PipeItemsPatternCraftingLogistics.BlockingMode.values()[Math.max(
            0,
            Math.min(
                PipeItemsPatternCraftingLogistics.BlockingMode.values().length - 1,
                tag.getInteger("patternBlockingMode")))];
        runningCraft = tag.hasKey("runningCraft") ? tag.getInteger("runningCraft")
            : tag.getInteger("bufferedPatternSlot");
        runningCraftInAdjacent = tag.hasKey("runningCraftInAdjacent") ? tag.getBoolean("runningCraftInAdjacent")
            : runningCraft >= 0;
        ingredientBuffer.readFromNBT(tag);
        requestedIngredient.readFromNBT(tag);
        readLostIngredientsFromNBT(tag);
        pendingStagedCrafting = tag.hasKey(STAGED_CRAFTING_TAG)
            ? (NBTTagCompound) tag.getCompoundTag(STAGED_CRAFTING_TAG).copy()
            : null;
        pendingRequestedIngredientRestoreRetries = !requestedIngredients.isEmpty();
        stagedCraftingRestoreAttempts = 0;
        debugEvent(
            "PERSIST",
            "loaded module nbt bufferedSlots=%d requestedSlots=%d lostQueued=%d pendingStaged=%s runningCraft=%d adjacentBatch=%s",
            ingredientBuffer.size(),
            requestedIngredients.size(),
            lostIngredients.size(),
            pendingStagedCrafting != null,
            runningCraft,
            runningCraftInAdjacent);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        patternInventory.writeToNBT(tag, "PatternCrafting");
        tag.setInteger("patternBlockingMode", blockingMode.ordinal());
        tag.setInteger("runningCraft", runningCraft);
        tag.setInteger("bufferedPatternSlot", runningCraft);
        tag.setBoolean("runningCraftInAdjacent", runningCraftInAdjacent);
        ingredientBuffer.writeToNBT(tag);
        requestedIngredient.writeToNBT(tag);
        writeLostIngredientsToNBT(tag);
        if (pendingStagedCrafting != null) {
            tag.setTag(STAGED_CRAFTING_TAG, pendingStagedCrafting.copy());
        } else {
            NBTTagCompound stagedTag = new NBTTagCompound();
            stagedCrafting.writeToNBT(stagedTag);
            tag.setTag(STAGED_CRAFTING_TAG, stagedTag);
        }
        debugEvent(
            "PERSIST",
            "saved module nbt bufferedSlots=%d requestedSlots=%d lostQueued=%d pendingStaged=%s runningCraft=%d adjacentBatch=%s",
            ingredientBuffer.size(),
            requestedIngredients.size(),
            lostIngredients.size(),
            pendingStagedCrafting != null,
            runningCraft,
            runningCraftInAdjacent);
    }
    // endregion

    // ------- DEBUG ------ //
    // region DEBUG

    @Override
    public void registerPosition(ModulePositionType slot, int positionInt) {
        super.registerPosition(slot, positionInt);
        sinkReply = new SinkReply(FixedPriority.ItemSink, 0, true, false, 1, 0, null);
    }

    void debug(String message, Object... args) {
        if (_service != null) {
            _service.getDebug().log("PatternCrafting: " + message, args);
        }
    }

    public void debugEvent(String category, String message, Object... args) {
        recordDebugEvent(category, formatDebugMessage(message, args));
    }

    void debugEventThrottled(String category, String message, Object... args) {
        debugEventThrottled(category, DEFAULT_THROTTLE_TICKS, message, args);
    }

    void debugEventThrottled(String category, int intervalTicks, String message, Object... args) {
        String formatted = formatDebugMessage(message, args);
        if (intervalTicks <= 0) {
            recordDebugEvent(category, formatted);
            return;
        }
        String key = category + "\n" + formatted;
        long tick = currentDebugTick();
        ThrottledDebugEvent state = throttledDebugEvents.get(key);
        if (state != null && tick - state.lastLoggedTick < intervalTicks) {
            state.suppressed++;
            return;
        }
        if (state == null) {
            state = new ThrottledDebugEvent();
            throttledDebugEvents.put(key, state);
        } else if (state.suppressed > 0) {
            formatted = formatted + " (suppressed repeats=" + state.suppressed + ")";
        }
        state.lastLoggedTick = tick;
        state.suppressed = 0;
        recordDebugEvent(category, formatted);
    }

    public void recordDebugEvent(String category, String message) {
        debug("%s", message);
        CraftingRequestDebugManager.recordPipeEvent(pipe, category, message);
    }

    private String formatDebugMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message == null ? "" : message;
        }
        try {
            return String.format(message, args);
        } catch (RuntimeException ignored) {
            StringBuilder out = new StringBuilder(message == null ? "" : message);
            out.append(" args=");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(args[i]);
            }
            return out.toString();
        }
    }

    private long currentDebugTick() {
        World world = pipe.getWorld();
        return world == null ? 0 : world.getTotalWorldTime();
    }
    // endregion

    private void restoreStagedCraftingIfNeeded() {
        if (pendingStagedCrafting == null) {
            return;
        }
        World world = pipe.getWorld();
        if (world != null && world.isRemote) {
            return;
        }
        if (stagedCrafting.restoreFromNBT(pendingStagedCrafting)) {
            debugEvent("STAGED", "restored staged crafting state after %d attempts", stagedCraftingRestoreAttempts + 1);
            pendingStagedCrafting = null;
            stagedCraftingRestoreAttempts = 0;
            return;
        }
        stagedCraftingRestoreAttempts++;
        if (stagedCraftingRestoreAttempts % RESTORE_DEBUG_INTERVAL == 0) {
            debugEvent("STAGED", "waiting to restore staged crafting state attempts=%d", stagedCraftingRestoreAttempts);
        }
    }

    private void scheduleRequestedIngredientRestoreRetriesIfReady() {
        if (!pendingRequestedIngredientRestoreRetries || pendingStagedCrafting != null) {
            return;
        }
        pendingRequestedIngredientRestoreRetries = false;
        for (Map.Entry<Integer, List<IPatternStack>> entry : requestedIngredients.entrySet()) {
            int patternSlot = entry.getKey();
            for (IPatternStack stack : entry.getValue()) {
                if (stack == null || stack.getAmount() <= 0) {
                    continue;
                }
                lostIngredients.add(
                    new DelayedGeneric<>(
                        new Pair<>(stack.copy(), new PatternTargetInformation(patternSlot)),
                        RESTORED_REQUESTED_RETRY_DELAY));
                debugEvent(
                    "REQUEST",
                    "restore queued requested ingredient retry slot=%d ingredient=%s",
                    patternSlot,
                    stack);
            }
        }
    }

    boolean supportsFluidCrafting() {
        ISlotUpgradeManager upgradeManager = getUpgradeManager();
        return upgradeManager != null && upgradeManager.getFluidCrafter() > 0;
    }

    boolean isPatternCraftingSupported(ItemStack pattern) {
        return !isFluidCraftingPattern(pattern) || supportsFluidCrafting();
    }

    private boolean isFluidCraftingPattern(ItemStack pattern) {
        if (pattern == null) {
            return false;
        }
        AbstractPattern configuredPattern = ItemPattern.fromStack(pattern);
        return PatternStackHelper.containsFluid(configuredPattern.getInputs())
            || PatternStackHelper.containsFluid(configuredPattern.getOutputs());
    }

    private void cancelUnsupportedFluidPatternCrafts() {
        // TODO dont execute every tick
        if (supportsFluidCrafting()) {
            return;
        }
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = patternHandler.getConfiguredPatternStack(slot);
            if (!isFluidCraftingPattern(pattern)) {
                continue;
            }
            if (stagedCrafting.hasPattern(slot) || requestedIngredients.containsKey(slot)
                || ingredientBuffer.asMap().containsKey(slot)) {
                debugEventThrottled("STAGED", "cancel fluid pattern slot=%d: fluid crafting upgrade missing", slot);
                cancelPatternCraft(slot);
            }
        }
    }

    /**
     * Offers already-registered extra outputs to a request tree before new crafting work is considered.
     * <p>
     * Item extras and fluid extras are checked against their own order managers. If an extra is consumed, fulfilment
     * will remove or reduce the destinationless extra order so the byproduct is not extracted twice.
     */
    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        IResource requested = tree.getRequestType();
        if (pipe.getPatternFluidOrderManager().hasExtras() && !tree.hasBeenQueried(pipe.getPatternFluidOrderManager())
            && requested instanceof FluidResource) {
            FluidIdentifier fluid = ((FluidResource) requested).getFluid();
            for (LogisticsFluidOrder order : pipe.getPatternFluidOrderManager()) {
                if (order.getType() == ResourceType.EXTRA && order.getFluid().equals(fluid)) {
                    int amount = Math.min(order.getAmount(), tree.getMissingAmount());
                    if (amount > 0) {
                        debug("providing extra fluid %s amount=%d for request %s", fluid, amount, requested);
                        tree.addPromise(new FluidExtraPromise(order.getFluid(), amount, this, true));
                        tree.setQueried(pipe.getPatternFluidOrderManager());
                        return;
                    }
                }
            }
            tree.setQueried(pipe.getPatternFluidOrderManager());
        }
        if (!pipe.getItemOrderManager().hasExtras() || tree.hasBeenQueried(pipe.getItemOrderManager())) {
            return;
        }
        for (LogisticsItemOrder order : pipe.getItemOrderManager()) {
            if (order.getType() == ResourceType.EXTRA
                && requested.matches(order.getResource().getItem(), IResource.MatchSettings.NORMAL)) {
                int amount = Math.min(order.getAmount(), tree.getMissingAmount());
                if (amount > 0) {
                    debug(
                        "providing extra %s amount=%d for request %s",
                        order.getResource().getItem(),
                        amount,
                        requested);
                    tree.addPromise(new LogisticsExtraPromise(order.getResource().getItem(), amount, this, true));
                    tree.setQueried(pipe.getItemOrderManager());
                    return;
                }
            }
        }
    }

    /**
     * Creates an item output order for a pattern craft or for a previously registered item extra.
     * <p>
     * When an extra promise is used to satisfy a request, its destinationless extra order is removed first; the new
     * order then targets the real requester like a normal craft output.
     */
    @Override
    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination,
                                   IAdditionalTargetInformation info) {
        if (promise instanceof LogisticsExtraPromise) {
            pipe.getItemOrderManager().removeExtras(
                new logisticspipes.request.resources.DictResource(
                    new ItemIdentifierStack(promise.item, promise.numberOfItems),
                    null));
        }
        pipe.spawnParticle(Particles.WhiteParticle, 2);
        debugEvent(
            "ORDER",
            "create item output order item=%s amount=%d destination=%s info=%s",
            promise.item,
            promise.numberOfItems,
            destination,
            info);
        return pipe.getItemOrderManager().addOrder(
            new ItemIdentifierStack(promise.item, promise.numberOfItems),
            destination,
            ResourceType.CRAFTING,
            info);
    }

    /**
     * Starts a staged craft from a request-tree branch.
     * <p>
     * The output order stays in this pipe's order manager, while the branch is kept so this module can request only the
     * ingredient sets that currently fit in its buffer or connected inventory.
     */
    @Override
    public IOrderInfoProvider fullFillStagedCrafting(IPromise promise, IResource requestType,
                                                     IAdditionalTargetInformation info, PatternCraftingBranch branch) {
        return stagedCrafting.fulfill(promise, requestType, info, branch);
    }

    /**
     * Creates a fluid output order for a pattern craft or for a previously registered fluid extra.
     * <p>
     * Consumed fluid extras are removed from the destinationless extra-order queue before the new requester-targeted
     * order is added.
     */
    @Override
    public IOrderInfoProvider fullFill(FluidLogisticsPromise promise, IRequestFluid destination, ResourceType type,
                                       IAdditionalTargetInformation info) {
        ResourceType orderType = type;
        if (promise instanceof FluidExtraPromise) {
            pipe.getPatternFluidOrderManager().removeExtras(promise.getLiquid(), promise.getAmount());
            orderType = ResourceType.CRAFTING;
        }
        pipe.spawnParticle(Particles.WhiteParticle, 2);
        debugEvent(
            "ORDER",
            "create fluid output order fluid=%s amount=%d destination=%s info=%s",
            promise.getLiquid(),
            promise.getAmount(),
            destination,
            info);
        return pipe.getPatternFluidOrderManager().addOrder(promise, destination, orderType, info);
    }

    @Override
    public void sendFailed(FluidIdentifier fluid, Integer amount) {
        if (fluid != null && amount != null && amount > 0) {
            debugEvent("FLOW", "fluid send failed fluid=%s amount=%d; queued lost ingredient retry", fluid, amount);
            lostIngredients.add(new DelayedGeneric<>(new Pair<>(new PatternFluidStack(fluid, amount), null), 5000));
        }
    }

    @Override
    public IRouter getRouter() {
        return pipe.getRouter();
    }

    @Override
    public void itemCouldNotBeSend(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        pipe.itemCouldNotBeSend(item, info);
    }

    @Override
    public int getID() {
        return pipe.getID();
    }

    @Override
    public int compareTo(IRequest request) {
        return getID() - request.getID();
    }

    /**
     * Registers item or fluid byproducts as destinationless extra orders.
     * <p>
     * Those orders force the extraction phase to remove extra products from the connected inventory or fluid handler,
     * then route them to storage or drop them if no storage can accept them.
     */
    @Override
    public void registerExtras(IPromise promise) {
        if (promise instanceof FluidLogisticsPromise fluidPromise) {
            debugEvent(
                "EXTRA",
                "register fluid extra %s amount=%d",
                fluidPromise.getLiquid(),
                fluidPromise.getAmount());
            pipe.getPatternFluidOrderManager().addExtra(fluidPromise.getLiquid(), fluidPromise.getAmount());
            return;
        }
        if (promise instanceof LogisticsDictPromise) {
            DictResource resource = ((LogisticsDictPromise) promise).getResource().clone();
            resource.getItemStack().setStackSize(promise.getAmount());
            debugEvent("EXTRA", "register dict extra %s amount=%d", resource.getItem(), promise.getAmount());
            pipe.getItemOrderManager().addExtra(resource);
            return;
        }
        debugEvent("EXTRA", "register extra %s amount=%d", promise.getItemType(), promise.getAmount());
        pipe.getItemOrderManager()
            .addExtra(new DictResource(new ItemIdentifierStack(promise.getItemType(), promise.getAmount()), null));
    }

    /**
     * Builds a crafting template for the requested item or fluid output.
     * <p>
     * The template records all local ingredients and all non-requested outputs as byproducts. Fluid outputs are matched
     * through their fluid display item identity so the normal request tree can discover them.
     */
    @Override
    public ICraftingTemplate addCrafting(IResource toCraft) {
        return templateBuilder.addCrafting(toCraft);
    }

    @Override
    public boolean canCraft(IResource toCraft) {
        for (ItemIdentifier item : getCraftedItems()) {
            if (toCraft.matches(item, IResource.MatchSettings.NORMAL)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getTodo() {
        return pipe.getItemOrderManager().totalAmountCountInAllOrders();
    }

    @Override
    public List<ItemIdentifierStack> getConfiguredCraftResults() {
        List<ItemIdentifierStack> results = new ArrayList<>();
        for (ItemStack pattern : patternHandler.getConfiguredPatterns()) {
            if (!isPatternCraftingSupported(pattern)) {
                continue;
            }
            AbstractPattern configuredPattern = ItemPattern.fromStack(pattern);
            for (IPatternStack output : configuredPattern.getOutputs()) {
                ItemIdentifierStack display = PatternStackHelper.makeDisplayStack(output);
                if (display != null) {
                    results.add(display);
                }
            }
        }
        return results;
    }

    public PatternCraftingHudState getHudState() {
        PatternCraftingHudState state = new PatternCraftingHudState(getEffectiveBlockingMode());
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = getPatternStack(slot);
            if (pattern == null) {
                continue;
            }
            AbstractPattern configuredPattern = ItemPattern.fromStack(pattern);
            PatternCraftingHudState.PatternInfo patternInfo = new PatternCraftingHudState.PatternInfo(slot);
            for (int inputSlot = 0; inputSlot < configuredPattern.getIngredientSlotCount(); inputSlot++) {
                IPatternStack ingredient = configuredPattern.getPatternStackInSlot(inputSlot);
                ItemIdentifierStack display = PatternStackHelper.makeDisplayStack(ingredient);
                if (display != null) {
                    patternInfo.getIngredients().add(
                        new PatternCraftingHudState.IngredientInfo(
                                    display,
                                    ingredientBuffer.amount(slot, ingredient),
                                    inputSlot));
                }
            }
            for (int outputSlot = 0; outputSlot < configuredPattern.getResultSlotCount(); outputSlot++) {
                IPatternStack output = configuredPattern
                        .getPatternStackInSlot(configuredPattern.getResultSlotStart() + outputSlot);
                ItemIdentifierStack display = PatternStackHelper.makeDisplayStack(output);
                if (display != null) {
                    patternInfo.getOutputs().add(
                        new PatternCraftingHudState.OutputInfo(
                            display,
                            stagedCrafting.remainingOutputAmount(slot, output),
                            outputSlot));
                }
            }
            patternInfo.setActive(slot == runningCraft);
            patternInfo.setStatus(getHudStatus(slot, pattern));
            state.getPatterns().add(patternInfo);
        }
        return state;
    }

    private String getHudStatus(int patternSlot, ItemStack pattern) {
        if (!isPatternCraftingSupported(pattern)) {
            return "Waiting: fluid crafting upgrade missing";
        }
        PipeItemsPatternCraftingLogistics.BlockingMode mode = getEffectiveBlockingMode();
        AdjacentTile connected = adjacentInventory.getConnected();
        int bufferedSets = completeBufferedSets(patternSlot);
        if (runningCraft == patternSlot && runningCraftInAdjacent) {
            return "Doing: crafting in target inventory";
        }
        if (mode != PipeItemsPatternCraftingLogistics.BlockingMode.OFF && runningCraft >= 0
            && runningCraft != patternSlot
            && runningCraftInAdjacent
            && connected != null
                && !isInventoryEmpty(connected)) {
            return "Waiting: blocking slot " + (runningCraft + 1) + " is crafting";
        }
        if (bufferedSets > 0) {
            if (connected == null) {
                return "Waiting: no target inventory";
            }
            if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING
                    && !adjacentInventory.isEmpty(connected)) {
                return "Waiting: target inventory occupied";
            }
            if (adjacentInventory.availablePatternSets(pattern) <= 0) {
                return "Waiting: no target space";
            }
            return "Doing: ready to insert " + formatSets(bufferedSets);
        }
        String pendingIngredient = getHudPendingIngredient(patternSlot, pattern);
        if (pendingIngredient != null) {
            return pendingIngredient;
        }
        int stagedSets = stagedRemainingSets(patternSlot);
        if (stagedSets > 0) {
            if (!canReceiveForPattern(patternSlot)) {
                return runningCraft >= 0 ? "Waiting: blocking slot " + (runningCraft + 1) : "Waiting: buffer space";
            }
            return "Doing: requesting " + formatSets(stagedSets);
        }
        if (totalAmount(ingredientBuffer.asMap().get(patternSlot)) > 0) {
            return "Waiting: buffered ingredients incomplete";
        }
        return "Idle";
    }

    private String getHudPendingIngredient(int patternSlot, ItemStack pattern) {
        for (IPatternStack ingredient : getLocalAggregatedIngredients(pattern)) {
            int buffered = ingredientBuffer.amount(patternSlot, ingredient);
            int requested = requestedIngredient.amount(patternSlot, ingredient);

            if (requested <= 0) continue;

            ingredient.getAmount();
            if (buffered < ingredient.getAmount()) {
                String status = "Waiting on " + getHudIngredientName(
                    ingredient) + " (" + buffered + "/" + ingredient.getAmount();
                status += ", " + requested + " routed";

                return status + ")";
            }
            return "Waiting on " + getHudIngredientName(ingredient) + " (" + requested + " routed)";
        }
        if (totalAmount(requestedIngredients.get(patternSlot)) > 0) {
            return "Waiting on ingredients";
        }
        return null;
    }

    private String getHudIngredientName(IPatternStack stack) {
        ItemIdentifierStack display = PatternStackHelper.makeDisplayStack(stack);
        return display == null ? "ingredient" : display.getItem().getFriendlyName();
    }

    private String formatSets(int sets) {
        return sets == 1 ? "1 set" : sets + " sets";
    }

    private int stagedRemainingSets(int patternSlot) {
        return stagedCrafting.remainingSets(patternSlot);
    }

    private int totalAmount(List<IPatternStack> stacks) {
        int amount = 0;
        if (stacks == null) {
            return amount;
        }
        for (IPatternStack stack : stacks) {
            if (stack != null) {
                amount += Math.max(0, stack.getAmount());
            }
        }
        return amount;
    }

    /**
     * Appends the current module-side staged crafting state to the crafting request debug dump.
     */
    public void appendDebugState(StringBuilder out) {
        out.append("Pattern crafting pipe at ").append(pipe.getX()).append(", ").append(pipe.getY()).append(", ")
            .append(pipe.getZ()).append(" router=").append(pipe.getRouter().getSimpleID()).append("\n");
        out.append("  mode stored=").append(blockingMode).append(" effective=").append(getEffectiveBlockingMode())
            .append(" fixed=").append(isBlockingModeFixed()).append(" runningCraft=").append(runningCraft)
            .append(" adjacentBatch=").append(runningCraftInAdjacent).append("\n");
        appendConnectedInventoryDebug(out);
        appendPatternDebug(out);
        appendStackMapDebug(out, "buffered ingredients", ingredientBuffer.asMap());
        appendStackMapDebug(out, "requested ingredients", requestedIngredients);
        appendStagedCraftDebug(out);
        appendOrderDebug(out);
        out.append("  lost ingredients queued=").append(lostIngredients.size()).append("\n");
    }

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        debugEvent("FLOW", "ingredient lost item=%s info=%s", item, info);
        if (info instanceof PatternTargetInformation && item != null) {
            FluidStack fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(item);
            int patternSlot = ((PatternTargetInformation) info).patternSlot();
            if (fluid != null) {
                PatternFluidStack patternFluid = new PatternFluidStack(FluidIdentifier.get(fluid), fluid.amount);
                requestedIngredient.remove(patternSlot, patternFluid);
                debugEvent(
                    "FLOW",
                    "lost fluid ingredient slot=%d fluid=%s amount=%d removed from requested and queued retry",
                    patternSlot,
                    FluidIdentifier.get(fluid),
                    fluid.amount);
                lostIngredients.add(
                    new DelayedGeneric<>(
                        new Pair<>(new PatternFluidStack(FluidIdentifier.get(fluid), fluid.amount), info),
                        5000));
                return;
            }
            requestedIngredient.remove(patternSlot, new PatternItemStack(item.clone()));
            debugEvent(
                "FLOW",
                "lost item ingredient slot=%d item=%s amount=%d removed from requested",
                patternSlot,
                item.getItem(),
                item.getStackSize());
        }
        if (item != null) {
            lostIngredients.add(new DelayedGeneric<>(new Pair<>(new PatternItemStack(item.clone()), info), 5000));
            debugEvent("FLOW", "queued lost item retry item=%s info=%s", item, info);
        }
    }

    /**
     * Accepts an ingredient that was routed to this pattern module.
     * <p>
     * Requested ingredients are accepted into the module buffer even when the adjacent inventory is currently busy.
     * Those requests were already staged against this pipe's own capacity, so asking the adjacent inventory again here
     * can bounce valid subcraft results back to storage. Non-requested overflow still uses the normal arrival capacity.
     * The routed stack is reduced to the amount that could not be accepted.
     */
    @Override
    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        if (!(info instanceof PatternTargetInformation) || item == null || item.getStackSize() <= 0) {
            debugEvent("FLOW", "arrival without pattern target item=%s info=%s", item, info);
            if (item != null && item.getStackSize() > 0) {
                FluidStack fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(item);
                int patternSlot = fluid != null ? findFluidArrivalPattern(FluidIdentifier.get(fluid))
                        : findItemArrivalPattern(item.getItem());
                if (patternSlot >= 0) {
                    itemArrived(item, new PatternTargetInformation(patternSlot));
                }
            }
            return;
        }
        int patternSlot = ((PatternTargetInformation) info).patternSlot();
        FluidStack fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(item);
        if (shouldRouteCancelledArrivalToStorage(patternSlot, item, fluid)) {
            sendArrivedIngredientToStorage(patternSlot, item, fluid);
            return;
        }
        ItemStack pattern = getPatternStack(patternSlot);
        if (fluid != null) {
            fluidArrived(patternSlot, pattern, item, fluid);
            return;
        }
        if (pattern == null || !patternContains(pattern, item.getItem())) {
            debugEvent(
                "FLOW",
                "item arrival rejected slot=%d item=%s pattern=%s contains=%s",
                patternSlot,
                item,
                pattern,
                pattern != null && patternContains(pattern, item.getItem()));
            return;
        }

        int original = item.getStackSize();
        int requested = requestedIngredient.amount(patternSlot, item.getItem());
        int space = spaceForArrivingIngredient(patternSlot, pattern, item.getItem());
        int accepted = Math.min(original, Math.max(requested, space));

        debugEvent(
            "FLOW",
            "item arrived slot=%d item=%s original=%d requested=%d space=%d accepted=%d",
            patternSlot,
            item.getItem(),
            original,
            requested,
            space,
            accepted);
        requestedIngredient
            .remove(patternSlot, new PatternItemStack(new ItemIdentifierStack(item.getItem(), accepted)));
        int requestedAfter = requestedIngredient.amount(patternSlot, item.getItem());
        if (accepted > 0) {
            ingredientBuffer.add(patternSlot, new PatternItemStack(new ItemIdentifierStack(item.getItem(), accepted)));
            debugEvent(
                "BUFFER",
                "item buffered slot=%d item=%s accepted=%d requested=%d->%d buffered=%d completeSets=%d",
                patternSlot,
                item.getItem(),
                accepted,
                requested,
                requestedAfter,
                ingredientBuffer.amount(patternSlot, item.getItem()),
                ingredientBuffer.completeSets(patternSlot, getLocalAggregatedIngredients(pattern)));
            activateRunningCraftFromBuffer(patternSlot);
            pushBufferedIngredientsFor(patternSlot);
        }
        item.setStackSize(original - accepted);
        if (accepted > 0) {
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        }
    }

    private boolean shouldRouteCancelledArrivalToStorage(int patternSlot, ItemIdentifierStack item, FluidStack fluid) {
        if (!cancelledPatternSlots.contains(patternSlot) || stagedRemainingSets(patternSlot) > 0) {
            return false;
        }
        if (fluid != null) {
            return requestedIngredient.amount(patternSlot, FluidIdentifier.get(fluid)) <= 0;
        }
        return requestedIngredient.amount(patternSlot, item.getItem()) <= 0;
    }

    private void sendArrivedIngredientToStorage(int patternSlot, ItemIdentifierStack item, FluidStack fluid) {
        debugEvent(
            "FLOW",
            "cancelled slot=%d sends late ingredient to storage item=%s fluid=%s amount=%d",
            patternSlot,
            item.getItem(),
            fluid == null ? "<none>" : FluidIdentifier.get(fluid),
            fluid == null ? item.getStackSize() : fluid.amount);
        pipe.sendStack(item.makeNormalStack(), -1, CoreRoutedPipe.ItemSendMode.Normal, null);
        item.setStackSize(0);
        pipe.getCacheHolder().trigger(CacheTypes.Inventory);
    }

    /**
     * Accepts a routed fluid container as a buffered fluid ingredient for one pattern slot.
     * <p>
     * The routed item is consumed only when the full fluid amount can fit in the staged buffer. Partial acceptance
     * would split the opaque LogisticsFluidContainer item and lose the exact routed-fluid accounting.
     */
    private void fluidArrived(int patternSlot, ItemStack pattern, ItemIdentifierStack routedStack,
                              FluidStack fluidStack) {
        FluidIdentifier fluid = FluidIdentifier.get(fluidStack);
        if (pattern == null || patternHandler.fluidIngredientAmount(pattern, fluid) <= 0) {
            debugEvent(
                "FLOW",
                "fluid arrival rejected slot=%d fluid=%s amount=%d pattern=%s",
                patternSlot,
                fluid,
                fluidStack == null ? 0 : fluidStack.amount,
                pattern);
            return;
        }

        int original = fluidStack.amount;
        int requested = requestedIngredient.amount(patternSlot, fluid);
        int space = Math.max(requested, spaceForArrivingFluidIngredient(patternSlot, pattern, fluid));
        int accepted = space >= original ? original : 0;
        debugEvent(
            "FLOW",
            "fluid arrived slot=%d fluid=%s original=%d requested=%d space=%d accepted=%d",
            patternSlot,
            fluid,
            original,
            requested,
            space,
            accepted);
        requestedIngredient.remove(patternSlot, new PatternFluidStack(fluid, accepted));
        int requestedAfter = requestedIngredient.amount(patternSlot, fluid);
        if (accepted > 0) {
            ingredientBuffer.add(patternSlot, new PatternFluidStack(fluid, accepted));
            debugEvent(
                "BUFFER",
                "fluid buffered slot=%d fluid=%s accepted=%d requested=%d->%d buffered=%d completeSets=%d",
                patternSlot,
                fluid,
                accepted,
                requested,
                requestedAfter,
                ingredientBuffer.amount(patternSlot, fluid),
                ingredientBuffer.completeSets(patternSlot, getLocalAggregatedIngredients(pattern)));
            activateRunningCraftFromBuffer(patternSlot);
            pushBufferedIngredientsFor(patternSlot);
            routedStack.setStackSize(0);
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        }
    }

    /**
     * Finds the best pattern slot for a fluid container that arrived without explicit pattern target information.
     * <p>
     * Requested fluid ingredients win over general capacity so rerouted or retried fluids complete the craft that asked
     * for them first.
     */
    private int findFluidArrivalPattern(FluidIdentifier fluid) {
        int fallback = -1;
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = patternHandler.getConfiguredPatternStack(slot);
            if (pattern == null || patternHandler.fluidIngredientAmount(pattern, fluid) <= 0) {
                continue;
            }
            if (requestedIngredient.amount(slot, fluid) > 0) {
                return slot;
            }
            if (fallback < 0 && canReceiveForPattern(slot)
                && spaceForPatternFluidIngredient(slot, pattern, fluid) > 0) {
                fallback = slot;
            }
        }
        debug("fluid arrival pattern lookup fluid=%s selected=%d", fluid, fallback);
        return fallback;
    }

    /**
     * Finds the best pattern slot for a routed item whose target information was not available after loading.
     * <p>
     * Saved requested-ingredient state wins so in-flight items from before a world stop keep completing the craft that
     * reserved them. Capacity fallback is used only when exactly one pattern can accept the item, avoiding ambiguous
     * multi-pattern ingredients being consumed by the wrong craft.
     */
    private int findItemArrivalPattern(ItemIdentifier item) {
        int requestedSlot = -1;
        int fallback = -1;
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = patternHandler.getConfiguredPatternStack(slot);
            if (pattern == null || !patternContains(pattern, item)) {
                continue;
            }
            if (requestedIngredient.amount(slot, item) > 0) {
                if (requestedSlot >= 0) {
                    debug("item arrival pattern lookup item=%s ambiguous requested slots", item);
                    return -1;
                }
                requestedSlot = slot;
                continue;
            }
            if (canReceiveForPattern(slot) && spaceForPatternIngredient(slot, pattern, item) > 0) {
                if (fallback >= 0) {
                    debug("item arrival pattern lookup item=%s ambiguous", item);
                    return -1;
                }
                fallback = slot;
            }
        }
        if (requestedSlot >= 0) {
            debug("item arrival pattern lookup item=%s selected requested slot=%d", item, requestedSlot);
            return requestedSlot;
        }
        debug("item arrival pattern lookup item=%s selected=%d", item, fallback);
        return fallback;
    }

    /**
     * Calculates how many arriving items can be consumed for one pattern before the transport layer treats them as
     * leftovers. This is intentionally separate from {@link #spaceFor(ItemIdentifier, boolean)} because arriving items
     * can complete a blocking-mode buffer that will be pushed as soon as the connected inventory becomes empty.
     */
    private int spaceForArrivingIngredient(int patternSlot, ItemStack pattern, ItemIdentifier item) {
        int space = spaceForPatternIngredient(patternSlot, pattern, item);
        AdjacentTile connected = adjacentInventory.getConnected();
        if (getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING && connected != null
            && adjacentInventory.isEmpty(connected)
            && ingredientBuffer.canCompleteOneSetAfterAdding(
            patternSlot,
            getLocalAggregatedIngredients(pattern),
            new PatternItemStack(new ItemIdentifierStack(item, space)))) {
            space += localIngredientAmount(pattern, item);
        }
        debug("arrival item space slot=%d item=%s space=%d", patternSlot, item, space);
        return space;
    }

    /**
     * Calculates whether an arriving fluid container can be accepted for a pattern slot.
     * <p>
     * Fluid containers are accepted all-or-nothing, but blocking mode can reserve one extra craft set when all solid
     * ingredients for that set are already buffered.
     */
    private int spaceForArrivingFluidIngredient(int patternSlot, ItemStack pattern, FluidIdentifier fluid) {
        int space = spaceForPatternFluidIngredient(patternSlot, pattern, fluid);
        AdjacentTile connected = adjacentInventory.getConnected();
        if (getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING && connected != null
            && adjacentInventory.isEmpty(connected)
            && ingredientBuffer.canCompleteOneSetAfterAdding(
            patternSlot,
            getLocalAggregatedIngredients(pattern),
            new PatternFluidStack(fluid, space))
            && itemIngredientsBufferedForOneSet(patternSlot, pattern)) {
            space += patternHandler.fluidIngredientAmount(pattern, fluid);
        }
        debug("arrival fluid space slot=%d fluid=%s space=%d", patternSlot, fluid, space);
        return space;
    }

    /**
     * Checks whether every local item ingredient for one set is already buffered.
     * <p>
     * This is used before accepting an extra blocking-mode fluid set so a fluid-only buffer cannot start a craft
     * without its matching solid ingredients.
     */
    private boolean itemIngredientsBufferedForOneSet(int patternSlot, ItemStack pattern) {
        for (IPatternStack ingredient : getLocalAggregatedIngredients(pattern)) {
            if (!PatternStackHelper.isSolid(ingredient)) {
                continue;
            }
            if (ingredientBuffer.amount(patternSlot, ingredient) < ingredient.getAmount()) {
                return false;
            }
        }
        return true;
    }

    protected ISlotUpgradeManager getUpgradeManager() {
        if (_service == null) {
            return null;
        }
        return _service.getUpgradeManager(slot, positionInt);
    }

    ForgeDirection getInsertionOrientation(AdjacentTile tile) {
        ForgeDirection insertion = tile.orientation;
        if (getUpgradeManager().hasSneakyUpgrade()) {
            insertion = getUpgradeManager().getSneakyOrientation();
        }
        return insertion;
    }

    int getRunningCraftForHandler() {
        return runningCraft;
    }

    /**
     * Returns the number of items this module can still sink for any configured pattern using the item as an
     * ingredient.
     * <p>
     * Requested ingredients reserve module buffer space for in-flight staged crafts. They remain sinkable even if the
     * adjacent inventory cannot accept another full pattern set yet; otherwise a subrequest from the same pipe can be
     * sent away as a lost item and recursively ask this method again through storage.
     */
    private int spaceFor(ItemIdentifier item, boolean includeInTransit) {
        int count = 0;
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = patternHandler.getConfiguredPatternStack(slot);
            if (pattern == null || !isPatternCraftingSupported(pattern) || localIngredientAmount(pattern, item) <= 0) {
                continue;
            }
            int requested = requestedIngredient.amount(slot, item);
            if (requested > 0) {
                count = Math.max(count, requested);
            }
            if (!canReceiveForPattern(slot)) {
                continue;
            }
            count = Math.max(count, spaceForPatternIngredient(slot, pattern, item) - requested);
        }
        if (includeInTransit) {
            count -= pipe.countOnRoute(item);
        }
        return Math.max(0, count);
    }

    /**
     * Calculates how much of a fluid ingredient this module can currently sink.
     * <p>
     * This mirrors item capacity but measures millibuckets in the module's pattern buffer instead of item stack counts.
     */
    private int spaceForFluid(FluidIdentifier fluid, boolean includeInTransit) {
        // TODO include in transit
        int count = 0;
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = patternHandler.getConfiguredPatternStack(slot);
            if (pattern == null || !isPatternCraftingSupported(pattern)
                || patternHandler.fluidIngredientAmount(pattern, fluid) <= 0) {
                continue;
            }
            int requested = requestedIngredient.amount(slot, fluid);
            if (requested > 0) {
                count = Math.max(count, requested);
            }
            if (!canReceiveForPattern(slot)) {
                continue;
            }
            count = Math.max(count, spaceForPatternFluidIngredient(slot, pattern, fluid) - requested);
        }
        return count;
    }

    /**
     * Returns whether a pattern slot is currently allowed to receive more local ingredients.
     * <p>
     * Blocking modes restrict buffering to the active craft or to an empty connected inventory.
     */
    boolean canReceiveForPattern(int patternSlot) {
        if (!isPatternCraftingSupported(patternHandler.getConfiguredPatternStack(patternSlot))) {
            return false;
        }
        PipeItemsPatternCraftingLogistics.BlockingMode mode = getEffectiveBlockingMode();
        if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.OFF) {
            return true;
        }
        if (isRunningCraftLocked()) {
            return runningCraft == patternSlot;
        }
        AdjacentTile connected = adjacentInventory.getConnected();
        return connected == null || adjacentInventory.isEmpty(connected);
    }

    /**
     * Calculates item ingredient capacity for one pattern slot, including the number of sets that fit in the adjacent
     * inventory for non-blocking modes.
     */
    private int spaceForPatternIngredient(int patternSlot, ItemStack pattern, ItemIdentifier item) {
        int sets = 1;
        if (getEffectiveBlockingMode() != PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING) {
            sets += adjacentInventory.availablePatternSets(pattern);
        }
        int capacity = sets * localIngredientAmount(pattern, item);
        int result = Math.max(0, capacity - ingredientBuffer.amount(patternSlot, item));
        debug(
            "pattern item capacity slot=%d item=%s sets=%d capacity=%d buffered=%d room=%d",
            patternSlot,
            item,
            sets,
            capacity,
            ingredientBuffer.amount(patternSlot, item),
            result);
        return result;
    }

    /**
     * Calculates fluid ingredient capacity for one pattern slot in millibuckets.
     */
    private int spaceForPatternFluidIngredient(int patternSlot, ItemStack pattern, FluidIdentifier fluid) {
        int sets = 1;
        if (getEffectiveBlockingMode() != PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING) {
            sets += adjacentInventory.availablePatternSets(pattern);
        }
        int capacity = sets * patternHandler.fluidIngredientAmount(pattern, fluid);
        int result = Math.max(0, capacity - ingredientBuffer.amount(patternSlot, fluid));
        debug(
            "pattern fluid capacity slot=%d fluid=%s sets=%d capacity=%d buffered=%d room=%d",
            patternSlot,
            fluid,
            sets,
            capacity,
            ingredientBuffer.amount(patternSlot, fluid),
            result);
        return result;
    }

    /**
     * Dispatches capacity checks for item and fluid pattern ingredients.
     */
    int spaceForPatternIngredient(int patternSlot, ItemStack pattern, IPatternStack ingredient) {
        ItemIdentifierStack solid = PatternStackHelper.asSolidStack(ingredient);
        if (solid != null) {
            return spaceForPatternIngredient(patternSlot, pattern, solid.getItem());
        }
        FluidIdentifier fluid = PatternStackHelper.asFluid(ingredient);
        if (fluid != null) {
            return spaceForPatternFluidIngredient(patternSlot, pattern, fluid);
        }
        return 0;
    }

    private boolean patternContains(ItemStack pattern, ItemIdentifier item) {
        return localIngredientAmount(pattern, item) > 0;
    }

    /**
     * Returns the non-satellite item ingredients that have to be buffered and inserted by this crafting pipe.
     * <p>
     * Ingredients assigned to a linked pattern satellite are requested directly for that satellite and therefore must
     * not be counted as local buffer requirements.
     */
    List<IPatternStack> getLocalAggregatedIngredients(ItemStack pattern) {
        List<IPatternStack> result = new ArrayList<>();
        if (pattern == null) {
            return result;
        }
        AbstractPattern configuredPattern = ItemPattern.fromStack(pattern);
        for (int slot = 0; slot < configuredPattern.getIngredientSlotCount(); slot++) {
            IPatternStack stack = configuredPattern.getPatternStackInSlot(slot);
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }
            if (PatternStackHelper.isSolid(stack) && getSatelliteTargetForInputSlot(configuredPattern, slot) != null) {
                continue;
            }
            if (PatternStackHelper.isFluid(stack)
                && getFluidSatelliteTargetForInputSlot(configuredPattern, slot) != null) {
                continue;
            }
            PatternStackHelper.addAggregated(result, stack);
        }
        return result;
    }

    /**
     * Resolves the satellite destination for an item ingredient in a pattern.
     * <p>
     * If duplicate input slots contain the same item and only some are assigned to satellites, the assigned satellite
     * is used for staged routing of that item. Keep pattern assignments uniform for duplicate ingredients when
     * splitting the same item between local and satellite machines matters.
     */
    IRequestItems getSatelliteTargetForIngredient(ItemStack pattern, ItemIdentifier item) {
        if (pattern == null || item == null) {
            return null;
        }
        AbstractPattern configuredPattern = ItemPattern.fromStack(pattern);
        for (int slot = 0; slot < configuredPattern.getIngredientSlotCount(); slot++) {
            IPatternStack stack = configuredPattern.getPatternStackInSlot(slot);
            if (!(stack instanceof PatternItemStack)
                || !((PatternItemStack) stack).getItemIdentifierStack().getItem().equalsForCrafting(item)) {
                continue;
            }
            IRequestItems target = getSatelliteTargetForInputSlot(configuredPattern, slot);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    /**
     * Builds ingredient request groups, keeping local and satellite-routed copies of the same item separate.
     */
    List<PatternIngredientTarget> getIngredientTargets(ItemStack pattern) {
        List<PatternIngredientTarget> result = new ArrayList<>();
        if (pattern == null) {
            return result;
        }
        AbstractPattern configuredPattern = ItemPattern.fromStack(pattern);
        for (int slot = 0; slot < configuredPattern.getIngredientSlotCount(); slot++) {
            IPatternStack stack = configuredPattern.getPatternStackInSlot(slot);
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }
            IRequestItems itemTarget = PatternStackHelper.isSolid(stack)
                ? getSatelliteTargetForInputSlot(configuredPattern, slot)
                : null;
            IRequestFluid fluidTarget = PatternStackHelper.isFluid(stack)
                ? getFluidSatelliteTargetForInputSlot(configuredPattern, slot)
                : null;
            boolean merged = false;
            for (PatternIngredientTarget existing : result) {
                if (existing.itemTarget() == itemTarget
                    && existing.fluidTarget() == fluidTarget
                    && existing.stack().canMerge(stack)) {
                    existing.stack().addAmount(stack.getAmount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                result.add(new PatternIngredientTarget(stack.copy(), itemTarget, fluidTarget));
            }
        }
        return result;
    }

    /**
     * Counts how much of an item ingredient still belongs to the local connected inventory after satellite assignments.
     */
    int localIngredientAmount(ItemStack pattern, ItemIdentifier item) {
        int amount = 0;
        for (IPatternStack ingredient : getLocalAggregatedIngredients(pattern)) {
            if (PatternStackHelper.matches(ingredient, item)) {
                amount += ingredient.getAmount();
            }
        }
        return amount;
    }

    /**
     * Checks whether one input slot is assigned to a linked and currently known pattern satellite pipe.
     */
    boolean hasLinkedSatelliteAssignment(ItemStack pattern, int inputSlot) {
        if (pattern == null) {
            return false;
        }
        AbstractPattern configuredPattern = ItemPattern.fromStack(pattern);
        IPatternStack stack = configuredPattern.getPatternStackInSlot(inputSlot);
        if (PatternStackHelper.isFluid(stack)) {
            return getFluidSatelliteTargetForInputSlot(configuredPattern, inputSlot) != null;
        }
        return getSatelliteTargetForInputSlot(configuredPattern, inputSlot) != null;
    }

    /**
     * Returns true when at least one input slot of this pattern is routed to a linked pattern satellite.
     */
    boolean hasLinkedSatelliteAssignments(ItemStack pattern) {
        if (pattern == null) {
            return false;
        }
        AbstractPattern configuredPattern = ItemPattern.fromStack(pattern);
        for (int slot = 0; slot < configuredPattern.getIngredientSlotCount(); slot++) {
            IPatternStack stack = configuredPattern.getPatternStackInSlot(slot);
            if (PatternStackHelper.isSolid(stack) && getSatelliteTargetForInputSlot(configuredPattern, slot) != null) {
                return true;
            }
            if (PatternStackHelper.isFluid(stack)
                && getFluidSatelliteTargetForInputSlot(configuredPattern, slot) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the linked pattern satellite assigned to one solid input slot.
     */
    private IRequestItems getSatelliteTargetForInputSlot(AbstractPattern pattern, int inputSlot) {
        if (adjacentInventory.isConnectedToPatternCraftingTable()) {
            return null;
        }
        int satelliteId = pattern.getSatelliteIdForInputSlot(inputSlot);
        String satelliteUuid = pattern.getSatelliteUuidForInputSlot(inputSlot);
        return pipe.resolvePatternSatelliteTarget(satelliteUuid, satelliteId);
    }

    /**
     * Resolves the linked pattern fluid satellite assigned to one fluid input slot.
     */
    private IRequestFluid getFluidSatelliteTargetForInputSlot(AbstractPattern pattern, int inputSlot) {
        if (adjacentInventory.isConnectedToPatternCraftingTable()) {
            return null;
        }
        int satelliteId = pattern.getFluidSatelliteIdForInputSlot(inputSlot);
        String satelliteUuid = pattern.getFluidSatelliteUuidForInputSlot(inputSlot);
        return pipe.resolvePatternFluidSatelliteTarget(satelliteUuid, satelliteId);
    }

    /**
     * Attempts to push complete buffered pattern sets into the selected adjacent inventory or fluid handler.
     * <p>
     * Blocking modes keep one active pattern slot locked only while arrived ingredients are still buffered or while the
     * adjacent target is processing a batch inserted by that slot.
     */
    private void pushBufferedIngredients() {
        AdjacentTile connected = getConnectedInventoryTile();
        if (connected == null) {
            debugEventThrottled(
                "BUFFER",
                "push skipped: no connected inventory bufferedSlots=%d",
                ingredientBuffer.size());
            return;
        }
        PipeItemsPatternCraftingLogistics.BlockingMode mode = getEffectiveBlockingMode();
        debug("push tick mode=%s runningCraft=%d bufferedSlots=%d", mode, runningCraft, ingredientBuffer.size());
        if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.OFF) {
            for (int patternSlot : new ArrayList<>(ingredientBuffer.asMap().keySet())) {
                if (completeBufferedSets(patternSlot) <= 0) continue;
                pushBufferedIngredientsFor(patternSlot);
            }
            return;
        }
        refreshRunningCraftState(connected);
        if (runningCraft >= 0) {
            pushBufferedIngredientsFor(runningCraft);
        }
    }

    /**
     * Pushes complete buffered sets for one pattern slot into the connected crafting target.
     */
    private void pushBufferedIngredientsFor(int patternSlot) {
        ItemStack pattern = getPatternStack(patternSlot);
        if (pattern == null) {
            debugEvent("BUFFER", "push slot=%d dropped buffer: pattern missing", patternSlot);
            ingredientBuffer.removeAll(patternSlot);
            return;
        }
        PipeItemsPatternCraftingLogistics.BlockingMode mode = getEffectiveBlockingMode();
        if (mode != PipeItemsPatternCraftingLogistics.BlockingMode.OFF && isRunningCraftLocked()
            && runningCraft != patternSlot) {
            debugEventThrottled(
                "BUFFER",
                "push slot=%d skipped: running craft locked by slot=%d",
                patternSlot,
                runningCraft);
            return;
        }
        AdjacentTile connected = adjacentInventory.getConnected();
        if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING && !adjacentInventory.isEmpty(connected)) {
            debugEventThrottled(
                "BUFFER",
                "push slot=%d skipped: blocking mode and adjacent inventory not empty",
                patternSlot);
            return;
        }
        int bufferedSets = completeBufferedSets(patternSlot);
        int insertableSets = adjacentInventory.availablePatternSets(pattern);
        if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING) {
            insertableSets = Math.min(insertableSets, 1);
        }
        int sets = Math.min(bufferedSets, insertableSets);
        if (sets <= 0 || !adjacentInventory.insertPatternSets(pattern, sets)) {
            debugEventThrottled(
                "BUFFER",
                "push slot=%d failed: bufferedSets=%d insertableSets=%d selectedSets=%d",
                patternSlot,
                bufferedSets,
                insertableSets,
                sets);
            return;
        }
        debugEvent(
            "BUFFER",
            "push slot=%d inserted sets=%d bufferedSets=%d insertableSets=%d",
            patternSlot,
            sets,
            bufferedSets,
            insertableSets);
        ingredientBuffer.removePatternSets(patternSlot, getLocalAggregatedIngredients(pattern), sets);
        if (mode != PipeItemsPatternCraftingLogistics.BlockingMode.OFF) {
            runningCraft = patternSlot;
            runningCraftInAdjacent = true;
        }
        debugEvent(
            "BUFFER",
            "push slot=%d buffer after insert remainingSets=%d runningCraft=%d adjacentBatch=%s",
            patternSlot,
            ingredientBuffer.completeSets(patternSlot, getLocalAggregatedIngredients(pattern)),
            runningCraft,
            runningCraftInAdjacent);
        requestIngredientsForStagedCrafts();
    }

    /**
     * Counts complete local ingredient sets currently buffered for one pattern slot.
     */
    private int completeBufferedSets(int patternSlot) {
        ItemStack pattern = getPatternStack(patternSlot);
        List<IPatternStack> localIngredients = getLocalAggregatedIngredients(pattern);
        int sets = localIngredients.isEmpty() ? 0 : ingredientBuffer.completeSets(patternSlot, localIngredients);
        debug("complete buffered sets slot=%d ingredients=%d sets=%d", patternSlot, localIngredients.size(), sets);
        return sets;
    }

    /**
     * Requests ingredients for all staged crafting orders that still have room in this module or the adjacent
     * inventory.
     */
    void requestIngredientsForStagedCrafts() {
        debugEventThrottled(
            "SCHED",
            20,
            "request staged crafts trigger bufferedSlots=%d requestedSlots=%d runningCraft=%d adjacentBatch=%s",
            ingredientBuffer.size(),
            requestedIngredients.size(),
            runningCraft,
            runningCraftInAdjacent);
        stagedCrafting.requestIngredients();
    }

    public boolean cancelPatternCraft(int patternSlot) {
        if (patternSlot < 0 || patternSlot >= patternHandler.size()) {
            return false;
        }
        boolean changed = stagedCrafting.cancelPattern(patternSlot);
        changed |= requestedIngredient.removeAll(patternSlot);
        changed |= flushBufferedIngredientsToStorage(patternSlot);
        if (runningCraft == patternSlot) {
            runningCraft = -1;
            runningCraftInAdjacent = false;
            changed = true;
        }
        if (changed) {
            cancelledPatternSlots.add(patternSlot);
            debugEvent("CANCEL", "cancelled pattern slot=%d and flushed buffer", patternSlot);
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        }
        return changed;
    }

    void clearCancelledPattern(int patternSlot) {
        cancelledPatternSlots.remove(patternSlot);
    }

    private boolean flushBufferedIngredientsToStorage(int patternSlot) {
        boolean sent = false;
        for (IPatternStack stack : ingredientBuffer.removeAll(patternSlot)) {
            for (ItemStack itemStack : PatternStackBufferHandler.makeItemStacks(stack)) {
                pipe.sendStack(itemStack, -1, CoreRoutedPipe.ItemSendMode.Normal, null);
                sent = true;
                debugEvent("CANCEL", "sent buffered ingredient to storage slot=%d stack=%s", patternSlot, stack);
            }
        }
        return sent;
    }

    /**
     * @return a pattern slot whose buffered arrived ingredients can be pushed as a complete set.
     */
    private int findCompleteBufferedPattern() {
        for (int patternSlot : ingredientBuffer.keySet()) {
            ItemStack pattern = getPatternStack(patternSlot);

            if (pattern == null) {
                ingredientBuffer.removeAll(patternSlot);
                continue;
            }

            if (completeBufferedSets(patternSlot) > 0) {
                return patternSlot;
            }
        }
        return -1;
    }

    /**
     * Marks the first arrived ingredient for a slot as the active blocking craft when no other slot is active.
     */
    private void activateRunningCraftFromBuffer(int patternSlot) {
        if (getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.OFF || runningCraft >= 0) {
            return;
        }
        runningCraft = patternSlot;
        runningCraftInAdjacent = false;
        debugEvent("BUFFER", "running craft activated from buffer slot=%d", patternSlot);
    }

    /**
     * Returns whether the active slot is still locked by arrived buffered ingredients or an inserted adjacent batch.
     */
    boolean isRunningCraftLocked() {
        refreshRunningCraftState(getConnectedInventoryTile());
        if (runningCraft < 0) {
            return false;
        }
        // if (hasBufferedIngredients(runningCraft)) {
        // return true;
        // }
        AdjacentTile connected = getConnectedInventoryTile();
        return runningCraftInAdjacent && connected != null && !isInventoryEmpty(connected);
    }

    /**
     * Releases stale blocking state and adopts already-buffered ingredients as the next active slot.
     */
    private void refreshRunningCraftState(AdjacentTile connected) {
        if (getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.OFF) {
            runningCraft = -1;
            runningCraftInAdjacent = false;
            return;
        }
        if (runningCraft >= 0 && getPatternStack(runningCraft) == null) {
            debugEvent("BUFFER", "running craft cleared slot=%d: pattern missing", runningCraft);
            runningCraft = -1;
            runningCraftInAdjacent = false;
        }
        if (runningCraft >= 0 && runningCraftInAdjacent && (connected == null || isInventoryEmpty(connected))) {
            debugEvent("BUFFER", "running craft adjacent batch finished slot=%d", runningCraft);
            runningCraftInAdjacent = false;
        }
        if (runningCraft >= 0 && !runningCraftInAdjacent && completeBufferedSets(runningCraft) <= 0) {
            debugEvent("BUFFER", "running craft released slot=%d: no arrived ingredients remain", runningCraft);
            runningCraft = -1;
        }
        if (runningCraft < 0) {
            runningCraftInAdjacent = false;
            int next = findCompleteBufferedPattern();
            if (next != -1) {
                runningCraft = next;
                runningCraftInAdjacent = false;
                debugEvent("BUFFER", "running craft selected buffered slot=%d", runningCraft);
            }
        }
    }

    /**
     * Refreshes blocking state once per tick after push/request processing.
     */
    private void clearRunningCraftIfFinished() {
        refreshRunningCraftState(getConnectedInventoryTile());
    }

    AdjacentTile getConnectedInventoryTile() {
        return adjacentInventory.getConnected();
    }

    private boolean isInventoryEmpty(AdjacentTile connected) {
        return adjacentInventory.isEmpty(connected);
    }

    /**
     * Determines whether this crafting pipe's outstanding output orders should be reported as destination-buffered.
     * <p>
     * The network-wide can-sink lookup may route back to this same module when a pattern requests a subitem that
     * another pattern in this pipe crafts. Self-destined orders are already represented by
     * {@link #requestedIngredients}, so they are treated as locally buffered here and are not sent through
     * {@link LogisticsManager#canSink} again.
     */
    private boolean areAllOrdersBuffered() {
        if (checkingBufferedOrders) {
            return true;
        }
        checkingBufferedOrders = true;
        try {
            boolean result = true;
            for (LogisticsItemOrder order : pipe.getItemOrderManager()) {
                if (isOrderDestinationThisModule(order)) {
                    debug(
                        "buffer check treats self-destined order as buffered order=%s amount=%d",
                        order.getResource().getItem(),
                        order.getAmount());
                    continue;
                }
                if (order.getDestination() instanceof IItemSpaceControl) {
                    SinkReply reply = LogisticsManager.canSink(
                        order.getDestination().getRouter(),
                        null,
                        true,
                        order.getResource().getItem(),
                        null,
                        true,
                        false);
                    if (reply != null && reply.bufferMode == BufferMode.NONE && reply.maxNumberOfItems >= 1) {
                        debug(
                            "buffer check found unbuffered destination order=%s replyRoom=%d",
                            order.getResource().getItem(),
                            reply.maxNumberOfItems);
                        result = false;
                        break;
                    }
                } else {
                    debug(
                        "buffer check found destination without space control order=%s",
                        order.getResource().getItem());
                    result = false;
                    break;
                }
            }
            debug("buffer check result=%s", result);
            return result;
        } finally {
            checkingBufferedOrders = false;
        }
    }

    boolean isOrderDestinationThisModule(LogisticsItemOrder order) {
        IRequest destination = order.getDestination();
        return destination == this || (destination != null && destination.getRouter() == getRouter());
    }

    boolean isOrderDestinationThisModule(LogisticsFluidOrder order) {
        IRequest destination = order.getDestination();
        return destination == this || (destination != null && destination.getRouter() == getRouter());
    }

    int requestedSamePipeItemAmount(LogisticsItemOrder order) {
        if (!(order.getInformation() instanceof PatternTargetInformation)) {
            return order.getAmount();
        }
        int patternSlot = ((PatternTargetInformation) order.getInformation()).patternSlot();
        return requestedIngredient.amount(patternSlot, order.getResource().getItem());
    }

    int requestedSamePipeFluidAmount(LogisticsFluidOrder order) {
        if (!(order.getInformation() instanceof PatternTargetInformation)) {
            return order.getAmount();
        }
        int patternSlot = ((PatternTargetInformation) order.getInformation()).patternSlot();
        return requestedIngredient.amount(patternSlot, order.getFluid());
    }

    private void appendConnectedInventoryDebug(StringBuilder out) {
        AdjacentTile connected = adjacentInventory.getConnected();
        if (connected == null) {
            out.append("  connected inventory: <none>\n");
            return;
        }
        if (connected.tile == null) {
            out.append("  connected inventory: <null tile> side=").append(connected.orientation).append("\n");
            return;
        }
        out.append("  connected inventory: ").append(connected.tile.getClass().getName()).append(" side=")
            .append(connected.orientation).append(" empty=").append(adjacentInventory.isEmpty(connected))
            .append(" patternTable=").append(adjacentInventory.isConnectedToPatternCraftingTable()).append("\n");
    }

    private void appendPatternDebug(StringBuilder out) {
        out.append("  patterns:\n");
        boolean found = false;
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = getPatternStack(slot);
            if (pattern == null) {
                continue;
            }
            found = true;
            out.append("    slot ").append(slot).append("\n");
            AbstractPattern configuredPattern = ItemPattern.fromStack(pattern);
            appendPatternSlots(out, configuredPattern, 0, configuredPattern.getIngredientSlotCount(), "      inputs");
            appendPatternSlots(
                out,
                configuredPattern,
                configuredPattern.getResultSlotStart(),
                configuredPattern.getItemSlotCount(),
                "      results");
        }
        if (!found) {
            out.append("    <none>\n");
        }
    }

    private void appendPatternSlots(StringBuilder out, AbstractPattern pattern, int start, int end, String label) {
        out.append(label).append(": ");
        boolean found = false;
        for (int slot = start; slot < end; slot++) {
            IPatternStack stack = pattern.getPatternStackInSlot(slot);
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }
            if (found) {
                out.append(", ");
            }
            out.append("slot ").append(slot).append("=").append(stack);
            found = true;
        }
        if (!found) {
            out.append("<none>");
        }
        out.append("\n");
    }

    private void appendStackMapDebug(StringBuilder out, String label,
                                     Map<Integer, List<IPatternStack>> stacksByPattern) {
        out.append("  ").append(label).append(":\n");
        if (stacksByPattern.isEmpty()) {
            out.append("    <none>\n");
            return;
        }
        for (Map.Entry<Integer, List<IPatternStack>> entry : stacksByPattern.entrySet()) {
            out.append("    slot ").append(entry.getKey()).append(": ");
            appendInlineStacks(out, entry.getValue());
            out.append("\n");
        }
    }

    private void appendStagedCraftDebug(StringBuilder out) {
        out.append("  staged crafts:\n");
        stagedCrafting.appendDebugState(out, "    ");
    }

    private void appendOrderDebug(StringBuilder out) {
        out.append("  output orders:\n");
        boolean found = false;
        for (LogisticsItemOrder order : pipe.getItemOrderManager()) {
            found = true;
            out.append("    - ").append(order.getType()).append(" ").append(order.getAmount()).append("x ")
                .append(order.getResource().getItem()).append(" -> router ").append(order.getRouterId())
                .append(order.isInProgress() ? " in-progress" : "").append(order.isFinished() ? " finished" : "");
            if (order.getInformation() != null) {
                out.append(" info=").append(order.getInformation());
            }
            out.append("\n");
        }
        if (!found) {
            out.append("    <none>\n");
        }
    }

    private void appendInlineStacks(StringBuilder out, List<IPatternStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            out.append("<none>");
            return;
        }
        for (int i = 0; i < stacks.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            IPatternStack stack = stacks.get(i);
            out.append(stack == null ? "<null>" : stack.toString());
        }
    }

    /**
     * Re-requests ingredients whose routed item or fluid container was lost before reaching this module.
     */
    private void retryLostItems() {
        DelayedGeneric<Pair<IPatternStack, IAdditionalTargetInformation>> lost = lostIngredients.poll();
        int rerequested = 0;
        while (lost != null && rerequested < 100) {
            Pair<IPatternStack, IAdditionalTargetInformation> pair = lost.get();
            IPatternStack stack = pair.getValue1();
            int received = requestLostIngredient(stack, pair.getValue2());
            debugEvent(
                "REQUEST",
                "lost retry ingredient=%s requested=%d received=%d info=%s",
                stack,
                stack.getAmount(),
                received,
                pair.getValue2());
            rerequested++;
            if (received < stack.getAmount()) {
                IPatternStack remaining = PatternStackHelper.copyWithAmount(stack, stack.getAmount() - received);
                if (remaining != null) {
                    debugEvent("REQUEST", "lost retry requeued remaining=%s", remaining);
                    lostIngredients.add(
                        new DelayedGeneric<>(
                            new Pair<>(remaining, pair.getValue2()),
                            4500 + (int) (Math.random() * 1000)));
                }
            }
            lost = lostIngredients.poll();
        }
    }

    /**
     * Places a partial request for a lost item or fluid ingredient.
     */
    private int requestLostIngredient(IPatternStack stack, IAdditionalTargetInformation info) {
        int originalAmount = stack == null ? 0 : stack.getAmount();
        IPatternStack outstanding = outstandingRequestedRetryStack(stack, info);
        if (outstanding == null || outstanding.getAmount() <= 0) {
            return originalAmount;
        }
        if (info instanceof PatternTargetInformation && hasLivePatternOutputOrderForRequested(
                ((PatternTargetInformation) info).patternSlot(),
                outstanding)) {
            debugEvent(
                "REQUEST",
                "lost retry skipped, live pattern output still pending ingredient=%s info=%s",
                outstanding,
                info);
            return originalAmount;
        }

        ItemIdentifierStack item = PatternStackHelper.asSolidStack(outstanding);
        if (item != null) {
            debugEvent("REQUEST", "lost retry requesting item=%s info=%s", item, info);
            return originalAmount - outstanding.getAmount() + RequestTree.requestPartial(item.clone(), pipe, info);
        }
        FluidIdentifier fluid = PatternStackHelper.asFluid(outstanding);
        if (fluid != null) {
            debugEvent(
                "REQUEST",
                "lost retry requesting fluid=%s amount=%d info=%s",
                fluid,
                outstanding.getAmount(),
                info);
            return originalAmount - outstanding.getAmount()
                + RequestTree.requestFluidPartial(fluid, outstanding.getAmount(), this, null, info);
        }
        return 0;
    }

    private IPatternStack outstandingRequestedRetryStack(IPatternStack stack, IAdditionalTargetInformation info) {
        if (!(info instanceof PatternTargetInformation) || stack == null || stack.getAmount() <= 0) {
            return stack;
        }
        int patternSlot = ((PatternTargetInformation) info).patternSlot();
        int outstanding = 0;
        ItemIdentifierStack item = PatternStackHelper.asSolidStack(stack);
        if (item != null) {
            outstanding = requestedIngredient.amount(patternSlot, item.getItem());
        } else {
            FluidIdentifier fluid = PatternStackHelper.asFluid(stack);
            if (fluid != null) {
                outstanding = requestedIngredient.amount(patternSlot, fluid);
            }
        }
        return PatternStackHelper.copyWithAmount(stack, Math.min(stack.getAmount(), outstanding));
    }

    private boolean hasLivePatternOutputOrderForRequested(int patternSlot, IPatternStack stack) {
        int outstanding = stack == null ? 0 : stack.getAmount();
        if (outstanding <= 0) {
            return false;
        }
        int covered = 0;
        for (IRouter router : SimpleServiceLocator.routerManager.getRouters()) {
            if (router == null || !(router.getPipe() instanceof PipeItemsPatternCraftingLogistics patternPipe)) {
                continue;
            }
            covered += liveItemOutputAmountFor(patternPipe, patternSlot, stack);
            covered += liveFluidOutputAmountFor(patternPipe, patternSlot, stack);
            if (covered >= outstanding) {
                return true;
            }
        }
        return false;
    }

    private int liveItemOutputAmountFor(PipeItemsPatternCraftingLogistics patternPipe, int patternSlot,
                                        IPatternStack stack) {
        ItemIdentifierStack item = PatternStackHelper.asSolidStack(stack);
        if (item == null) {
            return 0;
        }
        int amount = 0;
        for (LogisticsItemOrder order : patternPipe.getItemOrderManager()) {
            if (order.isFinished() || !item.getItem().equalsForCrafting(order.getResource().getItem())
                || !isOrderTargetingThisPattern(order, patternSlot)) {
                continue;
            }
            amount += Math.max(0, order.getAmount());
        }
        return amount;
    }

    private int liveFluidOutputAmountFor(PipeItemsPatternCraftingLogistics patternPipe, int patternSlot,
                                         IPatternStack stack) {
        FluidIdentifier fluid = PatternStackHelper.asFluid(stack);
        if (fluid == null) {
            return 0;
        }
        int amount = 0;
        for (LogisticsFluidOrder order : patternPipe.getPatternFluidOrderManager()) {
            if (order.isFinished() || !fluid.equals(order.getFluid())
                || !isOrderTargetingThisPattern(order, patternSlot)) {
                continue;
            }
            amount += Math.max(0, order.getAmount());
        }
        return amount;
    }

    private boolean isOrderTargetingThisPattern(LogisticsItemOrder order, int patternSlot) {
        return order.getDestination() != null && order.getDestination().getRouter() == getRouter()
            && order.getInformation() instanceof PatternTargetInformation
            && ((PatternTargetInformation) order.getInformation()).patternSlot() == patternSlot;
    }

    private boolean isOrderTargetingThisPattern(LogisticsFluidOrder order, int patternSlot) {
        return order.getDestination() != null && order.getDestination().getRouter() == getRouter()
            && order.getInformation() instanceof PatternTargetInformation
            && ((PatternTargetInformation) order.getInformation()).patternSlot() == patternSlot;
    }

    private void readLostIngredientsFromNBT(NBTTagCompound tag) {
        lostIngredients.clear();
        NBTTagList lost = tag.getTagList(LOST_INGREDIENTS_TAG, TAG_COMPOUND);
        for (int i = 0; i < lost.tagCount(); i++) {
            NBTTagCompound stackTag = lost.getCompoundTagAt(i);
            IPatternStack stack = IPatternStack.readFromNBT(stackTag);
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }
            long delay = Math.max(1, stackTag.getLong(LOST_DELAY_TAG));
            lostIngredients.add(new DelayedGeneric<>(new Pair<>(stack, readTargetInformation(stackTag)), delay));
        }
    }

    private void writeLostIngredientsToNBT(NBTTagCompound tag) {
        NBTTagList lost = new NBTTagList();
        for (DelayedGeneric<Pair<IPatternStack, IAdditionalTargetInformation>> queued : lostIngredients) {
            Pair<IPatternStack, IAdditionalTargetInformation> pair = queued.get();
            IPatternStack stack = pair.getValue1();
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            stackTag.setLong(LOST_DELAY_TAG, Math.max(1, queued.getDelay(TimeUnit.NANOSECONDS) / 1000));
            writeTargetInformation(stackTag, pair.getValue2());
            lost.appendTag(stackTag);
        }
        tag.setTag(LOST_INGREDIENTS_TAG, lost);
    }

    private IAdditionalTargetInformation readTargetInformation(NBTTagCompound tag) {
        if (!tag.hasKey(TARGET_PATTERN_SLOT_TAG)) {
            return null;
        }
        return new PatternTargetInformation(tag.getInteger(TARGET_PATTERN_SLOT_TAG));
    }

    private void writeTargetInformation(NBTTagCompound tag, IAdditionalTargetInformation info) {
        if (info instanceof PatternTargetInformation) {
            tag.setInteger(TARGET_PATTERN_SLOT_TAG, ((PatternTargetInformation) info).patternSlot());
        }
    }

    public void onAllowedRemoval() {

        World world = pipe.getWorld();

        stagedCrafting.releaseAll();
        requestedIngredients.clear();
        cancelledPatternSlots.clear();

        patternInventory.dropContents(world, pipe.getX(), pipe.getY(), pipe.getZ());
        ingredientBuffer.dropContents(world, pipe.getX(), pipe.getY(), pipe.getZ());
    }

    private static class ThrottledDebugEvent {

        private long lastLoggedTick = Long.MIN_VALUE;
        private int suppressed;
    }

}
