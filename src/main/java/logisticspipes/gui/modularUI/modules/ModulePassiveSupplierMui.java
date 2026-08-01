package logisticspipes.gui.modularUI.modules;

import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import logisticspipes.gui.modularUI.LogisticsModuleMUI;
import logisticspipes.modules.ModulePassiveSupplier;
import logisticspipes.modules.abstractmodules.LogisticsModule;

public class ModulePassiveSupplierMui extends LogisticsModuleMUI {

    private final IItemHandlerModifiable filterInventory;

    public ModulePassiveSupplierMui(LogisticsModule module) {
        super(module);
        filterInventory = new InvWrapper(((ModulePassiveSupplier) module).getFilterInventory());
    }

    @Override
    public String getId() {
        return "module_passive_supplier";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        ModulePassiveSupplier itemSinkModule = (ModulePassiveSupplier) module;

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(
                new Column().coverChildren().left(9).top(4).childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.START).child(new TextWidget<>("Requested items")).child(
                                SlotGroupWidget.builder().row("IIIIIIIII")
                                        .key('I', i -> new PhantomItemSlot().slot(filterInventory, i)).build()));

        return widget;
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 130;
    }
}
