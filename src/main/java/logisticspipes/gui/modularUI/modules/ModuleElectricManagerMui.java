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
import logisticspipes.modules.ModuleElectricManager;
import logisticspipes.modules.ModuleTerminus;
import logisticspipes.modules.abstractmodules.LogisticsModule;

public class ModuleElectricManagerMui extends LogisticsModuleMUI {

    private final IItemHandlerModifiable filterInventory;

    public ModuleElectricManagerMui(LogisticsModule module) {
        super(module);
        filterInventory = new InvWrapper(((ModuleElectricManager)module).getFilterInventory());
    }

    @Override
    public String getId() {
        return "module_electric_manager";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        ModuleElectricManager itemSinkModule = (ModuleElectricManager) module;

        if(addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget
            .child(new Column()
                .coverChildren()
                .left(9)
                .top(4)
                .childPadding(4)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .child(new TextWidget<>("Electric items"))
                .child(SlotGroupWidget.builder()
                    .row("IIIIIIIII")
                    .key('I', i -> new PhantomItemSlot()
                        .slot(filterInventory, i))
                    .build()
                )
                .child(new CycleButtonWidget()
                    .width(80).height(20)
                    .alignX(Alignment.END)
                    .value(
                        SyncHandlers.bool(itemSinkModule::isDischargeMode, itemSinkModule::setDischargeMode))
                    .overlay(
                        IKey.lang(
                            () -> itemSinkModule.isDischargeMode() ?
                                "Discharge items" :
                                "Charge items"
                        )
                    )
                )
            );

        return widget;
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 150;
    }
}
