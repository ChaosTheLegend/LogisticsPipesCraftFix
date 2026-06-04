package logisticspipes.gui.MUI;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import logisticspipes.modules.abstractmodules.LogisticsModule;

public class ProviderGUI extends LogisticsMUIGui {
    public ProviderGUI(LogisticsModule module) {
        super(module);
    }

    @Override
    public String getId() {
        return "Provider_module";
    }

    @Override
    public ModularPanel GetPanel(GuiData guiData, PanelSyncManager guiSyncManager) {

        if(!(guiData instanceof PlayerInventoryGuiData)){
            return super.GetPanel(guiData, guiSyncManager);
        }

        PlayerInventoryGuiData playerInventoryGuiData = (PlayerInventoryGuiData) guiData;

        var panel = ModularPanel
            .defaultPanel(getId(), 240, 300);

        panel.bindPlayerInventory();

        return panel;
    }
}
