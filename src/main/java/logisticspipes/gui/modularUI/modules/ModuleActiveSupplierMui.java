package logisticspipes.gui.modularUI.modules;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import logisticspipes.gui.modularUI.LogisticsModuleMUI;
import logisticspipes.modules.ModuleActiveSupplier;
import logisticspipes.modules.abstractmodules.LogisticsModule;

public class ModuleActiveSupplierMui extends LogisticsModuleMUI {

    private final IItemHandlerModifiable supplyInventory;

    public ModuleActiveSupplierMui(LogisticsModule module) {
        super(module);
        supplyInventory = new InvWrapper(((ModuleActiveSupplier) module).getDummyInventory());
    }

    @Override
    public String getId() {
        return "module_active_supplier";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        ModuleActiveSupplier activeSupplierModule = (ModuleActiveSupplier) module;

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(
                new Column().fullWidth().coverChildrenHeight().left(9).right(9).top(4).childPadding(4)
                        .child(new TextWidget<>("Items to keep stocked").alignX(Alignment.START))
                        .child(
                                SlotGroupWidget.builder().row("III").row("III").row("III")
                                        .key('I', i -> new PhantomItemSlot().slot(supplyInventory, i)).build()
                                        .coverChildren().alignX(Alignment.CENTER))
                        .child(
                                new CycleButtonWidget().width(120).height(16)
                                        .value(
                                                SyncHandlers.enumValue(
                                                        ModuleActiveSupplier.SupplyMode.class,
                                                        activeSupplierModule::getSupplyMode,
                                                        activeSupplierModule::setSupplyMode))
                                        .overlay(IKey.lang(() -> switch (activeSupplierModule.getSupplyMode()) {
                                        case Partial -> "Request mode: Partial";
                                        case Full -> "Request mode: Full";
                                        case Bulk50 -> "Request mode: Bulk50";
                                        case Bulk100 -> "Request mode: Bulk100";
                                        case Infinite -> "Request mode: Infinite";
                                        })).alignX(Alignment.START)));

        return widget;
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 180;
    }
}
