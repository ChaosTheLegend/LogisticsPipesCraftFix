package logisticspipes.crafting;

import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.interfaces.routing.*;
import logisticspipes.logistics.LogisticsManager;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.LogisticsExtraPromise;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.DelayQueue;

public class ModuleItemCrafting extends LogisticsGuiModule implements ICraftItems, IRequireReliableTransport, IStagedCraftingProvider {

    private final PipeItemsPatternCraftingLogistics pipe;
    private final SimpleStackInventory patternInventory = new SimpleStackInventory(9, "Patterns", 1);
    private final Map<Integer, List<ItemIdentifierStack>> bufferedIngredients = new HashMap<>();
    private final Map<Integer, List<ItemIdentifierStack>> requestedIngredients = new HashMap<>();
    private final List<PatternCraftingOrder> stagedCrafts = new ArrayList<>();
    private final DelayQueue<DelayedGeneric<Pair<ItemIdentifierStack, IAdditionalTargetInformation>>> lostItems = new DelayQueue<>();
    private final PatternHandler patternHandler = new PatternHandler(patternInventory);
    private final AdjacentInventoryHandler adjacentInventory;
    private final IngredientBufferHandler ingredientBuffer = new IngredientBufferHandler(bufferedIngredients, patternHandler);
    private final IngredientRequestHandler requestedIngredient = new IngredientRequestHandler(requestedIngredients);
    private WeakReference<TileEntity> lastAccessedCrafter = new WeakReference<>(null);
    private SinkReply sinkReply;
    private PipeItemsPatternCraftingLogistics.BlockingMode blockingMode = PipeItemsPatternCraftingLogistics.BlockingMode.OFF;
    private int runningCraft = -1;
    private final Set<Integer> requestingStagedIngredientPatterns = new HashSet<>();
    private boolean checkingBufferedOrders = false;

    public ModuleItemCrafting(PipeItemsPatternCraftingLogistics pipe) {
        this.pipe = pipe;
        adjacentInventory = new AdjacentInventoryHandler(this, pipe, patternHandler);
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
        if (!patternHandler.isIngredient(item)) {
            return null;
        }
        int room = spaceFor(item, includeInTransit);
        if (room <= 0) {
            return null;
        }
        _service.getDebug().log("crafting room " + room + " for " + item);
        return new SinkReply(sinkReply, room, areAllOrdersBuffered() ? BufferMode.DESTINATION_BUFFERED : BufferMode.NONE);
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
        craftFromAdjacentInventory();
    }

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    @Override
    public Collection<ItemIdentifier> getSpecificInterests() {
        return patternHandler.getIngredientItems();
    }

    public Set<ItemIdentifier> getCraftedItems() {
        Set<ItemIdentifier> crafted = new TreeSet<>();
        for (ItemStack pattern : patternHandler.getConfiguredPatterns()) {
            for (ItemIdentifierStack result : Pattern.getResults(pattern)) {
                crafted.add(result.getItem());
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
        bufferedIngredients.clear();
        NBTTagList buffer = tag.getTagList("patternIngredientBuffer", tag.getId());
        for (int i = 0; i < buffer.tagCount(); i++) {
            NBTTagCompound stackTag = buffer.getCompoundTagAt(i);
            int patternSlot = stackTag.getInteger("patternSlot");
            ItemStack stack = ItemStack.loadItemStackFromNBT(stackTag);
            if (stack != null) {
                getBuffer(patternSlot).add(ItemIdentifierStack.getFromStack(stack));
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        patternInventory.writeToNBT(tag, "PatternCrafting");
        tag.setInteger("patternBlockingMode", blockingMode.ordinal());
        tag.setInteger("runningCraft", runningCraft);
        tag.setInteger("bufferedPatternSlot", runningCraft);
        NBTTagList buffer = new NBTTagList();
        for (Map.Entry<Integer, List<ItemIdentifierStack>> entry : bufferedIngredients.entrySet()) {
            for (ItemIdentifierStack stack : entry.getValue()) {
                NBTTagCompound stackTag = new NBTTagCompound();
                stack.makeNormalStack().writeToNBT(stackTag);
                stackTag.setInteger("patternSlot", entry.getKey());
                buffer.appendTag(stackTag);
            }
        }
        tag.setTag("patternIngredientBuffer", buffer);
    }

    @Override
    public void registerPosition(ModulePositionType slot, int positionInt) {
        super.registerPosition(slot, positionInt);
        sinkReply = new SinkReply(FixedPriority.ItemSink, 0, true, false, 1, 0, null);
    }

    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        if (!pipe.getItemOrderManager().hasExtras() || tree.hasBeenQueried(pipe.getItemOrderManager())) {
            return;
        }
        IResource requested = tree.getRequestType();
        for (LogisticsItemOrder order : pipe.getItemOrderManager()) {
            if (order.getType() == ResourceType.EXTRA && requested.matches(order.getResource().getItem(), IResource.MatchSettings.NORMAL)) {
                int amount = Math.min(order.getAmount(), tree.getMissingAmount());
                if (amount > 0) {
                    tree.addPromise(new LogisticsExtraPromise(order.getResource().getItem(), amount, this, true));
                    tree.setQueried(pipe.getItemOrderManager());
                    return;
                }
            }
        }
    }

    @Override
    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination, IAdditionalTargetInformation info) {
        if (promise instanceof LogisticsExtraPromise) {
            pipe.getItemOrderManager().removeExtras(new logisticspipes.request.resources.DictResource(new ItemIdentifierStack(promise.item, promise.numberOfItems), null));
        }
        pipe.spawnParticle(Particles.WhiteParticle, 2);
        return pipe.getItemOrderManager().addOrder(new ItemIdentifierStack(promise.item, promise.numberOfItems), destination, ResourceType.CRAFTING, info);
    }

    /**
     * Starts a staged craft from a request-tree branch.
     * <p>
     * The output order stays in this pipe's order manager, while the branch is kept so this module can request only the
     * ingredient sets that currently fit in its buffer or connected inventory.
     */
    @Override
    public LogisticsOrder fullFillStagedCrafting(
            LogisticsPromise promise,
            IResource requestType,
            IAdditionalTargetInformation info,
            PatternCraftingBranch branch) {
        IRequestItems destination = getRequestTarget(requestType);
        if (destination == null) {
            return null;
        }
        LogisticsOrder order = fullFill(promise, destination, info);
        int patternSlot = promise instanceof PatternCraftingPromise
                ? ((PatternCraftingPromise) promise).getPatternSlot()
                : patternHandler.findPatternSlotForResult(promise.item);
        int resultAmountPerSet = promise instanceof PatternCraftingPromise
                ? ((PatternCraftingPromise) promise).getResultAmountPerSet()
                : Math.max(1, patternHandler.resultAmount(patternSlot, promise.item));
        if (patternSlot >= 0 && branch != null) {
            stagedCrafts.add(new PatternCraftingOrder(patternSlot, resultAmountPerSet, branch, patternHandler, requestedIngredient));
            requestIngredientsForStagedCrafts(patternSlot);
        }
        return order;
    }

    private IRequestItems getRequestTarget(IResource requestType) {
        if (requestType instanceof ItemResource) {
            return ((ItemResource) requestType).getTarget();
        }
        if (requestType instanceof logisticspipes.request.resources.DictResource) {
            return ((logisticspipes.request.resources.DictResource) requestType).getTarget();
        }
        return null;
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {
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

    @Override
    public void registerExtras(IPromise promise) {
        pipe.getItemOrderManager().addExtra(new logisticspipes.request.resources.DictResource(new ItemIdentifierStack(promise.getItemType(), promise.getAmount()), null));
    }

    @Override
    public ICraftingTemplate addCrafting(IResource toCraft) {
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = patternHandler.getConfiguredPatternStack(slot);
            if (pattern == null) {
                continue;
            }
            List<ItemIdentifierStack> results = Pattern.getResults(pattern);
            for (ItemIdentifierStack result : results) {
                if (!toCraft.matches(result.getItem(), IResource.MatchSettings.NORMAL)) {
                    continue;
                }
                PatternCraftingTemplate template = new PatternCraftingTemplate(result, this, 0, slot);
                for (ItemIdentifierStack ingredient : Pattern.getAggregatedIngredients(pattern)) {
                    template.addIngredient(new ItemResource(ingredient, this), new PatternTargetInformation(slot));
                }
                for (ItemIdentifierStack byproduct : results) {
                    if (!byproduct.getItem().equals(result.getItem())) {
                        template.addByproduct(byproduct);
                    }
                }
                return template;
            }
        }
        return null;
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
            results.addAll(Pattern.getResults(pattern));
        }
        return results;
    }

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        if (info instanceof PatternTargetInformation && item != null) {
            requestedIngredient.remove(((PatternTargetInformation) info).patternSlot(), item.getItem(), item.getStackSize());
        }
        lostItems.add(new DelayedGeneric<>(new Pair<>(item, info), 5000));
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
            return;
        }
        int patternSlot = ((PatternTargetInformation) info).patternSlot();
        ItemStack pattern = getPatternStack(patternSlot);
        if (pattern == null || !patternContains(pattern, item.getItem())) {

            return;
        }

        int original = item.getStackSize();
        int requested = requestedIngredient.amount(patternSlot, item.getItem());
        int accepted = Math.min(original, Math.max(requested, spaceForArrivingIngredient(patternSlot, pattern, item.getItem())));
        requestedIngredient.remove(patternSlot, item.getItem(), accepted);
        if (accepted > 0) {
            ingredientBuffer.add(patternSlot, item.getItem(), accepted);
            if (getEffectiveBlockingMode() != PipeItemsPatternCraftingLogistics.BlockingMode.OFF && runningCraft < 0) {
                runningCraft = patternSlot;
            }
            pushBufferedIngredientsFor(patternSlot);
        }
        item.setStackSize(original - accepted);
        if (accepted > 0) {
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        }
    }

    /**
     * Calculates how many arriving items can be consumed for one pattern before the transport layer treats them as
     * leftovers. This is intentionally separate from {@link #spaceFor(ItemIdentifier, boolean)} because arriving items
     * can complete a blocking-mode buffer that will be pushed as soon as the connected inventory becomes empty.
     */
    private int spaceForArrivingIngredient(int patternSlot, ItemStack pattern, ItemIdentifier item) {
        int space = spaceForPatternIngredient(patternSlot, pattern, item);
        AdjacentTile connected = adjacentInventory.getConnected();
        if (getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING && connected != null && adjacentInventory.isEmpty(connected) && ingredientBuffer.canCompleteOneSetAfterAdding(patternSlot, pattern, item, space)) {
            space += patternHandler.ingredientAmount(pattern, item);
        }
        return space;
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
            if (pattern == null || !patternHandler.containsIngredient(pattern, item)) {
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
     * Calculates module-side capacity for one ingredient according to the current blocking mode.
     * <p>
     * Blocking mode only stages one complete pattern in the module. Smart blocking and non-blocking mode also include
     * the number of complete pattern sets that can currently fit in the adjacent inventory.
     */
    private int spaceForPatternIngredient(int patternSlot, ItemStack pattern, ItemIdentifier item) {
        int sets = 1;
        if (getEffectiveBlockingMode() != PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING) {
            sets += adjacentInventory.availablePatternSets(pattern);
        }
        int capacity = sets * patternHandler.ingredientAmount(pattern, item);
        return Math.max(0, capacity - ingredientBuffer.amount(patternSlot, item));
    }

    private boolean patternContains(ItemStack pattern, ItemIdentifier item) {
        return patternHandler.containsIngredient(pattern, item);
    }

    private List<ItemIdentifierStack> getBuffer(int patternSlot) {
        return bufferedIngredients.computeIfAbsent(patternSlot, k -> new ArrayList<>());
    }

    private boolean hasBufferedIngredients() {
        for (List<ItemIdentifierStack> buffer : bufferedIngredients.values()) {
            if (!buffer.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void pushBufferedIngredients() {
        AdjacentTile connected = getConnectedInventoryTile();
        if (connected == null) {
            return;
        }
        PipeItemsPatternCraftingLogistics.BlockingMode mode = getEffectiveBlockingMode();
        if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.OFF) {
            for (Integer patternSlot : new ArrayList<>(bufferedIngredients.keySet())) {
                pushBufferedIngredientsFor(patternSlot);
            }
            return;
        }
        releaseIdleRunningCraft(connected);
        if (runningCraft < 0) {
            Integer next = findCompleteBufferedPattern();
            if (next != null && isInventoryEmpty(connected)) {
                runningCraft = next;
            }
        }
        if (runningCraft >= 0) {
            pushBufferedIngredientsFor(runningCraft);
        }
    }

    private void pushBufferedIngredientsFor(int patternSlot) {
        ItemStack pattern = getPatternStack(patternSlot);
        if (pattern == null) {
            bufferedIngredients.remove(patternSlot);
            return;
        }
        PipeItemsPatternCraftingLogistics.BlockingMode mode = getEffectiveBlockingMode();
        if (mode != PipeItemsPatternCraftingLogistics.BlockingMode.OFF && isRunningCraftLocked() && runningCraft != patternSlot) {
            return;
        }
        AdjacentTile connected = adjacentInventory.getConnected();
        if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING && !adjacentInventory.isEmpty(connected)) {
            return;
        }
        int bufferedSets = ingredientBuffer.completeSets(patternSlot, pattern);
        if (bufferedSets <= 0) {
            if (bufferedIngredients.get(patternSlot).isEmpty()) bufferedIngredients.remove(patternSlot);
            return;
        }
        int insertableSets = adjacentInventory.availablePatternSets(pattern);
        if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING) {
            insertableSets = Math.min(insertableSets, 1);
        }
        int sets = Math.min(bufferedSets, insertableSets);
        if (sets <= 0 || !adjacentInventory.insertPatternSets(pattern, sets)) {
            return;
        }
        ingredientBuffer.removePatternSets(patternSlot, pattern, sets);
        if (mode != PipeItemsPatternCraftingLogistics.BlockingMode.OFF) {
            runningCraft = patternSlot;
        }
        requestIngredientsForStagedCrafts();
    }

    /**
     * Requests ingredients for all staged crafting orders that still have room in this module or the adjacent inventory.
     */
    private void requestIngredientsForStagedCrafts() {
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
            return;
        }
        try {
            for (PatternCraftingOrder order : new ArrayList<>(stagedCrafts)) {
                if (order.patternSlot != patternSlot) {
                    continue;
                }
                ItemStack pattern = getPatternStack(order.patternSlot);
                if (pattern == null) {
                    order.releaseReservations();
                    stagedCrafts.remove(order);
                    continue;
                }
                if (order.isFullyRequested()) {
                    order.releaseReservations();
                    stagedCrafts.remove(order);
                    continue;
                }
                PipeItemsPatternCraftingLogistics.BlockingMode mode = getEffectiveBlockingMode();
                if (mode != PipeItemsPatternCraftingLogistics.BlockingMode.OFF && isRunningCraftLocked()
                        && runningCraft != order.patternSlot) {
                    continue;
                }
                int sets = Math.min(order.remainingSets, orderableSetsForPattern(order.patternSlot, pattern));
                sets = Math.min(sets, order.availableSetsFromBranches(pattern));
                if (sets <= 0) {
                    continue;
                }
                int requestedSets = order.requestIngredients(pattern, sets);
                if (requestedSets <= 0) {
                    continue;
                }
                pipe.getCacheHolder().trigger(CacheTypes.Inventory);
                if (order.isFullyRequested()) {
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
            return 0;
        }
        AdjacentTile connected = adjacentInventory.getConnected();
        int sets = Integer.MAX_VALUE;
        PipeItemsPatternCraftingLogistics.BlockingMode mode = getEffectiveBlockingMode();
        for (ItemIdentifierStack ingredient : patternHandler.getAggregatedIngredients(pattern)) {
            int room = spaceForPatternIngredient(patternSlot, pattern, ingredient.getItem());
            if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING
                    && connected != null
                    && adjacentInventory.isEmpty(connected)) {
                room += ingredient.getStackSize();
            }
            room -= requestedIngredient.amount(patternSlot, ingredient.getItem());
            sets = Math.min(sets, Math.max(0, room) / ingredient.getStackSize());
        }
        return sets == Integer.MAX_VALUE ? 0 : Math.max(0, sets);
    }

    private Integer findCompleteBufferedPattern() {
        for (Integer patternSlot : bufferedIngredients.keySet()) {
            ItemStack pattern = getPatternStack(patternSlot);
            if (pattern == null) {
                continue;
            }
            if (ingredientBuffer.completeSets(patternSlot, pattern) > 0) {
                return patternSlot;
            }
        }
        return null;
    }

    private boolean isRunningCraftLocked() {
        if (runningCraft < 0) {
            return false;
        }
        AdjacentTile connected = getConnectedInventoryTile();
        return connected != null && !isInventoryEmpty(connected);
    }

    private void releaseIdleRunningCraft(AdjacentTile connected) {
        if (runningCraft < 0 || connected == null || !isInventoryEmpty(connected)) {
            return;
        }
        ItemStack pattern = getPatternStack(runningCraft);
        if (pattern == null || ingredientBuffer.completeSets(runningCraft, pattern) <= 0) {
            runningCraft = -1;
        }
    }

    private void clearRunningCraftIfFinished() {
        if (runningCraft < 0) {
            return;
        }
        if (getPatternStack(runningCraft) == null) {
            runningCraft = -1;
            return;
        }
        AdjacentTile connected = getConnectedInventoryTile();
        releaseIdleRunningCraft(connected);
        if (runningCraft < 0) {
            return;
        }
        if (!pipe.getItemOrderManager().hasOrders(ResourceType.CRAFTING) && !hasBufferedIngredients() && (connected == null || isInventoryEmpty(connected))) {
            runningCraft = -1;
        }
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
                    continue;
                }
                if (order.getDestination() instanceof IItemSpaceControl) {
                    SinkReply reply = LogisticsManager.canSink(order.getDestination().getRouter(), null, true, order.getResource().getItem(), null, true, false);
                    if (reply != null && reply.bufferMode == BufferMode.NONE && reply.maxNumberOfItems >= 1) {
                        result = false;
                        break;
                    }
                } else {
                    result = false;
                    break;
                }
            }
            return result;
        } finally {
            checkingBufferedOrders = false;
        }
    }

    private boolean isOrderDestinationThisModule(LogisticsItemOrder order) {
        IRequest destination = order.getDestination();
        return destination == this || (destination != null && destination.getRouter() == getRouter());
    }

    private void retryLostItems() {
        DelayedGeneric<Pair<ItemIdentifierStack, IAdditionalTargetInformation>> lost = lostItems.poll();
        int rerequested = 0;
        while (lost != null && rerequested < 100) {
            Pair<ItemIdentifierStack, IAdditionalTargetInformation> pair = lost.get();
            int received = RequestTree.requestPartial(pair.getValue1(), pipe, pair.getValue2());
            rerequested++;
            if (received < pair.getValue1().getStackSize()) {
                pair.getValue1().setStackSize(pair.getValue1().getStackSize() - received);
                lostItems.add(new DelayedGeneric<>(pair, 4500 + (int) (Math.random() * 1000)));
            }
            lost = lostItems.poll();
        }
    }

    private void craftFromAdjacentInventory() {
        if (!pipe.isNthTick(6) || !pipe.getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
            return;
        }
        List<AdjacentTile> inventories = adjacentInventory.locateInventories();
        if (inventories.isEmpty()) {
            pipe.getItemOrderManager().sendFailed();
            return;
        }
        pipe.spawnParticle(Particles.VioletParticle, 2);
        LogisticsItemOrder order = pipe.getItemOrderManager().peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA);
        if (order == null) {
            return;
        }
        int maxToSend = Math.min(order.getAmount(), order.getResource().getItem().getMaxStackSize());
        for (AdjacentTile tile : inventories) {
            ItemStack extracted = adjacentInventory.extract(tile, order.getResource(), maxToSend);
            if (extracted == null || extracted.stackSize <= 0) {
                continue;
            }
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
            lastAccessedCrafter = new WeakReference<>(tile.tile);
            sendExtracted(order, extracted, tile.orientation);
            requestIngredientsForStagedCrafts();
            return;
        }
        pipe.getItemOrderManager().deferSend();
    }

    private void sendExtracted(LogisticsItemOrder order, ItemStack extracted, ForgeDirection orientation) {
        if (order.getDestination() != null) {
            IRoutedItem item = SimpleServiceLocator.routedItemHelper.createNewTravelItem(extracted);
            item.setDestination(order.getDestination().getRouter().getSimpleID());
            item.setTransportMode(TransportMode.Active);
            item.setAdditionalTargetInformation(order.getInformation());
            pipe.queueRoutedItem(item, orientation);
            pipe.getItemOrderManager().sendSuccessfull(extracted.stackSize, false, item);
        } else {
            pipe.sendStack(extracted, -1, CoreRoutedPipe.ItemSendMode.Normal, order.getInformation());
            pipe.getItemOrderManager().sendSuccessfull(extracted.stackSize, false, null);
        }
    }

    public void onAllowedRemoval() {

        World world = pipe.getWorld();

        for (PatternCraftingOrder order : stagedCrafts) {
            order.releaseReservations();
        }
        stagedCrafts.clear();

        patternInventory.dropContents(world, pipe.getX(), pipe.getY(), pipe.getZ());

        for (List<ItemIdentifierStack> value : bufferedIngredients.values()) {
            for (ItemIdentifierStack itemIdentifierStack : value) {
                if (MainProxy.isServer(world)) {
                    ItemStack stack = itemIdentifierStack.makeNormalStack();
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
