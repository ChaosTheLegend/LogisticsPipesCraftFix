package logisticspipes.gui.modularUI.modules;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import logisticspipes.pipes.PipeFluidBasic;
import logisticspipes.pipes.PipeFluidProvider;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class PipeFluidBasicMui extends LogisticsPipeMUI {


    public PipeFluidBasicMui(CoreRoutedPipe pipe) {
        super(pipe);
    }

    @Override
    public String getId() {
        return "pipe_satellite";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        PipeFluidBasic providerPipe = (PipeFluidBasic) pipe;

        if(addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget
            .child(new Column()
                .coverChildren()
                .top(4)
                .left(9)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .childPadding(4)
                .child(new TextWidget<>("Fluid filter")
                )
                .child(new FluidSlot()
                    .syncHandler(SyncHandlers.fluidSlot(providerPipe.filterTank).phantom(true).controlsAmount(false))
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
        return 140;
    }
}
