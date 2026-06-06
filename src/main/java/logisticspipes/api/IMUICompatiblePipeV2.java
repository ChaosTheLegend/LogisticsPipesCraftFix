package logisticspipes.api;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.gui.MUI.LogisticsMUIGui;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.pipes.basic.CoreUnroutedPipe;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public interface IMUICompatiblePipeV2 {

    default void openGui(EntityPlayer player, CoreUnroutedPipe pipe) {
        ModularUIHelper.openPipeUI(player, pipe);
    }

    LogisticsMUIGui getPipeGui();
}
