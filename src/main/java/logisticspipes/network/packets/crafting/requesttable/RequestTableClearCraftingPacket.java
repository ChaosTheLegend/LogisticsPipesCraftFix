package logisticspipes.network.packets.crafting.requesttable;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

/**
 * Clears the fake crafting input grid of the redesigned request table.
 */
public class RequestTableClearCraftingPacket extends CoordinatesPacket {

    public RequestTableClearCraftingPacket(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new RequestTableClearCraftingPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        LogisticsTileGenericPipe tile = getPipe(player.worldObj);
        if (tile == null || !(tile.pipe instanceof RequestTablePipe)) {
            return;
        }
        ((RequestTablePipe) tile.pipe).clearCraftingGrid();
    }
}
