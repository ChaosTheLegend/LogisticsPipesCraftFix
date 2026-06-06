package logisticspipes.gui.MUI;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.items.ItemModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.PipeLogisticsChassi;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ChassisGui extends LogisticsMUIGui{

    private static final Logger log = LogManager.getLogger(ChassisGui.class);
    private final PipeLogisticsChassi pipe;
    private final IItemHandlerModifiable upgradeHandler;
    private final IItemHandlerModifiable moduleInventory;

    public ChassisGui(PipeLogisticsChassi pipe) {
        this.pipe = pipe;
        upgradeHandler = pipe.getUpgradeManager().getUpgradeInventory();
        moduleInventory = new InvWrapper(pipe.getModuleInventory());
    }

    @Override
    public String getId() {
        return "chassis_gui_module";
    }

    PagedWidget.Controller controller;

    @Override
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager guiSyncManager){

        log.info("Creating ChassisGui");

        var panel = ModularPanel
            .defaultPanel(getId(), getWidth(), getHeight())
            .background(IDrawable.EMPTY);


        /*
        var row = new Row();

        controller = new PagedWidget.Controller();

        for (int i = 0; i < pipe.getChassieSize(); i++) {
            row.child(new PageButton(i, controller)
                .left(i * 20));
        }

        panel.child(row);

         */

        addWidgets(panel, true);

        PipeGuiFactory.addUpgradeGui(panel, upgradeHandler);

        return panel;
    }

    private ParentWidget addModuleUI(ParentWidget widget, LogisticsModule module){

        if(module == null) return widget;

        if(!(module instanceof IMUICompatibleModule)) return widget;

        LogisticsMUIGui gui = ((IMUICompatibleModule) module).getPipeGui();

        widget.width(gui.getWidth())
            .height(gui.getHeight());

        return gui.addWidgets(widget, false);
    }

    private boolean isModuleItem(ItemStack itemStack) {
        if(itemStack == null) return false;

        return itemStack.getItem() instanceof ItemModule;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        var mainPanel = new Column();

        if(addPlayerInventory) mainPanel.child(SlotGroupWidget.playerInventory(true));

        mainPanel.width(180)
            .height(180)
            .top(20)
            .background(ModularUIHelper.BACKGROUND_TEXTURE);

        //var pages = new PagedWidget().controller(controller);


        /*
        pages.width(180)
            .height(100);



        for (int i = 0; i < pipe.getChassieSize(); i++) {

            var column = new Column()
                .width(180)
                .height(100);

            column.child(new TextWidget<>(""+i));

            column.child(addModuleUI(new Column(), pipe.getModules().getModule(i)));

            pages.addPage(column);
        }


         */

        mainPanel.child(addModuleUI(new Column().width(180).height(100), pipe.getModules().getModule(0)));

        return (ParentWidget)widget.child(mainPanel);
    }

    @Override
    public int getWidth() {
        return 210;
    }

    @Override
    public int getHeight() {
        return 200;
    }
}
