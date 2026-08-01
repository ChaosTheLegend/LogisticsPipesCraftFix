package logisticspipes.network.packets.gui;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

public class PatternCraftingPipeMode extends CoordinatesPacket {

    private int mode;

    public PatternCraftingPipeMode(int id) {
        super(id);
    }

    public PatternCraftingPipeMode setMode(int mode) {
        this.mode = mode;
        return this;
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeInt(mode);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        mode = data.readInt();
    }

    @Override
    public void processPacket(EntityPlayer player) {
        LogisticsTileGenericPipe tile = getPipe(player.worldObj);
        if (tile == null || !(tile.pipe instanceof PipeItemsPatternCraftingLogistics)) {
            return;
        }
        PipeItemsPatternCraftingLogistics.BlockingMode[] values = PipeItemsPatternCraftingLogistics.BlockingMode
                .values();
        ((PipeItemsPatternCraftingLogistics) tile.pipe)
                .setBlockingMode(values[Math.max(0, Math.min(values.length - 1, mode))]);
    }

    @Override
    public ModernPacket template() {
        return new PatternCraftingPipeMode(getId());
    }
}
