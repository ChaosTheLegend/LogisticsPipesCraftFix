package logisticspipes.network.packets.crafting.requesttable;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.network.abstractpackets.IntegerCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

/**
 * Sends the selected internal storage back into the network when destinations exist.
 */
public class RequestTableSendStoragePacket extends IntegerCoordinatesPacket {

    public RequestTableSendStoragePacket(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new RequestTableSendStoragePacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        LogisticsTileGenericPipe tile = getPipe(player.worldObj);
        if (tile == null || !(tile.pipe instanceof RequestTablePipe)) {
            return;
        }
        RequestTablePipe table = (RequestTablePipe) tile.pipe;
        if (getInteger() == 1) {
            table.sendStoredFluidsToNetwork();
        } else {
            table.sendStoredItemsToNetwork();
        }
    }
}
