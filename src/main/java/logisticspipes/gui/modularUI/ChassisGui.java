package logisticspipes.gui.modularUI;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
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
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.items.ItemModule;
import logisticspipes.modules.ModuleCrafter;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.PipeLogisticsChassi;

public class ChassisGui extends LogisticsModularUI {

    private static final Logger log = LogManager.getLogger(ChassisGui.class);
    private final PipeLogisticsChassi pipe;
    private final IItemHandlerModifiable upgradeHandler;
    private final IItemHandlerModifiable moduleInventory;
    private final PagedWidget.Controller controller;

    private final List<DynamicSyncHandler> moduleSyncHandlers;
    private static final ResourceLocation ModuleSlotTexture1 = new ResourceLocation(
            "logisticspipes",
            "textures/gui/module_slot_1.png");
    private static final ResourceLocation ModuleSlotTexture2 = new ResourceLocation(
            "logisticspipes",
            "textures/gui/module_slot_2.png");
    private static final ResourceLocation ModuleSlotTexture3 = new ResourceLocation(
            "logisticspipes",
            "textures/gui/module_slot_3.png");

    public ChassisGui(PipeLogisticsChassi pipe) {
        this(pipe, "");
    }

    public ChassisGui(PipeLogisticsChassi pipe, String prefix) {
        super(prefix);
        this.pipe = pipe;
        moduleSyncHandlers = new ArrayList<>(pipe.getChassieSize());
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

        log.info("Creating ChassisGui");

        for (int i = 0; i < pipe.getChassieSize(); i++) {
            int slotId = i;
            moduleSyncHandlers.add(
                    new DynamicSyncHandler().widgetProvider(
                            (PanelSyncManager innerSyncManager,
                                    PacketBuffer packet) -> buildModuleWidget(innerSyncManager, packet, slotId)));
        }

        var panel = new ModularPanel(getId());

        panel.width(getWidth()).height(getHeight()).background(IDrawable.EMPTY);

        addWidgets(panel, guiSyncManager, true);

        var row = new Row().height(28).left(2).top(0);

        guiSyncManager.registerSlotGroup("module_inventory", pipe.getChassieSize());

        for (int i = 0; i < pipe.getChassieSize(); i++) {
            final int slot = i;
            var buttonContainer = new ParentWidget<>().size(28, 28);
            buttonContainer.child(
                    new PageButton(i, controller).size(28, 28)
                            .tab(GuiTextures.TAB_TOP, i == 7 || i == 0 ? i == 0 ? -1 : 1 : 0).pos(0, 0));
            buttonContainer.child(
                    new ItemSlot().slot(
                            new ModularSlot(moduleInventory, i).slotGroup("module_inventory").filter(this::isModuleItem)
                                    .changeListener(((newItem, onlyAmountChanged, client, init) -> {
                                        if (client && !onlyAmountChanged) {
                                            moduleSyncHandlers.get(slot).notifyUpdate(
                                                    packet -> NetworkUtils.writeItemStack(packet, newItem));
                                        }
                                    })))
                            .background(UITexture.fullImage(ModuleSlotTexture1)).pos(5, 5));
            row.child(buttonContainer);
        }
        panel.child(row);

        var upgrades = PipeGuiFactory.getUpgradeGui(upgradeHandler, guiSyncManager);
        upgrades.top(30).right(4);

        panel.child(upgrades);

        return panel;
    }

    private @Nullable ParentWidget buildModuleWidget(PanelSyncManager innerSyncManager, PacketBuffer packet, int slot) {

        return addModuleUI(
                new Row().fullWidth().height(100),
                innerSyncManager,
                NetworkUtils.readItemStack(packet),
                slot);
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return null;
    }

    private ParentWidget addModuleUI(ParentWidget widget, PanelSyncManager innerSyncManager, ItemStack moduleStack,
            int slot) {

        ItemStack stack = moduleStack;

        if (stack == null) {
            widget.child(new TextWidget<>("No module in slot").align(Alignment.Center));
            return widget;
        }

        var item = stack.getItem();

        if (item == null) {
            widget.child(new TextWidget<>("No module in slot").align(Alignment.Center));
            return widget;
        }

        if (!(item instanceof ItemModule itemModule)) {
            widget.child(new TextWidget<>("Item is not a module").align(Alignment.Center));
            return widget;
        }

        LogisticsModule module = itemModule.getModuleForItem(stack, null, null, null);

        if (module == null) {
            widget.child(new TextWidget<>("No module in slot").align(Alignment.Center));
            return widget;
        }

        if (module instanceof ModuleCrafter) {
            widget.child(
                    new TextWidget<>("Crafting Modules are being replaced with Pattern Crafting Pipes, please use them")
                            .align(Alignment.Center));
            return widget;
        }

        if (!(module instanceof IMUICompatibleModule)) {
            widget.child(new TextWidget<>("Module not compatible with MUI yet ＞﹏＜").align(Alignment.Center));
            return widget;
        }

        LogisticsModularUI gui = ((IMUICompatibleModule) module).getPipeGui();
        if (gui == null) {
            widget.child(new TextWidget<>("Module has no MUI").align(Alignment.Center));
            return widget;
        }

        var moduleUI = new Column().width(gui.getWidth()).fullHeight();
        gui.addWidgets(moduleUI, innerSyncManager, false);

        moduleUI.alignX(Alignment.Center);

        /*
         * var upgrades = new Column() .width(26) .child(SlotGroupWidget.builder() .row("I").row("I").row("I").row("I")
         * .key('I', i -> { var upgradeSlot = new ModularSlot(pipe.getModuleUpgradeManager(slot).getUpgradeInventory(),
         * i) .filter(PipeGuiFactory::isUpgradeItem) .accessibility(true, true); if (innerSyncManager != null) {
         * innerSyncManager.syncValue("chassis_upgrade_" + slot + "_" + i, upgradeSlot.getSyncHandler()); } return new
         * ItemSlot() .slot(upgradeSlot) .background(UITexture.fullImage(PipeGuiFactory.UpgradeSlotTexture)); })
         * .build()) .padding(4) .coverChildrenHeight(); upgrades.right(4) .alignY(Alignment.CENTER);
         */

        widget.fullWidth().fullHeight();
        widget.child(moduleUI);
        // .child(upgrades);

        return widget;
    }

    private boolean isModuleItem(ItemStack itemStack) {
        if (itemStack == null) return false;

        return itemStack.getItem() instanceof ItemModule;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        var mainPanel = new Column();

        mainPanel.width(224).height(180).left(2).top(28).background(ModularUIHelper.BACKGROUND_TEXTURE);

        var pages = new PagedWidget<>().controller(controller).fullWidth().height(100);

        for (int i = 0; i < pipe.getChassieSize(); i++) {
            pages.addPage(new DynamicSyncedWidget<>().fullWidth().height(100).syncHandler(moduleSyncHandlers.get(i)));
        }
        /*
         * addModuleUI(new Row() .fullWidth().height(100), i, syncManager)
         */

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
