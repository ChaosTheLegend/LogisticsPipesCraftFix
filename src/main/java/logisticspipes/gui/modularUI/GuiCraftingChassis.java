package logisticspipes.gui.modularUI;


import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.pipes.PipeCraftingChassi;

public class GuiCraftingChassis{

    private PipeCraftingChassi _targetPipe;

    public GuiCraftingChassis(PipeCraftingChassi targetPipe) {
        _targetPipe = targetPipe;
    }

    public void addWidgets(ModularPanel panel, PosGuiData data, PanelSyncManager syncManager) {
        panel.background(ModularUIHelper.BACKGROUND_TEXTURE).bindPlayerInventory().padding(2)
            .child(
                IKey.str("Crafting Chassis").asWidget()
            );


    }

}
