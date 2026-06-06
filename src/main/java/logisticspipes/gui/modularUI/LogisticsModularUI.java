package logisticspipes.gui.modularUI;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;

public abstract class LogisticsModularUI {

    public abstract String getId();

    /*
     * Returns a populated ModularPanel with the gui, usually used for module ui, calls addWidgets on default panel by default
     */
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager guiSyncManager) {
        return (ModularPanel)addWidgets(ModularPanel.defaultPanel(getId(), getWidth(), getHeight()), true);
    }

    /*
     * Adds widgets to the provided parent widget
     */
    public abstract ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory);

    public abstract int getWidth();

    public abstract int getHeight();
}
