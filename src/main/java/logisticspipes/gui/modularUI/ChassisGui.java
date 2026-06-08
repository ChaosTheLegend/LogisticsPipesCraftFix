package logisticspipes.gui.modularUI;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.EmptyWidget;
import com.cleanroommc.modularui.widget.ParentWidget;
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
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.PipeLogisticsChassi;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ChassisGui extends LogisticsModularUI {

    private static final Logger log = LogManager.getLogger(ChassisGui.class);
    private final PipeLogisticsChassi pipe;
    private final IItemHandlerModifiable upgradeHandler;
    private final IItemHandlerModifiable moduleInventory;
    private final PagedWidget.Controller controller;

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
        this.pipe = pipe;
        upgradeHandler = pipe.getUpgradeManager().getUpgradeInventory();
        moduleInventory = new InvWrapper(pipe.getModuleInventory());
        controller = new PagedWidget.Controller();

    }

    @Override
    public String getId() {
        return "chassis_gui_module";
    }

    @Override
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager guiSyncManager){

        log.info("Creating ChassisGui");

        var panel = ModularPanel
            .defaultPanel(getId(), getWidth(), getHeight())
            .background(IDrawable.EMPTY);

        addWidgets(panel, true);

        var row = new Row()
            .height(28)
            .left(2)
            .top(0);

        for (int i = 0; i < pipe.getChassieSize(); i++) {
            final int slot = i;
            var buttonContainer = new ParentWidget<>()
                .size(28, 28);
            buttonContainer.child(new PageButton(i, controller)
                .size(28, 28)
                .tab(GuiTextures.TAB_TOP, i == 7 || i == 0 ? i == 0 ? -1 : 1 : 0)
                .pos(0, 0));
            buttonContainer.child(new ItemSlot()
                .slot(new ModularSlot(moduleInventory, i)
                    .filter(this::isModuleItem))
                .background(UITexture.fullImage(ModuleSlotTexture1))
                .pos(5, 5));
            row.child(buttonContainer);
        }
        panel.child(row);

        var upgrades = PipeGuiFactory.getUpgradeGui(upgradeHandler);
        upgrades.top(30);

        panel.child(upgrades);

        return panel;
    }

    private ParentWidget addModuleUI(ParentWidget widget, int slot){

        var stack = moduleInventory.getStackInSlot(slot);
        if(stack == null) {
            widget.child(new TextWidget<>("No module in slot " + slot).align(Alignment.Center));
            return widget;
        }

        var item = stack.getItem();

        if(item == null) {
            widget.child(new TextWidget<>("No module in slot " + slot).align(Alignment.Center));
            return widget;
        }

        if(!(item instanceof ItemModule itemModule)) {
            widget.child(new TextWidget<>("Item in slot " + slot + " is not a module").align(Alignment.Center));
            return widget;
        }

        LogisticsModule module = itemModule.getModuleForItem(stack, null, null, null);

        if(module == null) {
            widget.child(new TextWidget<>("No module in slot " + slot).align(Alignment.Center));
            return widget;
        }

        if(!(module instanceof IMUICompatibleModule)) {
            widget.child(new TextWidget<>("Module not compatible with MUI yet ＞﹏＜").align(Alignment.Center));
            return widget;
        }

        LogisticsModularUI gui = ((IMUICompatibleModule) module).getPipeGui();
        if (gui == null) {
            widget.child(new TextWidget<>("Module has no MUI").align(Alignment.Center));
            return widget;
        }

        var moduleUI = new Column().width(gui.getWidth()).fullHeight();
        gui.addWidgets(moduleUI, false);

        moduleUI.alignX(Alignment.Center);

        var upgrades = new Column()
            .width(26)
            .child(SlotGroupWidget.builder()
                .row("I").row("I").row("I").row("I")
                .key('I', i -> new ItemSlot()
                    .slot(new ModularSlot(pipe.getModuleUpgradeManager(slot).getUpgradeInventory(), i)
                        .filter(PipeGuiFactory::isUpgradeItem)
                        .accessibility(true, true))
                    .background(UITexture.fullImage(PipeGuiFactory.UpgradeSlotTexture)))
                .build())
            .padding(4)
            .coverChildrenHeight();

        upgrades.right(4)
            .alignY(Alignment.CENTER);

        widget.fullWidth().fullHeight();

        widget.child(moduleUI)
            .child(upgrades);


        return widget;
    }

    private boolean isModuleItem(ItemStack itemStack) {
        if(itemStack == null) return false;

        return itemStack.getItem() instanceof ItemModule;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        var mainPanel = new Column();

        mainPanel.width(224)
            .height(180)
            .left(2)
            .top(28)
            .background(ModularUIHelper.BACKGROUND_TEXTURE);

        var pages = new PagedWidget<>()
            .controller(controller)
            .fullWidth()
            .height(100);

        for (int i = 0; i < pipe.getChassieSize(); i++) {
            pages.addPage(addModuleUI(new Row()
                .fullWidth().height(100), i));
        }

        mainPanel.child(pages);

        if(addPlayerInventory) mainPanel.child(SlotGroupWidget.playerInventory(true));

        return (ParentWidget)widget.child(mainPanel);
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
