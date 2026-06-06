package logisticspipes.compat;

import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiFactory;
import com.cleanroommc.modularui.factory.inventory.InventoryType;
import com.cleanroommc.modularui.factory.inventory.InventoryTypes;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.gui.MUI.LogisticsModuleData;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.GuiFactories;

import logisticspipes.LogisticsPipes;
import logisticspipes.pipes.basic.CoreUnroutedPipe;

public class ModularUIHelper {

    public static final UITexture BACKGROUND_TEXTURE = UITexture.builder()
            .location(LogisticsPipes.rl("textures/gui/GuiBackground.png")).imageSize(45, 45).adaptable(15).build();

    public static void openPipeUI(EntityPlayer player, CoreUnroutedPipe pipe) {
        World world = pipe.getWorld();
        if (world == null || world.isRemote) return;

        GuiFactories.tileEntity().open(player, pipe.getX(), pipe.getY(), pipe.getZ());
    }

    public static void openModuleUI(EntityPlayer player, LogisticsGuiModule module, World world) {
        if (world == null || world.isRemote) return;

        GuiFactories.playerInventory().openFromMainHand(player);
    }

    private static EntityPlayerMP verifyServerSide(EntityPlayer player) {
        if (player == null) throw new NullPointerException("Can't open UI for null player!");
        if (player instanceof EntityPlayerMP entityPlayerMP) return entityPlayerMP;
        throw new IllegalStateException("Expected server player to open UI on server!");
    }
}
