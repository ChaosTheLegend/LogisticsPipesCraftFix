package logisticspipes.gui.MUI;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.modules.ModuleProvider;
import logisticspipes.modules.abstractmodules.LogisticsModule;

public class ProviderGUI extends LogisticsMUIGui {

    private final IItemHandlerModifiable filterInventory;
    public ProviderGUI(LogisticsModule module) {
        super(module);

        filterInventory = new InvWrapper(((ModuleProvider)module).getFilterInventory());
    }
    @Override
    public String getId() {
        return "Provider_module";
    }

    @Override
    public ModularPanel GetPanel(GuiData guiData, PanelSyncManager guiSyncManager) {

        var panel = ModularPanel
            .defaultPanel(getId(), 180, 160);

        panel.bindPlayerInventory()
            .child(SlotGroupWidget
                .builder()
                .row("III")
                .row("III")
                .row("III")
                .key('I', i -> new PhantomItemSlot().slot(
                    filterInventory, i
                ))
                .build()
                .align(Alignment.TopCenter)
                .top(5));

        return panel;
    }
}
