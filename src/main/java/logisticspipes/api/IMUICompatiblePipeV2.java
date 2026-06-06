package logisticspipes.api;

import logisticspipes.compat.ModularUIHelper;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.pipes.basic.CoreUnroutedPipe;
import net.minecraft.entity.player.EntityPlayer;

public interface IMUICompatiblePipeV2 {

    default void openGui(EntityPlayer player, CoreUnroutedPipe pipe) {
        ModularUIHelper.openPipeUI(player, pipe);
    }

    LogisticsModularUI getPipeGui();
}
