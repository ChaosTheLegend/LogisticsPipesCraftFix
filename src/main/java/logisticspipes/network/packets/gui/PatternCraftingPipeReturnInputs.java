package logisticspipes.network.packets.gui;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

/**
 * Returns all inputs currently stored by a pattern crafting pipe to network storage.
 */
public class PatternCraftingPipeReturnInputs extends CoordinatesPacket {

    public PatternCraftingPipeReturnInputs(int id) {
        super(id);
    }

    @Override
    public void processPacket(EntityPlayer player) {
        LogisticsTileGenericPipe tile = getPipe(player.worldObj);
        if (tile == null || !(tile.pipe instanceof PipeItemsPatternCraftingLogistics)) {
            return;
        }
        ((PipeItemsPatternCraftingLogistics) tile.pipe).returnStoredInputsToStorage();
    }

    @Override
    public ModernPacket template() {
        return new PatternCraftingPipeReturnInputs(getId());
    }
}
