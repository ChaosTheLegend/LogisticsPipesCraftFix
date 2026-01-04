/*
 * Copyright (c) Krapht, 2011 "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0,
 * or MMPL. Please check the contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.pipes;

import cpw.mods.fml.client.FMLClientHandler;
import logisticspipes.LPConstants;
import logisticspipes.LogisticsPipes;
import logisticspipes.config.Configs;
import logisticspipes.gui.GuiChassiPipe;
import logisticspipes.gui.hud.HudChassisPipe;
import logisticspipes.interfaces.*;
import logisticspipes.interfaces.routing.*;
import logisticspipes.items.ItemModule;
import logisticspipes.logisticspipes.ChassiTransportLayer;
import logisticspipes.logisticspipes.ItemModuleInformationManager;
import logisticspipes.logisticspipes.TransportLayer;
import logisticspipes.logisticspipes.PipeTransportLayer;
import logisticspipes.modules.ChassiModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.modules.abstractmodules.LogisticsModule.ModulePositionType;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.guis.pipe.DummyPipeGuiProvider;
import logisticspipes.network.packets.hud.HUDStartWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopWatchingPacket;
import logisticspipes.network.packets.pipe.ChassiOrientationPacket;
import logisticspipes.network.packets.pipe.ChassiePipeModuleContent;
import logisticspipes.network.packets.pipe.RequestChassiOrientationPacket;
import logisticspipes.network.packets.pipe.SendQueueContent;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.upgrades.ModuleUpgradeManager;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.proxy.computers.interfaces.CCCommand;
import logisticspipes.proxy.computers.interfaces.CCType;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.security.SecuritySettings;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.ticks.HudUpdateTick;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.LPPosition;
import lombok.Getter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.*;

@CCType(name = "LogisticsChassiePipe")
public abstract class PipeCraftingChassi extends CoreRoutedPipe
        implements ICraftItems, IBufferItems, ISimpleInventoryEventHandler, ISendRoutedItem, IProvideItems,
        IHeadUpDisplayRendererProvider, ISendQueueContentRecieiver {

    public static ResourceLocation DummyGUI = new ResourceLocation(
        "logisticspipes",
        "textures/gui/chassipipe_size2.png");

    private final ChassiModule _module;
    private final ItemIdentifierInventory _moduleInventory;
    private final ModuleUpgradeManager[] _upgradeManagers;
    private boolean switchOrientationOnTick = true;
    private boolean init = false;

    private boolean convertFromMeta = false;

    // HUD
    public final LinkedList<ItemIdentifierStack> displayList = new LinkedList<>();
    public final PlayerCollectionList localModeWatchers = new PlayerCollectionList();
    private final HudChassisPipe hud;

    public PipeCraftingChassi(Item item) {
        super(item);
        _moduleInventory = new ItemIdentifierInventory(getChassiSize(), "Crafting Chassi pipe", 1);
        _upgradeManagers = new ModuleUpgradeManager[getChassiSize()];
        _module = null;
        hud = null;
        pointedDirection = ForgeDirection.UNKNOWN;
    }

    @Override
    protected List<IInventory> getConnectedRawInventories() {
        return Collections.emptyList();
    }

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

    public IInventory getModuleInventory() {
        return _moduleInventory;
    }

    public ModuleUpgradeManager getModuleUpgradeManager(int slot) {
        return _upgradeManagers[slot];
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
            _moduleInventory.readFromNBT(nbttagcompound, "chassi");
            pointedDirection = ForgeDirection.values()[nbttagcompound.getInteger("Orientation") % 7];
            if (nbttagcompound.getInteger("Orientation") == 0) {
                convertFromMeta = true;
            }
            switchOrientationOnTick = (pointedDirection == ForgeDirection.UNKNOWN);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        _moduleInventory.writeToNBT(nbttagcompound, "chassi");
        if (pointedDirection == null) {
            pointedDirection = ForgeDirection.UNKNOWN;
        }
        nbttagcompound.setInteger("Orientation", pointedDirection.ordinal());
    }

    @Override
    public void onAllowedRemoval() {
        _moduleInventory.removeListener(this);
        if (MainProxy.isServer(getWorld())) {
            _moduleInventory.dropContents(getWorld(), getX(), getY(), getZ());
        }
    }

    @Override
    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {
    }

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {
    }

    @Override
    public int addToBuffer(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        return item.getStackSize();
    }

    @Override
    public void InventoryChanged(IInventory inventory) {
    }

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

    public abstract int getChassiSize();

    @Override
    public final LogisticsModule getLogisticsModule() {
        return _module;
    }

    @Override
    public TransportLayer getTransportLayer() {
        if (_transportLayer == null) {
            _transportLayer = new PipeTransportLayer(this, this, getRouter());
        }
        return _transportLayer;
    }

    @Override
    public boolean handleClick(EntityPlayer entityplayer, SecuritySettings settings) {
        if (MainProxy.isServer(entityplayer.worldObj)) {
            NewGuiHandler.getGui(DummyPipeGuiProvider.class).setTilePos(container).open(entityplayer);
        }
        return true;
    }

    /*** IProvideItems ***/
    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
    }

    @Override
    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination,
            IAdditionalTargetInformation info) {
        return null;
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {
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
                    PacketHandler.getPacket(ChassiePipeModuleContent.class)
                            .setIdentList(ItemIdentifierStack.getListFromInventory(_moduleInventory)).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    player);
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

    public void handleModuleItemIdentifierList(Collection<ItemIdentifierStack> _allItems) {
        _moduleInventory.handleItemIdentifierList(_allItems);
    }

    public void handleContentItemIdentifierList(Collection<ItemIdentifierStack> _allItems) {
        _moduleInventory.handleItemIdentifierList(_allItems);
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

    public ChassiModule getModules() {
        return _module;
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
        return Collections.emptySet();
    }

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    @CCCommand(description = "Returns the LogisticsModule for the given slot number starting by 1")
    public LogisticsModule getModuleInSlot(Double i) {
        return null;
    }

    @CCCommand(description = "Returns the size of this Chassie pipe")
    public Integer getChassieSize() {
        return getChassiSize();
    }

    public abstract ResourceLocation getChassiGUITexture();

    /**
     * ICraftItems
     */
    public final LinkedList<LogisticsOrder> _extras = new LinkedList<>();

    @Override
    public void registerExtras(IPromise promise) {
    }

    @Override
    public ICraftingTemplate addCrafting(IResource toCraft) {
        return null;
    }

    @Override
    public List<ItemIdentifierStack> getConfiguredCraftResults() {
        return null;
    }

    @Override
    public boolean canCraft(IResource toCraft) {
        return false;
    }

    @Override
    public ISlotUpgradeManager getUpgradeManager(ModulePositionType slot, int positionInt) {
        return null;
    }

    @Override
    public int getTodo() {
        return 0;
    }

    public static class ChassiTargetInformation implements IAdditionalTargetInformation {

        @Getter
        private final int moduleSlot;

        public ChassiTargetInformation(int slot) {
            moduleSlot = slot;
        }
    }
}
