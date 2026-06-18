package logisticspipes.network.packets.gui;

import logisticspipes.crafting.PatternCraftingPipeGuiProvider;
import logisticspipes.crafting.pattern.PatternContainer;
import logisticspipes.network.abstractpackets.IntegerCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import net.minecraft.entity.player.EntityPlayer;

public class PatternPipeSelectPacket extends IntegerCoordinatesPacket {

    public PatternPipeSelectPacket(int id) {
        super(id);
    }

    @Override
    public void processPacket(EntityPlayer player) {
        LogisticsTileGenericPipe tile = getPipe(player.worldObj);
        if (tile == null || !(tile.pipe instanceof PipeItemsPatternCraftingLogistics pipe)) {
            return;
        }
        if (player.openContainer instanceof PatternContainer) {
            ((PatternContainer) player.openContainer).setSelectedPatternSlot(getInteger());
        }
        ((PatternCraftingPipeGuiProvider) pipe.getPatternModule().getPipeGuiProviderForModule())
            .setSelectedPatternSlot(getInteger())
            .setTilePos(pipe.container)
            .open(player);
    }

    @Override
    public ModernPacket template() {
        return new PatternPipeSelectPacket(getId());
    }
}
