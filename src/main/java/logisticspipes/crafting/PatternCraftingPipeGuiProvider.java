package logisticspipes.crafting;

import java.io.IOException;

import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.LogisticsPipes;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractguis.GuiProvider;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.utils.gui.DummyContainer;

public class PatternCraftingPipeGuiProvider extends ModuleCoordinatesGuiProvider {

    private int blockingMode;

    public PatternCraftingPipeGuiProvider(int id) {
        super(id);
    }

    public PatternCraftingPipeGuiProvider setBlockingMode(int blockingMode) {
        this.blockingMode = blockingMode;
        return this;
    }

    @Override
    public Object getClientGui(EntityPlayer player) {
        PipeItemsPatternCraftingLogistics pipe = getPatternPipe(player);
        if (pipe == null) {
            return null;
        }
        PipeItemsPatternCraftingLogistics.BlockingMode[] values = PipeItemsPatternCraftingLogistics.BlockingMode.values();
        pipe.setBlockingMode(values[Math.max(0, Math.min(values.length - 1, blockingMode))]);
        return new PatternCraftingPipeGui(player, pipe);
    }

    @Override
    public DummyContainer getContainer(EntityPlayer player) {
        PipeItemsPatternCraftingLogistics pipe = getPatternPipe(player);
        if (pipe == null) {
            return null;
        }
        DummyContainer dummy = new DummyContainer(player.inventory, pipe.getPatternModule().getPatternInventory());
        addPatternSlots(dummy, pipe);
        dummy.addNormalSlotsForPlayerInventory(8, 84);
        return dummy;
    }

    static void addPatternSlots(DummyContainer dummy, PipeItemsPatternCraftingLogistics pipe) {
        for (int i = 0; i < 9; i++) {
            dummy.addRestrictedSlot(i, pipe.getPatternModule().getPatternInventory(), 8 + i * 18, 28,
                    stack -> stack != null && stack.getItem() == LogisticsPipes.LogisticsPattern);
        }
    }

    private PipeItemsPatternCraftingLogistics getPatternPipe(EntityPlayer player) {
        LogisticsTileGenericPipe tile = getPipe(player.worldObj);
        if (tile == null || !(tile.pipe instanceof PipeItemsPatternCraftingLogistics)) {
            return null;
        }
        return (PipeItemsPatternCraftingLogistics) tile.pipe;
    }

    @Override
    public GuiProvider template() {
        return new PatternCraftingPipeGuiProvider(getId());
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeInt(blockingMode);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        blockingMode = data.readInt();
    }
}
