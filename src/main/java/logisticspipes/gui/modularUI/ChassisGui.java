package logisticspipes.gui.modularUI;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.DynamicSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.items.ItemModule;
import logisticspipes.logisticspipes.ItemModuleInformationManager;
import logisticspipes.modules.ModuleCrafter;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.PipeLogisticsChassi;

public class ChassisGui extends LogisticsModularUI {

    private final PipeLogisticsChassi pipe;
    private final IItemHandlerModifiable upgradeHandler;
    private final IItemHandlerModifiable moduleInventory;
    private final PagedWidget.Controller controller;

    private final List<DynamicSyncHandler> moduleSyncHandlers;
    private static final ResourceLocation ModuleSlotTexture1 = new ResourceLocation(
            "logisticspipes",
            "textures/gui/module_slot_1.png");

    public ChassisGui(PipeLogisticsChassi pipe) {
        this(pipe, "");
    }

    public ChassisGui(PipeLogisticsChassi pipe, String prefix) {
        super(prefix);
        this.pipe = pipe;
        moduleSyncHandlers = new ArrayList<>(pipe.getChassiSize());
        upgradeHandler = pipe.getUpgradeManager().getUpgradeInventory();
        moduleInventory = new InvWrapper(pipe.getModuleInventory());
        controller = new PagedWidget.Controller();
    }

    @Override
    public String getId() {
        return "chassis_gui_module";
    }

    @Override
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager guiSyncManager) {

        for (int i = 0; i < pipe.getChassiSize(); i++) {
            final int slot = i;
            DynamicSyncHandler handler = new DynamicSyncHandler().allowC2S()
                    .widgetProvider((innerSyncManager, packet) -> buildModuleWidget(innerSyncManager, slot));
            moduleSyncHandlers.add(handler);
        }

        var panel = new ModularPanel(getId());

        panel.width(getWidth()).height(getHeight()).background(IDrawable.EMPTY);

        addWidgets(panel, guiSyncManager, true);

        var row = new Flow(GuiAxis.X).height(28).left(2).top(0);

        guiSyncManager.registerSlotGroup("module_inventory", pipe.getChassiSize());

        int lastSlot = pipe.getChassiSize() - 1;
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            final int slot = i;
            var buttonContainer = new ParentWidget<>().size(28, 28);
            buttonContainer.child(
                    new PageButton(i, controller).size(28, 28)
                            .tab(GuiTextures.TAB_TOP, i == lastSlot || i == 0 ? i == 0 ? -1 : 1 : 0).pos(0, 0));
            buttonContainer.child(
                    new ItemSlot().slot(
                            new ModularSlot(moduleInventory, i).slotGroup("module_inventory").filter(this::isModuleItem)
                                    .changeListener(((newItem, onlyAmountChanged, client, init) -> {
                                        pipe.InventoryChanged(pipe.getModuleInventory());
                                        if (client && !onlyAmountChanged) {
                                            moduleSyncHandlers.get(slot).notifyUpdate(packet -> {});
                                        }
                                    })))
                            .background(UITexture.fullImage(ModuleSlotTexture1)).pos(5, 5));
            row.child(buttonContainer);
        }
        panel.child(row);

        var upgrades = PipeGuiFactory.getUpgradeGui(upgradeHandler, guiSyncManager);

        // .right(int) resolves this axis from a left+right anchor pair, which stretches the widget's width to fill
        // the gap instead of keeping the fixed width set in PipeGuiFactory - pin a fixed left instead so width/height
        // stay exactly as configured, while still landing the right edge 4px from the panel's right edge.
        upgrades.top(30).left(getWidth() - PipeGuiFactory.UPGRADE_GUI_WIDTH - 4);

        panel.child(upgrades);

        guiSyncManager.addCloseListener(player -> {
            IInventory inv = pipe.getModuleInventory();
            for (int i = 0; i < pipe.getChassiSize(); i++) {
                LogisticsModule module = pipe.getModules().getSubModule(i);
                if (module == null) {
                    continue;
                }
                ItemStack stack = inv.getStackInSlot(i);
                if (stack == null) {
                    continue;
                }
                ItemModuleInformationManager.saveInfotmation(stack, module);
                inv.setInventorySlotContents(i, stack);
            }
        });

        return panel;
    }

    private ParentWidget buildModuleWidget(PanelSyncManager innerSyncManager, int slot) {
        return addModuleUI(new Flow(GuiAxis.X).fullWidth().height(100), innerSyncManager, slot);
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return null;
    }

    private ParentWidget addModuleUI(ParentWidget widget, PanelSyncManager innerSyncManager, int slot) {

        ItemStack stack = pipe.getModuleInventory().getStackInSlot(slot);

        if (stack == null) {
            widget.child(centeredText("No module in slot"));
            return widget;
        }

        if (!(stack.getItem() instanceof ItemModule)) {
            widget.child(centeredText("Item is not a module"));
            return widget;
        }

        // the live, installed module for this slot - NOT a throwaway instance built from the itemstack,
        // so editing it here actually affects routing/pipe behavior
        LogisticsModule module = pipe.getModules().getSubModule(slot);

        if (module == null) {
            widget.child(centeredText("No module in slot"));
            return widget;
        }

        if (module instanceof ModuleCrafter) {
            widget.child(
                    centeredText("Crafting Modules are being replaced with Pattern Crafting Pipes, please use them"));
            return widget;
        }

        if (!(module instanceof IMUICompatibleModule compatibleModule)) {
            widget.child(centeredText("Module not compatible with MUI yet ＞﹏＜"));
            return widget;
        }

        // unique prefix per chassis slot, so two modules of the same type in different slots don't
        // collide on the same sync handler keys
        LogisticsModularUI gui = compatibleModule.getPipeGui("chassis_slot_" + slot);
        if (gui == null) {
            widget.child(centeredText("Module has no MUI"));
            return widget;
        }

        var moduleUI = new Flow(GuiAxis.Y).width(gui.getWidth()).fullHeight();
        gui.addWidgets(moduleUI, innerSyncManager, false);

        moduleUI.leftRel(Alignment.Center.x).anchorLeft(Alignment.Center.x);

        widget.fullWidth().fullHeight();
        widget.child(moduleUI);

        return widget;
    }

    private TextWidget<?> centeredText(String text) {
        return new TextWidget<>(text).leftRel(Alignment.Center.x).anchorLeft(Alignment.Center.x)
                .topRel(Alignment.Center.y).anchorTop(Alignment.Center.y);
    }

    private boolean isModuleItem(ItemStack itemStack) {
        if (itemStack == null) return false;

        return itemStack.getItem() instanceof ItemModule;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        var mainPanel = new Flow(GuiAxis.Y);

        mainPanel.width(224).height(180).left(2).top(28).background(ModularUIHelper.BACKGROUND_TEXTURE);

        var pages = new PagedWidget<>().controller(controller).fullWidth().height(100);

        // build each slot's module widget synchronously here (registering its sync handlers the normal way,
        // during panel construction) instead of via the DynamicSyncHandler's widgetProvider - creating new
        // sync handlers there only happens safely on genuine later rebuilds (e.g. a module swap), not on the
        // very first build, since that gets replayed from inside PanelSyncManager's own init pass and corrupts
        // its sync handler map
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            pages.addPage(
                    new DynamicSyncedWidget<>().fullWidth().height(100).syncHandler(moduleSyncHandlers.get(i))
                            .initialChild(addModuleUI(new Flow(GuiAxis.X).fullWidth().height(100), syncManager, i)));
        }

        mainPanel.child(pages);

        if (addPlayerInventory) mainPanel.child(SlotGroupWidget.playerInventory(true));

        return (ParentWidget) widget.child(mainPanel);
    }

    @Override
    public int getWidth() {
        return 254;
    }

    @Override
    public int getHeight() {
        return 210;
    }
}
