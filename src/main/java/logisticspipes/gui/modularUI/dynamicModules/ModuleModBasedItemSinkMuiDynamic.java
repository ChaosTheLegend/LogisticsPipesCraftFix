package logisticspipes.gui.modularUI.dynamicModules;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleModBasedItemSink;
import logisticspipes.utils.item.ItemIdentifier;

public class ModuleModBasedItemSinkMuiDynamic extends GenericModuleMUI<ModuleModBasedItemSink> {

    private final IItemHandlerModifiable analyseInventory;

    public ModuleModBasedItemSinkMuiDynamic(ModuleModBasedItemSink module) {
        this(module, "");
    }

    public ModuleModBasedItemSinkMuiDynamic(ModuleModBasedItemSink module, String prefix) {
        super(module, prefix);
        analyseInventory = new InvWrapper(module.getAnalyseInventory());
    }

    @Override
    public String getId() {
        return "module_mod_based_item_sink";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(
                new Flow(GuiAxis.Y).coverChildren().left(9).top(4).childPadding(3)
                        .crossAxisAlignment(Alignment.CrossAxis.START).child(new TextWidget<>("Mod name sink"))
                        .child(buildAnalyseRow(syncManager)).child(buildEntryList()));

        return widget;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    private Flow buildAnalyseRow(PanelSyncManager syncManager) {
        String id = getFullId();

        PhantomItemSlot slotWidget = new PhantomItemSlot();
        if (syncManager != null) {
            PhantomItemSlotSH slotSH = syncManager.getOrCreateSyncHandler(
                    id + "_analyse",
                    0,
                    PhantomItemSlotSH.class,
                    () -> new PhantomItemSlotSH(new ModularSlot(analyseInventory, 0)));
            slotWidget.syncHandler(slotSH);
        } else {
            slotWidget.slot(analyseInventory, 0);
        }

        return Flow.row().coverChildren().childPadding(4).child(slotWidget).child(
                new ButtonWidget<>().onMousePressed(i -> {
                    if (i != 0) return false;
                    ItemStack stack = analyseInventory.getStackInSlot(0);
                    if (stack != null) module.addMod(ItemIdentifier.get(stack));
                    return true;
                }).overlay(IKey.lang("Add")).width(40).height(16));
    }

    private ListWidget<IWidget, ?> buildEntryList() {
        ListWidget<IWidget, ?> list = new ListWidget<>().width(150).height(80);
        for (int i = 0; i < ModuleModBasedItemSink.MAX_ENTRIES; i++) {
            int idx = i;
            list.child(
                    Flow.row().coverChildren().height(12).childPadding(4)
                            .child(new TextWidget<>(IKey.dynamic(() -> entryAt(idx))).width(120).height(10))
                            .child(
                                    new ButtonWidget<>().onMousePressed(btn -> {
                                        if (btn != 0) return false;
                                        String mod = entryAt(idx);
                                        if (!mod.isEmpty()) module.removeMod(mod);
                                        return true;
                                    }).overlay(GuiTextures.CROSS_TINY.asIcon().size(8)).size(10)));
        }
        return list;
    }

    private String entryAt(int idx) {
        return idx < module.modList.size() ? module.modList.get(idx) : "";
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
