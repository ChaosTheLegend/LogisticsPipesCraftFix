package logisticspipes.gui.modularUI.pipes;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import logisticspipes.gui.modularUI.LogisticsPipeMUI;
import logisticspipes.pipes.ISatellitePipe;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class PipeSatelliteMui extends LogisticsPipeMUI {

    public PipeSatelliteMui(CoreRoutedPipe pipe) {
        super(pipe);
    }

    @Override
    public String getId() {
        return "pipe_satellite";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        ISatellitePipe satellitePipe = (ISatellitePipe) pipe;

        widget.child(
                new Column().coverChildren().top(4).childPadding(4).leftRel(Alignment.Center.x)
                        .anchorLeft(Alignment.Center.x).topRel(Alignment.Center.y).anchorTop(Alignment.Center.y)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER).child(new TextWidget<>("Satellite id"))
                        .child(
                                new TextFieldWidget().width(80).setNumbers(0, Integer.MAX_VALUE).value(
                                        SyncHandlers.intNumber(
                                                satellitePipe::getSatelliteId,
                                                satellitePipe::setSatelliteId)))
                        .child(
                                new ButtonWidget<>().width(80).overlay(IKey.lang("Next free"))
                                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(i -> {
                                            if (i.mouseButton != 0) return;
                                            satellitePipe.setNextFreeId();
                                        }))));

        return widget;
    }

    @Override
    public int getWidth() {
        return 120;
    }

    @Override
    public int getHeight() {
        return 80;
    }
}
