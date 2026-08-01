package logisticspipes.network.packets.crafting.requesttable;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.network.abstractpackets.ItemPacket;
import logisticspipes.network.abstractpackets.ModernPacket;

/**
 * Synchronizes the client-side carried stack after a custom network-grid interaction.
 */
public class RequestTableSetCursorPacket extends ItemPacket {

    public RequestTableSetCursorPacket(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new RequestTableSetCursorPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        player.inventory.setItemStack(getStack());
    }
}
