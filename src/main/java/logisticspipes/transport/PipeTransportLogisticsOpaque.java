/*
 * Copyright (c) Krapht, 2011 "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0,
 * or MMPL. Please check the contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.transport;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.config.Configs;
import logisticspipes.interfaces.IBufferItems;
import logisticspipes.proxy.MainProxy;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;

/**
 * Drop-in replacement for {@link PipeTransportLogistics} used when {@link Configs#TELEPORTING_ITEMS} is enabled.
 *
 * Instead of tracking every item as an {@link LPTravelingItem} that advances its position a little every tick
 * (which is what makes the traveling item visible/renderable), items are held in a simple delay queue and
 * teleported directly onto the next pipe once their delay elapses. No traveling item entity ever exists for
 * these items, so there is nothing to render and nothing to sync to clients - this is meant to be used instead
 * of putting an Opaque upgrade in every pipe, at the cost of items no longer being visible at all.
 *
 * Routing decisions (destination assignment, jam avoidance, buffering while unroutable) are untouched - they
 * still happen exactly as in the base class via {@link #resolveDestination(LPTravelingItemServer)} and
 * {@code _itemBuffer}. Only what happens once a valid next hop has been resolved differs: instead of the item
 * being added to {@link #items} and advancing every tick, it is teleported after a flat delay.
 */
public class PipeTransportLogisticsOpaque extends PipeTransportLogistics {

    private static final float PIPE_SEGMENT_DISTANCE = 1.0F;

    private final List<PendingTeleport> _inFlight = new LinkedList<>();

    public PipeTransportLogisticsOpaque(boolean isRouted) {
        super(isRouted);
    }

    private static final class PendingTeleport {

        private final LPTravelingItemServer item;
        private final ForgeDirection direction;
        private int ticksRemaining;

        private PendingTeleport(LPTravelingItemServer item, ForgeDirection direction, int ticksRemaining) {
            this.item = item;
            this.direction = direction;
            this.ticksRemaining = ticksRemaining;
        }
    }

    @Override
    public void updateEntity() {
        if (MainProxy.isServer(getWorld())) {
            tickInFlightItems();
        }
        // handles _itemBuffer (jam/reroute retries) and moveSolids() - moveSolids() is a no-op for us since we
        // never add anything to "items".
        super.updateEntity();
    }

    private void tickInFlightItems() {
        if (_inFlight.isEmpty()) {
            return;
        }
        List<PendingTeleport> arrived = null;
        Iterator<PendingTeleport> iterator = _inFlight.iterator();
        while (iterator.hasNext()) {
            PendingTeleport pending = iterator.next();
            if (--pending.ticksRemaining <= 0) {
                if (arrived == null) {
                    arrived = new LinkedList<>();
                }
                arrived.add(pending);
                iterator.remove();
            }
        }
        if (arrived == null) {
            return;
        }
        for (PendingTeleport pending : arrived) {
            deliver(pending);
        }
    }

    private void deliver(PendingTeleport pending) {
        LPTravelingItemServer item = pending.item;
        if (item.isCorrupted()) {
            return;
        }
        TileEntity tile = container.getTile(pending.direction);
        handleTileReachedServer(item, tile, pending.direction);
    }

    @Override
    public int injectItem(LPTravelingItem item, ForgeDirection inputOrientation) {
        if (item.isCorrupted()) {
            return 0;
        }
        getPipe().triggerDebug();

        int originalCount = item.getItemIdentifierStack().getStackSize();

        item.input = inputOrientation;

        if (MainProxy.isServer(container.getWorldObj())) {
            LPTravelingItemServer serverItem = (LPTravelingItemServer) item;
            readjustSpeed(serverItem);
            ForgeDirection output = resolveDestination(serverItem);
            if (output == null) {
                // buffered - _itemBuffer will retry this item, see PipeTransportLogistics.updateEntity()
                return originalCount - item.getItemIdentifierStack().getStackSize();
            }
            getPipe().debug
                    .log("Injected Item (teleporting): [%s, %s] (%s)", serverItem.input, output, serverItem.getInfo());
            if (output == ForgeDirection.UNKNOWN) {
                dropItem(serverItem);
            } else {
                scheduleArrival(serverItem, output);
            }
        }
        // Client: teleporting pipes never render traveling items, nothing to keep track of.
        return originalCount - item.getItemIdentifierStack().getStackSize();
    }

    @Override
    protected void reverseItem(LPTravelingItemServer item) {
        if (item.isCorrupted()) {
            return;
        }

        if (getPipe() instanceof IBufferItems) {
            item.getItemIdentifierStack().setStackSize(
                    ((IBufferItems) getPipe())
                            .addToBuffer(item.getItemIdentifierStack(), item.getAdditionalTargetInformation()));
            if (item.getItemIdentifierStack().getStackSize() <= 0) {
                return;
            }
        }

        // Assign new ID to update ItemStack content
        item.id = item.getNextId();

        item.input = item.output.getOpposite();

        readjustSpeed(item);
        ForgeDirection output = resolveDestination(item);
        if (output == null) {
            return; // buffered - _itemBuffer will retry this item
        } else if (output == ForgeDirection.UNKNOWN) {
            dropItem(item);
            return;
        }

        scheduleArrival(item, output);
    }

    private void scheduleArrival(LPTravelingItemServer item, ForgeDirection direction) {
        item.output = direction;
        int ticks = Math.max(1, Math.round(PIPE_SEGMENT_DISTANCE * Configs.TELEPORT_TICKS_PER_PIPE));
        _inFlight.add(new PendingTeleport(item, direction, ticks));
    }

    @Override
    public void dropBuffer() {
        super.dropBuffer();
        for (PendingTeleport pending : _inFlight) {
            MainProxy.dropItems(
                    getWorld(),
                    pending.item.getItemIdentifierStack().makeNormalStack(),
                    getPipe().getX(),
                    getPipe().getY(),
                    getPipe().getZ());
        }
        _inFlight.clear();
    }

    @Override
    public List<ItemStack> dropContents() {
        List<ItemStack> list = super.dropContents();
        if (MainProxy.isServer(getWorld())) {
            for (PendingTeleport pending : _inFlight) {
                list.add(pending.item.getItemIdentifierStack().makeNormalStack());
            }
        }
        return list;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        NBTTagList list = new NBTTagList();
        for (PendingTeleport pending : _inFlight) {
            NBTTagCompound tag = new NBTTagCompound();
            pending.item.writeToNBT(tag);
            tag.setInteger("teleportDirection", pending.direction.ordinal());
            tag.setInteger("teleportTicksRemaining", pending.ticksRemaining);
            list.appendTag(tag);
        }
        nbt.setTag("teleportingItems", list);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        _inFlight.clear();
        NBTTagList list = nbt.getTagList("teleportingItems", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            try {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                LPTravelingItemServer item = new LPTravelingItemServer(tag);
                if (item.isCorrupted()) {
                    continue;
                }
                ForgeDirection direction = ForgeDirection.getOrientation(tag.getInteger("teleportDirection"));
                int ticksRemaining = Math.max(1, tag.getInteger("teleportTicksRemaining"));
                _inFlight.add(new PendingTeleport(item, direction, ticksRemaining));
            } catch (Throwable t) {
                // It may be the case that entities cannot be reloaded between two versions - ignore these errors,
                // matching the tolerance of PipeTransportLogistics.readFromNBT().
            }
        }
    }
}
