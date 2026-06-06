package logisticspipes.gui.modularUI.modules;

import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.modules.ModuleItemSink;
import logisticspipes.modules.abstractmodules.LogisticsModule;

public class ModuleItemSinkMui extends LogisticsModuleMUI {

    private final IItemHandlerModifiable filterInventory;

    public ModuleItemSinkMui(LogisticsModule module) {
        super(module);
        filterInventory = new InvWrapper(((ModuleItemSink)module).getFilterInventory());
    }

    @Override
    public String getId() {
        return "module_item_sink";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        ModuleItemSink itemSinkModule = (ModuleItemSink) module;

        if(addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(
            SlotGroupWidget.builder()
                .row("IIIIIIIII")
                .key('I', i -> new PhantomItemSlot()
                    .slot(filterInventory, i))
                .build()
        );


        return widget;
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 120;
    }
}
