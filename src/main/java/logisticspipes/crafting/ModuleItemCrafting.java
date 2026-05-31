package logisticspipes.crafting;

import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.interfaces.routing.*;
import logisticspipes.logistics.LogisticsManager;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.debug.CraftingRequestDebugManager;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
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
import logisticspipes.utils.*;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.SinkReply.BufferMode;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.item.SimpleStackInventory;
import logisticspipes.utils.tuples.Pair;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import java.util.*;
import java.util.concurrent.DelayQueue;

public class ModuleItemCrafting extends LogisticsGuiModule implements ICraftItems, ICraftFluids, IRequestFluid,
        IRequireReliableTransport, IStagedCraftingProvider {

    private final PipeItemsPatternCraftingLogistics pipe;
    private final SimpleStackInventory patternInventory = new SimpleStackInventory(9, "Patterns", 1);
    private final Map<Integer, List<IPatternStack>> bufferedIngredients = new HashMap<>();
    private final Map<Integer, List<IPatternStack>> requestedIngredients = new HashMap<>();
    private final List<PatternCraftingOrder> stagedCrafts = new ArrayList<>();
    private final DelayQueue<DelayedGeneric<Pair<IPatternStack, IAdditionalTargetInformation>>> lostIngredients = new DelayQueue<>();
    private final PatternHandler patternHandler = new PatternHandler(patternInventory);
    private final AdjacentInventoryHandler adjacentInventory;
    private final PatternStackBufferHandler ingredientBuffer = new PatternStackBufferHandler(bufferedIngredients);
    private final PatternStackRequestHandler requestedIngredient = new PatternStackRequestHandler(requestedIngredients);
    private final PatternCraftingTemplateBuilder templateBuilder;
    private final PatternCraftingResultExtractor resultExtractor;
    private SinkReply sinkReply;
    private PipeItemsPatternCraftingLogistics.BlockingMode blockingMode = PipeItemsPatternCraftingLogistics.BlockingMode.OFF;
    private int runningCraft = -1;
    private boolean runningCraftInAdjacent = false;
    private final Set<Integer> requestingStagedIngredientPatterns = new HashSet<>();
    private boolean checkingBufferedOrders = false;

    public ModuleItemCrafting(PipeItemsPatternCraftingLogistics pipe) {
        this.pipe = pipe;
        adjacentInventory = new AdjacentInventoryHandler(this, pipe);
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

    private PipeItemsPatternCraftingLogistics.BlockingMode getEffectiveBlockingMode() {
        if (adjacentInventory.isConnectedToPatternCraftingTable()) {
            return PipeItemsPatternCraftingLogistics.BlockingMode.SMART;
        }
        return blockingMode;
    }

    @Override
    protected ModuleCoordinatesGuiProvider getPipeGuiProvider() {
        return NewGuiHandler.getGui(PatternCraftingPipeGuiProvider.class).setBlockingMode(getBlockingMode().ordinal());
    }

    @Override
    protected ModuleInHandGuiProvider getInHandGuiProvider() {
        return null;
    }

    /**
     * Reports how many ingredients this module can currently receive for its configured patterns.
     * <p>
     * The result includes reserved space for ingredients this module has already requested through staged crafting, so
     * subcraft results from this same pipe can be routed back into the module buffer instead of being rejected while the
     * connected inventory is busy.
     */
    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault, boolean includeInTransit) {
        if (bestPriority > sinkReply.fixedPriority.ordinal() || (bestPriority == sinkReply.fixedPriority.ordinal() && bestCustomPriority >= sinkReply.customPriority)) {
            return null;
        }
        FluidIdentifier fluid = item != null && item.isFluidContainer() ? FluidIdentifier.get(item) : null;
        if (fluid != null) {
            int room = spaceForFluid(fluid, includeInTransit);
            if (room <= 0) {
                debug("sink rejected fluid ingredient %s includeInTransit=%s", fluid, includeInTransit);
                return null;
            }
            debug("sink accepts fluid ingredient %s room=%d includeInTransit=%s", fluid, room, includeInTransit);
            return new SinkReply(sinkReply, room, areAllOrdersBuffered() ? BufferMode.DESTINATION_BUFFERED : BufferMode.NONE);
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
        return new SinkReply(sinkReply, room, areAllOrdersBuffered() ? BufferMode.DESTINATION_BUFFERED : BufferMode.NONE);
    }

    /**
     * Reports how much of a routed fluid container can currently be accepted as a pattern ingredient.
     * <p>
     * The pattern pipe advertises itself as a fluid sink so storage routing can find it, but the accepted fluid is still
     * delivered as a LogisticsFluidContainer item and buffered by this module.
     */
    public int sinkAmount(FluidStack stack) {
        if (stack == null || stack.amount <= 0) {
            return 0;
        }
        int room = spaceForFluid(FluidIdentifier.get(stack), true);
        debug("fluid sink amount check %s amount=%d room=%d", FluidIdentifier.get(stack), stack.amount, room);
        return room >= stack.amount ? stack.amount : 0;
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    @Override
    public void tick() {
        retryLostItems();
        pushBufferedIngredients();
        requestIngredientsForStagedCrafts();
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
            AbstractPattern configuredPattern = Pattern.fromStack(pattern);
            for (IPatternStack result : configuredPattern.getOutputs()) {
                ItemIdentifier item = PatternStackHelper.getRoutingItem(result);
                if (item != null) {
                    crafted.add(item);
                }
            }
        }
        return crafted;
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
    public net.minecraft.util.IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleCrafter");
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        patternInventory.readFromNBT(tag, "PatternCrafting");
        blockingMode = PipeItemsPatternCraftingLogistics.BlockingMode.values()[Math.max(0, Math.min(PipeItemsPatternCraftingLogistics.BlockingMode.values().length - 1, tag.getInteger("patternBlockingMode")))];
        runningCraft = tag.hasKey("runningCraft") ? tag.getInteger("runningCraft") : tag.getInteger("bufferedPatternSlot");
        runningCraftInAdjacent = tag.hasKey("runningCraftInAdjacent") ? tag.getBoolean("runningCraftInAdjacent") : runningCraft >= 0;
        bufferedIngredients.clear();
        NBTTagList buffer = tag.getTagList("patternIngredientBuffer", tag.getId());
        for (int i = 0; i < buffer.tagCount(); i++) {
            NBTTagCompound stackTag = buffer.getCompoundTagAt(i);
            int patternSlot = stackTag.getInteger("patternSlot");
            IPatternStack stack = IPatternStack.readFromNBT(stackTag);
            if (stack != null) {
                getBuffer(patternSlot).add(stack);
            }
        }
        NBTTagList fluidBuffer = tag.getTagList("patternFluidIngredientBuffer", tag.getId());
        for (int i = 0; i < fluidBuffer.tagCount(); i++) {
            NBTTagCompound fluidTag = fluidBuffer.getCompoundTagAt(i);
            int patternSlot = fluidTag.getInteger("patternSlot");
            PatternFluidStack fluid = PatternFluidStack.readFromNBT(fluidTag);
            if (fluid != null) {
                getBuffer(patternSlot).add(fluid);
            }
        }
        debug("loaded patterns=%d bufferedSlots=%d runningCraft=%d adjacentBatch=%s mode=%s",
                patternInventory.getSizeInventory(),
                bufferedIngredients.size(),
                runningCraft,
                runningCraftInAdjacent,
                blockingMode);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        patternInventory.writeToNBT(tag, "PatternCrafting");
        tag.setInteger("patternBlockingMode", blockingMode.ordinal());
        tag.setInteger("runningCraft", runningCraft);
        tag.setInteger("bufferedPatternSlot", runningCraft);
        tag.setBoolean("runningCraftInAdjacent", runningCraftInAdjacent);
        NBTTagList buffer = new NBTTagList();
        for (Map.Entry<Integer, List<IPatternStack>> entry : bufferedIngredients.entrySet()) {
            for (IPatternStack stack : entry.getValue()) {
                NBTTagCompound stackTag = new NBTTagCompound();
                stack.writeToNBT(stackTag);
                stackTag.setInteger("patternSlot", entry.getKey());
                buffer.appendTag(stackTag);
            }
        }
        tag.setTag("patternIngredientBuffer", buffer);
        debug("saved buffered ingredient slots=%d runningCraft=%d adjacentBatch=%s mode=%s",
                bufferedIngredients.size(),
                runningCraft,
                runningCraftInAdjacent,
                blockingMode);
    }

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

    void debugEvent(String category, String message, Object... args) {
        debug(message, args);
        CraftingRequestDebugManager.recordPipeEvent(pipe, category, message, args);
    }

    /**
     * Offers already-registered extra outputs to a request tree before new crafting work is considered.
     * <p>
     * Item extras and fluid extras are checked against their own order managers. If an extra is consumed, fulfilment will
     * remove or reduce the destinationless extra order so the byproduct is not extracted twice.
     */
    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        IResource requested = tree.getRequestType();
        if (pipe.getPatternFluidOrderManager().hasExtras()
                && !tree.hasBeenQueried(pipe.getPatternFluidOrderManager())
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
            if (order.getType() == ResourceType.EXTRA && requested.matches(order.getResource().getItem(), IResource.MatchSettings.NORMAL)) {
                int amount = Math.min(order.getAmount(), tree.getMissingAmount());
                if (amount > 0) {
                    debug("providing extra %s amount=%d for request %s", order.getResource().getItem(), amount, requested);
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
     * When an extra promise is used to satisfy a request, its destinationless extra order is removed first; the new order
     * then targets the real requester like a normal craft output.
     */
    @Override
    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination, IAdditionalTargetInformation info) {
        if (promise instanceof LogisticsExtraPromise) {
            pipe.getItemOrderManager().removeExtras(new logisticspipes.request.resources.DictResource(new ItemIdentifierStack(promise.item, promise.numberOfItems), null));
        }
        pipe.spawnParticle(Particles.WhiteParticle, 2);
        debugEvent("ORDER", "create item output order item=%s amount=%d destination=%s info=%s",
                promise.item,
                promise.numberOfItems,
                destination,
                info);
        return pipe.getItemOrderManager().addOrder(new ItemIdentifierStack(promise.item, promise.numberOfItems), destination, ResourceType.CRAFTING, info);
    }

    /**
     * Starts a staged craft from a request-tree branch.
     * <p>
     * The output order stays in this pipe's order manager, while the branch is kept so this module can request only the
     * ingredient sets that currently fit in its buffer or connected inventory.
     */
    @Override
    public IOrderInfoProvider fullFillStagedCrafting(
            IPromise promise,
            IResource requestType,
            IAdditionalTargetInformation info,
            PatternCraftingBranch branch) {
        if (!hasRequestTarget(promise, requestType)) {
            debugEvent("STAGED", "staged craft rejected without target promise=%s request=%s info=%s", promise, requestType, info);
            return null;
        }
        debugEvent("STAGED", "staged craft start promise=%s amount=%d request=%s info=%s branch=%s",
                promise.getItemType(),
                promise.getAmount(),
                requestType,
                info,
                branch == null ? "<none>" : "available");
        IOrderInfoProvider order = promise.fullFill(requestType, info);
        int patternSlot = getPatternSlotForPromise(promise);
        int resultAmountPerSet = getResultAmountPerSet(promise, patternSlot);
        debug("staged craft output order=%s patternSlot=%d resultAmountPerSet=%d",
                order == null ? "<none>" : order.getAsDisplayItem(),
                patternSlot,
                resultAmountPerSet);
        if (patternSlot >= 0 && branch != null && order != null) {
            PatternCraftingOrder stagedOrder = new PatternCraftingOrder(
                    patternSlot,
                    resultAmountPerSet,
                    branch,
                    order,
                    this,
                    patternHandler,
                    requestedIngredient);
            stagedCrafts.add(stagedOrder);
            PatternCraftingMonitorRegistry.register(order, stagedOrder);
            debugEvent("STAGED", "staged craft registered slot=%d remainingSets=%d ingredientBranches=%d",
                    patternSlot,
                    stagedOrder.remainingSets,
                    stagedOrder.ingredientBranches.size());
            requestIngredientsForStagedCrafts(patternSlot);
        }
        return order;
    }

    /**
     * Checks whether a promise can be fulfilled as a staged craft for the supplied request resource.
     */
    private boolean hasRequestTarget(IPromise promise, IResource requestType) {
        if (promise instanceof FluidLogisticsPromise) {
            return requestType instanceof FluidResource && ((FluidResource) requestType).getTarget() != null;
        }
        return getRequestTarget(requestType) != null;
    }

    /**
     * Extracts the item requester from an item resource variant.
     */
    private IRequestItems getRequestTarget(IResource requestType) {
        if (requestType instanceof ItemResource) {
            return ((ItemResource) requestType).getTarget();
        }
        if (requestType instanceof logisticspipes.request.resources.DictResource) {
            return ((logisticspipes.request.resources.DictResource) requestType).getTarget();
        }
        return null;
    }

    /**
     * Resolves the pattern slot associated with a crafting promise.
     * <p>
     * Pattern promises carry this directly; older generic promises fall back to matching the result against configured
     * pattern outputs.
     */
    private int getPatternSlotForPromise(IPromise promise) {
        if (promise instanceof PatternCraftingPromise) {
            return ((PatternCraftingPromise) promise).getPatternSlot();
        }
        if (promise instanceof PatternFluidCraftingPromise) {
            return ((PatternFluidCraftingPromise) promise).getPatternSlot();
        }
        return patternHandler.findPatternSlotForResult(promise.getItemType());
    }

    /**
     * Resolves how much output one pattern set creates for the given promise.
     */
    private int getResultAmountPerSet(IPromise promise, int patternSlot) {
        if (promise instanceof PatternCraftingPromise) {
            return ((PatternCraftingPromise) promise).getResultAmountPerSet();
        }
        if (promise instanceof PatternFluidCraftingPromise) {
            return ((PatternFluidCraftingPromise) promise).getResultAmountPerSet();
        }
        return Math.max(1, patternHandler.resultAmount(patternSlot, promise.getItemType()));
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {
    }

    @Override
    public Map<FluidIdentifier, Integer> getAvailableFluids() {
        return Collections.emptyMap();
    }

    /**
     * Creates a fluid output order for a pattern craft or for a previously registered fluid extra.
     * <p>
     * Consumed fluid extras are removed from the destinationless extra-order queue before the new requester-targeted
     * order is added.
     */
    @Override
    public IOrderInfoProvider fullFill(
            FluidLogisticsPromise promise,
            IRequestFluid destination,
            ResourceType type,
        IAdditionalTargetInformation info) {
        ResourceType orderType = type;
        if (promise instanceof FluidExtraPromise) {
            pipe.getPatternFluidOrderManager().removeExtras(promise.getLiquid(), promise.getAmount());
            orderType = ResourceType.CRAFTING;
        }
        pipe.spawnParticle(Particles.WhiteParticle, 2);
        debugEvent("ORDER", "create fluid output order fluid=%s amount=%d destination=%s info=%s",
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
        if (promise instanceof FluidLogisticsPromise) {
            FluidLogisticsPromise fluidPromise = (FluidLogisticsPromise) promise;
            debugEvent("EXTRA", "register fluid extra %s amount=%d", fluidPromise.getLiquid(), fluidPromise.getAmount());
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
        pipe.getItemOrderManager().addExtra(new DictResource(new ItemIdentifierStack(promise.getItemType(), promise.getAmount()), null));
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
            AbstractPattern configuredPattern = Pattern.fromStack(pattern);
            for (IPatternStack output : configuredPattern.getOutputs()) {
                ItemIdentifierStack display = PatternStackHelper.makeDisplayStack(output);
                if (display != null) {
                    results.add(display);
                }
            }
        }
        return results;
    }

    /**
     * Appends the current module-side staged crafting state to the crafting request debug dump.
     */
    public void appendDebugState(StringBuilder out) {
        out.append("Pattern crafting pipe at ")
                .append(pipe.getX())
                .append(", ")
                .append(pipe.getY())
                .append(", ")
                .append(pipe.getZ())
                .append(" router=")
                .append(pipe.getRouter().getSimpleID())
                .append("\n");
        out.append("  mode stored=")
                .append(blockingMode)
                .append(" effective=")
                .append(getEffectiveBlockingMode())
                .append(" fixed=")
                .append(isBlockingModeFixed())
                .append(" runningCraft=")
                .append(runningCraft)
                .append(" adjacentBatch=")
                .append(runningCraftInAdjacent)
                .append("\n");
        appendConnectedInventoryDebug(out);
        appendPatternDebug(out);
        appendStackMapDebug(out, "buffered ingredients", bufferedIngredients);
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
                requestedIngredient.remove(patternSlot, patternFluid, fluid.amount);
                debugEvent("FLOW", "lost fluid ingredient slot=%d fluid=%s amount=%d removed from requested and queued retry",
                        patternSlot,
                        FluidIdentifier.get(fluid),
                        fluid.amount);
                lostIngredients.add(new DelayedGeneric<>(
                        new Pair<>(new PatternFluidStack(FluidIdentifier.get(fluid), fluid.amount), info),
                        5000));
                return;
            }
            requestedIngredient.remove(patternSlot, new PatternSolidStack(item.clone()), item.getStackSize());
            debugEvent("FLOW", "lost item ingredient slot=%d item=%s amount=%d removed from requested",
                    patternSlot,
                    item.getItem(),
                    item.getStackSize());
        }
        if (item != null) {
            lostIngredients.add(new DelayedGeneric<>(new Pair<>(new PatternSolidStack(item.clone()), info), 5000));
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
                int patternSlot = fluid != null ? findFluidArrivalPattern(FluidIdentifier.get(fluid)) : -1;
                if (patternSlot >= 0) {
                    fluidArrived(patternSlot, getPatternStack(patternSlot), item, fluid);
                }
            }
            return;
        }
        int patternSlot = ((PatternTargetInformation) info).patternSlot();
        ItemStack pattern = getPatternStack(patternSlot);
        FluidStack fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(item);
        if (fluid != null) {
            fluidArrived(patternSlot, pattern, item, fluid);
            return;
        }
        if (pattern == null || !patternContains(pattern, item.getItem())) {
            debugEvent("FLOW", "item arrival rejected slot=%d item=%s pattern=%s contains=%s",
                    patternSlot,
                    item,
                    pattern,
                    pattern != null && patternContains(pattern, item.getItem()));
            return;
        }

        int original = item.getStackSize();
        int requested = requestedIngredient.amount(patternSlot, item.getItem());
        int accepted = Math.min(original, Math.max(requested, spaceForArrivingIngredient(patternSlot, pattern, item.getItem())));
        debugEvent("FLOW", "item arrived slot=%d item=%s original=%d requested=%d accepted=%d",
                patternSlot,
                item.getItem(),
                original,
                requested,
                accepted);
        requestedIngredient.remove(patternSlot, new PatternSolidStack(new ItemIdentifierStack(item.getItem(), accepted)), accepted);
        if (accepted > 0) {
            ingredientBuffer.add(patternSlot, new PatternSolidStack(new ItemIdentifierStack(item.getItem(), accepted)));
            activateRunningCraftFromBuffer(patternSlot);
            pushBufferedIngredientsFor(patternSlot);
        }
        item.setStackSize(original - accepted);
        if (accepted > 0) {
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        }
    }

    /**
     * Accepts a routed fluid container as a buffered fluid ingredient for one pattern slot.
     * <p>
     * The routed item is consumed only when the full fluid amount can fit in the staged buffer. Partial acceptance would
     * split the opaque LogisticsFluidContainer item and lose the exact routed-fluid accounting.
     */
    private void fluidArrived(int patternSlot, ItemStack pattern, ItemIdentifierStack routedStack, FluidStack fluidStack) {
        FluidIdentifier fluid = FluidIdentifier.get(fluidStack);
        if (pattern == null || patternHandler.fluidIngredientAmount(pattern, fluid) <= 0) {
            debugEvent("FLOW", "fluid arrival rejected slot=%d fluid=%s amount=%d pattern=%s",
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
        debugEvent("FLOW", "fluid arrived slot=%d fluid=%s original=%d requested=%d space=%d accepted=%d",
                patternSlot,
                fluid,
                original,
                requested,
                space,
                accepted);
        requestedIngredient.remove(patternSlot, new PatternFluidStack(fluid, accepted), accepted);
        if (accepted > 0) {
            ingredientBuffer.add(patternSlot, new PatternFluidStack(fluid, accepted));
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
            if (fallback < 0 && canReceiveForPattern(slot) && spaceForPatternFluidIngredient(slot, pattern, fluid) > 0) {
                fallback = slot;
            }
        }
        debug("fluid arrival pattern lookup fluid=%s selected=%d", fluid, fallback);
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
        if (getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING
                && connected != null
                && adjacentInventory.isEmpty(connected)
                && ingredientBuffer.canCompleteOneSetAfterAdding(
                        patternSlot,
                        getLocalAggregatedIngredients(pattern),
                        new PatternSolidStack(new ItemIdentifierStack(item, space)))) {
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
        if (getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING
                && connected != null
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
     * This is used before accepting an extra blocking-mode fluid set so a fluid-only buffer cannot start a craft without
     * its matching solid ingredients.
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
     * Returns the number of items this module can still sink for any configured pattern using the item as an ingredient.
     * <p>
     * Requested ingredients reserve module buffer space for in-flight staged crafts. They remain sinkable even if the
     * adjacent inventory cannot accept another full pattern set yet; otherwise a subrequest from the same pipe can be
     * sent away as a lost item and recursively ask this method again through storage.
     */
    private int spaceFor(ItemIdentifier item, boolean includeInTransit) {
        int count = 0;
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = patternHandler.getConfiguredPatternStack(slot);
            if (pattern == null || localIngredientAmount(pattern, item) <= 0) {
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
        int count = 0;
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = patternHandler.getConfiguredPatternStack(slot);
            if (pattern == null || patternHandler.fluidIngredientAmount(pattern, fluid) <= 0) {
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
        return Math.max(0, count);
    }

    /**
     * Returns whether a pattern slot is currently allowed to receive more local ingredients.
     * <p>
     * Blocking modes restrict buffering to the active craft or to an empty connected inventory.
     */
    private boolean canReceiveForPattern(int patternSlot) {
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
        debug("pattern item capacity slot=%d item=%s sets=%d capacity=%d buffered=%d room=%d",
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
        debug("pattern fluid capacity slot=%d fluid=%s sets=%d capacity=%d buffered=%d room=%d",
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
    private int spaceForPatternIngredient(int patternSlot, ItemStack pattern, IPatternStack ingredient) {
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
        AbstractPattern configuredPattern = Pattern.fromStack(pattern);
        for (int slot = 0; slot < configuredPattern.getIngredientSlotCount(); slot++) {
            IPatternStack stack = configuredPattern.getPatternStackInSlot(slot);
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }
            if (PatternStackHelper.isSolid(stack) && getSatelliteTargetForInputSlot(configuredPattern, slot) != null) {
                continue;
            }
            PatternStackHelper.addAggregated(result, stack);
        }
        return result;
    }

    /**
     * Resolves the satellite destination for an item ingredient in a pattern.
     * <p>
     * If duplicate input slots contain the same item and only some are assigned to satellites, the assigned satellite is
     * used for staged routing of that item. Keep pattern assignments uniform for duplicate ingredients when splitting
     * the same item between local and satellite machines matters.
     */
    IRequestItems getSatelliteTargetForIngredient(ItemStack pattern, ItemIdentifier item) {
        if (pattern == null || item == null) {
            return null;
        }
        AbstractPattern configuredPattern = Pattern.fromStack(pattern);
        for (int slot = 0; slot < configuredPattern.getIngredientSlotCount(); slot++) {
            IPatternStack stack = configuredPattern.getPatternStackInSlot(slot);
            if (!(stack instanceof PatternSolidStack)
                    || !((PatternSolidStack) stack).getItemIdentifierStack().getItem().equalsForCrafting(item)) {
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
        AbstractPattern configuredPattern = Pattern.fromStack(pattern);
        for (int slot = 0; slot < configuredPattern.getIngredientSlotCount(); slot++) {
            IPatternStack stack = configuredPattern.getPatternStackInSlot(slot);
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }
            IRequestItems target = PatternStackHelper.isSolid(stack)
                    ? getSatelliteTargetForInputSlot(configuredPattern, slot)
                    : null;
            boolean merged = false;
            for (PatternIngredientTarget existing : result) {
                if (existing.target == target && existing.stack.canMerge(stack)) {
                    existing.stack.addAmount(stack.getAmount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                result.add(new PatternIngredientTarget(stack.copy(), target));
            }
        }
        return result;
    }

    static class PatternIngredientTarget {

        final IPatternStack stack;
        final IRequestItems target;

        PatternIngredientTarget(IPatternStack stack, IRequestItems target) {
            this.stack = stack;
            this.target = target;
        }
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
        return pattern != null && getSatelliteTargetForInputSlot(Pattern.fromStack(pattern), inputSlot) != null;
    }

    /**
     * Returns true when at least one input slot of this pattern is routed to a linked pattern satellite.
     */
    boolean hasLinkedSatelliteAssignments(ItemStack pattern) {
        if (pattern == null) {
            return false;
        }
        AbstractPattern configuredPattern = Pattern.fromStack(pattern);
        for (int slot = 0; slot < configuredPattern.getIngredientSlotCount(); slot++) {
            if (getSatelliteTargetForInputSlot(configuredPattern, slot) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the linked pattern satellite assigned to one solid input slot.
     * <p>
     * Fluid ingredients are always local to the pattern crafting pipe; only item ingredients can be routed to pattern
     * satellites.
     */
    private IRequestItems getSatelliteTargetForInputSlot(AbstractPattern pattern, int inputSlot) {
        if (adjacentInventory.isConnectedToPatternCraftingTable()) {
            return null;
        }
        int satelliteId = pattern.getSatelliteIdForInputSlot(inputSlot);
        if (satelliteId <= 0 || !pipe.isPatternSatelliteLinked(satelliteId)) {
            return null;
        }
        return pipe.getLinkedPatternSatellite(satelliteId);
    }

    /**
     * Returns the mutable ingredient buffer for one pattern slot, creating it on demand.
     */
    private List<IPatternStack> getBuffer(int patternSlot) {
        return bufferedIngredients.computeIfAbsent(patternSlot, k -> new ArrayList<>());
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
            debug("push skipped: no connected inventory");
            return;
        }
        PipeItemsPatternCraftingLogistics.BlockingMode mode = getEffectiveBlockingMode();
        debug("push tick mode=%s runningCraft=%d bufferedSlots=%d", mode, runningCraft, bufferedIngredients.size());
        if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.OFF) {
            for (Integer patternSlot : new ArrayList<>(bufferedIngredients.keySet())) {
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
            debug("push slot=%d dropped buffer: pattern missing", patternSlot);
            bufferedIngredients.remove(patternSlot);
            return;
        }
        PipeItemsPatternCraftingLogistics.BlockingMode mode = getEffectiveBlockingMode();
        if (mode != PipeItemsPatternCraftingLogistics.BlockingMode.OFF && isRunningCraftLocked() && runningCraft != patternSlot) {
            debug("push slot=%d skipped: running craft locked by slot=%d", patternSlot, runningCraft);
            return;
        }
        AdjacentTile connected = adjacentInventory.getConnected();
        if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING && !adjacentInventory.isEmpty(connected)) {
            debug("push slot=%d skipped: blocking mode and adjacent inventory not empty", patternSlot);
            return;
        }
        int bufferedSets = completeBufferedSets(patternSlot, pattern);
        if (bufferedSets <= 0) {
            debug("push slot=%d skipped: incomplete buffered set", patternSlot);
            if (bufferedIngredients.get(patternSlot) != null && bufferedIngredients.get(patternSlot).isEmpty()) {
                bufferedIngredients.remove(patternSlot);
            }
            return;
        }
        int insertableSets = adjacentInventory.availablePatternSets(pattern);
        if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING) {
            insertableSets = Math.min(insertableSets, 1);
        }
        int sets = Math.min(bufferedSets, insertableSets);
        if (sets <= 0 || !adjacentInventory.insertPatternSets(pattern, sets)) {
            debug("push slot=%d failed: bufferedSets=%d insertableSets=%d selectedSets=%d",
                    patternSlot,
                    bufferedSets,
                    insertableSets,
                    sets);
            return;
        }
        debugEvent("BUFFER", "push slot=%d inserted sets=%d bufferedSets=%d insertableSets=%d",
                patternSlot,
                sets,
                bufferedSets,
                insertableSets);
        ingredientBuffer.removePatternSets(patternSlot, getLocalAggregatedIngredients(pattern), sets);
        if (mode != PipeItemsPatternCraftingLogistics.BlockingMode.OFF) {
            runningCraft = patternSlot;
            runningCraftInAdjacent = true;
        }
        requestIngredientsForStagedCrafts();
    }

    /**
     * Counts complete local ingredient sets currently buffered for one pattern slot.
     */
    private int completeBufferedSets(int patternSlot, ItemStack pattern) {
        List<IPatternStack> localIngredients = getLocalAggregatedIngredients(pattern);
        int sets = localIngredients.isEmpty() ? 0 : ingredientBuffer.completeSets(patternSlot, localIngredients);
        debug("complete buffered sets slot=%d ingredients=%d sets=%d", patternSlot, localIngredients.size(), sets);
        return sets;
    }

    /**
     * Requests ingredients for all staged crafting orders that still have room in this module or the adjacent inventory.
     */
    void requestIngredientsForStagedCrafts() {
        for (PatternCraftingOrder order : new ArrayList<>(stagedCrafts)) {
            requestIngredientsForStagedCrafts(order.patternSlot);
        }
    }

    /**
     * Requests ingredients for a single pattern slot.
     * <p>
     * The per-pattern guard allows different patterns in the same module to stage work independently while preventing
     * recursive requests for the same pattern from re-entering through branch fulfillment.
     */
    private void requestIngredientsForStagedCrafts(int patternSlot) {
        if (!requestingStagedIngredientPatterns.add(patternSlot)) {
            debug("request ingredients slot=%d skipped: already requesting", patternSlot);
            return;
        }
        try {
            debug("request ingredients slot=%d start stagedCrafts=%d", patternSlot, stagedCrafts.size());

            for (PatternCraftingOrder order : new ArrayList<>(stagedCrafts)) {
                if (order.outputOrder.isFinished()) {
                    debug("request ingredients slot=%d removing staged order: the order output is already satisfied", order.patternSlot);
                    stagedCrafts.remove(order);
                    continue;
                }
                if (order.patternSlot != patternSlot) {
                    continue;
                }
                ItemStack pattern = getPatternStack(order.patternSlot);
                if (pattern == null) {
                    debug("request ingredients slot=%d removing staged order: pattern missing", order.patternSlot);
                    order.releaseReservations();
                    stagedCrafts.remove(order);
                    continue;
                }
                if (order.isFullyRequested()) {
                    debug("request ingredients slot=%d removing staged order: fully requested", order.patternSlot);
                    order.releaseReservations();
                    stagedCrafts.remove(order);
                    continue;
                }
                PipeItemsPatternCraftingLogistics.BlockingMode mode = getEffectiveBlockingMode();
                if (mode != PipeItemsPatternCraftingLogistics.BlockingMode.OFF && isRunningCraftLocked()
                        && runningCraft != order.patternSlot) {
                    debug("request ingredients slot=%d skipped: running craft locked by slot=%d", order.patternSlot, runningCraft);
                    continue;
                }
                int orderableSets = orderableSetsForPattern(order.patternSlot, pattern);
                int branchSets = order.availableSetsFromBranches(pattern);
                int sets = Math.min(order.remainingSets, orderableSets);
                sets = Math.min(sets, branchSets);
                debugEvent("REQUEST", "request ingredients slot=%d remainingSets=%d orderableSets=%d branchSets=%d selectedSets=%d",
                        order.patternSlot,
                        order.remainingSets,
                        orderableSets,
                        branchSets,
                        sets);
                if (sets <= 0) {
                    continue;
                }
                int requestedSets = order.requestIngredients(pattern, sets);
                if (requestedSets <= 0) {
                    debug("request ingredients slot=%d requested no sets", order.patternSlot);
                    continue;
                }
                debugEvent("REQUEST", "request ingredients slot=%d requestedSets=%d remainingSets=%d",
                        order.patternSlot,
                        requestedSets,
                        order.remainingSets);
                pipe.getCacheHolder().trigger(CacheTypes.Inventory);
                if (order.isFullyRequested()) {
                    debugEvent("REQUEST", "request ingredients slot=%d completed staged order after request", order.patternSlot);
                    order.releaseReservations();
                    stagedCrafts.remove(order);
                }
            }
        } finally {
            requestingStagedIngredientPatterns.remove(patternSlot);
        }
    }

    /**
     * Calculates how many complete pattern sets can be ordered now without overcommitting the module buffer or the
     * adjacent inventory. Requested but not-yet-arrived ingredients are subtracted so repeated recalculation only orders
     * the newly freed capacity.
     */
    private int orderableSetsForPattern(int patternSlot, ItemStack pattern) {
        if (!canReceiveForPattern(patternSlot)) {
            debug("orderable sets slot=%d result=0 cannot receive", patternSlot);
            return 0;
        }
        AdjacentTile connected = adjacentInventory.getConnected();
        int sets = Integer.MAX_VALUE;
        PipeItemsPatternCraftingLogistics.BlockingMode mode = getEffectiveBlockingMode();
        for (IPatternStack ingredient : getLocalAggregatedIngredients(pattern)) {
            int room = spaceForPatternIngredient(patternSlot, pattern, ingredient);
            if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING
                    && connected != null
                    && adjacentInventory.isEmpty(connected)) {
                room += ingredient.getAmount();
            }
            room -= requestedIngredient.amount(patternSlot, ingredient);
            debug("orderable ingredient slot=%d ingredient=%s roomAfterRequested=%d amountPerSet=%d",
                    patternSlot,
                    ingredient,
                    room,
                    ingredient.getAmount());
            sets = Math.min(sets, Math.max(0, room) / ingredient.getAmount());
        }
        int result = sets == Integer.MAX_VALUE ? 0 : Math.max(0, sets);
        debug("orderable sets slot=%d result=%d", patternSlot, result);
        return result;
    }

    /**
     * Returns a pattern slot whose buffered arrived ingredients can be pushed as a complete set.
     */
    private Integer findCompleteBufferedPattern() {
        for (Integer patternSlot : bufferedIngredients.keySet()) {
            ItemStack pattern = getPatternStack(patternSlot);
            if (pattern == null) {
                continue;
            }
            if (completeBufferedSets(patternSlot, pattern) > 0) {
                return patternSlot;
            }
        }
        return null;
    }

    /**
     * Returns any pattern slot that has arrived local ingredients buffered, even if the set is not complete yet.
     */
    private Integer findBufferedPattern() {
        for (Map.Entry<Integer, List<IPatternStack>> entry : bufferedIngredients.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty() || getPatternStack(entry.getKey()) == null) {
                continue;
            }
            return entry.getKey();
        }
        return null;
    }

    /**
     * Checks whether a pattern slot has arrived ingredients waiting in the module buffer.
     */
    private boolean hasBufferedIngredients(int patternSlot) {
        List<IPatternStack> buffer = bufferedIngredients.get(patternSlot);
        return buffer != null && !buffer.isEmpty();
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
        debug("running craft activated from buffer slot=%d", patternSlot);
    }

    /**
     * Returns whether the active slot is still locked by arrived buffered ingredients or an inserted adjacent batch.
     */
    private boolean isRunningCraftLocked() {
        refreshRunningCraftState(getConnectedInventoryTile());
        if (runningCraft < 0) {
            return false;
        }
        if (hasBufferedIngredients(runningCraft)) {
            return true;
        }
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
            debug("running craft cleared slot=%d: pattern missing", runningCraft);
            runningCraft = -1;
            runningCraftInAdjacent = false;
        }
        if (runningCraft >= 0
                && runningCraftInAdjacent
                && (connected == null || isInventoryEmpty(connected))) {
            debug("running craft adjacent batch finished slot=%d", runningCraft);
            runningCraftInAdjacent = false;
        }
        if (runningCraft >= 0 && !runningCraftInAdjacent && !hasBufferedIngredients(runningCraft)) {
            debug("running craft released slot=%d: no arrived ingredients remain", runningCraft);
            runningCraft = -1;
        }
        if (runningCraft < 0) {
            runningCraftInAdjacent = false;
            Integer next = findCompleteBufferedPattern();
            if (next == null) {
                next = findBufferedPattern();
            }
            if (next != null) {
                runningCraft = next;
                runningCraftInAdjacent = false;
                debug("running craft selected buffered slot=%d", runningCraft);
            }
        }
    }

    /**
     * Refreshes blocking state once per tick after push/request processing.
     */
    private void clearRunningCraftIfFinished() {
        refreshRunningCraftState(getConnectedInventoryTile());
    }

    private AdjacentTile getConnectedInventoryTile() {
        return adjacentInventory.getConnected();
    }

    private boolean isInventoryEmpty(AdjacentTile connected) {
        return adjacentInventory.isEmpty(connected);
    }

    /**
     * Determines whether this crafting pipe's outstanding output orders should be reported as destination-buffered.
     * <p>
     * The network-wide can-sink lookup may route back to this same module when a pattern requests a subitem that another
     * pattern in this pipe crafts. Self-destined orders are already represented by {@link #requestedIngredients}, so they
     * are treated as locally buffered here and are not sent through {@link LogisticsManager#canSink} again.
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
                    debug("buffer check treats self-destined order as buffered order=%s amount=%d",
                            order.getResource().getItem(),
                            order.getAmount());
                    continue;
                }
                if (order.getDestination() instanceof IItemSpaceControl) {
                    SinkReply reply = LogisticsManager.canSink(order.getDestination().getRouter(), null, true, order.getResource().getItem(), null, true, false);
                    if (reply != null && reply.bufferMode == BufferMode.NONE && reply.maxNumberOfItems >= 1) {
                        debug("buffer check found unbuffered destination order=%s replyRoom=%d",
                                order.getResource().getItem(),
                                reply.maxNumberOfItems);
                        result = false;
                        break;
                    }
                } else {
                    debug("buffer check found destination without space control order=%s", order.getResource().getItem());
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

    private boolean isOrderDestinationThisModule(LogisticsItemOrder order) {
        IRequest destination = order.getDestination();
        return destination == this || (destination != null && destination.getRouter() == getRouter());
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
        out.append("  connected inventory: ")
                .append(connected.tile.getClass().getName())
                .append(" side=")
                .append(connected.orientation)
                .append(" empty=")
                .append(adjacentInventory.isEmpty(connected))
                .append(" patternTable=")
                .append(adjacentInventory.isConnectedToPatternCraftingTable())
                .append("\n");
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
            AbstractPattern configuredPattern = Pattern.fromStack(pattern);
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
            out.append("slot ")
                    .append(slot)
                    .append("=")
                    .append(stack);
            found = true;
        }
        if (!found) {
            out.append("<none>");
        }
        out.append("\n");
    }

    private void appendStackMapDebug(
            StringBuilder out,
            String label,
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
        if (stagedCrafts.isEmpty()) {
            out.append("    <none>\n");
            return;
        }
        for (PatternCraftingOrder order : stagedCrafts) {
            order.appendDebugState(out, "    ");
        }
    }

    private void appendOrderDebug(StringBuilder out) {
        out.append("  output orders:\n");
        boolean found = false;
        for (LogisticsItemOrder order : pipe.getItemOrderManager()) {
            found = true;
            out.append("    - ")
                    .append(order.getType())
                    .append(" ")
                    .append(order.getAmount())
                    .append("x ")
                    .append(order.getResource().getItem())
                    .append(" -> router ")
                    .append(order.getRouterId())
                    .append(order.isInProgress() ? " in-progress" : "")
                    .append(order.isFinished() ? " finished" : "");
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
            debugEvent("REQUEST", "lost retry ingredient=%s requested=%d received=%d info=%s",
                    stack,
                    stack.getAmount(),
                    received,
                    pair.getValue2());
            rerequested++;
            if (received < stack.getAmount()) {
                IPatternStack remaining = PatternStackHelper.copyWithAmount(stack, stack.getAmount() - received);
                if (remaining != null) {
                    debugEvent("REQUEST", "lost retry requeued remaining=%s", remaining);
                    lostIngredients.add(new DelayedGeneric<>(
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
        ItemIdentifierStack item = PatternStackHelper.asSolidStack(stack);
        if (item != null) {
            debugEvent("REQUEST", "lost retry requesting item=%s info=%s", item, info);
            return RequestTree.requestPartial(item.clone(), pipe, info);
        }
        FluidIdentifier fluid = PatternStackHelper.asFluid(stack);
        if (fluid != null) {
            debugEvent("REQUEST", "lost retry requesting fluid=%s amount=%d info=%s", fluid, stack.getAmount(), info);
            return RequestTree.requestFluidPartial(fluid, stack.getAmount(), this, null, info);
        }
        return 0;
    }

    public void onAllowedRemoval() {

        World world = pipe.getWorld();

        for (PatternCraftingOrder order : stagedCrafts) {
            debug("removal releases staged order slot=%d remainingSets=%d", order.patternSlot, order.remainingSets);
            order.releaseReservations();
        }
        stagedCrafts.clear();

        patternInventory.dropContents(world, pipe.getX(), pipe.getY(), pipe.getZ());
        ingredientBuffer.dropContents(world, pipe.getX(), pipe.getY(), pipe.getZ());

        for (List<IPatternStack> value : bufferedIngredients.values()) {
            for (IPatternStack ingredient : value) {
                if (MainProxy.isServer(world)) {
                    ItemStack stack = ingredient.makePatternStack();
                    if (stack == null) {
                        continue;
                    }
                    float f1 = 0.7F;
                    double d = (world.rand.nextFloat() * f1) + (1.0F - f1) * 0.5D;
                    double d1 = (world.rand.nextFloat() * f1) + (1.0F - f1) * 0.5D;
                    double d2 = (world.rand.nextFloat() * f1) + (1.0F - f1) * 0.5D;
                    EntityItem entityitem = new EntityItem(world, pipe.getX() + d, pipe.getY() + d1, pipe.getZ() + d2, stack);
                    entityitem.delayBeforeCanPickup = 10;
                    world.spawnEntityInWorld(entityitem);
                }
            }
        }
    }

}
