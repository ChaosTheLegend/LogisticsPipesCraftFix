package logisticspipes.gui.modularUI;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import logisticspipes.pipes.PipeBlockRequestTable;
import net.minecraft.util.ResourceLocation;

public class GuiLogisticsRequestTable {

    private PipeBlockRequestTable _table;

    //private static final ResourceLocation MODULE_ICON = new ResourceLocation("logisticspipes","textures/gui/ModuleGhost.png");
    //private static final ResourceLocation GHOST_UPGRADE = new ResourceLocation("logisticspipes","textures/gui/GhostUpgrade.png");

    public GuiLogisticsRequestTable(PipeBlockRequestTable targetPipe) {
        _table = targetPipe;
    }

    public void addWidgets(ModularPanel panel, PosGuiData data, PanelSyncManager syncManager) {

    }

}
