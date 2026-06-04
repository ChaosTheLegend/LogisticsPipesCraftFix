package logisticspipes.gui.MUI;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.modules.abstractmodules.LogisticsModule;

public class ProviderGUI extends LogisticsMUIGui {
    public ProviderGUI(LogisticsModule module) {
        super(module);
    }

    private final ItemStackHandler upgradeInventory = new ItemStackHandler(4);

    @Override
    public String getId() {
        return "Provider_module";
    }

    @Override
    public int GetWidth() {
        return 180;
    }

    @Override
    public int GetHeight() {
        return 140;
    }

    @Override
    public ModularPanel GetPanel(GuiData guiData, PanelSyncManager guiSyncManager) {

        if(!(guiData instanceof PlayerInventoryGuiData)){
            return super.GetPanel(guiData, guiSyncManager);
        }

        PlayerInventoryGuiData playerInventoryGuiData = (PlayerInventoryGuiData) guiData;

        var panel = ModularPanel
            .defaultPanel(getId(), 180, 140);


        panel.bindPlayerInventory();


        return panel;
    }
}
