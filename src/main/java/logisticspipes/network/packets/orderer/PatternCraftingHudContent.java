package logisticspipes.network.packets.orderer;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.network.abstractpackets.InventoryModuleCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

public class PatternCraftingHudContent extends InventoryModuleCoordinatesPacket {

    public PatternCraftingHudContent(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new PatternCraftingHudContent(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        final LogisticsTileGenericPipe tile = this.getPipe(player.worldObj);
        if (tile == null || !(tile.pipe instanceof PipeItemsPatternCraftingLogistics)) {
            return;
        }
        ((PipeItemsPatternCraftingLogistics) tile.pipe).setHudResultContent(getIdentList());
    }
}
