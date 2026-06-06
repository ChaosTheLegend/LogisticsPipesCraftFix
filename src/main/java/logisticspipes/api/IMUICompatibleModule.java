package logisticspipes.api;

import logisticspipes.compat.ModularUIHelper;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public interface IMUICompatibleModule {

    default void openGui(EntityPlayer player, LogisticsGuiModule module, World world) {
        ModularUIHelper.openModuleUI(player, module, world);
    }

    public LogisticsModularUI getHandGui();

    public LogisticsModularUI getPipeGui();

}
