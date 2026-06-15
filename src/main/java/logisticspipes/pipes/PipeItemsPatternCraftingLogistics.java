package logisticspipes.pipes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.ItemMemoryChip;
import logisticspipes.crafting.ModuleItemCrafting;
import logisticspipes.crafting.PatternCraftingHudState;
import logisticspipes.crafting.PatternCraftingTargetSelector;
import logisticspipes.crafting.PipeItemsPatternSatelliteLogistics;
import logisticspipes.gui.hud.HUDPatternCrafting;
import logisticspipes.interfaces.IChangeListener;
import logisticspipes.interfaces.IHeadUpDisplayRenderer;
import logisticspipes.interfaces.IHeadUpDisplayRendererProvider;
import logisticspipes.interfaces.IOrderManagerContentReceiver;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IFluidSink;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.hud.HUDStartWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopWatchingPacket;
import logisticspipes.network.packets.orderer.OrdererManagerContent;
import logisticspipes.network.packets.orderer.PatternCraftingHudContent;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.fluid.FluidRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.LogisticsFluidOrderManager;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.routing.order.LogisticsOrderManager;
import logisticspipes.security.SecuritySettings;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.transport.PipeFluidTransportLogistics;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.InventoryHelper;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Pattern crafting pipe that can stage item and fluid ingredients while still behaving like an item crafting pipe for
 * normal order-manager and HUD interactions.
 * <p>
 * The pipe extends {@link FluidRoutedPipe} so routed fluid containers reach the fluid transport hook, but raw tank
 * insertion is disabled. Fluid ingredients are accepted through the reliable item-arrival path and buffered by the
 * crafting module as pattern ingredients.
 */
public class PipeItemsPatternCraftingLogistics extends FluidRoutedPipe
        implements ICraftItems, IRequireReliableTransport, IFluidSink, IHeadUpDisplayRendererProvider, IChangeListener,
        IOrderManagerContentReceiver {

    public enum BlockingMode {
        OFF,
        BLOCKING,
        SMART
    }

    private static final String LINKED_PATTERN_SATELLITES_TAG = "linkedPatternSatelliteIds";

    private final ModuleItemCrafting module;
    private final LogisticsFluidOrderManager fluidOrderManager;
    private final PatternCraftingTargetSelector targetSelector;
    public final LinkedList<ItemIdentifierStack> oldList = new LinkedList<>();
    public final LinkedList<ItemIdentifierStack> displayList = new LinkedList<>();
    private PatternCraftingHudState oldHudState = PatternCraftingHudState.empty();
    private PatternCraftingHudState hudState = PatternCraftingHudState.empty();
    public final PlayerCollectionList localModeWatchers = new PlayerCollectionList();
    private final HUDPatternCrafting HUD = new HUDPatternCrafting(this);
    private boolean doContentUpdate = true;
    private final Set<Integer> linkedPatternSatelliteIds = new TreeSet<>();

    public PipeItemsPatternCraftingLogistics(Item item) {
        super(new PipeFluidTransportLogistics() {

            @Override
            public boolean canPipeConnect(TileEntity tile, ForgeDirection dir) {
                if (super.canPipeConnect(tile, dir)) {
                    return true;
                }
                if (SimpleServiceLocator.pipeInformationManager.isPipe(tile, false)) {
                    return false;
                }
                if (tile instanceof IFluidHandler) {
                    IFluidHandler handler = (IFluidHandler) tile;
                    return handler.getTankInfo(dir.getOpposite()) != null
                            && handler.getTankInfo(dir.getOpposite()).length > 0;
                }
                return false;
            }
        }, item);
        module = new ModuleItemCrafting(this);
        _orderItemManager = new logisticspipes.routing.order.LogisticsItemOrderManager(this, this);
        fluidOrderManager = new LogisticsFluidOrderManager(this, this);
        targetSelector = new PatternCraftingTargetSelector(this);
        throttleTime = 40;
    }

    @Override
    protected void onAllowedRemoval() {
        module.onAllowedRemoval();
    }

    @Override
    public void onNeighborBlockChange(int blockId) {
        targetSelector.clearCache();
        super.onNeighborBlockChange(blockId);
    }

    @Override
    public boolean disconnectPipe(TileEntity tile, ForgeDirection dir) {
        if (SimpleServiceLocator.pipeInformationManager.isPipe(tile, false)) {
            return false;
        }
        if (tile instanceof IInventory || tile instanceof IFluidHandler) {
            return !targetSelector.isSelectedInventory(tile, dir);
        }
        return true;
    }

    @Override
    protected boolean handleClick(EntityPlayer entityplayer, SecuritySettings settings) {
        if (entityplayer.isSneaking() && entityplayer.getCurrentEquippedItem() != null
                && entityplayer.getCurrentEquippedItem().getItem() == LogisticsPipes.LogisticsMemoryChip) {
            if (MainProxy.isServer(entityplayer.worldObj)) {
                if (settings == null || settings.openGui) {
                    int added = addLinkedPatternSatelliteIds(
                            ItemMemoryChip.getPatternSatelliteIds(entityplayer.getCurrentEquippedItem()));
                    entityplayer.addChatComponentMessage(
                            new ChatComponentText(
                                    added == 0 ? "No new pattern satellites linked"
                                            : "Linked " + added + " pattern satellite" + (added == 1 ? "" : "s")));
                } else {
                    entityplayer.addChatComponentMessage(new ChatComponentTranslation("lp.chat.permissiondenied"));
                }
            }
            return true;
        }
        if (!entityplayer.isSneaking() || !SimpleServiceLocator.toolWrenchHandler.isWrenchEquipped(entityplayer)
                || !SimpleServiceLocator.toolWrenchHandler.canWrench(entityplayer, getX(), getY(), getZ())) {
            return false;
        }
        if (MainProxy.isServer(entityplayer.worldObj)) {
            if (settings == null || settings.openGui) {
                targetSelector.cycleConnectedInventory(entityplayer);
            } else {
                entityplayer.addChatComponentMessage(new ChatComponentTranslation("lp.chat.permissiondenied"));
            }
        }
        SimpleServiceLocator.toolWrenchHandler.wrenchUsed(entityplayer, getX(), getY(), getZ());
        return true;
    }

    public AdjacentTile getConnectedInventoryTile() {
        return targetSelector.getConnectedInventoryTile();
    }

    public boolean isPatternSatelliteLinked(int satelliteId) {
        return linkedPatternSatelliteIds.contains(satelliteId);
    }

    public PipeItemsPatternSatelliteLogistics getLinkedPatternSatellite(int satelliteId) {
        if (!isPatternSatelliteLinked(satelliteId)) {
            return null;
        }
        return PipeItemsPatternSatelliteLogistics.findById(satelliteId);
    }

    public Collection<Integer> getLinkedPatternSatelliteIds() {
        return new ArrayList<>(linkedPatternSatelliteIds);
    }

    private int addLinkedPatternSatelliteIds(int[] satelliteIds) {
        int added = 0;
        if (satelliteIds == null) {
            return added;
        }
        for (int satelliteId : satelliteIds) {
            if (satelliteId > 0 && linkedPatternSatelliteIds.add(satelliteId)) {
                added++;
            }
        }
        if (added > 0) {
            refreshRender(false);
        }
        return added;
    }

    public void refreshSelectedInventoryConnection() {
        clearCache();
        triggerConnectionCheck();
        connectionUpdate();
        refreshRender(false);
    }

    @Override
    public void enabledUpdateEntity() {
        super.enabledUpdateEntity();
        if (doContentUpdate) {
            checkContentUpdate();
        }
        checkHudUpdate();
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_CRAFTER_TEXTURE;
    }

    @Override
    public LogisticsModule getLogisticsModule() {
        return module;
    }

    public ModuleItemCrafting getPatternModule() {
        return module;
    }

    public boolean cancelPatternCraft(int patternSlot) {
        boolean changed = module.cancelPatternCraft(patternSlot);
        if (changed) {
            doContentUpdate = true;
        }
        return changed;
    }

    @Override
    public ItemSendMode getItemSendMode() {
        return ItemSendMode.Normal;
    }

    /**
     * Prevents routed fluid ingredients from being stored in the inherited internal fluid tanks.
     * <p>
     * Pattern crafting needs the LogisticsFluidContainer item to arrive so the module can match it to a pattern slot
     * and record buffered ingredient state.
     */
    @Override
    public boolean canInsertToTanks() {
        return false;
    }

    /**
     * Disables the background side-tank transfer behavior from {@link FluidRoutedPipe}.
     * <p>
     * The selected adjacent inventory or fluid handler is managed explicitly by {@link ModuleItemCrafting}.
     */
    @Override
    public boolean canInsertFromSideToTanks() {
        return false;
    }

    /**
     * Rejects direct fluid-handler fills into this pipe.
     * <p>
     * Fluid ingredients must enter as routed fluid container items so request tracking and staged buffer accounting
     * stay consistent with item ingredients.
     */
    @Override
    public boolean canReceiveFluid() {
        return false;
    }

    /**
     * Returns the pattern-specific fluid order manager.
     * <p>
     * This manager tracks crafted fluid outputs and fluid extra orders; it is separate from the lazy manager provided
     * by the generic fluid pipe base class.
     */
    @Override
    public LogisticsFluidOrderManager getFluidOrderManager() {
        return fluidOrderManager;
    }

    /**
     * Keeps generic order-manager watching on the item manager.
     * <p>
     * Pattern crafting exposes both item and fluid orders in its custom HUD content, but callers expecting
     * CoreRoutedPipe behavior should still see the item order manager here.
     */
    @Override
    public LogisticsOrderManager<?, ?> getOrderManager() {
        return getItemOrderManager();
    }

    /**
     * Combines fluid-pipe shared-tank protection with the original item-inventory overlap protection.
     * <p>
     * Extending {@link FluidRoutedPipe} would otherwise only compare adjacent tanks, which would lose the item crafting
     * safeguard against two crafting pipes pulling from the same inventory.
     */
    @Override
    public boolean sharesInterestWith(CoreRoutedPipe other) {
        return super.sharesInterestWith(other) || sharesInventoryInterestWith(other);
    }

    /**
     * Checks whether this pipe and another routed pipe touch the same inventory.
     */
    private boolean sharesInventoryInterestWith(CoreRoutedPipe other) {
        List<IInventory> otherInventories = getConnectedInventories(other);
        if (otherInventories.isEmpty()) {
            return false;
        }
        for (IInventory inventory : getConnectedInventories(this)) {
            if (otherInventories.contains(inventory)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns normalized inventories connected to a routed pipe.
     * <p>
     * Inventory wrappers are normalized through {@link InventoryHelper} to match the old CoreRoutedPipe comparison.
     */
    private List<IInventory> getConnectedInventories(CoreRoutedPipe pipe) {
        List<IInventory> inventories = new ArrayList<>();
        for (AdjacentTile tile : pipe.getConnectedEntities()) {
            if (tile.tile instanceof IInventory) {
                inventories.add(InventoryHelper.getInventory((IInventory) tile.tile));
            }
        }
        return inventories;
    }

    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        module.canProvide(tree, root, filters);
    }

    @Override
    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination,
            IAdditionalTargetInformation info) {
        return module.fullFill(promise, destination, info);
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {
        module.getAllItems(list, filter);
    }

    @Override
    public void registerExtras(IPromise promise) {
        module.registerExtras(promise);
    }

    @Override
    public ICraftingTemplate addCrafting(IResource type) {
        return module.addCrafting(type);
    }

    @Override
    public boolean canCraft(IResource toCraft) {
        return module.canCraft(toCraft);
    }

    @Override
    public int getTodo() {
        return module.getTodo() + fluidOrderManager.totalAmountCountInAllOrders();
    }

    @Override
    public List<ItemIdentifierStack> getConfiguredCraftResults() {
        return module.getConfiguredCraftResults();
    }

    public PatternCraftingHudState getHudState() {
        return hudState;
    }

    @Override
    public Set<ItemIdentifier> getSpecificInterests() {
        return module.getCraftedItems();
    }

    @Override
    public double getLoadFactor() {
        return (_orderItemManager.totalAmountCountInAllOrders() + fluidOrderManager.totalAmountCountInAllOrders()
                + 63.0) / 64.0;
    }

    @Override
    public int sinkAmount(FluidStack stack) {
        return module.sinkAmount(stack);
    }

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        module.itemLost(item, info);
    }

    @Override
    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        module.itemArrived(item, info);
    }

    @Override
    public void listenedChanged() {
        doContentUpdate = true;
    }

    private void checkContentUpdate() {
        doContentUpdate = false;
        LinkedList<ItemIdentifierStack> all = _orderItemManager.getContentList(getWorld());
        all.addAll(fluidOrderManager.getContentList(getWorld()));
        if (!oldList.equals(all)) {
            oldList.clear();
            oldList.addAll(all);
            MainProxy.sendToPlayerList(
                    PacketHandler.getPacket(OrdererManagerContent.class).setIdentList(all).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    localModeWatchers);
        }
    }

    private void checkHudUpdate() {
        PatternCraftingHudState state = module.getHudState();
        if (!oldHudState.equals(state)) {
            oldHudState = state;
            MainProxy.sendToPlayerList(
                    PacketHandler.getPacket(PatternCraftingHudContent.class).setState(state).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    localModeWatchers);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        targetSelector.writeToNBT(nbttagcompound);
        int[] satelliteIds = new int[linkedPatternSatelliteIds.size()];
        int index = 0;
        for (Integer satelliteId : linkedPatternSatelliteIds) {
            satelliteIds[index++] = satelliteId;
        }
        nbttagcompound.setIntArray(LINKED_PATTERN_SATELLITES_TAG, satelliteIds);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        targetSelector.readFromNBT(nbttagcompound);
        linkedPatternSatelliteIds.clear();
        for (int satelliteId : nbttagcompound.getIntArray(LINKED_PATTERN_SATELLITES_TAG)) {
            if (satelliteId > 0) {
                linkedPatternSatelliteIds.add(satelliteId);
            }
        }
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        targetSelector.writeData(data);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        targetSelector.readData(data);
    }

    @Override
    public void setOrderManagerContent(Collection<ItemIdentifierStack> list) {
        displayList.clear();
        displayList.addAll(list);
    }

    public void setHudState(PatternCraftingHudState state) {
        hudState = state == null ? PatternCraftingHudState.empty() : state;
    }

    public LogisticsFluidOrderManager getPatternFluidOrderManager() {
        return fluidOrderManager;
    }

    @Override
    public IHeadUpDisplayRenderer getRenderer() {
        return HUD;
    }

    @Override
    public void startWatching() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(HUDStartWatchingPacket.class).setInteger(1).setPosX(getX()).setPosY(getY())
                        .setPosZ(getZ()));
    }

    @Override
    public void stopWatching() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(HUDStopWatchingPacket.class).setInteger(1).setPosX(getX()).setPosY(getY())
                        .setPosZ(getZ()));
    }

    @Override
    public void playerStartWatching(EntityPlayer player, int mode) {
        if (mode == 1) {
            localModeWatchers.add(player);
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(OrdererManagerContent.class).setIdentList(oldList).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    player);
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(PatternCraftingHudContent.class).setState(module.getHudState()).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    player);
        } else {
            super.playerStartWatching(player, mode);
        }
    }

    @Override
    public void playerStopWatching(EntityPlayer player, int mode) {
        super.playerStopWatching(player, mode);
        if (mode == 1) {
            localModeWatchers.remove(player);
        }
    }

    public BlockingMode getBlockingMode() {
        return module.getBlockingMode();
    }

    public void setBlockingMode(BlockingMode mode) {
        module.setBlockingMode(mode);
    }

    public boolean isBlockingModeFixed() {
        return module.isBlockingModeFixed();
    }

}
