package logisticspipes.gui.modularUI;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;

public abstract class LogisticsModularUI {

    public abstract String getId();

    public String getFullId() {
        return prefix + "_" + getId();
    }

    public LogisticsModularUI(String prefix) {
        this.prefix = prefix;
    }

    protected String prefix;

    /*
     * Returns a populated ModularPanel with the gui, usually used for module ui, calls addWidgets on default panel by
     * default
     */
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager guiSyncManager) {
        return (ModularPanel) addWidgets(
                ModularPanel.defaultPanel(getId(), getWidth(), getHeight()),
                guiSyncManager,
                true);
    }

    /*
     * Adds widgets to the provided parent widget
     */
    @Deprecated
    public abstract ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory);

    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {
        return addWidgets(widget, addPlayerInventory);
    };

    public abstract int getWidth();

    public abstract int getHeight();
}
