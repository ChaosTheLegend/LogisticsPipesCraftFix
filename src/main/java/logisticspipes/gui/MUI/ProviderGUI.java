package logisticspipes.gui.MUI;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import logisticspipes.logisticspipes.ExtractionMode;
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

    public ParentWidget addWidgets(ParentWidget widget){
        ModuleProvider moduleProvider = (ModuleProvider)module;

        widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(
                new Column()
                    .coverChildrenHeight()
                    .fullWidth()
                    .childPadding(4)
                    .child(new TextWidget<>("Filter items").left(4).height(10).top(4))
                    .child(new CycleButtonWidget()
                        .value(
                            SyncHandlers.enumValue(ExtractionMode.class, moduleProvider::getExtractionMode, i -> moduleProvider.setExtractionMode(i.ordinal())
                            ))
                        .overlay(
                            IKey.lang(
                                () -> switch (moduleProvider.getExtractionMode()){
                                    case Normal -> "Mode: Normal";
                                    case LeaveFirst -> "Mode: Leave first";
                                    case LeaveLast -> "Mode: Leave last";
                                    case LeaveFirstAndLast -> "Mode: Leave first and last";
                                    case Leave1PerStack -> "Mode: Leave 1 per stack";
                                    case Leave1PerType -> "Mode: Leave 1 per type";
                                })
                        )
                        .width(80).height(22).top(60).left(6))
                    .child(new CycleButtonWidget()
                        .value(
                            SyncHandlers.bool(moduleProvider::isExcludeFilter, moduleProvider::setFilterExcluded))
                        .overlay(
                            IKey.lang(
                                () -> moduleProvider.isExcludeFilter() ?
                                    "Whitelist" :
                                    "Blacklist"
                            )
                        )
                        .width(50).height(16).top(4).right(6)
                    )
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
                        .top(4))
            );

        return widget;
    }

    @Override
    public ModularPanel GetPanel(GuiData guiData, PanelSyncManager guiSyncManager) {

        ModuleProvider moduleProvider = (ModuleProvider)module;

        var panel = ModularPanel
            .defaultPanel(getId(), getWidth(), getHeight());

        addWidgets(panel);

        return panel;
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 170;
    }
}
