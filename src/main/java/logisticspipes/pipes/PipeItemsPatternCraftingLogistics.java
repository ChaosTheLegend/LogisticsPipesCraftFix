package logisticspipes.pipes;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import logisticspipes.crafting.ModuleItemCrafting;
import logisticspipes.gui.hud.HUDPatternCrafting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;

import logisticspipes.interfaces.IChangeListener;
import logisticspipes.interfaces.IHeadUpDisplayRenderer;
import logisticspipes.interfaces.IHeadUpDisplayRendererProvider;
import logisticspipes.interfaces.IOrderManagerContentReceiver;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraftItems;
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
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.transport.PipeTransportLogistics;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

public class PipeItemsPatternCraftingLogistics extends CoreRoutedPipe implements ICraftItems, IRequireReliableTransport,
        IHeadUpDisplayRendererProvider, IChangeListener, IOrderManagerContentReceiver {

    public enum BlockingMode {
        OFF,
        BLOCKING,
        SMART
    }

    private final ModuleItemCrafting module;
    public final LinkedList<ItemIdentifierStack> oldList = new LinkedList<>();
    public final LinkedList<ItemIdentifierStack> displayList = new LinkedList<>();
    private final LinkedList<ItemIdentifierStack> oldResultList = new LinkedList<>();
    private final LinkedList<ItemIdentifierStack> displayResultList = new LinkedList<>();
    public final PlayerCollectionList localModeWatchers = new PlayerCollectionList();
    private final HUDPatternCrafting HUD = new HUDPatternCrafting(this);
    private boolean doContentUpdate = true;

    public PipeItemsPatternCraftingLogistics(Item item) {
        super(new PipeTransportLogistics(true), item);
        module = new ModuleItemCrafting(this);
        _orderItemManager = new logisticspipes.routing.order.LogisticsItemOrderManager(this, this);
        throttleTime = 40;
    }

    @Override
    protected void onAllowedRemoval() {
        module.onAllowedRemoval();
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
        return module.getTodo();
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
        return (_orderItemManager.totalAmountCountInAllOrders() + 63.0) / 64.0;
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
    public void setOrderManagerContent(Collection<ItemIdentifierStack> list) {
        displayList.clear();
        displayList.addAll(list);
    }

    public void setHudResultContent(Collection<ItemIdentifierStack> list) {
        displayResultList.clear();
        displayResultList.addAll(list);
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
