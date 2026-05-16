package logisticspipes.pipes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.ItemMemoryChip;
import logisticspipes.crafting.ModuleItemCrafting;
import logisticspipes.crafting.PipeItemsPatternSatelliteLogistics;
import logisticspipes.gui.hud.HUDPatternCrafting;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
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

import logisticspipes.interfaces.IChangeListener;
import logisticspipes.interfaces.IHeadUpDisplayRenderer;
import logisticspipes.interfaces.IHeadUpDisplayRendererProvider;
import logisticspipes.interfaces.IOrderManagerContentReceiver;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.interfaces.routing.IFluidSink;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.hud.HUDStartWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopWatchingPacket;
import logisticspipes.network.packets.orderer.OrdererManagerContent;
import logisticspipes.network.packets.orderer.PatternCraftingHudContent;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.routing.order.LogisticsFluidOrderManager;
import logisticspipes.security.SecuritySettings;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.transport.PipeTransportLogistics;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.WorldUtil;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

public class PipeItemsPatternCraftingLogistics extends CoreRoutedPipe implements ICraftItems, IRequireReliableTransport,
        IFluidSink, IHeadUpDisplayRendererProvider, IChangeListener, IOrderManagerContentReceiver {

    public enum BlockingMode {
        OFF,
        BLOCKING,
        SMART
    }

    private static final String CONNECTED_INVENTORY_DIRECTION_TAG = "patternConnectedInventoryDirection";
    private static final String LINKED_PATTERN_SATELLITES_TAG = "linkedPatternSatelliteIds";

    private final ModuleItemCrafting module;
    private final LogisticsFluidOrderManager fluidOrderManager;
    public final LinkedList<ItemIdentifierStack> oldList = new LinkedList<>();
    public final LinkedList<ItemIdentifierStack> displayList = new LinkedList<>();
    private final LinkedList<ItemIdentifierStack> oldResultList = new LinkedList<>();
    private final LinkedList<ItemIdentifierStack> displayResultList = new LinkedList<>();
    public final PlayerCollectionList localModeWatchers = new PlayerCollectionList();
    private final HUDPatternCrafting HUD = new HUDPatternCrafting(this);
    private ForgeDirection connectedInventoryDirection = ForgeDirection.UNKNOWN;
    private AdjacentTile cachedConnectedInventory;
    private boolean doContentUpdate = true;
    private final Set<Integer> linkedPatternSatelliteIds = new TreeSet<>();

    public PipeItemsPatternCraftingLogistics(Item item) {
        super(new PipeTransportLogistics(true) {

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
        throttleTime = 40;
    }

    @Override
    protected void onAllowedRemoval() {
        module.onAllowedRemoval();
    }

    @Override
    public void onNeighborBlockChange(int blockId) {
        cachedConnectedInventory = null;
        super.onNeighborBlockChange(blockId);
    }

    @Override
    public boolean disconnectPipe(TileEntity tile, ForgeDirection dir) {
        if (SimpleServiceLocator.pipeInformationManager.isPipe(tile, false)) {
            return false;
        }
        if (tile instanceof IInventory || tile instanceof IFluidHandler) {
            return !isSelectedInventory(tile, dir);
        }
        return true;
    }

    @Override
    protected boolean handleClick(EntityPlayer entityplayer, SecuritySettings settings) {
        if (entityplayer.isSneaking()
                && entityplayer.getCurrentEquippedItem() != null
                && entityplayer.getCurrentEquippedItem().getItem() == LogisticsPipes.LogisticsMemoryChip) {
            if (MainProxy.isServer(entityplayer.worldObj)) {
                if (settings == null || settings.openGui) {
                    int added = addLinkedPatternSatelliteIds(
                            ItemMemoryChip.getPatternSatelliteIds(entityplayer.getCurrentEquippedItem()));
                    entityplayer.addChatComponentMessage(new ChatComponentText(
                            added == 0
                                    ? "No new pattern satellites linked"
                                    : "Linked " + added + " pattern satellite" + (added == 1 ? "" : "s")));
                } else {
                    entityplayer.addChatComponentMessage(new ChatComponentTranslation("lp.chat.permissiondenied"));
                }
            }
            return true;
        }
        if (!entityplayer.isSneaking()
                || !SimpleServiceLocator.toolWrenchHandler.isWrenchEquipped(entityplayer)
                || !SimpleServiceLocator.toolWrenchHandler.canWrench(entityplayer, getX(), getY(), getZ())) {
            return false;
        }
        if (MainProxy.isServer(entityplayer.worldObj)) {
            if (settings == null || settings.openGui) {
                cycleConnectedInventory(entityplayer);
            } else {
                entityplayer.addChatComponentMessage(new ChatComponentTranslation("lp.chat.permissiondenied"));
            }
        }
        SimpleServiceLocator.toolWrenchHandler.wrenchUsed(entityplayer, getX(), getY(), getZ());
        return true;
    }

    public AdjacentTile getConnectedInventoryTile() {
        if (isCachedConnectedInventoryValid()) {
            return cachedConnectedInventory;
        }
        cachedConnectedInventory = resolveConnectedInventoryTile();
        return cachedConnectedInventory;
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

    private boolean isCachedConnectedInventoryValid() {
        return cachedConnectedInventory != null
                && cachedConnectedInventory.orientation == connectedInventoryDirection
                && cachedConnectedInventory.tile != null
                && !cachedConnectedInventory.tile.isInvalid()
                && getAdjacentTile(cachedConnectedInventory.orientation) == cachedConnectedInventory.tile
                && isSelectableInventory(cachedConnectedInventory.tile, cachedConnectedInventory.orientation);
    }

    private AdjacentTile resolveConnectedInventoryTile() {
        AdjacentTile selected = getSelectableAdjacentInventory(connectedInventoryDirection);
        if (selected != null) {
            return selected;
        }
        if (connectedInventoryDirection != ForgeDirection.UNKNOWN) {
            return null;
        }
        List<AdjacentTile> inventories = getSelectableAdjacentInventories();
        if (inventories.isEmpty()) {
            return null;
        }
        selected = inventories.get(0);
        connectedInventoryDirection = selected.orientation;
        return selected;
    }

    private boolean isSelectedInventory(TileEntity tile, ForgeDirection direction) {
        AdjacentTile selected = getConnectedInventoryTile();
        return selected != null
                && selected.tile == tile
                && (selected.orientation == direction || selected.orientation == getDirectionTo(tile));
    }

    private void cycleConnectedInventory(EntityPlayer player) {
        List<AdjacentTile> inventories = getSelectableAdjacentInventories();
        if (inventories.isEmpty()) {
            connectedInventoryDirection = ForgeDirection.UNKNOWN;
            cachedConnectedInventory = null;
            refreshSelectedInventoryConnection();
            player.addChatComponentMessage(new ChatComponentText("Pattern crafting target: none"));
            return;
        }
        int current = -1;
        for (int i = 0; i < inventories.size(); i++) {
            if (inventories.get(i).orientation == connectedInventoryDirection) {
                current = i;
                break;
            }
        }
        AdjacentTile selected = inventories.get((current + 1) % inventories.size());
        connectedInventoryDirection = selected.orientation;
        cachedConnectedInventory = selected;
        refreshSelectedInventoryConnection();
        player.addChatComponentMessage(new ChatComponentText("Pattern crafting target: "
                + connectedInventoryDirection.name().toLowerCase(Locale.ENGLISH)));
    }

    private void refreshSelectedInventoryConnection() {
        clearCache();
        triggerConnectionCheck();
        connectionUpdate();
        refreshRender(false);
    }

    private List<AdjacentTile> getSelectableAdjacentInventories() {
        List<AdjacentTile> inventories = new ArrayList<>();
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            AdjacentTile tile = getSelectableAdjacentInventory(direction);
            if (tile != null) {
                inventories.add(tile);
            }
        }
        return inventories;
    }

    private AdjacentTile getSelectableAdjacentInventory(ForgeDirection direction) {
        if (direction == null || direction == ForgeDirection.UNKNOWN) {
            return null;
        }
        TileEntity tile = getAdjacentTile(direction);
        if (!isSelectableInventory(tile, direction)) {
            return null;
        }
        return new AdjacentTile(tile, direction);
    }

    private TileEntity getAdjacentTile(ForgeDirection direction) {
        if (direction == null || direction == ForgeDirection.UNKNOWN) {
            return null;
        }
        return new WorldUtil(getWorld(), getX(), getY(), getZ()).getAdjacentTileEntitie(direction);
    }

    private ForgeDirection getDirectionTo(TileEntity tile) {
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (getAdjacentTile(direction) == tile) {
                return direction;
            }
        }
        return ForgeDirection.UNKNOWN;
    }

    private boolean isSelectableInventory(TileEntity tile, ForgeDirection direction) {
        boolean hasInventory = tile instanceof IInventory && ((IInventory) tile).getSizeInventory() > 0;
        boolean hasTank = tile instanceof IFluidHandler
                && ((IFluidHandler) tile).getTankInfo(direction.getOpposite()) != null
                && ((IFluidHandler) tile).getTankInfo(direction.getOpposite()).length > 0;
        return (hasInventory || hasTank)
                && !SimpleServiceLocator.pipeInformationManager.isPipe(tile, false)
                && !isSideBlocked(direction, false)
                && transport.canPipeConnect(tile, direction);
    }

    @Override
    public void enabledUpdateEntity() {
        super.enabledUpdateEntity();
        if (doContentUpdate) {
            checkContentUpdate();
        }
        checkResultUpdate();
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

    @Override
    public ItemSendMode getItemSendMode() {
        return ItemSendMode.Normal;
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

    public List<ItemIdentifierStack> getHudCraftResults() {
        return displayResultList;
    }

    @Override
    public Set<ItemIdentifier> getSpecificInterests() {
        return module.getCraftedItems();
    }

    @Override
    public double getLoadFactor() {
        return (_orderItemManager.totalAmountCountInAllOrders() + fluidOrderManager.totalAmountCountInAllOrders() + 63.0) / 64.0;
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

    private void checkResultUpdate() {
        LinkedList<ItemIdentifierStack> all = new LinkedList<>(getConfiguredCraftResults());
        if (!oldResultList.equals(all)) {
            oldResultList.clear();
            oldResultList.addAll(all);
            MainProxy.sendToPlayerList(
                    PacketHandler.getPacket(PatternCraftingHudContent.class).setIdentList(all).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    localModeWatchers);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        nbttagcompound.setInteger(CONNECTED_INVENTORY_DIRECTION_TAG, connectedInventoryDirection.ordinal());
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
        connectedInventoryDirection = nbttagcompound.hasKey(CONNECTED_INVENTORY_DIRECTION_TAG)
                ? directionFromOrdinal(nbttagcompound.getInteger(CONNECTED_INVENTORY_DIRECTION_TAG))
                : ForgeDirection.UNKNOWN;
        linkedPatternSatelliteIds.clear();
        for (int satelliteId : nbttagcompound.getIntArray(LINKED_PATTERN_SATELLITES_TAG)) {
            if (satelliteId > 0) {
                linkedPatternSatelliteIds.add(satelliteId);
            }
        }
        cachedConnectedInventory = null;
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeInt(connectedInventoryDirection.ordinal());
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        connectedInventoryDirection = directionFromOrdinal(data.readInt());
        cachedConnectedInventory = null;
    }

    private ForgeDirection directionFromOrdinal(int ordinal) {
        ForgeDirection[] values = ForgeDirection.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return ForgeDirection.UNKNOWN;
        }
        return values[ordinal];
    }

    @Override
    public void setOrderManagerContent(Collection<ItemIdentifierStack> list) {
        displayList.clear();
        displayList.addAll(list);
    }

    public void setHudResultContent(Collection<ItemIdentifierStack> list) {
        displayResultList.clear();
        displayResultList.addAll(list);
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
            LinkedList<ItemIdentifierStack> results = new LinkedList<>(getConfiguredCraftResults());
            oldResultList.clear();
            oldResultList.addAll(results);
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(OrdererManagerContent.class).setIdentList(oldList).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    player);
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(PatternCraftingHudContent.class).setIdentList(results).setPosX(getX())
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
