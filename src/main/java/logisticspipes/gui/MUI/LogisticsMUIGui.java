package logisticspipes.gui.MUI;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import logisticspipes.modules.abstractmodules.LogisticsModule;

public abstract class LogisticsMUIGui {

    protected final LogisticsModule module;

    public LogisticsMUIGui(LogisticsModule module) {
        this.module = module;
    }

    public abstract String getId();

    public ModularPanel GetPanel(GuiData guiData, PanelSyncManager guiSyncManager) {
        return ModularPanel
            .defaultPanel(getId(), getWidth(), getHeight());
    }

    public abstract ParentWidget addWidgets(ParentWidget widget);

    public abstract int getWidth();

    public abstract int getHeight();
}
