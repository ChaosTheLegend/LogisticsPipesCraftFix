/*
 * Copyright (c) Krapht, 2011 "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0,
 * or MMPL. Please check the contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.pipes;

import java.util.*;

import logisticspipes.items.ItemModule;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import logisticspipes.api.IMUICompatiblePipe;
import logisticspipes.config.Configs;
import logisticspipes.gui.hud.HudChassisPipe;
import logisticspipes.gui.modularUI.GuiCraftingChassis;
import logisticspipes.interfaces.*;
import logisticspipes.interfaces.routing.*;
import logisticspipes.logisticspipes.PipeTransportLayer;
import logisticspipes.logisticspipes.TransportLayer;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.modules.abstractmodules.LogisticsModule.ModulePositionType;
import logisticspipes.network.PacketHandler;
import logisticspipes.pipes.upgrades.CraftingChassiUpgradeManager;
import logisticspipes.network.packets.hud.HUDStartWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopWatchingPacket;
import logisticspipes.network.packets.pipe.ChassiOrientationPacket;
import logisticspipes.network.packets.pipe.RequestChassiOrientationPacket;
import logisticspipes.network.packets.pipe.SendQueueContent;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.proxy.computers.interfaces.CCCommand;
import logisticspipes.proxy.computers.interfaces.CCType;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.DictResource;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.ticks.HudUpdateTick;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.LPPosition;

@CCType(name = "LogisticsChassiePipe")
public abstract class PipeCraftingChassi extends CoreRoutedPipe
        implements ICraftItems, IBufferItems, ISimpleInventoryEventHandler, ISendRoutedItem, IProvideItems,
        IHeadUpDisplayRendererProvider, ISendQueueContentRecieiver, IMUICompatiblePipe {

    public enum BlockingModeState {
        OFF,
        NORMAL,
        SMART
    }

    private boolean switchOrientationOnTick = true;
    private boolean init = false;

    private boolean convertFromMeta = false;

    // HUD
    public final LinkedList<ItemIdentifierStack> displayList = new LinkedList<>();
    public final PlayerCollectionList localModeWatchers = new PlayerCollectionList();
    private final HudChassisPipe hud;

    public PipeCraftingChassi(Item item) {
        super(item);
        hud = null;
        pointedDirection = ForgeDirection.UNKNOWN;
        _upgradeManager = new CraftingChassiUpgradeManager(this);
    }

    @Override
    protected List<IInventory> getConnectedRawInventories() {
        if (_cachedAdjacentInventories != null) {
            return _cachedAdjacentInventories;
        }
        List<IInventory> adjacent = new ArrayList<>(1);
        IInventory adjinv = getRealInventory();
        if (adjinv != null) {
            adjacent.add(adjinv);
        }
        _cachedAdjacentInventories = adjacent;
        return _cachedAdjacentInventories;
    }

    private List<IInventory> _cachedAdjacentInventories;

    public BlockingModeState blockMode = BlockingModeState.OFF;

    protected ItemStackHandler bufferInventory = new ItemStackHandler(9);

    protected ItemStackHandler patternInventory = new ItemStackHandler(36);

    protected ItemStackHandler upgradeInventory = new ItemStackHandler(4);

    protected final CraftingChassiUpgradeManager _upgradeManager;

    public void nextOrientation() {
        boolean found = false;
        ForgeDirection oldOrientation = pointedDirection;
        for (int l = 0; l < 6; ++l) {
            pointedDirection = ForgeDirection.values()[(pointedDirection.ordinal() + 1) % 6];
            if (isValidOrientation(pointedDirection)) {
                found = true;
                break;
            }
        }
        if (!found) {
            pointedDirection = ForgeDirection.UNKNOWN;
        }
        if (pointedDirection != oldOrientation) {
            clearCache();
            MainProxy.sendPacketToAllWatchingChunk(
                    getX(),
                    getZ(),
                    MainProxy.getDimensionForWorld(getWorld()),
                    PacketHandler.getPacket(ChassiOrientationPacket.class).setDir(pointedDirection).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()));
            refreshRender(true);
        }
    }

    public void setClientOrientation(ForgeDirection dir) {
        if (MainProxy.isClient(getWorld())) {
            pointedDirection = dir;
        }
    }

    private boolean isValidOrientation(ForgeDirection connection) {
        if (connection == ForgeDirection.UNKNOWN) {
            return false;
        }
        if (getRouter().isRoutedExit(connection)) {
            return false;
        }
        LPPosition pos = new LPPosition(getX(), getY(), getZ());
        pos.moveForward(connection);
        TileEntity tile = pos.getTileEntity(getWorld());

        if (tile == null) {
            return false;
        }
        if (SimpleServiceLocator.pipeInformationManager.isItemPipe(tile)) {
            return false;
        }
        return MainProxy.checkPipesConnections(container, tile, connection);
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_TEXTURE;
    }

    @Override
    public TextureType getRoutedTexture(ForgeDirection connection) {
        if (getRouter().isSubPoweredExit(connection)) {
            return Textures.LOGISTICSPIPE_SUBPOWER_TEXTURE;
        }
        return Textures.LOGISTICSPIPE_CHASSI_ROUTED_TEXTURE;
    }

    @Override
    public TextureType getNonRoutedTexture(ForgeDirection connection) {
        if (connection.equals(pointedDirection)) {
            return Textures.LOGISTICSPIPE_CHASSI_DIRECTION_TEXTURE;
        }
        if (isPowerProvider(connection)) {
            return Textures.LOGISTICSPIPE_POWERED_TEXTURE;
        }
        return Textures.LOGISTICSPIPE_CHASSI_NOTROUTED_TEXTURE;
    }

    @Override
    public void onNeighborBlockChange_Logistics() {
        if (!isValidOrientation(pointedDirection)) {
            if (MainProxy.isServer(getWorld())) {
                nextOrientation();
            }
        }
    }

    @Override
    public void onBlockPlaced() {
        super.onBlockPlaced();
        switchOrientationOnTick = true;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        try {
            super.readFromNBT(nbttagcompound);
            pointedDirection = ForgeDirection.values()[nbttagcompound.getInteger("Orientation") % 7];
            if (nbttagcompound.getInteger("Orientation") == 0) {
                convertFromMeta = true;
            }
            switchOrientationOnTick = (pointedDirection == ForgeDirection.UNKNOWN);
            this.bufferInventory.deserializeNBT(nbttagcompound.getCompoundTag("buffer_inventory"));
            this.patternInventory.deserializeNBT(nbttagcompound.getCompoundTag("pattern_inventory"));
            this.upgradeInventory.deserializeNBT(nbttagcompound.getCompoundTag("upgrade_inventory"));
            this._upgradeManager.updateUpgrades();
            this.blockMode = BlockingModeState.values()[nbttagcompound.getInteger("blocking_mode")];
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        if (pointedDirection == null) {
            pointedDirection = ForgeDirection.UNKNOWN;
        }
        nbttagcompound.setInteger("Orientation", pointedDirection.ordinal());
        nbttagcompound.setTag("buffer_inventory", this.bufferInventory.serializeNBT());
        nbttagcompound.setTag("pattern_inventory", this.patternInventory.serializeNBT());
        nbttagcompound.setTag("upgrade_inventory", this.upgradeInventory.serializeNBT());
        nbttagcompound.setInteger("blocking_mode", this.blockMode.ordinal());
    }

    @Override
    public void onAllowedRemoval() {

    }

    @Override
    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        if (MainProxy.isServer(getWorld())) {
            for (ItemStack pattern : patternInventory.getStacks()) {
                if (pattern == null) {
                    continue;
                }
                if (!(pattern.getItem() instanceof ItemModule)) {
                    continue;
                }
                if (!ItemModule.isCrafter(pattern)) {
                    continue;
                }
                LogisticsModule x = ((ItemModule) pattern.getItem()).getModuleForItem(pattern, null, this, this);
                if (x instanceof IRequireReliableTransport) {
                    logisticspipes.logisticspipes.ItemModuleInformationManager.readInformation(pattern, x);
                    ((IRequireReliableTransport) x).itemArrived(item, info);
                }
            }
        }
    }

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        if (MainProxy.isServer(getWorld())) {
            for (ItemStack pattern : patternInventory.getStacks()) {
                if (pattern == null) {
                    continue;
                }
                if (!(pattern.getItem() instanceof ItemModule)) {
                    continue;
                }
                if (!ItemModule.isCrafter(pattern)) {
                    continue;
                }
                LogisticsModule x = ((ItemModule) pattern.getItem()).getModuleForItem(pattern, null, this, this);
                if (x instanceof IRequireReliableTransport) {
                    logisticspipes.logisticspipes.ItemModuleInformationManager.readInformation(pattern, x);
                    ((IRequireReliableTransport) x).itemLost(item, info);
                }
            }
        }
    }

    @Override
    public int addToBuffer(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        if (MainProxy.isServer(getWorld())) {
            for (ItemStack pattern : patternInventory.getStacks()) {
                if (pattern == null) {
                    continue;
                }
                if (!(pattern.getItem() instanceof ItemModule)) {
                    continue;
                }
                if (!ItemModule.isCrafter(pattern)) {
                    continue;
                }
                LogisticsModule x = ((ItemModule) pattern.getItem()).getModuleForItem(pattern, null, this, this);
                if (x instanceof IBufferItems) {
                    logisticspipes.logisticspipes.ItemModuleInformationManager.readInformation(pattern, x);
                    return ((IBufferItems) x).addToBuffer(item, info);
                }
            }
        }
        return item.getStackSize();
    }

    @Override
    public void InventoryChanged(IInventory inventory) {}

    @Override
    public void ignoreDisableUpdateEntity() {
        if (switchOrientationOnTick) {
            switchOrientationOnTick = false;
            if (MainProxy.isServer(getWorld())) {
                nextOrientation();
            }
        }
        if (convertFromMeta && getWorld().getBlockMetadata(getX(), getY(), getZ()) != 0) {
            pointedDirection = ForgeDirection.values()[getWorld().getBlockMetadata(getX(), getY(), getZ()) % 6];
            getWorld().setBlockMetadataWithNotify(getX(), getY(), getZ(), 0, 0);
            convertFromMeta = false;
        }
        if (!init) {
            init = true;
            if (MainProxy.isClient(getWorld())) {
                MainProxy.sendPacketToServer(
                        PacketHandler.getPacket(RequestChassiOrientationPacket.class).setPosX(getX()).setPosY(getY())
                                .setPosZ(getZ()));
            }
        }
    }

    @Override
    public final LogisticsModule getLogisticsModule() {
        return null;
    }

    @Override
    public TransportLayer getTransportLayer() {
        if (_transportLayer == null) {
            _transportLayer = new PipeTransportLayer(this, this, getRouter());
        }
        return _transportLayer;
    }

    /*** IProvideItems ***/
    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        if (!isEnabled()) {
            return;
        }
        for (IFilter filter : filters) {
            if (filter.isBlocked() == filter.isFilteredItem(tree.getRequestType()) || filter.blockProvider()) {
                return;
            }
        }
        for (ItemStack pattern : patternInventory.getStacks()) {
            if (pattern == null) {
                continue;
            }
            if (!(pattern.getItem() instanceof ItemModule)) {
                continue;
            }
            if (!ItemModule.isCrafter(pattern)) {
                continue;
            }
            LogisticsModule x = ((ItemModule) pattern.getItem()).getModuleForItem(pattern, null, this, this);

            if (x instanceof IProvideItems) {
                logisticspipes.logisticspipes.ItemModuleInformationManager.readInformation(pattern, x);
                ((IProvideItems) x).canProvide(tree, root, filters);
            }
        }
    }

    @Override
    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination,
            IAdditionalTargetInformation info) {
        if (!isEnabled()) {
            return null;
        }
        for (ItemStack pattern : patternInventory.getStacks()) {
            if (pattern == null) {
                continue;
            }
            if (!(pattern.getItem() instanceof ItemModule)) {
                continue;
            }
            if (!ItemModule.isCrafter(pattern)) {
                continue;
            }
            LogisticsModule x = ((ItemModule) pattern.getItem()).getModuleForItem(pattern, null, this, this);

            if (x instanceof IProvideItems) {
                logisticspipes.logisticspipes.ItemModuleInformationManager.readInformation(pattern, x);
                LogisticsOrder result = ((IProvideItems) x).fullFill(promise, destination, info);
                if (result != null) {
                    spawnParticle(Particles.WhiteParticle, 2);
                    return result;
                }
            }
        }
        return null;
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {
        if (!isEnabled()) {
            return;
        }
        for (ItemStack pattern : patternInventory.getStacks()) {
            if (pattern == null) {
                continue;
            }
            if (!(pattern.getItem() instanceof ItemModule)) {
                continue;
            }
            if (!ItemModule.isCrafter(pattern)) {
                continue;
            }
            LogisticsModule x = ((ItemModule) pattern.getItem()).getModuleForItem(pattern, null, this, this);

            if (x instanceof IProvideItems) {
                logisticspipes.logisticspipes.ItemModuleInformationManager.readInformation(pattern, x);
                ((IProvideItems) x).getAllItems(list, filter);
            }
        }
    }

    @Override
    public ItemSendMode getItemSendMode() {
        return ItemSendMode.Normal;
    }

    @Override
    public IHeadUpDisplayRenderer getRenderer() {
        return null;
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
                    PacketHandler.getPacket(SendQueueContent.class)
                            .setIdentList(ItemIdentifierStack.getListSendQueue(_sendQueue)).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    player);
        } else {
            super.playerStartWatching(player, mode);
        }
    }

    @Override
    public void playerStopWatching(EntityPlayer player, int mode) {
        super.playerStopWatching(player, mode);
        localModeWatchers.remove(player);
    }

    @Override
    public int sendQueueChanged(boolean force) {
        if (MainProxy.isServer(getWorld())) {
            if (Configs.MULTI_THREAD_NUMBER > 0 && !force) {
                HudUpdateTick.add(getRouter());
            } else {
                if (localModeWatchers != null && localModeWatchers.size() > 0) {
                    LinkedList<ItemIdentifierStack> items = ItemIdentifierStack.getListSendQueue(_sendQueue);
                    MainProxy.sendToPlayerList(
                            PacketHandler.getPacket(SendQueueContent.class).setIdentList(items).setPosX(getX())
                                    .setPosY(getY()).setPosZ(getZ()),
                            localModeWatchers);
                    return items.size();
                }
            }
        }
        return 0;
    }

    @Override
    public void handleSendQueueItemIdentifierList(Collection<ItemIdentifierStack> _allItems) {
        displayList.clear();
        displayList.addAll(_allItems);
    }

    @Override
    public void setTile(TileEntity tile) {
        super.setTile(tile);
    }

    @Override
    public int getSourceID() {
        return getRouterId();
    }

    @Override
    public Set<ItemIdentifier> getSpecificInterests() {
        Set<ItemIdentifier> l1 = new TreeSet<>();
        // if we don't have a pointed inventory we can't be interested in anything
        if (getRealInventory() == null) {
            return l1;
        }

        for (ItemStack pattern : patternInventory.getStacks()) {
            if (pattern == null) {
                continue;
            }
            if (!(pattern.getItem() instanceof ItemModule)) {
                continue;
            }
            if (!ItemModule.isCrafter(pattern)) {
                continue;
            }
            LogisticsModule x = ((ItemModule) pattern.getItem()).getModuleForItem(pattern, null, this, this);
            if (x != null) {
                logisticspipes.logisticspipes.ItemModuleInformationManager.readInformation(pattern, x);
                if (x.interestedInAttachedInventory()) {
                    IInventoryUtil inv = getSneakyInventory(false, x.getSlot(), x.getPositionInt());
                    if (inv != null) {
                        Set<ItemIdentifier> items = inv.getItems();
                        l1.addAll(items);
                        for (ItemIdentifier id : items) {
                            l1.add(id.getIgnoringNBT());
                        }
                        if (x.interestedInUndamagedID()) {
                            for (ItemIdentifier id : items) {
                                l1.add(id.getUndamaged());
                            }
                        }
                    }
                }
                Collection<ItemIdentifier> current = x.getSpecificInterests();
                if (current != null) {
                    l1.addAll(current);
                }
            }
        }
        return l1;
    }

    @Override
    public boolean hasGenericInterests() {
        if (getRealInventory() == null) {
            return false;
        }
        for (ItemStack pattern : patternInventory.getStacks()) {
            if (pattern == null) {
                continue;
            }
            if (!(pattern.getItem() instanceof ItemModule)) {
                continue;
            }
            if (!ItemModule.isCrafter(pattern)) {
                continue;
            }
            LogisticsModule x = ((ItemModule) pattern.getItem()).getModuleForItem(pattern, null, this, this);

            if (x != null && x.hasGenericInterests()) {
                return true;
            }
        }
        return false;
    }

    @CCCommand(description = "Returns the LogisticsModule for the given slot number starting by 1")
    public LogisticsModule getModuleInSlot(Double i) {
        return null;
    }

    public final LinkedList<LogisticsOrder> _extras = new LinkedList<>();

    @Override
    public void registerExtras(IPromise promise) {
        if (!(promise instanceof LogisticsPromise)) {
            throw new UnsupportedOperationException("Extra has to be an item for a chassis pipe");
        }
        ItemIdentifierStack stack = new ItemIdentifierStack(
                ((LogisticsPromise) promise).item,
                ((LogisticsPromise) promise).numberOfItems);
        _extras.add(new LogisticsItemOrder(new DictResource(stack, null), null, ResourceType.EXTRA, null));
    }

    @Override
    public ICraftingTemplate addCrafting(IResource toCraft) {
        for (ItemStack pattern : patternInventory.getStacks()) {
            if(pattern == null) continue;
            if(!(pattern.getItem() instanceof ItemModule)) continue;
            if(!ItemModule.isCrafter(pattern)) continue;
            LogisticsModule x = ((ItemModule) pattern.getItem()).getModuleForItem(pattern, null, this, this);


            if (x instanceof ICraftItems) {
                logisticspipes.logisticspipes.ItemModuleInformationManager.readInformation(pattern, x);
                if (((ICraftItems) x).canCraft(toCraft)) {
                    return ((ICraftItems) x).addCrafting(toCraft);
                }
            }
        }
        return null;
    }

    @Override
    public List<ItemIdentifierStack> getConfiguredCraftResults() {
        List<ItemIdentifierStack> craftables = null;
        for (ItemStack pattern : patternInventory.getStacks() ) {
            if(pattern == null) continue;
            if(!(pattern.getItem() instanceof ItemModule)) continue;
            if(!ItemModule.isCrafter(pattern)) continue;
            LogisticsModule x = ((ItemModule) pattern.getItem()).getModuleForItem(pattern, null, this, this);

            if (x instanceof ICraftItems) {
                if (craftables == null) {
                    craftables = new LinkedList<>();
                }
                logisticspipes.logisticspipes.ItemModuleInformationManager.readInformation(pattern, x);
                List<ItemIdentifierStack> results = ((ICraftItems) x).getConfiguredCraftResults();
                if (results != null) {
                    craftables.addAll(results);
                }
            }
        }
        return craftables;
    }

    @Override
    public boolean canCraft(IResource toCraft) {
        for (ItemStack pattern : patternInventory.getStacks()) {
            if(pattern == null) continue;
            if(!(pattern.getItem() instanceof ItemModule)) continue;
            if(!ItemModule.isCrafter(pattern)) continue;

            LogisticsModule module = ((ItemModule) pattern.getItem()).getModuleForItem(pattern, null, this, this);
            if(module instanceof ICraftItems){
                logisticspipes.logisticspipes.ItemModuleInformationManager.readInformation(pattern, module);
                return ((ICraftItems) module).canCraft(toCraft);
            }
        }
        return false;
    }

    @Override
    public ISlotUpgradeManager getUpgradeManager(ModulePositionType slot, int positionInt) {
        _upgradeManager.updateUpgrades();
        return _upgradeManager;
    }

    @Override
    public int getTodo() {
        return 0;
    }

    @Override
    public String getId() {
        return "crafting_chassis";
    }

    @Override
    public int getGuiWidth() {
        return 184;
    }

    @Override
    public int getGuiHeight() {
        return 200;
    }

    public abstract int getChassisTier();

    @Override
    public void onWrenchClicked(EntityPlayer entityplayer) {
        openGui(entityplayer, this);
    }

    @Override
    public void addUIWidgets(ModularPanel panel, PosGuiData data, PanelSyncManager syncManager) {
        GuiCraftingChassis gui = new GuiCraftingChassis(this);
        gui.addWidgets(panel, data, syncManager);
    }

    public IItemHandlerModifiable getBufferInventory() {
        return bufferInventory;
    }

    public IItemHandlerModifiable getPatternInventory() {
        return patternInventory;
    }

    public IItemHandlerModifiable getUpgradeInventory() { return upgradeInventory; }
}
