package logisticspipes.gui.modularUI.modules;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import logisticspipes.gui.modularUI.LogisticsModuleMUI;
import logisticspipes.modules.ModuleItemSink;
import logisticspipes.modules.abstractmodules.LogisticsModule;

public class ModuleItemSinkMui extends LogisticsModuleMUI {

    private final IItemHandlerModifiable filterInventory;

    public ModuleItemSinkMui(LogisticsModule module) {
        super(module);
        filterInventory = new InvWrapper(((ModuleItemSink) module).getFilterInventory());
    }

    @Override
    public String getId() {
        return "module_item_sink";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        ModuleItemSink itemSinkModule = (ModuleItemSink) module;

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(
                new Column().coverChildren().left(9).top(4).childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.START).child(new TextWidget<>("Requested items"))
                        .child(
                                SlotGroupWidget.builder().row("IIIIIIIII")
                                        .key('I', i -> new PhantomItemSlot().slot(filterInventory, i)).build())
                        .child(
                                new Row().coverChildrenHeight().widthRel(1f)
                                        .child(new ButtonWidget<>().onMousePressed(i -> {
                                            if (i == 0) itemSinkModule.importFromInventory();
                                            return i == 0;
                                        }).overlay(IKey.lang("Import")).width(40).height(16))
                                        .child(new Row().expanded()).child(
                                                new Row().coverChildren().childPadding(4)
                                                        .child(new TextWidget<>("Default route:")).child(
                                                                new CycleButtonWidget().width(26).height(16).value(
                                                                        SyncHandlers.bool(
                                                                                itemSinkModule::isDefaultRoute,
                                                                                itemSinkModule::setDefaultRoute))
                                                                        .overlay(
                                                                                IKey.lang(
                                                                                        () -> itemSinkModule
                                                                                                .isDefaultRoute() ? "On"
                                                                                                        : "Off"))))));

        return widget;
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 140;
    }
}
