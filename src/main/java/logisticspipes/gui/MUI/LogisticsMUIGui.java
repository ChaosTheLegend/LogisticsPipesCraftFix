package logisticspipes.gui.MUI;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import logisticspipes.modules.abstractmodules.LogisticsModule;

public abstract class LogisticsMUIGui {

    protected final LogisticsModule module;

    public LogisticsMUIGui(LogisticsModule module) {
        this.module = module;
    }

    public abstract String getId();

    public ModularPanel GetPanel(GuiData guiData, PanelSyncManager guiSyncManager) {
        return ModularPanel
            .defaultPanel(getId(), 100, 100);
    }
}
