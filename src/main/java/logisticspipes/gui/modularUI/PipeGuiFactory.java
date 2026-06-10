package logisticspipes.gui.modularUI;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public class PipeGuiFactory {

    public static final ResourceLocation UpgradeSlotTexture = new ResourceLocation(
        "logisticspipes",
        "textures/gui/upgrade_slot.png");
    /*
     * Factory method to create a LogisticsMUIGui instance for a given pipe and module.
     * Automatically adds upgrade sidebar to the module UI
     *
     * @param pipe The CoreRoutedPipe instance for which the GUI is being created.
     * @param module The IMUICompatibleModule instance associated with the pipe.
     * @return A LogisticsMUIGui instance for the specified pipe and module.
     */
    public static LogisticsModularUI fromModule(CoreRoutedPipe pipe, IMUICompatibleModule module) {
        return new GenericPipeLogisticsGui(module, pipe);
    }

    public static boolean isUpgradeItem(ItemStack stack) {
        if(stack == null) return false;

        return (stack.getItem() instanceof ItemUpgrade);
    }

    public static void addUpgradeGui(ModularPanel panel, IItemHandlerModifiable upgradeHandler){
        panel.child(getUpgradeGui(upgradeHandler));
    }

    public static ParentWidget getUpgradeGui(IItemHandlerModifiable upgradeHandler, PanelSyncManager syncManager){

        syncManager.registerSlotGroup("upgrade_inventory", 4);

        var column = new Column()
            .background(ModularUIHelper.TAB_RIGHT_TEXTURE)
            .width(24)
            .child(SlotGroupWidget.builder()
                .row("I").row("I").row("I").row("I")
                .key('I', i -> new ItemSlot()
                    .slot(
                        new ModularSlot(upgradeHandler, i)
                            .slotGroup("upgrade_inventory")
                            .filter(PipeGuiFactory::isUpgradeItem)
                            .accessibility(true, true))
                    .background(UITexture.fullImage(UpgradeSlotTexture)))
                .build())
            .padding(4)
            .coverChildrenHeight();

        return (ParentWidget)column;
    }

    public static ParentWidget getUpgradeGui(IItemHandlerModifiable upgradeHandler){
        var column = new Column()
            .background(ModularUIHelper.TAB_RIGHT_TEXTURE)
            .width(24)
            .child(SlotGroupWidget.builder()
                .row("I").row("I").row("I").row("I")
                .key('I', i -> new ItemSlot()
                    .slot(
                        new ModularSlot(upgradeHandler, i)
                            .filter(PipeGuiFactory::isUpgradeItem)
                            .accessibility(true, true))
                    .background(UITexture.fullImage(UpgradeSlotTexture)))
                .build())
            .padding(4)
            .coverChildrenHeight();

        return (ParentWidget)column;
    }

    public static LogisticsModularUI fromMui(LogisticsPipeMUI pipeMui) {
        return new GenericSimplePipeLogisticsGui(pipeMui);
    }
}

