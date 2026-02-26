package logisticspipes.gui.orderer;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.pipes.PipeBlockRequestTable;
import net.minecraft.entity.player.EntityPlayer;

public class GuiRequestTableMUI{

    private PipeBlockRequestTable pipe;
    private EntityPlayer player;

    public GuiRequestTableMUI(PipeBlockRequestTable pipe){
        this.pipe = pipe;
    }

    public void addUIWidgets(ModularPanel panel, PosGuiData data, PanelSyncManager syncManager){
        panel.background(ModularUIHelper.BACKGROUND_TEXTURE).bindPlayerInventory();
    }
}
