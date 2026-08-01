package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleActiveSupplier;

public class ModuleActiveSupplierMuiDynamic extends GenericModuleMUI<ModuleActiveSupplier> {

    private final IItemHandlerModifiable supplyInventory;

    public ModuleActiveSupplierMuiDynamic(ModuleActiveSupplier module) {
        super(module);
        supplyInventory = new InvWrapper(module.getDummyInventory());
    }

    @Override
    public String getId() {
        return "module_active_supplier";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if(addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        var supplyModeSync = syncManager != null
            ? syncManager.getOrCreateSyncHandler(getFullId() + "_supply_mode", EnumSyncValue.class, () -> new EnumSyncValue<>(ModuleActiveSupplier.SupplyMode.class, module::getSupplyMode, module::setSupplyMode))
            : new EnumSyncValue<>(ModuleActiveSupplier.SupplyMode.class, module::getSupplyMode, module::setSupplyMode);

        widget
            .child(new Column()
                .fullWidth()
                .coverChildrenHeight()
                .left(9)
                .right(9)
                .top(4)
                .childPadding(4)
                .child(new TextWidget<>("Items to keep stocked").alignX(Alignment.START))
                .child(buildSupplySlots(syncManager)
                    .coverChildren()
                    .alignX(Alignment.CENTER)
                )
                .child(new CycleButtonWidget()
                    .width(120).height(16)
                    .value(supplyModeSync)
                    .overlay(
                        IKey.lang(
                            () -> switch (module.getSupplyMode()){
                                case Partial -> "Request mode: Partial";
                                case Full -> "Request mode: Full";
                                case Bulk50 -> "Request mode: Bulk50";
                                case Bulk100 -> "Request mode: Bulk100";
                                case Infinite -> "Request mode: Infinite";
                            }
                        )
                    )
                    .alignX(Alignment.START)
                )
            );

        return widget;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    private Flow buildSupplySlots(PanelSyncManager syncManager) {
        String id = getFullId();
        Flow col = Flow.col().coverChildren();
        for (int row = 0; row < 3; row++) {
            Flow rowWidget = Flow.row().coverChildren();
            for (int col2 = 0; col2 < 3; col2++) {
                int slotIndex = row * 3 + col2;
                PhantomItemSlot slotWidget = new PhantomItemSlot();
                if (syncManager != null) {
                    PhantomItemSlotSH slotSH = syncManager.getOrCreateSyncHandler(id + "_supply", slotIndex, PhantomItemSlotSH.class, () -> new PhantomItemSlotSH(new ModularSlot(supplyInventory, slotIndex)));
                    slotWidget.syncHandler(slotSH);
                } else {
                    slotWidget.slot(supplyInventory, slotIndex);
                }
                rowWidget.child(slotWidget);
            }
            col.child(rowWidget);
        }
        return col;
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
