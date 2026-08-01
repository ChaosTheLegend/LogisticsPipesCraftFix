package logisticspipes.network.packets.gui;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.network.abstractpackets.IntegerCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

public class PatternCraftingPipeCancel extends IntegerCoordinatesPacket {

    public PatternCraftingPipeCancel(int id) {
        super(id);
    }

    @Override
    public void processPacket(EntityPlayer player) {
        LogisticsTileGenericPipe tile = getPipe(player.worldObj);
        if (tile == null || !(tile.pipe instanceof PipeItemsPatternCraftingLogistics)) {
            return;
        }
        ((PipeItemsPatternCraftingLogistics) tile.pipe).cancelPatternCraft(getInteger());
    }

    @Override
    public ModernPacket template() {
        return new PatternCraftingPipeCancel(getId());
    }
}
