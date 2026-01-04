package logisticspipes.network.guis.pipe;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.gui.GuiDummy;
import logisticspipes.network.abstractguis.CoordinatesGuiProvider;
import logisticspipes.network.abstractguis.GuiProvider;
import logisticspipes.pipes.PipeCraftingChassi;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;

public class DummyPipeGuiProvider extends CoordinatesGuiProvider {

    public DummyPipeGuiProvider(int id) {
        super(id);
    }

    @Override
    public Object getClientGui(EntityPlayer player) {
        LogisticsTileGenericPipe pipe = getPipe(player.worldObj);
        if (pipe != null && pipe.pipe instanceof PipeCraftingChassi) {
            PipeCraftingChassi craftingChassi = (PipeCraftingChassi) pipe.pipe;
            int xSize = 194;
            int ySize = craftingChassi.getChassiSize() > 4 ? 256 : 186;
            return new GuiDummy(player, craftingChassi.getChassiGUITexture(), xSize, ySize);
        }
        return new GuiDummy(player, PipeCraftingChassi.DummyGUI, 176, 166);
    }

    @Override
    public DummyContainer getContainer(EntityPlayer player) {
        LogisticsTileGenericPipe pipe = getPipe(player.worldObj);
        DummyContainer dummy = new DummyContainer(player.inventory, null);
        if (pipe != null && pipe.pipe instanceof PipeCraftingChassi) {
            PipeCraftingChassi craftingChassi = (PipeCraftingChassi) pipe.pipe;
            if (craftingChassi.getChassiSize() < 5) {
                dummy.addNormalSlotsForPlayerInventory(18, 97);
            } else {
                dummy.addNormalSlotsForPlayerInventory(18, 174);
            }
        } else {
            dummy.addNormalSlotsForPlayerInventory(18, 97);
        }
        return dummy;
    }

    @Override
    public GuiProvider template() {
        return new DummyPipeGuiProvider(getId());
    }
}
